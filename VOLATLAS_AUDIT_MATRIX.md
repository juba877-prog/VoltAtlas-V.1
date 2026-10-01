# VOLATLAS_AUDIT_MATRIX.md

Phase 1 deliverable. Every finding re-verified against the current repository (branch `main`, HEAD `abd3110`) during this audit session. Classifications use the mandated vocabulary. Column key (15 mandated fields):
**ID | Class | File | Function | Lines | Observed | Expected | SoT (source of truth) | Evidence | Existing tests | Missing tests | Sev | Remediation | Prod? | Protected-OK?**

Legend: VB=VERIFIED_BUG, VR=VERIFIED_RISK, AD=VERIFIED_ARCHITECTURAL_DEBT, DUP=VERIFIED_DUPLICATION, DD=VERIFIED_DOCUMENTATION_DRIFT, TG=VERIFIED_TEST_GAP, PV=PARTIALLY_VERIFIED, DP=DISPROVEN, IB=INTENTIONAL_BEHAVIOR, NC=NOT_CHECKABLE.

---

## AUDIT A — Quick breaker fallback

| ID | Class | File | Function | Lines | Observed | Expected | SoT | Evidence | Existing tests | Missing tests | Sev | Remediation | Prod | Prot-OK |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| A-01 | VB | ui/screens/quick/QuickNewToolsModels.kt | QuickBreakerSelectEngine.calculate | 301-337 | chosen==null fabricates icu=36.0, ics=icu, icm=icu*2.1, "Generic Breaker","N/A" | DATA MISSING, null capacities (repo precedent: ConduitFillEngine, ATS, VoltageDrop) | assets/breaker_catalog.json via Catalog.breakers | `:313 icu = chosen?.icuKa ?: 36.0`; `:337 status="NO_EXACT_CATALOG_MATCH"` | QuickEngineeringToolsTest (asserts non-null only) | null-path test; no-synthetic-Icu assert | High | null→DATA MISSING, null capacities | Y | Y (quick pkg unprotected) |
| A-02 | VB | ui/screens/quick/QuickBreakerSelectScreen.kt | QuickBreakerSelectScreen | 247-313 | UI renders 36.0 kA, green Icu tile, margin %, provenance rows, copyable "Generic Breaker (45A, 36.0 kA)" under FAIL banner | No capacity numbers when no breaker selected; use existing DATA MISSING banner branch (QuickComponents.kt:174-179) | canonical breaker catalog | `:254-255` headline/subText; `:275` accent from 36.0>=Isc | none | Compose test: no "kA" when unselected | High | map selectedBreaker==null→DATA MISSING | Y | Y |
| A-03 | VB | data/Models.kt + ui/EngineeringViewModel.kt + ui/MainLayout.kt | Catalog.loadAll / retryStartup / nav | Models 136; VM 215-264; MainLayout 203-268 | Catalog.breakers defaults empty; load failure swallowed→ReadyWithWarnings; quick-tool nav not gated by catalogReady; boot skippable | Tool waits for catalogReady or visibly degrades | Catalog.isCatalogLoaded / getDiagnostics().status | Models.kt:136 emptyList; VM:228-231 catch; MainLayout:254-262 no flag | CatalogReadyGuardTest (workspace guard only) | pre-catalog nav gate test; failed-load→DATA MISSING test | High | gate/surface catalog state in quick nav | Y | Y |
| A-04 | VB | QuickNewToolsModels.kt | QuickBreakerSelectEngine.calculate | 303-308 | `\|\| b.poles == 3` returns 3P breaker for 4P/1P request; result model has no poles field | exact pole match or explicit no-match | breaker_catalog.json poles (4P rows exist :110-145) | `:304` filter; QuickBreakerSelectScreen:34 offers "4P (3P+N)" | QuickEngineeringToolsTest poles=3 only | poles=4/1 tests | High | remove clause; add poles to result | Y | Y |
| A-05 | DUP | QuickNewToolsModels.kt vs assets/breaker_catalog.json | FALLBACK_BREAKERS | 282-296 | 13 rows vs 52 canonical; 10 ordering codes nonexistent; NSX400/630 capacities wrong (36/36/75.6 vs 50/50/105, 100/100/220); phantom NSX100 50 A; Schneider-only | fallback == asset subset or none | breaker_catalog.json (935 lines) | full asset read vs list | CatalogLoaderTest (asset only) | fallback↔asset parity test | High | delete fallback or generate+parity-test | Y | Y |
| A-06 | AD | QuickNewToolsModels.kt vs engine/ShortCircuitEngine.kt | icm n-factor | 315 vs 274-292 | blanket icm=icu*2.1 vs tiered 2.10/2.20 (IEC 60947-2) | shared helper | ShortCircuitEngine tiered table | `:315` vs `ShortCircuitEngine.kt:290-291` | ShortCircuitEngineTest (engine only) | equivalence test | Low | extract shared n-factor helper | Y | Y for quick / N for engine |
| A-07 | TG | test/QuickEngineeringToolsTest.kt | breaker/conduit tests | 61-137 | plain JUnit → Catalog empty → tests exercise FALLBACK_BREAKERS only; bless PASS; no null-path coverage | tests run against real asset + cover null path | breaker_catalog.json | imports only, no catalog setup (contrast CatalogLoaderTest) | as cited | loaded-catalog test; null-path; parity | Med | load assets in tests | Y(indirect) | Y |
| A-08 | DD | QuickBreakerSelectScreen.kt + engine/quick/QuickEngineeringDataBridge.kt | provenance footer / KDoc | 309; 58-69 | footer names 3-vendor catalog even when no row used; bridge KDoc guarantees "Zero duplicate catalog tables" while FALLBACK_* shipped | provenance follows actual source; KDoc matches code | code | `:309 dataSource=…` vs `:82,173,282` | none | provenance varies with null selection | Med | result-derived dataSource; narrow KDoc | Y | Y quick / N (bridge KDoc is engine pkg) |
| A-09 | VR | QuickNewToolsModels.kt | calculate | 314-316 | fabricated Ics(=Icu)/Icm presented as manufacturer ratings; margin % computed on fabricate | unknown capacities not rendered; label as estimate (ShortCircuitEngine pattern) | breaker_catalog.json icsKa/icmKa | `:314-316` | none | assert no Ics/Icm strings when unselected | Med | same as A-01 | Y | Y |
| A-10 | IB | QuickNewToolsModels.kt | FALLBACK_BREAKERS/activeCatalog | 282,301 | explicit fallback mechanism is deliberate design | n/a — mechanism intentional; content defective | code | `if (Catalog.breakers.isNotEmpty()) … else FALLBACK_BREAKERS` | quick tests | n/a | — | keep mechanism, fix contract | Y | Y |
| A-11 | PV | QuickNewToolsModels.kt | magneticSelectivityRatio | 319-321,333-334 | magnetic ratio = thermal ratio re-used, displayed as separate ≥2.0 check | Im-based or relabel | IEC 60898-1 not in repo → cannot assert number | both fields from thermalRatio | QuickEngineeringToolsTest (locks both) | semantics doc test | Med | rename to In-ratio screen | Y | Y |

## AUDIT B — Quick calculators vs authoritative engines

Matrix: **Quick Tool | Authoritative Engine | Duplicate Logic? | Catalog Source | Formula Drift? | Default Drift? | Action**

| Quick Tool | Authoritative | Duplicate? | Catalog | Formula drift | Default drift | Action |
|---|---|---|---|---|---|---|
| QuickEarthingEngine (:65) | EarthingEngine | YES full reimpl | none | YES (ring path omitted, rod cap off-by-one, adiabatic rounding, Ia curve parse, Zs basis) | YES (630 A/C/230 V silent) | route via EarthingEngine adapter; parity tests |
| QuickConduitFillEngine (:171) | ConduitFillEngine | YES | Catalog.conduits + FALLBACK_CONDUITS | YES (count basis, overflow→lastOrNull, type filter, CPC provenance) | no (53/31/40 identical) | delegate; regenerate fallback |
| QuickBreakerSelectEngine (:281) | CableSizingEngine device select + ProtectionEngine verdict | YES | Catalog.breakers + FALLBACK_BREAKERS | YES (Icu-in-selection; fabrication; poles) | no | select by rating+type; Icu as verdict |
| QuickGeneratorEngine (:379) | GeneratorEngine | YES | size lists (quick missing 10/15/25/75) | YES (step/1.5 vs /0.8; η term +11.1%) | YES (DF 1.0 vs 0.8) | reuse engine sizing; sync ladder |
| QuickUpsEngine (:454) | UpsEngine | YES | identical size list | YES (frame load/0.8 vs design×1.2/0.8; DoD term) | YES (92%/30min/384V/0.80 vs 94%/15/400/0.9) | adopt authoritative basis |
| QuickAtsEngine (:527) | AtsEngine | YES | identical size list | YES (stepped Icm 1.5-2.2 vs fixed 2.2; no DSF/growth) | YES (9 s/AC-33B vs 10 s/AC-31A) | delegate; parity incl. Icm |
| QuickMotorStarterEngine (:616) | NONE exists | N/A (no counterpart) | internal lists | N/A | N/A | INTENTIONAL_BY_ABSENCE — document bases, reference tests |

Detailed findings (formula-level, file:line evidence):

