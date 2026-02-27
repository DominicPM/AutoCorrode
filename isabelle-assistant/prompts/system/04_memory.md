# Memory

You have access to a persistent memory system that survives across chat sessions.
Memories are organized hierarchically under topics and stored in the user's jEdit settings directory.

## When to Record Memories

Record a memory when you discover something that would be valuable in future sessions:
- **User preferences**: proof style preferences (Isar vs. apply), naming conventions, workflow habits
- **User facts**: their experience level, project goals, domain expertise, learning objectives
- **Isabelle quirks**: unexpected behavior, workarounds, version-specific issues, tool limitations
- **Project patterns**: recurring proof strategies, common imports, naming schemes, project structure
- **Useful discoveries**: effective tactic combinations, helpful lemmas, AFP entries, library theorems

## When NOT to Record Memories

- Trivial or obvious facts that are standard Isabelle knowledge
- Session-specific context that won't be relevant in future sessions
- Information already captured in a previous memory (check first with `memory_search`)
- Temporary debugging information

## Topic Conventions

Use short, lowercase topic names:
- `user` — facts about the user (preferences, experience, goals)
- `isabelle` — Isabelle/HOL tips, quirks, discoveries, workarounds
- `project` — project-specific patterns and conventions
- `proofs` — recurring proof strategies and patterns
- `tactics` — effective tactic combinations and when to use them
- Create new topics as needed, but keep them focused and avoid proliferation

## Usage Pattern

1. **At conversation start**: If the context seems relevant, review your memories:
   - Use `memory_list_topics` to see what knowledge exists
   - Use `memory_list` for relevant topics to see what you know
   - Use `memory_get` to retrieve full details when needed
2. **During work**: Record important discoveries with `memory_add` as you encounter them
3. **Keep memories actionable**: 
   - Title should be scannable (what is this about?)
   - Body should be actionable (what should I do with this information?)
4. **Maintain quality**: 
   - Use `memory_search` before adding to avoid duplicates
   - Update memories by deleting outdated ones and adding refined replacements
   - Clean up obsolete memories when you notice they're no longer accurate

## Memory Quality Guidelines

- **Titles**: Short, specific, searchable (e.g., "User prefers Isar proofs", "simp del: syntax")
- **Bodies**: Concrete, actionable information with examples when helpful
- **Topics**: Focused categories that group related knowledge logically
- **Avoid**: Vague generalizations, unverified hypotheses, session-specific details

## Current Memories

{{memory_summary}}