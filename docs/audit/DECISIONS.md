# VoltAtlas — Owner Decisions (D1–D10)

> ## OWNER DECISIONS RECORDED — 2026-10-04 (Phase C)
>
> The owner has supplied the following decisions. This section supersedes the earlier
> `PENDING OWNER DECISION` / `PROVISIONAL` statuses in the table below, which are retained for
> history and must not be erased.
>
> These are recorded as **owner-approved**, transcribed exactly as supplied. They are not
> agent proposals. Implementation of each decision is still tracked separately — an approved
> decision does not mean the corresponding code change has been made.
>
> | ID | Owner decision | Implementation state |
> |---|---|---|
> | D1 | **APPROVED** — 70/90 °C operating temperature basis | **IMPLEMENTED/VERIFIED** (Phase D, EE-01) |
> | D2 | **APPROVED** — Near-End / Far-End split | **IMPLEMENTED/VERIFIED** (Phase D, EE-02) |
> | D3 | **APPROVED** — Option B | **IMPLEMENTED/VERIFIED** (Phase D, EE-03) |
> | D4 | **APPROVED — IEC basic basis** | NOT IMPLEMENTED (Phase D, EE-04). **See the D4 constraint below.** |
> | D5 | **APPROVED** — Option A | **IMPLEMENTED/VERIFIED** (Phase D, EE-06) |
> | D6 | **APPROVED** — Option A | **IMPLEMENTED/VERIFIED in Phase C** (SEC-02) |
> | D7 | **APPROVED** — UI device locale; engineering files/PDF/DXF/JSON deterministic numeric formatting | NOT IMPLEMENTED (later phase, KT-03) |
> | D8 | **PENDING** — owner has no API server / HTTPS reporting endpoint | **NOT IMPLEMENTED. Explicitly out of scope for Phase C.** |
> | D9 | **APPROVED — REMOVE** | NOT EXECUTED in Phase C by owner instruction (belongs to the later architecture/security phase) |
> | D10 | **APPROVED** — revised deferral list (below) | Recorded |
| D-EE-07 | **APPROVED** — Option B (Transparent Standard Disclosure) | **IMPLEMENTED/VERIFIED** (Phase D, EE-07) |
| D-EE-11 | **APPROVED** — Fix ConduitFillEngine Conduit Material Filtering (P1-08) | **IMPLEMENTED/VERIFIED** (Phase D, ECT-D-EE-11) |
>
> ### D4 — governing-basis constraint (binding on implementation)
>
> D4 approves the **IEC basic basis** as the governing basis. It does **not** supply the numerical
> c-factor, $K_T$, or voltage-tolerance value. Those numbers **must come from the governing standard
> text or an owner-verified source**, never from an LLM assumption or an inferred default.
>
> Consequence: EE-04 / D4 remains **blocked on a standard-text value** even though the decision is
> approved. Phase C performs no engineering change and does not need this value.
>
> ### D8 — remains PENDING, and must stay PENDING
>
> The owner has **no API server or HTTPS reporting endpoint**. Therefore, for Phase C and until the
> owner supplies one:
>
> - **No endpoint may be invented** (no URL, no domain, no path).
> - **No backend may be created** (no Firebase reporting, no proxy, no serverless function).
> - **No fake transport may be added.**
> - **No "sent" / "delivered" acknowledgement may be simulated.**
> - SEC-14 (in-app AI content reporting) is **not implemented** in this phase.
>
> D8 is recorded as PENDING and must not be marked signed by any downstream phase.
>
> ### D10 — approved deferral list
>
> **DEFER:**
> `KT-01`, `KT-04`, `KT-05`, `KT-07`, `KT-09`, `EE-05`, `EE-09`
>
> **DO NOT DEFER (must be fixed before release):**
> `SEC-04`, `SEC-12`, `SEC-13`, `SEC-22`
>
> Any finding not listed in DEFER and not already closed is **not** deferred by this decision.

---

## Historical record (pre-Phase-C status)

Status legend: `PENDING OWNER DECISION` | `PROVISIONAL (owner-stated, not yet signed)` | `SIGNED`.
The table below is the pre-Phase-C state, kept for history. Agent proposals are not decisions.
Engineering behavior must not change until the relevant decision is approved (Phase 2).