| ID | Class | File:Function | Lines | Observed (evidence) | Expected / SoT | Tests exist → missing | Sev | Remediation | Prod | Prot-OK |
|---|---|---|---|---|---|---|---|---|---|---|
| AB-B01 | DUP | QuickNewToolsModels:QuickEarthingEngine | 68-133 | no ring parallel path (`achievedR=r1/(nRods*eff)` :94 vs EarthingEngine:143 parallel ring) → 13 rods vs 1 rod (ρ=50,L=3,target2Ω) | same result as EarthingEngine | quick tests lock quick value → parity test | High | adapter to EarthingEngine | Y | Y |
| AB-B02 | VB | same | 88-98,118 | `while(nRods<50)` reports 50 rods w/ 49-rod resistance | consistent count/value (EarthingEngine:133,146) | none → 50-rod ceiling test | Med-Low | align loop | Y | Y |
| AB-B03 | VB | same | 101-103 | size from unrounded exactS; inputs clamped not refused | round first; SELECT_LOADS refusal (EarthingEngine:89-110,169-173) | boundary test missing | Low | round+policy | Y | Y |
| AB-B04 | VR | same + QuickEarthingScreen | 41-43,106-114; screen 64-78 | Ia via naive when(curve); 630 A/C/230 V never exposed; zs/zsMax computed but never rendered | ProtectionEngine.parseTripCurve semantics; assumptions visible | none → curve parse + disclosure tests | Med | surface/remove silent inputs | Y | Y |
| AB-B05 | VB | same | 105-107 | zs = u0/3φ-fault (Ik1 basis ignored) | Ik1 preferred (EarthingEngine:163,182-185) | none | Med | align basis | Y | Y |
| AB-B06 | PV | same | 69-76 | clamp-and-continue vs refuse intent unproven | one documented policy | none | Low-Med | choose+test policy | Y | Y |
| AB-B07 | DUP | QuickConduitFillEngine | 195-232 | cable-count threshold vs object/routing-count (ConduitFillEngine:223-227) → same install 40%→PVC_75 vs 53%→PVC_63 | shared threshold impl | quick vs authoritative split → boundary parity | Med-High | delegate via ConduitFillInput | Y | Y |
| AB-B08 | VB | same | 221-232 | oversize → lastOrNull() + isCompliant=false; still shows trade size | `NO STANDARD SIZE AVAILABLE` (ConduitFillEngine:333-365) | none → oversize test | Med | failure status | Y | Y |
| AB-B09 | PV | engine/ConduitFillEngine.calculate (PROTECTED) | 247-249 | `\|\| input.conduitType=="Rigid PVC"` makes type filter no-op (EMT/GI admissible); quick filters strictly | agreed filter; authoritative side defective | ConduitFillEngineTest no cross-type assert | Med | **BLOCKED BY PROTECTED ENGINE RULE** — document; quick keeps strict filter | Y | N (protected) |
| AB-B10 | VR | QuickConduitFillEngine | 198-204 | CPC raw OD, no provenance/DATA MISSING (vs ConduitFillEngine:148-211) | CPC provenance flags | none | Med | mirror provenance or delegate | Y | Y |
| AB-B11 | VR | FALLBACK_CONDUITS | 173-193 | 19 rows, missing PVC_160/PVC_200 → large bundles fail where canonical succeeds | fallback==asset | quick tests use fallback only → parity test | Med-Low | drop/extend+parity | Y | Y |
| AB-B12 | VB | QuickBreakerSelectEngine | 298-337 | Icu≥Isc used as selection filter (MCB+25 kA→no match yet reports 36 kA) vs authoritative select-by-rating+type then verdict (CableSizingEngine:902-916; ProtectionEngine:394-400) | split selection/verdict | none for failing path | Med-High | split selection vs verdict | Y | Y |
| AB-B13 | VB | same | 303-308 | poles clause returns 3P for 4P request (concrete: 30 A case) | exact match / explicit unavailable | none | High | remove clause | Y | Y |
| AB-B14 | DUP | FALLBACK_BREAKERS | 282-296 | 13 all-3P rows w/ codes absent from asset | fallback==asset | none | Med | parity test | Y | Y |
| AB-B15 | VB | QuickGeneratorEngine | 386-400 | `max(s/0.80, step/1.50)` + DF 1.0 vs `max(...)/0.8×oversizing` + DF 0.8 (GeneratorEngine:108-116; Entities:156) | authoritative expression | quick test asserts continuous branch only → step test | High | reuse engine math | Y | Y |
| AB-B16 | VB | same | 392-396 | ÷(motorPf×0.90) → +11.1% vs ÷motorPf (GeneratorEngine:104-106); quick test locks drift | parity or documented mode | test locks drift → parity test | Med | drop η or document | Y | Y |
| AB-B17 | DUP | STANDARD_GEN_SIZES | 380-384 | missing 10/15/25/75 kVA (12→20 vs 15) | same ladder (GeneratorEngine:118-122) | none | Med | shared ladder | Y | Y |
| AB-B18 | AD | QuickGeneratorEngine | 405-416 | Xd″ dip model + verdict unique to quick (engine has warning only :154-156) | shared or labelled heuristic | none | Med | document mode | Y | Y |
| AB-B19 | VB | QuickUpsEngine | 460-468 | frame=load/0.80 vs design×1.2/0.8 (UpsEngine:71,89) → 80 vs 100 kVA @60 | authoritative basis | quick locks 80 | Med-High | adopt margin basis | Y | Y |
| AB-B20 | VB | same | 463,470-480 | battery ÷DoD on load kW vs design kVA×pf, no DoD (UpsEngine:73-81) | authoritative basis | quick locks values → parity | Med | decide basis | Y | Y |
| AB-B21 | PV | quick defaults vs Entities | 428-438,502-511,353-362 | 92%/30/384/0.8; 9 s AC-33B; DF1.0 vs Entities 94/15/400/0.9; 10 s AC-31A; 0.8 | align or label as scratch defaults | none → default parity suite | Low-Med | align/document | Y | Y |
| AB-B22 | VB | QuickAtsEngine | 538-546 | stepped Icm factor (72.0 kA@36) vs AtsEngine fixed 2.2 (79.2) | authoritative factor | quick locks 72.0 → parity | Med | fixed 2.2 or cited source | Y | Y |
| AB-B23 | VR | same | 533-550 | raw-current sizing (no 1.25×1.1); no cooldown/re-transfer/margin checks (AtsEngine:66-76,106-133) | design current + assessments | partial | Med | add checks or delegate | Y | Y |
| AB-B24 | IB | QuickMotorStarterEngine | 616-717 | no authoritative engine exists → standalone by absence; 1.25× rule & multipliers uncited | document bases + reference tests | quick unit tests exist → citation tests | Med | keep standalone; document | Y | Y |
| AB-B25 | DD | engine/quick/QuickEngineeringDataBridge.kt | 58-109 | KDoc "temperature-accurate columns" + labels "R_Cu90(90°C)" but data = R_Cu20 & 1.2×R20 (values match authoritative) | labels name actual columns (data_sheet.json:3-9) | column-selection test → label test | Low | reword KDoc/labels | Y | N (engine pkg KDoc) |
| AB-B26 | AD | bridge + quick screens | — | bridge used only by VoltageDropV2Models; 7 quick engines bypass it | stated UI→bridge architecture | Batch1 integration test pattern → extend | Med | route via engine entry points | Y | Y (delegation only) |
| AB-B27 | TG | test/QuickEngineeringToolsTest et al. | 1-293 | no cross-parity tests; quick tests run on fallbacks; hub navigation test asserts self-built 8-list vs real 15-16 registry; hub KDoc "8 tools" | parity suite; real catalogs; registry-driven test | Batch1 pattern exists → all parity pairs | High | add parity suite | N | Y |

## AUDIT C — Defaults vs engineering assumptions

| ID | Class | File | Function | Lines | Observed | Expected | SoT | Evidence | Tests → missing | Sev | Remediation | Prod | Prot-OK |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| C-01 | VR | ui/EngineeringViewModel.kt | uiState earthing input | 740 | systemVoltage_V=400.0 hardcoded for every project | derive from project transformer/circuit voltage | project entities (TransformerEngine:160) | `:740 systemVoltage_V = 400.0` | EarthingEngineTest(math only) → VM 415/480V test | Med | plumb project voltage | Y | Y |
| C-02 | VB | engine/EarthingEngine.kt (protected) + caller | EarthingInput.u0_V | 24,184-196 | u0 default 230 V; VM never passes u0 → zs/zsMax always at 230 V (wrong Ω for 380/415/480) | U0 = project phase-neutral (380→220,400→230,415→240,480→277 per VM voltage map :362-368) | IEC 60364-4-41 + project voltage | `EarthingEngine.kt:24,184-196`; VM:730-745 no u0 | engine Zs test w/ explicit input → VM-level U0 tests | High | feed u0_V from VM (engine unchanged) | Y | Y (VM side only) |
| C-03 | VB | ui/EngineeringViewModel.kt | uiState | 724-725 | mainBreaker = first transformer FLC (map-order) ?: 630.0 → drives Ia→zsMax→loopCheck (shown) | project main device or DATA MISSING; no installed-breaker entity exists (Entities:334-354) | project breaker data (absent) → explicit state | `:724-725`; ProtectionScreen:450-454 shows verdict | none → multi-tx + empty-tx tests | High | remove silent 630; DATA MISSING/explicit input | Y | Y |
| C-04 | VB | data/EngineeringRepository.kt | importProjectJson earthing | 1129-1140 | import fallbacks kFactor=1.0 (vs 159.0) → S inflated ~159×; soilResistivitySource="DEFAULT_TABLE" (not a valid token; clears ASSUMED warning) | import fallbacks == EarthingEntity defaults (Entities:344,348) | EarthingEntity defaults | `:1135,1139` vs Entities:344-348 | ImportExportTest (round-trip only) → fallback assertions | High | align fallbacks; map legacy→ASSUMED | Y | Y |
| C-05 | DUP | EngineeringRepository vs EngineeringViewModel | import vs createDefaultTransformer | 712-717; 335-352 | %Z 5.0/XR 7.0 (import) vs 6.0/6.0 (create) → different Isc for same data | single defaults table | none currently | both lines | TransformerEngineTest → parity test | Med | shared TransformerDefaults | Y | Y |
| C-06 | VR | EngineeringRepository.kt | import circuits/pfGlobal | 761-776,1066-1072 | import fallbacks (pf .85, VD .05, targetPf .95, 8760 h, $0.12…) differ from in-app defaults (pf .8, VD .03, .97, 6000, 1.5…) → old exports change verdicts; no DEFAULTED_BY_SCHEMA notice (pattern exists :734-735) | fallback==default + notice | Entities defaults | cited | ImportExportTest → default-parity tests | Med | centralize; emit notices | Y | Y |
| C-07 | DD | QuickEarthingScreen/NewToolsModels | quick earthing | 68-77,40-43,106-114 | visible example defaults (16 kA,0.4 s,2 Ω,ρ50) but silent 400/230/630/C basis for zs; Zs never rendered | disclose basis or drop computation | EarthingQuickInput defaults | cited | quick tests (Dwight only) → Zs disclosure | Low | show basis or drop | Y | Y |

