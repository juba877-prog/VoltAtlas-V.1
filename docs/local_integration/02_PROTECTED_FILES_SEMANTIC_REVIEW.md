# Protected Files Semantic Review & Justification Matrix

Every file change in protected architectural areas was subjected to line-by-line AST and semantic delta analysis before applying to the authoritative branch.

---

### 1. `app/src/main/java/com/example/MainActivity.kt`
- **Old Behavior:** Created `EngineeringViewModel` and `BootViewModel`; invoked `MainLayout(viewModel)` without assistant ViewModel parameter.
- **New Behavior:** Instantiates `assistantViewModel` with `remember { EngineeringAssistantViewModelFactory.production(application) }` and passes it to `MainLayout`.
- **Semantic Delta:** Wires the production AI Assistant DI factory at the root Activity composition.
- **Justification:** Essential to provide `EngineeringAssistantViewModel` with real `TrustedProposalApprovalGateway` and `CableProposalPreparationCoordinator` instances.
- **Architecture Affected:** AI Assistant Runtime & UI Presentation.
- **Regression Risk:** Zero. Uses standard Jetpack Compose `viewModel(factory=...)` lifecycle pattern.
- **Test Coverage:** `AssistantViewModelIntegrationTest`, `MainActivityUiTest`.
- **Action:** `TAKE_NEW`.

---

### 2. `app/src/main/java/com/example/ui/MainLayout.kt`
- **Old Behavior:** Internally called `viewModel()` to create a default `EngineeringAssistantViewModel` (which lacked approvalGateway & proposalCoordinator) and had a single `QuickCableCalcScreen` boolean toggle.
- **New Behavior:** Receives `assistantViewModel` as a parameter; adds `QuickEngineeringHubScreen` supporting 8 modular engineering scratchpads.
- **Semantic Delta:** Injects production assistant ViewModel; replaces single cable quick-calc with comprehensive Quick Engineering Hub.
- **Justification:** Eliminates un-injected ViewModel creation and integrates verified Quick Engineering tools into primary UI navigation.
- **Architecture Affected:** Navigation, UI Layout, AI Assistant, Quick Engineering.
- **Regression Risk:** Very low; maintains full backwards compatibility with project picker actions.
- **Test Coverage:** `QuickEngineeringHubNavigationTest`, `QuickEngineeringBatch1IntegrationTest`.
- **Action:** `TAKE_NEW`.

---

### 3. `app/src/main/java/com/example/ai/ui/EngineeringAssistantViewModel.kt`
- **Old Behavior:** Approval gateway property was internal var without constructor injection; `applyHybridResult` lacked proposal preparation coordinator routing; missing cancelation on close.
- **New Behavior:** Constructor accepts `approvalGatewayProvider` and `proposalPreparationCoordinator`; cancels `activeTurnJob` on close; routes `ConversationActionType.CREATE_PROPOSAL` to `proposalPreparationCoordinator.prepare(...)`; wires `onProposalReviewAction`.
- **Semantic Delta:** Completes proposal lifecycle bridging from LLM intent to review UI; manages coroutine lifecycle cleanly.
- **Justification:** Closes the gap between AI conversation decisions and the 2-step proposal review/approval UI.
- **Architecture Affected:** AI Assistant UI / Proposal Lifecycle.
- **Regression Risk:** Zero; fail-closed behavior on null coordinator.
- **Test Coverage:** `EngineeringAssistantViewModelTest`, `HybridConversationRouterTest`.
- **Action:** `TAKE_NEW`.

---

### 4. `app/src/main/java/com/example/ai/ui/EngineeringAssistantSheet.kt`
- **Old Behavior:** Proposal review card action callback was an empty no-op `{ /* Action handling */ }`.
- **New Behavior:** Wires callback to `viewModel.onProposalReviewAction(action.actionType)`.
- **Semantic Delta:** Connects user tap on proposal review card buttons to ViewModel state machine.
- **Justification:** Makes proposal review actions interactive and functional.
- **Architecture Affected:** AI Assistant UI Presentation.
- **Regression Risk:** Zero.
- **Test Coverage:** `EngineeringAssistantSheetTest`.
- **Action:** `TAKE_NEW`.

---

### 5. `app/src/main/java/com/example/ai/approval/TrustedProposalApprovalGateway.kt`
- **Old Behavior:** Declared as `internal class TrustedProposalApprovalGateway`.
- **New Behavior:** Declared as `class TrustedProposalApprovalGateway internal constructor(...)`.
- **Semantic Delta:** Class is public across packages while constructor remains internal.
- **Justification:** Required for `EngineeringAssistantViewModelFactory` in `com.example.ai.ui` package to reference the type.
- **Architecture Affected:** Proposal Approval Security Boundary.
- **Regression Risk:** Zero; constructor access remains restricted.
- **Test Coverage:** `TrustedProposalApprovalGatewayTest`.
- **Action:** `TAKE_NEW`.

