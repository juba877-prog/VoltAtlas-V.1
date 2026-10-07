# Engineering Change Ticket (ECT) Ledger

## Phase C entries (data safety — no engineering engine touched)

Phase C closed the **data-safety** gate only. Neither entry below touches a Tier F file, so neither
required a per-ticket Tier F scope authorization.

| ECT | Finding | Decision | Files | Tier F touched | Result |
|---|---|---|---|---|---|
| `ECT-KT-02` | Room schema export disabled; 25 migrations with zero `MigrationTestHelper` coverage | D-n/a (data safety) | `AppDatabase.kt`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, **new** `app/schemas/**`, **new** `RoomSchemaDisciplineTest.kt`, **new** `RoomMigrationInstrumentedTest.kt` | **No** | schema export `PASS`; migration verification `NOT TESTED` / partially `NOT RECONSTRUCTABLE` |
| `ECT-SEC-02` | Backup policy unverified; export/import losslessness unproven | **D6 = Option A** | **new** `BackupPolicyContractTest.kt`, **new** `ProjectExportImportRoundTripTest.kt` | **No** | `PASS` 11/11 |

Both entries are build/persistence-layer only. No calculation, constant, tolerance, c-factor or
formula was modified, so the Golden diff is empty and no engineering authorization was required.

### Per-ticket detail

**`ECT-KT-02`**

- Root cause: `exportSchema = false` plus no KSP `room.schemaLocation`; `MigrationTestHelper`
  referenced 0 times.
- Characterization test: `RoomSchemaDisciplineTest` (7 tests) — passes on the unmodified code except
  the two that require the new schema files, so it was written alongside the fix and is not a
  pre-change characterization.
- Target test: `RoomMigrationInstrumentedTest` (6 tests) — **not executed**, see `KT-02.md` §8.
- Expected Golden diff: **EMPTY**.
- Rollback: revert `exportSchema`, delete the `ksp` block and the three `sourceSets` entries, delete
  `app/schemas/`.
- Owner signature: not required — no Tier F file in scope, and the owner authorised Phase C data
  safety explicitly.

**`ECT-SEC-02`**

- Root cause: the D6 backup rules existed but nothing asserted them, and no test proved the
  export/import contract preserved all project-scoped state.
- Characterization test: none pre-existing was adequate (`ImportExportTest` has a single round-trip
  test; `ImportDefaultParityTest` covers default application only).
- Target test: `BackupPolicyContractTest` (5) + `ProjectExportImportRoundTripTest` (6) — all pass.
- Expected Golden diff: **EMPTY**.
- Rollback: delete both test files. No production change was made, so rollback is total.
- Owner signature: D6 approved by the owner (Option A).

### Residual items requiring a NEW ECT and, in some cases, Tier F authorization

| Item | Why it needs a ticket |
|---|---|
| Historical migrations 1→2 … 24→25 verification | `NOT RECONSTRUCTABLE` from repository evidence. Needs a device run to regenerate real schema JSONs, or an owner decision to accept the residual risk. |
| 25→26 migration execution | `NOT TESTED` — needs a device/emulator, or the `SQLiteDriverMigrationTestHelper` path described in `KT-02.md` §8.3 |
| `app/src/androidTest` does not compile | Pre-existing (`PdfReportInstrumentedTest.kt:43` calls a 4-argument-short `SizingResult`). Unrelated to Phase C and deliberately **not** fixed. Must be fixed before `connectedAndroidTest` can ever run. |
| Zero foreign keys on project-scoped tables | Referential integrity is application-enforced only. Partially covered by SEC-02; a schema-level fix would be a `Room` migration and needs its own ECT. |

No engineering ECTs are authorized yet.

---

## Phase C remediation entries (2026-10-05) — test infrastructure only

| ECT | Finding | Files | Tier F touched | Result |
|---|---|---|---|---|
| `ECT-KT-02-REM` | `app/src/androidTest` did not compile: `PdfReportInstrumentedTest.kt` omitted four non-defaulted `SizingResult` verdict parameters | `app/src/androidTest/java/com/example/pdf/PdfReportInstrumentedTest.kt`, `app/src/androidTest/java/com/example/data/RoomMigrationInstrumentedTest.kt` | **No** | `compileDebugAndroidTestKotlin` **PASS** |
| `ECT-KT-02-SCHEMA` | Room schema history untracked | `app/schemas/**` (commit `0d01806`) | **No** | committed, isolated |

Neither entry touches production code. The `SizingResult` fix is a **test-fixture update to match the
current production constructor** — the production class was not modified, no compatibility
constructor or default was added, and no assertion was weakened. The four values were derived from
the fixture's own data following the engine's convention at `CableSizingEngine.kt:865-882`.

Commit `0d01806` contains exactly 3 files, all under `app/schemas/`. Verified before staging that
nothing else was staged. No KT-12 commit was amended or squashed. The commit was amended once, to
strip a UTF-8 BOM that `Out-File -Encoding utf8` had written into the subject line.

### New findings recorded, NOT fixed

| Item | Why not fixed |
|---|---|
| `kotlinx-serialization-json` pinned to **1.7.3** while `androidx.room:room-migration:2.8.5` (pulled in by `room-testing`) requires ≥ 1.8.0 → `AbstractMethodError` at runtime, blocking all 4 migration tests | It is an `implementation` (production) dependency. Bumping a serialization runtime across the whole app is outside the remediation's scope lock and needs its own authorisation. Diagnosis and recommended fix in `KT-02_REMEDIATION_2026-10-05.md` §5. |
| `ExampleInstrumentedTest.useAppContext` asserts `com.example` but the app is `com.voltatlas.app.v1.debug` | Pre-existing, unrelated to KT-02, outside the scope lock. |

### Residual KT-02 state

- 25 → 26 migration: **`NOT TESTED`** — blocked on the serialization alignment above.
- 1 → 24 historical hops: **`NOT RECONSTRUCTABLE`** — no historical schema JSON was ever committed and
  none was manufactured.