| ID | Topic | Status | Value | Notes |
|---|---|---|---|---|
| D1 | Voltage-drop resistance temperature (EE-01) | PENDING OWNER DECISION | — | Plan default (c); evidence to be attached after Phase 1 |
| D2 | Circuit model / Isc location (EE-02) | PENDING OWNER DECISION | — | Fixed by audit result |
| D3 | Cable thermal withstand clearing time (EE-03) | PENDING OWNER DECISION | — | Plan default (b) |
| D4 | K_T and c-factors (EE-04) | PENDING OWNER DECISION | — | Owner must supply the standard text for c-factors |
| D5 | Breaker selection vs Icu (EE-06) | PENDING OWNER DECISION | — | Plan default (a) |
| D6 | Local project backup (SEC-02) | PENDING OWNER DECISION | — | Plan default (a) |
| D7 | Number locale policy (KT-03) | PENDING OWNER DECISION | — | Plan default as stated |
| D8 | AI report destination | PROVISIONAL (owner-stated, not yet signed) | decision = A | See below |
| D9 | AppFunctions | PROVISIONAL (owner-stated default, not yet signed) | REMOVE | See below |
| D10 | Deferral list | PENDING OWNER DECISION | — | To be proposed after Phase 1 |

## D8 — AI report destination

- **D8 decision = A** — owner-controlled HTTPS endpoint (owner statement, 2026-10-04).
- **D8 implementation = PENDING**
  - destination URL: PENDING ENDPOINT IMPLEMENTATION (not invented; to be supplied by the owner)
  - acknowledgement mechanism: PENDING
  - Phase 1 audit may continue.
  - Phase 6 AI release gate is BLOCKED until the endpoint exists, is verified, and has acknowledged a real report
    from an on-device test (Section 14 acceptance).
  - Gemini is NOT disabled to avoid implementing reporting.

## D9 — AppFunctions

- **D9 = REMOVE** (plan default; owner stated use of default).
- Removal is NOT executed until the Phase 2 decision gate is officially closed.
- Phase 1 must verify the full removal scope: manifest `<service>` + intent filter, service class, local
  AppFunction stubs, obsolete tests, ProGuard keep rule, merged release manifest, no externally callable function.

## Phase 2 signature sheet (added 2026-10-04)

Phase 2 is read-only. Nothing below has been implemented. Each row states the **exact decision
text** the owner is being asked to sign. Signing means writing `SIGNED` in the Status column and
completing the signature block. An unsigned row remains `NEEDS DECISION` and forbids behavioural
code changes that depend on it.

**Do not sign a row whose "requires from owner" column is empty for you** — those rows cannot be
resolved by the audit and must not be filled in by the agent.

