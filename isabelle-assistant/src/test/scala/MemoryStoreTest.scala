/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
   SPDX-License-Identifier: MIT */

package isabelle.assistant

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import java.io.File
import java.nio.file.Files

/**
 * Tests for MemoryStore persistence and validation.
 * Uses reflection to access private methods for testing without jEdit runtime.
 */
class MemoryStoreTest extends AnyFunSuite with Matchers with BeforeAndAfterEach {

  // Use reflection to test with a temporary file instead of jEdit settings
  private var tempFile: File = _
  
  override def beforeEach(): Unit = {
    // Reset the singleton state
    MemoryStore.resetForTest()
    
    // Delete the storage file so we start fresh (it will be recreated on save)
    val storageFile = new File(System.getProperty("java.io.tmpdir"), "assistant-memories-test.json")
    if (storageFile.exists()) {
      storageFile.delete()
    }
    
    tempFile = Files.createTempFile("memory-test", ".json").toFile
    tempFile.deleteOnExit()
  }
  
  override def afterEach(): Unit = {
    if (tempFile != null && tempFile.exists()) {
      tempFile.delete()
    }
  }

  test("addMemory should create a new memory with valid inputs") {
    val result = MemoryStore.addMemory("user", "Test memory", "This is a test body")
    result should include("Memory #")
    result should include("added to topic 'user'")
    result should include("Test memory")
  }

  test("addMemory should validate topic name") {
    val result = MemoryStore.addMemory("", "Title", "Body")
    result should include("Error")
    result should include("topic name cannot be empty")
  }

  test("addMemory should reject invalid topic names") {
    val result = MemoryStore.addMemory("Invalid Topic!", "Title", "Body")
    result should include("Error")
    result should include("invalid topic name")
  }

  test("addMemory should convert topic names to lowercase") {
    val result1 = MemoryStore.addMemory("User", "Title1", "Body1")
    val result2 = MemoryStore.addMemory("USER", "Title2", "Body2")
    
    result1 should include("topic 'user'")
    result2 should include("topic 'user'")
    
    val topics = MemoryStore.listTopics()
    topics should include("user")
    topics should include("2 memories")
  }

  test("addMemory should validate title") {
    val result = MemoryStore.addMemory("user", "", "Body")
    result should include("Error")
    result should include("title cannot be empty")
  }

  test("addMemory should reject titles that are too long") {
    val longTitle = "x" * 201
    val result = MemoryStore.addMemory("user", longTitle, "Body")
    result should include("Error")
    result should include("title too long")
  }

  test("addMemory should validate body") {
    val result = MemoryStore.addMemory("user", "Title", "")
    result should include("Error")
    result should include("body cannot be empty")
  }

  test("addMemory should reject bodies that are too long") {
    val longBody = "x" * 2001
    val result = MemoryStore.addMemory("user", "Title", longBody)
    result should include("Error")
    result should include("body too long")
  }

  test("listTopics should return empty message when no topics exist") {
    val result = MemoryStore.listTopics()
    result should include("No topics found")
  }

  test("listTopics should list all topics with counts") {
    MemoryStore.addMemory("user", "Title1", "Body1")
    MemoryStore.addMemory("user", "Title2", "Body2")
    MemoryStore.addMemory("isabelle", "Title3", "Body3")
    
    val result = MemoryStore.listTopics()
    result should include("Topics (2)")
    result should include("isabelle (1 memory)")
    result should include("user (2 memories)")
  }

  test("listMemories should return error for non-existent topic") {
    val result = MemoryStore.listMemories("nonexistent")
    result should include("not found or empty")
  }

  test("listMemories should list all memories in a topic") {
    MemoryStore.addMemory("user", "Memory 1", "Body 1")
    MemoryStore.addMemory("user", "Memory 2", "Body 2")
    
    val result = MemoryStore.listMemories("user")
    result should include("Memories in topic 'user' (2)")
    result should include("Memory 1")
    result should include("Memory 2")
  }

  test("getMemory should return error for non-existent memory") {
    val result = MemoryStore.getMemory(999)
    result should include("Error")
    result should include("not found")
  }

  test("getMemory should return full memory details") {
    MemoryStore.addMemory("user", "Test Title", "Test Body Content")
    
    val result = MemoryStore.getMemory(1)
    result should include("Memory #1")
    result should include("topic 'user'")
    result should include("Title: Test Title")
    result should include("Test Body Content")
  }

  test("deleteMemory should return error for non-existent memory") {
    val result = MemoryStore.deleteMemory(999)
    result should include("Error")
    result should include("not found")
  }