- KT-02 overall: **PARTIALLY CLOSED / BLOCKED**.

## Governance note (recorded 2026-10-04, Phase 0)

The Tier F manifest (`docs/audit/protected_files.sha256`, 27 entries) contains all 24 tracked files under
`app/src/main/java/com/example/engine/` plus the two circuit-editor UI files and one test. Therefore **Tier E is
currently empty**: every engine fix (EE-01..EE-10, KT-01, KT-05, KT-06, KT-08) touches a Tier F file and requires a
per-ticket scope authorization under Plan Section 2.3(a) before the ECT can be opened. Until the owner signs such
an authorization for a specific file, the finding is NEEDS-DECISION, not fixable.

## ECT template

    ECT id:            ECT-<finding id>
    Finding / decision: <EE-xx or KT-xx> / <D-number>
    Files:             <exact paths>
    Characterization test: <name>  (must pass on the unmodified code)
    Target test:       <name>      (must fail on the unmodified code)
    Expected Golden diff: <circuits and PASS/FAIL flips expected>
    Rollback:          <commit to revert>
    SHA-256 before:    <per file>
    SHA-256 after:     <filled in after the change>
    Owner signature / date: <required BEFORE first edit>

## Entries

### ECT-EE-02 — short-circuit fault-location / topology semantics

    ECT id:            ECT-EE-02
    Finding / decision: EE-02 / D2
    Files (Tier F):    app/src/main/java/com/example/engine/ShortCircuitEngine.kt
    Files (test):      app/src/test/java/com/example/engine/ShortCircuitFaultLocationTest.kt   (new)
                       app/src/test/java/com/example/ShortCircuitEngineTest.kt                (modified — see NOTE)
    Files (audit):     docs/audit/EE-02.md, docs/audit/ECT_LEDGER.md, docs/audit/DECISIONS.md
    Characterization test: EE02 characterization cases in ShortCircuitFaultLocationTest
                           (must PASS on the unmodified code)
    Target test:       EE02 near-end excludes the feeder / far-end includes it
                       (must FAIL on the unmodified code)
    Discrimination:    EE02 regression discrimination — re-including the feeder in the near-end
                       fault must make the test fail
    Expected Golden diff: EMPTY. golden_baseline.json Isc entries are TRANSFORMER cases only
                       (TC-TX-01.Isc_3ph_kA, TC-TX-02.Isc_3ph_kA) produced by TransformerEngine,
                       which this ECT does not touch. Golden explicitly records EE-02 as having
                       no authoritative after-oracle, so proof is differential/topological.
    Rollback:          the dedicated EE-02 commit (recorded below after creation); the tree is
                       otherwise uncommitted, so `git checkout HEAD -- <file>` restores the
                       pre-ECT blob.
    SHA-256 before:    ShortCircuitEngine.kt = 033949f2f3debdffd0c0e1ee18460acfb67bcd5aae6e4740c50e3c1e475163a4
                       git blob SHA-1       = adb2bac6b11a432a7e15a95b779e7d706ae4c834
                       (worktree verified identical to HEAD before the edit)
    SHA-256 after:     ShortCircuitEngine.kt = 4702a3712346cee80e88dc99eee89add1341a0c54934f4fa92219fd553b77ade
                       git blob SHA-1       = ebbf2edf665e422e6a45a842e2220931b740aa2c
                       bytes                = 20106 (before: 16896)
                       manifest line 14 updated to this hash; the other 26 lines untouched.
                       Tier F re-verified 27/27 PASS, working-tree drift NONE, VERIFY_EXIT=0.
    Commit:            4da9e0341ac5b8c41530ef5ff592a80462e03197
                       "EE-02: correct short-circuit fault-point semantics"
                       parent 884a278e43daed47394609efcd8847d8ccb71170 (no amend), exactly 3 files.
    Owner signature / date: 2026-10-05 — owner authorized Phase D / ECT-EE-02 and D2 explicitly
                       ("AUTHORIZE SPLIT FAULT-LOCATION ARCHITECTURE"). Recorded in
                       docs/audit/DECISIONS.md reconciliation note, dated 2026-10-05.

**Exact Tier F scope and why it is exactly one file.**

Authorized: `ShortCircuitEngine.kt` only — the file named in the ticket as the expected primary file.

Not modified, deliberately, even though they consume the affected value:

| File | Why NOT modified |
|---|---|
| `ProtectionEngine.kt` | Tier F, not in this ECT's scope. Its Icu check (`:398`) and cable-withstand check (`:412`) read `shortCircuitResult.iscKA`, so they are corrected *by* the fix rather than by editing this file. |
| `CircuitUiModelMapper.kt` | Tier F circuit-editor file, explicitly protected. Its `isIscOk` (`:189`) reads `iscKA` and is likewise corrected without editing it. |
| `ProtectionEngine` ADS block (`:207-330`) | Already uses downstream cable resistance (r1 + r2) and never reads `iscKA`. CHECK 5 is already correct; changing it would be EE-04 scope. |
| `ConduitFillEngine.kt`, `CableSizingEngine.kt` | Explicitly out of scope. EE-06 / EE-03 untouched. |

No second Tier F file was discovered to be necessary. No database field, no schema migration, no
new topology type, no new constant is introduced.

**NOTE on the one modified existing test.** `ShortCircuitEngineTest.testShortCircuit_usesR20ConductorResistanceBasis_handCalculatedReference`
asserts `10.58 kA` on `res.iscKA`. That value is the **far-end** fault at the far end of a 100 m
feeder, and it is the only existing assertion whose meaning moves when `iscKA` becomes the near-end
(origin) fault point. The assertion is **relocated, not weakened**: the same 10.58 value, same
±0.02 tolerance, same hand calculation, same R20-intent is preserved on the new far-end field, and a
*stronger* near-end assertion is added alongside it. No assertion is deleted or loosened anywhere.

Recorded 2026-10-05, before the first source edit.

**CLOSURE RESULTS — recorded after the change (2026-10-05).**

