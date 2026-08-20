package com.example.ai.proposal

import com.squareup.moshi.JsonClass

/**
 * Type discriminator for typed field values in mutation change records.
 */
enum class FieldValueKind {
    STRING,
    INTEGER,
    DECIMAL,
    NULL
}

/**
 * Machine-typed representation of a field value before or after mutation.
 * Preserves strict type distinctions without lossy stringification.
 */
@JsonClass(generateAdapter = true)
data class TypedFieldValue(
    val kind: FieldValueKind,
    val stringValue: String? = null,
    val intValue: Long? = null,
    val doubleValue: Double? = null,
    val unit: String? = null
) {
    companion object {
        fun ofString(value: String?, unit: String? = null): TypedFieldValue =
            if (value == null) ofNull() else TypedFieldValue(FieldValueKind.STRING, stringValue = value, unit = unit)

        fun ofInt(value: Int?, unit: String? = null): TypedFieldValue =
            if (value == null) ofNull() else TypedFieldValue(FieldValueKind.INTEGER, intValue = value.toLong(), unit = unit)

        fun ofDouble(value: Double?, unit: String? = null): TypedFieldValue =
            if (value == null) ofNull() else TypedFieldValue(FieldValueKind.DECIMAL, doubleValue = value, unit = unit)

        fun ofNull(): TypedFieldValue = TypedFieldValue(FieldValueKind.NULL)
    }
}

/**
 * Machine-typed record of an individual field change applied to a circuit.
 */
@JsonClass(generateAdapter = true)
data class FieldChangeRecord(
    val fieldName: String,
    val action: PatchAction,
    val previousValue: TypedFieldValue? = null,
    val newValue: TypedFieldValue? = null,
    val displayChange: String? = null
)

enum class MutationSuccessStatus {
    /**
     * Database transaction committed successfully. Cache invalidation and calculation
     * revalidation are mandatory before exposing results to public runtime or users.
     */
    COMMITTED_REVALIDATION_REQUIRED
}

/**
 * Data payload describing the scope of mutation impact for handoff to Phase 7D revalidation.
 * Data only: Does NOT execute calculation or invalidation in Phase 7C.
 */
@JsonClass(generateAdapter = true)
data class MutationImpact(
    val projectId: String,
    val entityType: String = "CIRCUIT",
    val entityId: String,
    val mutatedFields: Set<String>,
    val baseRevision: Long,
    val resultingRevision: Long,
    val candidateAffectedDomains: Set<String>,
    val committedAt: Long,
    val status: MutationSuccessStatus = MutationSuccessStatus.COMMITTED_REVALIDATION_REQUIRED,
    val requiresRevalidation: Boolean = true
)

/**
 * Public, sanitized outcome container for revision-guarded engineering mutations.
 */
sealed class MutationOutcome {
    /**
     * Mutation successfully applied, project revision incremented by exactly 1,
     * and SQLite WAL transaction committed.
     * Note: Requires Phase 7D revalidation before results are current.
     */
    data class Success(
        val proposalId: String,
        val proposalDigest: String,
        val projectId: String,
        val targetEntityId: String,
        val baseRevision: Long,
        val resultingRevision: Long,
        val appliedChanges: List<FieldChangeRecord>,
        val committedAt: Long,
        val status: MutationSuccessStatus = MutationSuccessStatus.COMMITTED_REVALIDATION_REQUIRED,
        val impact: MutationImpact
    ) : MutationOutcome()

    /**
     * Target project revision in database did not match the proposal's expected base revision.
     * Transaction aborted; zero entity writes and zero revision increments occurred.
     */
    data class RevisionConflict(
        val projectId: String,
        val expectedRevision: Long,
        val actualRevision: Long
    ) : MutationOutcome()

    /**
     * Target entity was not found in the target project.
     * Transaction aborted; zero entity writes and zero revision increments occurred.
     */
    data class EntityNotFound(
        val entityType: String,
        val entityId: String
    ) : MutationOutcome()

    /**
     * Target project was not found in database.
     * Transaction aborted; zero entity writes and zero revision increments occurred.
     */
    data class ProjectNotFound(
        val projectId: String
    ) : MutationOutcome()

    /**
     * Target entity exists but belongs to a different project than the proposal.
     * Transaction aborted; zero entity writes and zero revision increments occurred.
     */
    data class ProjectMismatch(
        val expectedProjectId: String,
        val actualProjectId: String
    ) : MutationOutcome()

    /**
     * Proposal intent structure was invalid (e.g. non-integer target entity ID for circuit).
     * No transaction opened; zero database modifications occurred.
     */
    data class InvalidIntent(
        val reason: String
    ) : MutationOutcome()

    /**
     * Patch values failed pre-transaction catalog, range, or syntax validation.
     * No transaction opened; zero database modifications occurred.
     */
    data class InvalidPatch(
        val reason: String
    ) : MutationOutcome()

    /**
     * Proposal approval grant verification failed (e.g. digest mismatch, expired, or DenyAll).
     * No transaction opened; zero database modifications occurred.
     */
    data class ApprovalNotVerified(
        val reason: String
    ) : MutationOutcome()

    /**
     * SQLite or Room transaction failed unexpectedly.
     * Transaction rolled back; message is sanitized with zero internal SQL/file details.
     */
    data class DatabaseFailure(
        val errorCode: String,
        val sanitizedMessage: String,
        val isRecoverable: Boolean = false,
        val diagnosticRef: String? = null
    ) : MutationOutcome()

    val isSuccess: Boolean
        get() = this is Success
}

/**
 * Internal container holding committed mutation results before public outcome mapping.
 */
internal data class InternalMutationSuccess(
    val proposalId: String,
    val proposalDigest: String,
    val projectId: String,
    val targetEntityId: String,
    val baseRevision: Long,
    val resultingRevision: Long,
    val fieldChanges: List<FieldChangeRecord>,
    val committedAt: Long,
    val candidateAffectedDomains: Set<String>
)

/**
 * Internal exception hierarchy used strictly inside [androidx.room.withTransaction]
 * to trigger deterministic SQLite transaction rollbacks.
 */
internal sealed class InternalRollbackException(message: String) : RuntimeException(message) {
    class ProjectNotFound(val projectId: String) :
        InternalRollbackException("Project '$projectId' not found in database.")

    class EntityNotFound(val entityType: String, val entityId: String) :
        InternalRollbackException("Entity '$entityType' with ID '$entityId' not found in database.")

    class ProjectMismatch(val expectedProjectId: String, val actualProjectId: String) :
        InternalRollbackException("Entity belongs to project '$actualProjectId', expected '$expectedProjectId'.")

    class RevisionConflict(val projectId: String, val expectedRevision: Long, val actualRevision: Long) :
        InternalRollbackException("Revision conflict for project '$projectId': expected $expectedRevision, actual $actualRevision.")

    class InvalidPatch(val reason: String) :
        InternalRollbackException(reason)

    class CircuitUpdateFailed(val projectId: String, val circuitId: Int) :
        InternalRollbackException("Circuit update for circuit ID $circuitId in project '$projectId' affected 0 rows.")

    class DatabaseError(val errorCode: String, val sanitizedMessage: String) :
        InternalRollbackException(sanitizedMessage)
}
