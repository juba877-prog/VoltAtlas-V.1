# 06: AI Tools & Coordinator Audit

## 1. Complete AI Tools Inventory

The project currently implements **10 production deterministic AI tools** (6 Read tools and 4 Cable Engineering tools). All tools are strictly read-only or calculation-only; zero tools perform database mutations.

| Tool Name | Domain | Contract / Schema | Authoritative Source | TaskManager Used | Mutation Capable? | Approval Required? | Phase | Status |
|---|---|---|---|---|---|---|---|---|
| `get_current_project_context` | Read | `GetCurrentProjectContextRequest` | `ProjectContextProvider` | Yes (`READ`) | No | No | Phase 5 | `VERIFIED` |
| `get_entity` | Read | `GetEntityRequest` | `ProjectContextProvider` | Yes (`READ`) | No | No | Phase 5 | `VERIFIED` |
| `get_calculation_state` | Read | `GetCalculationStateRequest` | `CalculationFreshnessProvider` | Yes (`READ`) | No | No | Phase 5 | `VERIFIED` |
| `get_dependencies` | Read | `GetDependenciesRequest` | `ProjectContextProvider` | Yes (`READ`) | No | No | Phase 5 | `VERIFIED` |
| `get_catalog_item` | Read | `GetCatalogItemRequest` | `CatalogRepository` | Yes (`READ`) | No | No | Phase 5 | `VERIFIED` |
| `get_change_history` | Read | `GetChangeHistoryRequest` | `ProjectContextProvider` | Yes (`READ`) | No | No | Phase 5 | `VERIFIED` |
| `calculate_cable_sizing` | Cable | `CalculateCableSizingRequest` | `EngineeringCalculationCoordinator` | Yes (`CALCULATE`) | No | No | Phase 6 | `VERIFIED` |
| `evaluate_cable_candidate` | Cable | `EvaluateCableCandidateRequest` | `EngineeringCalculationCoordinator` | Yes (`CALCULATE`) | No | No | Phase 6 | `VERIFIED` |
| `compare_cable_candidates` | Cable | `CompareCableCandidatesRequest` | `EngineeringCalculationCoordinator` | Yes (`CALCULATE`) | No | No | Phase 6 | `VERIFIED` |
| `run_cable_what_if` | Cable | `RunCableWhatIfRequest` | `EngineeringCalculationCoordinator` | Yes (`CALCULATE`) | No | No | Phase 6 | `VERIFIED` |

---

## 2. Core Power Chain & Coordinator Audit

- **Five Domains in Coordinator**:
  1. Cable Sizing (`calculateCableSizing`) $\longrightarrow$ Active & tested.
  2. Transformer Sizing (`calculateTransformer`) $\longrightarrow$ Active & tested.
  3. Short Circuit (`calculateShortCircuit`) $\longrightarrow$ Active & tested.
  4. Protection / ADS (`calculateProtectionAds`) $\longrightarrow$ Active & tested.
  5. Coordination (`calculateCoordination`) $\longrightarrow$ Deferred at Slice 5 state per project decision.
- **Cache Isolation**: Strongly-typed project cache keys (`ProjectCircuitKey`, `ProjectTransformerKey`) prevent cross-project cache leaks.
- **Freshness Integration**: All calculation executions record fingerprints in `InMemoryCalculationFreshnessMetadataRegistry`.
- **Missing AI Tools for Non-Cable Domains**: Dedicated AI tool wrappers for Transformer, Short Circuit, and Protection calculations have not yet been authored (scheduled for Phase 9+).