| Gate | Result |
|---|---|
| Characterization on unmodified code | **PASS** — `characterization_singleFaultPointToday_isTheFarEndFaultAtBoardB` at 12:33:58 vs before-SHA `adb2bac6` |
| Target tests on unmodified code | **4 of 4 FAIL** as intended — `106.43` vs `11.208925` mΩ (9.5×); `3.29` vs `44.2364` kA (13.4×); `deviceCheck` PASS→FAIL; `cableCheck` PASS→FAIL |
| CHECK 5 on unmodified code | **PASS** (ADS already correct — unchanged by this ECT) |
| Focused suites after fix | 77 tests, **0 failures**, 1 pre-existing skip |
| Full gate runs (6) | RUN 1 **0** / RUN 2 1 (`EngineeringTaskManagerTest`) / RUN 3 **0** / RUN 4 2 (`EngineeringTaskManagerTest`) / RUN 5 **0** / RUN 6 1 (`Gate10TrustedMutationRevalidationTest`) — all 318 suites / 2855 tests / 14 skipped. Both failing suites pass **100 % in isolation** (67/67, 12/12), contain zero references to `ShortCircuitEngine`, and are pre-existing coroutine races — **not fixed here**, to be filed separately. `SldProjectResolutionTest` did not reproduce in any run. |
| Golden before / after | 5 oracle suites, 36 tests, **0 failures** both; `golden_baseline.json` unchanged → Golden diff **EMPTY** |
| lintDebug | **0** — BUILD SUCCESSFUL, 0 `Error:` lines |
| lintRelease | **0** — BUILD SUCCESSFUL, 0 `Error:` lines |
| compileReleaseKotlin | **0** — BUILD SUCCESSFUL |
| Tier F before / after | **27/27** / **27/27**, drift NONE, exit 0 |
| Engine diff scope | exactly 1 file; forbidden-pattern scan (`drop(1)`, `excludeSelfCable`, `removeAt`, `subList`, `const val`) = **0** matches |
| Commit | `4da9e0341ac5b8c41530ef5ff592a80462e03197` — 3 files, no amend, no schema files |

### ECT-EE-06 — breaker Icu / final protection verdict integrity (R0)

    ECT id:            ECT-EE-06
    Finding / decision: EE-06 / D5 = PLAN DEFAULT (A)
    Tier F files:      NONE. Proven from docs/audit/protected_files.sha256 (27 entries):
                       all 24 engine/ files + 3 circuit-editor UI files. Neither
                       CableSizingEngine.kt (line 5) nor ProtectionEngine.kt (line 13)
                       needs editing — D5 explicitly leaves candidate selection alone,
                       and ProtectionEngine.finalStatus is already correct.
                       CircuitUiModelMapper.kt, Sections.kt, ProjectReportSnapshot.kt,
                       CircuitCardModelCache.kt, CircuitsGridScreen.kt,
                       EngineeringViewModel.kt are NOT in the manifest.
    Files (production): NEW     app/src/main/java/com/example/verdict/CircuitFinalVerdict.kt
                               (owner-directed neutral package — NOT under engine/)
                         ...    ui/screens/circuits/CircuitUiModelMapper.kt
                         ...    ui/screens/circuits/CircuitCardModelCache.kt
                         ...    ui/screens/CircuitsGridScreen.kt
                         ...    pdf/Sections.kt
                         ...    pdf/ProjectReportSnapshot.kt
                         ...    ui/EngineeringViewModel.kt   (ONE line; partial-staged,
                                     see STAGING NOTE)
    Files (test):      NEW     app/src/test/java/com/example/ProtectionVerdictCharacterizationTest.kt
                         ...    target / matrix suite (added with the fix)
    Files (audit):     docs/audit/EE-06.md, docs/audit/ECT_LEDGER.md
    Characterization test: ProtectionVerdictCharacterizationTest
                           (MUST PASS on the unmodified code)
    Target test:       D5 contract — Ib <= In, Isc > Icu, candidate unchanged,
                           final circuit verdict MUST NOT be PASS
                           (MUST FAIL on the unmodified code)
    Expected Golden diff: EMPTY for engineering numbers. Ib, In, Iz, cable/device
                           selection, deviceBreakingKA (Icu), iscKA (Isc), vdPct,
                           utilizationPct and all protection metrics must be
                           BYTE-IDENTICAL before/after. Only status/verdict
                           propagation changes. Any numerical delta = HARD STOP.
    Rollback:          `git checkout <EE-06 commit> -- <file>` for the files above, and
                           delete verdict/CircuitFinalVerdict.kt. No Tier F file, no
                           schema, no constant, no formula is touched, so rollback is total.
    SHA-256 before:    CircuitUiModelMapper.kt      063b26a06abcd9df712cd68d18b4c428d92e01b9974d7b4bed188da3d7ff1cd7
                       CircuitCardModelCache.kt    a68f595ab94475a646774b028aaa10f4af0bf9d10947232091aa186a0e7338ce
                       CircuitsGridScreen.kt       0a982a2aa9d4760fe19fa4d55a2e1562150a4be255946410b3fcc15dfa18ae94
                       Sections.kt                 44053079344f1ee96ddb427e415ebd9afaff81ad7a37447984c7e831aac919e9
                       ProjectReportSnapshot.kt    23f871c276e2c8a412613905778d1fdff259fe4177e2e3ff6eb711b90e9993c8
                       EngineeringViewModel.kt     53f91d7241570948182925f3aa2095af34384c389fe55e7482692a236cfd2208
                                                    (already dirty pre-ECT — see STAGING NOTE)
    Safety precheck:   HEAD 4da9e0341ac5b8c41530ef5ff592a80462e03197 (EE-02 intact, not
                           amended), branch audit/final-release-2026-10-04, staged 0,
                           engine tree clean, Tier F 27/27 PASS / TIERF_EXIT=0.
    Owner signature / date: 2026-10-05 — owner authorized ECT-EE-06 with D5 = PLAN
                           DEFAULT (A). Two scoping answers recorded during the read-only
                           audit: (1) the canonical final-verdict resolver lives in a NEW
                           NEUTRAL PACKAGE `com.example.verdict` — deliberately not under
                           engine/, so nothing new lands in the Tier F directory;
                           (2) CircuitUiModelMapper:189 `isIscOk` IS to be re-sourced from
                           the authoritative ProtectionResult.shortCircuitStatus rather
                           than re-deriving Isc <= Icu in the UI.
    Recorded:           2026-10-05, BEFORE the first production source edit.

