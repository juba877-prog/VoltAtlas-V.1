package com.example.ai.proposal

import androidx.room.withTransaction
import com.example.data.AppDatabase
import com.example.data.CircuitDao
import com.example.data.CircuitEntity
import com.example.data.ProjectDao
import com.example.data.catalog.CatalogRepository
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

/**
 * Internal, disabled mutation engine for applying revision-guarded cable circuit proposals.
 *
 * Invariants:
 * - Internal visibility: Unreachable from public ViewModels, tool registries, and orchestrators.
 * - Shared Publication Fence: Transaction and revision CAS acquire [EngineeringCalculationCoordinator.getPublicationMutex].
 * - Atomic CAS: Revision increment is the FIRST database write inside [androidx.room.withTransaction].
 * - Rollback Guarantee: Every failure path inside the transaction throws an [InternalRollbackException].
 * - Cancellation Safety: [CancellationException] is strictly rethrown and never swallowed.
 * - Checked Write: Project-scoped circuit update row count must equal 1.
 */
internal class EngineeringMutationEngine(
    private val db: AppDatabase,
    private val projectDao: ProjectDao = db.projectDao(),
    private val circuitDao: CircuitDao = db.circuitDao(),
    private val catalogRepository: CatalogRepository? = null,
    private val approvalVerifier: ProposalApprovalVerifier = DenyAllApprovalVerifier,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun applyCableMutation(
        intent: ValidatedCableProposalIntent,
        grant: ProposalApprovalGrant
    ): MutationOutcome {
        val now = clock()

        // 1. Outside Transaction: Pre-Transaction Boundary Validations
        if (intent.targetEntityType != "CIRCUIT") {
            return MutationOutcome.InvalidIntent("Target entity type must be 'CIRCUIT', got '${intent.targetEntityType}'.")
        }

        val targetCircuitId = intent.targetEntityId.toIntOrNull()
            ?: return MutationOutcome.InvalidIntent("Target entity ID '${intent.targetEntityId}' is not a valid integer circuit ID.")

        val approvalResult = approvalVerifier.verifyApproval(intent, grant, now)
        if (approvalResult is ApprovalVerificationResult.Rejected) {
            return MutationOutcome.ApprovalNotVerified(approvalResult.reason)
        }

        val numericValidationError = preValidateNumericBounds(intent.instructions)
        if (numericValidationError != null) {
            return MutationOutcome.InvalidPatch(numericValidationError)
        }

        val catalogValidationResult = preValidateCatalogConstraints(intent.instructions)
        if (catalogValidationResult != null) {
            return MutationOutcome.InvalidPatch(catalogValidationResult)
        }

        // 2. Inside Transaction with Shared Project Publication Fence: Atomic Guarded Mutation with Proven Rollback
        return EngineeringCalculationCoordinator.getPublicationMutex(intent.projectId).withLock {
            try {
                val internalSuccess = db.withTransaction {
                // 2.1. Atomic Compare-And-Swap Revision Claim (FIRST DB WRITE)
                val rowsUpdated = projectDao.incrementRevisionGuarded(
                    id = intent.projectId,
                    expectedRevision = intent.baseProjectRevision,
                    updatedAt = now
                )

                if (rowsUpdated != 1) {
                    // Diagnose inside the exact same transaction snapshot
                    val currentRev = projectDao.getRevision(intent.projectId)
                    if (currentRev == null) {
                        throw InternalRollbackException.ProjectNotFound(intent.projectId)
                    } else {
                        throw InternalRollbackException.RevisionConflict(
                            projectId = intent.projectId,
                            expectedRevision = intent.baseProjectRevision,
                            actualRevision = currentRev
                        )
                    }
                }

                // 2.2. Project-Scoped Target Circuit Lookup
                val circuit = circuitDao.getCircuitByIdAndProject(intent.projectId, targetCircuitId)
                    ?: throw InternalRollbackException.EntityNotFound("CIRCUIT", intent.targetEntityId)

                // 2.3. Project Ownership and Operation Boundary Confirmation
                if (circuit.projectId != intent.projectId) {
                    throw InternalRollbackException.ProjectMismatch(
                        expectedProjectId = intent.projectId,
                        actualProjectId = circuit.projectId
                    )
                }

                // 2.4. Validate Final Merged Combination Against Authoritative Catalog
                val finalCombinationError = validateMergedCatalogCombination(circuit, intent.instructions)
                if (finalCombinationError != null) {
                    throw InternalRollbackException.InvalidPatch(finalCombinationError)
                }

                // 2.5. Build Patched Entity and Typed Field Change Records
                val (patchedCircuit, fieldChanges) = buildPatchedCircuit(circuit, intent.instructions)

                // 2.6. Checked Project-Scoped Circuit Write
                val circuitRowsUpdated = circuitDao.updateCircuit(patchedCircuit)
                if (circuitRowsUpdated != 1) {
                    throw InternalRollbackException.CircuitUpdateFailed(intent.projectId, targetCircuitId)
                }

                // 2.7. Return Internal Success (Commit Linearization Point)
                InternalMutationSuccess(
                    proposalId = intent.proposalId,
                    proposalDigest = intent.proposalDigest,
                    projectId = intent.projectId,
                    targetEntityId = intent.targetEntityId,
                    baseRevision = intent.baseProjectRevision,
                    resultingRevision = intent.baseProjectRevision + 1L,
                    fieldChanges = fieldChanges,
                    committedAt = now,
                    candidateAffectedDomains = determineAffectedDomains(intent.instructions)
                )
            }

            // 3. Outside Transaction: Map Committed Success to Public Outcome
            MutationOutcome.Success(
                proposalId = internalSuccess.proposalId,
                proposalDigest = internalSuccess.proposalDigest,
                projectId = internalSuccess.projectId,
                targetEntityId = internalSuccess.targetEntityId,
                baseRevision = internalSuccess.baseRevision,
                resultingRevision = internalSuccess.resultingRevision,
                appliedChanges = internalSuccess.fieldChanges,
                committedAt = internalSuccess.committedAt,
                status = MutationSuccessStatus.COMMITTED_REVALIDATION_REQUIRED,
                impact = MutationImpact(
                    projectId = internalSuccess.projectId,
                    entityType = "CIRCUIT",
                    entityId = internalSuccess.targetEntityId,
                    mutatedFields = internalSuccess.fieldChanges.map { it.fieldName }.toSet(),
                    baseRevision = internalSuccess.baseRevision,
                    resultingRevision = internalSuccess.resultingRevision,
                    candidateAffectedDomains = internalSuccess.candidateAffectedDomains,
                    committedAt = internalSuccess.committedAt,
                    status = MutationSuccessStatus.COMMITTED_REVALIDATION_REQUIRED,
                    requiresRevalidation = true
                )
            )
        } catch (e: InternalRollbackException.ProjectNotFound) {
            MutationOutcome.ProjectNotFound(e.projectId)
        } catch (e: InternalRollbackException.EntityNotFound) {
            MutationOutcome.EntityNotFound(e.entityType, e.entityId)
        } catch (e: InternalRollbackException.ProjectMismatch) {
            MutationOutcome.ProjectMismatch(e.expectedProjectId, e.actualProjectId)
        } catch (e: InternalRollbackException.RevisionConflict) {
            MutationOutcome.RevisionConflict(e.projectId, e.expectedRevision, e.actualRevision)
        } catch (e: InternalRollbackException.InvalidPatch) {
            MutationOutcome.InvalidPatch(e.reason)
        } catch (e: InternalRollbackException.CircuitUpdateFailed) {
            MutationOutcome.DatabaseFailure(
                errorCode = "CIRCUIT_UPDATE_ZERO_ROWS",
                sanitizedMessage = "Failed to update target circuit in database.",
                isRecoverable = true
            )
        } catch (e: CancellationException) {
            // Strictly rethrow CancellationException: Never convert to DatabaseFailure!
            throw e
        } catch (t: Throwable) {
            MutationOutcome.DatabaseFailure(
                errorCode = "DB_TRANSACTION_FAILED",
                sanitizedMessage = "Database mutation transaction failed unexpectedly.",
                isRecoverable = true,
                diagnosticRef = "TX-ERR-$now"
            )
        }
    }
}

    private fun preValidateNumericBounds(instructions: CablePatchInstructions): String? {
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

    private fun preValidateCatalogConstraints(instructions: CablePatchInstructions): String? {
        val catRepo = catalogRepository ?: return null // Optional if running in minimal test fixture

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

    private fun validateMergedCatalogCombination(
        circuit: CircuitEntity,
        instructions: CablePatchInstructions
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

    private fun buildPatchedCircuit(
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

    private fun determineAffectedDomains(instructions: CablePatchInstructions): Set<String> {
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