Classification inventory note: 400/230/630 defaults above = class (5) unsafe fabricated fallback when they reach results; quick-screen visible numeric seeds (16 kA etc.) = class (4) example/demo values shown as editable fields; entity schema defaults (voltageV=400 etc.) = class (2) schema-compatibility defaults. No silent-deletion recommended for valid class (1)-(3) defaults.

## AUDIT D — SLD data integrity (hardcoded/divergent values)

| ID | Class | File | Function | Lines | Observed | Expected | SoT | Evidence | Tests → missing | Sev | Remediation | Prod | Prot-OK |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| D-01 | VB | sld/ElectricalNetworkGraphBuilder.kt | buildGraph circuit node | 1275-1277 | voltage label chosen from circ.phases only → "400 V (3Ph)"/"230 V (1Ph)"; voltageV (415/380) ignored; contradiction with current computed from voltageV (:1214-1217) | label from circ.voltageV (>0 guard) | CircuitEntity.voltageV (Entities:26) | `:1275` vs `:1185` guarded site | none → 415 V label test | High | derive from voltageV | Y | Y |
| D-02 | VB | same | generator node | 198-200,218,221 | sysVoltage=400 constant; gen Ib from same | equipment/project voltage else DATA REQUIRED (house rule :81-84) | NONE — GeneratorEntity has no voltage column (Entities:143-168) | `:198 val sysVoltage = 400` | none → gen voltage test | Med | add entity column (migration) or inherit linked ATS | Y | Y |
| D-03 | VB | same | SYNC_BUS node | 254-264 | sync bus always "400 V" | from member gensets/ATS voltage else null | AtsEntity.systemVoltage | `:260,263 voltage="400 V"` | none | Med-Low | inherit after D-02 | Y | Y |
| D-04 | VB(latent) | same | bus-tie node | 698-712 | tie voltage "400 V" though endpoints are board PKs | endpoint CircuitEntity.voltageV + mismatch check | CircuitEntity.voltageV | `:704` | none | Med-Low | look up endpoints | Y(latent) | Y |
| D-05 | VB | same | UPS node | 748-750 | systemVoltage<=0 → "400 V" + Ib at 400 | null/DATA REQUIRED | UpsEntity.systemVoltage | `:748 else "400 V"` | none → 0-V test | Low | DATA REQUIRED | Y | Y |
| D-06 | VB | same | transformer secondary/FLC | 134-143 | tx.voltage only if 100..1000 else 400.0 → displayed ratio + fallback FLC | out-of-range = no-data + issue | TransformerEntity.voltage | `:135 else 400.0`; `:137` | in-range only → 0/3300 V tests | Med | null + validation issue | Y | Y |
| D-07 | VB | same | ibStr fallback | 950 | fallback Ib = loadKVA×1.44 (=400 V, pf1.0, 3φ) ignores voltageV/phases/pf; rendered as "Design Current (Ib)" | Ib from entity formula or DATA REQUIRED | CircuitEntity.voltageV/phases/pf; SizingResult.Ib_A | `:950 (circ.loadKVA*1.44)` | none → 100 kVA/0.8 pf case (180.4≠144) | Med-High(conditional) | entity formula or DATA REQUIRED | Y | Y |
| D-08 | VB | same | boardCurrentA | 1214-1218 | ÷circ.voltageV with NO >0 guard (neighbors :1185,:1236 guard) → v=0 yields Inf/NaN into BusbarSizingEngine (:1231-1236) | same guard; non-finite never to sizing | CircuitEntity.voltageV | `:1216` unguarded vs `:1185` guarded | stop-gate test only 400 V → 0-V test | Med | add guard + input validation | Y | Y |
| D-09 | VB | sld/RiserLayoutEngine.kt | tier datum labels | 1121-1130 | top datum always "UTILITY INCOMER (22kV)" contradicting data-driven grid label | caption from utility node voltage | TransformerEntity.primaryVoltageV → grid node (:100-116) | `:1123` vs builder `:108` | none | High | derive from graph node | Y | Y |
| D-10 | DD | sld/SldLabelFieldLibrary.kt | labelFields contract | 69,97,132,175,183-186 | ≥3-5 populationSource statements false vs implementation (grid fault present; secondary not "HARDCODED" anymore in parts; busbarRatingA exists) — contract test stays green masking D-01/D-06 | doc == implementation | builder + Entities | entries vs builder:121,:134-135,Entities:85 | structure-only test → truth tests | Med | correct entries with D-01 fix | Y | Y |
| D-11 | AD | sld/SldSymbols.kt | draw* defaults | 117-119,158,187,216,303,926-927,971 | unused default params carry engineering literals ("22 kV Grid","1000 kVA","22kV / 400V") — inert but fragile | no engineering literals in symbol code | SldDataBlock | bodies don't reference them | symbol audits miss them → lint-style test | Low | delete unused defaults | Y | Y |
| D-12 | VB | sld/SldCanvas.kt | feeder callout | 416-423 | literal " PASS" appended regardless of vdPct vs maxVDpct; color from different verdict source → text/color can disagree | verdict computed from vdPct vs limit | CircuitEntity.maxVDpct; SizingResult.vdPct | `:420 "$vdText PASS"` | none → vdPct>limit test | Med-High | compute PASS/FAIL from data | Y | Y |
| D-13 | AD | ui/SldScreen.kt | toSldNode | 743-749 | second UI-layer Ib formula (√3 for all, ignores pf) + unreachable entity branch leaving literal `?: "400 V (3P+N)"` | single builder-derived path | CircuitEntity; SizingResult.Ib_A | `:743-749` | none | Low | take builder data block | Y | Y |
| D-14 | VB | ui/SldScreen.kt | toSldNode provenance | 764 | every node stamped VERIFIED_FORMULA overwriting builder provenance (PROJECT_INPUT/APPROVED_STANDARD) shown in details sheet | preserve builder provenance | builder :168,:230,:565,:815,:1038-1050 | `:764 provenance=VERIFIED_FORMULA` | none → sheet==builder test | Med | thread builder provenance | Y | Y |

## AUDIT E — SLD topology / first-instance fallbacks

Category (a) = alters engineering meaning; category (b) = visual-only (acceptable, documented).

| ID | Class | File | Function | Lines | Observed | Expected | SoT | Evidence | Tests → missing | Sev | Remediation | Prod | Prot-OK |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| E-01 | VB(a) | ElectricalNetworkGraphBuilder | circuit loop transformer keyword branch | 1115-1126 | `projTransformers.firstOrNull()` (no ambiguity check) when fedFrom generic keyword & transformerId blank; edge stamped APPROVED_STANDARD | size==1 resolve; >1 AMBIGUOUS+DISCONNECTED (policy exists :375-385) | CircuitEntity.transformerId | `:1116 firstOrNull()` vs `:375-385` | explicit-ID only → ambiguity test | High | singleOrNull + AMBIGUOUS issue | Y | Y |
| E-02 | VB(a) | same | fedFrom contains ats/gen | 1063-1080 | name-contains match `?: firstOrNull()` first-instance; else parents to ANY transformer or utility node | explicit ID → unique exact match → issue | assignedAtsId/assignedGeneratorId (Entities:65-67) | `:1066,:1074,:1071` | explicit-ID paths only → ambiguity/missing tests | High | delete `?: firstOrNull()` tails | Y | Y |
| E-03 | VB(a) | same | UPS upstream resolution | 753-785 | name equality then fallbackPanel/Ats/Tx/Grid `firstOrNull()` chain; any hit flips upsStatus→VALID (:795) | only explicit FKs; else existing UPS_UPSTREAM_SOURCE_REQUIRED (:840-849) | CircuitEntity.assignedUpsId | `:771-774,:776-785,:795` | explicit links only → 2-MDB test | High | remove fallbacks | Y | Y |
| E-04 | VB(a) | same | ATS blank normalSource | 415-432 | blank → FIRST "MDB-looking" circuit by list order or single tx, no issue | blank→ATS_NORMAL_SOURCE_REQUIRED (exists :591-601) unless exactly one candidate | AtsEntity.normalSourceId (Entities:221) | `:417-424` | test with zero circuits only → 2-MDB test | Med-High | exactly-one or issue | Y | Y |
| E-05 | IB(a) | same | ATS blank emergencySource | 525-530 | binds to sole generator (unambiguous) but silently confers VALID | keep rule + inferred warning | AtsEntity.emergencySourceId | `:526-529` | none → inferred-warning test | Low | add warning | Y | Y |
| E-06 | VB(a) | same | generator frequency fallback | 204-210 | `atsList?.firstOrNull()?.systemFrequency` — unfiltered list → foreign-project frequency can stamp this project | no linked ATS → null; project-filtered only | AtsEntity.systemFrequency; filter pattern :288-294 | `:206-210` | none | Med | drop `?:` tail | Y | Y |
| E-07 | VB(a) | same | circuit matchedAts/Gen/Ups | 981-997,1036-1047 | outputBoardId/name-equality firstOrNull defines topology+APPROVED_STANDARD; duplicate names undetected; UNASSIGNED issues only when flag set | size==1 exact else ambiguity (mirror :321-336) | assigned*Id entities | `:984,:990,:996,:1039-1043` | flag matrix + explicit IDs → duplicate-name test | Med | require .size==1 | Y | Y |
| E-08 | VB(a) | ui/SldScreen.kt | SldScreen → calculateLayout | 129-147 | busTies NEVER passed (param absent; SldLayoutEngine:90 defaults emptyList) → bus-tie synthesis, BUS_TIE_UNRESOLVED validation, tie nodes all DEAD in production | SldScreen accepts + caller supplies busTies | BusTieEntity (Entities:371-387) | `SldScreen.kt:131-147` no arg vs `SldLayoutEngine.kt:90,109` | direct-call tests only → call-path test | High | add param + pass from repository | Y | Y |
| E-09 | VB(a) | ui/SldScreen.kt | effectiveProjId | 81 | blank activeProjectId → first circuit's project / first transformer / "DEFAULT_PROJECT" | explicit error/active-project store | AppStateEntity.activeProjectId | `:81` | cross-project builder test only → blank-id test | Med | resolve via store | Y | Y |
| E-10 | IB(b) | sld/RiserLayoutEngine.kt | assignCoordinates X-fallbacks | 470-508 | first-MDB/first-TX/first-ATS for horizontal alignment only — no edges/labels/verdicts changed | category (b) acceptable | geometry only | `:475,:482,:507-508` | layout determinism tests | Info | optional deterministic pick | Y | Y |
| E-11 | IB(b) | sld/ShopDrawingLayoutEngine.kt | calculateLayout | 29-32,160-192 | sortedBy roots; first/last child for widths — drawing order/geometry only (deterministic) | category (b) acceptable | geometry only | `:32,:163,:177,:187` | DXF suites | Info | none | Y | Y |

