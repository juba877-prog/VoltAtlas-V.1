package com.example.ai.proposal

/**
 * Pure contract validator for [EngineeringProposal] envelopes and patches.
 *
 * Guarantees:
 * - Pure logic: Zero database queries, zero Room/catalog/engine dependencies.
 * - Fail-closed: Missing required fields, invalid bounds, or empty patches return structured validation errors.
 */
interface ProposalValidator {
    /**
     * Validates a proposal envelope and its typed patch.
     */
    fun validate(proposal: EngineeringProposal<*>): ProposalValidationResult
}

/**
 * Authoritative default implementation of [ProposalValidator].
 */
class DefaultProposalValidator : ProposalValidator {

    override fun validate(proposal: EngineeringProposal<*>): ProposalValidationResult {
        val errors = mutableListOf<ProposalValidationError>()

        if (proposal.proposalId.isBlank()) {
            errors.add(
                ProposalValidationError(
                    errorCode = ProposalErrorCode.MISSING_REQUIRED_FIELD,
                    field = "proposalId",
                    message = "Proposal ID cannot be blank."
                )
            )
        }

        if (proposal.schemaVersion != PROPOSAL_SCHEMA_VERSION_V1) {
            errors.add(
                ProposalValidationError(
                    errorCode = ProposalErrorCode.INVALID_PROPOSAL,
                    field = "schemaVersion",
                    message = "Unsupported proposal schema version '${proposal.schemaVersion}'. Expected '$PROPOSAL_SCHEMA_VERSION_V1'."
                )
            )
        }

        if (proposal.projectId.isBlank()) {
            errors.add(
                ProposalValidationError(
                    errorCode = ProposalErrorCode.MISSING_REQUIRED_FIELD,
                    field = "projectId",
                    message = "Project ID cannot be blank."
                )
            )
        }

        if (proposal.baseProjectRevision < 1L) {
            errors.add(
                ProposalValidationError(
                    errorCode = ProposalErrorCode.INVALID_FIELD_VALUE,
                    field = "baseProjectRevision",
                    message = "Base project revision must be greater than or equal to 1."
                )
            )
        }

        if (proposal.targetEntityType.isBlank()) {
            errors.add(
                ProposalValidationError(
                    errorCode = ProposalErrorCode.MISSING_REQUIRED_FIELD,
                    field = "targetEntityType",
                    message = "Target entity type cannot be blank."
                )
            )
        }

        if (proposal.targetEntityId.isBlank()) {
            errors.add(
                ProposalValidationError(
                    errorCode = ProposalErrorCode.MISSING_REQUIRED_FIELD,
                    field = "targetEntityId",
                    message = "Target entity ID cannot be blank."
                )
            )
        }

        if (proposal.sourceToolName.isBlank()) {
            errors.add(
                ProposalValidationError(
                    errorCode = ProposalErrorCode.MISSING_REQUIRED_FIELD,
                    field = "sourceToolName",
                    message = "Source tool name cannot be blank."
                )
            )
        }

        if (proposal.sourceRequestId.isBlank()) {
            errors.add(
                ProposalValidationError(
                    errorCode = ProposalErrorCode.MISSING_REQUIRED_FIELD,
                    field = "sourceRequestId",
                    message = "Source request ID cannot be blank."
                )
            )
        }

        if (proposal.createdAt <= 0L) {
            errors.add(
                ProposalValidationError(
                    errorCode = ProposalErrorCode.INVALID_FIELD_VALUE,
                    field = "createdAt",
                    message = "Creation timestamp must be greater than zero."
                )
            )
        }

        if (proposal.expiresAt != null && proposal.expiresAt <= proposal.createdAt) {
            errors.add(
                ProposalValidationError(
                    errorCode = ProposalErrorCode.INVALID_FIELD_VALUE,
                    field = "expiresAt",
                    message = "Expiry timestamp must be strictly greater than creation timestamp."
                )
            )
        }

        when (val patch = proposal.patch) {
            is CableCircuitPatch -> validateCableCircuitPatch(patch, errors)
            else -> {
                errors.add(
                    ProposalValidationError(
                        errorCode = ProposalErrorCode.INVALID_PATCH,
                        field = "patch",
                        message = "Unsupported patch type '${patch?.javaClass?.name}'."
                    )
                )
            }
        }

        return if (errors.isEmpty()) {
            ProposalValidationResult.valid()
        } else {
            ProposalValidationResult.invalid(errors)
        }
    }

