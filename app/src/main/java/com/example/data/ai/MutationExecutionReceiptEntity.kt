package com.example.data.ai

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.ai.proposal.RevalidationStatus

/**
 * Room entity representing an immutable, durable execution receipt for a committed database mutation.
 *
 * Foreign Key Policy:
 * - Option A (Project-Local Cascade): Deleting an [ApprovalGrantEntity] cascades and deletes its execution receipt.
 *
 * Invariants:
 * - Every persisted receipt represents an already-committed database mutation.
 * - [approvalId] is uniquely indexed (at most one receipt per approval grant).
 * - [executionId] is the unique primary key.
 */
@Entity(
    tableName = "mutation_execution_receipts",
    foreignKeys = [
        ForeignKey(
            entity = ApprovalGrantEntity::class,
            parentColumns = ["approvalId"],
            childColumns = ["approvalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["executionId"], unique = true),
        Index(value = ["approvalId"], unique = true),
        Index(value = ["projectId"])
    ]
)
data class MutationExecutionReceiptEntity(
    @PrimaryKey val executionId: String,
    val approvalId: String,
    val proposalId: String,
    val proposalDigest: String,
    val proposalSemanticKey: String,
    val projectId: String,
    val targetEntityType: String = "CIRCUIT",
    val targetEntityId: String,
    val revisionBefore: Long,
    val revisionAfter: Long,
    val revalidationStatus: RevalidationStatus,
    val appliedChangesJson: String,
    val createdAt: Long,
    val committedAt: Long,
    val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val workflowCompletedAt: Long? = null,
    val errorCode: String? = null,
    val messageKey: String? = null,
    val isRecoverable: Boolean = false,
    val diagnosticRef: String? = null
) {
    companion object {
        /**
         * Trusted factory for constructing a valid [MutationExecutionReceiptEntity]
         * upon successful Room mutation commit under future Model A execution.
         */
        fun createCommitted(
            executionId: String,
            approval: ApprovalGrantEntity,
            revisionBefore: Long,
            revisionAfter: Long,
            appliedChangesJson: String,
            now: Long
        ): MutationExecutionReceiptEntity {
            require(executionId.isNotBlank()) { "executionId cannot be blank" }
            require(revisionAfter == revisionBefore + 1L) {
                "Single-step revision invariant violated: before=$revisionBefore, after=$revisionAfter"
            }
            return MutationExecutionReceiptEntity(
                executionId = executionId,
                approvalId = approval.approvalId,
                proposalId = approval.proposalId,
                proposalDigest = approval.proposalDigest,
                proposalSemanticKey = approval.proposalSemanticKey,
                projectId = approval.projectId,
                targetEntityType = approval.targetEntityType,
                targetEntityId = approval.targetEntityId,
                revisionBefore = revisionBefore,
                revisionAfter = revisionAfter,
                revalidationStatus = RevalidationStatus.PENDING,
                appliedChangesJson = appliedChangesJson,
                createdAt = now,
                committedAt = now,
                attemptCount = 0,
                lastAttemptAt = null,
                workflowCompletedAt = null,
                errorCode = null,
                messageKey = null,
                isRecoverable = false,
                diagnosticRef = null
            )
        }
    }
}
