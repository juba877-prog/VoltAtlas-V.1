# VoltAtlas — A-Series Closeout

Status: PARTIAL CLOSEOUT (owner-reviewed 2026-09-28)
9b67c62 / a7b096c accepted 2026-09-28 (2 unrelated pre-existing UI-test failures, see below).

## CLOSED (committed, tested, owner-reviewed)

- A1 `7f742a8` — grouping-derating prefix keys + fail loud on unknown installation types.
- A1-R `a7b096c` — exact-match `D1`/`D2` aliases; typed failure for unsupported installation methods;
  fabricated `"B1"` import default removed; test fixtures use production vocabulary.
- A3 `04330e3` — 20 degC conductor resistance (`r20`) as the max-fault basis; loud DATA MISSING on
  missing `r20`; `rUsed` (voltage-drop basis) untouched.
- A3-fu `2786c31` — gate every Isc consumer on status (Protection, mapper, ATS aggregate, Highest-Isc KPI).
- A3-fu2 `9b67c62` — item 2: per-ATS Isc scope, hierarchy-resolved feeding transformer (no first-in-map),
  engine-layer `AtsIscResolver`, DATA MISSING instead of partial max, honest upper-bound message.
- A5 `665c0ff` — `HierarchyResolver.resolveAncestors` (cycle/self-parent guard shared by all walkers).

## NOT STARTED (Group A remainder — backlog, not blocking)

- A4 — 100-iteration bulk-pass silent drop
- A6 — resolveCableType silent substitution
- A7 — belongsToTx double-count
- A8 — earthing c-factor basis inconsistency

## OPEN FINDINGS LOGGED (not fixed, not blocking)

- ENG-28 — `isZeResolved` computed but never forwarded to `ProtectionEngine.calculateAds`
  (`EngineeringCalculationCoordinator.kt:1154`, `:1286`, `:1849`) → production ADS verdicts can
  never reach "✓ PASS". Depends on §12.2-a (r1/r2 nullable, DATA MISSING) being live before
  ENG-28's isZeResolved-forwarding fix lands, otherwise closing ENG-28 alone re-enables a false
  PASS via 0.0 loop.

- N1 — `QuickEngineeringDataBridge.kt:106-110` — `else -> entry.air`: any unrecognised installation
  string silently gets free-air ampacity (non-conservative silent default).
- N2 — `ContextResolver.kt:591-593` — AI slot still emits `"In Conduit"` (now fails loud, but the AI
  layer still offers an unsupported option).
- N3 — `CableSizingEngine.kt:347-357` — `ampacityOf(...) else -> entry.air.toDouble()`: same mechanism
  as N1, second site. An unrecognised installation silently takes the air ampacity column.
- `§12.2` — `ProtectionEngine` `?: 0.0` on R20 (ADS loop understated) — HIGH. **FIXED as ENG-30,
  2026-09-28**: `r1`/`r2` nullable + `DATA MISSING` on catalog miss (§12.2-a), material- and
  insulation-aware ADS temperature factor (§12.2-b). Gate evidence under "Gate at ENG-30" below.
- Busway in a fault path -> DATA MISSING (new A3 failure mode; HELD for owner).
- VD 70-degC vs XLPE 90-degC temperature convention (owner-deferred).
- `Cable Tray` -> Air assumes perforated tray (unperforated/solid unverified) — mapping accepted 2026-09-28.
- `A1`/`A2`/`B1`/`B2`/`C` -> typed failure (no mapping) — accepted 2026-09-28 as a known limitation, not a defect.
- Room rows already stored with `"B1"` / `"In Conduit"` (possible migration).
- K_T transformer impedance correction missing (`ShortCircuitEngine.kt`).
- `cmax = 1.05` hard-coded (`ShortCircuitEngine.kt:85`).
- Generator / motor contribution absent from ATS Icw.
- Ancestor-walk `?: continue` silent segment skip.
- `CatalogRepository` fallback keys that can load non-20-degC values.
- `R_Cu70 get() = R_Cu20` misleading aliases (`Models.kt`, `CatalogModels.kt`).
- `EngineeringTaskManagerTest#observeActiveTask_switchesAtomicallyToSupersedingTask` — failed at
  `a312b15`, 20/20 PASS on `a7b096c`. Not reproduced; monitor.
