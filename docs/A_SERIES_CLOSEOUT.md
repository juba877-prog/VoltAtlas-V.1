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

## FINDINGS LOGGED (not all still open — closed entries carry their fix commit)

- ENG-28 — `isZeResolved` computed but never forwarded to `ProtectionEngine.calculateAds`
  (`EngineeringCalculationCoordinator.kt:1154`, `:1286`, `:1849`) → production ADS verdicts can
  never reach "✓ PASS". Depends on §12.2-a (r1/r2 nullable, DATA MISSING) being live before
  ENG-28's isZeResolved-forwarding fix lands, otherwise closing ENG-28 alone re-enables a false
  PASS via 0.0 loop.

- N1 — `QuickEngineeringDataBridge.kt:106-110` — `else -> entry.air`: any unrecognised installation
  string silently gets free-air ampacity (non-conservative silent default). **FIXED `8b1531e`**
  (2026-09-28): unsupported/blank installations are rejected with a typed failure / DATA MISSING and
  the air fallback no longer exists.
- N2 — `ContextResolver.kt:591-593` — AI slot still emits `"In Conduit"` (now fails loud, but the AI
  layer still offers an unsupported option).
- N3 — `CableSizingEngine.kt:347-357` — `ampacityOf(...) else -> entry.air.toDouble()`: same mechanism
  as N1, second site. An unrecognised installation silently takes the air ampacity column.
  **FIXED `7642b55`** (2026-09-28): the engine resolves the canonical installation family once and
  `ampacityOf` consumes that family with no Air catch-all; an unresolved family is rejected in
  `checkCandidate`.
- N6 — `CableSizingEngine.kt` `getDepthDerating` / `getSoilDerating` — the buried depth/soil gates
  keyed on the raw `isDirectBuried(...)` phrase while the ampacity column and grouping use the
  canonical family, so canonical `D2`/`Buried` silently skipped both derating factors
  (0.99 depth x 0.91 soil at the default inputs; overstating derated ampacity).
  **FIXED `2ae6a8c`** (2026-09-28): both gates now use the same canonical-family boundary as the rest
  of the engine. Found while qualifying N3 — it was not part of the original audit.
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
  also FAILS. Root cause (investigated 2026-09-28 as TST-23): `CableSizingEngine.kt:233`
  `Catalog.deratingData ?: return null` → `:549-557` `?: return failedResult(...)` → `:418 "✗ FAIL"`,
  while `EngineFixesTest.kt:74` asserts `"✓ PASS"`. The test's PASS depends on whether a predecessor
  populated `Catalog.deratingData` — `ProjectContextProviderTest.kt:155` and
  `ai/tools/cable/CableEngineeringToolsTest.kt:94` assign it and their `@After tearDown()` never
  restores it. (Supersedes the earlier `getTempDerating:151` hypothesis: `:151 ?: return 1.0` is not
  on the operative path.) The closeout baseline (`failed=2`, flaky class) no longer reproduces at HEAD:
  order-dependent state, not a code regression. Full mechanism + fix options under **TST-23** below.

- **TST-23** — global mutable `object Catalog` shared by every test in one JVM → cross-class state
  leakage. **Test-infra, not blocking, awaiting scheduling.** Options A/B/C proposed, **NONE selected —
  awaiting owner decision.**
  *Mechanism:* `Catalog` (`Models.kt:132`) is a process-wide Kotlin `object` singleton; mutable
  `var cableTypes/dataSheet/cableCatalog/breakers/accessories/conduits/busways` (`:133-139`),
  `var deratingData: JSONObject?` (`:140`), `var lastError` (`:178`). `app/build.gradle.kts:233-311`
  configures only profile `filter { include/excludeTestsMatching }` — **no `maxParallelForks`, no
  `forkEvery`, no explicit ordering** — so all tests run sequentially in one JVM and whatever a writer
  installs in `@Before`/`@BeforeClass` is visible to every later test. `CatalogStateResetRule`
  (`:69-93`) is explicitly *"snapshot on entry, restore on exit"*: it hands back exactly the state it
  found, so it stops leaks **out** but cannot sanitize state inherited **in**. `Catalog.resetForTesting()`
  (`Models.kt:430-442`) does hard-reset to empty, but only 2 test classes call it.
  *Scope (recomputed 2026-09-28, case-sensitive `git grep`):* **35 of 270** test classes reference
  `Catalog.` → **444 of 2662** `@Test` methods. Split: **28 writer** classes (assign a `Catalog.*`
  field), **6 reader-only**, plus the rule itself; **15** writers are rule-protected; 1 writer has
  neither `@After` nor the rule (`CatalogLoaderTest`, restores via `finally`).
  *Proven order-dependent:* `EngineFixesTest#testSinglePhaseNeutralSizing` — virgin-JVM isolation run
  2026-09-28 11:56 (`--tests com.example.EngineFixesTest` alone, no predecessor): `tests=5 failures=1`,
  `org.junit.ComparisonFailure at EngineFixesTest.kt:74`, `expected:<[✓ PASS]> but was:<[✗ FAIL]>`,
  gradle exit=1. Chain: `CableSizingEngine.kt:233` `Catalog.deratingData ?: return null` → `:554
  failedResult(...)` → `:418 "✗ FAIL"`. The test never writes `deratingData` (only `cableTypes`/
  `dataSheet`/`cableCatalog`), and `:151 ?: return 1.0` cannot emit a FAIL verdict, so a PASS requires a
  predecessor to have populated `Catalog.deratingData` — **deduction from the code path; no passing run
  has been observed.**
  *Same-shape readers (6 classes install conditionally, `if (Catalog.cableTypes.isEmpty())`, so they
  silently adopt a predecessor's catalog):* `MasterEngineeringStressTest`,
  `OperationalFreshnessIntegrationTest`, `CoordinatorInvocationMeasurementTest`,
  `CoordinatorNonBlockingExecutionTest`, `CoordinatorPassRevisionHoistingTest`, `RevisionCorrectnessTest`.
  *Options (proposed, none chosen, none implemented):*
  — **A** — explicit `@Before` install + `@After` reset in every class that writes `Catalog` (28
  classes). Small, localised, but relies on discipline for every future writer.
  — **B** — remove the global singleton: inject `Catalog`/pass state into the readers (21 production
  files, 129 refs in `app/src/main`). Root-cause fix, largest change, touches production signatures.
  — **C** — one global `TestRule` that snapshots all `Catalog` fields before every test and restores
  after every test, regardless of class. Single attachment point, no production change, but hides leaks
  rather than making each test self-contained.

- **TST-24** — `ProtectionScreen.kt:605` / `:599` (§12.2 display remainder): Safety Margin (605) /
  progress bar (599) render misleadingly on DATA MISSING rows (100%/0% instead of suppressed); NaN% on
  missing-Ze path pre-existing. **Display-only, no verdict-correctness impact. Logged 2026-09-28, not
  fixed, no priority assigned yet** (owner will prioritize later).

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
