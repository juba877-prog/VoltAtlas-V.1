package com.example.ai.proposal

import androidx.room.withTransaction
import com.example.ai.context.freshness.contracts.CalculationDomain
import com.example.data.AppDatabase
import com.example.data.CircuitDao
import com.example.data.CircuitEntity
import com.example.data.ProjectDao
import com.example.data.ai.ApprovalGrantDao
import com.example.data.ai.ApprovalGrantEntity
import com.example.data.ai.MutationExecutionReceiptDao
import com.example.data.ai.MutationExecutionReceiptEntity
import com.example.data.catalog.CatalogRepository
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Authoritative, internal orchestrator executing durable proposal approval consumption,
 * atomic database mutation, committed receipt recording, and post-commit targeted revalidation.
 *
 * Invariants:
 * - Internal visibility: Unreachable from UI, ViewModels, Assistant, Gemini, tools, and Firebase.
 * - Single Atomic DB Truth Boundary: Approval consumption, revision CAS increment, circuit write,
 *   and receipt insertion execute inside the exact same [androidx.room.withTransaction] block.
 * - Linearizable Publication: Database CAS and post-commit revalidation acquire [EngineeringCalculationCoordinator.getPublicationMutex].
 * - Single-Use & Replay: Consumed approvals return existing durable receipts without remutation.
 * - Mandatory Catalog Gate: Missing or unpopulated [CatalogRepository] fails closed before transaction opening.
 * - Guarded Lifecycle: Receipt states transition only via conditional DAO queries with verified row counts.
 * - Cancellation Safety: [CancellationException] is strictly rethrown and never converted to database failure.
 */