| ID | Exact decision text put to the owner | Status | Requires from owner |
|---|---|---|---|
| **D1** | Adopt voltage-drop resistance at standard conductor operating temperature: **70 °C for PVC, 90 °C for XLPE**, per IEC 60364-5-52. `CableSizingEngine.kt:645-650, 751-754` currently uses R20 (PVC) and R70est (XLPE) with `thermalFactor = 1.0`; `QuickEngineeringDataBridge.kt:95-98` falsely advertises 70 °C / 90 °C labels. **Alternative under consideration:** keep R20 and correct the UI labels to say 20 °C / 70 °C-estimated. | PENDING OWNER DECISION | Confirmation that the governing standard permits the 70/90 °C basis for the project's jurisdiction |
| **D2** | Authorize split fault-location architecture: evaluate breaker $I_{cu}$, making capacity $I_{cm}$ and cable thermal withstand $I_w$ at the **upstream parent board busbar (near-end fault)**; reserve far-end circuit impedance for **ADS loop disconnection ($Z_s$)** only. Current code (`ShortCircuitEngine.kt:63-68, 137-153`) adds the target circuit's own cable impedance, so an upstream breaker is judged against an attenuated far-end fault current. This is the R0 launch blocker EE-02. | PENDING OWNER DECISION | Approval of the near-end design basis (this changes protective device ratings) |
| **D3** | Adopt Plan Default (b): add user-definable clearing/delay overrides on `CircuitEntity`, and mandate transparent disclosure of the assumed clearing time in UI and PDF. Current code (`CableSizingEngine.kt:917-922`) hardcodes MCB 0.01 s / MCCB 0.1 s / ACB 0.3 s with no disclosure. | PENDING OWNER DECISION | Preferred default clearing times per protective-device class used by the owner |
| **D4** | Align `ElectricalConverters.kt:372` to $c = 1.05$ (matching `TransformerEngine.kt:187`) and document $K_T = 1.0$ as an explicit consulting simplification, **or** keep both as-is. Current 5 % divergence understates $I''_k$ by 2.6 %–3.8 % because IEC 60909 $K_T$ is omitted. | PENDING OWNER DECISION | **The governing standard text for the LV voltage-tolerance / c-factor** (e.g. +6 % → c = 1.05, +10 % → c = 1.10) as it applies to the owner's jurisdiction. This value is deliberately NOT guessed. |
| **D5** | Adopt Plan Default (a): unified multi-domain status rollup — if `ProtectionResult.finalStatus == "FAIL"` **or** $I_{sc} > I_{cu}$, then `CircuitUiModelMapper` and the PDF executive summary MUST display **FAIL**, never PASS. Today `CableSizingEngine.kt:902-908` selects on $I_n \ge I_b$ only and `CircuitUiModelMapper.kt:27-36` shows "✓ PASS" on circuits where $I_{sc} > I_{cu}$. R0 launch blocker EE-06. | PENDING OWNER DECISION | Confirmation that a breaker may still be *candidateselected* on $I_n$ as long as a breaking-capacity shortfall forces circuit FAIL |
| **D6** | Keep the database excluded from cloud backup **and** device-to-device transfer, with prominent in-app export guidance (user-driven JSON export/import is the restore path). `data_extraction_rules.xml` and `backup_rules.xml` already exclude `electrical_engineering_db{,-wal,-shm}` and `voltatlas_ai_secure_keystore.xml` in both `<cloud-backup>` and `<device-transfer>`. | PENDING OWNER DECISION | Acceptance that native phone-to-phone project restore is not offered |
| **D7** | UI follows the device locale; PDF / DXF / JSON exports use a **fixed numeric locale** independent of device locale. Current code (`Sections.kt:50-521`) uses `"%.2f".format()` with the device default locale (emitting commas in EU/AR) while DXF already strictly enforces `Locale.US`. | PENDING OWNER DECISION | None outstanding — but signing is still required because it changes emitted numbers in exports |
| **D8** | **Direction = A: owner-controlled HTTPS endpoint** (owner-stated 2026-10-04). Decision A is agreed; the implementation is NOT. | **NEEDS DECISION — INCOMPLETE** | **Endpoint URL, API contract, authentication scheme, and retention policy.** None of these exist in the repository and none were invented. Phase 6 AI release gate stays BLOCKED until a real report is acknowledged by the real endpoint from an on-device run. |
| **D9** | **REMOVE AppFunctions from the release.** Currently NOT executed: `app/src/main/AndroidManifest.xml:44-49` still declares `<service android:name="com.example.ai.appfunctions.VoltAtlasAppFunctionService" android:permission="android.permission.BIND_APP_FUNCTION_SERVICE">` with the `android.app.appfunctions.AppFunctionService` intent filter, and the merged debug manifest still carries `BIND_APP_FUNCTION_SERVICE`. Removal scope per `SEC-03_AppFunctions.md`: manifest `<service>` + intent filter, service class, local AppFunction stubs, obsolete tests, ProGuard keep rule, merged release manifest, and proof no externally callable function remains. **Half-removal is not acceptable.** | PENDING OWNER DECISION | Confirmation of REMOVE before Phase 2 gate closes |
| **D10** | Deferral list: every knowingly deferred R2/R3 item. **Proposed content from the audit, for owner edit:** KT-01 (runBlocking, R2), KT-04 (broad exception handling, R2), KT-05 (O(N²) scans, R3), KT-07 (file size, R3), KT-09 (baseline profile, R2), EE-05 (motor kappa, R3), EE-09 (generator nameplate, R2), SEC-04 (release logging, R2), SEC-22 (OCR temp files, R2), SEC-12 (dead suggestion chips, R1 — **proposed to defer, owner may override**), SEC-13 (keystore error masking, R1 — **proposed to defer, owner may override**), KT-07/quick-tool parity backlog. | PENDING OWNER DECISION | Owner must confirm the deferral list, and must explicitly rule on whether R1 items SEC-12 / SEC-13 may be deferred |

### Hard blockers that no decision can sign away

These are recorded so signing does not create a false impression of release readiness:

| ID | Blocker | Evidence |
|---|---|---|
| **KT-02 (R0)** | `AppDatabase.kt:1065` sets `exportSchema = false`; `app/schemas/` does not exist; `MigrationTestHelper` is referenced **nowhere** in the test tree; 25 migrations (1→2 … 25→26) exist and schema version is **26**. No migration is covered by `MigrationTestHelper`. | `grep -r MigrationTestHelper app/src/test app/src/androidTest` → 0 hits |
| **SEC-14 (R0)** | No in-app AI content reporting mechanism exists. Hard Play AI-policy blocker, dependent on D8. | `docs/audit/PHASE1_KT_SEC_SUMMARY.md` |
| **Golden** | `ip`, `Iw`, `ADS Zs` have **no authoritative reference** anywhere in the repository. See `golden_baseline.json` → `_phase2a_reinvestigation`. | Phase 2A, 2026-10-04 |
| **Tier E is empty** | The Tier F manifest holds all 24 `engine/` files plus 2 circuit-editor UI files. **Every** EE-01..EE-10 and KT-01/05/06/08 fix therefore touches a Tier F file and needs a per-ticket scope authorization before an ECT can be opened. | `docs/audit/ECT_LEDGER.md` |

## Owner signature

Owner signature: ____________________   Date: ____________   (unsigned)

Phase 2 gate: **NOT CLOSED** — 0 of 10 decisions signed.
Consequence: Phase 3 (data safety), Phase 4 (engineering correctness) and all other
behaviour-changing phases remain forbidden until D1–D10 are signed.

---

## Phase C gate status (added 2026-10-04)

The owner decisions above release the **data-safety gate only** (KT-02, SEC-02). Engineering
behaviour changes (Phase D: EE-02, EE-06, …) remain unauthorised by this prompt and are explicitly
out of scope for this run.

| Gate | State |
|---|---|
| Phase C (data safety: KT-02, SEC-02) | **AUTHORISED** |
| Phase D (engineering: EE-02, EE-06, Golden regression) | **NOT AUTHORISED in this run** |
| D4 numerical values (c-factor / K_T / tolerance) | **BLOCKED** — needs standard text |
| D8 (AI reporting endpoint) | **PENDING** — no endpoint exists |
| D9 (AppFunctions removal) | Approved but **deferred by owner instruction** to a later phase |

> The Phase D row above applied to the Phase C run of 2026-10-04 only. Phase D was subsequently
> authorised by the owner on 2026-10-05 (ECT-EE-02). See the reconciliation note below.

---

## D2 RECONCILIATION — append-only note (added 2026-10-05, ECT-EE-02)

Owner-authorized documentation reconciliation performed **before any engineering source edit**, as
directed. No prior evidence was rewritten; nothing above this note was altered except for the single
pointer sentence added to the Phase C gate table.

**No conflicting authoritative record was found.** The repository contained an *internal*
inconsistency in this file, not a contradiction with any newer signed decision:

### Previous recorded state (preserved above, verbatim)

| Location | Recorded state |
|---|---|
| Line 16 — Phase C owner-decisions table | `D2 \| **APPROVED** — Near-End / Far-End split \| NOT IMPLEMENTED (Phase D, EE-02)` |
| Line 69 — historical pre-Phase-C table | `D2 \| Circuit model / Isc location (EE-02) \| PENDING OWNER DECISION` |
| Line 110 — Phase 2 signature sheet | `D2 \| Authorize split fault-location architecture … \| PENDING OWNER DECISION` |

