package com.example.data.ai

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Authoritative Room DAO governing persistence and guarded lifecycle transitions
 * for [MutationExecutionReceiptEntity].
 *
 * Invariants:
 * - Prohibits generic production lifecycle `@Update`.
 * - All revalidation state transitions are guarded conditional queries whose affected row count is authoritative.
 */
@Dao
interface MutationExecutionReceiptDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReceipt(receipt: MutationExecutionReceiptEntity)

    @Query("SELECT * FROM mutation_execution_receipts WHERE executionId = :executionId LIMIT 1")
    suspend fun getReceiptById(executionId: String): MutationExecutionReceiptEntity?

    @Query("SELECT * FROM mutation_execution_receipts WHERE approvalId = :approvalId LIMIT 1")
    suspend fun getReceiptByApprovalId(approvalId: String): MutationExecutionReceiptEntity?

    @Query("SELECT * FROM mutation_execution_receipts WHERE projectId = :projectId ORDER BY committedAt DESC")
    suspend fun getReceiptsForProject(projectId: String): List<MutationExecutionReceiptEntity>

    /**
     * Atomically transitions a pending receipt to `RUNNING` status,
     * incrementing [attemptCount] and clearing any prior error records.
     */
    @Query("""
        UPDATE mutation_execution_receipts
        SET revalidationStatus = 'RUNNING',
            attemptCount = attemptCount + 1,
            lastAttemptAt = :now,
            errorCode = NULL,
            messageKey = NULL,
            diagnosticRef = NULL
        WHERE executionId = :executionId
          AND revalidationStatus = 'PENDING'
    """)
    suspend fun transitionPendingToRunning(
        executionId: String,
        now: Long
    ): Int

    /**
     * Atomically records successful revalidation completion,
     * recording [workflowCompletedAt] and clearing error records.
     */
    @Query("""
        UPDATE mutation_execution_receipts
        SET revalidationStatus = 'SUCCEEDED',
            workflowCompletedAt = :now,
            errorCode = NULL,
            messageKey = NULL,
            diagnosticRef = NULL
        WHERE executionId = :executionId
          AND revalidationStatus = 'RUNNING'
    """)
    suspend fun transitionRunningToSucceeded(
        executionId: String,
        now: Long
    ): Int

    /**
     * Atomically records partial revalidation completion,
     * keeping [workflowCompletedAt] null to permit retry.
     */
    @Query("""
        UPDATE mutation_execution_receipts
        SET revalidationStatus = 'PARTIAL',
            errorCode = :errorCode,
            messageKey = :messageKey,
            isRecoverable = :isRecoverable,
            diagnosticRef = :diagnosticRef
        WHERE executionId = :executionId
          AND revalidationStatus = 'RUNNING'
    """)
    suspend fun transitionRunningToPartial(
        executionId: String,
        errorCode: String,
        messageKey: String,
        isRecoverable: Boolean,
        diagnosticRef: String?
    ): Int

    /**
     * Atomically records revalidation failure,
     * keeping [workflowCompletedAt] null to permit retry.
     */
    @Query("""
        UPDATE mutation_execution_receipts
        SET revalidationStatus = 'FAILED',
            errorCode = :errorCode,
            messageKey = :messageKey,
            isRecoverable = :isRecoverable,
            diagnosticRef = :diagnosticRef
        WHERE executionId = :executionId
          AND revalidationStatus = 'RUNNING'
    """)
    suspend fun transitionRunningToFailed(
        executionId: String,
        errorCode: String,
        messageKey: String,
        isRecoverable: Boolean,
        diagnosticRef: String?
    ): Int

    /**
     * Atomically records revalidation cancellation.
     */
    @Query("""
        UPDATE mutation_execution_receipts
        SET revalidationStatus = 'CANCELLED',
            errorCode = 'ERR_CANCELLED',
            messageKey = 'error.revalidation.cancelled',
            isRecoverable = 1,
            diagnosticRef = :diagnosticRef
        WHERE executionId = :executionId
          AND revalidationStatus = 'RUNNING'
    """)
    suspend fun transitionRunningToCancelled(
        executionId: String,
        diagnosticRef: String?
    ): Int

    /**
     * Atomically marks revalidation as `SUPERSEDED` by a newer project revision,
     * recording [workflowCompletedAt].
     */
    @Query("""
        UPDATE mutation_execution_receipts
        SET revalidationStatus = 'SUPERSEDED',
            workflowCompletedAt = :now,
            errorCode = NULL,
            messageKey = NULL,
            diagnosticRef = NULL
        WHERE executionId = :executionId
          AND revalidationStatus IN ('PENDING', 'RUNNING', 'PARTIAL', 'FAILED', 'CANCELLED')
    """)
    suspend fun transitionToSuperseded(
        executionId: String,
        now: Long
    ): Int

    /**
     * Atomically restarts revalidation from a retryable terminal attempt (`PARTIAL`, `FAILED`, `CANCELLED`),
     * incrementing [attemptCount], setting [lastAttemptAt], and clearing prior error fields.
     */
    @Query("""
        UPDATE mutation_execution_receipts
        SET revalidationStatus = 'RUNNING',
            attemptCount = attemptCount + 1,
            lastAttemptAt = :now,
            errorCode = NULL,
            messageKey = NULL,
            diagnosticRef = NULL
        WHERE executionId = :executionId
          AND revalidationStatus IN ('PARTIAL', 'FAILED', 'CANCELLED')
    """)
    suspend fun transitionRetryToRunning(
        executionId: String,
        now: Long
    ): Int
}
