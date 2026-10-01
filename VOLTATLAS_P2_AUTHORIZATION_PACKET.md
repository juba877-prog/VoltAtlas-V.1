# P2 AUTHORIZATION PACKET — BLOCKED ITEMS

**Created:** Phase 6 Batch 4 (continuation), base `4581bc0`
**Status:** P2-1, P2-2 (partially), P2-4 are **BLOCKED / NEEDS AUTHORIZATION**.
These are NOT deferred for convenience. Each has two or more mutually incompatible candidate
authorities, at least one of which lives inside the **protected** `com.example.engine.*` surface.
Resolving them by picking a winner would be inventing engineering authority, which the Phase 6
rules prohibit.

---

## P2-1 — Quick engine duplication · BLOCKED (two independent blockers)

### Blocker A — Conduit Fill delegation would propagate a known protected defect

| | |
|---|---|
| Affected quick impl | `QuickConduitFillEngine` (`ui/screens/quick/QuickNewToolsModels.kt:171`) |
| Authoritative target | `ConduitFillEngine` (`engine/ConduitFillEngine.kt`) — **PROTECTED** |
| Known defect | `ConduitFillEngine.kt:247-249` filters `it.conduitType.trim().equals(input.conduitType.trim(), ignoreCase = true) \|\| input.conduitType == "Rigid PVC"`. The disjunction makes the material filter a no-op for "Rigid PVC", admitting EMT/GI rows. |
| Existing evidence | `ConduitFillMaterialFilterCharacterizationTest` (P1-8) proves: a "Rigid PVC" request returns **"20 mm EMT"** from the engine, while the strict quick engine returns **"25 mm"** for the same circuit. |
| Why delegation is unsafe | The plan requires "Add parity tests before removing/repointing behavior". Parity is **unattainable**: the only way to make the two agree is to change the protected engine. Delegating quick → engine would push the known material-filter defect straight into the user-facing quick tool. |
| Protected change needed | Make the material filter strict (drop the `\|\| input.conduitType == "Rigid PVC"` disjunct) in `ConduitFillEngine.kt`. |
| Parity test after authorization | The existing characterization test must be **inverted** to assert strict behavior (PVC in → PVC out), and a new quick-vs-engine equivalence test added. |
| Authorization needed | Unprotect `ConduitFillEngine.kt`, or authorize an engine fix scope. |

### Blocker B — Motor Starter has no authoritative engine at all

| | |
|---|---|
| Affected quick impl | `QuickMotorStarterEngine` (`QuickNewToolsModels.kt:649`) |
| Authoritative target | **NONE EXISTS** — verified: no `MotorStarter*` declaration anywhere under `app/src/main/java/com/example/engine/`. |
| Why delegation is impossible | There is nothing to delegate to. |
| What is forbidden | Creating a new `MotorStarterEngine` would be "a second/duplicate engineering calculation engine" (rule 5 / rule 7). |
| Authorization needed | Either (a) authorize creating a NEW authoritative motor-starter engine in the protected area, with its own verified methodology, or (b) formally accept the quick implementation as the sole authority for motor starters and document it as such. |

### Consumers (for blast-radius)
Each Quick engine has exactly ONE production consumer — its own screen:
`QuickEarthingScreen`, `QuickConduitFillScreen`, `QuickBreakerSelectScreen`,
`QuickGeneratorScreen`, `QuickUpsScreen`, `QuickAtsScreen`, `QuickMotorStarterScreen`.
No engine is reachable from the AI tool layer, so the assistant path is unaffected.

---

## P2-2 — Shared engineering helpers · BLOCKED IN PART

