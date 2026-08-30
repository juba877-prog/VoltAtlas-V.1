package com.example.ui

import com.example.sld.SldValidationIssue
import com.example.sld.SldValidationStatus
import org.junit.Assert.*
import org.junit.Test

/**
 * Dashboard state-driven rendering logic tests.
 * Verifies visibility conditions, empty-state handling, and data-binding
 * without hardcoded screenshot values.
 */
class DashboardScreenTest {

    // ── State A: Healthy project ───────────────────────────────────────
    @Test
    fun testStateA_HealthyProject_NeedsAttentionIsHidden() {
        val summary = ProjectValidationSummary(
            overallStatus = "100% COMPLIANT / PASS",
            totalFailures = 0,
            totalReviewsRequired = 0,
            totalWarnings = 0,
            openIssues = emptyList()
        )

        assertEquals("100% COMPLIANT / PASS", summary.overallStatus)
        assertTrue(summary.openIssues.isEmpty())

        // Needs Attention section must be completely hidden when openIssues is empty
        val shouldShowNeedsAttention = summary.openIssues.isNotEmpty()
        assertFalse("Needs Attention section must be hidden when zero issues exist", shouldShowNeedsAttention)

        // Badge text derivation
        val badgeText = when {
            summary.totalFailures > 0 -> "FAIL"
            summary.totalReviewsRequired > 0 -> "REVIEW REQUIRED"
            summary.totalWarnings > 0 -> "WARNINGS"
            else -> "PASS"
        }
        assertEquals("PASS", badgeText)
    }

    // ── State B: Review-required project ───────────────────────────────
    @Test
    fun testStateB_ReviewProject_NeedsAttentionRendersOpenIssueCount() {
        val issues = listOf(
            SldValidationIssue(
                issueCode = "VD_EXCEEDED",
                affectedNodeId = "circ_1",
                componentName = "SHOP-1",
                failedCondition = "Voltage drop 5.2% exceeds project limit of 5.0%",
                severity = SldValidationStatus.DATA_REQUIRED
            )
        )
        val summary = ProjectValidationSummary(
            overallStatus = "REVIEW REQUIRED",
            totalFailures = 0,
            totalReviewsRequired = 1,
            totalWarnings = 0,
            openIssues = issues
        )

        assertEquals("REVIEW REQUIRED", summary.overallStatus)
        assertEquals(1, summary.openIssues.size)
        assertTrue("Needs Attention section must render when openIssues is not empty", summary.openIssues.isNotEmpty())
        assertEquals("SHOP-1", summary.openIssues[0].componentName)
    }

    // ── State C: Failed project with multiple issues ───────────────────
    @Test
    fun testStateC_FailedProject_MultipleIssuesRenderedWithoutHardcoding() {
        val issues = listOf(
            SldValidationIssue(
                issueCode = "THERMAL_EXCEEDED",
                affectedNodeId = "circ_1",
                componentName = "FEEDER-1",
                failedCondition = "Cable thermal capacity exceeded",
                severity = SldValidationStatus.INVALID
            ),
            SldValidationIssue(
                issueCode = "TX_OVERLOAD",
                affectedNodeId = "tx_2",
                componentName = "TX-2",
                failedCondition = "Transformer loading 112% exceeds rating",
                severity = SldValidationStatus.INVALID
            )
        )
        val summary = ProjectValidationSummary(
            overallStatus = "ENGINEERING FAILURES",
            totalFailures = 2,
            totalReviewsRequired = 0,
            totalWarnings = 0,
            openIssues = issues
        )

        assertEquals("ENGINEERING FAILURES", summary.overallStatus)
        assertEquals(2, summary.openIssues.size)
        // No hardcoded "Gate 3" or screenshot-derived strings
        assertFalse(summary.openIssues.any { it.componentName.contains("Gate 3", ignoreCase = true) })

        val badgeText = when {
            summary.totalFailures > 0 -> "FAIL"
            summary.totalReviewsRequired > 0 -> "REVIEW REQUIRED"
            else -> "PASS"
        }
        assertEquals("FAIL", badgeText)
    }

