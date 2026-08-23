# 09: AI UI, ViewModel & Dependency Injection Audit

## 1. UI Entry Points & Navigation Reachability

- **Entry Point**: The assistant is accessible from the top app bar across the application via `MainLayout.kt` (lines 75–95). Clicking the assistant icon opens `EngineeringAssistantSheet.kt`.
- **ViewModel Lifecycle**: `EngineeringAssistantViewModel` is created via `EngineeringAssistantViewModelFactory.production(application)` in `MainActivity.kt` and passed down to `MainLayout`.
- **Material Design 3 Compliance**:
  - Uses standard MD3 tonal surfaces and `Surface` containers.
  - Zero backdrop blur or glassmorphic styling.
  - Clear hierarchy with distinct card types: `UserUtteranceCard`, `ExplanationCard`, `ResultCard`, `WhatIfCard`, `ProposalReviewCard`, `ApprovalConfirmationCard`, `MutationReceiptCard`, `SafeErrorCard`.

---

## 2. Interaction & Error Handling

- **Send Action**: Submits text to `viewModelScope.launch`, cancelling any ongoing turn job.
- **Cancel Action**: Dismisses sheet and resets in-flight review.
- **Proposal Review**: Non-executable card with "Continue to Approval" and "Cancel" buttons.
- **Explicit Approval**: Displays a distinct confirmation modal requiring explicit confirmation.
- **Principal Missing**: If `trustedEngineerIdentity` is blank in `GlobalPreferences`, displays a clear guidance message directing the user to Global Settings instead of failing silently.
