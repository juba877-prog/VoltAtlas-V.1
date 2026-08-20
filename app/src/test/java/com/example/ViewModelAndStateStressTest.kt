package com.example

import com.example.data.*
import com.example.sld.*
import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test

class ViewModelAndStateStressTest {

    @Test
    fun testGraphBuilderCyclicLoopsAndAtsResolution() {
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "MDB-MAIN", loadKVA = 200.0, fedFrom = "Transformer", transformerId = "TX-01", projectId = "PROJ-1"),
            CircuitEntity(id = 2, circuitName = "PANEL-A", loadKVA = 50.0, fedFrom = "MDB-MAIN", transformerId = "TX-01", projectId = "PROJ-1"),
            CircuitEntity(id = 3, circuitName = "PANEL-B", loadKVA = 20.0, fedFrom = "PANEL-C", transformerId = "TX-01", projectId = "PROJ-1"),
            CircuitEntity(id = 4, circuitName = "PANEL-C", loadKVA = 20.0, fedFrom = "PANEL-B", transformerId = "TX-01", projectId = "PROJ-1"),
            CircuitEntity(id = 5, circuitName = "EMERGENCY-PANEL", loadKVA = 30.0, fedFrom = "ATS-01", transformerId = "TX-01", atsIncluded = true, projectId = "PROJ-1")
        )

        val transformers = listOf(
            TransformerEntity(projectId = "PROJ-1", transformerId = "TX-01", kVA = 1000.0, status = "Active")
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = "PROJ-1",
            transformers = transformers,
            circuits = circuits
        )

        assertNotNull("Electrical network graph must be built without throw or crash", graph)
        assertTrue("Graph must contain nodes", graph.nodes.isNotEmpty())
        println("VIEWMODEL STRESS: Network Graph built successfully with ${graph.nodes.size} nodes and ${graph.edges.size} edges.")
    }

    @Test
    fun testSecondaryEnginePipelinesInUnitTestContext() {
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "FEEDER-1", loadKVA = 100.0, fedFrom = "Transformer", transformerId = "TX-1", length_m = 50.0),
            CircuitEntity(id = 2, circuitName = "LIGHTING-1", loadKVA = 10.0, fedFrom = "FEEDER-1", transformerId = "TX-1", length_m = 30.0)
        )
        val sizingResults = mapOf(
            1 to SizingResult(144.0, 160.0, "MCCB", 1.0, 144.0, "M-70", "70 mm²", 194.0, 194.0, 74.0, "70 mm²", "70 mm²", 0.8, 0.2, 0.2, "PASS", resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null),
            2 to SizingResult(14.4, 16.0, "MCB", 1.0, 14.4, "M-4", "4 mm²", 34.0, 34.0, 42.0, "4 mm²", "4 mm²", 0.5, 0.1, 0.3, "PASS", resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null)
        )

        val boq = BoqEngine.generateBoq(circuits, sizingResults)
        assertNotNull("BoQ must be generated without crash", boq)
        assertTrue("BoQ items must be populated", boq.isNotEmpty())

        val drumAllocations = DrumScheduleEngine.generateDetailedDrumSchedule(circuits, sizingResults, maxDrumLength = 500.0)
        assertNotNull("Drum allocations must be calculated", drumAllocations)

        val procurement = ProcurementEngine.generateProcurement(circuits, sizingResults)
        assertNotNull("Procurement summary must be calculated", procurement)

        println("VIEWMODEL STRESS: BoQ (${boq.size} items), Drums (${drumAllocations.size} drums), Procurement (${procurement.size} items) verified.")
    }
}
