/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
   SPDX-License-Identifier: MIT */

package isabelle.assistant

/** Canonical identifier for assistant tools.
  *
  * Using a closed enum prevents drift and typo-based bugs across tool
  * definitions, execution dispatch, and permission policy.
  */
enum ToolId(val wireName: String) {
  case ReadTheory extends ToolId("read_theory")
  case ListTheories extends ToolId("list_theories")
  case SearchInTheory extends ToolId("search_in_theory")
  case SearchTheories extends ToolId("search_theories")
  case GetGoalState extends ToolId("get_goal_state")
  case GetSubgoal extends ToolId("get_subgoal")
  case GetProofContext extends ToolId("get_proof_context")
  case FindTheorems extends ToolId("find_theorems")
  case VerifyProof extends ToolId("verify_proof")
  case RunSledgehammer extends ToolId("run_sledgehammer")
  case RunNitpick extends ToolId("run_nitpick")
  case RunQuickcheck extends ToolId("run_quickcheck")
  case FindCounterexample extends ToolId("find_counterexample")
  case GetType extends ToolId("get_type")
  case GetCommandText extends ToolId("get_command_text")
  case GetErrors extends ToolId("get_errors")
  case GetDiagnostics extends ToolId("get_diagnostics")
  case GetDefinitions extends ToolId("get_definitions")
  case GetFileStats extends ToolId("get_file_stats")
  case GetProcessingStatus extends ToolId("get_processing_status")
  case GetSorryPositions extends ToolId("get_sorry_positions")
  case ExecuteStep extends ToolId("execute_step")
  case TraceSimplifier extends ToolId("trace_simplifier")
  case GetProofBlock extends ToolId("get_proof_block")
  case GetProofOutline extends ToolId("get_proof_outline")
  case GetContextInfo extends ToolId("get_context_info")
  case SearchAllTheories extends ToolId("search_all_theories")
  case GetDependencies extends ToolId("get_dependencies")
  case GetWarnings extends ToolId("get_warnings")
  case SetCursorPosition extends ToolId("set_cursor_position")
  case EditTheory extends ToolId("edit_theory")
  case TryMethods extends ToolId("try_methods")
  case GetEntities extends ToolId("get_entities")
  case WebSearch extends ToolId("web_search")
  case CreateTheory extends ToolId("create_theory")
  case OpenTheory extends ToolId("open_theory")
  case AskUser extends ToolId("ask_user")
  case TaskListAdd extends ToolId("task_list_add")
  case TaskListDone extends ToolId("task_list_done")
  case TaskListIrrelevant extends ToolId("task_list_irrelevant")
  case TaskListNext extends ToolId("task_list_next")
  case TaskListShow extends ToolId("task_list_show")
  case TaskListGet extends ToolId("task_list_get")
  case MemoryAdd extends ToolId("memory_add")
  case MemoryDelete extends ToolId("memory_delete")
  case MemoryDeleteTopic extends ToolId("memory_delete_topic")
  case MemoryListTopics extends ToolId("memory_list_topics")
  case MemoryList extends ToolId("memory_list")
  case MemoryGet extends ToolId("memory_get")
  case MemorySearch extends ToolId("memory_search")
  case PlanApproach extends ToolId("plan_approach")
}

object ToolId {
  private val byWire: Map[String, ToolId] =
    ToolId.values.map(t => t.wireName -> t).toMap

  def fromWire(name: String): Option[ToolId] = byWire.get(name)

  def displayName(id: ToolId): String =
    id.wireName.split("_").map(_.capitalize).mkString(" ")
  
  /** Tool IDs available to the planning sub-agent (read-only exploration only).
    * Excludes write operations, verification tools, and the plan_approach tool itself
    * to prevent recursion. */
  val planningToolIds: Set[ToolId] = Set(
    ReadTheory, ListTheories, SearchInTheory, SearchTheories, SearchAllTheories,
    GetGoalState, GetSubgoal, GetProofContext, GetProofBlock, GetProofOutline,
    GetContextInfo, GetCommandText, GetType,
    FindTheorems, GetDefinitions, GetDependencies, GetEntities,
    GetErrors, GetWarnings, GetDiagnostics,
    GetFileStats, GetProcessingStatus, GetSorryPositions,
    SetCursorPosition
  )
}