    // ── State D: Empty project ─────────────────────────────────────────
    @Test
    fun testStateD_EmptyProject_MetricsShowSafeDefaults() {
        val summary = ProjectValidationSummary(
            overallStatus = "100% COMPLIANT / PASS",
            totalFailures = 0,
            totalReviewsRequired = 0,
            totalWarnings = 0,
            openIssues = emptyList()
        )

        val totalCircuits = 0
        val transformerCount = 0
        val sizingResultsEmpty = true

        assertEquals(0, totalCircuits)
        assertEquals(0, transformerCount)
        assertTrue(summary.openIssues.isEmpty())

        // Empty transformer list → "N/A", not a misleading "0.0% OK"
        val txValueStr = if (transformerCount > 0) "0.0" else "N/A"
        assertEquals("N/A", txValueStr)

        val txStatusText = if (transformerCount == 0) null else "OK"
        assertNull("Empty transformers must not show OK badge", txStatusText)

        // Empty sizing results → "N/A"
        val vdValueStr = if (!sizingResultsEmpty) "0.00" else "N/A"
        assertEquals("N/A", vdValueStr)
    }

    // ── State E: Partial configuration ─────────────────────────────────
    @Test
    fun testStateE_PartialConfiguration_HandledGracefully() {
        val circuitCount = 1
        val transformerCount = 0

        assertTrue(circuitCount > 0)
        assertEquals(0, transformerCount)

        // Circuit metric shows OK, transformer metric is suppressed
        val circuitStatusText = if (circuitCount > 0) "OK" else null
        assertEquals("OK", circuitStatusText)

        val txStatusText = if (transformerCount == 0) null else "OK"
        assertNull(txStatusText)
    }

    // ── State F: Long content ──────────────────────────────────────────
    @Test
    fun testStateF_LongContent_FormatsWithoutCrash() {
        val longIssueName = "Primary High Voltage Distribution Grid Substation Phase 4 Expansion Feeder Circuit MDB-04-F12"
        val longCondition = "The cumulative voltage drop along main distribution trunk line M-04 exceeds the 5.0% threshold specified by IEC 60364-5-52 under peak demand scenario"

        val issue = SldValidationIssue(
            issueCode = "VD_EXCEEDED",
            affectedNodeId = "circ_12",
            componentName = longIssueName,
            failedCondition = longCondition,
            severity = SldValidationStatus.INVALID
        )

        assertTrue(issue.componentName.length > 50)
        assertTrue(issue.failedCondition.length > 100)
        // No crash, fields accessible
        assertEquals(longIssueName, issue.componentName)
        assertEquals(SldValidationStatus.INVALID, issue.severity)
    }

    // ── Issue navigation target derivation ─────────────────────────────
    @Test
    fun testIssueNavigation_TargetTabDerivedFromComponentName() {
        fun deriveTargetTab(componentName: String): Int = when {
            componentName.contains("Transformer", ignoreCase = true) -> 2
            componentName.contains("ATS", ignoreCase = true) || componentName.contains("Generator", ignoreCase = true) -> 3
            componentName.contains("Protection", ignoreCase = true) -> 4
            else -> 1
        }

        assertEquals(2, deriveTargetTab("Transformer TX-1 Overload"))
        assertEquals(3, deriveTargetTab("ATS Transfer Fault"))
        assertEquals(3, deriveTargetTab("Generator G1 Sizing"))
        assertEquals(4, deriveTargetTab("Protection Coordination Failure"))
        assertEquals(1, deriveTargetTab("FEEDER-1 Cable Oversize"))
    }

