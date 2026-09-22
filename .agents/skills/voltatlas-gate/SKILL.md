---
name: voltatlas-gate
description: Guidelines and procedures for running tests, maintaining quality gates, and protecting project rules in VoltAtlas.
---

# VoltAtlas Project Quality Gate & Development Guidelines

## Critical Rules
1. **Local Commits Only**: NEVER run `git push` (remote token rejects pushes).
2. **Preserve Group A Files**: Do not touch, commit, or revert:
   - `app/src/main/java/com/example/ui/dialogs/circuiteditor/ui/CircuitEditorParallelEntry.kt`
   - `app/src/main/java/com/example/ui/dialogs/circuiteditor/ui/CircuitEditorShell.kt`
   - `app/src/test/java/com/example/ui/dialogs/circuiteditor/ui/CircuitEditorSessionRuntimeTest.kt`
3. **Engineering Integrity**: The LLM must NEVER perform electrical calculations directly. All calculation outputs must come from the deterministic engines under `com.example.engine.*`.
4. **Safety Over Cosmetic Fixes**: A wrong `PASS` verdict is a safety hazard. Never introduce optimistic fallbacks.

## CI Test Gate Command
Always verify changes with the designated CI test gate profile:
```powershell
.\gradlew.bat :app:testDebugUnitTest -Pprofile=gate --rerun --console=plain
```
- Baseline: 2,209 tests, 0 failures, 13 skipped.
- Do NOT use `-Pprofile=fast` as the gate (it excludes AI mutation safety tests).
- Do NOT use `-Pprofile=full` as the gate (it hangs on legacy dialog isolation tests).