**Exact scope and why it is exactly these files.**

| File | Why it is in scope |
|---|---|
| `verdict/CircuitFinalVerdict.kt` (NEW) | §13 requires ONE canonical source. Neither `SizingResultStatus` nor `ProtectionResult.finalStatus` alone can express "sizing PASS + protection FAIL ⇒ FAIL", so §13's own escape clause applies. Pure status combination: no IEC formula, no new constant ⇒ not an engine; it *reads* `ProtectionResult.finalStatus` ⇒ not a second protection engine; one function consumed by every surface ⇒ not a parallel status architecture. |
| `CircuitUiModelMapper.kt` | The named defect. `resolveCircuitStatus(:27-36)` read only `SizingResult.resultStatus`, so `mapToCardUiModel(:71)`, the project summary `(:254-305)` and the status filter could never see a protection FAIL. **Not Tier F** (proven in `EE-06.md` §A1). |
| `CircuitCardModelCache.kt` | Must key on protection too, or a stale card survives a protection re-run. |
| `CircuitsGridScreen.kt` | Only place that can supply `state.protectionResults[circ.id]` (available at `UiState.kt:56`) to the card and summary. |
| `Sections.kt` | False-PASS surfaces S5 (`:107-111`) and S6 (`:170`). |
| `ProjectReportSnapshot.kt` | Has no `protectionResults` field; all fields are defaulted, so adding one is source-compatible. No schema change. |
| `EngineeringViewModel.kt` | Sole construction site of the snapshot (`:546`). **ONE line.** |

Not modified, deliberately:

| File | Why NOT modified |
|---|---|
| `CableSizingEngine.kt` | **Tier F.** D5 keeps candidate selection exactly as found (`:902-907` keys on `rating_A >= deviceSizeA`; `icu_kA` only stored at `:912`). `isPass = isAmpacityPass && isVdPass` is *correct* — it is the SIZING verdict, not the circuit verdict. |
| `ProtectionEngine.kt` | **Tier F.** `finalStatus` (`:453-458`) already returns FAIL for `!scPass` and `DATA MISSING` for missing Isc. It is the source of truth; it is wired, not edited. |
| `ShortCircuitEngine.kt` | **Tier F, frozen by EE-02.** `deviceCheckOk` (`:237`) untouched. |
| `ProtectionCalculationService.kt`, `EngineeringCalculationCoordinator.kt` | **Tier F.** Not required. |
| `AppDatabase.kt` | Pre-existing unrelated KT-12 change; **not staged**. §7 forbids Room-file changes. |
| `ProtectionScreen.kt`, `ElectricalNetworkGraphBuilder.kt`, `ProjectContextProviderImpl.kt` | Already honour `ProtectionResult.finalStatus` (see `EE-06.md` §A4). |

**STAGING NOTE — `EngineeringViewModel.kt` carries pre-existing uncommitted KT-12 work**
(test-only `AppDatabase` injection seam + the `retryStartup()` synchronous-reset race fix),
which is unrelated to EE-06. The owner directed **partial staging**: the EE-06 commit takes
HEAD + only the one added `protectionResults = currentState.protectionResults` line; the
KT-12 changes remain uncommitted in the worktree exactly as they were before this ECT. The
staged diff is verified line-by-line before commit. `AppDatabase.kt` is not staged at all.

**Recorded 2026-10-05, before the first source edit.**

---

**Final status — `CLOSED — VERIFIED` (recorded 2026-10-06, after owner-authorized remediation
and full re-verification; was `BLOCKED — VERIFICATION` from 2026-10-05 until the remediation —
the original blocked record for `89d31446…` is preserved here below and in `EE-06.md` PART D
D0–D6).**

Commit created per §12: `89d31446a104b694350fd3c34a12bfb1f77db364` (parent
`4da9e0341ac5b8c41530ef5ff592a80462e03197`, subject `EE-06: make final circuit verdict
protection-aware`, 12 files, `1660 insertions(+), 60 deletions(-)`, branch
`audit/final-release-2026-10-04`, not pushed). Every engineering criterion passed under the
mandated profile: full gate ×2 (320 suites / 2877 tests / 0 failures / 0 errors / 14
skipped each), lintDebug (9m28s, 30/30), lintRelease (8m40s, 17/17), compileReleaseKotlin
(2m39s, 8/8), EE-06 focused **15/15**, CircuitsGridScreenC2Test **18/18**, Golden
differential **EMPTY** (exactly the four verdict deltas, zero engineering-value movement),
Tier F **27/27** before and after the commit, `git diff --cached -- engine/` EMPTY, staged
set exactly the 12 approved files (`EngineeringViewModel.kt` `+3` only, §8 harness proven),
`git diff --cached --check` PASS, EE-02 intact.

Closure is withheld for ONE blocker found by the §14 pristine-checkout proof: the commit
contains **11 lines of KT-12 work** inside `CircuitsGridScreenC2Test.kt` —
`import com.example.data.AppDatabase` plus the self-labeled `// KT-12:` `@After tearDown()`
calling `AppDatabase.resetInstanceForTesting()`, a function that exists **only** in the
uncommitted KT-12 `AppDatabase.kt:1142`. Attribution is proven by
`docs/audit/KT-12_RESIDUAL_1C.md` §2, where KT-12's own `add_teardowns.ps1` names this exact
file. A pristine checkout of the commit therefore fails `:app:compileDebugUnitTestKotlin`
(`CircuitsGridScreenC2Test.kt:155:21 Unresolved reference 'resetInstanceForTesting'`). Two
§18 hard stops apply — KT-12 entered the commit, and the post-commit proof depends on
unrelated dirty changes — so per §18/§19 **no amend, no second commit and no fix-forward**
were performed; remediation options await owner authorization (`EE-06.md` PART D, §D6).

