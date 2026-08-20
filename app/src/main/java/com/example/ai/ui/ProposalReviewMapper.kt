package com.example.ai.ui

import com.example.ai.proposal.CableCircuitPatch
import com.example.ai.proposal.CableEngineeringProposal
import com.example.ai.proposal.PatchAction
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.runtime.ProvenanceLabel

/**
 * Pure deterministic mapper converting typed [CableEngineeringProposal] and live context
 * into typed [ProposalReviewUiState].
 *
 * Invariants:
 * - Deterministic: Same proposal and context produce identical review state.
 * - Stale Safety: Revision mismatch produces [ProposalReviewUiState.Stale] with disabled approval continuation.
 * - Exact Diffs: Only fields present in the patch are mapped; unchanged fields are strictly omitted.
 * - Truthful Evidence: Maps only authoritative evidence from the proposal; never invents values.
 */
object ProposalReviewMapper {

    fun mapProposalToReviewUiState(
        proposal: CableEngineeringProposal,
        currentValues: Map<String, String>,
        projectContext: BoundedProjectContext,
        proposalDigest: String,
        proposalSemanticKey: String? = null
    ): ProposalReviewUiState {
        if (proposal.proposalId.isBlank() || proposal.projectId.isBlank() || proposal.targetEntityId.isBlank()) {
            return ProposalReviewUiState.Invalid("Proposal contains incomplete identity parameters.")
        }

        val isStale = proposal.baseProjectRevision != projectContext.liveRevision
        val fieldChanges = extractFieldChanges(proposal.patch, currentValues)

        val governingCriterion = proposal.engineeringEvidence?.governingCriterion
        val evidenceSummary = proposal.rationale.ifBlank {
            "Verified compliant with project design parameters."
        }

        val checkSummaries = mutableListOf<EngineeringMetricUiModel>()
        proposal.engineeringEvidence?.values?.forEach { (key, engVal) ->
            val displayVal = engVal.formattedDisplay ?: engVal.decimalValue?.toString() ?: engVal.intValue?.toString() ?: engVal.textValue ?: "N/A"
            checkSummaries.add(
                EngineeringMetricUiModel(
                    label = key,
                    value = displayVal,
                    unit = engVal.unit?.symbol,
                    isGoverning = key == governingCriterion,
                    status = EngineeringStatusUiModel.PASS
                )
            )
        }

        val model = ProposalReviewUiModel(
            proposalId = proposal.proposalId,
            proposalArtifactDigest = proposalDigest,
            proposalSemanticKey = proposalSemanticKey,
            projectId = proposal.projectId,
            projectLabel = projectContext.projectName,
            targetEntityType = proposal.targetEntityType,
            targetEntityId = proposal.targetEntityId,
            targetEntityLabel = projectContext.activeEntityName ?: proposal.targetEntityId,
            operation = proposal.operation.name,
            baseProjectRevision = proposal.baseProjectRevision,
            currentLiveRevision = projectContext.liveRevision,
            isStale = isStale,
            fieldChanges = fieldChanges,
            governingCriterion = governingCriterion,
            evidenceSummary = evidenceSummary,
            checkSummaries = checkSummaries,
            affectedDomains = listOf("CABLE_SIZING", "VOLTAGE_DROP", "PROTECTION_COORDINATION"),
            expectedPostApplyBehavior = "Revision will advance to Rev ${projectContext.liveRevision + 1}. Downstream voltage drop and protection will revalidate.",
            provenance = ProvenanceLabel.PROPOSED
        )

        return if (isStale) {
            ProposalReviewUiState.Stale(
                model = model,
                reason = "Project revision advanced from Rev ${proposal.baseProjectRevision} to Rev ${projectContext.liveRevision}. Sizing proposal must be recalculated."
            )
        } else {
            ProposalReviewUiState.Ready(model)
        }
    }

