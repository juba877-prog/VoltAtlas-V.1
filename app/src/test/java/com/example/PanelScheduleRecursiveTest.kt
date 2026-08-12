package com.example

import com.example.data.CircuitEntity
import com.example.data.ProjectEntity
import com.example.engine.PanelScheduleEngine
import com.example.engine.PanelScheduleRecursiveResult
import org.junit.Assert.*
import org.junit.Test

class PanelScheduleRecursiveTest {

    @Test
    fun testLeafPanelLoadAggregation() {
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "C1", loadKVA = 5.0, fedFrom = "DB-1", phases = "3P+N"),
            CircuitEntity(id = 2, circuitName = "C2", loadKVA = 3.0, fedFrom = "DB-1", phases = "1P+N", assignedPhase = "L1")
        )

        val result = PanelScheduleEngine.generateScheduleRecursive("DB-1", circuits)

        assertFalse(result.hasCircularReference)
        assertEquals(8.0, result.totalPanelLoadKVA, 0.01)
        assertEquals(8.0, result.aggregatedChildrenLoadKVA, 0.01)
        assertEquals(2, result.rows.size)
    }

    @Test
    fun testNestedSubPanelLoadAggregation() {
        // Hierarchy: Transformer -> MDB-1 -> SMDB-1 -> DB-1 -> C1 (10 kVA)
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "MDB-1", loadKVA = 0.0, fedFrom = "Transformer", panelType = "MDB"),
            CircuitEntity(id = 2, circuitName = "SMDB-1", loadKVA = 0.0, fedFrom = "MDB-1", panelType = "SMDB"),
            CircuitEntity(id = 3, circuitName = "DB-1", loadKVA = 0.0, fedFrom = "SMDB-1", panelType = "DB"),
            CircuitEntity(id = 4, circuitName = "C1", loadKVA = 10.0, fedFrom = "DB-1", phases = "3P+N")
        )

        val db1Result = PanelScheduleEngine.generateScheduleRecursive("DB-1", circuits)
        assertEquals(10.0, db1Result.totalPanelLoadKVA, 0.01)

        val smdb1Result = PanelScheduleEngine.generateScheduleRecursive("SMDB-1", circuits)
        assertEquals(10.0, smdb1Result.totalPanelLoadKVA, 0.01)

        val mdb1Result = PanelScheduleEngine.generateScheduleRecursive("MDB-1", circuits)
        assertEquals(10.0, mdb1Result.totalPanelLoadKVA, 0.01)
    }

    @Test
    fun testCircularReferenceDetection() {
        // Circular: DB-A -> DB-B -> DB-A
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "DB-A", loadKVA = 0.0, fedFrom = "DB-B", panelType = "DB"),
            CircuitEntity(id = 2, circuitName = "DB-B", loadKVA = 0.0, fedFrom = "DB-A", panelType = "DB")
        )

        val result = PanelScheduleEngine.generateScheduleRecursive("DB-A", circuits)

        assertTrue(result.hasCircularReference)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("Circular reference detected"))
    }

    @Test
    fun testConfirmedDirectLoadAddition() {
        // MDB-1 has 15 kVA recorded load, feeds C1 (10 kVA), and directLoadConfirmed = true
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "MDB-1", loadKVA = 15.0, fedFrom = "Transformer", panelType = "MDB", directLoadConfirmed = true),
            CircuitEntity(id = 2, circuitName = "C1", loadKVA = 10.0, fedFrom = "MDB-1", phases = "3P+N")
        )

        val result = PanelScheduleEngine.generateScheduleRecursive("MDB-1", circuits)

        assertFalse(result.hasCircularReference)
        assertEquals(25.0, result.totalPanelLoadKVA, 0.01) // 10 child + 15 direct = 25 kVA
        assertEquals(10.0, result.aggregatedChildrenLoadKVA, 0.01)
        assertEquals(15.0, result.panelOwnLoadKVA, 0.01)
        assertFalse(result.hasLoadMismatch)
        assertFalse(result.hasUnconfirmedDirectLoadDialog)
    }

    @Test
    fun testUnconfirmedDirectLoadTriggersDialogFlag() {
        // MDB-1 has 15 kVA recorded load, feeds C1 (10 kVA), directLoadConfirmed = null
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "MDB-1", loadKVA = 15.0, fedFrom = "Transformer", panelType = "MDB", directLoadConfirmed = null),
            CircuitEntity(id = 2, circuitName = "C1", loadKVA = 10.0, fedFrom = "MDB-1", phases = "3P+N")
        )

        val result = PanelScheduleEngine.generateScheduleRecursive("MDB-1", circuits)

        assertTrue(result.hasUnconfirmedDirectLoadDialog)
        assertTrue(result.hasLoadMismatch)
        assertEquals(10.0, result.aggregatedChildrenLoadKVA, 0.01)
        assertEquals(15.0, result.panelOwnLoadKVA, 0.01)
    }

    @Test
    fun testConfirmedEstimateIgnoresPanelOwnLoad() {
        // MDB-1 has 15 kVA recorded load, feeds C1 (10 kVA), directLoadConfirmed = false
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "MDB-1", loadKVA = 15.0, fedFrom = "Transformer", panelType = "MDB", directLoadConfirmed = false),
            CircuitEntity(id = 2, circuitName = "C1", loadKVA = 10.0, fedFrom = "MDB-1", phases = "3P+N")
        )

        val result = PanelScheduleEngine.generateScheduleRecursive("MDB-1", circuits)

        assertFalse(result.hasUnconfirmedDirectLoadDialog)
        assertEquals(10.0, result.totalPanelLoadKVA, 0.01) // Purely children load
        assertTrue(result.hasLoadMismatch) // Signals that panel circuit load is 15 but aggregated is 10
    }

    @Test
    fun testNewPanelWithZeroOwnLoadAndChildrenTriggersLoadMismatch() {
        // MDB-1 created with 0.0 kVA load, then children SMDB-1 (10 kVA) and SMDB-2 (15 kVA) added
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "MDB-1", loadKVA = 0.0, fedFrom = "Transformer", panelType = "MDB"),
            CircuitEntity(id = 2, circuitName = "SMDB-1", loadKVA = 10.0, fedFrom = "MDB-1", panelType = "SMDB"),
            CircuitEntity(id = 3, circuitName = "SMDB-2", loadKVA = 15.0, fedFrom = "MDB-1", panelType = "SMDB")
        )

        val result = PanelScheduleEngine.generateScheduleRecursive("MDB-1", circuits)

        assertEquals(25.0, result.aggregatedChildrenLoadKVA, 0.01)
        assertEquals(0.0, result.panelOwnLoadKVA, 0.01)
        assertTrue(result.hasLoadMismatch) // Signals mismatch so UI can prompt sync to 25 kVA
        assertFalse(result.hasUnconfirmedDirectLoadDialog) // 0.0 is not an unconfirmed non-zero direct load
    }

    @Test
    fun testPanelCandidateIdentificationForFreshSubPanels() {
        // Scenario: MDB-1 has children SMDB-1 and SMDB-2. SMDB-1 has no children yet.
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "MDB-1", loadKVA = 0.0, fedFrom = "Transformer", panelType = "MDB"),
            CircuitEntity(id = 2, circuitName = "SMDB-1", loadKVA = 10.0, fedFrom = "MDB-1", panelType = "SMDB"),
            CircuitEntity(id = 3, circuitName = "SMDB-2", loadKVA = 15.0, fedFrom = "MDB-1", panelType = "SMDB")
        )

        val candidatePanels = circuits.filter { c ->
            val pType = c.panelType?.trim()?.uppercase() ?: ""
            pType in setOf("MDB", "SMDB", "DB", "MCC", "PANEL", "OTHER")
        }.map { it.circuitName.trim() }

        assertTrue(candidatePanels.contains("MDB-1"))
        assertTrue(candidatePanels.contains("SMDB-1"))
        assertTrue(candidatePanels.contains("SMDB-2"))
    }

    @Test
    fun testFinalLoadExcludedFromPanelCandidatesAndSubPanelFlag() {
        // SMDB-1 feeds AHU-1 (final load) and DB-1 (sub-panel)
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "SMDB-1", loadKVA = 0.0, fedFrom = "Transformer", panelType = "SMDB"),
            CircuitEntity(id = 2, circuitName = "AHU-1", loadKVA = 12.0, fedFrom = "SMDB-1", panelType = "LOAD"),
            CircuitEntity(id = 3, circuitName = "DB-1", loadKVA = 5.0, fedFrom = "SMDB-1", panelType = "DB")
        )

        // 1. Candidate panels check: Only SMDB-1 and DB-1 should be candidate panels, AHU-1 must be excluded
        val candidatePanels = circuits.filter { c ->
            val pType = c.panelType?.trim()?.uppercase() ?: ""
            pType in setOf("MDB", "SMDB", "DB", "MCC", "PANEL", "OTHER")
        }.map { it.circuitName.trim() }

        assertTrue(candidatePanels.contains("SMDB-1"))
        assertTrue(candidatePanels.contains("DB-1"))
        assertFalse(candidatePanels.contains("AHU-1"))

        // 2. Schedule execution check: AHU-1 must appear in SMDB-1 schedule as a regular non-subpanel row
        val smdbResult = PanelScheduleEngine.generateScheduleRecursive("SMDB-1", circuits)
        assertEquals(2, smdbResult.rows.size)

        val ahuRow = smdbResult.rows.find { it.description.contains("AHU-1") }
        assertNotNull(ahuRow)
        assertFalse(ahuRow!!.isSubPanel)
        assertNull(ahuRow.subPanelName)

        val dbRow = smdbResult.rows.find { it.description.contains("DB-1") }
        assertNotNull(dbRow)
        assertTrue(dbRow!!.isSubPanel)
        assertEquals("DB-1", dbRow.subPanelName)
    }

    @Test
    fun testEffectivePhaseImbalanceLimitAndStatus() {
        val defaultProject = ProjectEntity(projectId = "P1", projectName = "Test Project", defaultPhaseImbalanceLimitPct = 20.0)
        val strictProject = ProjectEntity(projectId = "P2", projectName = "Strict Project", defaultPhaseImbalanceLimitPct = 10.0)

        val circuitNoOverride = CircuitEntity(id = 1, circuitName = "MDB-1", loadKVA = 30.0, fedFrom = "Transformer", panelType = "MDB", phaseImbalanceLimitOverridePct = null)
        val circuitWithOverride = CircuitEntity(id = 2, circuitName = "SMDB-1", loadKVA = 30.0, fedFrom = "Transformer", panelType = "SMDB", phaseImbalanceLimitOverridePct = 25.0)

        // Result with 15% unbalance
        val result1 = PanelScheduleRecursiveResult(
            panelName = "MDB-1",
            panelCircuit = circuitNoOverride,
            unbalancePct = 15.0
        )

        // 1. Default project (20%) + No Override -> 20% limit, 15% unbalance is balanced
        assertEquals(20.0, result1.effectiveImbalanceLimit(defaultProject), 0.01)
        assertTrue(result1.isBalanced(defaultProject))

        // 2. Strict project (10%) + No Override -> 10% limit, 15% unbalance is unbalanced
        assertEquals(10.0, result1.effectiveImbalanceLimit(strictProject), 0.01)
        assertFalse(result1.isBalanced(strictProject))

        // 3. Result with circuit override (25%)
        val result2 = PanelScheduleRecursiveResult(
            panelName = "SMDB-1",
            panelCircuit = circuitWithOverride,
            unbalancePct = 15.0
        )

        // Circuit override (25%) wins over strict project default (10%) -> 25% limit, 15% unbalance is balanced
        assertEquals(25.0, result2.effectiveImbalanceLimit(strictProject), 0.01)
        assertTrue(result2.isBalanced(strictProject))
    }

    @Test
    fun testLoadTypeBreakdownCalculation() {
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "L1", loadKVA = 10.0, pf = 0.92, demandFactor = 0.90, loadCategory = "LTG", fedFrom = "MDB"),
            CircuitEntity(id = 2, circuitName = "L2", loadKVA = 5.0, pf = 0.92, demandFactor = 0.90, loadCategory = "LTG", fedFrom = "MDB"),
            CircuitEntity(id = 3, circuitName = "M1", loadKVA = 20.0, pf = 0.85, demandFactor = 0.80, loadCategory = "MTR", fedFrom = "MDB"),
            CircuitEntity(id = 4, circuitName = "S1", loadKVA = 8.0, pf = 0.85, demandFactor = 0.60, loadCategory = "SO", fedFrom = "MDB"),
            CircuitEntity(id = 5, circuitName = "U1", loadKVA = 2.0, pf = 0.80, demandFactor = 1.00, loadCategory = null, fedFrom = "MDB")
        )

        val breakdown = com.example.data.calculateLoadTypeBreakdown(circuits)

        assertEquals(45.0, breakdown.totalConnectedKva, 0.01)
        // Demand kVA = 15*0.90 (13.5) + 20*0.80 (16.0) + 8*0.60 (4.8) + 2*1.0 (2.0) = 36.3 kVA
        assertEquals(36.3, breakdown.totalDemandKva, 0.01)
        assertEquals(36.3 / 45.0, breakdown.overallDemandFactor, 0.001)

        assertEquals(4, breakdown.rows.size)

        val ltgRow = breakdown.rows.first { it.categoryCode == "LTG" }
        assertEquals(2, ltgRow.circuitCount)
        assertEquals(15.0, ltgRow.connectedKva, 0.01)
        assertEquals(13.5, ltgRow.demandKva, 0.01)
        assertEquals(0.90, ltgRow.effectiveDemandFactor, 0.01)

        val mtrRow = breakdown.rows.first { it.categoryCode == "MTR" }
        assertEquals(1, mtrRow.circuitCount)
        assertEquals(20.0, mtrRow.connectedKva, 0.01)
        assertEquals(16.0, mtrRow.demandKva, 0.01)

        val uncategRow = breakdown.rows.first { it.categoryCode == "UNCATEGORIZED" }
        assertEquals(1, uncategRow.circuitCount)
        assertEquals(2.0, uncategRow.connectedKva, 0.01)
    }

    @Test
    fun testLoadTypeBreakdownInPanelScheduleRecursive() {
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "MDB", loadKVA = 0.0, fedFrom = "Transformer", panelType = "MDB"),
            CircuitEntity(id = 2, circuitName = "C1", loadKVA = 10.0, pf = 0.92, demandFactor = 0.90, loadCategory = "LTG", fedFrom = "MDB"),
            CircuitEntity(id = 3, circuitName = "C2", loadKVA = 10.0, pf = 0.85, demandFactor = 0.80, loadCategory = "MTR", fedFrom = "MDB")
        )

        val result = PanelScheduleEngine.generateScheduleRecursive("MDB", circuits)

        assertEquals(20.0, result.totalPanelLoadKVA, 0.01)
        assertNotNull(result.loadTypeBreakdown)
        assertEquals(20.0, result.loadTypeBreakdown.totalConnectedKva, 0.01)
        assertEquals(17.0, result.loadTypeBreakdown.totalDemandKva, 0.01) // 9 + 8 = 17
        assertEquals(2, result.loadTypeBreakdown.rows.size)
    }
}
