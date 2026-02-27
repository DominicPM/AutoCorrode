/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
   SPDX-License-Identifier: MIT */

package isabelle.assistant

import isabelle._
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.util.control.NonFatal
import java.io.{File, PrintWriter}
import scala.io.Source
import software.amazon.awssdk.thirdparty.jackson.core.{JsonFactory, JsonToken}

/**
 * Thread-safe persistent memory store for LLM-managed knowledge base.
 * 
 * Memories are organized hierarchically under topics (e.g., "user", "isabelle")
 * and persist across sessions in a JSON file in the jEdit settings directory.
 * Unlike TaskList (session-scoped), memories survive chat clears and restarts.
 */
object MemoryStore {
  
  /** Memory data structure */
  case class Memory(
    id: Int,
    topic: String,
    title: String,
    body: String,
    created: LocalDateTime
  )
  
  // Thread-safe state
  private val lock = new Object()
  @volatile private var memories: Map[String, List[Memory]] = Map.empty
  @volatile private var nextId: Int = 1
  @volatile private var loaded: Boolean = false
  private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
  
  // Validation limits
  private val MAX_TOPICS = 100
  private val MAX_MEMORIES_PER_TOPIC = 200
  private val MAX_TITLE_LENGTH = 200
  private val MAX_BODY_LENGTH = 2000
  private val TOPIC_NAME_PATTERN = "^[a-z0-9_]+$".r
  
  /**
   * Get the storage file path in jEdit settings directory.
   * Falls back to temp directory during testing.
   */
  private def storageFile: File = {
    try {
      val settingsDir = org.gjt.sp.jedit.jEdit.getSettingsDirectory
      if (settingsDir != null) {
        new File(settingsDir, "assistant-memories.json")
      } else {
        // Fallback for testing
        new File(System.getProperty("java.io.tmpdir"), "assistant-memories-test.json")
      }
    } catch {
      case _: Throwable =>
        // jEdit not available (testing environment)
        new File(System.getProperty("java.io.tmpdir"), "assistant-memories-test.json")
    }
  }
  
  /**
   * Lazy-load memories from disk on first access.
   */
  private def ensureLoaded(): Unit = lock.synchronized {
    if (!loaded) {
      load()
      loaded = true
    }
  }
  
  /**
   * Safe logging that doesn't fail if Output is unavailable.
   */
  private def safeLog(message: String): Unit = {
    try {
      Output.writeln(message)
    } catch {
      case _: Throwable => // Ignore if Output not available
    }
  }
  
  /**
   * Safe cache clearing that doesn't fail if PromptLoader is unavailable.
   */
  private def safeClearCache(): Unit = {
    try {
      PromptLoader.clearCache()
    } catch {
      case _: Throwable => // Ignore if PromptLoader not available
    }
  }
  
  /**
   * Escape a string for JSON output.
   */
  private def jsonEscape(s: String): String = {
    s.flatMap {
      case '"'  => "\\\""
      case '\\' => "\\\\"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c if c < ' ' => f"\\u${c.toInt}%04x"
      case c => c.toString
    }
  }
  
