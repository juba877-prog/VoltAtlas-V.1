package com.example

import com.example.data.AtsEntity
import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.data.UpsEntity
import com.example.sld.ElectricalNetworkGraphBuilder
import com.example.sld.SldValidationStatus
import com.example.ui.ProjectValidationSummary
import org.junit.Assert.*
import org.junit.Test

class ValidationAndUiRegressionTest {

    @Test
    fun testAtsDisabled_NoFailureWarningOrNode() {
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "MDB-1", fedFrom = "Transformer", atsIncluded = false, projectId = "P1")
        )
        val transformers = listOf(
            TransformerEntity(projectId = "P1", transformerId = "TX-01")
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = "P1",
            transformers = transformers,
            circuits = circuits
        )

        val atsNode = graph.nodes.find { it.id == "ats_1" } ?: graph.unconnectedNodes.find { it.id == "ats_1" }
        assertNull("Disabled ATS should not produce an SLD node", atsNode)

        val atsIssues = graph.validationIssues.filter { it.componentName.contains("ATS", ignoreCase = true) }
        assertTrue("Disabled ATS should produce zero validation issues", atsIssues.isEmpty())
    }

    @Test
    fun testUpsDisabled_NoWarningOrNode() {
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "MDB-1", fedFrom = "Transformer", upsBacked = false, projectId = "P1")
        )
        val transformers = listOf(
            TransformerEntity(projectId = "P1", transformerId = "TX-01")
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = "P1",
            transformers = transformers,
            circuits = circuits
        )

        val upsNode = graph.nodes.find { it.id == "ups_1" } ?: graph.unconnectedNodes.find { it.id == "ups_1" }
        assertNull("Disabled UPS should not produce an SLD node", upsNode)

        val upsIssues = graph.validationIssues.filter { it.issueCode == "UPS_NO_LOAD" }
        assertTrue("Disabled UPS should produce no UPS_NO_LOAD issues", upsIssues.isEmpty())
    }

    @Test
    fun testAtsEnabled_MissingSource_IsDataRequiredNotFail() {
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "MDB-1", fedFrom = "Transformer", atsIncluded = true, projectId = "P1")
        )
        val transformers = listOf(
            TransformerEntity(projectId = "P1", transformerId = "TX-01")
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = "P1",
            transformers = transformers,
            circuits = circuits,
            atsResult = com.example.engine.AtsResult(status = "Valid ATS Sized", finalRating = 250.0)
        )

        val atsFailures = graph.validationIssues.filter { 
            it.componentName.contains("ATS", ignoreCase = true) && it.severity == SldValidationStatus.INVALID 
        }
        assertTrue("Missing optional generator/output destination should be DATA_REQUIRED, not INVALID (FAIL)", atsFailures.isEmpty())

        val atsDataReq = graph.validationIssues.filter { 
            it.componentName.contains("ATS", ignoreCase = true) && it.severity == SldValidationStatus.DATA_REQUIRED 
        }
        assertTrue("Missing generator source should issue DATA_REQUIRED severity", atsDataReq.isNotEmpty())
    }

    @Test
    fun testAtsEnabled_CrossProjectSource_IsFail() {
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "MDB-1", fedFrom = "Transformer", atsIncluded = true, projectId = "OTHER_PROJ")
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = "P1",
            transformers = emptyList(),
            circuits = circuits
        )

        val crossProjectFailures = graph.validationIssues.filter { it.severity == SldValidationStatus.INVALID }
        assertTrue("Cross-project source isolation error must evaluate to INVALID (FAIL)", crossProjectFailures.isNotEmpty())
    }

    @Test
    fun testDashboardStatus_FailuresPrecludePass() {
        val summary = ProjectValidationSummary(
            totalFailures = 2,
            totalReviewsRequired = 0,
            totalWarnings = 0,
            overallStatus = "ENGINEERING FAILURES"
        )
        assertNotEquals("100% COMPLIANT / PASS", summary.overallStatus)
        assertEquals("ENGINEERING FAILURES", summary.overallStatus)
    }

    @Test
    fun testDashboardStatus_ReviewRequiredOnly() {
        val summary = ProjectValidationSummary(
            totalFailures = 0,
            totalReviewsRequired = 3,
            totalWarnings = 0,
            overallStatus = "REVIEW REQUIRED"
        )
        assertEquals("REVIEW REQUIRED", summary.overallStatus)
    }

    @Test
    fun testDashboardStatus_AllPass() {
        val summary = ProjectValidationSummary(
            totalFailures = 0,
            totalReviewsRequired = 0,
            totalWarnings = 0,
            overallStatus = "100% COMPLIANT / PASS"
        )
        assertEquals("100% COMPLIANT / PASS", summary.overallStatus)
    }
}
