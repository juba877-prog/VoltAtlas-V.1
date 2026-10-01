# VOLATLAS_FINAL_AUDIT_REPORT.md

**Audit:** VOLTATLAS full re-audit + engineering integrity hardening (Phases 0–5)
**Date:** 2026-09-30
**Repo:** `E:\voltatlas-v.1 (4)` — branch `main`, HEAD `abd3110`
**Outcome:** Audit COMPLETE · Remediation PLAN CREATED · Implementation NOT STARTED (stopped by user order after audit)

---

## 1. Executive summary
Independent re-audit of the full engineering path (UI → ViewModel → Repository → Room → Catalog → Engine → SLD → Reports/Exports → AI Tools → AI Responses) found **150+ findings across 23 audit streams (A–W)**, including **fabricated breaker results, hardcoded SLD voltages contradicting computed values, silent first-instance topology selection, forced 230 V/630 A earthing basis, two cross-project data leaks, and a Main-thread `runBlocking` in the AI pipeline**. All prior-audit hypotheses were re-verified against the repository, not inherited. Deterministic calculation authority, mutation approval, grounded-response validation, and revision gating were confirmed intact. No production code was modified.

## 2. Scope
- 350 main Kotlin files / 281 test / 2 androidTest; 8 test profiles; 3 protected files.
- Audits A–W: breaker fallback · quick engines · defaults · SLD hardcodes · SLD topology · EngineeringViewModel · earthing source-of-truth · cable dimensions · catalogs · AI runtime · runBlocking · Nano · Firebase stub · hardcode-test scope · project isolation · identity · backup · release safety · assistant UX · outputs · size · dead code · doc drift.

## 3. Method
- Phase 0 baseline recorded (`VOLATLAS_PRE_AUDIT_BASELINE.md`), then baseline tests executed pre-modification.
- Parallel read-only explore subagents per audit pair (no write access → findings returned inline; truncated tails recovered from tool-output files).
- Environment workaround: ripgrep-based glob/grep unusable → Python scripts + `git grep` + `read`; exhaustive negative claims downgraded to PARTIALLY_VERIFIED.
- Findings consolidated into `VOLATLAS_AUDIT_MATRIX.md` (15 mandated fields each).

## 4. Environment & tool health
| Tool | Status |
|---|---|
| `glob`/`grep` (ripgrep) | BROKEN ("ripgrep execution failed") → Python/git-grep/read used |
| Shell quoting / PowerShell | Mangled / non-functional → Python scripts from temp dir |
| Gradle | WORKING (9.7.1, JDK 21, config cache) |
| Lint | **NOT_RUN** (no report exists; `checkReleaseBuilds=false`) |

## 5. Build & test matrix (executed)
| Profile | Execution | Result |
|---|---|---|
| fast | EXECUTED (pre-modification) | **BUILD SUCCESSFUL** — 200 suites / 1477 tests / **0 failures** / 0 errors / 2 skipped |
| gate | test task **FROM-CACHE** (outputs restored, not re-executed this session) | **BUILD SUCCESSFUL** — 285 suites / **2641 tests / 0 failures / 0 errors / 14 skipped** |
| ai / full / diagnostic / stress | **NOT EXECUTED** (halted with stop order) | BUILD_NOT_EXECUTED |
| lintDebug | **NOT RUN** | NOT_RUN |
| assembleDebug / assembleRelease | NOT RUN | Release packaging intentionally BLOCKED (debug keystore guard working — by design) |

## 6. Source-of-truth verdict
See `VOLATLAS_SOURCE_OF_TRUTH_MATRIX.md`. **7 of 7 quick engines duplicate authoritative engines with formula/default drift**; catalogs have two parsers and drifting fallbacks; SLD labels diverge from entity data; earthing inputs are silently forced. Single-sources that ARE intact: deterministic tool path, revision CAS, mutation gateway, grounded validator, dispatcher project/revision fail-closed.

## 7. Catalog verdict
Canonical assets exist and load (`CatalogLoaderTest`). Defects: `FALLBACK_BREAKERS` (wrong Icu/Ics/Icm, nonexistent codes), `FALLBACK_CONDUITS` (missing 2 rows), conduits/busways not validated (I-01), duplicate breaker parser (I-04), UI placeholders (I-09). No test ties in-code tables to assets (I-08).

## 8. Engine protection audit
All three protected files untouched; `com.example.engine.*` untouched. Two engine defects identified and **BLOCKED BY PROTECTED ENGINE RULE**: conduit type-filter no-op (I-03/AB-B09) and device breaking-capacity defaults (I-05) → remediation routed to call-sites (P1-8).

## 9. Top defects (P0)
1. **Fabricated 36 kA breaker result** (A-01/A-02) — reachable, renders fake Icu/Ics/Icm as fact.
2. **Pole filter returns 3P for 4P** (A-04) — wrong safety device.
3. **SLD voltage labels ignore `voltageV`** (D-01/T1) — 415 V circuit shown as 400 V while current uses 415 V.
4. **Guardless divide → Inf/NaN into BusbarSizing** (D-08); **literal " PASS" verdict** (D-12); **Ib fallback 1.44 fabrication** (D-07).
5. **First-instance SLD topology** (E-01..E-07); **busTies never passed** (E-08) — validation dead in production.
6. **Earthing forced U0=230 V / mainBreaker=630 / 400 V** (C-01..C-03, Audit G) — verdicts on unstated basis.
7. **Import defaults off by ~159×** (C-04).
8. **Cross-project leaks**: auto-design cache (F-02/O-01) and assistant chat/cards (O-02/S-11).
9. **Vacuous isolation test** `assertTrue(true)` (O-03/F-03).

