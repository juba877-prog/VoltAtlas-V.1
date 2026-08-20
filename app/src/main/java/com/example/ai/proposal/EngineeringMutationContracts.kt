package com.example.ai.proposal

import com.example.data.CircuitEntity
import com.example.data.catalog.CatalogRepository
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

/**
 * Authoritative, reusable internal helper for applying and validating cable circuit mutations.
 */
internal object CableCircuitMutationApplier {

    fun preValidateNumericBounds(instructions: CablePatchInstructions): String? {
        instructions.parallelRuns?.let {
            if (it.action == PatchAction.SET && it.value != null) {
                if (it.value < 1 || it.value > 20) {
                    return "parallelRuns must be between 1 and 20, got ${it.value}."
                }
            }
        }

        instructions.lengthM?.let {
            if (it.action == PatchAction.SET && it.value != null) {
                if (it.value.isNaN() || it.value.isInfinite() || it.value <= 0.0 || it.value > 10000.0) {
                    return "lengthM must be a finite positive number <= 10000.0, got ${it.value}."
                }
            }
        }

        instructions.overrideDeviceSize?.let {
            if (it.action == PatchAction.SET && it.value != null) {
                if (it.value.isNaN() || it.value.isInfinite() || it.value <= 0.0 || it.value > 100000.0) {
                    return "overrideDeviceSize must be a finite positive number <= 100000.0, got ${it.value}."
                }
            }
        }

        return null
    }

    fun preValidateCatalogConstraints(
        instructions: CablePatchInstructions,
        catalogRepository: CatalogRepository?
    ): String? {
        val catRepo = catalogRepository ?: return null

        instructions.cableType?.let { instr ->
            if (instr.action == PatchAction.SET && instr.value != null) {
                if (catRepo.cableTypesData.isNotEmpty()) {
                    val exists = catRepo.cableTypesData.any { it.cableType.equals(instr.value, ignoreCase = true) }
                    if (!exists) {
                        return "Cable type '${instr.value}' is not recognized in the cable catalog."
                    }
                }
            }
        }

        instructions.manufacturer?.let { instr ->
            if (instr.action == PatchAction.SET && instr.value != null) {
                if (catRepo.cableProducts.isNotEmpty()) {
                    val exists = catRepo.cableProducts.any { it.mfg.equals(instr.value, ignoreCase = true) }
                    if (!exists) {
                        return "Manufacturer '${instr.value}' is not recognized in the product catalog."
                    }
                }
            }
        }

        instructions.overrideDeviceType?.let { instr ->
            if (instr.action == PatchAction.SET && instr.value != null) {
                if (catRepo.breakers.isNotEmpty()) {
                    val exists = catRepo.breakers.any { it.type.equals(instr.value, ignoreCase = true) }
                    if (!exists) {
                        return "Device type '${instr.value}' is not recognized in the breaker catalog."
                    }
                }
            }
        }

        return null
    }

