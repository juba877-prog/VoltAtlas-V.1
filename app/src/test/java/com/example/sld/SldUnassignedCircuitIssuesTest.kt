package com.example.sld

import com.example.data.AtsEntity
import com.example.data.CircuitEntity
import com.example.data.GeneratorEntity
import com.example.data.UpsEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M6: the project-level unassigned-circuit surfacing is the only way a user learns that
 * flagged circuits became unassigned. Each condition must raise exactly its graph
 * validation issue, and the issue must be absent otherwise.
 */
class SldUnassignedCircuitIssuesTest {

    private fun build(
        vararg circuits: CircuitEntity,
        generators: List<GeneratorEntity> = emptyList(),
        atsList: List<AtsEntity> = emptyList(),
        upsList: List<UpsEntity> = emptyList()
    ) = ElectricalNetworkGraphBuilder.buildGraph(
        activeProjectId = "p",
        transformers = emptyList(),
        circuits = circuits.toList(),
        generators = generators,
        atsList = atsList,
        upsList = upsList
    )

    @Test
    fun flaggedButUnassignedCircuitsRaiseTheirProjectLevelIssues() {
        val graph = build(
            CircuitEntity(id = 1, projectId = "p", circuitName = "G-LOAD", loadKVA = 10.0, generatorIncluded = true),
            CircuitEntity(id = 2, projectId = "p", circuitName = "A-LOAD", loadKVA = 10.0, atsIncluded = true),
            CircuitEntity(id = 3, projectId = "p", circuitName = "U-LOAD", loadKVA = 10.0, upsBacked = true)
        )
        assertTrue(
            "Flagged-but-unassigned generator circuit must raise UNASSIGNED_GENERATOR_CIRCUIT",
            graph.validationIssues.any { it.issueCode == "UNASSIGNED_GENERATOR_CIRCUIT" && it.componentName == "G-LOAD" }
        )
        assertTrue(
            "Flagged-but-unassigned ATS circuit must raise UNASSIGNED_ATS_CIRCUIT",
            graph.validationIssues.any { it.issueCode == "UNASSIGNED_ATS_CIRCUIT" && it.componentName == "A-LOAD" }
        )
        assertTrue(
            "Flagged-but-unassigned UPS circuit must raise UNASSIGNED_UPS_CIRCUIT",
            graph.validationIssues.any { it.issueCode == "UNASSIGNED_UPS_CIRCUIT" && it.componentName == "U-LOAD" }
        )
    }

    @Test
    fun explicitlyAssignedCircuitsRaiseNoUnassignedIssues() {
        val graph = build(
            CircuitEntity(id = 1, projectId = "p", circuitName = "G-LOAD", loadKVA = 10.0, generatorIncluded = true, assignedGeneratorId = "GEN-01"),
            CircuitEntity(id = 2, projectId = "p", circuitName = "A-LOAD", loadKVA = 10.0, atsIncluded = true, assignedAtsId = "ATS-01"),
            CircuitEntity(id = 3, projectId = "p", circuitName = "U-LOAD", loadKVA = 10.0, upsBacked = true, assignedUpsId = "UPS-01"),
            generators = listOf(GeneratorEntity(projectId = "p", generatorId = "GEN-01", generatorName = "Gen 1")),
            atsList = listOf(AtsEntity(projectId = "p", atsId = "ATS-01", atsName = "ATS 1")),
            upsList = listOf(UpsEntity(projectId = "p", upsId = "UPS-01", upsName = "UPS 1"))
        )
        assertFalse(
            "Explicitly assigned circuits must raise no UNASSIGNED_* issues",
            graph.validationIssues.any { it.issueCode.startsWith("UNASSIGNED_") }
        )
    }

    @Test
    fun clearedFlagCircuitsRaiseNoUnassignedIssues() {
        val graph = build(
            CircuitEntity(id = 1, projectId = "p", circuitName = "G-LOAD", loadKVA = 10.0, generatorIncluded = false),
            CircuitEntity(id = 2, projectId = "p", circuitName = "A-LOAD", loadKVA = 10.0, atsIncluded = false),
            CircuitEntity(id = 3, projectId = "p", circuitName = "U-LOAD", loadKVA = 10.0, upsBacked = false)
        )
        assertFalse(
            "Circuits with cleared flags must raise no UNASSIGNED_* issues",
            graph.validationIssues.any { it.issueCode.startsWith("UNASSIGNED_") }
        )
    }
}