Secondary observation, out of scope and pre-existing at this entry's parent (executed at
both `4da9e03` and `89d3144`): a pristine checkout of any branch head fails *configuration*
because committed `app/build.gradle.kts:394,404` reference `libs.androidx.room.testing` while
the catalog line exists only in the uncommitted `gradle/libs.versions.toml` (`EE-06.md` D4) —
needs its own owner decision, not an EE-06 change. *(Still true after remediation: the catalog
line remains uncommitted and was supplied only to the §10 clean worktree as a documented,
owner-authorized one-line exception — see the closure block below.)*

**Remediation and closure (2026-10-06, owner-authorized Option 1 — index-only partial-restage + amend).**

Commit `89d31446a104b694350fd3c34a12bfb1f77db364` was replaced — never soft-reset, never
second-committed — by **`d5c3db21118f582a24257d9eba96f6b6b89c78c5`** via index-only
partial-restage of `CircuitsGridScreenC2Test.kt` (drop `import com.example.data.AppDatabase`,
drop the self-labeled `// KT-12:` 10-line tearDown, restore the trailing newline) followed by
`git commit --amend --no-edit`; **parent `4da9e0341ac5b8c41530ef5ff592a80462e03197` unchanged**,
mandated subject unchanged, **12 files, 1648 insertions(+), 59 deletions(-), exactly one commit**.
The worktree keeps the KT-12 block as an uncommitted remainder, byte-for-byte preserved
(C2 `12/1` unstaged, `EngineeringViewModel.kt` `47/3` unstaged, `AppDatabase.kt` seam never
staged/committed). Proofs after the amend: whole-commit marker scan **1648 added lines / 0 hits**;
staged set exactly the 12 EE-06 files; VM index hunk = the 3 approved lines only;
`git diff --cached --check` exit 0 (vs HEAD and vs parent); Tier F **27/27** post-amend;
`git diff HEAD -- engine/` EMPTY. §10 clean-tree proof at `d5c3db2` (pristine tree + the
owner-authorized D4 catalog line, `ee06_clean10.log`): the prior
`CircuitsGridScreenC2Test.kt:155:21 Unresolved reference 'resetInstanceForTesting'` failure is
**gone**, 0 compilation errors, EE-06 suites **53/53**, and the 33 remaining failures proven
pre-existing by an executed parent run at `4da9e03` (`ee06_parent10.log`: byte-identical
15-suite / 33-failure set = `PHASE0_BASELINE.md`, `KT-12_GateReliability.md:18`) ⇒ EE-06
introduces zero failures. §11: focused **15/15**, C2 **18/18**, gate ×2 green (runs 1 + 4;
run 4 XML **320 suites / 2877 tests / 0 failures / 0 errors / 14 skipped**), lintDebug 9m57s,
lintRelease 9m16s, compileReleaseKotlin 2m32s — all `EXITCODE=0`; two red gate runs disclosed
and classified (not retried away): `Gate10TrustedMutationRevalidationTest:612` byte-identical to
`EE-02.md:301`, and `EngineeringTaskManagerTest:862` — the EE-02-documented race suites, method
shifting = the scheduling signature proved at `EE-02.md:312-314` → separate tickets per EE-02's
owner-approved policy, both green in the final gate run. §12 Golden **EMPTY** (fresh record
byte-identical to the pre-remediation record, 0 numerical deltas, four verdict values verified);
§14 final audit `AUDIT14=PASS` (whitespace: commit-vs-parent exit 0; global exit 2 =
pre-existing `GlobalSettingsResponsiveTest.kt`, KT-12 §1-frozen, reported separately).
Full evidence: `EE-06.md` PART D **D7** (D0–D6 preserved as the historical blocked record).


