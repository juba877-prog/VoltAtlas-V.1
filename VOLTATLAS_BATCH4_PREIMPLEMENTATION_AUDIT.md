# VOLTATLAS — Phase 6 Batch 4 Pre-Implementation Audit

**Date:** 2026-10-01
**HEAD at audit time:** `6d655e5` (Phase 6 Batch 3 — P1)
**Status:** AUDIT ONLY — **no production, engine, or test code was modified.**
**Working tree:** clean of `app/` changes (only untracked `VOLTATLAS_*.md` artifacts + the B3 log).

---

## 1. GIT SAFETY / PROTECTED BASELINE

Recorded SHA-256 prefixes (16 hex) for every protected artifact, to be re-verified at completion:

| Protected artifact | SHA-256 prefix |
|---|---|
| `engine/AtsEngine.kt` | `f6f81286e952c03a` |
| `engine/AtsIscResolver.kt` | `a166317757db34bb` |
| `engine/BusbarSizingEngine.kt` | `1ea5201ee699c4c7` |
| `engine/BuswayEngine.kt` | `d38f3e6b5654714b` |
| `engine/CableSizingEngine.kt` | `ef059d5755431320` |
| `engine/ConduitFillEngine.kt` | `98e49d23c758938c` |
| `engine/CoordinationEngine.kt` | `f7de0be525f30d4d` |
| `engine/DiversityCalculationEngine.kt` | `2b5eaa2bb5ac0ff5` |
| `engine/EarthingEngine.kt` | `64bc21ca060730f6` |
| `engine/GeneratorEngine.kt` | `9cca49c540f09e42` |
| `engine/OtherEngines.kt` | `2aeee5fe9c4710b1` |
| `engine/PfCorrectionEngine.kt` | `8183f72132eb367a` |
| `engine/ProtectionEngine.kt` | `36a0066fdf21c15d` |
| `engine/ShortCircuitEngine.kt` | `033949f2f3debdff` |
| `engine/TransformerEngine.kt` | `1d02001490ec4bc2` |
| `engine/UpsEngine.kt` | `d9fb6eb8f00cb8eb` |
| `engine/converters/ElectricalConverters.kt` | `3693bce830d773db` |
| `engine/coordinator/CableCalculationService.kt` | `3999eb603a6db394` |
| `engine/coordinator/CoordinationCalculationService.kt` | `c2e203ca100c5017` |
| `engine/coordinator/EngineeringCalculationCoordinator.kt` | `b822ec370487b887` |
| `engine/coordinator/ProtectionCalculationService.kt` | `6c9630c9888ca671` |
| `engine/coordinator/ShortCircuitCalculationService.kt` | `fa4ad7bd73f0c33e` |
| `engine/coordinator/TransformerCalculationService.kt` | `a4eb35c3c994ad6c` |
| `engine/quick/QuickEngineeringDataBridge.kt` | `74aa22d94274ee8c` |
| `ui/…/CircuitEditorParallelEntry.kt` | `4d430bdf60abed16` |
| `ui/…/CircuitEditorShell.kt` | `4cc0fee135bc844e` |
| `test/…/CircuitEditorSessionRuntimeTest.kt` | `770ff2c895dd8d4d` |

24 files under `com.example.engine.*`. **None modified.**

---

## 2. GEMINI RECOVERY TRACK — ROOT CAUSE (production defect, proven)

### 2.1 Traced production path (actual call sites, not class names)

```
EngineeringAssistantSheet (composable)
  └─ EngineeringAssistantViewModel.onUserSubmit
       └─ HybridConversationRouter.evaluateTurn            (HybridConversationRouter.kt:34)
            ├─ connectivityProbe.isLikelyOnline()          (:39)
            └─ withTimeout(cloudTimeoutMs) { cloudEngine.evaluateTurn }   (:62)
                 └─ CloudLlmConversationEngine
                      └─ AgenticToolLoopOrchestrator
                           └─ DirectGeminiClientSeam.generateTurn        (:60)
                                └─ buildRequestJson                        (:80)
                                └─ OkHttp POST
                                   https://generativelanguage.googleapis.com/v1beta/
                                   models/{config.modelName}:generateContent   (:81)
                                └─ parseGeminiResponse / parseErrorResponse
            └─ on cloudDegradationReason != null → localEngine.evaluateTurn  (:69)
       └─ offlineBadgeVisible = (source == LOCAL_RULE_ENGINE && reason != null)  (:645-650)
  └─ EngineeringAssistantSheet renders "OFFLINE"          (:320-334)
```

