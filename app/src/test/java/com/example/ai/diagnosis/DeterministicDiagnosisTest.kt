package com.example.ai.diagnosis

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.contracts.EngineeringCheck
import com.example.ai.contracts.EngineeringUnit
import com.example.ai.contracts.EngineeringValue
import com.example.ai.contracts.ToolStatus
import com.example.ai.conversation.AssistantIntent
import com.example.ai.conversation.ConversationAction
import com.example.ai.conversation.ConversationActionType
import com.example.ai.conversation.ConversationTaskState
import com.example.ai.conversation.ConversationTurnDecision
import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.grounding.GroundedResponseValidator
import com.example.ai.grounding.GroundingFailureReason
import com.example.ai.grounding.GroundingValidationResult
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.tools.cable.CableEngineeringToolsTest
import com.example.ai.tools.cable.CableSizingDetailsDto
import com.example.ai.tools.cable.CableSizingExecutionResult
import com.example.ai.tools.cable.CheckStatus
import com.example.ai.tools.cable.EngineeringCheckItemDto
import com.example.ai.tools.cable.GoverningCriterion
import com.example.ai.ui.AssistantCardUiModel
import com.example.ai.ui.AssistantResponseMapper
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Gate 8: Deterministic, evidence-backed diagnostic flow tests.
 *
 * Verifies:
 * 1. Real FAIL produces evidence-backed diagnosis with actual vs limit values in FailureDiagnosisCard.
 * 2. Multiple failed checks are preserved without arbitrary priority.
 * 3. Missing governing data produces incomplete diagnosis (SafeErrorCard, fail-closed).
 * 4. Model cannot invent diagnosis numbers or flip PASS/FAIL.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DeterministicDiagnosisTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadCatalog() {
            CableEngineeringToolsTest.loadCatalogData()
        }
    }

    private val sampleContext = BoundedProjectContext(
        projectId = "PROJ-DIAG-1",
        projectName = "Diagnostic Project",
        liveRevision = 5L,
        activeScreen = "CIRCUIT_EDITOR",
        activeEntityType = "CIRCUIT",
        activeEntityId = "1",
        activeEntityName = "Feeder-1",
        knownEntityInputs = mapOf(
            "cableSize" to "70 mm²",
            "maxVDpct" to "3.0",
            "status" to "FAIL"
        )
    )

    private fun sampleDetails(vdPct: Double = 4.2, iz: Double = 180.0, ib: Double = 220.0): CableSizingDetailsDto {
        return CableSizingDetailsDto(
            Ib_A = ib,
            deviceSize_A = 250.0,
            deviceType = "MCCB",
            cTotalDerating = 0.8,
            iRequired_perCable_A = ib,
            productCode = "CABLE-70",
            cableSize_mm2 = "70",
            ratedI_table_A = 225.0,
            capacity_derated_Iz_A = iz,
            utilizationPct = 122.2,
            neutral_mm2 = "70",
            earthCPC_mm2 = "35",
            voltDrop_V = 16.8,
            vdPct = vdPct,
            cumulativeVDpct = vdPct,
            status = "FAIL",
            failReason = "Voltage drop $vdPct% exceeds limit 3.0%"
        )
    }

    @Test
    fun realFailure_producesEvidenceBackedDiagnosisWithActualVsLimit() {
        val check = EngineeringCheckItemDto(
            checkName = "VOLTAGE_DROP",
            status = CheckStatus.FAIL,
            actualValue = EngineeringValue.ofDecimal(4.2, EngineeringUnit.PERCENT),
            limitValue = EngineeringValue.ofDecimal(3.0, EngineeringUnit.PERCENT),
            actualDisplay = "4.2%",
            limitDisplay = "3.0%",
            unit = "%",
            message = "Voltage drop 4.2% exceeds maximum allowable limit of 3.0%"
        )

        val result = CableSizingExecutionResult(
            projectId = "PROJ-DIAG-1",
            circuitId = "1",
            circuitName = "Feeder-1",
            projectRevision = 5L,
            freshnessState = "CURRENT",
            isRecalculated = false,
            overallStatus = CheckStatus.FAIL,
            governingCriterion = GoverningCriterion.VOLTAGE_DROP,
            governingReason = "Voltage drop 4.2% exceeds maximum allowable 3.0%",
            details = sampleDetails(vdPct = 4.2),
            checks = listOf(check)
        )

        val evidence = listOf(
            EngineeringEvidenceRecord(
                evidenceId = "EVD-DIAG-1",
                requestId = "REQ-DIAG-1",
                toolName = "calculate_cable_sizing",
                projectId = "PROJ-DIAG-1",
                entityType = "CIRCUIT",
                entityId = "1",
                projectRevision = 5L,
                executionStatus = ToolStatus.FAIL,
                result = result,
                checks = listOf(
                    EngineeringCheck(
                        checkId = "CHK-VD-1",
                        checkName = "VOLTAGE_DROP",
                        status = ToolStatus.FAIL,
                        actualValue = check.actualValue,
                        requiredLimit = check.limitValue,
                        message = check.message
                    )
                )
            )
        )

        val decision = ConversationTurnDecision(
            decisionId = "DEC-DIAG-1",
            action = ConversationAction(
                actionType = ConversationActionType.DIAGNOSE_FAILURE,
                reasonCode = "FAILURE_DETECTED"
            ),
            updatedState = ConversationTaskState(
                taskId = "TASK-1",
                projectId = "PROJ-DIAG-1",
                baseProjectRevision = 5L,
                activeEntityId = "1"
            )
        )

        val uiState = AssistantResponseMapper.mapDecisionToUiState(
            decision = decision,
            state = decision.updatedState,
            projectContext = sampleContext,
            evidence = evidence
        )

        assertTrue(uiState.activeCard is AssistantCardUiModel.FailureDiagnosisCard)
        val card = uiState.activeCard as AssistantCardUiModel.FailureDiagnosisCard

        assertEquals("VOLTAGE_DROP", card.violatedCriterion)
        assertEquals("4.2%", card.actualValue)
        assertEquals("3.0%", card.limitValue)
        assertTrue(card.diagnosisText.contains("4.2%"))
        assertTrue(card.remediationOptions.isNotEmpty())
        assertTrue(card.remediationOptions.any { it.contains("voltage drop", ignoreCase = true) })
    }

    @Test
    fun multipleFailedChecks_arePreservedWithoutArbitraryPriority() {
        val vdCheck = EngineeringCheckItemDto(
            checkName = "VOLTAGE_DROP",
            status = CheckStatus.FAIL,
            actualValue = EngineeringValue.ofDecimal(4.5, EngineeringUnit.PERCENT),
            limitValue = EngineeringValue.ofDecimal(3.0, EngineeringUnit.PERCENT),
            actualDisplay = "4.5%",
            limitDisplay = "3.0%",
            unit = "%",
            message = "Voltage drop 4.5% exceeds 3.0%"
        )
        val ampCheck = EngineeringCheckItemDto(
            checkName = "AMPACITY",
            status = CheckStatus.FAIL,
            actualValue = EngineeringValue.ofDecimal(220.0, EngineeringUnit.AMPERE),
            limitValue = EngineeringValue.ofDecimal(180.0, EngineeringUnit.AMPERE),
            actualDisplay = "220.0 A",
            limitDisplay = "180.0 A",
            unit = "A",
            message = "Design current 220A exceeds derated ampacity 180A"
        )

        val result = CableSizingExecutionResult(
            projectId = "PROJ-DIAG-1",
            circuitId = "1",
            circuitName = "Feeder-1",
            projectRevision = 5L,
            freshnessState = "CURRENT",
            isRecalculated = false,
            overallStatus = CheckStatus.FAIL,
            governingCriterion = GoverningCriterion.MULTIPLE_CONSTRAINTS,
            governingReason = "Multiple constraints violated: VOLTAGE_DROP and AMPACITY",
            details = sampleDetails(vdPct = 4.5, iz = 180.0, ib = 220.0),
            checks = listOf(vdCheck, ampCheck)
        )

        val evidence = listOf(
            EngineeringEvidenceRecord(
                evidenceId = "EVD-MULTI-1",
                requestId = "REQ-MULTI-1",
                toolName = "calculate_cable_sizing",
                projectId = "PROJ-DIAG-1",
                entityType = "CIRCUIT",
                entityId = "1",
                projectRevision = 5L,
                executionStatus = ToolStatus.FAIL,
                result = result
            )
        )

        val decision = ConversationTurnDecision(
            decisionId = "DEC-MULTI-1",
            action = ConversationAction(
                actionType = ConversationActionType.DIAGNOSE_FAILURE,
                reasonCode = "MULTIPLE_FAILURES"
            ),
            updatedState = ConversationTaskState(
                taskId = "TASK-1",
                projectId = "PROJ-DIAG-1",
                baseProjectRevision = 5L,
                activeEntityId = "1"
            )
        )

        val uiState = AssistantResponseMapper.mapDecisionToUiState(
            decision = decision,
            state = decision.updatedState,
            projectContext = sampleContext,
            evidence = evidence
        )

        assertTrue(uiState.activeCard is AssistantCardUiModel.FailureDiagnosisCard)
        val card = uiState.activeCard as AssistantCardUiModel.FailureDiagnosisCard

        // Verifies both failures are preserved in diagnosisText without suppression
        assertTrue(card.diagnosisText.contains("VOLTAGE_DROP"))
        assertTrue(card.diagnosisText.contains("AMPACITY"))
        assertTrue(card.diagnosisText.contains("4.5%"))
        assertTrue(card.diagnosisText.contains("220.0 A"))
    }

    @Test
    fun missingOrIndeterminateGoverningData_producesSafeErrorCardWithoutGuessing() {
        val decision = ConversationTurnDecision(
            decisionId = "DEC-INDET-1",
            action = ConversationAction(
                actionType = ConversationActionType.DIAGNOSE_FAILURE,
                reasonCode = "REQUEST_DIAGNOSIS"
            ),
            updatedState = ConversationTaskState(
                taskId = "TASK-1",
                projectId = "PROJ-DIAG-1",
                baseProjectRevision = 5L,
                activeEntityId = "1"
            )
        )

        // Empty evidence passed
        val uiState = AssistantResponseMapper.mapDecisionToUiState(
            decision = decision,
            state = decision.updatedState,
            projectContext = sampleContext,
            evidence = emptyList()
        )

        // Must fail closed with SafeErrorCard, NEVER fabricating a fake FailureDiagnosisCard
        assertTrue(uiState.activeCard is AssistantCardUiModel.SafeErrorCard)
        val errorCard = uiState.activeCard as AssistantCardUiModel.SafeErrorCard
        assertTrue(errorCard.dataUnchangedConfirmed)
        assertTrue(errorCard.userFacingMessage.contains("unavailable") || errorCard.userFacingMessage.contains("incomplete", ignoreCase = true))
    }

    @Test
    fun modelCannotInventDiagnosisNumbersOrFlipPassFail() {
        val check = EngineeringCheckItemDto(
            checkName = "VOLTAGE_DROP",
            status = CheckStatus.FAIL,
            actualValue = EngineeringValue.ofDecimal(4.2, EngineeringUnit.PERCENT),
            limitValue = EngineeringValue.ofDecimal(3.0, EngineeringUnit.PERCENT),
            actualDisplay = "4.2%",
            limitDisplay = "3.0%",
            unit = "%",
            message = "Voltage drop 4.2% exceeds limit"
        )

        val evidence = listOf(
            EngineeringEvidenceRecord(
                evidenceId = "EVD-FAIL-1",
                requestId = "REQ-FAIL-1",
                toolName = "calculate_cable_sizing",
                projectId = "PROJ-DIAG-1",
                entityType = "CIRCUIT",
                entityId = "1",
                projectRevision = 5L,
                executionStatus = ToolStatus.FAIL,
                result = CableSizingExecutionResult(
                    projectId = "PROJ-DIAG-1",
                    circuitId = "1",
                    circuitName = "Feeder-1",
                    projectRevision = 5L,
                    freshnessState = "CURRENT",
                    isRecalculated = false,
                    overallStatus = CheckStatus.FAIL,
                    governingCriterion = GoverningCriterion.VOLTAGE_DROP,
                    governingReason = "Voltage drop 4.2% exceeds 3.0%",
                    details = sampleDetails(vdPct = 4.2),
                    checks = listOf(check)
                )
            )
        )

        // 1. Model attempts to claim PASS when evidence is FAIL
        val passClaimText = "The cable sizing is compliant and passed all IEC requirements with full margin."
        val passValidation = GroundedResponseValidator.validateResponse(
            text = passClaimText,
            evidenceList = evidence,
            context = sampleContext
        )
        assertTrue(passValidation is GroundingValidationResult.Rejected)
        assertEquals(GroundingFailureReason.STATUS_MISMATCH, (passValidation as GroundingValidationResult.Rejected).reason)

        // 2. Model attempts to invent an unverified cable recommendation
        val inventedRecommendation = "I recommend using 300 mm² cable to fix this."
        val inventValidation = GroundedResponseValidator.validateResponse(
            text = inventedRecommendation,
            evidenceList = evidence,
            context = sampleContext
        )
        assertTrue(inventValidation is GroundingValidationResult.Rejected)
        assertEquals(GroundingFailureReason.NUMERIC_MISMATCH, (inventValidation as GroundingValidationResult.Rejected).reason)
    }
}