Lines 69 and 110 are the **retained historical** states that line 5-7 of this file already declares
superseded ("This section supersedes the earlier `PENDING OWNER DECISION` … statuses in the table
below, which are retained for history and must not be erased"). They were left untouched.

### Owner-approved state (2026-10-05)

> **AUTHORIZE SPLIT FAULT-LOCATION ARCHITECTURE:**
>
> - evaluate breaker Icu at the upstream parent-board / breaker fault point
> - evaluate breaker making capacity / ip at the same near-end fault point
> - evaluate cable fault withstand at the appropriate fault point
> - reserve far-end circuit impedance for downstream fault / minimum-fault / ADS calculations where
>   applicable

**D2 = APPROVED.** Recorded exactly as supplied. `ECT-EE-02` is thereby authorized to implement it.

### Delta against the previously drafted D2 wording

The previously drafted text (line 110) reserved far-end circuit impedance for **"ADS loop
disconnection ($Z_s$) only"**. The owner-approved wording is broader: **"for downstream fault /
minimum-fault / ADS calculations where applicable"**. The owner-approved wording governs. This is a
widening consistent with the ticket's CHECK 4 (far-end Isc must include feeder impedance) and CHECK 5
(ADS/minimum-fault uses downstream impedance) — not a narrowing, and no prior record is deleted.

| Field | Value |
|---|---|
| Date | 2026-10-05 |
| Source | Owner instruction, Phase D / ECT-EE-02 |
| Previous state | APPROVED (line 16) superseding PENDING (lines 69, 110) |
| Owner-approved state | **APPROVED — split fault-location architecture**, text as quoted above |
| Engineering code touched by this reconciliation | **none** |

Related gates, unchanged by this note: D1/D3/D4/D5 remain their own decisions for their own
findings; D8 remains PENDING; D9 remains deferred. Phase D scope for this run is **EE-02 only** —
EE-06 is not authorized by this note.

---

## D-EE-07 OWNER DECISION — Neutral / CPC Sizing & Thermal Withstand Integrity (added 2026-10-06)

Decision ID:
    D-EE-07

Decision:
    APPROVE OPTION B — TRANSPARENT STANDARD DISCLOSURE

Rationale:
- Phase, neutral, and CPC cross-sectional areas MUST NOT be summed into one thermal withstand area.
- Neutral and CPC are not parallel conductors with the phase conductor.
- The current branch-circuit architecture does not model dedicated line-neutral prospective fault current or branch line-earth prospective fault current required for independent conductor-by-conductor adiabatic verification.
- No inferred, synthetic, assumed, or invented Ief / Isc,L-N values are allowed.
- Existing near-end EE-02 fault-point semantics MUST NOT be silently changed.
- ProtectionEngine.kt and ShortCircuitEngine.kt are Tier-F protected and MUST NOT be expanded.
- Option B mandates:
  1. Authoritative disclosure in UI (`ProtectionScreen`) and PDF (`ProtectionSection`):
     "CPC sized per IEC 60364-5-54 Table 54.7 geometric method. Separate CPC adiabatic fault withstand verification is not performed when conductor-specific fault-current model is unavailable."
  2. Activate and validate user-supplied `overrideNeutralSize` and `overrideEarthSize` in `CableSizingEngine.kt` (if present, parse and validate against standard sizes, reject invalid sizes safely with `CableFailureCode.INCOMPLETE_INPUT`).
  3. `SizingResult` retains `neutralSize` and `cpcSize` based on geometric rules or validated user overrides.
  4. Phase thermal withstand check in `ProtectionEngine` evaluates phase conductor area only (unchanged).

| Field | Value |
|---|---|
| Date | 2026-10-06 |
| Ticket | EE-07 |
| Owner Decision | D-EE-07: APPROVE OPTION B — TRANSPARENT STANDARD DISCLOSURE |
| Implementation State | IN PROGRESS |
---

## D-EE-11 OWNER DECISION — ConduitFillEngine Material Filtering Fix (P1-08) (added 2026-10-07)

Decision ID:
    D-EE-11 / P1-08

Decision:
    APPROVED — FIX CONDUITFILLENGINE CONDUIT-TYPE MATERIAL FILTERING (ECT-D-EE-11)

Implementation Scope:
- Tier-F engine `app/src/main/java/com/example/engine/ConduitFillEngine.kt:247-249` corrected.
- Removed defective disjunction `|| input.conduitType == "Rigid PVC"` from catalog filter predicate.
- Restored strict conduit material filtering parity with `QuickConduitFillEngine`.
- Only catalog rows matching requested canonical conduit type are eligible; metallic EMT/GI conduits are strictly excluded for PVC queries.
- Manifest `docs/audit/protected_files.sha256` updated with new blob hash.
- Verified across double full gate (332 suites, 2943 tests, 0 failures), release compilation, and lint.

| Field | Value |
|---|---|
| Date | 2026-10-07 |
| Ticket | ECT-D-EE-11 / P1-08 |
| Owner Decision | D-EE-11: APPROVED — Fix ConduitFillEngine conduit-type material filtering |
| Implementation State | IMPLEMENTED/VERIFIED |
| Tier-F Scope | AUTHORIZED: app/src/main/java/com/example/engine/ConduitFillEngine.kt only |