### 2.2 PRIMARY DEFECT — invalid `Content.role` on every tool-result turn

`DirectGeminiClientSeam.kt:302` emits:

```kotlin
put("role", "function")          // <-- INVALID for generateContent
```

**Authoritative contract** — `https://ai.google.dev/api/generate-content`, `Content.role`:

> "The producer of the content. **Must be either 'user' or 'model'.**"

`generateContent` defines **no** `function` role. Function results are returned as a content
with `role: "user"` whose parts carry `functionResponse`. The app therefore sends a **structurally
invalid second turn** for every agentic tool round-trip.

### 2.3 Why Settings says CONNECTED while the Assistant says OFFLINE

This is the exact reported symptom and it is fully explained:

| Step | Request shape | Result |
|---|---|---|
| `testConnection()` (Settings) | `contents:[{role:user, parts:[text:"ping"]}]`, `maxOutputTokens:5`, **no `tools`** | **HTTP 200** → `ConnectionTestStatus.CONNECTED` → badge **"● Connected"** |
| Assistant turn 1 | `contents` + `tools` + `generationConfig` | 200; model emits `functionCall` |
| Assistant turn 2 (tool result) | `role:"function"` + `functionResponse` parts | **HTTP 400 INVALID_ARGUMENT** |
| Router | any cloud degradation → local engine | `cloudDegradationReason != null` |
| ViewModel | rule at `:645` | `offlineBadgeVisible = true` → **"OFFLINE"** |

**`testConnection()` cannot detect this defect by construction** — it never sends `tools`, never
sends a `functionResponse`, and never performs a second turn. It proves *key + endpoint + model
reachability* only, while the UI labels it "● Connected".

### 2.4 Wire-protocol audit (verified against official docs, 2026-10-01)

| Element | Official `generateContent` contract | App today | Verdict |
|---|---|---|---|
| System instruction | `systemInstruction` | `system_instruction` (`:136`) | snake_case alias — accepted by protobuf JSON |
| Tool declarations | `tools[].functionDeclarations` | `function_declarations` (`:147`) | snake_case alias — accepted |
| `functionCall` args | **`args`** (Struct) — *not* `arguments` | `args` (`:283`, parsed `:432`) | ✅ **CORRECT** |
| `FunctionCall.id` | "If populated, the client … return the response with the matching `id`" | uses provider id, else **`UUID.randomUUID()`** (`:431`) | ❌ **invents IDs** |
| `functionResponse` | `id`, `name`, `response` | `name`, `response`, `id` | ✅ correct shape |
| **Content role** | **`user` \| `model`** | **`function`** (`:302`) | ❌ **INVALID — ROOT CAUSE** |
| **`thoughtSignature`** | `Part.thoughtSignature` — "An opaque signature for the thought so it can be reused in subsequent requests. A base64-encoded string." | **absent: never parsed, never stored, never resent** | ❌ **Gemini 3 continuity broken** |
| Schema `type` values | REST accepts lowercase OpenAPI (`"object"`); uppercase also accepted | uppercase (`OBJECT`,`STRING`,`NUMBER`,`ARRAY`) | ✅ accepted |
| Tool names | `a-z A-Z 0-9 _ -`, max 128 | canonical snake_case tool names | ✅ compliant |

### 2.5 thoughtSignature — status and requirement

Google, `https://ai.google.dev/gemini-api/docs/thinking`:

> "In the `generateContent` API … signatures are metadata that can be attached to any part,
> such as **living inside `functionCall` parts** or the final part of a response."

The app **discards them**. Because the app manually manages conversation state, it is required to
resend them verbatim on the next model turn. Today it neither captures nor replays them, so:

1. the second turn is additionally structurally invalid (role), and
2. even once role is fixed, Gemini 3 reasoning continuity is still lost.

