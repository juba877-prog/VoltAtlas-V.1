package com.example.ai.ui

import com.example.ai.contracts.EngineeringValue
import com.example.ai.proposal.*
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.runtime.ProvenanceLabel
import org.junit.Assert.*
import org.junit.Test

class ProposalReviewMapperTest {

    private fun createSampleProposal(
        baseRevision: Long = 3L,
        patch: CableCircuitPatch = CableCircuitPatch(
            overrideCableSize = PatchValue.set("300.0"),
            cableType = PatchValue.set("Cu/XLPE/SWA/PVC"),
            installation = PatchValue.clear()
        )
    ): CableEngineeringProposal {
        return CableEngineeringProposal(
            proposalId = "PROP-8001",
            projectId = "PROJ-101",
            baseProjectRevision = baseRevision,
            targetEntityType = "CIRCUIT",
            targetEntityId = "201",
            operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
            patch = patch,
            rationale = "300 mm² required to satisfy derated ampacity (Iz=345A > Ib=310A).",
            sourceToolName = "ProposeCableSizingTool",
            sourceRequestId = "REQ-101",
            engineeringEvidence = ProposalEvidenceDto(
                evidenceType = "CABLE_SIZING_CALCULATION",
                governingCriterion = "Ampacity Iz",
                values = mapOf(
                    "Ampacity Iz" to com.example.ai.contracts.EngineeringValue.ofDecimal(345.0, com.example.ai.contracts.EngineeringUnit.AMPERE),
                    "Voltage Drop" to com.example.ai.contracts.EngineeringValue.ofDecimal(1.85, com.example.ai.contracts.EngineeringUnit.PERCENT)
                )
            )
        )
    }

    private fun createProjectContext(liveRevision: Long = 3L): BoundedProjectContext {
        return BoundedProjectContext(
            projectId = "PROJ-101",
            projectName = "Commercial Complex",
            liveRevision = liveRevision,
            activeScreen = "CircuitsGridScreen",
            activeEntityType = "CIRCUIT",
            activeEntityId = "201",
            activeEntityName = "Feeder 201"
        )
    }

    // 1. Valid proposal maps exact typed before/after changes
    @Test
    fun validProposal_mapsExactTypedBeforeAfterChanges() {
        val proposal = createSampleProposal()
        val currentValues = mapOf(
            "overrideCableSize" to "240.0",
            "cableType" to "Cu/PVC/PVC",
            "installation" to "In Conduit"
        )
        val ctx = createProjectContext(liveRevision = 3L)

        val state = ProposalReviewMapper.mapProposalToReviewUiState(
            proposal = proposal,
            currentValues = currentValues,
            projectContext = ctx,
            proposalDigest = "digest-8001",
            proposalSemanticKey = "key-8001"
        )

        assertTrue(state is ProposalReviewUiState.Ready)
        val model = (state as ProposalReviewUiState.Ready).model

        assertEquals("PROP-8001", model.proposalId)
        assertEquals("digest-8001", model.proposalArtifactDigest)
        assertEquals("key-8001", model.proposalSemanticKey)
        assertEquals(3L, model.baseProjectRevision)
        assertEquals(3L, model.currentLiveRevision)
        assertFalse(model.isStale)
        assertEquals(ProvenanceLabel.PROPOSED, model.provenance)

        assertEquals(3, model.fieldChanges.size)

        val sizeChange = model.fieldChanges.first { it.fieldKey == "overrideCableSize" }
        assertEquals(ProposalChangeActionUi.SET, sizeChange.action)
        assertEquals("240.0 mm²", sizeChange.currentValue?.formattedText)
        assertEquals("300.0 mm²", sizeChange.proposedValue?.formattedText)

        val installChange = model.fieldChanges.first { it.fieldKey == "installation" }
        assertEquals(ProposalChangeActionUi.CLEAR_TO_AUTOMATIC, installChange.action)
        assertEquals("In Conduit", installChange.currentValue?.formattedText)
        assertEquals("Automatic", installChange.proposedValue?.formattedText)
        assertTrue(installChange.proposedValue?.isAutomatic == true)
    }

