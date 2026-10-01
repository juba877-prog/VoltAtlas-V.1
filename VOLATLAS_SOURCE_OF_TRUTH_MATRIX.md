# VOLATLAS_SOURCE_OF_TRUTH_MATRIX.md

Phase 3 deliverable. Re-verified against the repository at HEAD `abd3110` during this audit session.
Every row names ONE authoritative source; divergent sources are flagged with the audit finding ID.

## 1. Catalog / data assets (canonical = `app/src/main/assets/`)

| Data | Canonical source | Parsers | Divergent copies | Finding |
|---|---|---|---|---|
| Circuit breakers | `assets/breaker_catalog.json` (52 rows, 935 lines) | `Catalog.loadAll` (Models.kt:132-140) + `CatalogRepository` (data/catalog:143-185) | `FALLBACK_BREAKERS` (QuickNewToolsModels:282, 13 rows, wrong capacities, nonexistent codes) | A-01, A-05, I-04, AB-B14 |
| Conduits | `assets/conduit_catalog.json` (21 rows) | `Catalog.loadAll` | `FALLBACK_CONDUITS` (QuickNewToolsModels:173, 19 rows, missing PVC_160/200) | I-02, AB-B11 |
| Cables | `assets/cable_catalog.json` | `Catalog.loadAll` + `CatalogRepository` | `CircuitOverrideUtils` placeholder manufacturers (UI-only, fails downstream) | I-09 |
| Cable dimensions | `assets/data_sheet.json` | `Catalog.loadAll` | `estimateSensibleWT` sanctioned estimation (flagged, bounded) | H2, H3 |
| Accessories | `assets/accessory_catalog.json` | `Catalog.loadAll` | — | — |
| Standard size ladders | IEC/DIN standards (documented in KDoc) | `STANDARD_GEN_SIZES`/`STANDARD_UPS_SIZES`/`STANDARD_ATS_SIZES` etc. | intentional by design | I-06 |

## 2. Engineering calculations (canonical = `com.example.engine.*` — PROTECTED)

| Value | Authoritative engine | Duplicate implementations | Divergence | Finding |
|---|---|---|---|---|
| Earthing (rods/ring/adiabatic/Zs) | `EarthingEngine` | `QuickEarthingEngine` (no ring path, off-by-one, different curve parse) | YES | AB-B01..B06 |
| Conduit fill | `ConduitFillEngine` | `QuickConduitFillEngine` (different count basis, overflow→lastOrNull) | YES | AB-B07..B11, I-03 |
| Cable sizing / device select | `CableSizingEngine` | `QuickBreakerSelectEngine` (Icu in selection, fabricated result) | YES | A-01..A-04, AB-B12..B14 |
| Generator sizing | `GeneratorEngine` | `QuickGeneratorEngine` (÷1.5 vs ÷0.8, η term, DF 1.0 vs 0.8) | YES | AB-B15..B18 |
| UPS sizing | `UpsEngine` | `QuickUpsEngine` (no load margin, different DoD basis) | YES | AB-B19..B21 |
| ATS | `AtsEngine` | `QuickAtsEngine` (stepped Icm vs 2.2, no DSF/growth) | YES | AB-B22, AB-B23 |
| Motor starter | NONE exists | `QuickMotorStarterEngine` (standalone by absence) | N/A | AB-B24 |
| Short-circuit icm n-factor | `ShortCircuitEngine` (2.10/2.20 tiered) | `QuickBreakerSelectEngine` blanket ×2.1 | YES | A-06 |
| Trip curve / Ia parse | `ProtectionEngine.parseTripCurve` | quick naive `when(curve)` | YES | AB-B04 |
| Panel schedule | `PanelScheduleEngine` (recursive) | `OtherEngines.generateSchedule` (flat, wired into `UiState.panels`) | YES (consumer inventory pending) | T6 |
| AI cable tool | → same coordinator → `CableSizingEngine` | consistent with UI | NO divergence (verified) | T8 |

## 3. Numeric single sources (value chains)