`ToolCall` (`CloudLlmResponseContract.kt`) currently carries `callId`, `toolName`, `arguments`
only — there is **nowhere** to hold a signature. `AgenticTurnItem.ModelCalls` / `ExecutedTools`
(`AgenticSafetyBudget.kt`) carry the calls, so full protocol fidelity requires threading a new
field through `ToolCall` (the user has already approved "full protocol fidelity").

### 2.6 Model health — NO downgrade

`gemini-3.8-flash` was verified against current Google documentation: it appears in the docs
banner, in the thinking-levels table ("On (medium)"), and in every current API example.
**KEEPING the model unchanged.** No downgrade is justified or permitted.

### 2.7 Error classification collapse

`parseErrorResponse` (`:463-484`) computes a `category` string for **logging only**, then returns a
single `ProviderError` for every failure. Downstream, `CloudLlmConversationEngine` maps it to
`HTTP_OR_PROVIDER_ERROR` (`:456`, `:617-646`). Consequently these **all** render as the single
"OFFLINE" badge:

HTTP 400 · 401 · 403 · 404 (model not found) · 429 · 5xx · timeout · request-schema failure ·
response parse failure.

`CloudDegradationReason` already defines the vocabulary needed
(`AUTHENTICATION_FAILURE`, `RATE_LIMITED`, `NETWORK_TIMEOUT`, `CONFIGURATION_FAILURE`,
`INVALID_TOOL_CALL`, `GROUNDING_VALIDATION_FAILED`, `SAFETY_BLOCKED`, `CLOUD_DISABLED`, …) — the
seam simply never populates the specific ones. **Grounding rejection is already a distinct reason
and must remain so.**

### 2.8 Keystore

`SecureCredentialStorage.getCredential()` `:63-69`:

```kotlin
} catch (_: Throwable) {
    null
}
```

Decrypt failure, invalid key state, corrupted ciphertext, base64 corruption and "no key stored"
all become `null`, so `hasCredential()` is false and Settings reports **"Not Configured"** — a
misleading claim for a key that exists but cannot be read. It also **swallows
`CancellationException`**, violating the project's no-swallowed-cancellation rule.

`testConnection()` also collapses categories (`:210-247`): `MODEL_UNAVAILABLE` exists for 404 but
no `FORBIDDEN` vs `UNAUTHORIZED` vs `REQUEST_SCHEMA_ERROR` distinction; `ConnectionTestStatus`
lacks `KEYSTORE_ERROR` entirely.

### 2.9 Why this survived the existing test suite

`ByokGeminiProviderTest` covers the seam with **24 tests**, including
`directGeminiClientSeam_buildsValidGenerateContentJsonPayload` and
`agenticOrchestrator_withDirectGeminiSeam_executesDeterministicToolAndSynthesizesAnswer`.

But:
- the payload test asserts `system_instruction`, `function_declarations`, `contents.length()==3`,
  and `generationConfig.temperature` — it **never asserts any `role`, nor any `functionResponse`**;
- the orchestrator test drives the loop through an OkHttp **interceptor that returns HTTP 200
  unconditionally**, so an invalid request body is never rejected;
- **zero** occurrences of `functionResponse` exist anywhere under `app/src/test`.

So no test ever validates the outgoing second-turn wire contract. That is the coverage gap that
let a deterministic production 400 ship.

---

## 3. AUDIT MATRIX — GEMINI TRACK (all: implementation REQUIRED)

| ID | Defect | Location | Minimal fix | Planned regression test |
|---|---|---|---|---|
| G-1 | `role:"function"` invalid on tool-result turn | `DirectGeminiClientSeam.kt:302` | emit `role:"user"` carrying `functionResponse` parts | wire-contract test: every emitted role ∈ {user, model}; functionResponse present |
| G-2 | `thoughtSignature` dropped | `DirectGeminiClientSeam` parse + build; `ToolCall` | capture verbatim onto `ToolCall`, replay on the model turn part | signature round-trip test (model part → next request byte-identical) |
| G-3 | invented function-call IDs | `:431` | stop synthesizing `UUID.randomUUID()`; keep provider id or none | ID-correlation test |
| G-4 | 400/401/403/404/429/5xx/timeout all → OFFLINE | `:463-484` → `CloudLlmConversationEngine:456,631-646` | map to distinct `CloudDegradationReason` | HTTP-status classification tests (M,N,O,P,Q) |
| G-5 | keystore failures masquerade as "no key"; cancellation swallowed | `SecureCredentialStorage.kt:63-69` | typed `KEY_NOT_CONFIGURED` / `KEY_READ_FAILED` / `KEY_DECRYPT_FAILED`; rethrow `CancellationException` | keystore classification test (R) |
| G-6 | `testConnection` over-claims ("● Connected") and cannot see tool-loop breakage | `:169-257`, `AiSettingsDialog.kt:405-412` | keep minimal ping but label it connectivity-only; reuse production model/endpoint/key/stack | contract test that both paths share key/model/endpoint/stack |

