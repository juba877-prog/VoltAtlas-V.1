package com.example

import com.example.data.*
import com.example.engine.*
import com.example.pdf.*
import com.example.sld.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 3 / Part 5.1 — Downstream Honesty at Zero Instances Verification Suite.
 *
 * Verifies that when any equipment type (Generator, ATS, UPS) has zero instances:
 * 1. Diagram graph contains NO node of that type (no placeholder, no phantom symbol, no phantom edges).
 * 2. CAD DXF export emits NO symbol, NO block insert, NO annotation, and NO legend row for absent types.
 * 3. PDF report omits absent equipment sections (emits only present types, or marks "Not configured" when all absent).
 * 4. Load aggregation and dependent sizing behave consistently with absence of source (never zero/default-filled figures).
 * 5. BOQ contains NO line item for absent equipment.
 *
 * Governed by zero fabrication and generic synthetic fixtures.
 */
class PowerSystemsDownstreamZeroHonestyTest {

    private val genericProjectId = "proj-downstream-zero-honesty"
    private val genericProjectName = "Generic Synthetic Substation"

    private fun createGenericTransformer(): TransformerEntity = TransformerEntity(
        projectId = genericProjectId,
        transformerId = "tx-alpha",
        transformerName = "Main Incomer Transformer",
        kVA = 1000.0,
        voltage = 400,
        primaryVoltageV = 11000,
        percentZ = 5.75,
        vectorGroup = "Dyn11"
    )

    private fun createGenericCircuits(): List<CircuitEntity> = listOf(
        CircuitEntity(
            id = 101,
            projectId = genericProjectId,
            circuitName = "Main Incomer Feeder",
            loadKVA = 300.0,
            fedFrom = "tx-alpha",
            transformerId = "tx-alpha",
            panelType = "MDB",
            length_m = 15.0,
            cableType = "Cu/XLPE",
            manufacturer = "GenericMfg"
        ),
        CircuitEntity(
            id = 102,
            projectId = genericProjectId,
            circuitName = "Sub-Distribution Board 1",
            loadKVA = 120.0,
            fedFrom = "Main Incomer Feeder",
            transformerId = "tx-alpha",
            panelType = "SMDB",
            parentId = 101,
            length_m = 35.0,
            cableType = "Cu/XLPE",
            manufacturer = "GenericMfg"
        )
    )

    private fun createSnapshot(
        generatorResults: Map<String, GeneratorResult> = emptyMap(),
        atsResults: Map<String, AtsResult> = emptyMap(),
        upsResults: Map<String, UpsResult> = emptyMap(),
        pfCorrectionResults: List<PfCorrectionResult> = emptyList()
    ): ProjectReportSnapshot = ProjectReportSnapshot(
        project = ProjectEntity(projectId = genericProjectId, projectName = genericProjectName),
        circuits = emptyList(),
        transformers = emptyList(),
        sizingResults = emptyMap(),
        shortCircuitResults = emptyMap(),
        conduitResults = emptyMap(),
        conduitProcurement = emptyList(),
        generatorResults = generatorResults,
        atsResults = atsResults,
        upsResults = upsResults,
        pfCorrectionResults = pfCorrectionResults,
        options = ReportOptions(isCompleteProjectReport = false, selectedSections = listOf("Power Systems"))
    )

    private fun createDummySizingResult(
        size: String = "185",
        productCode: String = "GEN-185",
        cableOD: Double = 30.0
    ): SizingResult = SizingResult(
        Ib_A = 400.0,
        deviceSize_A = 400.0,
        deviceType = "MCCB",
        cTotalDerating = 1.0,
        iRequired_perCable_A = 400.0,
        productCode = productCode,
        cableSize_mm2 = size,
        ratedI_table_A = 450.0,
        capacity_derated_Iz_A = 450.0,
        utilizationPct = 88.8,
        neutral_mm2 = size,
        earthCPC_mm2 = size,
        voltDrop_V = 2.0,
        vdPct = 0.5,
        cumulativeVDpct = 0.5,
        status = "PASS",
        cableOD_mm = cableOD,
        resultStatus = SizingResultStatus.PASS,
        isAmpacityOk = true,
        isVoltageDropOk = true,
        primaryFailureCode = null
    )