  /**
   * Load memories from JSON file using Jackson streaming parser.
   */
  private def load(): Unit = {
    val file = storageFile
    if (!file.exists()) {
      memories = Map.empty
      nextId = 1
      return
    }
    
    try {
      val source = Source.fromFile(file, "UTF-8")
      val content = try source.mkString finally source.close()
      
      val jsonFactory = new JsonFactory()
      val parser = jsonFactory.createParser(content)
      val loadedMemories = scala.collection.mutable.Map[String, List[Memory]]()
      
      try {
        // Expect START_OBJECT
        if (parser.nextToken() != JsonToken.START_OBJECT) return
        
        while (parser.nextToken() != JsonToken.END_OBJECT) {
          val fieldName = parser.currentName()
          parser.nextToken() // Move to field value
          
          fieldName match {
            case "version" => val _ = parser.getIntValue // Ignore for now
            case "nextId" => nextId = parser.getIntValue
            case "topics" =>
              // Parse topics object
              if (parser.getCurrentToken == JsonToken.START_OBJECT) {
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                  val topic = parser.currentName()
                  parser.nextToken() // Move to array
                  
                  if (parser.getCurrentToken == JsonToken.START_ARRAY) {
                    val mems = scala.collection.mutable.ListBuffer[Memory]()
                    
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                      // Parse memory object
                      var id = 0
                      var title = ""
                      var body = ""
                      var createdStr = ""
                      
                      if (parser.getCurrentToken == JsonToken.START_OBJECT) {
                        while (parser.nextToken() != JsonToken.END_OBJECT) {
                          val memField = parser.currentName()
                          parser.nextToken()
                          
                          memField match {
                            case "id" => id = parser.getIntValue
                            case "title" => title = parser.getText
                            case "body" => body = parser.getText
                            case "created" => createdStr = parser.getText
                            case _ => val _ = parser.skipChildren()
                          }
                        }
                        
                        if (id > 0 && title.nonEmpty && createdStr.nonEmpty) {
                          try {
                            val created = LocalDateTime.parse(createdStr, timeFormatter)
                            mems += Memory(id, topic, title, body, created)
                          } catch {
                            case NonFatal(e) =>
                              safeLog(s"[MemoryStore] Skipping corrupted memory in topic '$topic': ${e.getMessage}")
                          }
                        }
                      }
                    }
                    
                    if (mems.nonEmpty) {
                      loadedMemories(topic) = mems.toList
                    }
                  }
                }
              }
            case _ => val _ = parser.skipChildren()
          }
        }
        
        memories = loadedMemories.toMap
        safeLog(s"[MemoryStore] Loaded ${totalMemoryCount} memories from ${memories.size} topics")
      } finally {
        parser.close()
      }
    } catch {
      case NonFatal(e) =>
        safeLog(s"[MemoryStore] Failed to load memories: ${e.getMessage}")
        memories = Map.empty
        nextId = 1
    }
  }
  
  /**
   * Save memories to JSON file with atomic write (tmp + rename).
   */
  private def save(): Unit = {
    try {
      val json = new StringBuilder
      json.append("{\n")
      json.append(s"""  "version": 1,\n""")
      json.append(s"""  "nextId": $nextId,\n""")
      json.append("""  "topics": {""")
      
      val topicEntries = memories.toList.sortBy(_._1).map { case (topic, mems) =>
        val memEntries = mems.map { mem =>
          s"""      {
             |        "id": ${mem.id},
             |        "title": "${jsonEscape(mem.title)}",
             |        "body": "${jsonEscape(mem.body)}",
             |        "created": "${mem.created.format(timeFormatter)}"
             |      }""".stripMargin
        }.mkString(",\n")
        
        s"""    "$topic": [
           |$memEntries
           |    ]""".stripMargin
      }.mkString(",\n")
      
      json.append("\n")
      json.append(topicEntries)
      json.append("\n  }\n")
      json.append("}\n")
      
      val file = storageFile
      file.getParentFile.mkdirs()
      
      // Atomic write: write to tmp file then rename
      val tmpFile = new File(file.getParent, file.getName + ".tmp")
      val writer = new PrintWriter(tmpFile, "UTF-8")
      try {
        writer.write(json.toString)
      } finally {
        writer.close()
      }
      
      // Atomic rename
      if (!tmpFile.renameTo(file)) {
        // Fallback for systems where rename might fail
        tmpFile.delete()
        val directWriter = new PrintWriter(file, "UTF-8")
        try {
          directWriter.write(json.toString)
        } finally {
          directWriter.close()
        }
      }
    } catch {
      case NonFatal(e) =>
        safeLog(s"[MemoryStore] Failed to save memories: ${e.getMessage}")
    }
  }
  
  /**
   * Validate topic name.
   */
  private def validateTopicName(topic: String): Either[String, String] = {
    val trimmed = topic.trim.toLowerCase
    if (trimmed.isEmpty) Left("Error: topic name cannot be empty")
    else if (trimmed.length > 50) Left("Error: topic name too long (max 50 characters)")
    else if (TOPIC_NAME_PATTERN.findFirstIn(trimmed).isEmpty)
      Left(s"Error: invalid topic name '$trimmed' (use lowercase alphanumeric and underscores only)")
    else Right(trimmed)
  }
  
  /**
   * Validate title.
   */
  private def validateTitle(title: String): Either[String, String] = {
    val trimmed = title.trim
    if (trimmed.isEmpty) Left("Error: title cannot be empty")
    else if (trimmed.length > MAX_TITLE_LENGTH)
      Left(s"Error: title too long (max $MAX_TITLE_LENGTH characters)")
    else Right(trimmed)
  }
  
  /**
   * Validate body.
   */
  private def validateBody(body: String): Either[String, String] = {
    val trimmed = body.trim
    if (trimmed.isEmpty) Left("Error: body cannot be empty")
    else if (trimmed.length > MAX_BODY_LENGTH)
      Left(s"Error: body too long (max $MAX_BODY_LENGTH characters)")
    else Right(trimmed)
  }
  
  /**
   * Add a new memory to a topic.
   * Creates the topic if it doesn't exist.
   */
  def addMemory(topic: String, title: String, body: String): String = lock.synchronized {
    ensureLoaded()
    
    val result = for {
      validTopic <- validateTopicName(topic)
      validTitle <- validateTitle(title)
      validBody <- validateBody(body)
    } yield {
      // Check topic limit
      if (!memories.contains(validTopic) && memories.size >= MAX_TOPICS) {
        Left(s"Error: maximum number of topics ($MAX_TOPICS) reached")
      } else {
        // Check memories per topic limit
        val topicMems = memories.getOrElse(validTopic, Nil)
        if (topicMems.length >= MAX_MEMORIES_PER_TOPIC) {
          Left(s"Error: maximum number of memories ($MAX_MEMORIES_PER_TOPIC) reached for topic '$validTopic'")
        } else {
          val mem = Memory(
            id = nextId,
            topic = validTopic,
            title = validTitle,
            body = validBody,
            created = LocalDateTime.now()
          )
          
          memories = memories.updated(validTopic, topicMems :+ mem)
          nextId += 1
          save()
          safeClearCache()
          
          Right(s"Memory #${mem.id} added to topic '$validTopic': ${mem.title}")
        }
      }
    }
    
    result match {
      case Right(Right(msg)) => msg
      case Right(Left(err)) => err
      case Left(err) => err
    }
  }
  
  /**
   * Delete a memory by ID.
   */
  def deleteMemory(memoryId: Int): String = lock.synchronized {
    ensureLoaded()
    
    // Find the memory
    memories.iterator.flatMap { case (topic, mems) =>
      mems.find(_.id == memoryId).map(mem => (topic, mem))
    }.toList.headOption match {
      case None => s"Error: memory #$memoryId not found"
      case Some((topic, mem)) =>
        val updatedMems = memories(topic).filterNot(_.id == memoryId)
        if (updatedMems.isEmpty) {
          memories = memories - topic
        } else {
          memories = memories.updated(topic, updatedMems)
        }
        save()
        safeClearCache()
        s"Memory #$memoryId '${mem.title}' deleted from topic '$topic'"
    }
  }
  
  /**
   * Delete an entire topic and all its memories.
   */
  def deleteTopic(topic: String): String = lock.synchronized {
    ensureLoaded()
    
    validateTopicName(topic) match {
      case Left(err) => err
      case Right(validTopic) =>
        memories.get(validTopic) match {
          case None => s"Error: topic '$validTopic' not found"
          case Some(mems) =>
            val count = mems.length
            memories = memories - validTopic
            save()
            safeClearCache()
            s"Topic '$validTopic' deleted ($count memories removed)"
        }
    }
  }
  
  /**
   * List all topics with memory counts.
   */
  def listTopics(): String = lock.synchronized {
    ensureLoaded()
    
    if (memories.isEmpty)
      "No topics found. Add memories to create topics."
    else {
      val sorted = memories.toList.sortBy(_._1)
      val lines = sorted.map { case (topic, mems) =>
        s"• $topic (${mems.length} ${if (mems.length == 1) "memory" else "memories"})"
      }
      s"Topics (${memories.size}):\n${lines.mkString("\n")}"
    }
  }
  
  /**
   * List all memories in a topic.
   */
  def listMemories(topic: String): String = lock.synchronized {
    ensureLoaded()
    
    validateTopicName(topic) match {
      case Left(err) => err
      case Right(validTopic) =>
        memories.get(validTopic) match {
          case None => s"Topic '$validTopic' not found or empty"
          case Some(mems) if mems.isEmpty => s"Topic '$validTopic' is empty"
          case Some(mems) =>
            val lines = mems.map { mem =>
              s"#${mem.id}. ${mem.title}"
            }
            s"Memories in topic '$validTopic' (${mems.length}):\n${lines.mkString("\n")}"
        }
    }
  }
  
  /**
   * Get full details of a specific memory.
   */
  def getMemory(memoryId: Int): String = lock.synchronized {
    ensureLoaded()
    
    memories.iterator.flatMap { case (topic, mems) =>
      mems.find(_.id == memoryId)
    }.toList.headOption match {
      case None => s"Error: memory #$memoryId not found"
      case Some(mem) =>
        s"Memory #${mem.id} in topic '${mem.topic}':\n" +
        s"Title: ${mem.title}\n" +
        s"Created: ${mem.created.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}\n\n" +
        s"${mem.body}"
    }
  }
  
  /**
   * Search for memories matching a query (case-insensitive).
   */
  def searchMemories(query: String): String = lock.synchronized {
    ensureLoaded()
    
    val trimmedQuery = query.trim
    if (trimmedQuery.isEmpty) {
      "Error: search query cannot be empty"
    } else {
      val lowerQuery = trimmedQuery.toLowerCase
      val matches = scala.collection.mutable.ListBuffer[(String, Memory, String)]()
      
      for ((topic, mems) <- memories; mem <- mems) {
        val titleMatch = mem.title.toLowerCase.contains(lowerQuery)
        val bodyMatch = mem.body.toLowerCase.contains(lowerQuery)
        
        if (titleMatch || bodyMatch) {
          // Extract a snippet
          val snippet = if (titleMatch) {
            mem.title
          } else {
            // Find context around match in body
            val idx = mem.body.toLowerCase.indexOf(lowerQuery)
            val start = math.max(0, idx - 40)
            val end = math.min(mem.body.length, idx + lowerQuery.length + 40)
            val prefix = if (start > 0) "..." else ""
            val suffix = if (end < mem.body.length) "..." else ""
            prefix + mem.body.substring(start, end) + suffix
          }
          matches += ((topic, mem, snippet))
        }
      }
      
      if (matches.isEmpty)
        s"No memories found matching: $trimmedQuery"
      else {
        val lines = matches.map { case (topic, mem, snippet) =>
          s"#${mem.id} [$topic] ${mem.title}\n  $snippet"
        }
        s"Found ${matches.length} ${if (matches.length == 1) "memory" else "memories"} matching '$trimmedQuery':\n\n${lines.mkString("\n\n")}"
      }
    }
  }
  
  /**
   * Get a compact summary of all memories for system prompt injection.
   * Caps output at ~2000 characters to avoid consuming too much token budget.
   */
  def getAllMemoriesSummary(): String = lock.synchronized {
    ensureLoaded()
    
    if (memories.isEmpty)
      "No memories recorded yet."
    else {
      val lines = scala.collection.mutable.ListBuffer[String]()
      val sorted = memories.toList.sortBy(_._1)
      var charCount = 0
      val maxChars = 2000
      var truncated = false
      
      for ((topic, mems) <- sorted if !truncated) {
        val topicLine = s"**$topic** (${mems.length}):"
        if (charCount + topicLine.length < maxChars) {
          lines += topicLine
          charCount += topicLine.length + 1
          
          for (mem <- mems.take(10) if !truncated) {
            val memLine = s"  • #${mem.id}: ${mem.title}"
            if (charCount + memLine.length < maxChars) {
              lines += memLine
              charCount += memLine.length + 1
            } else {
              truncated = true
            }
          }
          
          if (!truncated && mems.length > 10) {
            val moreLine = s"  ... and ${mems.length - 10} more"
            if (charCount + moreLine.length < maxChars) {
              lines += moreLine
              charCount += moreLine.length + 1
            } else {
              truncated = true
            }
          }
        } else {
          truncated = true
        }
      }
      
      val result = lines.mkString("\n")
      if (truncated) result + "\n\n(Summary truncated at 2000 characters)" else result
    }
  }
  
  /**
   * Get all memories as structured data (for testing).
   */
  private[assistant] def getAllMemories: Map[String, List[Memory]] = lock.synchronized {
    ensureLoaded()
    memories
  }
  
  /**
   * Get the next ID (for testing).
   */
  private[assistant] def getNextId: Int = lock.synchronized {
    ensureLoaded()
    nextId
  }
  
  /**
   * Total count of all memories across all topics.
   */
  private def totalMemoryCount: Int = memories.values.map(_.length).sum
  
  /**
   * Force reload from disk (for testing).
   */
  private[assistant] def reload(): Unit = lock.synchronized {
    loaded = false
    ensureLoaded()
  }
  
  /**
   * Delete all memories (for testing only - not exposed as a tool).
   */
  private[assistant] def deleteAll(): Unit = lock.synchronized {
    memories = Map.empty
    nextId = 1
    save()
    safeClearCache()
  }
  
  /**
   * Reset the singleton state (for testing only).
   * Clears in-memory state and marks as not loaded, forcing reload on next access.
   */
  private[assistant] def resetForTest(): Unit = lock.synchronized {
    memories = Map.empty
    nextId = 1
    loaded = false
  }
}