## AUDIT F — EngineeringViewModel

| ID | Class | File:Function | Lines | Observed (evidence) | Expected/SoT | Tests → missing | Sev | Remediation | Prod | Prot-OK |
|---|---|---|---|---|---|---|---|---|---|---|
| F-01 | AD | EngineeringViewModel:uiState | 664,670 | transformerFLC = first-instance FLC passed to AtsEngine but NEVER read by AtsEngine (param :39 unused) — dead first-instance coupling | remove dead param | AtsEngineTest → characterization | Low | delete plumbing | Y | Y |
| F-02 | VB | EngineeringViewModel:_autoDesignResultState | 213,747,1141-1164 | VM-lifetime cache NOT cleared on openProject/create/close/delete → project B can render A's auto-design electrode layout (also into PDF :527-536) | keyed by projectId or cleared on transition | none → cross-project test | High | clear on transitions | Y | Y |
| F-03 | TG | test/ui/ProjectIsolationTest.kt | 10-14 | isolation test = `assertTrue(true)` stub | executable assertions | others exist (ImportExportTest A/B template) → real isolation test | Med | replace with Room-backed test | N | Y |
| F-04 | IB | EngineeringViewModel:uiState | 832 (+475,303,225,194; Coordinator 624,773,1062) | heavy aggregate calculation uses `.flowOn(Dispatchers.Default)`; IO for PDF/import/catalog — VERIFIED GOOD, do not regress | same | stress/guard tests | — | preserve | Y | — |
| F-05 | IB | EngineeringRepository + Coordinator | Repo 32-71; Coord 88-134 | per-project mutex + revision-bounded mutations; failed revision now throws (no silent 1L) | same | ProjectPersistenceIntegrityTest; RevisionFailure test @Ignore'd → un-ignore | — | un-ignore quarantine | Y | Y |
| F-06 | DP | EngineeringViewModel:onRunAutoDesign | 1149-1166 | claimed main-thread jank: DISPROVEN — bounded ~1000 float ops | n/a | EarthingEngineTest | — | none | N | — |
| F-07 | IB | EngineeringViewModel | 722; Coordinator 133-134 | "DEFAULT_PROJECT" fallback only scopes transient entity/cache keys; no engineering value change | same | stub → cache-scoping test optional | Low | none | N | — |
| F-08 | VR | pdf/Sections.kt EarthingSection | 514-550 | PDF shows Zs/verdict but omits U0/In/curve/t/k basis and never renders earthing.messages (assumption disclosures) | disclose every assumption | pdf structure tests → disclosure test | Med | add basis rows + messages | Y | Y |
| F-09 | VR | ui/MainLayout.kt openAssistantWithContext | 319-329 | complianceStatus="NORMAL" hardcoded for AI context regardless of actual validation | derive from validationSummary | ProjectContextProviderTest → fidelity test | Low | compute status | Y | Y |

## AUDIT G — Earthing source-of-truth (answer)

| Value | Status | Evidence |
|---|---|---|
| system voltage | **SILENTLY FORCED 400 V** (workspace `EngineeringViewModel:740`; engine default EarthingEngine:18; quick QuickNewToolsModels:40) → C-01 | as cited |
| U0 | **FORCED 230 V** (EarthingEngine:24,184 never passed by VM; ProtectionEngine:226 ADS hard-codes u0Ads=230; quick :41) → C-02 | as cited |
| main breaker | **NOT project-derived**: first-tx-FLC ?: 630 (VM:724-725); quick 630 → C-03 | as cited |
| fault current | PROJECT-DERIVED (VM:724 maxOf iscKA; earth fault VM:742 isc1Phase + explicit conservative message EarthingEngine:163,219-223) | VERIFIED control |
| target resistance | PROJECT-DERIVED (entity override else Villa 5.0/other 2.0 VM:727-728, surfaced) | VERIFIED control |
| project type | user-setting derived (Entities:345, dropdown) | VERIFIED control |
Verdict: voltage/U0/breaker forced → zs/zsMax/loopCheck computed on unstated 230 V/630 A basis. Remediation = plumb existing project values through VM layer; engine untouched.

## AUDIT H — Cable dimension approximation

| ID | Class | File:Function | Lines | Observed (evidence) | Expected/SoT | Tests → missing | Sev | Remediation | Prod | Prot-OK |
|---|---|---|---|---|---|---|---|---|---|---|
| H1 | VR | EngineeringViewModel:585,607-613 + ConduitFillScreen:437-442,191 | — | approx-OD message appended only to details list; status badge/PASS rests on "Standard Est." OD unwarned | verdict shows estimate caveat where verdict read | ConduitFillEngineTest (explicit OD only) → propagation test | Med | surface flag in badge/report | Y | Y (UI side) |
| H2 | VR | engine/OtherEngines.kt ProcurementEngine (PROTECTED) | 862,917-998 | gland/tray sizing from estimated OD reported APPROVED; `cableOD==0 → 10.0` unlabeled | estimated inputs → REVIEW not APPROVED | Boq/Drum tests (no provenance) → OD-provenance test | Med | VM-side flag passed to UI/PDF (avoid engine edit) | Y | N engine / Y via VM |
| H3 | AD | engine/OtherEngines.kt BoqEngine (PROTECTED) + pdf/Sections | 99-140; 411-417 | estimated weight (estimateSensibleWT) → status "OK", PDF prints bare kg; no ESTIMATED state | provenance-graded status | BoqDrumProcurementTest (wt<=0 only) → distinguish test | Med | VM/PDF-side ≈ marker | Y | N engine / Y via VM |
| H4 | TG | test/CableSizingEngineTest.kt | 277 ln | no assertion on cableOD_mm/isODApproximated/resolveCableDimensions fallback; ConduitFill tests all pass explicit OD | lock fallback branch | — → fallback+propagation tests | Med | add tests | N | Y |
| H5 | IB | engine/CableSizingEngine (PROTECTED) | 929-961; ConduitFillEngine 125-135 | **ESTIMATION CORRECTLY BOUNDED**: OD resolved AFTER candidate selection; ampacity/VD/Icu unaffected; 0-OD → DATA MISSING (no false PASS) | same | parity suite indirectly | — | none | Y | — |
| H6 | PV | EngineeringViewModel:607-613 | — | flag has ONE confirmed consumer; DXF/PDF/circuit-list show OD/weight without provenance (full-file reads; grep unavailable → absence not provable) | any artifact presenting OD as fact carries provenance | none → cross-artifact test | Med | add provenance rows in PDF/DXF | Y | Y |

Verdict on Audit H questions: estimation IS allowed and IS correctly excluded from sizing/safety math (H5); the risk is **presentation without caveat** (H1/H2/H3/H6), not sizing contamination. Catalog absence partially visible (details only).

## AUDIT I — Catalog integrity

