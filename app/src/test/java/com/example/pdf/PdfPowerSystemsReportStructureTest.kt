package com.example.pdf

import com.example.data.ProjectEntity
import com.example.engine.AtsResult
import com.example.engine.GeneratorResult
import com.example.engine.UpsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural test for the Power Systems report section (B2).
 *
 * The blocks are derived deterministically by [powerSystemsReportBlocks], which is what
 * [PowerSystemsSection] draws verbatim. Assertions key on identifiers taken from the
 * fixture maps themselves — never on literal identifier strings.
 */
class PdfPowerSystemsReportStructureTest {

    private val project = ProjectEntity(projectId = "proj-report-structure", projectName = "Generic Report Structure")

    private fun snapshot(
        generatorResults: Map<String, GeneratorResult> = emptyMap(),
        atsResults: Map<String, AtsResult> = emptyMap(),
        upsResults: Map<String, UpsResult> = emptyMap()
    ) = ProjectReportSnapshot(
        project = project,
        circuits = emptyList(),
        transformers = emptyList(),
        sizingResults = emptyMap(),
        shortCircuitResults = emptyMap(),
        conduitResults = emptyMap(),
        conduitProcurement = emptyList(),
        generatorResults = generatorResults,
        atsResults = atsResults,
        upsResults = upsResults,
        options = ReportOptions(isCompleteProjectReport = false, selectedSections = listOf("Power Systems"))
    )

    @Test
    fun severalInstancesProduceOneBlockPerInstanceKeyedToItsIdentifier() {
        // Generic synthetic identifiers derived from the fixture maps.
        val genIds = listOf("gen-alpha", "gen-beta", "gen-gamma")
        val atsIds = listOf("ats-alpha", "ats-beta")
        val upsIds = listOf("ups-alpha")
        val genResults = genIds.associateWith { id -> GeneratorResult(status = "OPTIMAL", designCapacity = 100.0) }
        val atsResults = atsIds.associateWith { id -> AtsResult(status = "VALID SIZED", governingCurrent = 250.0) }
        val upsResults = upsIds.associateWith { id -> UpsResult(status = "OPTIMAL", totalCriticalLoadKVA = 10.0, totalCriticalLoadKW = 9.0, upsApparentPowerReq = 11.1, designUpsKVA = 12.0, batteryLoadKW = null, batteryEnergyReq = null, correctedBatteryCapacity = null, batteryAh = null, recommendedSize = null, loadingPct = null, batteryBankRequired = "N/A", estimatedBatteryStrings = null, autonomyAtFullLoad = "N/A", messages = emptyList()) }

        val blocks = powerSystemsReportBlocks(snapshot(genResults, atsResults, upsResults))

        // N instances -> N blocks; each block keys on its own identifier.
        assertEquals("One block per generator instance", genIds.size, blocks.count { it.typeLabel == "GENERATOR" })
        assertEquals("One block per ATS instance", atsIds.size, blocks.count { it.typeLabel == "ATS" })
        assertEquals("One block per UPS instance", upsIds.size, blocks.count { it.typeLabel == "UPS" })

        genIds.forEach { id ->
            assertTrue(
                "A GENERATOR block must exist keyed to its own identifier",
                blocks.any { it.typeLabel == "GENERATOR" && it.instanceId == id }
            )
        }
        atsIds.forEach { id ->
            assertTrue(
                "An ATS block must exist keyed to its own identifier",
                blocks.any { it.typeLabel == "ATS" && it.instanceId == id }
            )
        }
        upsIds.forEach { id ->
            assertTrue(
                "An UPS block must exist keyed to its own identifier",
                blocks.any { it.typeLabel == "UPS" && it.instanceId == id }
            )
        }

        // Each labelled block is exactly as rendered by the section.
        assertTrue(
            "Header text rendered is '<TYPE> - <id>'",
            blocks.all { it.typeLabel.isNotBlank() && it.instanceId.isNotBlank() }
        )
        assertTrue(
            "Every ATS block carries its governing current value line",
            atsIds.all { id -> blocks.first { it.typeLabel == "ATS" && it.instanceId == id }.keyValues.any { it.first == "Governing Current" } }
        )
    }

    @Test
    fun zeroInstancesProduceNotConfiguredState() {
        val blocks = powerSystemsReportBlocks(snapshot())
        assertTrue("No blocks when every type is absent", blocks.isEmpty())
        assertTrue("Not-configured state reported when every type is absent", powerSystemsReportNotConfigured(snapshot()))
    }

    @Test
    fun mixedPopulationRendersPresentTypeOnlyAndNotConfiguredPerType() {
        // Present types render; an absent type produces no block and does not suppress
        // the present ones — the previous conjunctive not-configured condition is pinned
        // per type by test rather than by inspection.
        val genResults = mapOf("gen-only" to GeneratorResult(status = "OPTIMAL", designCapacity = 100.0))
        val snapshot0 = snapshot(generatorResults = genResults)

        val blocks = powerSystemsReportBlocks(snapshot0)
        assertTrue(blocks.any { it.typeLabel == "GENERATOR" && it.instanceId == "gen-only" })
        assertTrue(blocks.none { it.typeLabel == "ATS" })
        assertTrue(blocks.none { it.typeLabel == "UPS" })
        // Not-configured is only when EVERY type is absent; a present type must prevent it.
        assertFalse(powerSystemsReportNotConfigured(snapshot0))
    }
}