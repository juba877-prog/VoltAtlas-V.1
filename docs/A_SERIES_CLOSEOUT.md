# VoltAtlas — A-Series Closeout

Status: CLOSED (owner-verified). Do not reopen without a new finding number.

## Commits
- A5 `665c0ff` — `HierarchyResolver.resolveAncestors` (cycle/self-parent guard shared by all walkers).
- A1 `7f742a8` — grouping-derating prefix keys + fail loud on unknown installation types.
- A3 `04330e3` — 20 degC conductor resistance (`r20`) as the max-fault basis; loud DATA MISSING on
  missing `r20`; `rUsed` (voltage-drop basis) untouched.
- A3-fu `2786c31` — gate every Isc consumer on status (Protection, mapper, ATS aggregate, Highest-Isc KPI).
- A3-fu2 `9b67c62` — item 2: per-ATS Isc scope, hierarchy-resolved feeding transformer (no first-in-map),
  engine-layer `AtsIscResolver`, DATA MISSING instead of partial max, honest upper-bound message.
- A1-R `<hash>` — exact-match `D1`/`D2` aliases; typed failure for unsupported installation methods;
  fabricated `"B1"` import default removed; test fixtures use production vocabulary.

## Final gate totals
`discovered=2597 passed=2583 skipped=14 failed=0` (flaky EngineeringTaskManagerTest included: 20/20 PASS on HEAD in isolation).

The nine class-(a)/(b) fixtures fixed by A1-R plus the A-series tests are all green; nothing is red.

## Engineering basis (rules that survive this series)
1. Max-fault path uses 20 degC line resistance (`r20`); voltage drop keeps its own basis.
2. Ampacity catalog has exactly three families: Duct, Buried, Air(/Tray). IEC codes map exactly:
   `D1` -> Duct, `D2` -> Buried. Conduit/trunking/`A1`/`A2`/`B1`/`B2`/`C`/`In Ground` have no mapping
   and fail typed. Blank installation (`""`) fails as "not specified", never defaulted.
3. A DATA MISSING number is never used as a real value, never averaged or maxed in, never presented
   as if complete. ATS needs a resolved fault level or it is DATA MISSING.
4. Import/export round-trips preserve room state; a missing key is reported by circuit name/id/count.

## Open log (NOT fixed — each needs its own ticket)
- K_T transformer impedance correction missing (`ShortCircuitEngine.kt`).
- `cmax = 1.05` hard-coded (`ShortCircuitEngine.kt:85`).
- Generator / motor contribution absent from ATS Icw.
- Ancestor-walk `?: continue` silent segment skip.
- `ProtectionEngine` `?: 0.0` on R20 (ADS loop understated).
- `CatalogRepository` fallback keys that can load non-20-degC values.
- `R_Cu70 get() = R_Cu20` misleading aliases.
- VD 70-degC vs XLPE 90-degC temperature convention (owner-deferred).
- `Cable Tray` -> Air assumes perforated tray (unperforated/solid unverified).
- Room rows already stored with `"B1"` / `"In Conduit"` (possible migration).
- `EngineeringTaskManagerTest#observeActiveTask_switchesAtomicallyToSupersedingTask`:
  20/20 PASS on HEAD in isolation; treat as flaky. Re-run if it recurs.
- Busway in a fault path -> DATA MISSING (new A3 failure mode; HELD for owner).
