package com.example.ai.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ai.context.EngineeringContext
import com.example.ai.proposal.*
import com.example.ai.runtime.ProvenanceLabel
import com.example.ai.ui.components.*
import com.example.ui.theme.VoltAtlasTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Gate 12 Roborazzi Screenshot / Golden Test Suite.
 *
 * Verifies visual integrity across form factors:
 * - Phone (360dp narrow portrait)
 * - Tablet (800dp wide expanded)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AssistantScreenGoldenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val outputDir = File("build/outputs/roborazzi")

    @Before
    fun setUp() {
        outputDir.mkdirs()
    }

    // -------------------------------------------------------------------------
    // Phone Form Factor (360dp)
    // -------------------------------------------------------------------------

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun assistant_idleState_360dp() {
        composeTestRule.setContent {
            VoltAtlasTheme {
                AssistantIdleLanding(
                    context = EngineeringContext(
                        projectId = "PROJ-GOLDEN",
                        projectName = "Commercial Complex",
                        activeEntityName = "Main Busway Feeder",
                        activeEntityType = "circuit"
                    ),
                    onPromptClick = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "Assistant_Idle_360dp.png").path)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun assistant_verifiedResult_360dp() {
        val card = AssistantCardUiModel.ResultSummaryCard(
            cardId = "card-golden-result",
            title = "Cable Sizing Verification",
            status = EngineeringStatusUiModel.PASS,
            governingCriterion = "Voltage Drop",
            summaryText = "Conductor 185 mm² meets all thermal, grouping, and voltage constraints under IEC 60364-5-52.",
            metrics = listOf(
                EngineeringMetricUiModel(
                    label = "Voltage Drop",
                    value = "2.14",
                    unit = "%",
                    limit = "3.0%",
                    isGoverning = true,
                    status = EngineeringStatusUiModel.PASS
                ),
                EngineeringMetricUiModel(
                    label = "Derated Ampacity Iz",
                    value = "345",
                    unit = "A",
                    limit = "280 A",
                    status = EngineeringStatusUiModel.PASS
                )
            ),
            provenance = ProvenanceLabel.CALCULATED_BY_VOLTATLAS
        )

        composeTestRule.setContent {
            VoltAtlasTheme {
                AssistantResultCard(card = card, onActionClick = {})
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "Assistant_Result_360dp.png").path)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun assistant_proposalReview_360dp() {
        val model = ProposalReviewUiModel(
            proposalId = "PROP-GOLDEN",
            proposalArtifactDigest = "sha256-abcdef",
            projectId = "PROJ-1",
            projectLabel = "Commercial Complex",
            targetEntityType = "CIRCUIT",
            targetEntityId = "CIRC-101",
            targetEntityLabel = "Main Incomer Feeder",
            operation = "UPDATE_CABLE_SIZE",
            baseProjectRevision = 4L,
            currentLiveRevision = 4L,
            isStale = false,
            fieldChanges = listOf(
                ProposalFieldChangeUiModel(
                    fieldKey = "cableSize",
                    label = "Conductor Size",
                    action = ProposalChangeActionUi.SET,
                    currentValue = EngineeringDisplayValueUi("150", "150 mm²", "mm²"),
                    proposedValue = EngineeringDisplayValueUi("185", "185 mm²", "mm²"),
                    unit = "mm²",
                    significance = ProposalChangeSignificanceUi.CRITICAL
                )
            ),
            governingCriterion = "Voltage Drop",
            evidenceSummary = "Voltage drop reduced from 3.4% to 2.14% (<= 3.0% threshold).",
            affectedDomains = listOf("CABLE_SIZING"),
            expectedPostApplyBehavior = "Revision will advance to Rev 5.",
            provenance = ProvenanceLabel.PROPOSED
        )

        composeTestRule.setContent {
            VoltAtlasTheme {
                Column {
                    AssistantProposalReviewCard(model = model, onActionClick = {})
                    AssistantApprovalConfirmationCard(
                        reviewModel = model,
                        isSubmitting = false,
                        onConfirmApprove = {},
                        onCancel = {}
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "Assistant_Proposal_360dp.png").path)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun assistant_failureDiagnosis_360dp() {
        val card = AssistantCardUiModel.FailureDiagnosisCard(
            cardId = "card-golden-diag",
            title = "Thermal Overload Failure",
            violatedCriterion = "Conductor Ampacity Iz",
            actualValue = "215 A",
            limitValue = "240 A required",
            diagnosisText = "Derated ampacity (215 A) is below circuit design current Ib (240 A).",
            remediationOptions = listOf(
                "Increase cross-section to 95 mm²",
                "Reduce grouping derating factor by rerouting tray"
            )
        )

        composeTestRule.setContent {
            VoltAtlasTheme {
                AssistantFailureDiagnosisCard(card = card, onActionClick = {})
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "Assistant_FailureDiagnosis_360dp.png").path)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun assistant_mutationReceipt_360dp() {
        composeTestRule.setContent {
            VoltAtlasTheme {
                AssistantMutationReceiptCard(
                    receiptId = "RCPT-GOLDEN-01",
                    revisionBefore = 4L,
                    revisionAfter = 5L,
                    appliedChanges = listOf(
                        ProposalFieldChangeUiModel(
                            fieldKey = "cableSize",
                            label = "Conductor Size",
                            action = ProposalChangeActionUi.SET,
                            currentValue = EngineeringDisplayValueUi("150", "150 mm²", "mm²"),
                            proposedValue = EngineeringDisplayValueUi("185", "185 mm²", "mm²"),
                            unit = "mm²"
                        )
                    ),
                    revalidationStatus = "PASS",
                    onDismiss = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "Assistant_MutationReceipt_360dp.png").path)
    }

    // -------------------------------------------------------------------------
    // Tablet Form Factor (800dp)
    // -------------------------------------------------------------------------

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun assistant_idleState_800dp() {
        composeTestRule.setContent {
            VoltAtlasTheme {
                AssistantIdleLanding(
                    context = EngineeringContext(
                        projectId = "PROJ-TABLET",
                        projectName = "Hospital Center",
                        activeEntityName = "ICU Feeder A",
                        activeEntityType = "circuit"
                    ),
                    onPromptClick = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "Assistant_Idle_800dp.png").path)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun assistant_verifiedResult_800dp() {
        val card = AssistantCardUiModel.ResultSummaryCard(
            cardId = "card-tablet-res",
            title = "Cable Sizing Verification (Tablet)",
            status = EngineeringStatusUiModel.PASS,
            governingCriterion = "Voltage Drop",
            summaryText = "Conductor 240 mm² meets all hospital life-safety thermal and voltage limits.",
            metrics = listOf(
                EngineeringMetricUiModel(
                    label = "Voltage Drop",
                    value = "1.82",
                    unit = "%",
                    limit = "2.5%",
                    isGoverning = true,
                    status = EngineeringStatusUiModel.PASS
                ),
                EngineeringMetricUiModel(
                    label = "Derated Ampacity Iz",
                    value = "410",
                    unit = "A",
                    limit = "350 A",
                    status = EngineeringStatusUiModel.PASS
                )
            ),
            provenance = ProvenanceLabel.CALCULATED_BY_VOLTATLAS
        )

        composeTestRule.setContent {
            VoltAtlasTheme {
                AssistantResultCard(card = card, onActionClick = {})
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "Assistant_Result_800dp.png").path)
    }
}