## 10. Top risks (P1)
`runBlocking` on Main (K-01) · dead Nano route presented as available (L-01) · unguarded echo stub (M-01) · unreachable 30 s budget (J-01) · lint NOT_RUN (R-1) · unvalidated catalog tables (I-01) · orphaned bus_ties (O-06) · fast profile skips all AI safety tests (J-10).

## 11. AI safety architecture verdict
VERIFIED WORKING: canonical entry point, deterministic tool registry+dispatcher, project/revision fail-closed, cancellation/timeout seams, tool budget, duplicate-call detection, grounded response validator, engineering-claim guard, single mutation approval gateway, durable receipts, explicit tool-request extraction. LLM performs **no** engineering computation anywhere audited. Weaknesses: budget/timeout interaction (J-01), over-rejection (J-02), composition unwired by tests (J-03), Main-thread blocking seam (K-01).

## 12. Assistant UX verdict
Claimed behaviors (no auto-keyboard, Back-vs-keyboard, suggestion lifecycle/no stacking, scrolling, deterministic states, mode switching) all VERIFIED except **project-context sync (S-11)**. Layout polish findings S-03/07/09/12/13/14 → P2.

## 13. Outputs verdict
SLD/DXF/PDF share one computation but diverge in **presentation**: hardcoded/contradictory labels (T1/T2), DXF drops verdicts (T3), PDF prints 0.0 where UI shows "—" (T5), provenance overwritten (D-14). AI cable tool proven consistent (T8 — DISPROVEN claim).

## 14. Security/release verdict
No key in APK; cloud gated off; debug-keystore release refusal working; credential storage Keystore-backed. Findings: lint disabled (R-1), broad R8 keeps (R-5), "securely stored" claim overstated (P-3), identity = attribution not authentication (P, ATTRIBUTION), backup semantics undocumented (Q-2/Q-3).

## 15. Size & dead code
24 files ≥1000 lines; incremental-only refactor guidance per file (`VOLATLAS_DEAD_CODE_AUDIT.md`); 10 dead-code candidates catalogued, **none deleted** (missing exhaustive search tooling + stop order).

## 16. Documentation drift
8 confirmed doc-vs-code mismatches (hub "8 tools", bridge "zero duplicate tables", label-field library, ModePolicy Phase-7 comment, Nano KDoc, TrustedPrincipal KDoc, builder comment above hardcoded line, Firebase stub) → P2/P3 with truth tests.

## 17. DISPROVEN items
F-06 (auto-design jank) · O-05 (coordinator cache leak) · O-08 (AI cross-project tools) · O-09 (import merge leak) · S-04 (suggestion stacking) · T8 (AI/UI computation divergence) · backup-exclusion concerns (Q-1 correct) · double-caching/M5 concerns (intentional revision-gated).

## 18. INTENTIONAL items (allow-list)
`flowOn(Dispatchers.Default)` (F-04 — **preserve**) · revision gating · approval gateway · standard size ladders · quick fallback mechanism (mechanism yes, content no) · motor-starter standalone · AI-scoped hardcode scanner · attribution identity · no-key BuildConfig · assistant keyboard/scroll behaviors.

## 19. Hypothesis disposition (prior audits)
VERIFIED: A (breaker), B (quick drift), C (defaults), D (hardcodes), E (first-instance), G (earthing force), H (presentation risk only), I (catalog gaps), J (controls good + 3 gaps), K (runBlocking), L (Nano dead), M (stub), N (scope intentional + gap), O (2 leaks + stub test + 3 disproven), P (attribution), R (lint not run), S (14/15), T (presentation divergence), U/V/W (drift).
DISPROVEN: F-06, O-05/08/09, S-04, T8.

## 20. Test strategy
Every P0 fix gets a focused regression test; prefer real Room/catalog/engine/dispatcher fixtures over mocks (Batch 1 + `ImportExportTest` templates exist). Parity suite across all 7 quick/engine pairs. No test weakening; quarantined suites kept (fix → re-enable).

## 21. Remediation gate
`VOLATLAS_REMEDIATION_PLAN.md`: **15 P0**, **12 P1**, **19 P2**, **12 P3** items. Conditional GO for P0+P1. **Implementation not started.**

## 22. Files created
`VOLATLAS_PRE_AUDIT_BASELINE.md` · `VOLATLAS_AUDIT_MATRIX.md` · `VOLATLAS_SOURCE_OF_TRUTH_MATRIX.md` · `VOLATLAS_DEAD_CODE_AUDIT.md` · `VOLATLAS_REMEDIATION_PLAN.md` · `VOLATLAS_FINAL_AUDIT_REPORT.md` (+ `VOLATLAS_POST_AUDIT.md` = NOT APPLICABLE, see §24).

## 23. Residual limitations
- Dead-code/credential/unscoped-flow exhaustive negatives = PARTIALLY_VERIFIED (search tooling).
- ai/full/diagnostic/stress profiles + lint + assembles not executed.
- gate results FROM-CACHE (restored outputs, valid inputs, not re-run in-session).
- Motor-starter engineering bases uncited in-repo (flagged, not numbered).

## 24. POST_AUDIT status
`VOLATLAS_POST_AUDIT.md` written as a NOT_APPLICABLE stub: static re-audit of fixes is impossible because no fixes exist yet (implementation halted by stop order). It must be produced after Phase 6.