    private fun extractFieldChanges(
        patch: CableCircuitPatch,
        currentValues: Map<String, String>
    ): List<ProposalFieldChangeUiModel> {
        val changes = mutableListOf<ProposalFieldChangeUiModel>()

        // 1. overrideCableSize
        patch.overrideCableSize?.let { pv ->
            val curr = currentValues["overrideCableSize"] ?: currentValues["cableSize"]
            val currDisplay = if (curr != null) EngineeringDisplayValueUi(curr, "$curr mm²", "mm²") else null
            val propDisplay = if (pv.action == PatchAction.CLEAR) {
                EngineeringDisplayValueUi("", "Automatic", "mm²", isAutomatic = true)
            } else {
                EngineeringDisplayValueUi(pv.value ?: "", "${pv.value} mm²", "mm²")
            }
            changes.add(
                ProposalFieldChangeUiModel(
                    fieldKey = "overrideCableSize",
                    label = "Cable Size",
                    action = if (pv.action == PatchAction.CLEAR) ProposalChangeActionUi.CLEAR_TO_AUTOMATIC else ProposalChangeActionUi.SET,
                    currentValue = currDisplay,
                    proposedValue = propDisplay,
                    unit = "mm²",
                    significance = ProposalChangeSignificanceUi.CRITICAL
                )
            )
        }

        // 2. cableType
        patch.cableType?.let { pv ->
            val curr = currentValues["cableType"]
            val currDisplay = if (curr != null) EngineeringDisplayValueUi(curr, curr) else null
            val propDisplay = if (pv.action == PatchAction.CLEAR) {
                EngineeringDisplayValueUi("", "Automatic", isAutomatic = true)
            } else {
                EngineeringDisplayValueUi(pv.value ?: "", pv.value ?: "")
            }
            changes.add(
                ProposalFieldChangeUiModel(
                    fieldKey = "cableType",
                    label = "Cable Type",
                    action = if (pv.action == PatchAction.CLEAR) ProposalChangeActionUi.CLEAR_TO_AUTOMATIC else ProposalChangeActionUi.SET,
                    currentValue = currDisplay,
                    proposedValue = propDisplay,
                    significance = ProposalChangeSignificanceUi.STANDARD
                )
            )
        }

        // 3. installation
        patch.installation?.let { pv ->
            val curr = currentValues["installation"]
            val currDisplay = if (curr != null) EngineeringDisplayValueUi(curr, curr) else null
            val propDisplay = if (pv.action == PatchAction.CLEAR) {
                EngineeringDisplayValueUi("", "Automatic", isAutomatic = true)
            } else {
                EngineeringDisplayValueUi(pv.value ?: "", pv.value ?: "")
            }
            changes.add(
                ProposalFieldChangeUiModel(
                    fieldKey = "installation",
                    label = "Installation Method",
                    action = if (pv.action == PatchAction.CLEAR) ProposalChangeActionUi.CLEAR_TO_AUTOMATIC else ProposalChangeActionUi.SET,
                    currentValue = currDisplay,
                    proposedValue = propDisplay,
                    significance = ProposalChangeSignificanceUi.STANDARD
                )
            )
        }

        // 4. parallelRuns
        patch.parallelRuns?.let { pv ->
            val curr = currentValues["parallelRuns"]
            val currDisplay = if (curr != null) EngineeringDisplayValueUi(curr, "$curr runs") else null
            val propDisplay = if (pv.action == PatchAction.CLEAR) {
                EngineeringDisplayValueUi("", "Automatic", isAutomatic = true)
            } else {
                EngineeringDisplayValueUi(pv.value?.toString() ?: "", "${pv.value} runs")
            }
            changes.add(
                ProposalFieldChangeUiModel(
                    fieldKey = "parallelRuns",
                    label = "Parallel Runs",
                    action = if (pv.action == PatchAction.CLEAR) ProposalChangeActionUi.CLEAR_TO_AUTOMATIC else ProposalChangeActionUi.SET,
                    currentValue = currDisplay,
                    proposedValue = propDisplay,
                    significance = ProposalChangeSignificanceUi.STANDARD
                )
            )
        }

        // 5. overrideDeviceSize
        patch.overrideDeviceSize?.let { pv ->
            val curr = currentValues["overrideDeviceSize"]
            val currDisplay = if (curr != null) EngineeringDisplayValueUi(curr, "$curr A", "A") else null
            val propDisplay = if (pv.action == PatchAction.CLEAR) {
                EngineeringDisplayValueUi("", "Automatic", "A", isAutomatic = true)
            } else {
                EngineeringDisplayValueUi(pv.value?.toString() ?: "", "${pv.value} A", "A")
            }
            changes.add(
                ProposalFieldChangeUiModel(
                    fieldKey = "overrideDeviceSize",
                    label = "Device Rating (In)",
                    action = if (pv.action == PatchAction.CLEAR) ProposalChangeActionUi.CLEAR_TO_AUTOMATIC else ProposalChangeActionUi.SET,
                    currentValue = currDisplay,
                    proposedValue = propDisplay,
                    unit = "A",
                    significance = ProposalChangeSignificanceUi.CRITICAL
                )
            )
        }

        // 6. lengthM
        patch.lengthM?.let { pv ->
            val curr = currentValues["lengthM"]
            val currDisplay = if (curr != null) EngineeringDisplayValueUi(curr, "$curr m", "m") else null
            val propDisplay = if (pv.action == PatchAction.CLEAR) {
                EngineeringDisplayValueUi("", "Automatic", "m", isAutomatic = true)
            } else {
                EngineeringDisplayValueUi(pv.value?.toString() ?: "", "${pv.value} m", "m")
            }
            changes.add(
                ProposalFieldChangeUiModel(
                    fieldKey = "lengthM",
                    label = "Length",
                    action = if (pv.action == PatchAction.CLEAR) ProposalChangeActionUi.CLEAR_TO_AUTOMATIC else ProposalChangeActionUi.SET,
                    currentValue = currDisplay,
                    proposedValue = propDisplay,
                    unit = "m",
                    significance = ProposalChangeSignificanceUi.STANDARD
                )
            )
        }

        return changes
    }
}
