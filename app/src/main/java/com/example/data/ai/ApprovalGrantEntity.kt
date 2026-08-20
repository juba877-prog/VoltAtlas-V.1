package com.example.data.ai

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.ai.proposal.ApprovalIssuerType
import com.example.ai.proposal.ApprovalStatus
import com.example.data.ProjectEntity

/**
 * Room entity representing an immutable, single-use approval grant for an engineering proposal.
 *
 * Foreign Key Policy:
 * - Option A (Project-Local Cascade): Deleting a [ProjectEntity] cascades and deletes all associated approval grants.
 *
 * Uniqueness Invariants:
 * - [approvalId] is the primary key.
 * - [activeProposalSemanticKey] is uniquely indexed, ensuring at most ONE active (`ISSUED`) approval exists
 *   for any given mutation semantic key at any time.
 */
@Entity(
    tableName = "approval_grants",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["projectId"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["approvalId"], unique = true),
        Index(value = ["projectId"]),
        Index(value = ["proposalDigest"]),
        Index(value = ["proposalSemanticKey"]),
        Index(value = ["activeProposalSemanticKey"], unique = true)
    ]
)
data class ApprovalGrantEntity(
    @PrimaryKey val approvalId: String,
    val proposalId: String,
    val proposalDigest: String,
    val proposalSemanticKey: String,
    val activeProposalSemanticKey: String?,
    val projectId: String,
    val targetEntityType: String = "CIRCUIT",
    val targetEntityId: String,
    val baseProjectRevision: Long,
    val status: ApprovalStatus,
    val issuerType: ApprovalIssuerType,
    val approvedBy: String,
    val userActionId: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val consumedAt: Long? = null,
    val consumedByExecutionId: String? = null
) {
    companion object {
        /**
         * Trusted factory for constructing a valid [ApprovalGrantEntity] in state [ApprovalStatus.ISSUED].
         * Automatically derives [activeProposalSemanticKey] from [proposalSemanticKey].
         */
        fun createIssued(
            approvalId: String,
            proposalId: String,
            proposalDigest: String,
            proposalSemanticKey: String,
            projectId: String,
            targetEntityType: String = "CIRCUIT",
            targetEntityId: String,
            baseProjectRevision: Long,
            userActionId: String,
            approvedBy: String,
            issuedAt: Long,
            expiresAt: Long
        ): ApprovalGrantEntity {
            require(approvalId.isNotBlank()) { "approvalId cannot be blank" }
            require(proposalId.isNotBlank()) { "proposalId cannot be blank" }
            require(proposalDigest.isNotBlank()) { "proposalDigest cannot be blank" }
            require(proposalSemanticKey.isNotBlank()) { "proposalSemanticKey cannot be blank" }
            require(projectId.isNotBlank()) { "projectId cannot be blank" }
            require(targetEntityType.isNotBlank()) { "targetEntityType cannot be blank" }
            require(targetEntityId.isNotBlank()) { "targetEntityId cannot be blank" }
            require(userActionId.isNotBlank()) { "userActionId cannot be blank" }
            require(approvedBy.isNotBlank()) { "approvedBy cannot be blank" }
            require(expiresAt > issuedAt) { "expiresAt ($expiresAt) must be strictly greater than issuedAt ($issuedAt)" }

            return ApprovalGrantEntity(
                approvalId = approvalId,
                proposalId = proposalId,
                proposalDigest = proposalDigest,
                proposalSemanticKey = proposalSemanticKey,
                activeProposalSemanticKey = proposalSemanticKey,
                projectId = projectId,
                targetEntityType = targetEntityType,
                targetEntityId = targetEntityId,
                baseProjectRevision = baseProjectRevision,
                status = ApprovalStatus.ISSUED,
                issuerType = ApprovalIssuerType.USER_EXPLICIT_CONFIRMATION,
                approvedBy = approvedBy,
                userActionId = userActionId,
                issuedAt = issuedAt,
                expiresAt = expiresAt,
                consumedAt = null,
                consumedByExecutionId = null
            )
        }
    }
}