### ECT-EE-01 — voltage-drop resistance temperature basis (R1)

    ECT id:            ECT-EE-01
    Finding / decision: EE-01 / D1 = APPROVED (70 °C PVC, 90 °C XLPE)
    Tier F files:      app/src/main/java/com/example/engine/CableSizingEngine.kt
                       (sole authorized Tier F file per owner authorization 2026-10-06)
    Files (test):      NEW app/src/test/java/com/example/engine/VoltageDropTemperatureBasisCharacterizationTest.kt
                       NEW app/src/test/java/com/example/engine/VoltageDropTemperatureBasisTargetTest.kt
                       MODIFIED app/src/test/java/com/example/CableSizingEngineTest.kt (oracle alignment)
    Files (oracle):    app/src/main/assets/qa_reference_cases.json
                       app/src/test/resources/qa_reference_cases.json
    Files (audit):     docs/audit/EE-01.md, docs/audit/ECT_LEDGER.md, docs/audit/protected_files.sha256
    Characterization test: VoltageDropTemperatureBasisCharacterizationTest
                           (PASSED 3/3 on unmodified code; @Ignored post-fix to preserve provenance)
    Target test:       VoltageDropTemperatureBasisTargetTest
                           (FAILED 3/3 on unmodified code; PASSED 3/3 on modified code)
    Expected Golden diff: Voltage-drop numbers (voltDrop_V, vdPct, cumulativeVDpct) increase across
                           cable circuits by the ratio of operating-temperature resistance:
                           PVC +19.65% (factor 1.1965 vs 1.0000);
                           XLPE +6.26% (factor 1.2751 vs 1.2000 estimated).
                           Zero movement on Isc, Icm, ip, Iw, ADS, or transformer metrics.
    Rollback:          git checkout <commit> -- app/src/main/java/com/example/engine/CableSizingEngine.kt
    SHA-256 before:    CableSizingEngine.kt = ef059d57554313206c8e0cfb361afde05d6036496e7d7f12a17be25a91505790
                       git blob SHA-1       = 1bc0925ad5fd2b245c4326076981a42518190e59
                       bytes                = 42921
    SHA-256 after:     CableSizingEngine.kt = 5216db2ac4bf0246b280d356d9940927eb1660b58685776ba1fcda57b3d3e849
                       git blob SHA-1       = 233be0e1430f7de1583234d1c5aad958f40363f0
                       bytes                = 43947
                       manifest line 5 updated to this hash; the other 26 lines untouched.
                       Tier F verified 27/27 PASS.
    Owner signature / date: 2026-10-06 — owner authorized ECT-EE-01 and D1 explicitly
                       ("PVC = 70 °C, XLPE = 90 °C; sole Tier F production file: CableSizingEngine.kt").
    Commit:            1b4c4c16b013f20e76131d874ebf44eefa5d4acc
                       "EE-01: correct voltage-drop resistance operating temperature basis (70C PVC, 90C XLPE)"
                       parent d5c3db21118f582a24257d9eba96f6b6b89c78c5, exactly 6 files.
    Gate Verification:
                       - Full Gate Run #1 (clean): 322 suites / 2883 tests / 0 failures / 0 errors / 17 skipped (7m 57s, EXITCODE=0)
                       - Full Gate Run #2 (clean): 322 suites / 2883 tests / 0 failures / 0 errors / 17 skipped (7m 13s, EXITCODE=0)
                       - Disclosed pre-existing race suites: EngineeringTaskManagerTest:862, AutoDesignProjectIsolationTest (both 100% green in isolation and in subsequent gate runs)
                       - lintDebug: BUILD SUCCESSFUL (0 errors, 0 fatal)
                       - lintRelease: BUILD SUCCESSFUL (0 errors, 0 fatal)
                       - compileReleaseKotlin: BUILD SUCCESSFUL (EXITCODE=0)
    Golden Differential:
                       - 8 reference cable cases examined (TC-CABLE-001..008)
                       - 8 cases with numerical delta (VD increased by ~4.5% to ~6.0% reflecting 90 °C operating temperature basis)
                       - 1 case with PASS -> FAIL flip: TC-CABLE-006 (cumulative VD rose from 2.9832% to 3.1492%, exceeding 3.00% limit)
                       - 0 cases with FAIL -> PASS
                       - 0 cases with cable-size changes
                       - 0 deltas in transformer cases or non-voltage-drop engineering metrics (Isc, Icm, ip, Iw, ADS)
    Final Status:      CLOSED — VERIFIED


### ECT-EE-03 — cable thermal clearing time (R1)

    ECT id:            ECT-EE-03
    Finding / decision: EE-03 / D3 = APPROVED (Option B — user-definable override with category fallback)
    Tier F files:      app/src/main/java/com/example/engine/CableSizingEngine.kt
                       (sole authorized Tier F file per owner authorization 2026-10-06)
    Files (production):
                       MODIFIED app/src/main/java/com/example/data/Entities.kt
                       MODIFIED app/src/main/java/com/example/data/AppDatabase.kt
                       MODIFIED app/src/main/java/com/example/data/EngineeringRepository.kt
                       MODIFIED app/src/main/java/com/example/ui/ProtectionScreen.kt
                       MODIFIED app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt
                       MODIFIED app/src/main/java/com/example/pdf/Sections.kt
                       NEW app/schemas/com.example.data.AppDatabase/27.json
    Files (test):      NEW app/src/test/java/com/example/engine/CableThermalWithstandCharacterizationTest.kt
                       NEW app/src/test/java/com/example/engine/CableThermalWithstandTargetTest.kt
                       MODIFIED app/src/test/java/com/example/DatabaseMigrationTest.kt
                       MODIFIED app/src/test/java/com/example/data/RoomSchemaDisciplineTest.kt
                       MODIFIED app/src/test/java/com/example/ui/dialogs/circuiteditor/spec/CircuitEntityActivatedPathCoverageTest.kt
                       MODIFIED app/src/test/java/com/example/ui/dialogs/circuiteditor/spec/CircuitEntityMapperSpecTest.kt
                       MODIFIED app/src/test/java/com/example/ui/screens/circuits/CircuitsGridCircuitEditorIntegrationTest.kt
    Files (audit):     docs/audit/EE-03.md, docs/audit/ECT_LEDGER.md, docs/audit/protected_files.sha256
    Characterization test: CableThermalWithstandCharacterizationTest
                           (PASSED 3/3 on unmodified code: category defaults, result carrying, downstream consumption)
    Target test:       CableThermalWithstandTargetTest
                           (PASSED 7/7: defaults, override flag, invalid input rejection, margin/verdict flip, export/import round-trip, UI/PDF disclosure, migration 26->27 round-trip)
    Expected Golden diff: EMPTY (when override is null, exact category defaults MCB 0.01s, MCCB 0.1s, ACB 0.3s apply)
    Rollback:          git checkout <commit> -- app/src/main/java/com/example/engine/CableSizingEngine.kt app/src/main/java/com/example/data/Entities.kt app/src/main/java/com/example/data/AppDatabase.kt app/src/main/java/com/example/data/EngineeringRepository.kt app/src/main/java/com/example/ui/ProtectionScreen.kt app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt app/src/main/java/com/example/pdf/Sections.kt
    SHA-256 before:    CableSizingEngine.kt = 5216db2ac4bf0246b280d356d9940927eb1660b58685776ba1fcda57b3d3e849
    SHA-256 after:     CableSizingEngine.kt = 2c640b16a98bc411e8cbf41bf2557b7908323c5eafd1c95b27d57e4826547334
                       git blob SHA-1       = fe18a79ee7706bcb2f176a277916330f30d0a67e
                       bytes                = 44706
                       manifest line 5 updated to this hash; the other 26 lines untouched.
                       Tier F verified 27/27 PASS.
    Owner signature / date: 2026-10-06 — owner authorized ECT-EE-03 and D3 Option B explicitly.
    Commit:            "EE-03: make cable thermal clearing time explicit"
    Gate Verification:
                       - Full Gate Run #1: 322 suites / 2894 tests / 0 failures / 0 errors / 17 skipped (3m 3s, EXITCODE=0)
                       - Full Gate Run #2: 322 suites / 2894 tests / 0 failures / 0 errors / 17 skipped (3m 3s, EXITCODE=0)
                       - lintDebug: BUILD SUCCESSFUL (0 errors, 0 fatal)
                       - lintRelease: BUILD SUCCESSFUL (0 errors, 0 fatal)
                       - compileReleaseKotlin: BUILD SUCCESSFUL (EXITCODE=0)
    Golden Differential:
                       - EMPTY. Zero changes to baseline reference cases TC-CABLE-001..008.
    Final Status:      CLOSED — VERIFIED