    fun validateMergedCatalogCombination(
        circuit: CircuitEntity,
        instructions: CablePatchInstructions,
        catalogRepository: CatalogRepository?
    ): String? {
        val catRepo = catalogRepository ?: return null

        val finalCableType = instructions.cableType?.value ?: circuit.cableType
        val finalMfg = instructions.manufacturer?.value ?: circuit.manufacturer
        val finalSize = when (instructions.overrideCableSize?.action) {
            PatchAction.SET -> instructions.overrideCableSize.value
            PatchAction.CLEAR -> null
            null -> circuit.overrideCableSize
        }
        val finalDeviceType = when (instructions.overrideDeviceType?.action) {
            PatchAction.SET -> instructions.overrideDeviceType.value
            PatchAction.CLEAR -> null
            null -> circuit.overrideDeviceType
        }
        val finalDeviceSize = when (instructions.overrideDeviceSize?.action) {
            PatchAction.SET -> instructions.overrideDeviceSize.value
            PatchAction.CLEAR -> null
            null -> circuit.overrideDeviceSize
        }

        if (catRepo.cableTypesData.isNotEmpty()) {
            val typeEntry = catRepo.cableTypesData.firstOrNull { it.cableType.equals(finalCableType, ignoreCase = true) }
            if (typeEntry == null) {
                return "Cable type '$finalCableType' is not recognized in the cable catalog."
            }

            if (catRepo.cableProducts.isNotEmpty()) {
                val mfgProducts = catRepo.cableProducts.filter {
                    it.mfg.equals(finalMfg, ignoreCase = true) && it.typeCode == typeEntry.typeCode
                }
                if (mfgProducts.isEmpty()) {
                    return "Manufacturer '$finalMfg' does not supply cable type '$finalCableType'."
                }

                if (finalSize != null) {
                    val numericSize = finalSize.replace("mm²", "").replace("mm2", "").trim().toDoubleOrNull()
                    val sizeExists = if (numericSize != null) {
                        mfgProducts.any { it.size == numericSize }
                    } else {
                        mfgProducts.any { it.model.contains(finalSize, ignoreCase = true) }
                    }
                    if (!sizeExists) {
                        return "Cable size '$finalSize' is not available for manufacturer '$finalMfg' and cable type '$finalCableType'."
                    }
                }
            }
        }

        if (finalDeviceType != null && catRepo.breakers.isNotEmpty()) {
            val breakersForType = catRepo.breakers.filter { it.type.equals(finalDeviceType, ignoreCase = true) }
            if (breakersForType.isEmpty()) {
                return "Device type '$finalDeviceType' is not recognized in the breaker catalog."
            }
            if (finalDeviceSize != null) {
                val ratingExists = breakersForType.any { it.ratingA == finalDeviceSize }
                if (!ratingExists) {
                    return "Device rating ${finalDeviceSize}A is not available for breaker type '$finalDeviceType'."
                }
            }
        }

        return null
    }

