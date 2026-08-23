# 07: AI Conversation, Persona & Policy Audit

## 1. System Persona & Engineering Rules

The assistant system persona is defined in `RuntimePromptCompiler.kt` (lines 40–95). It enforces the following core engineering invariants:
1. **Deterministic Engineering Primacy**: Never fabricate numbers, cable sizes, or voltage drops.
2. **Authority Attribution**: Distinguish calculated values from user assumptions and catalog specs.
3. **Safety Disclaimers**: Display electrical engineering standards compliance caveats.
4. **Bilingual Parity**: Supports English and Arabic engineering queries with parity (`ArabicEnglishBehaviorParityTest.kt`).
5. **Anti-Injection Defenses**: Prompt compilation isolates user utterance inside a dedicated bounded section and explicitly forbids prompt overrides of mutation gates.

---

## 2. Policy Engine Capabilities (`ConversationPolicyEngine.kt`)

- **Slot Extraction**: Regex and keyword extraction for entity names (e.g. `C-01`, `TR-01`), length values (meters), load (kVA/kW), and installation methods.
- **Intent Classification**:
  - `WHAT_IF_ANALYSIS` $\longrightarrow$ Non-mutating calculation.
  - `NAVIGATE` $\longrightarrow$ Switch active entity context.
  - `CREATE_PROPOSAL` $\longrightarrow$ Initiates 2-step proposal flow.
  - `OUT_OF_SCOPE` $\longrightarrow$ Politely rejects non-electrical queries.
  - `SAFETY_DISCLAIMER` $\longrightarrow$ Attaches engineering disclaimer.
- **Conversational Affirmation Guard**: Conversational "apply/yes/confirm" without a valid UI `userActionId` is explicitly rejected with `CONVERSATIONAL_AFFIRMATION_REJECTED`.
