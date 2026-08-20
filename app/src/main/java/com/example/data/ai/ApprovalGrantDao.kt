package com.example.data.ai

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Authoritative Room DAO governing persistence and guarded lifecycle transitions
 * for [ApprovalGrantEntity].
 *
 * Invariants:
 * - Prohibits generic production lifecycle `@Update`.
 * - All state transitions are guarded conditional queries whose affected row count is authoritative.
 */
@Dao
interface ApprovalGrantDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGrant(grant: ApprovalGrantEntity)

    @Query("SELECT * FROM approval_grants WHERE approvalId = :approvalId LIMIT 1")
    suspend fun getGrantById(approvalId: String): ApprovalGrantEntity?

    @Query("SELECT * FROM approval_grants WHERE activeProposalSemanticKey = :semanticKey LIMIT 1")
    suspend fun getActiveGrantBySemanticKey(semanticKey: String): ApprovalGrantEntity?

    @Query("SELECT * FROM approval_grants WHERE projectId = :projectId ORDER BY issuedAt DESC")
    suspend fun getGrantsForProject(projectId: String): List<ApprovalGrantEntity>

    /**
     * Atomically consumes an active approval grant.
     * Enforces that current status is `ISSUED`, expected semantic key and revision match, and grant is not expired.
     * Sets `status = 'CONSUMED'`, clears `activeProposalSemanticKey`, and records `consumedAt` and `consumedByExecutionId`.
     *
     * @return 1 if successfully consumed, 0 if condition failed (e.g. already consumed, expired, or mismatch).
     */
    @Query("""
        UPDATE approval_grants
        SET status = 'CONSUMED',
            activeProposalSemanticKey = NULL,
            consumedAt = :now,
            consumedByExecutionId = :executionId
        WHERE approvalId = :approvalId
          AND status = 'ISSUED'
          AND proposalSemanticKey = :expectedSemanticKey
          AND baseProjectRevision = :expectedRevision
          AND expiresAt > :now
    """)
    suspend fun consumeGrantGuarded(
        approvalId: String,
        expectedSemanticKey: String,
        expectedRevision: Long,
        executionId: String,
        now: Long
    ): Int

    /**
     * Atomically marks an expired approval grant as `EXPIRED` and clears [activeProposalSemanticKey].
     *
     * @return 1 if successfully expired, 0 if condition failed.
     */
    @Query("""
        UPDATE approval_grants
        SET status = 'EXPIRED',
            activeProposalSemanticKey = NULL
        WHERE approvalId = :approvalId
          AND status = 'ISSUED'
          AND expiresAt <= :now
    """)
    suspend fun expireGrantGuarded(
        approvalId: String,
        now: Long
    ): Int

    /**
     * Atomically cancels an active approval grant and clears [activeProposalSemanticKey].
     *
     * @return 1 if successfully cancelled, 0 if condition failed.
     */
    @Query("""
        UPDATE approval_grants
        SET status = 'CANCELLED',
            activeProposalSemanticKey = NULL
        WHERE approvalId = :approvalId
          AND status = 'ISSUED'
    """)
    suspend fun cancelGrantGuarded(
        approvalId: String
    ): Int
}