| Sub-item | Status | Reason |
|---|---|---|
| Earthing group efficiency (η) | **EQUIVALENT** | Quick (`QuickNewToolsModels.kt:91-92`) is byte-for-byte the same formula as `EarthingEngine.kt:138-139` (`0.95 - 0.06*(n-1)` / `0.90 - 0.07*(n-1)`, same `.coerceAtLeast` floors). Also `zsMax` uses Cmin 0.95 in both, matching `ProtectionEngine.maxZsAllowed`. Duplicated but **semantically identical** — consolidating would require exposing an engine helper (protected). |
| **Icm n-factor** | **BLOCKED** | Engine `ShortCircuitEngine.iec60947_2_icmNFactor` is **tiered per IEC 60947-2** (≤16 kA→2.00, ≤20→2.00, ≤50→2.10, >50→2.20). Quick `QuickAtsEngine` uses a **blanket `2.2`**. The engine is authoritative; the helper is `private` inside a protected file, so it cannot be shared or reused without unprotecting it. |
| **Generator η / DF** | **BLOCKED** | Engine `GeneratorEngine` uses `settings.demandFactor / settings.diversityFactor` (**configurable**). Quick hardcodes `/ 0.80` and `/ 1.50`. Reconciling means changing quick's constants — but §10 forbids choosing a winner by intuition, and the engine's values are configuration, not literals. |
| **UPS efficiency / DoD** | **BLOCKED** | Engine `UpsEngine` uses `settings.upsEfficiency` (**configurable**). Quick hardcodes `/ 0.80`. Same conflict. |

**Net:** the two genuinely divergent pairs (Icm n-factor, η/DF) cannot be resolved without either
modifying a protected engine or inventing engineering constants. Both are prohibited.
**Authorization needed:** expose the protected helpers (e.g. make `iec60947_2_icmNFactor`
internal/public on a non-protected surface) so Quick can delegate to the single authority.

---

## P2-4 — Shared TransformerDefaults · BLOCKED (three conflicting authorities)

| Source | percentZ | xr | File |
|---|---|---|---|
| **Entity schema** | **5.0** | **6.0** | `data/Entities.kt` — `TransformerEntity` defaults |
| **Import fallback** | 5.0 | **7.0** | `data/EngineeringRepository.kt:719-720` |
| **createDefault** | **6.0** | 6.0 | `ui/EngineeringViewModel.kt:373-374` |
| **Quick tool (3rd opinion)** | `TransformerEngine.getDefaultPercentZ(txType, kVA)` | 6.0 | `ui/screens/QuickToolScreens.kt:856-857, 982-983` — calls the **PROTECTED** engine |

Three mutually incompatible candidate authorities:

1. The entity schema says 5.0 / 6.0. Neither consumer matches it fully.
2. The **protected** `TransformerEngine.getDefaultPercentZ()` provides a *type-and-rating-specific*
   default, which is a **different semantic basis** from a flat literal.
3. A prior authorized fix (**A1-R**, recorded in the P0-12 deviations) deliberately protects the
   import-defaulting semantics — "missing import keys are not defaulted" is an authorized
   behavior, so import fallbacks cannot simply be aligned to the schema.

**Why this is not just "pick one":** if the engine's type-specific default is authoritative, then
the import fallback of 5.0 and `createDefault` of 6.0 are *both* wrong and should be replaced by an
engine call — but that changes import semantics and would rewrite behavior A1-R deliberately
established. If instead the schema literal is authoritative, then `QuickToolScreens` (which calls
the engine) becomes the outlier. **These decisions have different blast radii and are a domain
call, not an implementation detail.**

**Authorization needed:** a domain decision on which of {schema literal, engine-derived default,
per-path literals} is authoritative for `%Z` and `%X/R`, plus confirmation that A1-R still holds.

---

## What is NOT blocked

P2-3 was audited and found **ALREADY SATISFIED**: `FALLBACK_BREAKERS` no longer exists anywhere in
`app/src/main` — it was deleted in Phase 6 Batch 1 (P0-1), which replaced the fallback table with an
explicit `CATALOG MISSING` state. No action required.

P2-11 was completed in this continuation (permanent-invariant copy contract + test).

## Recommendation

Authorize **one** of, in order of preference:
1. Unprotect the specific engine helpers needed for delegation (n-factor, η/DF, %Z defaults) so a
   single authority can exist without touching engineering formulas; or
2. Formally designate the entity schema as the authority for %Z/%X/R and accept that quick tools
   continue to use the engine's type-specific default (documented divergence); or
3. Accept the Quick implementations as authoritative for their respective tools and close the
   corresponding engine-side findings instead.