    fun buildPatchedCircuit(
        original: CircuitEntity,
        instructions: CablePatchInstructions
    ): Pair<CircuitEntity, List<FieldChangeRecord>> {
        var current = original
        val records = mutableListOf<FieldChangeRecord>()

        instructions.overrideCableSize?.let { instr ->
            val prev = TypedFieldValue.ofString(original.overrideCableSize)
            when (instr.action) {
                PatchAction.SET -> {
                    current = current.copy(overrideCableSize = instr.value)
                    records.add(
                        FieldChangeRecord(
                            fieldName = "overrideCableSize",
                            action = PatchAction.SET,
                            previousValue = prev,
                            newValue = TypedFieldValue.ofString(instr.value),
                            displayChange = "Set overrideCableSize from '${original.overrideCableSize}' to '${instr.value}'"
                        )
                    )
                }
                PatchAction.CLEAR -> {
                    current = current.copy(overrideCableSize = null)
                    records.add(
                        FieldChangeRecord(
                            fieldName = "overrideCableSize",
                            action = PatchAction.CLEAR,
                            previousValue = prev,
                            newValue = TypedFieldValue.ofNull(),
                            displayChange = "Cleared overrideCableSize (reset to auto)"
                        )
                    )
                }
            }
        }

        instructions.cableType?.let { instr ->
            if (instr.action == PatchAction.SET && instr.value != null) {
                val prev = TypedFieldValue.ofString(original.cableType)
                current = current.copy(cableType = instr.value)
                records.add(
                    FieldChangeRecord(
                        fieldName = "cableType",
                        action = PatchAction.SET,
                        previousValue = prev,
                        newValue = TypedFieldValue.ofString(instr.value),
                        displayChange = "Set cableType from '${original.cableType}' to '${instr.value}'"
                    )
                )
            }
        }

        instructions.manufacturer?.let { instr ->
            if (instr.action == PatchAction.SET && instr.value != null) {
                val prev = TypedFieldValue.ofString(original.manufacturer)
                current = current.copy(manufacturer = instr.value)
                records.add(
                    FieldChangeRecord(
                        fieldName = "manufacturer",
                        action = PatchAction.SET,
                        previousValue = prev,
                        newValue = TypedFieldValue.ofString(instr.value),
                        displayChange = "Set manufacturer from '${original.manufacturer}' to '${instr.value}'"
                    )
                )
            }
        }

        instructions.installation?.let { instr ->
            if (instr.action == PatchAction.SET && instr.value != null) {
                val prev = TypedFieldValue.ofString(original.installation)
                current = current.copy(installation = instr.value)
                records.add(
                    FieldChangeRecord(
                        fieldName = "installation",
                        action = PatchAction.SET,
                        previousValue = prev,
                        newValue = TypedFieldValue.ofString(instr.value),
                        displayChange = "Set installation from '${original.installation}' to '${instr.value}'"
                    )
                )
            }
        }

        instructions.parallelRuns?.let { instr ->
            if (instr.action == PatchAction.SET && instr.value != null) {
                val prev = TypedFieldValue.ofInt(original.parallelRuns)
                current = current.copy(parallelRuns = instr.value)
                records.add(
                    FieldChangeRecord(
                        fieldName = "parallelRuns",
                        action = PatchAction.SET,
                        previousValue = prev,
                        newValue = TypedFieldValue.ofInt(instr.value),
                        displayChange = "Set parallelRuns from ${original.parallelRuns} to ${instr.value}"
                    )
                )
            }
        }

        instructions.overrideDeviceSize?.let { instr ->
            val prev = TypedFieldValue.ofDouble(original.overrideDeviceSize, unit = "A")
            when (instr.action) {
                PatchAction.SET -> {
                    current = current.copy(overrideDeviceSize = instr.value)
                    records.add(
                        FieldChangeRecord(
                            fieldName = "overrideDeviceSize",
                            action = PatchAction.SET,
                            previousValue = prev,
                            newValue = TypedFieldValue.ofDouble(instr.value, unit = "A"),
                            displayChange = "Set overrideDeviceSize from ${original.overrideDeviceSize} A to ${instr.value} A"
                        )
                    )
                }
                PatchAction.CLEAR -> {
                    current = current.copy(overrideDeviceSize = null)
                    records.add(
                        FieldChangeRecord(
                            fieldName = "overrideDeviceSize",
                            action = PatchAction.CLEAR,
                            previousValue = prev,
                            newValue = TypedFieldValue.ofNull(),
                            displayChange = "Cleared overrideDeviceSize (reset to auto)"
                        )
                    )
                }
            }
        }

        instructions.overrideDeviceType?.let { instr ->
            val prev = TypedFieldValue.ofString(original.overrideDeviceType)
            when (instr.action) {
                PatchAction.SET -> {
                    current = current.copy(overrideDeviceType = instr.value)
                    records.add(
                        FieldChangeRecord(
                            fieldName = "overrideDeviceType",
                            action = PatchAction.SET,
                            previousValue = prev,
                            newValue = TypedFieldValue.ofString(instr.value),
                            displayChange = "Set overrideDeviceType from '${original.overrideDeviceType}' to '${instr.value}'"
                        )
                    )
                }
                PatchAction.CLEAR -> {
                    current = current.copy(overrideDeviceType = null)
                    records.add(
                        FieldChangeRecord(
                            fieldName = "overrideDeviceType",
                            action = PatchAction.CLEAR,
                            previousValue = prev,
                            newValue = TypedFieldValue.ofNull(),
                            displayChange = "Cleared overrideDeviceType (reset to auto)"
                        )
                    )
                }
            }
        }

        instructions.lengthM?.let { instr ->
            if (instr.action == PatchAction.SET && instr.value != null) {
                val prev = TypedFieldValue.ofDouble(original.length_m, unit = "m")
                current = current.copy(length_m = instr.value)
                records.add(
                    FieldChangeRecord(
                        fieldName = "lengthM",
                        action = PatchAction.SET,
                        previousValue = prev,
                        newValue = TypedFieldValue.ofDouble(instr.value, unit = "m"),
                        displayChange = "Set lengthM from ${original.length_m} m to ${instr.value} m"
                    )
                )
            }
        }

        return Pair(current, records)
    }

    fun determineAffectedDomains(instructions: CablePatchInstructions): Set<String> {
        val domains = mutableSetOf<String>()
        if (instructions.overrideCableSize != null ||
            instructions.cableType != null ||
            instructions.manufacturer != null ||
            instructions.installation != null ||
            instructions.parallelRuns != null ||
            instructions.lengthM != null
        ) {
            domains.add("CABLE_SIZING")
            domains.add("VOLTAGE_DROP")
            domains.add("SHORT_CIRCUIT")
        }

        if (instructions.overrideDeviceSize != null || instructions.overrideDeviceType != null) {
            domains.add("PROTECTION_ADS")
            domains.add("COORDINATION")
        }

        return domains
    }
}

