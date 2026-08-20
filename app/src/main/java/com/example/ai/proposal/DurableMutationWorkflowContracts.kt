package com.example.ai.proposal

import com.example.data.CircuitEntity
import com.example.data.ai.ApprovalGrantEntity
import com.example.data.ai.MutationExecutionReceiptEntity
import com.squareup.moshi.JsonClass

/**
 * Request container for executing a pre-validated proposal with an issued durable approval grant.
 */
data class DurableMutationRequest(
    val intent: ValidatedCableProposalIntent,
    val approvalId: String,
    val executionId: String? = null
)

/**
 * Pure data container holding committed mutation artifacts produced inside
 * the single atomic Room database transaction.
 */
internal data class InternalDurableCommitRecord(
    val receipt: MutationExecutionReceiptEntity,
    val patchedCircuit: CircuitEntity,
    val fieldChanges: List<FieldChangeRecord>,
    val candidateAffectedDomains: Set<String>
)

/**
 * Internal exception hierarchy used strictly inside [androidx.room.withTransaction]
 * to trigger deterministic SQLite transaction rollback across approval consumption,
 * circuit mutation, revision CAS, and receipt insertion.
 */
internal sealed class InternalDurableRollbackException(message: String) : RuntimeException(message) {
    class ApprovalConsumeFailed(val approvalId: String) :
        InternalDurableRollbackException("Guarded consumption of approval '$approvalId' affected 0 rows.")

    class ProjectNotFound(val projectId: String) :
        InternalDurableRollbackException("Project '$projectId' not found in database.")

    class EntityNotFound(val entityType: String, val entityId: String) :
        InternalDurableRollbackException("Entity '$entityType' with ID '$entityId' not found in database.")

    class ProjectMismatch(val expectedProjectId: String, val actualProjectId: String) :
        InternalDurableRollbackException("Entity belongs to project '$actualProjectId', expected '$expectedProjectId'.")

    class RevisionConflict(val projectId: String, val expectedRevision: Long, val actualRevision: Long) :
        InternalDurableRollbackException("Revision conflict for project '$projectId': expected $expectedRevision, actual $actualRevision.")

    class InvalidPatch(val reason: String) :
        InternalDurableRollbackException(reason)

    class CircuitUpdateFailed(val projectId: String, val circuitId: Int) :
        InternalDurableRollbackException("Circuit update for circuit ID $circuitId in project '$projectId' affected 0 rows.")

    class DatabaseError(val errorCode: String, val sanitizedMessage: String) :
        InternalDurableRollbackException(sanitizedMessage)
}

/**
 * Comprehensive, typed, sanitized outcome of the internal durable mutation workflow.
 *
 * Guarantees:
 * - Every committed outcome ([isCommitted] == true) guarantees that the database mutation and receipt are durable.
 * - Zero raw SQL, stack traces, file paths, or private host details in error messages.
 */
sealed class DurableMutationOutcome {

    /**
     * Database mutation committed, receipt recorded, and post-commit revalidation succeeded completely.
     */
    data class Completed(
        val receipt: MutationExecutionReceiptEntity,
        val appliedChanges: List<FieldChangeRecord>,
        val revalidationOutcome: RevalidationOutcome.RevalidatedSuccessfully
    ) : DurableMutationOutcome()

    /**
     * Database mutation committed and receipt recorded, but some downstream calculation domains were partial or skipped.
     */
    data class CommittedPartial(
        val receipt: MutationExecutionReceiptEntity,
        val appliedChanges: List<FieldChangeRecord>,
        val revalidationOutcome: RevalidationOutcome.CommittedWithPartialRevalidation
    ) : DurableMutationOutcome()

    /**
     * Database mutation committed and receipt recorded, but revalidation failed.
     * Receipt status is FAILED (workflow remains open for retry).
     */
    data class CommittedRevalidationFailed(
        val receipt: MutationExecutionReceiptEntity,
        val appliedChanges: List<FieldChangeRecord>,
        val revalidationOutcome: RevalidationOutcome.CommittedButRevalidationFailed
    ) : DurableMutationOutcome()