- `EngineFixesTest#testSinglePhaseNeutralSizing` — **pre-existing at `e45c950`, not ENG-30**. Verified
  2026-09-28 with a clean-tree control run: full gate at HEAD = `discovered=2597 failed=1` (this test),
  full gate with ENG-30 = `discovered=2601 failed=1` (same test) → **0 new failures**; isolated at HEAD
  also FAILS. Root cause (not investigated further, per rule): `CableSizingEngine.getTempDerating:151`
  returns `1.0` when `Catalog.deratingData == null`, so this test's PASS depends on whether a leaking
  class ran first — `ProjectContextProviderTest.kt:155` and `ai/tools/cable/CableEngineeringToolsTest.kt:94`
  assign `Catalog.deratingData` and their `@After tearDown()` never restores it. The closeout baseline
  (`failed=2`, flaky class) no longer reproduces at HEAD: order-dependent state, not a code regression.

## FLAKY — classified 2026-09-28, not a gate blocker

- `com.example.ai.ui.AssistantConversationScrollBehaviorTest` — 2 failures in the full gate run
  (`clickingIndicator_returnsToTheNewestContentAndHidesItself`,
  `atLatest_newResponseAutoScrollsToNewestContent`), `java.lang.IllegalArgumentException at
  SubcomposeLayout.kt:453`.
  **3/3 isolated re-runs PASS** (5/5 tests, 0 failures each). Classified **FLAKY** — Compose/Robolectric
  test-idling, consistent with TST-16 / TST-21.
  Not A-series: file added in `480288c` (2026-09-27) and unchanged since; no source file changed
  since `a7b096c`.

## Engineering basis (rules that survive this series)

1. Max-fault path uses 20 degC line resistance (`r20`); voltage drop keeps its own basis.
2. Ampacity catalog has exactly three families: Duct, Buried, Air(/Tray). IEC codes map exactly:
   `D1` -> Duct, `D2` -> Buried. Conduit/trunking/`A1`/`A2`/`B1`/`B2`/`C`/`In Ground` have no mapping
   and fail typed. Blank installation (`""`) fails as "not specified", never defaulted.
3. A DATA MISSING number is never used as a real value, never averaged or maxed in, never presented
   as if complete. ATS needs a resolved fault level or it is DATA MISSING.
4. Import/export round-trips preserve room state; a missing key is reported by circuit name/id/count.

## Gate at closeout

`discovered=2597 passed=2581 skipped=14 failed=2`

Single daemon, 6m33s, XML timestamps 2026-09-28 09:02:03, no infrastructure error. The 2 failures are
the flaky class above. Isolated re-runs of that class: **BUILD SUCCESSFUL** on all 3 attempts
(`--tests com.example.ai.ui.AssistantConversationScrollBehaviorTest -Pprofile=gate --rerun`).

## Gate at ENG-30 (2026-09-28)

With ENG-30: `discovered=2601 passed=2586 skipped=14 failed=1`.
Control run the same morning on a **clean `e45c950` tree**: `discovered=2597 passed=2582 skipped=14
failed=1` — the *same* `EngineFixesTest#testSinglePhaseNeutralSizing`. **0 new failures from ENG-30**
(the 4 new §12.2 tests pass).

The old baseline `failed=2` (flaky class) no longer reproduces: that class passed in both runs, and
the EngineFixesTest failure above took its place. It is order-dependent global `Catalog.deratingData`
state (logged under OPEN FINDINGS), not a regression — proven by the clean-tree control run.