### ECT-EE-07 — neutral / CPC sizing & thermal withstand integrity (R1)

    ECT id:            ECT-EE-07
    Finding / decision: EE-07 / D-EE-07 = APPROVE OPTION B — TRANSPARENT STANDARD DISCLOSURE
    Tier F files:      app/src/main/java/com/example/engine/CableSizingEngine.kt
                       (sole authorized Tier F file per owner authorization)
    Files (production):
                       MODIFIED app/src/main/java/com/example/engine/CableSizingEngine.kt
                       MODIFIED app/src/main/java/com/example/ui/ProtectionScreen.kt
                       MODIFIED app/src/main/java/com/example/pdf/Sections.kt
    Files (test):      NEW app/src/test/java/com/example/engine/NeutralCpcSizingCharacterizationTest.kt
                       NEW app/src/test/java/com/example/engine/NeutralCpcSizingTargetTest.kt
                       MODIFIED app/src/test/java/com/example/ui/screens/circuits/CircuitsGridCircuitEditorIntegrationTest.kt (teardown hygiene fix)
    Files (audit):     docs/audit/EE-07.md, docs/audit/DECISIONS.md, docs/audit/ECT_LEDGER.md, docs/audit/protected_files.sha256
    Characterization test: NeutralCpcSizingCharacterizationTest
                           (PASSED 6/6: geometric sizing defaults, harmonic sizing, 1P parity, Table 54.7 lookups, phase-only adiabatic withstand baseline)
    Target test:       NeutralCpcSizingTargetTest
                           (PASSED 10/10: override activation, flags in SizingResult, AUTO/null handling, strict standard size validation, rejection of non-numeric/negative/zero values, ADS R2 impedance reduction, phase withstand invariance)
    Expected Golden diff: EMPTY (when overrides are null/AUTO, exact standard Table 54.7 and reduced-neutral geometric sizing apply)
    Rollback:          git checkout <commit> -- app/src/main/java/com/example/engine/CableSizingEngine.kt app/src/main/java/com/example/ui/ProtectionScreen.kt app/src/main/java/com/example/pdf/Sections.kt
    SHA-256 before:    CableSizingEngine.kt = 2c640b16a98bc411e8cbf41bf2557b7908323c5eafd1c95b27d57e4826547334
                       git blob SHA-1       = fe18a79ee7706bcb2f176a277916330f30d0a67e
    SHA-256 after:     CableSizingEngine.kt = dbfd571824e6a86d85a5c51f65e673fcf51d0fc2d628f09f24e449fd0fbc1c9f
                       git blob SHA-1       = adc154883ace03a72ca289367dc15f81620fc45c
                       manifest line 5 updated to this hash; the other 26 lines untouched.
                       Tier F verified 27/27 accounted for.
    Owner signature / date: 2026-10-06 / 2026-10-07 — owner authorized ECT-EE-07 and D-EE-07 Option B explicitly.
    Engineering basis:
                       - Conductor area summation (S_effective = S_phase + S_neutral + S_cpc) REJECTED as electrically invalid.
                       - No synthetic fault-current modeling.
                       - ProtectionEngine.kt and ShortCircuitEngine.kt UNCHANGED.
                       - Transparent disclosure in UI and PDF.
    Gate Verification:
                       - Full Gate Run #1: 326 suites / 2910 tests / 0 failures / 0 errors / 17 skipped (6m 47s, EXITCODE=0)
                       - Full Gate Run #2: 326 suites / 2910 tests / 0 failures / 0 errors / 17 skipped (7m 07s, EXITCODE=0)
                       - lintDebug: BUILD SUCCESSFUL (0 errors, 0 fatal)
                       - lintRelease: BUILD SUCCESSFUL (0 errors, 0 fatal)
                       - compileReleaseKotlin: BUILD SUCCESSFUL (EXITCODE=0)
    Golden Differential:
                       - EMPTY. Zero changes to baseline reference cases.
    Final Status:      CLOSED — VERIFIED

---