    // ------------------------------------------------------------------
    // DXF Tokenizer & Entity Parser Helper
    // ------------------------------------------------------------------
    private data class DxfPair(val code: Int, val value: String)

    private fun tokenizeDxf(dxf: String): List<DxfPair> {
        val lines = dxf.lines()
        val out = mutableListOf<DxfPair>()
        var i = 0
        while (i + 1 < lines.size) {
            out.add(DxfPair(lines[i].trim().toIntOrNull() ?: -1, lines[i + 1]))
            i += 2
        }
        return out
    }

    private fun getEntitiesPairs(dxf: String): List<DxfPair> {
        val pairs = tokenizeDxf(dxf)
        val out = mutableListOf<DxfPair>()
        var insideEntities = false
        var i = 0
        while (i < pairs.size) {
            val p = pairs[i]
            when {
                p.code == 0 && p.value == "SECTION" -> {
                    insideEntities = pairs.getOrNull(i + 1)?.let { it.code == 2 && it.value == "ENTITIES" } == true
                }
                p.code == 0 && p.value == "ENDSEC" -> {
                    if (insideEntities) break
                }
                insideEntities -> out.add(p)
            }
            i++
        }
        return out
    }

    private fun findBlockInsertsInEntities(entitiesPairs: List<DxfPair>): List<String> {
        val blockNames = mutableListOf<String>()
        var i = 0
        while (i < entitiesPairs.size) {
            if (entitiesPairs[i].code == 0 && entitiesPairs[i].value == "INSERT") {
                var j = i + 1
                while (j < entitiesPairs.size && entitiesPairs[j].code != 0) {
                    if (entitiesPairs[j].code == 2) {
                        blockNames.add(entitiesPairs[j].value)
                    }
                    j++
                }
                i = j
            } else {
                i++
            }
        }
        return blockNames
    }

    private fun findTextEntitiesInEntities(entitiesPairs: List<DxfPair>): List<Pair<String, String>> {
        val texts = mutableListOf<Pair<String, String>>()
        var i = 0
        while (i < entitiesPairs.size) {
            if (entitiesPairs[i].code == 0 && entitiesPairs[i].value == "TEXT") {
                var j = i + 1
                var layer = ""
                var text = ""
                while (j < entitiesPairs.size && entitiesPairs[j].code != 0) {
                    if (entitiesPairs[j].code == 8) layer = entitiesPairs[j].value
                    if (entitiesPairs[j].code == 1) text = entitiesPairs[j].value
                    j++
                }
                texts.add(layer to text)
                i = j
            } else {
                i++
            }
        }
        return texts
    }

    // ==================================================================
    // 1. DIAGRAM GRAPH HONESTY AT ZERO INSTANCES
    // ==================================================================

    @Test
    fun testDiagramGraphOmitsAbsentEquipmentNodesAndEdgesAtZeroInstances() {
        val tx = createGenericTransformer()
        val circuits = createGenericCircuits()

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = genericProjectId,
            transformers = listOf(tx),
            circuits = circuits,
            generators = emptyList(),
            atsList = emptyList(),
            upsList = emptyList()
        )

        // Must contain NO nodes of type GENERATOR, SYNC_BUS, ATS, or UPS
        val generatorNodes = graph.nodes.filter { it.type == SldNodeType.GENERATOR }
        val syncBusNodes = graph.nodes.filter { it.type == SldNodeType.SYNC_BUS }
        val atsNodes = graph.nodes.filter { it.type == SldNodeType.ATS }
        val upsNodes = graph.nodes.filter { it.type == SldNodeType.UPS }

        assertTrue("Diagram graph must contain 0 GENERATOR nodes when list is empty", generatorNodes.isEmpty())
        assertTrue("Diagram graph must contain 0 SYNC_BUS nodes when list is empty", syncBusNodes.isEmpty())
        assertTrue("Diagram graph must contain 0 ATS nodes when list is empty", atsNodes.isEmpty())
        assertTrue("Diagram graph must contain 0 UPS nodes when list is empty", upsNodes.isEmpty())