| Value | Derivation chain | Hardcode/divergence points | Finding |
|---|---|---|---|
| Circuit voltage (SLD label) | `CircuitEntity.voltageV` | **label** computed from `phases` only (builder:1275) while **current** uses voltageV (builder:1214) — contradiction | D-01, T1, D-13 |
| Circuit design current Ib | `SizingResult.Ib_A` (CableSizingEngine) | fallback `loadKVA×1.44` (builder:950) ignores voltage/phases/pf; second formula in SldScreen:743 | D-07, D-13 |
| Generator voltage | GeneratorEntity — **no voltage column** | forced 400 V (builder:198) | D-02, D-03 |
| Earth-loop U0 | project phase-neutral voltage | forced 230 V (EarthingEngine:24 default; ProtectionEngine:226 ADS) — never passed by VM | C-02, G |
| Earthing system voltage | project voltage | forced 400 V (VM:740) | C-01, G |
| Main breaker for earthing | project device — **no entity exists** | first-tx-FLC ?: 630 (VM:724) | C-03, G |
| SLD topology parentage | assignedAtsId/assignedGeneratorId/assignedUpsId/transformerId FKs | first-instance `firstOrNull()` + name-contains fallbacks (builder:1116, 1066, 771, 984) | E-01..E-07 |
| Bus ties | `BusTieEntity` | **never passed** by SldScreen (param absent) — dead in production | E-08 |
| Validation verdict (PASS/FAIL) | `vdPct` vs `maxVDpct`, engine statuses | SldCanvas feeder callout literal `" PASS"` (SldCanvas:420) | D-12 |
| Provenance strings | builder data blocks (PROJECT_INPUT / APPROVED_STANDARD) | SldScreen overwrites with VERIFIED_FORMULA (SldScreen:764) | D-14 |
| Transformer %Z/XR | TransformerEntity | import fallback 5.0/7.0 vs createDefault 6.0/6.0 | C-05 |
| Import defaults | Entity schema defaults | importProjectJson fallbacks diverge (kFactor 1.0 vs 159.0, pf .85 vs .8, etc.) | C-04, C-06 |
| AI answer grounding | deterministic tool results + grounded response validator | LLM never computes — single canonical path verified | J-controls |

## 4. Runtime single-sources (verified intact)

| Concern | Authoritative component | Evidence |
|---|---|---|
| AI conversation entry | `EngineeringAssistantViewModel.onUserSubmit:376` | canonical; mode routing per-turn |
| Tool execution | `EngineeringToolDispatcher` (projectId/revision overwritten, fail-closed) | J-controls, O-08 |
| Mutation approval | `TrustedProposalApprovalGateway` (single trusted instance) | J-controls |
| Revision consistency | `EngineeringCalculationCoordinator` revision-bounded CAS | F-05, ProjectPersistenceIntegrityTest |
| Project switching | `AppStateEntity.activeProjectId` (persistence) vs VM `open()` | two owners — dormant write path (O-11) |
| Catalog readiness | `Catalog.getDiagnostics()` | not surfaced to quick nav (A-03) |
| Dispatcher defaults | `Dispatchers.Default` via `.flowOn` in VM:832 | F-04 — PRESERVE |

## 5. Documentation vs code truth map

| Documented claim | Actual code | Finding |
|---|---|---|
| "Zero duplicate catalog tables" (QuickEngineeringDataBridge KDoc) | FALLBACK_BREAKERS/FALLBACK_CONDUITS shipped | I-07, AB-B25 |
| "exactly 8 Batch 1 tools" (QuickEngineeringHubScreen KDoc) | 15 items / 6 categories | AU-W-01 |
| SldLabelFieldLibrary populationSource entries (≥3 false) | builder implements grid fault/secondary/frame | D-10, AU-W-02 |
| "no hardcoded defaults in bodyCurrentA" (builder comment) | `voltageLabel from phases` hardcodes | T7, D-01 |
| TrustedPrincipalProvider "authenticated engineer" | user-entered plaintext text | P-1 |
| Settings "stored securely" | unencrypted Room + plaintext prefs | P-3 |
| ExecutionModePolicy "until Phase 7" | permanent false invariant | J-09, AU-W-03 |
| Nano "hardware-backed Gemini Nano" | keyword regex parser, no SDK | L-02, AU-W-04 |
| FirebaseAiServiceImpl "Phase 2 wiring" | never wired (Phase-1 stub) | M-01 |