Explicitly **unchanged**: `gemini-3.8-flash`; `EngineeringClaimGuard` and the grounding rule;
`CloudDegradationReason.GROUNDING_VALIDATION_FAILED` staying distinct from network failure; the
P1-4 ~40 s budget (failure here is a deterministic 400, not latency — §14 says do not increase
without evidence); the P0/P1 architecture.

---

## 4. BATCH 4 P2 SCOPE — RECOVERED AND AUDITED (NOT IMPLEMENTED THIS PASS)

Scope recovered verbatim from `VOLTATLAS_REMEDIATION_PLAN.md` (P2-1 … P2-19).

| ID | Finding | Audit result |
|---|---|---|
| P2-1 | quick/engine duplication (AB-B01..B27) | **STILL PRESENT** — `QuickNewToolsModels.kt` retains independent `QuickEarthing/ConduitFill/BreakerSelect/Generator/Ups/Ats` engines vs `engine/*` |
| P2-2 | shared helpers: icm n-factor, η, DF defaults, Icm | **STILL PRESENT** (quick engine uses blanket ×2.1; generator η/DF diverge) |
| P2-3 | single breaker parser / fallback↔asset parity | **STILL PRESENT** — `FALLBACK_BREAKERS` divergence |
| P2-4 | shared `TransformerDefaults` (C-05) | **STILL PRESENT** — import 5.0/7.0 vs createDefault 6.0/6.0 |
| P2-5 … P2-19 | provenance, DXF/PDF parity, panel schedule, `isODApproximated`, identity copy, backup semantics, comments, PDF earthing, `DEFAULT_PROJECT` gating, unscoped flows, catalog-gated nav, R8, signing guard, UI behaviour, doc drift | **NOT AUDITED IN DEPTH THIS PASS** — deferred |

**Why deferred:** P2-1..P2-4 are an architectural consolidation touching the deterministic
calculation surface. Doing that in the same batch as a protocol fix would breach the "smallest
possible surface", "do not perform unrelated architecture refactor", and "do not regress P0/P1"
rules — and any regression there would be an **engineering-integrity** regression, not a cosmetic
one. They require their own authorized pass with parity/equivalence suites.

---

## 5. STOP CONDITIONS / UNCERTAINTY DISCLOSED

1. **No live provider call was made.** I cannot prove from this environment that the user's
   specific key/project reaches `gemini-3.8-flash`. The protocol defect is proven from the code +
   official schema and is *sufficient on its own* to produce the reported symptom; the live probe
   should confirm after the fix.
2. **"Reason the user sees OFFLINE" is the tool-result turn 400**, per §2.2–2.3. If, after G-1/G-2
   land, a live request still fails, the seam's new per-status logging (§6) will name the exact
   cause rather than "OFFLINE".
3. **P2-1..P2-19 remain open.** No claim of completion is made for them.
4. `NEW-1` (`NameplateOcrProcessor` `Bitmap.Config.HARDWARE` at minSdk 24) untouched — documented debt.
5. `ConduitFillEngine` protected material-filter defect (P1-8) untouched — characterization test remains.

---

## 6. RECOMMENDED NEXT STEP (on authorization)

Implement G-1 → G-6 in that order, each with its own regression test, verifying with:
focused AI tests → full `-Pprofile=ai` → full `-Pprofile=fast` → `:app:lintDebug` →
`:app:assembleDebug`. Then re-run this audit's protected-file fingerprint comparison.
