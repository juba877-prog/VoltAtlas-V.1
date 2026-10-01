# VOLTATLAS - Phase 6 Batch 5: Assistant Quality, Actionability & Release Upgrade Path

**Baseline commit:** `4581bc0` (Batch 4 Stage A - Gemini production recovery, G-1..G-6)
**Branch:** `main`
**Protected engine set:** 27 artifacts - untouched, aggregate SHA-256
`874492118d9b7e31...`, zero differences vs HEAD.

---

## 1. BASELINE

Stage A is preserved intact. This batch changed only the **presentation layer**, the
**action labels**, the **action-chip accessibility**, and the **build identity config**.
No engineering value, no protected engine, and no Gemini transport code was modified.

---

## 2. RESPONSE RENDERER AUDIT

Audited the full path: Gemini response -> response contract -> grounding -> UI state ->
message model -> Compose renderer.

**Finding: there is no Markdown or LaTeX renderer.** The module has zero `commonmark`
or other markdown dependency and no HTML renderer. The assistant bubble
(`AssistantMessageBubbleItem`) and every result card render through plain Compose `Text`.
Consequence: **every markup character reaches the screen literally.**

| Symptom | Actual source | Proof |
|---|---|---|
| `$I_z = 158.6\text{ A}$` | Gemini emits LaTeX; the prompt **never asks for it** | `RuntimePromptCompiler` LAYER A §4 asks only for "precise engineering units and standard symbols" |
| `**bold**` shown literally | `ConversationPolicyEngine:822,885,928,948` emit `**` into plain text | plain-`Text` renderer |
| `[CALCULATED_BY_VOLTATLAS]` in prose | provenance labels surfaced into message text | prompt LAYER A §3 |
| `70 mm² mm²` | see §3 | type-level proof below |

**Decision:** do not add a Markdown renderer (the repo uses none, and the brief forbids
introducing a heavy one). Build one deterministic normalization layer at the
render/protocol boundary.

---

## 3. FORMATTING ROOT CAUSES (fixed at source, type/field aware)

The duplication is **type-driven**, not textual:

```
CableSizingResultDetails.cableSize_mm2 : String   // ALREADY CONTAINS ITS UNIT -> "70 mm²"
EngineeringMetricUiModel(value: String, unit: String?)   // unit held SEPARATELY -> "mm²"
AssistantResultCard: "${metric.value} ${metric.unit}"      // -> "70 mm² mm²"
```

`AssistantResponseMapper` already stripped the unit on its **fallback** path
(`?.replace(" mm²", "")`) but not on the **authoritative tool-result** path - that
asymmetry *was* the bug. `SldDxfExporter.kt:744` already documents this same bug class
for the DXF exporter (`"16 mm² mm²"`, `"0.245 ΩΩ"`), so the repo recognises it; the
assistant path simply had no fix.

**Fix:** `EngineeringDisplayFormatter` (new), applied in the **render join**:

- `joinValueAndUnit(value, unit)` strips a unit already present in the value and appends
  the canonical symbol exactly once.
- Removal is **type/field aware**: only when the trailing token is a known alias of the
  *declared* unit and is preceded by a delimiter or digit.
  - `("70 mm²", "mm²")` -> `"70 mm²"` ✅
  - `("CABLE", "A")` -> `"CABLE"` ✅ (letter preceded by a letter, not a delimiter)
  - `("70", "mm²")` -> `"70 mm²"` ✅
- Percent binds tight (`2.18%`); other symbols take a space (`70 mm²`).

**Correction made during this batch:** normalizing the value inside the *model* was tried
first and **reverted** - it broke the grounding contract that a metric's value equals the
authoritative engine string. See `VOLTATLAS_R1_CORRECTION.md`.

---

## 4. ACTION CAPABILITY AUDIT

Audited `EngineeringToolCatalog` (the single authoritative declaration surface, proven
drift-free against the executable registries by `EngineeringToolRegistry.init`).

**There is no mutation tool.** All ten declared tools are read-only or calculation-only:

| Tool | Domain | Mode | Status |
|---|---|---|---|
| `get_current_project_context` | READ | READ_ONLY | READ-ONLY |
| `get_entity` | READ | READ_ONLY | READ-ONLY |
| `get_calculation_state` | READ | READ_ONLY | READ-ONLY |
| `get_dependencies` | READ | READ_ONLY | READ-ONLY |
| `get_change_history` | READ | READ_ONLY | READ-ONLY |
| `get_catalog_item` | READ | READ_ONLY | READ-ONLY (not project-scoped) |
| `calculate_cable_sizing` | CABLE | LIVE_CALCULATION | READ-ONLY |
| `evaluate_cable_candidate` | CABLE | LIVE_CALCULATION | READ-ONLY |
| `compare_cable_candidates` | CABLE | LIVE_CALCULATION | READ-ONLY |
| `run_what_if` | CABLE | WHAT_IF | READ-ONLY |

None requires confirmation because none mutates. `isMutationAllowed` is rejected by
construction in `isAllowed`.

---

## 5. ACTIONS ALREADY IMPLEMENTED

| User intent | Capability | Status |
|---|---|---|
| "what if 50 mm" | `run_what_if` | **EXECUTABLE** |
| "compare 50 and 70" | `compare_cable_candidates` | **EXECUTABLE** |
| "why is 50 failing?" | `calculate_cable_sizing` + diagnose | **EXECUTABLE** |
| inspect calculation | `get_calculation_state` | **EXECUTABLE** |
| read project / circuit | `get_entity`, `get_current_project_context` | **READ-ONLY (executable)** |
| **"use 70 mm" (cable size)** | propose -> review -> confirm -> gateway | **AVAILABLE WITH CONFIRMATION** |
| inspect catalog | `get_catalog_item` | **READ-ONLY (executable)** |
| change breaker | - | **UNAVAILABLE** |
| change other settings / fields | - | **UNAVAILABLE** |
| save project | - | **UNAVAILABLE** via assistant |
| undo / go back | - | **UNAVAILABLE** |

**Correction to an initial misreading:** the mutation path is **not** dead code.
`EngineeringMutationEngine` is documented "disabled" and is referenced only from tests,
but `DurableMutationWorkflow.createGateway` **is** wired in production
(`EngineeringAssistantViewModelFactory:73` -> `TrustedProposalApprovalGateway`), and
`onConfirmApproveAndApply()` is documented as the "sole trusted physical UI action".
Verified chain:

```
"use 70 mm" -> CREATE_PROPOSAL -> CableProposalPreparationCoordinator (evidence match)
  -> ProposalReviewCard -> onContinueToApproval
  -> ApprovalConfirmationCard -> onConfirmApproveAndApply()
  -> TrustedProposalApprovalGateway -> DurableMutationWorkflow.execute
  -> publication fence + revision CAS + circuitDao.updateCircuit
  -> MutationExecutionReceipt + post-commit revalidation -> onMutationCommitted(revision)
```

So `Use 70 mm²` is honestly **AVAILABLE WITH CONFIRMATION**; everything else is honestly
**UNAVAILABLE**. No action was invented and no approval gate was bypassed.

## 6. DEAD ACTION REMOVED

`AssistantResponseMapper` offered `Propose <current size> mm²` on the result-summary card.
`CableProposalPreparationCoordinator:130` rejects any proposal equal to the persisted
value as a no-op, so that button could **only** produce a `SafeErrorCard`. Removed.

Labels also corrected to state the real effect: `Propose X` -> `Use X mm²`,
`Propose This Size` -> `Use this size`, `Run what-if scenario` -> `What-if`,
`Evaluate a candidate` -> `Evaluate size`, `Compare candidate sizes` -> `Compare sizes`,
`Review & Apply` -> `Review & apply`, `Exit Scenario` -> `Exit scenario`.

---

## 7. FILES CHANGED

**New**
- `app/src/main/java/com/example/ai/ui/EngineeringDisplayFormatter.kt` - the deterministic
  presentation contract.