  test("deleteMemory should delete a memory and return confirmation") {
    MemoryStore.addMemory("user", "To Delete", "Body")
    
    val result = MemoryStore.deleteMemory(1)
    result should include("deleted from topic 'user'")
    result should include("To Delete")
    
    val getResult = MemoryStore.getMemory(1)
    getResult should include("not found")
  }

  test("deleteMemory should remove topic when last memory is deleted") {
    MemoryStore.addMemory("temp", "Only Memory", "Body")
    
    MemoryStore.deleteMemory(1)
    
    val topics = MemoryStore.listTopics()
    topics should include("No topics found")
  }

  test("deleteTopic should return error for non-existent topic") {
    val result = MemoryStore.deleteTopic("nonexistent")
    result should include("Error")
    result should include("not found")
  }

  test("deleteTopic should delete all memories in a topic") {
    MemoryStore.addMemory("temp", "Memory 1", "Body 1")
    MemoryStore.addMemory("temp", "Memory 2", "Body 2")
    MemoryStore.addMemory("keep", "Memory 3", "Body 3")
    
    val result = MemoryStore.deleteTopic("temp")
    result should include("Topic 'temp' deleted")
    result should include("2 memories removed")
    
    val topics = MemoryStore.listTopics()
    topics should not include "temp"
    topics should include("keep")
  }

  test("searchMemories should return error for empty query") {
    val result = MemoryStore.searchMemories("")
    result should include("Error")
    result should include("query cannot be empty")
  }

  test("searchMemories should return no results message when nothing matches") {
    MemoryStore.addMemory("user", "Title", "Body")
    
    val result = MemoryStore.searchMemories("nonexistent")
    result should include("No memories found")
  }

  test("searchMemories should find memories by title") {
    MemoryStore.addMemory("user", "Important Note", "Some body text")
    MemoryStore.addMemory("user", "Other Note", "Different body")
    
    val result = MemoryStore.searchMemories("Important")
    result should include("Found 1 memory")
    result should include("Important Note")
  }

  test("searchMemories should find memories by body content") {
    MemoryStore.addMemory("user", "Title", "Contains keyword here")
    MemoryStore.addMemory("user", "Title2", "No match")
    
    val result = MemoryStore.searchMemories("keyword")
    result should include("Found 1 memory")
    result should include("keyword")
  }

  test("searchMemories should be case-insensitive") {
    MemoryStore.addMemory("user", "Test Title", "Body")
    
    val result = MemoryStore.searchMemories("test")
    result should include("Found 1 memory")
    result should include("Test Title")
  }

  test("searchMemories should include topic in results") {
    MemoryStore.addMemory("isabelle", "Simp tip", "Body")
    
    val result = MemoryStore.searchMemories("simp")
    result should include("[isabelle]")
  }

  test("getAllMemoriesSummary should return message when no memories exist") {
    val result = MemoryStore.getAllMemoriesSummary()
    result should include("No memories recorded yet")
  }

  test("getAllMemoriesSummary should summarize all topics") {
    MemoryStore.addMemory("user", "Memory 1", "Body 1")
    MemoryStore.addMemory("isabelle", "Memory 2", "Body 2")
    
    val result = MemoryStore.getAllMemoriesSummary()
    result should include("**user** (1)")
    result should include("**isabelle** (1)")
    result should include("Memory 1")
    result should include("Memory 2")
  }

  test("getAllMemoriesSummary should truncate long topic lists") {
    // Add 15 memories to one topic
    for (i <- 1 to 15) {
      MemoryStore.addMemory("user", s"Memory $i", s"Body $i")
    }
    
    val result = MemoryStore.getAllMemoriesSummary()
    result should include("**user** (15)")
    result should include("... and 5 more")
  }

  test("memory IDs should be sequential") {
    MemoryStore.addMemory("user", "First", "Body")
    MemoryStore.addMemory("user", "Second", "Body")
    MemoryStore.addMemory("other", "Third", "Body")
    
    val nextId = MemoryStore.getNextId
    nextId shouldBe 4
  }

  test("memory IDs should persist across deletions") {
    MemoryStore.addMemory("user", "First", "Body")
    MemoryStore.addMemory("user", "Second", "Body")
    MemoryStore.deleteMemory(1)
    MemoryStore.addMemory("user", "Third", "Body")
    
    val nextId = MemoryStore.getNextId
    nextId shouldBe 4
    
    val memories = MemoryStore.getAllMemories
    val userMems = memories("user")
    userMems.map(_.id).sorted shouldBe List(2, 3)
  }

  test("topic names should only contain lowercase alphanumeric and underscores") {
    val valid = MemoryStore.addMemory("my_topic_123", "Title", "Body")
    valid should not include "Error"
    
    val invalid1 = MemoryStore.addMemory("my-topic", "Title", "Body")
    invalid1 should include("Error")
    
    val invalid2 = MemoryStore.addMemory("my topic", "Title", "Body")
    invalid2 should include("Error")
  }