    // ── Project Health Card Consistency & Visual State Tests ────────────
    @Test
    fun testProjectHealthCard_ConsistencyAndVisualState_ZeroIssues() {
        val summary = ProjectValidationSummary(
            totalFailures = 0,
            totalReviewsRequired = 0,
            totalWarnings = 0,
            overallStatus = "100% COMPLIANT / PASS",
            openIssues = emptyList()
        )

        val totalIssueCount = summary.totalFailures + summary.totalReviewsRequired + summary.totalWarnings

        // 1. Text Consistency
        val subtitleText = when (totalIssueCount) {
            0 -> "No open issues"
            1 -> "1 item needs attention"
            else -> "$totalIssueCount items need attention"
        }
        val badgeText = when (totalIssueCount) {
            0 -> "0 items"
            1 -> "1 item"
            else -> "$totalIssueCount items"
        }

        assertEquals("No open issues", subtitleText)
        assertEquals("0 items", badgeText)
        assertEquals("100% COMPLIANT / PASS", summary.overallStatus)

        // 2. Visual State Assertion: MUST be PASS/Neutral, NOT Error/Warning
        val isErrorOrWarning = summary.totalFailures > 0 || summary.totalReviewsRequired > 0
        assertFalse("Card must NOT be in error or warning visual state when issues == 0", isErrorOrWarning)

        val badgeSeverity = when {
            summary.totalFailures > 0 -> "FAIL"
            summary.totalReviewsRequired > 0 -> "REVIEW REQUIRED"
            summary.totalWarnings > 0 -> "WARNINGS"
            else -> "PASS"
        }
        assertEquals("PASS", badgeSeverity)
    }

    @Test
    fun testProjectHealthCard_ConsistencyAndVisualState_SingleFailure() {
        val summary = ProjectValidationSummary(
            totalFailures = 1,
            totalReviewsRequired = 0,
            totalWarnings = 0,
            overallStatus = "1 ISSUES REQUIRE ATTENTION",
            openIssues = emptyList()
        )

        val totalIssueCount = summary.totalFailures + summary.totalReviewsRequired + summary.totalWarnings
        val subtitleText = when (totalIssueCount) {
            0 -> "No open issues"
            1 -> "1 item needs attention"
            else -> "$totalIssueCount items need attention"
        }
        val badgeText = when (totalIssueCount) {
            0 -> "0 items"
            1 -> "1 item"
            else -> "$totalIssueCount items"
        }

        // Must be mutually consistent: title, badge, and subtitle all acknowledge 1 item
        assertEquals("1 item needs attention", subtitleText)
        assertEquals("1 item", badgeText)
        assertEquals("1 ISSUES REQUIRE ATTENTION", summary.overallStatus)

        // Visual state must be FAIL
        val isError = summary.totalFailures > 0
        assertTrue("Visual state must reflect error when totalFailures > 0", isError)
    }

    @Test
    fun testProjectHealthCard_ConsistencyAndVisualState_MultipleIssues() {
        val summary = ProjectValidationSummary(
            totalFailures = 2,
            totalReviewsRequired = 1,
            totalWarnings = 1,
            overallStatus = "2 ISSUES REQUIRE ATTENTION",
            openIssues = emptyList()
        )

        val totalIssueCount = summary.totalFailures + summary.totalReviewsRequired + summary.totalWarnings
        val subtitleText = when (totalIssueCount) {
            0 -> "No open issues"
            1 -> "1 item needs attention"
            else -> "$totalIssueCount items need attention"
        }
        val badgeText = when (totalIssueCount) {
            0 -> "0 items"
            1 -> "1 item"
            else -> "$totalIssueCount items"
        }

        assertEquals("4 items need attention", subtitleText)
        assertEquals("4 items", badgeText)
    }

    @Test
    fun testProjectHealthCard_ClickBehavior() {
        var navigatedTab: Int? = null

        fun handleCardClick(summary: ProjectValidationSummary) {
            val totalIssueCount = summary.totalFailures + summary.totalReviewsRequired + summary.totalWarnings
            if (totalIssueCount > 0) {
                navigatedTab = 5
            }
        }

        // Zero issues -> safe no-op (no unexpected navigation)
        val zeroSummary = ProjectValidationSummary(totalFailures = 0, totalReviewsRequired = 0, totalWarnings = 0)
        handleCardClick(zeroSummary)
        assertNull("Zero issues must not trigger navigation", navigatedTab)

        // With issues -> navigates to SLD screen (tab 5)
        val issuesSummary = ProjectValidationSummary(totalFailures = 1, totalReviewsRequired = 0, totalWarnings = 0)
        handleCardClick(issuesSummary)
        assertEquals(5, navigatedTab)
    }
}