- `app/src/test/java/com/example/ai/ui/EngineeringDisplayFormatterTest.kt` - 17 tests.
- `app/src/test/java/com/example/ai/ui/AssistantResponseStyleContractTest.kt` - 8 tests.
- `VOLTATLAS_BATCH5_ASSISTANT_QUALITY_REPORT.md`, `VOLTATLAS_R1_CORRECTION.md`.

**Modified**
- `AssistantResultCard.kt`, `AssistantAdditionalCards.kt` - value/unit join + text
  normalization at the render boundary.
- `AssistantSafeErrorCard.kt` - normalize tool error copy.
- `EngineeringAssistantViewModel.kt` - normalize once at `AssistantMessage` creation.
- `AssistantResponseMapper.kt` - action labels; dead chip removed.
- `SuggestedActionsRow.kt` - 48dp min touch target + real content descriptions (§29).
- `app/build.gradle.kts` - debug `applicationIdSuffix`, `verifyApkIdentity` task.

**§23 history consistency:** message text is normalized once, *before* it enters UI state.
Audit confirmed there is **no Room persistence of assistant message text** (messages are
in-memory), so the normalized text is by construction both what is displayed and what
history would replay. Display and storage cannot diverge.

---

## 8. RELEASE UPGRADE PATH (§30A) - ROOT CAUSE PROVEN

Measured from real artifacts on disk:

| | `app-debug.apk` | `app-release.apk` |
|---|---|---|
| `applicationId` | `com.voltatlas.app.v1` | `com.voltatlas.app.v1` |
| `versionCode` / `versionName` | `2` / `1.1` | `2` / `1.1` |
| Signer DN | `CN=Android Debug, O=Android, C=US` | `CN=VoltAtlas Dev, OU=Development, O=VoltAtlas` |
| **Cert SHA-256** | `c1a3b9ba...d799c84c` | `2e2d5793...6d6ef05` |