        // Must contain NO phantom equipment edges
        val syncEdges = graph.edges.filter { it.type == SldEdgeType.GEN_SYNC_CONNECTION }
        val atsEdges = graph.edges.filter {
            it.type == SldEdgeType.ATS_NORMAL_PATH ||
                it.type == SldEdgeType.ATS_EMERGENCY_PATH ||
                it.type == SldEdgeType.ATS_OUTPUT
        }
        val upsEdges = graph.edges.filter {
            it.type == SldEdgeType.UPS_INPUT || it.type == SldEdgeType.UPS_OUTPUT
        }

        assertTrue("Diagram graph must contain 0 generator sync edges", syncEdges.isEmpty())
        assertTrue("Diagram graph must contain 0 ATS edges", atsEdges.isEmpty())
        assertTrue("Diagram graph must contain 0 UPS edges", upsEdges.isEmpty())
    }

    @Test
    fun testDiagramGraphSelectivelyIncludesOnlyPresentEquipmentTypes() {
        val tx = createGenericTransformer()
        val circuits = createGenericCircuits()

        // 1. Generator only configured
        val singleGen = GeneratorEntity(
            projectId = genericProjectId,
            generatorId = "gen-synthetic-alpha",
            generatorName = "Backup Genset Alpha",
            manualSizeKVA = 500.0
        )
        val graphGenOnly = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = genericProjectId,
            transformers = listOf(tx),
            circuits = circuits,
            generators = listOf(singleGen),
            atsList = emptyList(),
            upsList = emptyList()
        )
        assertEquals(1, graphGenOnly.nodes.count { it.type == SldNodeType.GENERATOR })
        assertEquals("gen_gen-synthetic-alpha", graphGenOnly.nodes.first { it.type == SldNodeType.GENERATOR }.id)
        assertEquals(0, graphGenOnly.nodes.count { it.type == SldNodeType.ATS })
        assertEquals(0, graphGenOnly.nodes.count { it.type == SldNodeType.UPS })

        // 2. ATS only configured
        val singleAts = AtsEntity(
            projectId = genericProjectId,
            atsId = "ats-synthetic-alpha",
            atsName = "Automatic Transfer Switch Alpha",
            normalSourceId = "101"
        )
        val graphAtsOnly = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = genericProjectId,
            transformers = listOf(tx),
            circuits = circuits,
            generators = emptyList(),
            atsList = listOf(singleAts),
            upsList = emptyList()
        )
        assertEquals(1, graphAtsOnly.nodes.count { it.type == SldNodeType.ATS })
        assertEquals("ats_ats-synthetic-alpha", graphAtsOnly.nodes.first { it.type == SldNodeType.ATS }.id)
        assertEquals(0, graphAtsOnly.nodes.count { it.type == SldNodeType.GENERATOR })
        assertEquals(0, graphAtsOnly.nodes.count { it.type == SldNodeType.UPS })

        // 3. UPS only configured
        val singleUps = UpsEntity(
            projectId = genericProjectId,
            upsId = "ups-synthetic-alpha",
            upsName = "Static UPS Alpha",
            systemVoltage = 400
        )
        val graphUpsOnly = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = genericProjectId,
            transformers = listOf(tx),
            circuits = circuits,
            generators = emptyList(),
            atsList = emptyList(),
            upsList = listOf(singleUps)
        )
        assertEquals(1, graphUpsOnly.nodes.count { it.type == SldNodeType.UPS })
        assertEquals("ups_ups-synthetic-alpha", graphUpsOnly.nodes.first { it.type == SldNodeType.UPS }.id)
        assertEquals(0, graphUpsOnly.nodes.count { it.type == SldNodeType.GENERATOR })
        assertEquals(0, graphUpsOnly.nodes.count { it.type == SldNodeType.ATS })
    }

    // ==================================================================
    // 2. CAD EXPORT (DXF) HONESTY AT ZERO INSTANCES
    // ==================================================================

    @Test
    fun testCadExportEmitsNoSymbolsBlocksAnnotationsOrLegendRowsWhenAllAbsent() {
        val tx = createGenericTransformer()
        val circuits = createGenericCircuits()

        val layoutResult = SldLayoutEngine.calculateLayout(
            activeProjectId = genericProjectId,
            transformers = listOf(tx),
            circuits = circuits,
            generators = emptyList(),
            atsList = emptyList(),
            upsList = emptyList()
        )

        val dxf = SldDxfExporter.generateDxf(layoutResult, genericProjectName)
        val entitiesPairs = getEntitiesPairs(dxf)
        val insertedBlocks = findBlockInsertsInEntities(entitiesPairs)
        val textEntities = findTextEntitiesInEntities(entitiesPairs)

        // 1. Block Inserts in ENTITIES section: must contain NO Generator, ATS, or UPS inserts
        assertFalse("DXF ENTITIES must not insert BLK_GENERATOR", insertedBlocks.contains(SldDxfExporter.BLK_GENERATOR))
        assertFalse("DXF ENTITIES must not insert BLK_ATS_3P", insertedBlocks.contains(SldDxfExporter.BLK_ATS_3P))
        assertFalse("DXF ENTITIES must not insert BLK_UPS_ONLINE", insertedBlocks.contains(SldDxfExporter.BLK_UPS_ONLINE))

        // 2. Annotations / Layers in ENTITIES: must contain NO text on generator layer
        assertFalse("DXF ENTITIES must not contain text on E-GEN layer", textEntities.any { it.first == "E-GEN" })

        // 3. Legend rows: must NOT contain absent equipment legend entries
        assertFalse("Legend must not mention Standby Generator", dxf.contains("(G) STANDBY GENERATOR"))
        assertFalse("Legend must not mention ATS", dxf.contains("[ATS] TRANSFER SWITCH"))
        assertFalse("Legend must not mention UPS", dxf.contains("[UPS] STATIC UPS"))
        assertFalse("Legend must not mention UPS Battery Autonomy", dxf.contains("Bat: UPS Battery Autonomy"))
    }

    @Test
    fun testCadExportSelectivelyEmitsOnlyPresentEquipmentSymbolsAndLegendRows() {
        val tx = createGenericTransformer()
        val circuits = createGenericCircuits()

        // Scenario A: Generator present, ATS & UPS absent
        val gen = GeneratorEntity(
            projectId = genericProjectId,
            generatorId = "gen-synthetic-cad",
            generatorName = "Standby Generator Alpha",
            manualSizeKVA = 400.0
        )
        val layoutGen = SldLayoutEngine.calculateLayout(
            activeProjectId = genericProjectId,
            transformers = listOf(tx),
            circuits = circuits,
            generators = listOf(gen),
            atsList = emptyList(),
            upsList = emptyList()
        )
        val dxfGen = SldDxfExporter.generateDxf(layoutGen, genericProjectName)
        val insertsGen = findBlockInsertsInEntities(getEntitiesPairs(dxfGen))

        assertTrue("DXF ENTITIES must insert BLK_GENERATOR when generator is configured", insertsGen.contains(SldDxfExporter.BLK_GENERATOR))
        assertFalse("DXF ENTITIES must not insert BLK_ATS_3P when ATS is absent", insertsGen.contains(SldDxfExporter.BLK_ATS_3P))
        assertFalse("DXF ENTITIES must not insert BLK_UPS_ONLINE when UPS is absent", insertsGen.contains(SldDxfExporter.BLK_UPS_ONLINE))
        assertTrue("Legend must include Standby Generator", dxfGen.contains("(G) STANDBY GENERATOR"))
        assertFalse("Legend must not include ATS", dxfGen.contains("[ATS] TRANSFER SWITCH"))
        assertFalse("Legend must not include UPS", dxfGen.contains("[UPS] STATIC UPS"))

        // Scenario B: ATS present, Generator & UPS absent
        val ats = AtsEntity(
            projectId = genericProjectId,
            atsId = "ats-synthetic-cad",
            atsName = "Automatic Transfer Switch Alpha",
            normalSourceId = "101"
        )
        val layoutAts = SldLayoutEngine.calculateLayout(
            activeProjectId = genericProjectId,
            transformers = listOf(tx),
            circuits = circuits,
            generators = emptyList(),
            atsList = listOf(ats),
            upsList = emptyList()
        )
        val dxfAts = SldDxfExporter.generateDxf(layoutAts, genericProjectName)
        val insertsAts = findBlockInsertsInEntities(getEntitiesPairs(dxfAts))

        assertTrue("DXF ENTITIES must insert BLK_ATS_3P when ATS is configured", insertsAts.contains(SldDxfExporter.BLK_ATS_3P))
        assertFalse("DXF ENTITIES must not insert BLK_GENERATOR when generator is absent", insertsAts.contains(SldDxfExporter.BLK_GENERATOR))
        assertFalse("DXF ENTITIES must not insert BLK_UPS_ONLINE when UPS is absent", insertsAts.contains(SldDxfExporter.BLK_UPS_ONLINE))
        assertTrue("Legend must include ATS", dxfAts.contains("[ATS] TRANSFER SWITCH"))
        assertFalse("Legend must not include Standby Generator", dxfAts.contains("(G) STANDBY GENERATOR"))
        assertFalse("Legend must not include UPS", dxfAts.contains("[UPS] STATIC UPS"))

        // Scenario C: UPS present, Generator & ATS absent
        val ups = UpsEntity(
            projectId = genericProjectId,
            upsId = "ups-synthetic-cad",
            upsName = "Static UPS Alpha",
            systemVoltage = 400
        )
        val layoutUps = SldLayoutEngine.calculateLayout(
            activeProjectId = genericProjectId,
            transformers = listOf(tx),
            circuits = circuits,
            generators = emptyList(),
            atsList = emptyList(),
            upsList = listOf(ups)
        )
        val dxfUps = SldDxfExporter.generateDxf(layoutUps, genericProjectName)
        val insertsUps = findBlockInsertsInEntities(getEntitiesPairs(dxfUps))

        assertTrue("DXF ENTITIES must insert BLK_UPS_ONLINE when UPS is configured", insertsUps.contains(SldDxfExporter.BLK_UPS_ONLINE))
        assertFalse("DXF ENTITIES must not insert BLK_GENERATOR when generator is absent", insertsUps.contains(SldDxfExporter.BLK_GENERATOR))
        assertFalse("DXF ENTITIES must not insert BLK_ATS_3P when ATS is absent", insertsUps.contains(SldDxfExporter.BLK_ATS_3P))
        assertTrue("Legend must include UPS", dxfUps.contains("[UPS] STATIC UPS"))
        assertFalse("Legend must not include Standby Generator", dxfUps.contains("(G) STANDBY GENERATOR"))
        assertFalse("Legend must not include ATS", dxfUps.contains("[ATS] TRANSFER SWITCH"))
    }

    // ==================================================================
    // 3. PDF REPORT HONESTY AT ZERO INSTANCES
    // ==================================================================

    @Test
    fun testPdfReportOmitsAbsentEquipmentSectionsAndMarksNotConfiguredWhenAllAbsent() {
        // 1. All absent: 0 blocks, notConfigured == true (renders "Not configured")
        val snapshotAllAbsent = createSnapshot(
            generatorResults = emptyMap(),
            atsResults = emptyMap(),
            upsResults = emptyMap()
        )
        val blocksAllAbsent = powerSystemsReportBlocks(snapshotAllAbsent)
        assertTrue("PDF report must produce 0 blocks when all power systems equipment is absent", blocksAllAbsent.isEmpty())
        assertTrue("PDF report must flag not-configured when all power systems equipment is absent", powerSystemsReportNotConfigured(snapshotAllAbsent))

        // 2. Generator present only: 1 generator block, 0 ATS blocks, 0 UPS blocks, notConfigured == false
        val snapshotGenOnly = createSnapshot(
            generatorResults = mapOf("gen-alpha" to GeneratorResult(status = "OPTIMAL", designCapacity = 500.0)),
            atsResults = emptyMap(),
            upsResults = emptyMap()
        )
        val blocksGenOnly = powerSystemsReportBlocks(snapshotGenOnly)
        assertEquals(1, blocksGenOnly.size)
        assertEquals("GENERATOR", blocksGenOnly[0].typeLabel)
        assertEquals("gen-alpha", blocksGenOnly[0].instanceId)
        assertTrue("PDF report must not contain ATS blocks when ATS is absent", blocksGenOnly.none { it.typeLabel == "ATS" })
        assertTrue("PDF report must not contain UPS blocks when UPS is absent", blocksGenOnly.none { it.typeLabel == "UPS" })
        assertFalse("PDF report is not unconfigured when generator is present", powerSystemsReportNotConfigured(snapshotGenOnly))

        // 3. ATS present only: 1 ATS block, 0 generator blocks, 0 UPS blocks, notConfigured == false
        val snapshotAtsOnly = createSnapshot(
            generatorResults = emptyMap(),
            atsResults = mapOf("ats-alpha" to AtsResult(status = "SIZED", governingCurrent = 400.0)),
            upsResults = emptyMap()
        )
        val blocksAtsOnly = powerSystemsReportBlocks(snapshotAtsOnly)
        assertEquals(1, blocksAtsOnly.size)
        assertEquals("ATS", blocksAtsOnly[0].typeLabel)
        assertEquals("ats-alpha", blocksAtsOnly[0].instanceId)
        assertTrue("PDF report must not contain GENERATOR blocks when generator is absent", blocksAtsOnly.none { it.typeLabel == "GENERATOR" })
        assertTrue("PDF report must not contain UPS blocks when UPS is absent", blocksAtsOnly.none { it.typeLabel == "UPS" })
        assertFalse(powerSystemsReportNotConfigured(snapshotAtsOnly))

        // 4. UPS present only: 1 UPS block, 0 generator blocks, 0 ATS blocks, notConfigured == false
        val snapshotUpsOnly = createSnapshot(
            generatorResults = emptyMap(),
            atsResults = emptyMap(),
            upsResults = mapOf("ups-alpha" to UpsResult(
                status = "OPTIMAL",
                totalCriticalLoadKVA = 50.0,
                totalCriticalLoadKW = 45.0,
                upsApparentPowerReq = 55.0,
                designUpsKVA = 60.0,
                batteryLoadKW = null,
                batteryEnergyReq = null,
                correctedBatteryCapacity = null,
                batteryAh = null,
                recommendedSize = 60.0,
                loadingPct = 83.3,
                batteryBankRequired = "N/A",
                estimatedBatteryStrings = null,
                autonomyAtFullLoad = "15 min",
                messages = emptyList()
            ))
        )
        val blocksUpsOnly = powerSystemsReportBlocks(snapshotUpsOnly)
        assertEquals(1, blocksUpsOnly.size)
        assertEquals("UPS", blocksUpsOnly[0].typeLabel)
        assertEquals("ups-alpha", blocksUpsOnly[0].instanceId)
        assertTrue("PDF report must not contain GENERATOR blocks when generator is absent", blocksUpsOnly.none { it.typeLabel == "GENERATOR" })
        assertTrue("PDF report must not contain ATS blocks when ATS is absent", blocksUpsOnly.none { it.typeLabel == "ATS" })
        assertFalse(powerSystemsReportNotConfigured(snapshotUpsOnly))
    }

    // ==================================================================
    // 4. LOAD AGGREGATION & DEPENDENT SIZING HONESTY
    // ==================================================================

    @Test
    fun testEnginesBehaveConsistentlyWithAbsenceOfEquipment() {
        val circuits = createGenericCircuits()

        // 1. calculateAll on empty collections returns empty map (never fabricates default instances)
        val genAllEmpty = GeneratorEngine.calculateAll(emptyList(), emptyList(), circuits, 420.0)
        assertTrue("GeneratorEngine.calculateAll with 0 generators must return empty map", genAllEmpty.isEmpty())

        val atsAllEmpty = AtsEngine.calculateAll(emptyList(), circuits, emptyMap(), 400.0, 10.0, 420.0)
        assertTrue("AtsEngine.calculateAll with 0 ATS must return empty map", atsAllEmpty.isEmpty())

        val upsAllEmpty = UpsEngine.calculateAll(emptyList(), circuits)
        assertTrue("UpsEngine.calculateAll with 0 UPS must return empty map", upsAllEmpty.isEmpty())

        // Generator with unassigned circuits returns "No Generator Required" with null sizing (honest absence of generator demand)
        val singleGen = GeneratorEntity(generatorId = "gen-unassigned", generatorName = "Genset")
        val genResult = GeneratorEngine.calculate(singleGen, circuits, 420.0)
        assertEquals("No Generator Required", genResult.status)
        assertEquals(0.0, genResult.essentialLoadKVA, 0.001)
        assertNull("Generator with no assigned loads must have null finalSize", genResult.finalSize)
        assertNull("Generator with no assigned loads must have null loadingPct", genResult.loadingPct)

        // Generator with 0 project circuits returns SELECT_LOADS
        val genZeroCirc = GeneratorEngine.calculate(singleGen, emptyList(), 0.0)
        assertEquals("SELECT_LOADS", genZeroCirc.status)
        assertNull(genZeroCirc.finalSize)

        // ATS with 0 matching circuits
        val singleAts = AtsEntity(atsId = "ats-unassigned", atsName = "ATS")
        val atsResult = AtsEngine.calculate(singleAts, 420.0, 0.0, 0.0, 400.0, 10.0, circuits)
        assertEquals("SELECT_LOADS", atsResult.status)
        assertNull("ATS with no assigned loads must have null finalRating", atsResult.finalRating)
        assertNull("ATS with no assigned loads must have null finalLoadingPct", atsResult.finalLoadingPct)

        // UPS with 0 matching circuits
        val singleUps = UpsEntity(upsId = "ups-unassigned", upsName = "UPS")
        val upsResult = UpsEngine.calculate(singleUps, circuits)
        assertEquals("SELECT_LOADS", upsResult.status)
        assertEquals(0.0, upsResult.totalCriticalLoadKVA, 0.001)
        assertNull("UPS with no assigned loads must have null recommendedSize", upsResult.recommendedSize)
        assertNull("UPS with no assigned loads must have null loadingPct", upsResult.loadingPct)
        assertNull("UPS with no assigned loads must have null batteryAh", upsResult.batteryAh)

        // 3. Dependent sizing honesty: ATS pointing to absent generator does NOT fabricate a generator size
        val atsWithAbsentGen = AtsEntity(
            atsId = "ats-beta",
            emergencySourceId = "non-existent-gen-id"
        )
        val atsResWithAbsentGen = AtsEngine.calculateAll(
            atsList = listOf(atsWithAbsentGen),
            circuits = emptyList(),
            generatorSelectedSizes = emptyMap(), // generator absent
            transformerFLC = 200.0,
            isc3PhaseAtTransformer = 15.0,
            totalConnectedLoad = 300.0
        )
        val resAts = atsResWithAbsentGen["ats-beta"]
        assertNotNull(resAts)
        assertEquals(0.0, resAts!!.generatorFLC, 0.001)
        assertNull(resAts.finalRating)
    }

    // ==================================================================
    // 5. BOQ HONESTY (NO PHANTOM LINE ITEMS FOR ABSENT EQUIPMENT)
    // ==================================================================

    @Test
    fun testBoqContainsNoLineItemForAbsentEquipment() {
        val circuits = createGenericCircuits()
        val dummySizing = mapOf(
            101 to createDummySizingResult(size = "185", productCode = "GEN-185"),
            102 to createDummySizingResult(size = "70", productCode = "GEN-70")
        )

        // Generate BOQ
        val boqItems = BoqEngine.generateBoq(circuits, dummySizing)
        val boqResult = BoqEngine.generateBoqResult(circuits, dummySizing)

        // BOQ items must only contain cable categories from actual circuits
        assertTrue("BOQ must produce items for actual circuits", boqItems.isNotEmpty())
        for (item in boqItems) {
            // Must be cables, never equipment frames
            assertTrue("BOQ cableType must match configured circuits", item.cableType.contains("Cu/XLPE", ignoreCase = true))
            assertFalse("BOQ must not contain generator items", item.cableType.contains("GENERATOR", ignoreCase = true))
            assertFalse("BOQ must not contain ATS items", item.cableType.contains("ATS", ignoreCase = true))
            assertFalse("BOQ must not contain UPS items", item.cableType.contains("UPS", ignoreCase = true))
        }

        // Empty circuit list produces empty BOQ
        val emptyBoq = BoqEngine.generateBoq(emptyList())
        val emptyBoqResult = BoqEngine.generateBoqResult(emptyList())
        assertTrue("BOQ on empty circuits must produce 0 items", emptyBoq.isEmpty())
        assertTrue("BOQ result on empty circuits must have 0 items", emptyBoqResult.items.isEmpty())
        assertEquals(0, emptyBoqResult.kpi.totalCircuits)
        assertEquals(0.0, emptyBoqResult.kpi.totalRoute_m, 0.001)
        assertEquals(0.0, emptyBoqResult.kpi.totalInstalled_m, 0.001)
    }
}
