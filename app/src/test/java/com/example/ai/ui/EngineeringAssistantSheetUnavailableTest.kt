package com.example.ai.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.ai.context.EngineeringContext
import com.example.ai.ui.components.AssistantApprovalConfirmationCard
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verification test suite for C′-1:
 * Proves that:
 * 1. GatewayStatus.Unavailable is surfaced distinctly in the UI via gateway_unavailable_banner.
 * 2. AssistantApprovalConfirmationCard gates the "Approve & Apply" action and renders an explicit reason
 *    when isApplyEnabled is false / gateway is unavailable, preventing affordance failure on click.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EngineeringAssistantSheetUnavailableTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testUnavailableBanner_displayedWhenGatewayStatusIsUnavailable() {
        val viewModel = EngineeringAssistantViewModel(
            approvalGateway = null,
            asyncApprovalGatewayProvider = null
        )
        viewModel.open(
            AssistantEntryPoint.TOP_BAR_ICON,
            EngineeringContext(projectId = "P-1", projectName = "Test Project")
        )

        composeTestRule.setContent {
            VoltAtlasTheme {
                EngineeringAssistantSheet(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("gateway_unavailable_banner").assertIsDisplayed()
    }

    @Test
    fun testApprovalConfirmationCard_whenGatewayUnavailable_disablesApplyButtonAndShowsReason() {
        val reviewModel = ProposalReviewUiModel(
            proposalId = "PROP-UI-1",
            proposalArtifactDigest = "digest-1",
            projectId = "PROJ-101",
            projectLabel = "Complex",
            targetEntityType = "CIRCUIT",
            targetEntityId = "201",
            targetEntityLabel = "Feeder 201",
            operation = "UPDATE_CABLE_ATTRIBUTES",
            baseProjectRevision = 1L,
            currentLiveRevision = 1L,
            isStale = false,
            fieldChanges = emptyList(),
            governingCriterion = "Thermal Ampacity",
            evidenceSummary = "Standard derating applied",
            expectedPostApplyBehavior = "Sizing valid"
        )

        composeTestRule.setContent {
            VoltAtlasTheme {
                AssistantApprovalConfirmationCard(
                    reviewModel = reviewModel,
                    isSubmitting = false,
                    isApplyEnabled = false,
                    gatewayUnavailableReason = "AI mutation gateway is not configured. Modifications cannot be applied.",
                    onConfirmApprove = {},
                    onCancel = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("action_approve_and_apply").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("gateway_unavailable_reason").assertIsDisplayed()
    }
}
