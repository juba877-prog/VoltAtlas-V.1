package com.example.ai.proposal

/**
 * Pure policy validator responsible for validating proposal envelopes, operation-to-field compatibility,
 * per-field SET/CLEAR rules, bounds, expiration, and canonical digest generation.
 *
 * Invariants:
 * - Pure logic: Zero database/Room queries, zero repository dependencies, zero network calls.
 * - Testable time: Accepts an explicit [currentTimeMillis] parameter for deterministic clock evaluation.
 * - Fail-closed: Returns structured [ProposalPolicyError] entries on any contract or policy violation.
 */
interface ProposalPolicyValidator {
    /**
     * Validates a cable engineering proposal against Phase 7B policies and produces a [ValidatedProposalIntent] on success.
     *
     * @param proposal The proposal envelope to validate.
     * @param currentTimeMillis Explicit current time used for expiration evaluation (defaults to proposal.createdAt).
     */
    fun validateCableProposal(
        proposal: CableEngineeringProposal,
        currentTimeMillis: Long = proposal.createdAt
    ): ProposalPolicyResult<CablePatchInstructions>
}

/**
 * Authoritative default implementation of [ProposalPolicyValidator].
 */
class DefaultProposalPolicyValidator(
    private val digestEncoder: ProposalDigestEncoder = DefaultProposalDigestEncoder()
) : ProposalPolicyValidator {

    companion object {
        private val CABLE_FIELDS = setOf(
            "overrideCableSize",
            "cableType",
            "manufacturer",
            "installation",
            "parallelRuns",
            "lengthM"
        )

        private val PROTECTIVE_DEVICE_FIELDS = setOf(
            "overrideDeviceSize",
            "overrideDeviceType"
        )

        private val FIELD_CLEAR_POLICIES = mapOf(
            "overrideCableSize" to FieldClearPolicy.SET_OR_CLEAR,
            "overrideDeviceSize" to FieldClearPolicy.SET_OR_CLEAR,
            "overrideDeviceType" to FieldClearPolicy.SET_OR_CLEAR,
            "cableType" to FieldClearPolicy.SET_ONLY,
            "manufacturer" to FieldClearPolicy.SET_ONLY,
            "installation" to FieldClearPolicy.SET_ONLY,
            "parallelRuns" to FieldClearPolicy.SET_ONLY,
            "lengthM" to FieldClearPolicy.SET_ONLY
        )
    }

    override fun validateCableProposal(
        proposal: CableEngineeringProposal,
        currentTimeMillis: Long
    ): ProposalPolicyResult<CablePatchInstructions> {
        val errors = mutableListOf<ProposalPolicyError>()

        // 1. Envelope & Identity Checks
        if (proposal.proposalId.isBlank()) {
            errors.add(
                ProposalPolicyError(
                    errorCode = ProposalPolicyErrorCode.INVALID_PROPOSAL,
                    field = "proposalId",
                    message = "Proposal ID cannot be blank."
                )
            )
        }

        if (proposal.schemaVersion != PROPOSAL_SCHEMA_VERSION_V1) {
            errors.add(
                ProposalPolicyError(
                    errorCode = ProposalPolicyErrorCode.INVALID_SCHEMA_VERSION,
                    field = "schemaVersion",
                    message = "Unsupported schema version '${proposal.schemaVersion}'. Expected '$PROPOSAL_SCHEMA_VERSION_V1'."
                )
            )
        }

        if (proposal.projectId.isBlank()) {
            errors.add(
                ProposalPolicyError(
                    errorCode = ProposalPolicyErrorCode.INVALID_PROPOSAL,
                    field = "projectId",
                    message = "Project ID cannot be blank."
                )
            )
        }

        if (proposal.baseProjectRevision < 1L) {
            errors.add(
                ProposalPolicyError(
                    errorCode = ProposalPolicyErrorCode.INVALID_BASE_REVISION,
                    field = "baseProjectRevision",
                    message = "Base project revision must be greater than or equal to 1."
                )
            )
        }

        if (proposal.targetEntityType.isBlank()) {
            errors.add(
                ProposalPolicyError(
                    errorCode = ProposalPolicyErrorCode.INVALID_PROPOSAL,
                    field = "targetEntityType",
                    message = "Target entity type cannot be blank."
                )
            )
        }

        if (proposal.targetEntityId.isBlank()) {
            errors.add(
                ProposalPolicyError(
                    errorCode = ProposalPolicyErrorCode.INVALID_PROPOSAL,
                    field = "targetEntityId",
                    message = "Target entity ID cannot be blank."
                )
            )
        }

        if (proposal.sourceToolName.isBlank()) {
            errors.add(
                ProposalPolicyError(
                    errorCode = ProposalPolicyErrorCode.INVALID_PROPOSAL,
                    field = "sourceToolName",
                    message = "Source tool name cannot be blank."
                )
            )
        }

        if (proposal.sourceRequestId.isBlank()) {
            errors.add(
                ProposalPolicyError(
                    errorCode = ProposalPolicyErrorCode.INVALID_PROPOSAL,
                    field = "sourceRequestId",
                    message = "Source request ID cannot be blank."
                )
            )
        }

        if (proposal.createdAt <= 0L) {
            errors.add(
                ProposalPolicyError(
                    errorCode = ProposalPolicyErrorCode.INVALID_FIELD_VALUE,
                    field = "createdAt",
                    message = "Creation timestamp must be greater than zero."
                )
            )
        }

        if (proposal.expiresAt != null) {
            if (proposal.expiresAt <= proposal.createdAt) {
                errors.add(
                    ProposalPolicyError(
                        errorCode = ProposalPolicyErrorCode.INVALID_FIELD_VALUE,
                        field = "expiresAt",
                        message = "Expiry timestamp must be strictly greater than creation timestamp."
                    )
                )
            } else if (currentTimeMillis > proposal.expiresAt) {
                errors.add(
                    ProposalPolicyError(
                        errorCode = ProposalPolicyErrorCode.PROPOSAL_EXPIRED,
                        field = "expiresAt",
                        message = "Proposal has expired (expiresAt: ${proposal.expiresAt}, currentTime: $currentTimeMillis)."
                    )
                )
            }
        }

        // 2. Patch Non-Empty Check
        val patch = proposal.patch
        if (!patch.hasProposedChanges()) {
            errors.add(
                ProposalPolicyError(
                    errorCode = ProposalPolicyErrorCode.EMPTY_PATCH,
                    field = "patch",
                    message = "Proposal patch contains no proposed field changes."
                )
            )
            return ProposalPolicyResult.Failure(errors)
        }

        // 3. Operation-to-Field Compatibility Checks
        val proposedFieldNames = mutableSetOf<String>()
        if (patch.overrideCableSize != null) proposedFieldNames.add("overrideCableSize")
        if (patch.cableType != null) proposedFieldNames.add("cableType")
        if (patch.manufacturer != null) proposedFieldNames.add("manufacturer")
        if (patch.installation != null) proposedFieldNames.add("installation")
        if (patch.parallelRuns != null) proposedFieldNames.add("parallelRuns")
        if (patch.overrideDeviceSize != null) proposedFieldNames.add("overrideDeviceSize")
        if (patch.overrideDeviceType != null) proposedFieldNames.add("overrideDeviceType")
        if (patch.lengthM != null) proposedFieldNames.add("lengthM")

        val hasCableFields = proposedFieldNames.any { it in CABLE_FIELDS }
        val hasDeviceFields = proposedFieldNames.any { it in PROTECTIVE_DEVICE_FIELDS }

        if (hasCableFields && hasDeviceFields) {
            errors.add(
                ProposalPolicyError(
                    errorCode = ProposalPolicyErrorCode.MIXED_OPERATION_FIELDS,
                    field = "patch",
                    message = "Proposal patch cannot mix cable attributes and protective device attributes in a single operation."
                )
            )
        }

        when (proposal.operation) {
            ProposalOperation.UPDATE_CABLE_ATTRIBUTES -> {
                for (field in proposedFieldNames) {
                    if (field !in CABLE_FIELDS) {
                        errors.add(
                            ProposalPolicyError(
                                errorCode = ProposalPolicyErrorCode.FIELD_NOT_ALLOWED_FOR_OPERATION,
                                field = field,
                                message = "Field '$field' is not permitted for operation '${proposal.operation}'."
                            )
                        )
                    }
                }
            }
            ProposalOperation.UPDATE_PROTECTIVE_DEVICE -> {
                for (field in proposedFieldNames) {
                    if (field !in PROTECTIVE_DEVICE_FIELDS) {
                        errors.add(
                            ProposalPolicyError(
                                errorCode = ProposalPolicyErrorCode.FIELD_NOT_ALLOWED_FOR_OPERATION,
                                field = field,
                                message = "Field '$field' is not permitted for operation '${proposal.operation}'."
                            )
                        )
                    }
                }
            }
        }

        // 4. Per-Field SET / CLEAR Policy and Value Bounds Checks
        validateField(patch.overrideCableSize, "overrideCableSize", errors) { v ->
            if (v.isBlank()) "Cable size override cannot be blank." else null
        }

        validateField(patch.cableType, "cableType", errors) { v ->
            if (v.isBlank()) "Cable type cannot be blank." else null
        }

        validateField(patch.manufacturer, "manufacturer", errors) { v ->
            if (v.isBlank()) "Manufacturer cannot be blank." else null
        }

        validateField(patch.installation, "installation", errors) { v ->
            if (v.isBlank()) "Installation method cannot be blank." else null
        }

        validateField(patch.parallelRuns, "parallelRuns", errors) { v ->
            if (v <= 0) "Parallel runs must be greater than or equal to 1, got: $v." else null
        }

        validateField(patch.overrideDeviceSize, "overrideDeviceSize", errors) { v ->
            if (v <= 0.0) "Device rating override must be greater than zero, got: $v." else null
        }

        validateField(patch.overrideDeviceType, "overrideDeviceType", errors) { v ->
            if (v.isBlank()) "Device type cannot be blank." else null
        }

        validateField(patch.lengthM, "lengthM", errors) { v ->
            if (v <= 0.0) "Cable length must be greater than zero, got: $v." else null
        }

        if (errors.isNotEmpty()) {
            return ProposalPolicyResult.Failure(errors)
        }

        // 5. Canonical Digest Computation
        val digest = try {
            digestEncoder.computeDigest(proposal)
        } catch (t: Throwable) {
            errors.add(
                ProposalPolicyError(
                    errorCode = ProposalPolicyErrorCode.DIGEST_GENERATION_FAILED,
                    field = "proposalDigest",
                    message = "Failed to compute canonical proposal digest: ${t.message}"
                )
            )
            return ProposalPolicyResult.Failure(errors)
        }

        // 6. Build Normalized ValidatedProposalIntent
        val instructions = CablePatchInstructions(
            overrideCableSize = patch.overrideCableSize?.let { FieldInstruction("overrideCableSize", it.action, it.value) },
            cableType = patch.cableType?.let { FieldInstruction("cableType", it.action, it.value) },
            manufacturer = patch.manufacturer?.let { FieldInstruction("manufacturer", it.action, it.value) },
            installation = patch.installation?.let { FieldInstruction("installation", it.action, it.value) },
            parallelRuns = patch.parallelRuns?.let { FieldInstruction("parallelRuns", it.action, it.value) },
            overrideDeviceSize = patch.overrideDeviceSize?.let { FieldInstruction("overrideDeviceSize", it.action, it.value) },
            overrideDeviceType = patch.overrideDeviceType?.let { FieldInstruction("overrideDeviceType", it.action, it.value) },
            lengthM = patch.lengthM?.let { FieldInstruction("lengthM", it.action, it.value) }
        )

        val intent = ValidatedProposalIntent(
            validatedIntentId = "INTENT-${proposal.proposalId}-${proposal.baseProjectRevision}",
            proposalId = proposal.proposalId,
            proposalDigest = digest,
            schemaVersion = proposal.schemaVersion,
            projectId = proposal.projectId,
            baseProjectRevision = proposal.baseProjectRevision,
            targetEntityType = proposal.targetEntityType,
            targetEntityId = proposal.targetEntityId,
            operation = proposal.operation,
            instructions = instructions,
            sourceToolName = proposal.sourceToolName,
            sourceRequestId = proposal.sourceRequestId,
            validatedAt = currentTimeMillis,
            policyVersion = PROPOSAL_POLICY_VERSION_V1
        )

        return ProposalPolicyResult.Success(intent)
    }

    private fun <T> validateField(
        patchValue: PatchValue<T>?,
        fieldName: String,
        errors: MutableList<ProposalPolicyError>,
        valueBoundsCheck: (T) -> String?
    ) {
        if (patchValue == null) return // Absent field produces no error

        val policy = FIELD_CLEAR_POLICIES[fieldName] ?: FieldClearPolicy.SET_ONLY

        when (patchValue.action) {
            PatchAction.CLEAR -> {
                if (policy == FieldClearPolicy.SET_ONLY) {
                    errors.add(
                        ProposalPolicyError(
                            errorCode = ProposalPolicyErrorCode.CLEAR_NOT_ALLOWED,
                            field = fieldName,
                            message = "Field '$fieldName' is SET_ONLY and does not permit CLEAR actions."
                        )
                    )
                }
                if (patchValue.value != null) {
                    errors.add(
                        ProposalPolicyError(
                            errorCode = ProposalPolicyErrorCode.CLEAR_REQUIRES_NULL,
                            field = fieldName,
                            message = "Field '$fieldName' with CLEAR action must specify null value, got: ${patchValue.value}."
                        )
                    )
                }
            }
            PatchAction.SET -> {
                if (patchValue.value == null) {
                    errors.add(
                        ProposalPolicyError(
                            errorCode = ProposalPolicyErrorCode.SET_REQUIRES_VALUE,
                            field = fieldName,
                            message = "Field '$fieldName' with SET action must specify a non-null value."
                        )
                    )
                } else {
                    val boundsViolation = valueBoundsCheck(patchValue.value)
                    if (boundsViolation != null) {
                        errors.add(
                            ProposalPolicyError(
                                errorCode = ProposalPolicyErrorCode.INVALID_FIELD_VALUE,
                                field = fieldName,
                                message = boundsViolation
                            )
                        )
                    }
                }
            }
        }
    }
}