    // 2. Clear action displays "Automatic", not blank
    @Test
    fun clearAction_displaysAutomatic_notBlank() {
        val patch = CableCircuitPatch(overrideCableSize = PatchValue.clear())
        val proposal = createSampleProposal(patch = patch)
        val ctx = createProjectContext()

        val state = ProposalReviewMapper.mapProposalToReviewUiState(
            proposal = proposal,
            currentValues = mapOf("overrideCableSize" to "185.0"),
            projectContext = ctx,
            proposalDigest = "digest-clear"
        )

        assertTrue(state is ProposalReviewUiState.Ready)
        val model = (state as ProposalReviewUiState.Ready).model
        val change = model.fieldChanges.first()
        assertEquals("Automatic", change.proposedValue?.formattedText)
        assertNotEquals("", change.proposedValue?.formattedText)
    }

    // 3. Unchanged fields are not rendered
    @Test
    fun unchangedFields_areNotRendered() {
        val patch = CableCircuitPatch(overrideCableSize = PatchValue.set("300.0"))
        val proposal = createSampleProposal(patch = patch)
        val ctx = createProjectContext()

        val state = ProposalReviewMapper.mapProposalToReviewUiState(
            proposal = proposal,
            currentValues = emptyMap(),
            projectContext = ctx,
            proposalDigest = "digest-single"
        )

        assertTrue(state is ProposalReviewUiState.Ready)
        val model = (state as ProposalReviewUiState.Ready).model
        assertEquals(1, model.fieldChanges.size)
        assertEquals("overrideCableSize", model.fieldChanges.first().fieldKey)
    }

    // 4. Engineering evidence maps only authoritative values
    @Test
    fun engineeringEvidence_mapsOnlyAuthoritativeValues() {
        val proposal = createSampleProposal()
        val ctx = createProjectContext()

        val state = ProposalReviewMapper.mapProposalToReviewUiState(
            proposal = proposal,
            currentValues = emptyMap(),
            projectContext = ctx,
            proposalDigest = "digest-ev"
        )

        assertTrue(state is ProposalReviewUiState.Ready)
        val model = (state as ProposalReviewUiState.Ready).model
        assertEquals("Ampacity Iz", model.governingCriterion)
        assertEquals(2, model.checkSummaries.size)
        val izCheck = model.checkSummaries.first { it.label == "Ampacity Iz" }
        assertTrue(izCheck.isGoverning)
        assertTrue(izCheck.value.contains("345"))
        assertEquals("A", izCheck.unit)
    }

    // 5. Revision mismatch maps stale proposal
    @Test
    fun revisionMismatch_mapsStaleProposal() {
        val proposal = createSampleProposal(baseRevision = 3L)
        val ctx = createProjectContext(liveRevision = 4L) // Advanced live revision

        val state = ProposalReviewMapper.mapProposalToReviewUiState(
            proposal = proposal,
            currentValues = emptyMap(),
            projectContext = ctx,
            proposalDigest = "digest-stale"
        )

        assertTrue(state is ProposalReviewUiState.Stale)
        val staleState = state as ProposalReviewUiState.Stale
        assertTrue(staleState.model.isStale)
        assertEquals(3L, staleState.model.baseProjectRevision)
        assertEquals(4L, staleState.model.currentLiveRevision)
        assertTrue(staleState.reason.contains("Rev 3 to Rev 4"))
    }

    // 6. Missing required identity maps safe invalid state
    @Test
    fun missingRequiredIdentity_mapsSafeInvalidState() {
        val invalidProposal = CableEngineeringProposal(
            proposalId = "", // Missing ID
            projectId = "PROJ-1",
            baseProjectRevision = 1L,
            targetEntityId = "201",
            operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
            patch = CableCircuitPatch(),
            rationale = "test",
            sourceToolName = "tool",
            sourceRequestId = "req"
        )
        val ctx = createProjectContext()

        val state = ProposalReviewMapper.mapProposalToReviewUiState(
            proposal = invalidProposal,
            currentValues = emptyMap(),
            projectContext = ctx,
            proposalDigest = "digest-inv"
        )

        assertTrue(state is ProposalReviewUiState.Invalid)
        val inv = state as ProposalReviewUiState.Invalid
        assertTrue(inv.safeMessage.contains("incomplete identity"))
    }
}