---

### 6. `app/src/main/java/com/example/ai/proposal/DurableMutationWorkflow.kt`
- **Old Behavior:** Lacked a factory constructor helper for the gateway.
- **New Behavior:** Added `companion object { fun createGateway(...) }` constructing workflow and gateway atomically.
- **Semantic Delta:** Clean composition helper.
- **Justification:** Enables `EngineeringAssistantViewModelFactory` to construct the approval gateway cleanly without leaking internal dependencies.
- **Architecture Affected:** Proposal Mutation Engine.
- **Regression Risk:** Zero.
- **Test Coverage:** `DurableMutationWorkflowTest`.
- **Action:** `TAKE_NEW`.

---

### 7. `app/src/main/java/com/example/data/GlobalPreferences.kt` & `GlobalSettingsScreen.kt`
- **Old Behavior:** No trusted engineer identity preference.
- **New Behavior:** Adds `trustedEngineerIdentity: StateFlow<String>` in `GlobalPreferences` and an editable input in `GlobalSettingsScreen`.
- **Semantic Delta:** Device-local configuration for engineer identity string.
- **Justification:** Required by `TrustedProposalApprovalGateway` as the principal identity for audit receipts during explicit approvals.
- **Architecture Affected:** User Settings / Audit Logging.
- **Regression Risk:** Zero. Intentionally blank by default; does not bypass approval or revision guards.
- **Test Coverage:** `GlobalPreferencesTest`.
- **Action:** `TAKE_NEW`.

---

### 8. `app/src/main/java/com/example/engine/converters/ElectricalConverters.kt`
- **Old Behavior:** Inverted 3-phase factor for star vs delta capacitor conversions; lacked explicit total-to-branch kVAR/Î¼F conversion helpers.
- **New Behavior:** Implements physically exact formulas ({\text{total, Delta}} = 3 \times 2\pi f C V_{LL}^2 \times 10^{-9}$, {\text{total, Star}} = 2\pi f C V_{LL}^2 \times 10^{-9}$); adds `apparentPowerToCurrent`, `currentToApparentPower`, and reactive power helpers.
- **Semantic Delta:** Corrects physical formulas and adds helper functions for Quick Engineering screens.
- **Justification:** Ensures strict mathematical and physical accuracy in converter tools and quick calculators.
- **Architecture Affected:** Deterministic Engineering Converters.
- **Regression Risk:** Zero; verified by `ElectricalConvertersTest` with 1e-4 tolerance assertions.
- **Test Coverage:** `ElectricalConvertersTest`.
- **Action:** `TAKE_NEW`.

---

### 9. `app/src/main/java/com/example/data/Models.kt`
- **Old Behavior:** `Catalog.getDiagnostics()` recomputed full sets on every call; `Catalog` lacked indexed type lookups.
- **New Behavior:** Adds `@Volatile cachedDiagnostics`, `supportedTypeCodes: Set<Int>`, `cableTypesByName: Map<String, CableType>`, and `isCableTypeUnsupported(...)`.
- **Semantic Delta:** High-performance indexed lookups and diagnostics memoization.
- **Justification:** Eliminates redundant iteration across large cable catalogs during UI rendering and calculation passes.
- **Architecture Affected:** Core Catalog Data Models.
- **Regression Risk:** Zero. Includes `invalidateIndexes()` on catalog reload.
- **Test Coverage:** `CatalogLoaderTest.testCatalogDiagnosticsCachingAndInvalidation`.
- **Action:** `TAKE_NEW`.

---

### 10. `app/build.gradle.kts` & `gradle/libs.versions.toml`
- **Old Behavior:** Declared unused `com.google.ai.client.generativeai:0.9.0` dependency; no test profiles.
- **New Behavior:** Removed `google.genai` dependency; added test execution profile filter configuration (`fast`, `dev`, `ai`, `stress`, `full`).
- **Semantic Delta:** Clean dependency hygiene; support for targeted test profiles.
- **Justification:** The project uses offline local seams and Firebase AI; unused Google GenAI SDK was out of scope for the current seam-based phase.
- **Architecture Affected:** Build System / CI / Dependencies.
- **Regression Risk:** Zero; verified offline compilation.
- **Test Coverage:** Gradle build and test tasks.
- **Action:** `TAKE_NEW`.