    /**
     * Database mutation committed and receipt recorded, but revalidation was cancelled.
     * Receipt status is CANCELLED (workflow remains open for retry).
     */
    data class CommittedRevalidationCancelled(
        val receipt: MutationExecutionReceiptEntity,
        val appliedChanges: List<FieldChangeRecord>,
        val revalidationOutcome: RevalidationOutcome.CommittedButRevalidationCancelled
    ) : DurableMutationOutcome()

    /**
     * Database mutation committed and receipt recorded, but project revision advanced concurrently before revalidation publication.
     * Receipt status is SUPERSEDED (terminal).
     */
    data class Superseded(
        val receipt: MutationExecutionReceiptEntity,
        val appliedChanges: List<FieldChangeRecord>,
        val revalidationOutcome: RevalidationOutcome.RevisionAdvancedOrSuperseded
    ) : DurableMutationOutcome()

    /**
     * Replay returned existing durable truth without performing any remutation.
     */
    data class ExistingReceiptReturned(
        val receipt: MutationExecutionReceiptEntity,
        val appliedChanges: List<FieldChangeRecord>,
        val message: String
    ) : DurableMutationOutcome()

    /**
     * Approval ID was not found in the database. Zero database modifications occurred.
     */
    data class ApprovalNotFound(
        val approvalId: String,
        val sanitizedReason: String
    ) : DurableMutationOutcome()

    /**
     * Approval is expired. Zero database modifications occurred.
     */
    data class ApprovalExpired(
        val approvalId: String,
        val expiredAt: Long,
        val sanitizedReason: String
    ) : DurableMutationOutcome()

    /**
     * Approval is cancelled. Zero database modifications occurred.
     */
    data class ApprovalCancelled(
        val approvalId: String,
        val sanitizedReason: String
    ) : DurableMutationOutcome()

    /**
     * Approval is already consumed. Zero database modifications occurred.
     */
    data class ApprovalAlreadyConsumed(
        val approvalId: String,
        val existingReceipt: MutationExecutionReceiptEntity?,
        val sanitizedReason: String
    ) : DurableMutationOutcome()

    /**
     * Approval grant fields mismatch the validated proposal intent. Zero database modifications occurred.
     */
    data class ApprovalMismatch(
        val approvalId: String,
        val fieldName: String,
        val expectedValue: String,
        val actualValue: String,
        val sanitizedReason: String
    ) : DurableMutationOutcome()

    /**
     * Project revision in database did not match the baseProjectRevision. Zero database modifications occurred.
     */
    data class RevisionConflict(
        val projectId: String,
        val expectedRevision: Long,
        val actualRevision: Long,
        val sanitizedReason: String
    ) : DurableMutationOutcome()

    /**
     * Target circuit or project entity was not found in database. Zero database modifications occurred.
     */
    data class EntityNotFound(
        val entityType: String,
        val entityId: String,
        val sanitizedReason: String
    ) : DurableMutationOutcome()

    /**
     * Validated proposal intent is malformed or invalid. Zero database modifications occurred.
     */
    data class InvalidIntent(
        val sanitizedReason: String
    ) : DurableMutationOutcome()

    /**
     * Patch instructions are invalid (e.g. out of bounds or not found in catalog). Zero database modifications occurred.
     */
    data class InvalidPatch(
        val sanitizedReason: String
    ) : DurableMutationOutcome()

    /**
     * Approval is marked CONSUMED in database, but no matching mutation execution receipt exists.
     * System integrity violation. Zero database modifications occurred.
     */
    data class ReceiptIntegrityFailure(
        val approvalId: String,
        val sanitizedReason: String
    ) : DurableMutationOutcome()

    /**
     * Database transaction or constraint failure. Zero mutation committed.
     */
    data class DatabaseFailure(
        val errorCode: String,
        val messageKey: String,
        val isRecoverable: Boolean,
        val diagnosticRef: String? = null,
        val sanitizedReason: String
    ) : DurableMutationOutcome()

    val isCommitted: Boolean
        get() = this is Completed ||
                this is CommittedPartial ||
                this is CommittedRevalidationFailed ||
                this is CommittedRevalidationCancelled ||
                this is Superseded ||
                this is ExistingReceiptReturned
}