| ID | Class | File:Function | Lines | Observed (evidence) | Expected/SoT | Tests → missing | Sev | Remediation | Prod | Prot-OK |
|---|---|---|---|---|---|---|---|---|---|---|
| I-01 | AD | data/Models.kt Catalog.loadAll/getDiagnostics | 101-112,206-249,391-415,421-426 | validation covers cableTypes/dataSheet/cableCatalog/breakers/accessories only — conduits/busways may be empty silently; ConduitCatalogEntry defaults provenance="EXCEL_REFERENCE", status="APPROVED" for any row | every consumed table validated; explicit provenance/status | CatalogLoaderTest (asset) → empty-conduit → non-COMPLETE test | High | add conduits/busways to validation + INVALID rule | Y | Y |
| I-02 | DUP | QuickNewToolsModels FALLBACK_CONDUITS vs conduit_catalog.json | 173-193 | 19 vs 21 rows; missing PVC_160/PVC_200 | fallback==asset | quick tests → parity + 160/200 test | Med | drop/extend + parity | Y | Y |
| I-03 | VB | engine/ConduitFillEngine.calculate (PROTECTED) | 247-251 | type filter no-op for "Rigid PVC" default → EMT/GI rows can win (EMT_50 2164.8mm² sorts before PVC_63 2463.0) | only requested material | ConduitFillEngineTest → material-parity test | Med-High | **BLOCKED BY PROTECTED ENGINE RULE**; workaround: test + strict quick filter documented | Y | N |
| I-04 | DUP | data/Models.kt vs data/catalog/CatalogRepository.kt | Models 132-140,400-415; Repo 143-185 | two parsers/models for breaker_catalog.json with parallel fields + tolerances; production cable sizing uses legacy global path (CableCalculationService:93-97 → CableSizingEngine isLegacy) | one parser per asset | CatalogLoaderTest covers one → cross-parser equivalence test | Med | converge on one parser | Y | Y |
| I-05 | VR | engine/CableSizingEngine (PROTECTED) | 902-916,988 | type-based breaking-capacity defaults (MCB 10/ACB 65/else 36) feed deviceCheckOk → can PASS without catalog; mitigated by isDeviceFromCatalog + ShortCircuitEngine "assumed — verify" note | catalog-only or DATA MISSING for device check | engine tests → missing-data test | Med | **BLOCKED (engine)**: surface DATA MISSING in VM when isDeviceFromCatalog=false | Y | N engine / Y VM |
| I-06 | IB | CableSizing/Transformer/QuickGen STANDARD_* series | 73-83; 43-46; 380-384 | normative IEC/DIN size ladders — not catalog duplicates | as-is (documented %Z bands) | engine tests | — | none | N | — |
| I-07 | DD | engine/quick/QuickEngineeringDataBridge.kt KDoc | 58-69 | guarantee "Zero duplicate catalog tables" contradicted by FALLBACK_* in same feature area | scoped/enforced guarantee | bridge test → architecture test forbidding new FALLBACK_* | Med | narrow KDoc or enforce via test | Y | N (KDoc in engine pkg) |
| I-08 | TG | tests | — | no test ties any in-code table to its canonical asset; Catalog.loadAll (production parser) has no JVM test | parity + empty-catalog tests per consumer | CatalogLoaderTest (other parser) → 5 tests listed | Med | add (a)-(e) | Y | Y |
| I-09 | VR | ui/CircuitOverrideUtils.kt | 124-131 | placeholder manufacturers ("Elsewedy","IEC-Generic") offered when cableCatalog empty (downstream fails honestly with CATALOG_MISSING) | empty list + not-loaded hint | CatalogLoaderTest asserts names exist in asset → empty-state test | Low | return emptyList + hint | Y | Y |
| I-10 | NC | repo-wide | — | exhaustive listOf() table inventory limited by broken ripgrep in this environment; inventory done via Python + targeted reads (may be non-exhaustive) | full inventory | — | — | re-sweep with working search | N | — |

**Catalog table classification (Audit I):** canonical = assets/{breaker,conduit,cable,data_sheet,accessory,transformer?}.json; duplicate+drifting = FALLBACK_BREAKERS (unsafe), FALLBACK_CONDUITS (incomplete); UI fixture = CircuitOverrideUtils placeholder; intentional standards = STANDARD_* ladders; sanctioned estimation = estimateSensibleWT (flagged, see H3); duplicate parser = CatalogRepository breaker path.

## AUDIT J — AI runtime architecture

Control inventory VERIFIED (all exist + enforced, with evidence): canonical entry `EngineeringAssistantViewModel.onUserSubmit:376`; deterministic registry (EngineeringToolRegistry init duplicate-name + descriptor binding validation); tool descriptors (schema invariants at init); dispatcher (unknown/mode/blank-project/missing-entity fail-closed, CancellationException rethrow :143-144); invokers (DeterministicRead/CableToolInvoker via AssistantToolRuntime:101-106); cancellation (router:92-94, orchestrator, dispatcher, VM close); timeout (router 15 s :54; orchestrator iteration 10 s / total 30 s; seam withTimeout); tool budget (AgenticSafetyBudget maxIterations 5 / calls 10 / repeats 2 / parallel 4); duplicate-call protection (ToolExecutionSignature incl. revision → REPEATED_TOOL_CALL_DETECTED); project isolation (dispatcher overwrites projectId :124-129, fail-closed); revision consistency (fail-closed on missing revision; CAS in gateway); grounded response validator (used orchestrator:193-211, cloud engine:698-711); engineering claim guard (router:70-73, cloud:694-697, nano:247-250); mutation approval (ExecutionModePolicy MUTATION→false; registry isAllowed false; single TrustedProposalApprovalGateway; VM confirm path only); durable receipts (DurableMutationWorkflow withTransaction + replay idempotency).

| ID | Class | File:Function | Lines | Observed (evidence) | Expected/SoT | Tests → missing | Sev | Remediation | Prod | Prot-OK |
|---|---|---|---|---|---|---|---|---|---|---|
| J-01 | AD | HybridConversationRouter + AgenticSafetyBudget | Router 52-56,85-91; Budget 16-17 | outer withTimeout(15 s) always fires before orchestrator totalTimeout(30 s) → 30 s branch unreachable; pre-timeout tool evidence discarded | single authoritative budget | case15 (iteration only), router timeout stub → combined interaction test | Med | derive cloudTimeout from budget or lower totalTimeout | Y | Y |
| J-02 | VR | CloudLlmConversationEngine + HybridConversationRouter | Cloud 691-730; Router 68-84 | single-turn branch always returns hasBackingToolResult=false → router claim guard discards BACKED text too (over-rejection) | trust engine's evidence-aware decision | controlled tests without priorEvidence → backed-text test | Med | propagate priorEvidence backing flag | Y | Y |
| J-03 | TG | tests (composition level) | — | no test constructs provideConversationEngine(toolRuntimeProvider=…) — production-only branch never executed by tests; budget×router interaction untested; totalTimeout untested | production-wiring tests | component tests exist → composition tests (i)-(iv) | Med | add composition tests | N | Y |
| J-04 | PV | EngineeringAssistantViewModel | 315-321,484-514,556-562 | evidence append lacks project check; budget absent on VM tool path; mixed ledger only cleared on open() project change (validator mitigates) | project-scoped ledger | ContextResolver tests → switch-while-open test | Low | filter on append | Y | Y |
| J-05..J-08 | IB | approval/proposal/grounding | — | mutation approval gate, dual claim/grounding guards, durable receipts + replay, project/revision fail-closed — all VERIFIED working with tests | same | Gate9/10/12, TrustedApproval*, Durable* | — | preserve | N | — |
| J-09 | DD | ai/contracts/ExecutionModePolicy.kt | 9-22 | KDoc says mutation non-executable "until Phase 7 establishes…" — Phase 7 artifacts exist; isMutationAllowed hard-coded false permanently | comment states permanent invariant | contracts tests | Low | rewrite comment | Y | Y |
| J-10 | TG | app/build.gradle.kts profile filter | 246-276 | default fast profile excludes com.example.ai.* — safety-control tests only run in gate/ai/full | CI must run gate | gate profile exists | Med | ensure CI gate runs `-Pprofile=gate` | N | Y |

## AUDIT K — ConversationEngineProvider runBlocking

| ID | Class | File:Function | Lines | Observed (evidence) | Expected/SoT | Tests → missing | Sev | Remediation | Prod | Prot-OK |
|---|---|---|---|---|---|---|---|---|---|---|
| K-01 | VB | ai/hybrid/ConversationEngineProvider.kt orchestratorProvider | 103-115 (runBlocking :106) | `runBlocking{provider()}` executed inside suspend chain on viewModelScope (Dispatchers.Main.immediate — VM:392) → parks MAIN thread (first call: 7 JSON asset loads behind it); runBlocking scope immune to activeTurnJob.cancel()/close()/withTimeout; runCatching swallows all Throwable silently; latent Main-dispatch deadlock | suspend-native composition (orchestratorProvider type at CloudLlmConversationEngine:445 → suspend), cancellation/timeout propagate, typed+logged failures | **NONE** — no test passes toolRuntimeProvider (branch never executed in tests) → 5 tests (no-main-block, cancellation, timeout, failure, context inheritance) | High | make orchestratorProvider suspend; drop runBlocking; log failures | Y | Y (ai/hybrid unprotected) |
| K-02 | TG | tests + build profile | — | production-only branch untested; fast profile excludes ai tests entirely | cover branch at production wiring in CI gate | — → K-01 test list | High | add tests; run gate in CI | N | Y |

## AUDIT L — Nano wiring

**Product state (verified):** AiProvider enum = {DIRECT_GEMINI, FIREBASE_AI_LOGIC, LOCAL_ONLY} — no Nano provider; no Nano/GenAI SDK dependency (only mlkit text-recognition); settings UI offers 2 chips (Direct Gemini, Local) — no Nano, no Firebase chip; production router constructed WITHOUT nanoEngine (ConversationEngineProvider:121-126 → nano branch dead); only test constructs OnDeviceNanoConversationEngine by hand.