internal class DurableMutationWorkflow(
    private val db: AppDatabase,
    private val approvalGrantDao: ApprovalGrantDao = db.approvalGrantDao(),
    private val mutationExecutionReceiptDao: MutationExecutionReceiptDao = db.mutationExecutionReceiptDao(),
    private val projectDao: ProjectDao = db.projectDao(),
    private val circuitDao: CircuitDao = db.circuitDao(),
    private val transformerDao: com.example.data.TransformerDao = db.transformerDao(),
    private val catalogRepository: CatalogRepository,
    private val revalidationOrchestrator: PostCommitRevalidationOrchestrator = PostCommitRevalidationOrchestrator(),
    private val semanticKeyEncoder: ProposalSemanticKeyEncoder = DefaultProposalSemanticKeyEncoder(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val executionIdProvider: () -> String = { UUID.randomUUID().toString() },
    private val staleRunningThresholdMs: Long = 60_000L
) {

    /**
     * Executes the end-to-end durable approval mutation workflow.
     */
    suspend fun execute(request: DurableMutationRequest): DurableMutationOutcome {
        val now = clock()
        val intent = request.intent
        val approvalId = request.approvalId

        // 1. Mandatory Catalog Gate (Fail-Closed)
        if (catalogRepository.cableTypesData.isEmpty() ||
            catalogRepository.cableProducts.isEmpty() ||
            catalogRepository.breakers.isEmpty()
        ) {
            return DurableMutationOutcome.InvalidPatch("Engineering catalog data is not loaded or unavailable.")
        }

        // 2. Pre-Validation of Intent & Numeric Bounds
        if (intent.targetEntityType != "CIRCUIT") {
            return DurableMutationOutcome.InvalidIntent("Target entity type must be 'CIRCUIT', got '${intent.targetEntityType}'.")
        }
        val targetCircuitId = intent.targetEntityId.toIntOrNull()
            ?: return DurableMutationOutcome.InvalidIntent("Target entity ID '${intent.targetEntityId}' is not a valid integer.")

        val numericError = CableCircuitMutationApplier.preValidateNumericBounds(intent.instructions)
        if (numericError != null) {
            return DurableMutationOutcome.InvalidPatch(numericError)
        }

        val catalogError = CableCircuitMutationApplier.preValidateCatalogConstraints(intent.instructions, catalogRepository)
        if (catalogError != null) {
            return DurableMutationOutcome.InvalidPatch(catalogError)
        }

        // 3. Load & Verify Approval Grant
        val grant = approvalGrantDao.getGrantById(approvalId)
            ?: return DurableMutationOutcome.ApprovalNotFound(approvalId, "Approval grant '$approvalId' was not found.")

        if (grant.status == ApprovalStatus.CANCELLED) {
            return DurableMutationOutcome.ApprovalCancelled(approvalId, "Approval grant '$approvalId' has been cancelled.")
        }

        if (grant.status == ApprovalStatus.EXPIRED || (grant.status == ApprovalStatus.ISSUED && grant.expiresAt <= now)) {
            if (grant.status == ApprovalStatus.ISSUED) {
                approvalGrantDao.expireGrantGuarded(approvalId, now)
            }
            return DurableMutationOutcome.ApprovalExpired(
                approvalId = approvalId,
                expiredAt = grant.expiresAt,
                sanitizedReason = "Approval grant '$approvalId' expired at ${grant.expiresAt} (current time: $now)."
            )
        }

        if (grant.status == ApprovalStatus.CONSUMED) {
            val existingReceipt = mutationExecutionReceiptDao.getReceiptByApprovalId(approvalId)
            return if (existingReceipt != null) {
                val appliedChanges = try {
                    ReceiptPayloadCodec.decode(existingReceipt.appliedChangesJson)
                } catch (e: Exception) {
                    emptyList()
                }
                DurableMutationOutcome.ExistingReceiptReturned(
                    receipt = existingReceipt,
                    appliedChanges = appliedChanges,
                    message = "Approval '$approvalId' was already consumed. Returning existing committed receipt '${existingReceipt.executionId}'."
                )
            } else {
                DurableMutationOutcome.ReceiptIntegrityFailure(
                    approvalId = approvalId,
                    sanitizedReason = "Approval '$approvalId' is marked CONSUMED, but no matching mutation receipt exists."
                )
            }
        }

        if (grant.status != ApprovalStatus.ISSUED) {
            return DurableMutationOutcome.ApprovalMismatch(
                approvalId = approvalId,
                fieldName = "status",
                expectedValue = ApprovalStatus.ISSUED.name,
                actualValue = grant.status.name,
                sanitizedReason = "Approval grant must be in state ISSUED, but was '${grant.status}'."
            )
        }

        // 4. Detailed Approval Identity Verification
        if (grant.issuerType != ApprovalIssuerType.USER_EXPLICIT_CONFIRMATION) {
            return DurableMutationOutcome.ApprovalMismatch(
                approvalId = approvalId,
                fieldName = "issuerType",
                expectedValue = ApprovalIssuerType.USER_EXPLICIT_CONFIRMATION.name,
                actualValue = grant.issuerType.name,
                sanitizedReason = "Invalid approval issuer type."
            )
        }
        if (grant.userActionId.isBlank()) {
            return DurableMutationOutcome.ApprovalMismatch(
                approvalId = approvalId,
                fieldName = "userActionId",
                expectedValue = "NON_BLANK",
                actualValue = "BLANK",
                sanitizedReason = "Approval userActionId cannot be blank."
            )
        }
        if (grant.approvedBy.isBlank()) {
            return DurableMutationOutcome.ApprovalMismatch(
                approvalId = approvalId,
                fieldName = "approvedBy",
                expectedValue = "NON_BLANK",
                actualValue = "BLANK",
                sanitizedReason = "Approval approvedBy cannot be blank."
            )
        }
        if (grant.proposalId != intent.proposalId) {
            return DurableMutationOutcome.ApprovalMismatch(
                approvalId = approvalId,
                fieldName = "proposalId",
                expectedValue = intent.proposalId,
                actualValue = grant.proposalId,
                sanitizedReason = "Proposal ID mismatch."
            )
        }
        if (grant.proposalDigest != intent.proposalDigest) {
            return DurableMutationOutcome.ApprovalMismatch(
                approvalId = approvalId,
                fieldName = "proposalDigest",
                expectedValue = intent.proposalDigest,
                actualValue = grant.proposalDigest,
                sanitizedReason = "Proposal artifact digest mismatch."
            )
        }
        val expectedSemanticKey = semanticKeyEncoder.computeSemanticKey(
            schemaVersion = intent.schemaVersion,
            projectId = intent.projectId,
            baseProjectRevision = intent.baseProjectRevision,
            targetEntityType = intent.targetEntityType,
            targetEntityId = intent.targetEntityId,
            operation = intent.operation,
            patch = intent.instructions.toCableCircuitPatch()
        )
        if (grant.proposalSemanticKey != expectedSemanticKey) {
            return DurableMutationOutcome.ApprovalMismatch(
                approvalId = approvalId,
                fieldName = "proposalSemanticKey",
                expectedValue = expectedSemanticKey,
                actualValue = grant.proposalSemanticKey,
                sanitizedReason = "Proposal semantic key mismatch."
            )
        }
        if (grant.projectId != intent.projectId) {
            return DurableMutationOutcome.ApprovalMismatch(
                approvalId = approvalId,
                fieldName = "projectId",
                expectedValue = intent.projectId,
                actualValue = grant.projectId,
                sanitizedReason = "Project ID mismatch."
            )
        }
        if (grant.targetEntityType != intent.targetEntityType) {
            return DurableMutationOutcome.ApprovalMismatch(
                approvalId = approvalId,
                fieldName = "targetEntityType",
                expectedValue = intent.targetEntityType,
                actualValue = grant.targetEntityType,
                sanitizedReason = "Target entity type mismatch."
            )
        }
        if (grant.targetEntityId != intent.targetEntityId) {
            return DurableMutationOutcome.ApprovalMismatch(
                approvalId = approvalId,
                fieldName = "targetEntityId",
                expectedValue = intent.targetEntityId,
                actualValue = grant.targetEntityId,
                sanitizedReason = "Target entity ID mismatch."
            )
        }
        if (grant.baseProjectRevision != intent.baseProjectRevision) {
            return DurableMutationOutcome.ApprovalMismatch(
                approvalId = approvalId,
                fieldName = "baseProjectRevision",
                expectedValue = intent.baseProjectRevision.toString(),
                actualValue = grant.baseProjectRevision.toString(),
                sanitizedReason = "Base project revision mismatch."
            )
        }
        if (grant.activeProposalSemanticKey != grant.proposalSemanticKey) {
            return DurableMutationOutcome.ApprovalMismatch(
                approvalId = approvalId,
                fieldName = "activeProposalSemanticKey",
                expectedValue = grant.proposalSemanticKey,
                actualValue = grant.activeProposalSemanticKey ?: "NULL",
                sanitizedReason = "Active proposal semantic key mismatch."
            )
        }

        // 5. Determine Execution ID
        val executionId = (request.executionId?.takeIf { it.isNotBlank() } ?: executionIdProvider()).trim()

        // 6. Single Atomic DB Transaction with Publication Fence
        val commitRecord: InternalDurableCommitRecord = try {
            EngineeringCalculationCoordinator.getPublicationMutex(intent.projectId).withLock {
                db.withTransaction {
                    // 6.1. Atomically consume approval
                    val consumeRows = approvalGrantDao.consumeGrantGuarded(
                        approvalId = approvalId,
                        expectedSemanticKey = grant.proposalSemanticKey,
                        expectedRevision = intent.baseProjectRevision,
                        executionId = executionId,
                        now = now
                    )
                    if (consumeRows != 1) {
                        throw InternalDurableRollbackException.ApprovalConsumeFailed(approvalId)
                    }

                    // 6.2. Atomically increment project revision CAS
                    val revRows = projectDao.incrementRevisionGuarded(
                        id = intent.projectId,
                        expectedRevision = intent.baseProjectRevision,
                        updatedAt = now
                    )
                    if (revRows != 1) {
                        val currentRev = projectDao.getRevision(intent.projectId)
                        if (currentRev == null) {
                            throw InternalDurableRollbackException.ProjectNotFound(intent.projectId)
                        } else {
                            throw InternalDurableRollbackException.RevisionConflict(
                                projectId = intent.projectId,
                                expectedRevision = intent.baseProjectRevision,
                                actualRevision = currentRev
                            )
                        }
                    }

                    // 6.3. Project-scoped target circuit lookup
                    val circuit = circuitDao.getCircuitByIdAndProject(intent.projectId, targetCircuitId)
                        ?: throw InternalDurableRollbackException.EntityNotFound("CIRCUIT", intent.targetEntityId)

                    if (circuit.projectId != intent.projectId) {
                        throw InternalDurableRollbackException.ProjectMismatch(intent.projectId, circuit.projectId)
                    }

                    // 6.3a. Approved-precondition revalidation against authoritative persisted state.
                    //      Fails closed before any write; the rollback is a pure no-op, so no partial
                    //      value change, revision advance, or receipt can survive.
                    val preconditionFailure = evaluatePrecondition(
                        circuit = circuit,
                        expectedCurrentValues = request.expectedCurrentValues
                    )
                    if (preconditionFailure != null) {
                        throw preconditionFailure
                    }

                    // 6.4. Build patched circuit & field changes (pure transformation)
                    val (patchedCircuit, fieldChanges) = CableCircuitMutationApplier.buildPatchedCircuit(
                        original = circuit,
                        instructions = intent.instructions
                    )

                    // 6.4a. No-op protection: the approved patch must produce a real persisted change.
                    if (patchedCircuit == circuit) {
                        throw InternalDurableRollbackException.NoOpDetected(intent.proposalId)
                    }

                    // 6.5. Validate merged catalog combination
                    val mergedError = CableCircuitMutationApplier.validateMergedCatalogCombination(
                        circuit = circuit,
                        instructions = intent.instructions,
                        catalogRepository = catalogRepository
                    )
                    if (mergedError != null) {
                        throw InternalDurableRollbackException.InvalidPatch(mergedError)
                    }

                    // 6.6. Checked circuit write
                    val circuitRows = circuitDao.updateCircuit(patchedCircuit)
                    if (circuitRows != 1) {
                        throw InternalDurableRollbackException.CircuitUpdateFailed(intent.projectId, targetCircuitId)
                    }

                    // 6.7. Encode receipt payload
                    val payloadJson = ReceiptPayloadCodec.encode(fieldChanges)

                    // 6.8. Construct & insert committed receipt
                    val receipt = MutationExecutionReceiptEntity(
                        executionId = executionId,
                        approvalId = grant.approvalId,
                        proposalId = grant.proposalId,
                        proposalDigest = grant.proposalDigest,
                        proposalSemanticKey = grant.proposalSemanticKey,
                        projectId = grant.projectId,
                        targetEntityType = grant.targetEntityType,
                        targetEntityId = grant.targetEntityId,
                        revisionBefore = intent.baseProjectRevision,
                        revisionAfter = intent.baseProjectRevision + 1L,
                        revalidationStatus = RevalidationStatus.PENDING,
                        appliedChangesJson = payloadJson,
                        createdAt = now,
                        committedAt = now,
                        attemptCount = 0
                    )
                    mutationExecutionReceiptDao.insertReceipt(receipt)

                    InternalDurableCommitRecord(
                        receipt = receipt,
                        patchedCircuit = patchedCircuit,
                        fieldChanges = fieldChanges,
                        candidateAffectedDomains = CableCircuitMutationApplier.determineAffectedDomains(intent.instructions)
                    )
                }
            }
        } catch (e: InternalDurableRollbackException.ApprovalConsumeFailed) {
            val latestGrant = approvalGrantDao.getGrantById(approvalId)
            return when (latestGrant?.status) {
                ApprovalStatus.CONSUMED -> {
                    val existingReceipt = mutationExecutionReceiptDao.getReceiptByApprovalId(approvalId)
                    DurableMutationOutcome.ApprovalAlreadyConsumed(
                        approvalId = approvalId,
                        existingReceipt = existingReceipt,
                        sanitizedReason = "Approval '$approvalId' was already consumed."
                    )
                }
                ApprovalStatus.EXPIRED -> DurableMutationOutcome.ApprovalExpired(approvalId, latestGrant.expiresAt, "Approval expired.")
                ApprovalStatus.CANCELLED -> DurableMutationOutcome.ApprovalCancelled(approvalId, "Approval cancelled.")
                else -> DurableMutationOutcome.ApprovalMismatch(
                    approvalId = approvalId,
                    fieldName = "status",
                    expectedValue = ApprovalStatus.ISSUED.name,
                    actualValue = latestGrant?.status?.name ?: "UNKNOWN",
                    sanitizedReason = "Failed to consume approval grant."
                )
            }
        } catch (e: InternalDurableRollbackException.NoOpDetected) {
            return DurableMutationOutcome.NoOp(
                proposalId = intent.proposalId,
                projectId = intent.projectId,
                targetEntityId = intent.targetEntityId,
                revision = intent.baseProjectRevision,
                sanitizedReason = "The approved patch is already the persisted state; no change was required."
            )
        } catch (e: InternalDurableRollbackException.PreconditionFailed) {
            return DurableMutationOutcome.CurrentValueChanged(
                proposalId = intent.proposalId,
                projectId = intent.projectId,
                targetEntityId = intent.targetEntityId,
                revision = intent.baseProjectRevision,
                fieldName = e.fieldName,
                expectedValue = e.expectedValue,
                actualValue = e.actualValue,
                sanitizedReason = "The persisted value of '${e.fieldName}' changed after the proposal was reviewed. " +
                    "Re-review and recreate the proposal before approving."
            )
        } catch (e: InternalDurableRollbackException.RevisionConflict) {
            return DurableMutationOutcome.RevisionConflict(
                projectId = e.projectId,
                expectedRevision = e.expectedRevision,
                actualRevision = e.actualRevision,
                sanitizedReason = "Project revision conflict: expected ${e.expectedRevision}, actual ${e.actualRevision}."
            )
        } catch (e: InternalDurableRollbackException.ProjectNotFound) {
            return DurableMutationOutcome.EntityNotFound("PROJECT", e.projectId, "Project '${e.projectId}' was not found.")
        } catch (e: InternalDurableRollbackException.EntityNotFound) {
            return DurableMutationOutcome.EntityNotFound(e.entityType, e.entityId, "Entity '${e.entityType}:${e.entityId}' was not found.")
        } catch (e: InternalDurableRollbackException.ProjectMismatch) {
            return DurableMutationOutcome.InvalidIntent("Entity belongs to project '${e.actualProjectId}', expected '${e.expectedProjectId}'.")
        } catch (e: InternalDurableRollbackException.InvalidPatch) {
            return DurableMutationOutcome.InvalidPatch(e.reason)
        } catch (e: InternalDurableRollbackException.CircuitUpdateFailed) {
            return DurableMutationOutcome.DatabaseFailure(
                errorCode = "CIRCUIT_UPDATE_FAILED",
                messageKey = "error.database.circuit_update",
                isRecoverable = true,
                sanitizedReason = "Circuit write failed in database."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            return DurableMutationOutcome.DatabaseFailure(
                errorCode = "DB_TRANSACTION_FAILED",
                messageKey = "error.database.transaction",
                isRecoverable = true,
                diagnosticRef = "TX-$now",
                sanitizedReason = "Database mutation transaction failed unexpectedly."
            )
        }

        // 7. Post-Commit Revalidation Phase
        return runRevalidationLifecycle(commitRecord)
    }

    /**
     * Resolves the authoritative project/entity binding for an approval request directly from Room.
     *
     * This is the approval-time source of truth: no caller-supplied context, model summary, or
     * conversation projection participates. Returns null when the project itself does not exist,
     * which the gateway must treat as a fail-closed rejection.
     */
    internal suspend fun resolveAuthoritativeApprovalContext(
        projectId: String,
        entityType: String,
        entityId: String
    ): AuthoritativeApprovalContext? {
        if (projectId.isBlank()) return null
        val liveRevision = projectDao.getRevision(projectId) ?: return null

        val numericEntityId = entityId.toIntOrNull()
        if (entityType != CIRCUIT_ENTITY_TYPE || numericEntityId == null) {
            return AuthoritativeApprovalContext(
                projectId = projectId,
                liveRevision = liveRevision,
                targetEntityType = entityType,
                targetEntityId = entityId,
                entityFound = false,
                targetEntityProjectId = null
            )
        }

        val scoped = circuitDao.getCircuitByIdAndProject(projectId, numericEntityId)
        if (scoped != null) {
            return AuthoritativeApprovalContext(
                projectId = projectId,
                liveRevision = liveRevision,
                targetEntityType = entityType,
                targetEntityId = entityId,
                entityFound = true,
                targetEntityProjectId = scoped.projectId
            )
        }

        // The entity exists, but not inside the authoritative project -> cross-project attempt.
        val foreign = circuitDao.getCircuitById(numericEntityId)
        return AuthoritativeApprovalContext(
            projectId = projectId,
            liveRevision = liveRevision,
            targetEntityType = entityType,
            targetEntityId = entityId,
            entityFound = false,
            targetEntityProjectId = foreign?.projectId
        )
    }

    /**
     * Authoritative idempotency lookup: returns the already-committed receipt for an identical
     * semantic mutation signature, if one exists. A non-null result means the exact approved
     * semantic action is already durable and must never be executed a second time.
     */
    internal suspend fun findExistingReceipt(
        projectId: String,
        proposalSemanticKey: String
    ): MutationExecutionReceiptEntity? {
        if (projectId.isBlank() || proposalSemanticKey.isBlank()) return null
        return mutationExecutionReceiptDao.getReceiptBySemanticKey(projectId, proposalSemanticKey)
    }

    /**
     * Revalidates the recorded persisted-value preconditions of an approved proposal against the
     * authoritative circuit row read inside the mutation transaction.
     *
     * Value-identity comparison only: no engineering quantity is recomputed, re-derived, or
     * "corrected" here. Returns null when every recorded precondition currently holds.
     */
    private fun evaluatePrecondition(
        circuit: CircuitEntity,
        expectedCurrentValues: Map<String, String>
    ): InternalDurableRollbackException.PreconditionFailed? {
        for ((fieldName, expectedValue) in expectedCurrentValues) {
            val actualValue: String? = when (fieldName) {
                "overrideCableSize" -> circuit.overrideCableSize
                "cableType" -> circuit.cableType
                "manufacturer" -> circuit.manufacturer
                "installation" -> circuit.installation
                "parallelRuns" -> circuit.parallelRuns.toString()
                "overrideDeviceSize" -> circuit.overrideDeviceSize?.toString()
                "overrideDeviceType" -> circuit.overrideDeviceType
                "lengthM" -> circuit.length_m.toString()
                else -> return InternalDurableRollbackException.PreconditionFailed(
                    fieldName = fieldName,
                    expectedValue = expectedValue,
                    actualValue = null
                )
            }
            if (!scalarValuesMatch(expectedValue, actualValue)) {
                return InternalDurableRollbackException.PreconditionFailed(
                    fieldName = fieldName,
                    expectedValue = expectedValue,
                    actualValue = actualValue
                )
            }
        }
        return null
    }

    /**
     * Compares two persisted scalar values for identity. String comparison is exact after trimming;
     * numeric formatting differences ("240" vs "240.0") are treated as the same persisted value.
     */
    private fun scalarValuesMatch(expected: String?, actual: String?): Boolean {
        val e = expected?.trim()
        val a = actual?.trim()
        if (e == null || a == null) return e == a
        if (e == a) return true
        val eNum = e.toDoubleOrNull()
        val aNum = a.toDoubleOrNull()
        return eNum != null && aNum != null && kotlin.math.abs(eNum - aNum) < SCALAR_IDENTITY_EPSILON
    }

    /**
     * Resumes or retries revalidation for a committed receipt following a process restart,
     * transient revalidation failure, or retryable terminal state.
     */
    suspend fun recoverOrRetryReceipt(executionId: String): DurableMutationOutcome {
        val now = clock()
        val receipt = mutationExecutionReceiptDao.getReceiptById(executionId)
            ?: return DurableMutationOutcome.EntityNotFound("RECEIPT", executionId, "Receipt '$executionId' was not found.")

        val appliedChanges = try {
            ReceiptPayloadCodec.decode(receipt.appliedChangesJson)
        } catch (e: Exception) {
            emptyList()
        }

        when (receipt.revalidationStatus) {
            RevalidationStatus.SUCCEEDED,
            RevalidationStatus.NOT_REQUIRED -> {
                return DurableMutationOutcome.Completed(
                    receipt = receipt,
                    appliedChanges = appliedChanges,
                    revalidationOutcome = RevalidationOutcome.RevalidatedSuccessfully(
                        projectId = receipt.projectId,
                        targetEntityId = receipt.targetEntityId,
                        resultingRevision = receipt.revisionAfter,
                        revalidatedDomains = setOf(CalculationDomain.CABLE_SIZING, CalculationDomain.SHORT_CIRCUIT, CalculationDomain.PROTECTION_ADS, CalculationDomain.COORDINATION),
                        affectedCircuitIds = listOf(receipt.targetEntityId.toIntOrNull() ?: 0),
                        completedAt = receipt.workflowCompletedAt ?: receipt.committedAt
                    )
                )
            }
            RevalidationStatus.SUPERSEDED -> {
                return DurableMutationOutcome.Superseded(
                    receipt = receipt,
                    appliedChanges = appliedChanges,
                    revalidationOutcome = RevalidationOutcome.RevisionAdvancedOrSuperseded(
                        projectId = receipt.projectId,
                        expectedRevision = receipt.revisionAfter,
                        currentRevision = receipt.revisionAfter + 1L
                    )
                )
            }
            RevalidationStatus.PENDING -> {
                val p2r = mutationExecutionReceiptDao.transitionPendingToRunning(executionId, now)
                if (p2r != 1) {
                    val updated = mutationExecutionReceiptDao.getReceiptById(executionId) ?: receipt
                    return DurableMutationOutcome.ExistingReceiptReturned(updated, appliedChanges, "Receipt state was updated concurrently.")
                }
            }
            RevalidationStatus.RUNNING -> {
                val isStale = receipt.lastAttemptAt == null || (now - receipt.lastAttemptAt) > staleRunningThresholdMs
                if (isStale) {
                    val retryRows = mutationExecutionReceiptDao.transitionRetryToRunning(executionId, now)
                    if (retryRows != 1) {
                        val updated = mutationExecutionReceiptDao.getReceiptById(executionId) ?: receipt
                        return DurableMutationOutcome.ExistingReceiptReturned(updated, appliedChanges, "Receipt state was updated concurrently.")
                    }
                } else {
                    return DurableMutationOutcome.ExistingReceiptReturned(receipt, appliedChanges, "Revalidation is currently RUNNING.")
                }
            }
            RevalidationStatus.PARTIAL,
            RevalidationStatus.FAILED,
            RevalidationStatus.CANCELLED -> {
                val retryRows = mutationExecutionReceiptDao.transitionRetryToRunning(executionId, now)
                if (retryRows != 1) {
                    val updated = mutationExecutionReceiptDao.getReceiptById(executionId) ?: receipt
                    return DurableMutationOutcome.ExistingReceiptReturned(updated, appliedChanges, "Receipt state was updated concurrently.")
                }
            }
        }

        val updatedReceipt = mutationExecutionReceiptDao.getReceiptById(executionId) ?: receipt
        val targetCircuit = circuitDao.getCircuitByIdAndProject(receipt.projectId, receipt.targetEntityId.toIntOrNull() ?: 0)
            ?: return DurableMutationOutcome.EntityNotFound("CIRCUIT", receipt.targetEntityId, "Target circuit not found during retry.")

        val record = InternalDurableCommitRecord(
            receipt = updatedReceipt,
            patchedCircuit = targetCircuit,
            fieldChanges = appliedChanges,
            candidateAffectedDomains = setOf("CABLE_SIZING", "VOLTAGE_DROP", "SHORT_CIRCUIT", "PROTECTION_ADS", "COORDINATION")
        )
        return runRevalidationLifecycle(record)
    }

    private suspend fun runRevalidationLifecycle(record: InternalDurableCommitRecord): DurableMutationOutcome {
        val executionId = record.receipt.executionId
        val now = clock()

        // 1. Transition receipt PENDING -> RUNNING if not already transitioned
        if (record.receipt.revalidationStatus == RevalidationStatus.PENDING) {
            val p2rRows = mutationExecutionReceiptDao.transitionPendingToRunning(executionId, now)
            if (p2rRows != 1) {
                val current = mutationExecutionReceiptDao.getReceiptById(executionId)
                if (current?.revalidationStatus == RevalidationStatus.SUPERSEDED) {
                    return DurableMutationOutcome.Superseded(
                        receipt = current,
                        appliedChanges = record.fieldChanges,
                        revalidationOutcome = RevalidationOutcome.RevisionAdvancedOrSuperseded(
                            projectId = record.receipt.projectId,
                            expectedRevision = record.receipt.revisionAfter,
                            currentRevision = record.receipt.revisionAfter + 1L
                        )
                    )
                }
            }
        }

        // Build MutationImpact
        val impact = MutationImpact(
            projectId = record.receipt.projectId,
            entityType = record.receipt.targetEntityType,
            entityId = record.receipt.targetEntityId,
            mutatedFields = record.fieldChanges.map { it.fieldName }.toSet(),
            baseRevision = record.receipt.revisionBefore,
            resultingRevision = record.receipt.revisionAfter,
            candidateAffectedDomains = record.candidateAffectedDomains,
            committedAt = record.receipt.committedAt,
            status = MutationSuccessStatus.COMMITTED_REVALIDATION_REQUIRED,
            requiresRevalidation = true
        )

        val allCircuits = circuitDao.getCircuitsListForProject(record.receipt.projectId)
        val allTransformers = transformerDao.getTransformersListForProject(record.receipt.projectId)
        val liveRevision = projectDao.getRevision(record.receipt.projectId) ?: record.receipt.revisionAfter

        val revalOutcome = revalidationOrchestrator.revalidate(
            impact = impact,
            allCircuits = allCircuits,
            allTransformers = allTransformers,
            currentProjectRevision = liveRevision
        )

        // Map 7D outcome to guarded receipt transition
        return when (revalOutcome) {
            is RevalidationOutcome.RevalidatedSuccessfully -> {
                mutationExecutionReceiptDao.transitionRunningToSucceeded(executionId, clock())
                val updatedReceipt = mutationExecutionReceiptDao.getReceiptById(executionId) ?: record.receipt
                DurableMutationOutcome.Completed(
                    receipt = updatedReceipt,
                    appliedChanges = record.fieldChanges,
                    revalidationOutcome = revalOutcome
                )
            }
            is RevalidationOutcome.CommittedWithPartialRevalidation -> {
                mutationExecutionReceiptDao.transitionRunningToPartial(
                    executionId = executionId,
                    errorCode = "REVAL_PARTIAL",
                    messageKey = "error.revalidation.partial",
                    isRecoverable = true,
                    diagnosticRef = "PARTIAL-$now"
                )
                val updatedReceipt = mutationExecutionReceiptDao.getReceiptById(executionId) ?: record.receipt
                DurableMutationOutcome.CommittedPartial(
                    receipt = updatedReceipt,
                    appliedChanges = record.fieldChanges,
                    revalidationOutcome = revalOutcome
                )
            }
            is RevalidationOutcome.CommittedButRevalidationFailed -> {
                mutationExecutionReceiptDao.transitionRunningToFailed(
                    executionId = executionId,
                    errorCode = "REVAL_FAILED",
                    messageKey = "error.revalidation.failed",
                    isRecoverable = true,
                    diagnosticRef = "FAIL-$now"
                )
                val updatedReceipt = mutationExecutionReceiptDao.getReceiptById(executionId) ?: record.receipt
                DurableMutationOutcome.CommittedRevalidationFailed(
                    receipt = updatedReceipt,
                    appliedChanges = record.fieldChanges,
                    revalidationOutcome = revalOutcome
                )
            }
            is RevalidationOutcome.CommittedButRevalidationCancelled -> {
                mutationExecutionReceiptDao.transitionRunningToCancelled(
                    executionId = executionId,
                    diagnosticRef = "CANCEL-$now"
                )
                val updatedReceipt = mutationExecutionReceiptDao.getReceiptById(executionId) ?: record.receipt
                DurableMutationOutcome.CommittedRevalidationCancelled(
                    receipt = updatedReceipt,
                    appliedChanges = record.fieldChanges,
                    revalidationOutcome = revalOutcome
                )
            }
            is RevalidationOutcome.RevisionAdvancedOrSuperseded -> {
                mutationExecutionReceiptDao.transitionToSuperseded(executionId, clock())
                val updatedReceipt = mutationExecutionReceiptDao.getReceiptById(executionId) ?: record.receipt
                DurableMutationOutcome.Superseded(
                    receipt = updatedReceipt,
                    appliedChanges = record.fieldChanges,
                    revalidationOutcome = revalOutcome
                )
            }
            is RevalidationOutcome.InternalFailure -> {
                mutationExecutionReceiptDao.transitionRunningToFailed(
                    executionId = executionId,
                    errorCode = "REVAL_INTERNAL_ERROR",
                    messageKey = "error.revalidation.internal",
                    isRecoverable = true,
                    diagnosticRef = revalOutcome.diagnosticRef
                )
                val updatedReceipt = mutationExecutionReceiptDao.getReceiptById(executionId) ?: record.receipt
                DurableMutationOutcome.CommittedRevalidationFailed(
                    receipt = updatedReceipt,
                    appliedChanges = record.fieldChanges,
                    revalidationOutcome = RevalidationOutcome.CommittedButRevalidationFailed(
                        projectId = record.receipt.projectId,
                        targetEntityId = record.receipt.targetEntityId,
                        resultingRevision = record.receipt.revisionAfter,
                        failedDomain = CalculationDomain.CABLE_SIZING,
                        reason = revalOutcome.sanitizedMessage
                    )
                )
            }
            is RevalidationOutcome.InvalidMutationImpact -> {
                mutationExecutionReceiptDao.transitionRunningToFailed(
                    executionId = executionId,
                    errorCode = "REVAL_INVALID_IMPACT",
                    messageKey = "error.revalidation.invalid_impact",
                    isRecoverable = false,
                    diagnosticRef = "INVALID_IMPACT-$now"
                )
                val updatedReceipt = mutationExecutionReceiptDao.getReceiptById(executionId) ?: record.receipt
                DurableMutationOutcome.CommittedRevalidationFailed(
                    receipt = updatedReceipt,
                    appliedChanges = record.fieldChanges,
                    revalidationOutcome = RevalidationOutcome.CommittedButRevalidationFailed(
                        projectId = record.receipt.projectId,
                        targetEntityId = record.receipt.targetEntityId,
                        resultingRevision = record.receipt.revisionAfter,
                        failedDomain = CalculationDomain.CABLE_SIZING,
                        reason = revalOutcome.reason
                    )
                )
            }
            is RevalidationOutcome.TargetMissingAfterCommit -> {
                mutationExecutionReceiptDao.transitionRunningToFailed(
                    executionId = executionId,
                    errorCode = "REVAL_TARGET_MISSING",
                    messageKey = "error.revalidation.target_missing",
                    isRecoverable = false,
                    diagnosticRef = "TARGET_MISSING-$now"
                )
                val updatedReceipt = mutationExecutionReceiptDao.getReceiptById(executionId) ?: record.receipt
                DurableMutationOutcome.CommittedRevalidationFailed(
                    receipt = updatedReceipt,
                    appliedChanges = record.fieldChanges,
                    revalidationOutcome = RevalidationOutcome.CommittedButRevalidationFailed(
                        projectId = record.receipt.projectId,
                        targetEntityId = record.receipt.targetEntityId,
                        resultingRevision = record.receipt.revisionAfter,
                        failedDomain = CalculationDomain.CABLE_SIZING,
                        reason = "Target entity ${revalOutcome.entityId} was missing post-commit."
                    )
                )
            }
        }
    }

    companion object {
        /**
         * Authoritative entity type for the only mutation domain currently approved in the trusted
         * boundary. A proposal cannot change its domain at approval time.
         */
        internal const val CIRCUIT_ENTITY_TYPE = "CIRCUIT"

        /** Tolerance for treating two persisted scalar encodings as the same value identity. */
        private const val SCALAR_IDENTITY_EPSILON = 1.0E-6

        fun createGateway(
            database: AppDatabase,
            catalogRepository: CatalogRepository,
            principalProvider: com.example.ai.approval.TrustedPrincipalProvider
        ): com.example.ai.approval.TrustedProposalApprovalGateway {
            val workflow = DurableMutationWorkflow(
                db = database,
                catalogRepository = catalogRepository
            )
            return com.example.ai.approval.TrustedProposalApprovalGateway(
                approvalGrantDao = database.approvalGrantDao(),
                durableWorkflow = workflow,
                principalProvider = principalProvider
            )
        }
    }
}

private fun CablePatchInstructions.toCableCircuitPatch(): CableCircuitPatch {
    return CableCircuitPatch(
        overrideCableSize = overrideCableSize?.let { PatchValue(it.action, it.value) },
        cableType = cableType?.let { PatchValue(it.action, it.value) },
        manufacturer = manufacturer?.let { PatchValue(it.action, it.value) },
        installation = installation?.let { PatchValue(it.action, it.value) },
        parallelRuns = parallelRuns?.let { PatchValue(it.action, it.value) },
        overrideDeviceSize = overrideDeviceSize?.let { PatchValue(it.action, it.value) },
        overrideDeviceType = overrideDeviceType?.let { PatchValue(it.action, it.value) },
        lengthM = lengthM?.let { PatchValue(it.action, it.value) }
    )
}
