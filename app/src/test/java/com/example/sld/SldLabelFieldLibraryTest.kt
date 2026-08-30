package com.example.sld

import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.CableFailureCode
import com.example.engine.SizingResult
import com.example.engine.SizingResultStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enforces the label-field audit contract (Roadmap §1.2):
 *
 *  1. Every §1.2 checklist equipment class is audited, and every audit record
 *     is fully documented (requirement, slot, population source).
 *  2. Fields marked RENDERED are actually live end-to-end — verified here for
 *     the two slots added in this pass:
 *       - transformer windingConnection ← TransformerEntity.vectorGroup
 *       - device interruptingRatingKa  ← SizingResult.deviceBreakingKA
 *  3. Fields not yet in the data model must stay out of [SldDataBlock] until a
 *     source exists (no dead slots — same policy as the §1.1 symbol audit).
 */
class SldLabelFieldLibraryTest {

    @Test
    fun everyChecklistEquipmentClass_isAudited() {
        val auditedClasses = SldLabelFieldLibrary.labelFields.map { it.equipmentClass }.toSet()
        assertEquals(
            "Every §1.2 checklist equipment class must have at least one audited field. Missing: ${SldLabelFieldLibrary.EQUIPMENT_CLASSES - auditedClasses}",
            SldLabelFieldLibrary.EQUIPMENT_CLASSES,
            auditedClasses
        )
    }

    @Test
    fun everyFieldSpec_isFullyDocumented() {
        SldLabelFieldLibrary.labelFields.forEach { spec ->
            assertTrue("[${spec.equipmentClass}.${spec.fieldId}] requirement must be documented", spec.checklistRequirement.isNotBlank())
            assertTrue("[${spec.equipmentClass}.${spec.fieldId}] dataBlockSlot must be documented", spec.dataBlockSlot.isNotBlank())
            assertTrue("[${spec.equipmentClass}.${spec.fieldId}] populationSource must be documented", spec.populationSource.isNotBlank())
            if (spec.auditStatus != LabelFieldAuditStatus.RENDERED) {
                assertTrue(
                    "${spec.equipmentClass}.${spec.fieldId} is ${spec.auditStatus} — must explain what is missing",
                    spec.notes.isNotBlank()
                )
            }
        }
        val duplicates = SldLabelFieldLibrary.labelFields
            .groupingBy { it.equipmentClass to it.fieldId }
            .eachCount()
            .filterValues { it > 1 }
        assertTrue("Duplicate field specs: $duplicates", duplicates.isEmpty())
    }

    private fun buildGraphWithDefaults() = ElectricalNetworkGraphBuilder.buildGraph(
        activeProjectId = "p",
        transformers = listOf(TransformerEntity(projectId = "p", transformerId = "TX-01", kVA = 1000.0)),
        circuits = listOf(
            CircuitEntity(id = 1, projectId = "p", circuitName = "PANEL-A", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB")
        )
    )

    @Test
    fun transformerWindingConnection_isPopulatedFromEntityVectorGroup() {
        val graph = buildGraphWithDefaults()
        val txNode = graph.nodes.first { it.type == SldNodeType.TRANSFORMER }
        assertEquals("Dyn11", txNode.dataBlock.windingConnection)
        assertTrue(txNode.dataBlock.windingConnection!!.isNotBlank())
        // The pre-existing §1.2 checklist fields on the transformer remain intact
        assertEquals("5.0%", txNode.dataBlock.impedancePct)
        assertTrue(txNode.dataBlock.ratedKva!!.contains("kVA"))
    }

    private fun sizingResult(icuKA: Double) = SizingResult(
        Ib_A = 57.7,
        deviceSize_A = 63.0,
        deviceType = "MCCB",
        cTotalDerating = 0.85,
        iRequired_perCable_A = 68.0,
        productCode = "TEST-1",
        cableSize_mm2 = "25 mm²",
        ratedI_table_A = 82.0,
        capacity_derated_Iz_A = 69.7,
        utilizationPct = 82.8,
        neutral_mm2 = "14 mm²",
        earthCPC_mm2 = "14 mm²",
        voltDrop_V = 4.0,
        vdPct = 1.0,
        cumulativeVDpct = 1.2,
        status = "PASS",
        resultStatus = SizingResultStatus.PASS,
        isAmpacityOk = true,
        isVoltageDropOk = true,
        primaryFailureCode = null,
        deviceBreakingKA = icuKA
    )

    @Test
    fun circuitInterruptingRating_isPopulatedFromSizingResult() {
        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = "p",
            transformers = listOf(TransformerEntity(projectId = "p", transformerId = "TX-01", kVA = 1000.0)),
            circuits = listOf(
                CircuitEntity(id = 1, projectId = "p", circuitName = "PANEL-A", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB")
            ),
            sizingResults = mapOf(1 to sizingResult(icuKA = 36.0))
        )
        val panelNode = graph.nodes.first { it.id == "circ_1" }
        assertEquals("36 kA", panelNode.dataBlock.interruptingRatingKa)
        // The fault-duty kA field (distinct from Icu) remains populated
        assertNotNull(panelNode.dataBlock.faultLevelKa)
    }

    @Test
    fun circuitInterruptingRating_isNull_whenNoSizingResultExists() {
        val graph = buildGraphWithDefaults()
        val panelNode = graph.nodes.first { it.id == "circ_1" }
        assertNull("Icu must stay null when no sizing data exists — no fabricated values", panelNode.dataBlock.interruptingRatingKa)
    }

    @Test
    fun pendingDataModelFields_haveNoDeadDataBlockSlots() {
        // Guard against "dead slot" drift: fields documented as PENDING_DATA_MODEL
        // must not have a same-named SldDataBlock property added prematurely.
        val dataBlockProperties = SldDataBlock::class.java.declaredFields.map { it.name }
        SldLabelFieldLibrary.labelFields
            .filter { it.auditStatus == LabelFieldAuditStatus.PENDING_DATA_MODEL }
            .forEach { spec ->
                val slotName = spec.dataBlockSlot
                    .substringAfter("SldDataBlock.", "")
                    .substringBefore(' ').substringBefore('(')
                if (slotName.isNotBlank()) {
                    assertTrue(
                        "${spec.equipmentClass}.${spec.fieldId} is PENDING_DATA_MODEL but a slot named '$slotName' already exists in SldDataBlock",
                        slotName !in dataBlockProperties
                    )
                }
            }
    }
}