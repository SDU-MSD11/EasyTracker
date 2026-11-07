# EasyTracker — Agent Team Guardrails

Stack: Native Android + Kotlin 2.2.10, AGP 9.5.1, compileSdk/targetSdk 37, minSdk 24,
Compose BOM 2026.02.01, Material3, Activity Compose 1.8.0. Single module `:app` for now.

## RELEVANCE-ONLY EXPLORATION (hard stop, all agents)

1. Start from the requested change; identify target folders/files first.
2. Search narrowly for the exact feature, component, config key, or path needed.
   Prefer `grep`/`glob`/`read` on known paths. No repo-wide sweeps.
3. Once API + conventions are clear, stop reading and implement.
4. If the user names the working area or says stop, stop immediately.
5. Report back with ONLY relevant file paths + findings, not full dumps.

## Git is read-only

- Allowed: `git status`, `git diff`, `git log` for context.
- NEVER: `git add`, `commit`, `push`, `tag`, `checkout`, `reset`, or any mutating git.
  Config already denies `add/commit/push/tag` and asks on `checkout/reset`.
- NEVER tell the user when to commit/push. They decide.

## Subagent delegation

- Never use a subagent for a narrow task on ≤2 known files — handle directly
  (exception: `android-lead` is a pure router and always delegates edits).
- Never replace deterministic tools (`grep`, `glob`, `read`) with a search agent.
  Only delegate search when semantic understanding / cross-codebase synthesis is needed.
- Match model to load: free/cheap (`beam-free`, `minimax-m3.1-flash`) for lookup,
  formatting, log parsing; capable (`muse-spark-1.3-contributor`, `glm-5.3-flash`)
  for architecture, multi-file builds, reviews.
- Domain agents may spawn exactly one level: `api-scout` only. No chains.
- Run independent subagents in parallel. Don't re-do their work in the parent;
  only integrate, resolve conflicts, verify.
- Substantial multi-file/system work requires: implement → fresh `reviewer`
  → `fixer` applies findings → re-review if still substantial → lead verifies
  build + tests.

## Code standards

- Prefer compact code: fewer lines when readability/maintainability is preserved.
  No duplication, no speculative abstraction, no dead code.
- Kotlin official style (`kotlin.code.style=official`). Compose-first, Material3.
- Unclear requirements → ask via `question` tool before coding.
- Multiple viable approaches → pros/cons table + recommendation + `question`, wait for decision.
- Outside your area → delegate, don't guess. Name the target agent.
