# VOLTATLAS - Phase 6 Batch 5 + ICU crash fix: final verification report

**Commits:** `35180af` (assistant quality + release identity) -> `d1b088a` (ICU regex crash)
**Branch:** `main`
**Protected engine set:** 27 artifacts, byte-identical to HEAD throughout.

---

## 1. HEADLINE

Three real defects were found and fixed in this batch. All three were proven from evidence,
not inference, and all three are now verified on a device.

| Defect | Evidence | Status |
|---|---|---|
| Raw LaTeX / duplicated units reaching the screen | `cableSize_mm2: String` carries its unit while `AssistantMetricUiModel.unit` holds it separately; renderer concatenated both | FIXED + device-verified |
| Release upgrade refused with "package conflict" | debug and release shared `applicationId` under different certificates | FIXED + upgrade proven on device |
| **App crashed on every assistant turn** | `PatternSyntaxException near index 84/86` on Android ICU | FIXED + user-verified on emulator |

---

## 2. THE CRASH (most serious; caused by this batch's own work)

### Symptom
Every assistant input killed the app immediately.

### Root cause
`EngineeringDisplayFormatter.TEXT_CMD` used the pattern:

```
\\(?:text|mathrm|...)\s*\{([^{}]*)}      (84 characters)
```

On Android, `java.util.regex.Pattern` **is** `com.android.icu.util.regex.PatternNative`
(proven from the device stack trace). ICU reads `\(` as a *literal* paren, so `(?:` can
never open a group -> `PatternSyntaxException`. Because the patterns were `val` properties on
an `object`, the failure occurred in the static initializer and became
`ExceptionInInitializerError`, killing the process on first use.

**Why tests missed it:** every JVM unit test ran OpenJDK's engine, which accepts the pattern.
The JVM is not a faithful oracle for Android regex.

### Second, subtler instance
After fixing the paren, the app still crashed - at index 86, the *new* pattern's exact length.
The remaining cause was a **trailing bare `}`**, which Java tolerates and ICU rejects. Fixed by
escaping every literal brace (`\{`, `\}`, `[^\{\}]`).

### Third-order fix (architecture)
Every pattern now compiles through `regexOrNever(...)`, which returns a never-matching
`Regex` instead of throwing. A presentation formatter can no longer take down the process,
whatever the engine decides.

### Regression tests added
- `EngineeringDisplayFormatterRegexPortabilityTest` (7) - replicates ICU's grouping rule on
  the JVM, plus a self-check that the checker itself rejects the original defect
- `EngineeringDisplayFormatterStressTest` (6) - long input, unterminated math,
  catastrophic-backtracking probes, timing bound
- Mutation-verified: reintroducing the crashing pattern fails 3 tests naming the bad pattern

---

## 3. RELEASE UPGRADE PATH (§30A)

### Root cause, proven from artifacts
Both variants declared `applicationId = com.voltatlas.app.v1` while being signed by
**different certificates**:

| | signer | SHA-256 |
|---|---|---|
| what was installed | `CN=Android Debug, O=Android` | `c1a3b9ba...` |
| genuine release | `CN=VoltAtlas Dev, O=VoltAtlas` | `2e2d5793...` |

The distributed "release" was in fact a **debug build installed under the production
applicationId**. Android refuses that (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, surfaced to users
as a package conflict); uninstall is the only remedy, which destroys project data.

### Fix
`debug { applicationIdSuffix = ".debug" }` - proven safe, the only package reference in the
repository is `applicationId` itself. `applicationId` was **not** changed.

### Upgrade verified on device
```
BEFORE   versionCode=2   firstInstallTime 15:15:15
INSTALL  Success
AFTER    versionCode=3   firstInstallTime 15:15:15   <- unchanged: no uninstall
```
Same package, same signer, higher versionCode, installed in place; app launched.

### One-time migration that remains
Any device still holding the **debug-signed** versionCode 2 will still refuse this release.
Different signature: no versionCode change can fix that. Those devices need one manual
uninstall. Every future release is upgrade-safe.

---

## 4. RESPONSE RENDERING

The module has **no Markdown or LaTeX library**; the assistant renders through plain Compose
`Text`, so every markup character reached the screen literally.

`EngineeringDisplayFormatter` now normalizes at the render boundary: math delimiters, LaTeX
commands, engineering subscripts (`I_z` -> `Iz`), unsupported Markdown, internal provenance
markers, and duplicated units. Unit handling is type/field aware - a unit is stripped only
when the trailing token is a known alias of the *declared* unit and is preceded by a
delimiter or digit, so `("CABLE", "A")` is untouched.

**Correction made during this batch:** normalizing the value inside the *model* was tried first
and reverted - it broke the grounding contract that a metric's value equals the authoritative
engine string. See `VOLTATLAS_R1_CORRECTION.md`.

---

## 5. ACTION CAPABILITY

All ten catalog tools are read-only or calculation-only; **there is no mutation tool**. The
mutation path is nonetheless wired in production
(`ViewModelFactory` -> `createGateway` -> `TrustedProposalApprovalGateway`), so:

| Intent | Status |
|---|---|
| what-if / compare / diagnose / read | **EXECUTABLE** |
| change cable size | **AVAILABLE WITH CONFIRMATION** |
| change breaker / settings / save / undo | **UNAVAILABLE** (reported, not faked) |

Removed a dead chip: "Propose \<current size\>" could only ever produce an error, since the
coordinator rejects proposals equal to the persisted value.

---

## 6. VERIFICATION LEDGER

| Check | Result |
|---|---|
| Full AI profile | **1228 tests, 0 failures** (fresh, caches disabled) |
| Full fast profile | **1542 tests, 0 failures**, 2 pre-existing `@Ignore` skips |
| Formatter + style + portability + stress | 38/38 |
| `assembleRelease` @ versionCode 3 | SUCCESS |
| Lint | **0 errors / 286 warnings** (unchanged baseline) |
| Dex binary check | ICU-safe pattern present, crashing pattern absent |
| Device upgrade v2 -> v3, no uninstall | **PROVEN** |
| Crash on assistant turn | **user-verified fixed on emulator** |
| Gemini agentic loop, live | 4x HTTP 200, 3 tool calls, `thoughtSignature` replayed, `FINAL_RESPONSE OK` |
| Protected set (27 files) | byte-identical |
| Secrets | none in any commit; keystore gitignored |

---

## 7. HONEST LIMITATIONS

1. **Data preservation across upgrade is NOT proven.** The emulator's database is empty, so the
   test proved nothing was lost, not that data survived. Needs a device with a real project.
2. **Other devices may still need one uninstall** (debug-signed v2, above).
3. **Mutation coverage partly unproven.** Seven mutants were re-run with honest
   DETECTED/SURVIVED/INCONCLUSIVE classification; any SURVIVED result is a real coverage gap
   in tests previously reported as passing.
4. **Tone/length on live Gemini output is unverified.** The normalizer guarantees renderable
   output, not concision. `ResponseDetailLevel` is threaded through the prompt but its live
   effect was not measured.
5. **Assistant history is in-memory only**, so display/storage consistency is satisfied by
   construction today but has no reload path to test yet.
6. **Mutation remains cable-size-only.** Breaker and settings changes genuinely do not exist.