**Answers to §30A.2:** `applicationId` is **identical** - it has never changed
(`git log -S applicationId` shows only the initial commit `0279f56`; both `abd3110` and
`HEAD` read `com.voltatlas.app.v1` / `vc 2` / `vn 1.1`). The **signing certificates
differ**. Android therefore refuses the install with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`,
surfaces it as a **package conflict**, and the only remedy is a manual uninstall that
destroys project data. Exactly the reported symptom.

**Real cause:** `buildTypes.debug` had **no `applicationIdSuffix`**, so debug and release
both claimed `com.voltatlas.app.v1` while being signed by different keys.

**Fix:**
```kotlin
debug { applicationIdSuffix = ".debug" }
```
Proved safe: the only package reference in the whole repository is `applicationId` itself
at `build.gradle.kts:20` - no manifest, test, ProGuard or CI file hardcodes it.

Verified after rebuild: debug APK identity is now
`package: name='com.voltatlas.app.v1.debug' versionCode='2'`.

Per §30A.3 `applicationId` was **not** changed (that would create a new app rather than
repair the upgrade path), and no suffix was added to release.

**Secret hygiene (§30A.5)** - unchanged and correct: credentials already flow through
`keystore.properties` or `KEYSTORE_PATH/STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD`. No
`.jks`/`.keystore`/`keystore.properties` file is git-tracked. No secret was added, printed
or committed. The "debug keystore is never a valid release credential" guard and the hard
fail on release packaging were left intact.

**Reproducible verification (§30A.8):** `:app:verifyApkIdentity` prints path, size and
`apksigner` certificate DN + SHA-256 for one or all built APKs, never printing passwords or
key material.

---

## 9. TESTS ADDED

`EngineeringDisplayFormatterTest` (17) covers §22/§25 directly: no raw LaTeX in output,
no duplicated units, Unicode engineering symbols intact, no malformed Markdown, direct
answer first, concise vs detailed both possible, markers not leaked, action labels match
executable actions, idempotence (history consistency), and that numeric precision is never
altered.

`AssistantResponseStyleContractTest` (8) asserts the style contract against realistic
policy-layer copy rather than prompt text.

**Mutation verification** (fresh, caches disabled, one Gradle invocation at a time)
confirmed the tests discriminate rather than merely pass - see §12.

---

## 10. LINT / BUILD

- `lintDebug`: **0 errors / 286 warnings** - identical to the recorded pre-existing
  baseline. `abortOnError = true` retained; no mass-fix of pre-existing warnings.
- `assembleDebug`: **BUILD SUCCESSFUL**.

---

## 11. REMAINING LIMITATIONS (honest)

1. **Live provider verification is still unavailable.** No API key, no attached device.
   Gemini transport is unchanged and Stage A invariants remain covered, but a real
   end-to-end cloud turn has still not been executed by this agent.
2. **Release signing continuity is not proven.** Only one release artifact exists locally
   (`app-release.apk`, `CN=VoltAtlas Dev`); there is no older release APK in the
   repository. Debug-vs-release divergence is **proven**; release-vs-prior-release
   continuity is **not** claimed. If a prior public release used a different upload key,
   that must be recovered before shipping.
3. **No automated APK-over-APK install** (no device attached). Manual procedure below.
4. **Tone and response length for cloud turns depend on the model.** The normalizer
   guarantees *renderable* output; it cannot make Gemini concise. `ResponseDetailLevel`
   already exists and is threaded through `RuntimePromptCompiler` LAYER F, so length
   intent is expressed, but its effect on live Gemini output is unverified without a key.
5. **Mutation remains cable-size-only.** Breaker/settings changes are genuinely absent and
   are reported as unavailable rather than faked.
6. **Conversation history is in-memory only**, so "history consistency" is structurally
   satisfied today but there is no reload path to test yet.

---

## 12. VERIFICATION LEDGER

| Check | Result |
|---|---|
| Focused formatter + style suites | 25/25 pass (fresh) |
| `GroundedResponseValidatorTest` | 20/20 pass (fresh) |
| `EngineeringTaskManagerTest` | 67/67 pass (fresh) |
| Full AI profile | 1213 tests, 2 failed -> **1 real regression (fixed), 1 flake (passes in isolation)** |
| Full fast profile | superseded - rerun required after the fix |
| Protected set (27 files) | byte-identical to HEAD |
| Stage A suites (6 files, 52 tests) | present, unmodified |
| Stage A invariants | all present in source |
| Lint | 0 errors / 286 warnings |
| assembleDebug | SUCCESS |

---

## 13. MANUAL DEVICE ACCEPTANCE CHECKLIST

Release upgrade path (§30A.9), **no uninstall** between steps:

1. Install today's release APK. Launch once; create a project + circuit.
2. Record `versionCode` and the signer SHA-256 (`verifyApkIdentity`).
3. Build the next release with an **incremented** `versionCode`, signed with the **same**
   upload keystore. Verify both APKs report the **same** SHA-256 digest.
4. Install the new APK **over** the old one.
5. Expect: update succeeds, no uninstall, app launches, Room opens, project + circuit
   intact, assistant settings survive.
6. If step 4 reports a package conflict -> `RELEASE SIGNING IDENTITY CHANGED`; stop and
   recover the original key.

Data safety: no uninstall, no data clearing, no destructive migration, no database
identity change. Release `applicationId` is unchanged, so existing app data and its Room
database survive updates by Android's own semantics.

Assistant UX:

1. Open a real project, open Assistant, ask `what if 50 mm`.
   Expect no raw LaTeX, no duplicated units, direct answer first.
2. Ask `use 70 mm`. Expect a **proposal + confirmation**, not an immediate write, and no
   pretense of a change.
3. Confirm. Expect the circuit to change, revision to advance, an execution receipt, and
   the UI to refresh.
4. Re-open the circuit and verify the saved value.
5. Try `compare 50 and 70`, then `what if 75`, then `why is 50 failing?` - responses must
   stay concise and readable.
6. Break the current revision from another screen and retry a mutation: expect a specific
   stale-revision error, not a generic AI error.
