# 10: AI Mutation, Approval & Revision Audit

## 1. The 5-Stage Mutation Safety Pipeline

Every project mutation requested by the user through the assistant must traverse 5 sequential, deterministic stages:

$$\text{Stage 1: Proposal Policy Validation} \longrightarrow \text{Stage 2: Explicit Approval & Grant} \longrightarrow \text{Stage 3: Revision CAS Check} \longrightarrow \text{Stage 4: Durable Mutation} \longrightarrow \text{Stage 5: Post-Commit Revalidation}$$

---

## 2. Invariants & Defenses

1. **User Action Provenance**: `TrustedProposalApprovalGateway` requires a non-blank `userActionId` generated solely by explicit user click in `AssistantApprovalConfirmationCard`.
2. **Authenticated Engineer Principal**: `TrustedPrincipalProvider` checks `trustedEngineerIdentity` in `GlobalPreferences`. If blank, execution fails closed pre-commit (`PrincipalMissing`).
3. **Revision CAS Guard**: Checks that `proposal.baseProjectRevision == liveRevision`. If the project was edited concurrently, execution fails closed (`StaleProposal`).
4. **Content Integrity**: `ProposalDigestEncoder` hashes proposal content with SHA-256; `ProposalSemanticKeyEncoder` prevents duplicate active proposals on the same entity.
5. **Durable Storage**:
   - `ApprovalGrantEntity`: Recorded in Room (`approval_grants` table). Transitions from `ISSUED` to `CONSUMED`.
   - `MutationExecutionReceiptEntity`: Recorded in Room (`mutation_execution_receipts` table). Transitions from `IN_PROGRESS` to `COMMITTED_REVALIDATED` or `COMMITTED_REVALIDATION_FAILED`.
6. **Internal Visibility**: `DurableMutationWorkflow` is `internal` and unreachable from UI code. Architecture tests enforce that UI/Gemini files cannot instantiate the workflow directly.