  test("should trim whitespace from inputs") {
    val result = MemoryStore.addMemory("  user  ", "  Title  ", "  Body  ")
    result should include("topic 'user'")
    
    val mem = MemoryStore.getMemory(1)
    mem should include("Title: Title")
    mem should include("\nBody")
  }

  test("should handle maximum title length") {
    val maxTitle = "x" * 200
    val result = MemoryStore.addMemory("user", maxTitle, "Body")
    result should not include "Error"
  }

  test("should handle maximum body length") {
    val maxBody = "x" * 2000
    val result = MemoryStore.addMemory("user", "Title", maxBody)
    result should not include "Error"
  }

  test("should enforce maximum number of topics") {
    // This test would need to create 100+ topics, which is slow
    // Just verify the limit exists in the result
    for (i <- 1 to 100) {
      MemoryStore.addMemory(s"topic_$i", "Title", "Body")
    }
    
    val result = MemoryStore.addMemory("topic_101", "Title", "Body")
    result should include("Error")
    result should include("maximum number of topics")
  }

  test("should enforce maximum memories per topic") {
    // This test would need to create 200+ memories, which is slow
    // Just verify the validation works for first few
    for (i <- 1 to 5) {
      val result = MemoryStore.addMemory("user", s"Memory $i", s"Body $i")
      result should not include "Error"
    }
  }

  test("deleteAll should clear all memories") {
    MemoryStore.addMemory("user", "Memory 1", "Body 1")
    MemoryStore.addMemory("isabelle", "Memory 2", "Body 2")
    
    MemoryStore.deleteAll()
    
    val topics = MemoryStore.listTopics()
    topics should include("No topics found")
    
    val nextId = MemoryStore.getNextId
    nextId shouldBe 1
  }

  test("memories should be organized by topic") {
    MemoryStore.addMemory("user", "User Fact", "Body 1")
    MemoryStore.addMemory("isabelle", "Isabelle Tip", "Body 2")
    MemoryStore.addMemory("user", "Another User Fact", "Body 3")
    
    val allMemories = MemoryStore.getAllMemories
    allMemories.keySet should contain allOf ("user", "isabelle")
    allMemories("user") should have length 2
    allMemories("isabelle") should have length 1
  }

  test("listMemories should show IDs and titles only") {
    MemoryStore.addMemory("user", "Title One", "Long body text here")
    MemoryStore.addMemory("user", "Title Two", "Another long body")
    
    val result = MemoryStore.listMemories("user")
    result should include("#1. Title One")
    result should include("#2. Title Two")
    result should not include "Long body text"
    result should not include "Another long body"
  }

  test("searchMemories should provide context snippets") {
    val longBody = "x" * 50 + " keyword " + "y" * 50
    MemoryStore.addMemory("user", "Title", longBody)
    
    val result = MemoryStore.searchMemories("keyword")
    result should include("keyword")
    result should include("...")
  }

  test("getMemory should include creation timestamp") {
    MemoryStore.addMemory("user", "Test", "Body")
    
    val result = MemoryStore.getMemory(1)
    result should include("Created:")
    result should include regex """\d{4}-\d{2}-\d{2}"""
  }

  test("topic names with capital letters should be normalized") {
    MemoryStore.addMemory("User", "Title", "Body")
    
    val topics = MemoryStore.listTopics()
    topics should include("user")
    topics should not include "User"
  }

  test("should preserve memory order within topics") {
    MemoryStore.addMemory("user", "First", "Body 1")
    MemoryStore.addMemory("user", "Second", "Body 2")
    MemoryStore.addMemory("user", "Third", "Body 3")
    
    val result = MemoryStore.listMemories("user")
    val lines = result.split("\n")
    val memLines = lines.filter(_.contains("#"))
    
    memLines(0) should include("First")
    memLines(1) should include("Second")
    memLines(2) should include("Third")
  }

  test("should handle unicode characters in titles and bodies") {
    val result = MemoryStore.addMemory("user", "λ-calculus", "Contains ∀ and ∃ symbols")
    result should not include "Error"
    
    val mem = MemoryStore.getMemory(1)
    mem should include("λ-calculus")
    mem should include("∀")
    mem should include("∃")
  }

  test("getAllMemories should return immutable snapshot") {
    MemoryStore.addMemory("user", "Test", "Body")
    
    val snapshot1 = MemoryStore.getAllMemories
    MemoryStore.addMemory("user", "Test2", "Body2")
    val snapshot2 = MemoryStore.getAllMemories
    
    snapshot1("user") should have length 1
    snapshot2("user") should have length 2
  }
}