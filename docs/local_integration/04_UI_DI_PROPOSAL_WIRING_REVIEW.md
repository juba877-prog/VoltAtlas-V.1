# AI UI, DI, and Proposal Lifecycle Wiring Review

## 1. Architectural Chain Verification

The complete Assistant lifecycle was reviewed across all 6 core components:

`
[MainActivity]
      â”‚ (injects production factory)
      â–¼
[EngineeringAssistantViewModelFactory]
      â”œâ”€â”€> ConversationEngineProvider.provideConversationEngine()
      â”œâ”€â”€> CableProposalPreparationCoordinator (ProjectDao, CircuitDao)
      â””â”€â”€> TrustedProposalApprovalGateway (lazy on-demand gateway construction)
              â”‚
              â–¼
[EngineeringAssistantViewModel] â”€â”€> [EngineeringAssistantSheet]
              â”‚                              â”‚
              â”‚ (onProposalReviewAction)      â–¼
              â–¼                     [AssistantProposalReviewCard]
       (Step 1: Review)                      â”‚
              â”‚                              â–¼
              â–¼                     (User explicitly confirms)
       (Step 2: Approval)                    â”‚
              â”‚                              â–¼
              â–¼                     [AssistantApprovalConfirmationCard]
   [TrustedProposalApprovalGateway]          â”‚
              â”‚                              â–¼
       (Revision CAS Guard)         [DurableMutationWorkflow]
              â”‚                              â”‚
              â–¼                              â–¼
      [Room CAS Mutation]  â”€â”€â”€â”€â”€â”€â”€> [EngineeringCalculationCoordinator]
                                             â”‚
                                             â–¼
                                     [Revalidation Receipt]
`

## 2. Security & Compliance Checklist

- [x] **Zero LLM Direct Calculation:** LLM only produces conversational intent or candidate values; calculations are exclusively delegated to deterministic Kotlin engines.
- [x] **No AI Direct Room Mutation:** The AI subsystem cannot directly mutate Room entities; all mutations must pass through TrustedProposalApprovalGateway with durable grants.
- [x] **Revision Guard CAS:** Mutations enforce optimistic locking on aseProjectRevision. If project revision changed during review, mutation fails closed.
- [x] **Explicit 2-Step Confirmation:** UI requires explicit user tap on review card followed by confirmation card before approval execution.
- [x] **TaskOwner Coroutine Management:** ctiveTurnJob is properly tracked and cancelled on new turn or sheet close.