    private fun validateCableCircuitPatch(
        patch: CableCircuitPatch,
        errors: MutableList<ProposalValidationError>
    ) {
        if (!patch.hasProposedChanges()) {
            errors.add(
                ProposalValidationError(
                    errorCode = ProposalErrorCode.INVALID_PATCH,
                    field = "patch",
                    message = "Proposal patch must contain at least one proposed field change."
                )
            )
            return
        }

        patch.parallelRuns?.let { pv ->
            if (pv.action == PatchAction.SET && (pv.value == null || pv.value <= 0)) {
                errors.add(
                    ProposalValidationError(
                        errorCode = ProposalErrorCode.INVALID_FIELD_VALUE,
                        field = "parallelRuns",
                        message = "Parallel runs must be greater than or equal to 1."
                    )
                )
            }
        }

        patch.lengthM?.let { pv ->
            if (pv.action == PatchAction.SET && (pv.value == null || pv.value <= 0.0)) {
                errors.add(
                    ProposalValidationError(
                        errorCode = ProposalErrorCode.INVALID_FIELD_VALUE,
                        field = "lengthM",
                        message = "Cable length must be greater than zero."
                    )
                )
            }
        }

        patch.overrideDeviceSize?.let { pv ->
            if (pv.action == PatchAction.SET && (pv.value == null || pv.value <= 0.0)) {
                errors.add(
                    ProposalValidationError(
                        errorCode = ProposalErrorCode.INVALID_FIELD_VALUE,
                        field = "overrideDeviceSize",
                        message = "Protective device rating must be greater than zero."
                    )
                )
            }
        }

        patch.overrideCableSize?.let { pv ->
            if (pv.action == PatchAction.SET && pv.value.isNullOrBlank()) {
                errors.add(
                    ProposalValidationError(
                        errorCode = ProposalErrorCode.INVALID_FIELD_VALUE,
                        field = "overrideCableSize",
                        message = "Cable size override cannot be blank when action is SET."
                    )
                )
            }
        }

        patch.cableType?.let { pv ->
            if (pv.action == PatchAction.SET && pv.value.isNullOrBlank()) {
                errors.add(
                    ProposalValidationError(
                        errorCode = ProposalErrorCode.INVALID_FIELD_VALUE,
                        field = "cableType",
                        message = "Cable type cannot be blank when action is SET."
                    )
                )
            }
        }

        patch.manufacturer?.let { pv ->
            if (pv.action == PatchAction.SET && pv.value.isNullOrBlank()) {
                errors.add(
                    ProposalValidationError(
                        errorCode = ProposalErrorCode.INVALID_FIELD_VALUE,
                        field = "manufacturer",
                        message = "Manufacturer cannot be blank when action is SET."
                    )
                )
            }
        }

        patch.installation?.let { pv ->
            if (pv.action == PatchAction.SET && pv.value.isNullOrBlank()) {
                errors.add(
                    ProposalValidationError(
                        errorCode = ProposalErrorCode.INVALID_FIELD_VALUE,
                        field = "installation",
                        message = "Installation method cannot be blank when action is SET."
                    )
                )
            }
        }

        patch.overrideDeviceType?.let { pv ->
            if (pv.action == PatchAction.SET && pv.value.isNullOrBlank()) {
                errors.add(
                    ProposalValidationError(
                        errorCode = ProposalErrorCode.INVALID_FIELD_VALUE,
                        field = "overrideDeviceType",
                        message = "Protective device type cannot be blank when action is SET."
                    )
                )
            }
        }
    }
}