| ID | Class | File:Function | Lines | Observed (evidence) | Expected/SoT | Tests → missing | Sev | Remediation | Prod | Prot-OK |
|---|---|---|---|---|---|---|---|---|---|---|
| L-01 | AD | ConversationEngineProvider | 121-126 | nanoEngine never passed → entire Nano route + OnDeviceNanoConversationEngine + NanoClientSeam unreachable in shipped app; architecture LOOKS available but is not | explicit product decision: wire w/ provider+flag+settings+test, OR mark experimental | OnDeviceNanoConversationEngineTest (hand-built only) → production wiring test asserting expected route | Med | mark experimental/unwired + production wiring test (do NOT fabricate support) | N | Y |
| L-02 | DD | OnDeviceNanoConversationEngine.kt | 11-18,54-70,163-164 | KDoc claims ML Kit GenAI/Gemini Nano hardware; default seam is keyword+Regex parser with isHardwareAvailable default TRUE (no probing; no SDK in build) | docs describe shipped parser; fail-closed default | hardware-unavailable test (explicit false only) → default-construction test | Med-Low | reword KDoc; invert default to false | Y | Y |
| L-03 | PV | AiSettingsDialog | 121-143 | FIREBASE_AI_LOGIC provider implemented (Provider:94-96) but not selectable in UI | expose third provider or annotate non-user-selectable | none → chip-set test | Low | align enum/UI | Y | Y |

## AUDIT M — FirebaseAiServiceImpl

| ID | Class | File:Function | Lines | Observed (evidence) | Expected/SoT | Tests → missing | Sev | Remediation | Prod | Prot-OK |
|---|---|---|---|---|---|---|---|---|---|---|
| M-01 | DD | ai/service/FirebaseAiServiceImpl.kt | 5-9,29-33 | KDoc/body describe Phase-1 system awaiting "Phase 2" wiring; actual production = ConversationEngineProvider pipeline; file is a pure string echo | deprecate/delete with pointer to canonical seam | none (no service test pkg) → reachability guard if kept | Med-Low | delete or @Deprecated+internal | Y | Y |
| M-02 | PV | same + EngineeringAiService | — | no construction site found in extensive directory enumeration; exhaustive proof limited by broken grep (residual non-AI areas) | proven unreachability before delete | none → architecture guard test | Med | ref-search then delete | N | Y |
| M-03 | VR | FirebaseAiServiceImpl.sendUserMessage | 12-34 | public class beside real pipeline returns plausible assistant text with ZERO guards (no gate/claim-guard/grounding/budget/project checks) — developer foot-gun | non-functional seam not indistinguishable from production | none → guard test | Med | deprecate/removal after reachability proof | Y | Y |

## AUDIT N — Hardcode audit scope

| ID | Class | File:Function | Lines | Observed (evidence) | Expected/SoT | Tests → missing | Sev | Remediation | Prod | Prot-OK |
|---|---|---|---|---|---|---|---|---|---|---|
| N-01 | IB | test/ai/NoHardcodedEngineeringValuesTest.kt | 7-20,25-42,45-61 | scope deliberately = com.example.ai (documented in class KDoc); 15 forbidden patterns + comment exclusion | AI-layer scope is intentional | these 2 tests | Info | keep AI scope; add sibling layers | N | Y |
| N-02 | TG | ui/screens/quick (V2 models/screens) | VoltageDropV2:44-57; PfcV2:44-51; LoadCurrentV2:23-27; QuickAtsScreen:44-69 | unscanned engineering seeds: 400.0/0.85/3.0%/35 mm²/0.727/0.080, "3-Phase (400V)", ATS 380/400 A, 25 kA, `?: 400.0` fallbacks | covered by a quick-layer scanner or sourced from config/catalog | content tests only → layer scanner | Med | 5-layer plan (below) | Y | Y |
| N-03 | TG | EngineeringViewModel earthing inputs | 725,740,727-728 | 630.0/400.0/target 5.0/2.0 outside every scanner | defaults guarded by test/justified | none → fallback assertions | Med | include VM in adapter layer scan | Y | Y |
| N-04 | PV | test tree inventory | — | only scanner found = AI-layer; absence in other layers PARTIALLY_VERIFIED (no grep) | scanner per layer | — → implement plan | Med | 5-layer shared harness | N | Y |

**Recommended layered audit (N):** L1 AI `com/example/ai` (EXISTS) · L2 quick `ui/screens/quick` · L3 SLD `sld/` (incl. DEFAULT_PROJECT literal) · L4 adapters `engine/` + EngineeringViewModel · L5 report/export `pdf/` + repository export. Allow-list: UI dp/sp dims, schema/migration versions, IDs/sentinels, timeouts/retries, catalog-backed standard ladders, comment lines. Shared parameterized harness extracted from NoHardcodedEngineeringValuesTest:25-61.

## AUDIT O — Project isolation

