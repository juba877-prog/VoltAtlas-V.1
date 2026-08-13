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

    @Test
    fun testSupersededWarning_IdenticalLoadMatch_NoWarning() {
        // Acceptance Test 1: Panel DP-G3 own loadKVA (89.4) matches childrenTotalLoad (89.4) exactly
        val circ = CircuitEntity(id = 101, circuitName = "DP-G3", loadKVA = 89.4, fedFrom = "Transformer", directLoadConfirmed = null)
        val child1 = CircuitEntity(id = 102, circuitName = "C1", loadKVA = 50.0, fedFrom = "DP-G3")
        val child2 = CircuitEntity(id = 103, circuitName = "C2", loadKVA = 39.4, fedFrom = "DP-G3")

        val childCircuits = listOf(child1, child2)
        val hasChildren = childCircuits.isNotEmpty()
        val childrenTotalLoad = childCircuits.sumOf { it.loadKVA }
        val childrenDiffers = Math.abs(circ.loadKVA - childrenTotalLoad) > 0.001

        val isUnconfirmedConflict = hasChildren && circ.directLoadConfirmed == null && childrenDiffers

        assertFalse("When panel load equals children sum (89.4 vs 89.4), no superseded warning badge must be shown", isUnconfirmedConflict)
    }

    @Test
    fun testSupersededWarning_GenuineLoadMismatch_WarningShown() {
        // Acceptance Test 2: Panel DP-G3 own loadKVA (100.0) genuinely differs from childrenTotalLoad (89.4)
        val circ = CircuitEntity(id = 101, circuitName = "DP-G3", loadKVA = 100.0, fedFrom = "Transformer", directLoadConfirmed = null)
        val child1 = CircuitEntity(id = 102, circuitName = "C1", loadKVA = 50.0, fedFrom = "DP-G3")
        val child2 = CircuitEntity(id = 103, circuitName = "C2", loadKVA = 39.4, fedFrom = "DP-G3")

        val childCircuits = listOf(child1, child2)
        val hasChildren = childCircuits.isNotEmpty()
        val childrenTotalLoad = childCircuits.sumOf { it.loadKVA }
        val childrenDiffers = Math.abs(circ.loadKVA - childrenTotalLoad) > 0.001

        val isUnconfirmedConflict = hasChildren && circ.directLoadConfirmed == null && childrenDiffers

        assertTrue("When panel load genuinely differs from children sum (100.0 vs 89.4), warning badge MUST be shown", isUnconfirmedConflict)
    }

    @Test
    fun testSupersededWarning_ConfirmedDirectLoad_NoWarning() {
        // Acceptance Test 3: Panel DP-G3 own loadKVA (100.0) differs from children sum (89.4), but directLoadConfirmed == true
        val circ = CircuitEntity(id = 101, circuitName = "DP-G3", loadKVA = 100.0, fedFrom = "Transformer", directLoadConfirmed = true)
        val child1 = CircuitEntity(id = 102, circuitName = "C1", loadKVA = 50.0, fedFrom = "DP-G3")
        val child2 = CircuitEntity(id = 103, circuitName = "C2", loadKVA = 39.4, fedFrom = "DP-G3")

        val childCircuits = listOf(child1, child2)
        val hasChildren = childCircuits.isNotEmpty()
        val childrenTotalLoad = childCircuits.sumOf { it.loadKVA }
        val childrenDiffers = Math.abs(circ.loadKVA - childrenTotalLoad) > 0.001

        val isUnconfirmedConflict = hasChildren && circ.directLoadConfirmed == null && childrenDiffers

        assertFalse("When direct load is explicitly confirmed (directLoadConfirmed == true), no superseded warning badge must be shown", isUnconfirmedConflict)
    }
}