### ECT-KT-06 — String-Typed Engineering Domain State Normalization (R1)

    ECT id:            ECT-KT-06
    Finding / decision: KT-06 (Approved Boundary Normalization)
    Tier F files:      NONE
                       (Tier F engines remain strictly frozen: 0 drift)
    Files (production):
                       NEW app/src/main/java/com/example/data/DomainNormalization.kt
                       MODIFIED app/src/main/java/com/example/data/EngineeringRepository.kt
                       MODIFIED app/src/main/java/com/example/ui/dialogs/circuiteditor/mapper/CircuitEntityMapper.kt
                       MODIFIED app/src/main/java/com/example/ui/dialogs/CircuitEditDialog.kt
    Files (test):      NEW app/src/test/java/com/example/engine/DomainStringNormalizationCharacterizationTest.kt
                       NEW app/src/test/java/com/example/engine/DomainStringNormalizationTargetTest.kt
    Files (audit):     docs/audit/KT-06.md, docs/audit/DECISIONS.md, docs/audit/ECT_LEDGER.md
    Characterization test: DomainStringNormalizationCharacterizationTest
                           (PASSED 3/3: harmonicPhaseFactor startsWith("3") vulnerability, conductorMaterial synonym catalog lookup failure, phases contains("3P") vulnerability)
    Target test:       DomainStringNormalizationTargetTest
                           (PASSED 8/8: phase normalization to 3P calculation across variants, single phase normalization, material synonyms Cu/Al, insulation synonyms XLPE/PVC, harmonic derating across variants, conduit catalog equivalence, authoritative engine conduit filtering)
    Expected Golden diff: EMPTY (Golden tests evaluate calculation results; normalization ensures inputs match catalog expectations)
    Rollback:          git checkout <commit> -- app/src/main/java/com/example/data/EngineeringRepository.kt app/src/main/java/com/example/ui/dialogs/circuiteditor/mapper/CircuitEntityMapper.kt app/src/main/java/com/example/ui/dialogs/CircuitEditDialog.kt; rm app/src/main/java/com/example/data/DomainNormalization.kt
    Owner signature / date: 2026-10-07 — Phase E KT-06 boundary normalization authorized.
    Engineering basis:
                       - Critical electrical domain strings (phase systems, conductor materials, insulation types, cable catalog keys, conduit types) normalized at entity ingestion and UI boundaries.
                       - Room schema preserved (no column type alterations or migration required, AppDatabase hash unchanged).
                       - Frozen Tier-F calculation engines preserved (ConduitFillEngine defect P1-08 resolved under companion ECT-D-EE-11).
                       - Preserves lossless round-trip serialization in importProjectFromJsonDetailed per SEC-02 / D6.
    Gate Verification:
                       - Full Gate Run #1: 332 suites / 2943 tests / 0 failures / 0 errors / 17 skipped (8m 24s, EXITCODE=0)
                       - Full Gate Run #2: 332 suites / 2943 tests / 0 failures / 0 errors / 17 skipped (8m 01s, EXITCODE=0)
                       - lintDebug: BUILD SUCCESSFUL (0 errors, 0 fatal)
                       - lintRelease: BUILD SUCCESSFUL (0 errors, 0 fatal)
                       - compileReleaseKotlin: BUILD SUCCESSFUL (EXITCODE=0)
    Tier-F Manifest Verification:
                       - Exactly 1 Tier-F engine file (ConduitFillEngine.kt) opened under authorized ECT-D-EE-11; all other 26 files untouched.
    Golden Differential:
                       - EMPTY. Zero changes to baseline reference cases.
    Final Status:      CLOSED — VERIFIED

---

### ECT-D-EE-11 / P1-08 — ConduitFillEngine Conduit Material Filter Restoration

    ECT id:            ECT-D-EE-11
    Finding / defect:  P1-08 (I-03 / AB-B09) / D-EE-11
    Tier F files:      MODIFIED app/src/main/java/com/example/engine/ConduitFillEngine.kt
                       (Lines 247–249: removed defective disjunction '|| input.conduitType == "Rigid PVC"')
    Files (production):
                       MODIFIED app/src/main/java/com/example/engine/ConduitFillEngine.kt
    Files (test):      MODIFIED app/src/test/java/com/example/ConduitFillMaterialFilterCharacterizationTest.kt
                       MODIFIED app/src/test/java/com/example/engine/DomainStringNormalizationTargetTest.kt
    Files (audit):     docs/audit/protected_files.sha256, docs/audit/DECISIONS.md, docs/audit/ECT_LEDGER.md, docs/audit/KT-06.md
    Characterization test: ConduitFillMaterialFilterCharacterizationTest (historically locked defective EMT selection)
    Target test:       ConduitFillMaterialFilterCharacterizationTest.rigidPvcRequestStrictlyFiltersMaterial_targetRegression
                       DomainStringNormalizationTargetTest.testConduitTypeNormalization_authoritativeEngineFiltering
                       (PASSED: proves strict material filtering across PVC, Rigid PVC, EMT, GI/RMC, and regression boundary)
    Expected Golden diff: EMPTY / NOT APPLICABLE (Golden baseline does not track conduit fill sizing)
    Rollback:          git checkout <commit> -- app/src/main/java/com/example/engine/ConduitFillEngine.kt app/src/test/java/com/example/ConduitFillMaterialFilterCharacterizationTest.kt app/src/test/java/com/example/engine/DomainStringNormalizationTargetTest.kt
    SHA-256 before:    ConduitFillEngine.kt = 98e49d23c758938cc75f092c0c90e314071fbc5361ddfd249cc3010773b7a3dc
    SHA-256 after:     ConduitFillEngine.kt = bed51dcce14c70d4a9c21c14e2f8c330d7135002585f0ba8e06da4376d0aa317
    Owner signature / date: 2026-10-07 — Owner explicitly authorized ECT-D-EE-11 / P1-08 Tier-F scope.
    Engineering basis:
                       - Removed defective disjunction '|| input.conduitType == "Rigid PVC"' from ConduitFillEngine.kt:248.
                       - Restored strict conduit material filtering parity with QuickConduitFillEngine.
                       - PVC requests strictly consider only Rigid PVC candidates; metallic EMT/GI rows are fully excluded.
                       - No modifications to formulas, fill ratios, cable areas, or catalog data.
    Gate Verification:
                       - Full Gate Run #1: 332 suites / 2943 tests / 0 failures / 0 errors / 17 skipped (8m 24s, EXITCODE=0)
                       - Full Gate Run #2: 332 suites / 2943 tests / 0 failures / 0 errors / 17 skipped (8m 01s, EXITCODE=0)
                       - lintDebug: BUILD SUCCESSFUL (0 errors, 0 fatal)
                       - lintRelease: BUILD SUCCESSFUL (0 errors, 0 fatal)
                       - compileReleaseKotlin: BUILD SUCCESSFUL (EXITCODE=0)
    Tier-F Manifest Verification:
                       - Line 6 of docs/audit/protected_files.sha256 updated to bed51dcce14c70d4a9c21c14e2f8c330d7135002585f0ba8e06da4376d0aa317.
                       - Exactly 1 Tier-F engine file modified under owner authorization.
    Golden Differential:
                       - EMPTY / NOT APPLICABLE. Zero changes to baseline calculation cases.
    Final Status:      CLOSED — VERIFIED