| ID | Class | File:Function | Lines | Observed (evidence) | Expected/SoT | Tests → missing | Sev | Remediation | Prod | Prot-OK |
|---|---|---|---|---|---|---|---|---|---|---|
| O-01 | VB | EngineeringViewModel:_autoDesignResultState | 213,747,1141-1164 | cross-project auto-design cache leak (== F-02) | keyed/cleared per project | none → A/B test | High | clear on open/close | Y | Y |
| O-02 | VB | ai/ui/EngineeringAssistantViewModel:open() | 315-345 vs 266-287 | project switch clears conversation/evidence but NOT messages/typedCards/approvalExecutionState/activeProposalReview → A's chat+cards render under B (data-write safe: approval re-resolves via proposal.projectId :737-800) | full assistant UI reset on projectId change | no VM-level switch test → add | High | clear on project change | Y | Y |
| O-03 | TG | test/ui/ProjectIsolationTest.kt | 9-14 | `assertTrue(true)` stub — VACUOUS isolation test (prior-audit concern CONFIRMED present) | executable A/B assertions (ImportExportTest:48-70 is template) | — → Room-backed A/B switch test | High | replace stub | N | Y |
| O-04 | VR | sld/ElectricalNetworkGraphBuilder.kt | 52-58 | rows with projectId=="DEFAULT_PROJECT" admitted into ANY open project (cross-project rows only issue-flagged) | strict project membership for render | none → membership test | Med | gate legacy admission | Y | Y |
| O-05 | DP | EngineeringCalculationCoordinator caches | 34-42,157-187 | DISPROVEN: caches keyed ProjectCircuitKey(projectId,circuitId); clearProjectCache exists | — | ProjectContextProviderTest:269-317 | Info | none (allow-list) | N | — |
| O-06 | VR | EngineeringRepository:deleteProject | 338-355 | bus_ties rows not deleted with project (orphaned; invisible due to UUIDs) | transactional deletion of all project tables | none → bus_ties count test | Med | delete in same transaction | Y | Y |
| O-07 | PV | Repository + AppDatabase unscoped flows | Repo 73-74; Dao 438-512 | unfiltered circuitsFlow/transformersFlow/name-keyed lookups exist; consumer inventory not verifiable without grep | explicit project scope on externally consumed streams | parent-resolution tests only → consumer inventory | Med | deprecate unscoped / re-verify with search | poss. | Y |
| O-08 | DP | ai/tools/EngineeringToolDispatcher | 77-129 | DISPROVEN: projectId overwritten from authoritative caller; fail-closed blank/revision; ContextResolver filters foreign evidence | — | case09/10/11, ProjectContextProviderTest:867-893 | Info | none (allow-list) | N | — |
| O-09 | DP | EngineeringRepository export/import | 375-386,658-675 | DISPROVEN: export strictly by projectId; import mints fresh PRJ_<ts>_<uuid> and reassigns all — cannot merge into existing project | — | ImportExportTest:48-70 | Info | none (allow-list) | N | — |
| O-10 | VR | EngineeringViewModel pendingDeleteConfirmation | 947-1038 vs 197-205 | delete-confirmation dialog state survives project switch (A's confirmable dialog active under B) | transient per-project state cleared on switch | none → A/B test | Med | clear in switch branch | Y | Y |
| O-11 | AD | AppDatabase AppStateDao + VM startup | Db 409-419; Repo 358; VM 148-151,283 | app_state.activeProjectId persisted but VM forces null at startup — two competing owners, one dormant | single authoritative owner | none → restore round-trip if intended | Low | restore or delete write path | N | Y |

## AUDIT P — Trusted engineer identity

**Determination: (A) ATTRIBUTION** — no authentication boundary exists anywhere in the runtime path (gateway only fails closed on blank and stores `approvedBy` as audit trail).

| ID | Class | File:Function | Lines | Observed (evidence) | Expected/SoT | Tests → missing | Sev | Remediation | Prod | Prot-OK |
|---|---|---|---|---|---|---|---|---|---|---|
| P-1 | DD | ai/approval/TrustedPrincipalProvider.kt | 3-23 | KDoc "authenticated engineer principal", "Never falls back to user-entered text" — production supplier IS user-entered text (Factory:59-61,76) | naming/KDoc match free-text settings field | AssistantArchitectureGuardTest (fail-closed) → wiring test | Med | reword to "device-local attribution label" | Y | Y |
| P-2 | IB | data/GlobalPreferences.kt | 49,60-74,315,427-449 | plaintext SharedPreferences identity, default "", no crypto — sufficient FOR ATTRIBUTION; not used as authorization input (gateway :41-44,:168) | attribution semantics documented | GlobalPreferencesTest:62 | Low | document "not a login/signature" in settings help | Y | Y |
| P-3 | DD | ui/GlobalSettingsScreen.kt | 209-232,778-783 | "…stored locally and **securely** on this device" — DB is unencrypted Room (AppDatabase:1086-1091), prefs plaintext → claim unbacked (credential store IS Keystore-backed — contrast SecureCredentialStorage:97-157) | UI claims only what repo implements | no copy-contract test → add | Low-Med | factual wording | Y | Y |

## AUDIT Q — Backup / restore

| ID | Class | File:Function | Lines | Observed (evidence) | Expected/SoT | Tests → missing | Sev | Remediation | Prod | Prot-OK |
|---|---|---|---|---|---|---|---|---|---|---|
| Q-1 | IB | backup_rules.xml, data_extraction_rules.xml, AndroidManifest.xml | rules 2-15; manifest 7-10 | allowBackup=true; DB+wal+shm excluded (both scopes); voltatlas_ai_secure_keystore.xml excluded; names match runtime (AppDatabase:1091; SecureCredentialStorage:160) | exclusions match runtime — VERIFIED correct | none → XML↔runtime contract test | Low | add drift-detector test | N | Y (manifest edits would need justification) |
| Q-2 | DD | data_extraction_rules.xml | 3-14 | `VoltAtlas_Globals` (incl. trusted_engineer_identity) NOT excluded → identity transfers on backup/d2d; Gemini credential does NOT (excluded + non-exportable KeyStore key) | document actual behavior; product decision recorded | none → decision record | Med | document transfer semantics; no silent policy change | Y | Y |
| Q-3 | DD | ui/GlobalSettingsScreen.kt | 77-104,835-850,1031-1117 | recovery = manual JSON export/import; DB backup-excluded → uninstall yields ZERO projects unless exported; UI never says so | user-facing backup/recovery statement | ImportExportTest (round-trip) → recovery-coverage doc/test | Med | add backup/recovery help text | Y | Y |

## AUDIT R — Release safety

| ID | Class | File:Function | Lines | Observed (evidence) | Expected/SoT | Tests → missing | Sev | Remediation | Prod | Prot-OK |
|---|---|---|---|---|---|---|---|---|---|---|
| R-1 | VR | app/build.gradle.kts lint{} | 97-101 | checkReleaseBuilds=false, abortOnError=false, checkDependencies=false; NO lint report exists, NO lint-baseline.xml → lint status **NOT_RUN**, gate disabled | at least one recorded, triaged lint run | n/a → run `:app:lintDebug`, triage, THEN decide (do not change config blind) | Med | run+triage first | N | Y (build config) |
| R-2 | PV | taskGraph.whenReady guard + signingConfigs | 32-85,113-147 | guard throws for packageRelease/bundleRelease/assembleRelease w/o valid non-debug keystore; local keystore.properties → debug keystore → release packaging BLOCKED on this machine (diagnostic output confirms: "NO VALID RELEASE KEYSTORE FOUND"); name-prefix coverage + deprecated taskGraph.whenReady under configuration cache not executed in this audit | no path signs release w/o upload key | none → TestKit test; verify under config cache | Med | verify guard fires; extend prefixes | N | Y |
| R-3 | IB | defaultConfig + secrets{} | 28-29,155-161 | GEMINI_API_KEY="" ; ENABLE_CLOUD_LLM=false; ignoreList blocks injection; .env/jks gitignored; placeholder rejected by gate | no key in APK | ReleaseBuildConfigSafetyTest; CloudLlmFeatureGateTest (note: test hardcodes AGP output path — may break on AGP change) | keep | Y | Y |
| R-4 | PV | ai/hybrid logging | DirectGeminiClientSeam 44-49,93,479; AiLogger | spot checks: only `credentialConfigured=<bool>`/masked values; exhaustive secret scan impossible this session (no grep) | no key in logcat | Byok/Seam tests → static secret-scan test | Low | add AIza/log-secret scanner test | N | Y |
| R-5 | AD | app/proguard-rules.pro | 4-8,20-23,29-40,54-60 | broad package keeps `-keep class com.example.ai.{proposal,contracts,tools,hybrid,orchestrator,appfunctions}.** { *; }` + @Entity/@Dao keep-all → hybrid (incl. credential storage) un-shrunk/un-obfuscated | keep only serialized/reflection surfaces | none (R8 output unasserted) → minified smoke test | Low-Med | narrow keeps + smoke test | Y | Y |

## AUDIT S — UI/UX (assistant focus)

| ID | Class | Claim/File | Lines | Observed (evidence) | Expected/SoT | Tests → missing | Sev | Remediation | Prod | Prot-OK |
|---|---|---|---|---|---|---|---|---|---|---|
| S-01 | IB | Sheet keyboard auto-open | 106-120,656-663 | NO FocusRequester/requestFocus/show() — keyboard follows explicit composer tap only | claim 1 satisfied | AssistantKeyboardBackBehaviorTest + source-gate | — | none | N | — |
| S-02 | IB | Sheet Back-vs-keyboard | 217-221,239-251 | keyboardOwnsBack → Back consumed → hide()+clearFocus first; later Back closes | claim 2 satisfied (key path) | 4 behavioral tests | — | none | N | — |
| S-03 | VR | Sheet keyboardDismissPending + PredictiveBackHandler | 113-120,88-94; VM 997-1021 | keyboardDismissPending never reset when IME hidden by other means → next Back swallowed; activity-level PredictiveBackHandler bypasses keyboard-first policy | reset on IME-hide; gesture path honors policy | key-path tests only → IME-hidden + swipe tests | Low | reset flag; gate gesture on IME | Y | Y |
| S-04 | DP | suggestion stacking | Sheet 125-140,589-607 | DISPROVEN: suggestions are derived `remember(messages,entries){build()}`, LazyRow outside list, keyed, capped 4; idle landing duplication asserted intentional by Gate12 | claim 3 satisfied | SuggestionsBarBehavior + Model tests | — | none (optional: key by cardId+action) | N | — |
| S-05 | IB | suggestion lifecycle | 135-140; AssistantSuggestions 39-85 | pure derivation, VM-backed session data; VM survives hide/show | claim 4 satisfied | AssistantLazyLifecycleTest | — | none | N | — |
| S-06 | IB | scrolling | Sheet 143-185,473-533; policy 37-59 | auto-scroll when near-latest; position preserved; 48dp new-message control | claim 5 satisfied | 5 behavior + 10 policy tests | — | none | N | — |
| S-07 | PV | tool/status layout | 391-464,511-532,227 | tool status is in-list item (append-only); suggestions bar reserves 56dp; BUT gateway banner conditional row resizes list area un-animated; sheet 0.80↔0.95 fill flip un-animated; stable testTags exist | no unexpected jumps | status-transition tests only → layout-stability test; Gate12 stateC renders synthetic Row not production code | Low | AnimatedVisibility/reserve height | Y | Y |
| S-08 | IB | error/loading determinism | VM 287-313,461-470 | single StateFlow; gateway 4-state init; turnId discard; UUID card ids | claim 7 satisfied | GatewayAsync + Hybrid #4 | — | none | N | — |
| S-09 | VR | context-blocked card spam | VM 530-548,382-389,648-650 | failed submits append SafeErrorCard unbounded; chips dispatch sendMessage → repeated identical cards + scroll churn | bounded/dedup error surface | none → repeated-submit test | Low | replace prior card / disable chips | Y | Y |
| S-10 | IB | mode transitions | VM 186-189,347-352,446-453 | cloud↔local switch preserves messages/cards/priorState; engine resolved per turn | claim 8 satisfied | LazyLifecycle + routing tests; explicit switch test missing | — | optional mid-conversation switch test | N | — |
| S-11 | VB | project context sync | VM open() 315-345; MainLayout 313-434 | on project switch messages KEPT verbatim, typedCards never cleared, no new greeting, stale-card actions tappable under B; NO automatic open() on project change | claim 9 violated (≈O-02) | none (ProjectIsolationTest stub) → A/B assistant test | Med | clear messages/typedCards on project change + hook | Y | Y |
| S-12 | PV | responsive | Sheet 82-83,226-227; VoltAtlasWindowWidth 23-75; AiSettingsDialog 62-70,121-143 | sheet adapts correctly (Compact full-width, widthIn 720dp otherwise); AiSettingsDialog fillMaxWidth no max (tablet stretch) + non-weighted chip Row (narrow overflow) | consistent phone+tablet | card goldens at 360/800 only → sheet+dialog tests at 320-360/≥840 | Low-Med | widthIn 560 + FlowRow | Y | Y |
| S-13 | VR | header truncation | Sheet 262-388 | no weight/maxLines/ellipsis on title/pill/subtitle vs MainLayout:628-646 which ellipsizes → long names can displace 48dp actions | ellipsis + weight | none → long-name test | Med | weight(1f)+ellipsis | Y | Y |
| S-14 | PV | touch targets | AiSettingsDialog 100-109,291-300 (32/36dp); Gate12:69-73; A11y:51-54 | dialog icon sizes 32/36dp (M3 min-interactive realization unproven); two tests compare raw px to 48f WITHOUT density | ≥48dp; dp-based assertions | partial → bounds tests | Low | 48dp sizes; density-correct asserts | Y | Y |
| S-15 | TG | profiles/goldens | build 246-324; AssistantScreenGoldenTest 35-260 | fast profile excludes ALL ai tests incl. assistant behavior; assistant goldens capture to file but assert nothing (no committed baselines — app/src/test/screenshots has 2 non-assistant pngs) | assistant UI exercised in normal loop; goldens verified | — → assert captures + full-sheet golden | Med | include ai.ui in fast or run gate in CI | N | Y |

Also verified (no defect): dark mode tokens theme-aware; contentDescription present on icon controls; IME insets via windowInsetsPadding (source-gated); predictive back retained+gated; empty/loading/disabled states deterministic.

## AUDIT T — SLD/DXF/report output consistency

| ID | Class | File:Function | Lines | Observed (evidence) | Expected/SoT | Tests → missing | Sev | Remediation | Prod | Prot-OK |
|---|---|---|---|---|---|---|---|---|---|---|
| T1 | VB | ElectricalNetworkGraphBuilder | 1275-1277 vs 1214-1217 | voltage label hardcoded while adjacent current uses real voltageV → internally contradictory SLD+DXF annotation (exporter reads dataBlock.voltage :1738) | label = voltageV | none → 415 V test | High | derive label (=D-01) | Y | Y |
| T2 | VB | same generator node | 197-227 | hardcoded 400 V + Ib formatted even when kVA unknown ("DATA REQUIRED" kVA + "0 A" Ib + "400 V" in one block) | no fabrication; Ib null when kVA unknown | none → zero-kVA test | Med | null Ib; voltage omit/derive | Y | Y |
| T3 | VR | sld/SldDxfExporter.kt (full read) | (no statusText ref) | DXF drops statusText entirely — exports carry no PASS/FAIL/DATA REQUIRED verdict that UI+PDF show | exports convey verdict | 8 DXF suites, none assert status → status test | Med | emit status line | Y | Y |
| T4 | PV | SldDxfExporter.formatUnit | 746-776 | appends unit to ANY non-blank string → latent "DATA REQUIRED A" if placeholder routed through (no current instance; builders use null) | units only on numeric strings | sanitization tests → non-numeric input test | Low(latent) | numeric-pattern guard | Y | Y |
| T5 | VR | pdf/Sections.kt | 186,189,366-374,411-417 | PDF prints 0.0 where UI shows "—"; ignores item.status; prints OD without validationMessages → presentation divergence (computation single-sourced) | identical missing/provenance semantics | structure/mapping tests → parity test | Med | reuse mapper rules; render messages | Y | Y |
| T6 | AD | engine/OtherEngines.generateSchedule vs PanelScheduleEngine | 485-497; wired VM:627-629 | flat panel schedule (hard 230 V, pf 0.8) feeds UiState.panels while PanelScheduleScreen/SLD use recursive+sizing path → potential divergence across surfaces | one schedule computation | PanelScheduleRecursiveTest, SldBoardRollupPlumbTest → parity test | Med | redirect consumers / prove panels dead (engine method protected) | Y | N(protected method)/Y(VM) |
| T7 | DD | SldLabelFieldLibrary vs builder comment | lib:97 vs builder:1215 | library documents 400 V hardcode as sanctioned; builder comment claims "never a hardcoded default" directly above code that hardcodes it | one documented truth | structure test | Med | fix alongside D-01 | Y | Y |
| T8 | DP | ai/tools/cable/CalculateCableSizingTool | — | DISPROVEN: AI cable tool → same coordinator → CableSizingEngine as UI/PDF — computation consistent (provenance gap shared with H6) | — | Batch1/NoHardcoded | Info | none (optional provenance string) | N | — |

## AUDIT U/V/W — maintainability, dead code, documentation drift

**U inventory (main source, exact; test tree spot-probed):** ≥500: 51 main files · ≥800: 29 · ≥1000: 24 · ≥1500: 6 · ≥2000: 3. Top: SldDxfExporter 2157, PowerSystemsScreen 2116, Coordinator 2002(protected), RiserLayoutEngine 1854, PanelScheduleScreen 1642, CircuitsGridScreen 1479, EngineeringRepository 1376, ElectricalNetworkGraphBuilder 1373.

| ID | Class | File | Observed (evidence) | Verdict | Tests → missing | Sev | Remediation | Prod | Prot-OK |
|---|---|---|---|---|---|---|---|---|---|
| AU-U-01 | mixed per file | see U table | responsibility mixes: EngineeringViewModel (state+persist+PDF+orchestration) → refactor-worth-it INCREMENTAL (strong tests); PowerSystemsScreen → leave (Compose-dense, covered; extract UI constants :45-54 only); PanelScheduleScreen → hoist in-composable engineering calc :817-827; CircuitsGridScreen → leave (mapper split exists); CircuitEditDialog → **RISKY** (dual route + 2 quarantined characterization tests) — no refactor; EngineeringRepository → refactor-worth-it (extract import/export; 8 test suites); SldDxfExporter → leave (8 suites, single domain); RiserLayoutEngine → leave (pure, covered); QuickNewToolsModels → leave pending Audit B; Coordinator → protected, assessment only | per file | existing suites named in audit | Med | incremental only, test-first | Y | Y (protected: no-touch) |
| AU-V-01 | PV | ai/service/FirebaseAiServiceImpl | Phase-1 stub; not built by composition root (== M-01/M-02) | quarantine; delete after ref search | none → guard if kept | Med | ref search → delete | N | Y |
| AU-V-02 | VR | ConversationEngineProvider:106 | runBlocking (== K-01) | suspend conversion | none | High | (K-01) | Y | Y |
| AU-V-03 | DD | ui/dialogs/CircuitEditDialog.kt 47-84 | KDoc says flag "false (default)" + "requires owner authorization"; literal `USE_MOBILE_CIRCUIT_EDITOR = true` (:57); legacy+mobile routes coexist; legacy branch compile-dead | correct KDoc/value with owner sign-off; NO deletion until characterization tests un-quarantined | RoutingContract + ProductionCutover; Legacy* QUARANTINED | Med | doc fix only now | Y | Y (file itself unprotected; legacy test files quarantined not deleted) |
| AU-V-04 | TG | 3 quarantined suites (629+629+232 lines) | excluded from fast+gate; run only under diagnostic profile (documented, not silent) | un-quarantine when Compose-idle fixed | they exist (diagnostic only) | Med | fix harness → re-enable in gate | N | Y (do not delete) |
| AU-W-01 | DD | ui/screens/QuickEngineeringHubScreen.kt 54-61 vs 72-204 | KDoc "exactly 8 Batch 1 tools" vs actual 15 items/6 categories (registry) | fix KDoc (+ registry-size test) | navigation test asserts self-built list → registry-driven test | Low | KDoc + test | Y | Y |
| AU-W-02 | DD | sld/SldLabelFieldLibrary.kt 64-72,152-169,179-187 | ≥3 populationSource claims false: busbar field exists (Entities:85 + exporter:919-940); frame designation exists (BreakerEntry.frame); grid fault input exists (gridFaultMVA) | correct entries (+ truth test) | structure test → entity-assertion test | Med | update library | Y | Y |
| AU-W-03 | DD | ai/contracts/ExecutionModePolicy.kt 8-20 | Phase-3/7 comment references non-existent classes (EngineeringProposalManager, EngineeringRevisionGuard); behavior unchanged (false) | rewrite comment as permanent invariant (== J-09) | contracts tests | Low | comment fix | Y | Y |
| AU-W-04 | DD | OnDeviceNanoConversationEngine.kt 11-18 | ML Kit/Nano KDoc vs keyword parser (== L-02) | qualify KDoc | tests | Low-Med | (L-02) | Y | Y |

**VOLATLAS_DEAD_CODE_AUDIT.md content (no deletion without proof):**

| Candidate | Reachability evidence | Class | Safe-to-delete? |
|---|---|---|---|
| ai/service/FirebaseAiServiceImpl (35 ln) | not built by ConversationEngineProvider:51-127; Phase-1 stub KDoc | PV | N — repo-wide ref search needed (grep broken this session) |
| ai/service/EngineeringAiService (44 ln) | only implementor = FirebaseAiServiceImpl; no composition-root usage | PV | N — same proof + VM unused import :32 |
| OnDeviceNanoConversationEngine + NanoClientSeam | composition root passes no nanoEngine; test-only construction | PV | N — ref search + OnDeviceNanoConversationEngineTest impact |
| LegacyCircuitEditDialogInternal branch (CircuitEditDialog:75-83) | compile-dead while USE_MOBILE_CIRCUIT_EDITOR=true | AD | N — un-quarantine 2 characterization tests first |
| DataSheetEntry legacy ctor+aliases (Models:28-51) | aliases cross-map R_Cu70→R_Cu20 etc.; needs data_sheet.json column check | PV | N |
| Catalog.resetForTesting (Models:429-442) | test-only helper, used by test rules | IB | N/A |
| Duplicate panel-schedule paths | UI delegate vs engine; UiState.panels consumer inventory unverifiable (no grep) | PV | N — ref search (T6) |
| Dormant AppStateDao write path (O-11) | written, never read at startup | AD | N — decide restore-vs-delete |
| @Deprecated/@Ignore sweep | tooling-limited this session | NC | re-run with working search |

---

## Cross-cutting DISPROVEN / INTENTIONAL (allow-list)

- **DISPROVEN:** F-06 (auto-design jank), O-05 (coordinator cache leak), O-08 (AI tool cross-project), O-09 (import merge leak), S-04 (suggestion stacking), T8 (AI vs UI computation divergence), J-05..J-08 controls are working (not defects), Q-1 backup exclusions correct.
- **INTENTIONAL:** A-10 fallback mechanism, I-06 standard ladders, AB-B24 motor-starter standalone, E-05 single-gen inference (needs warning only), E-10/E-11 visual-only fallbacks, H5 estimation bounded away from sizing, F-04 `flowOn(Dispatchers.Default)` (PRESERVE), F-05 revision gating, N-01 AI-scoped hardcode test, P-2 identity=attribution, R-3 no-key-in-BuildConfig, S-01/02/05/06/08/10 assistant behaviors verified good.

## NOT_CHECKABLE / environment-limited

- I-10 exhaustive embedded-table inventory (ripgrep broken; Python+targeted reads used).
- O-07 unscoped-flow consumer inventory; R-4 exhaustive secret scan; M-02 absolute unreachability proof; AU-V-01 same — all blocked on repository-wide search tooling; each marked PV/NC with re-verification step.
- Lint findings status: NOT_RUN (no report exists) — command `gradlew.bat :app:lintDebug`.
