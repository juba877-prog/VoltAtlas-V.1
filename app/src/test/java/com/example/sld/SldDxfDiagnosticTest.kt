package com.example.sld

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.Charset

/**
 * Phase 3 Forensic Diagnostic — DXF Export Failure Investigation.
 *
 * 1. Generates a REAL DXF from a representative VoltAtlas topology.
 * 2. Writes it to disk using the EXACT production byte path
 *    (SldDxfExporter.writeDxfToStream -> windows-1252) so the physical
 *    bytes can be independently audited (e.g. with ezdxf / ODA).
 * 3. Re-parses the physical file line-by-line enforcing the AutoCAD ASCII DXF
 *    grammar the way strict CAD parsers (AutoCAD/ODA/ezdxf audit) do.
 */
class SldDxfDiagnosticTest {

    private data class GroupPair(val code: Int, val value: String, val lineNo: Int)

    private fun createSampleEngineResult(): SldEngineResult {
        val txNode = PositionedSldNode(
            id = "TX-01",
            label = "TX-01 1000kVA",
            panelType = "TRANSFORMER",
            ratingText = "22kV / 400V",
            statusColorHex = "#38BDF8",
            x = 200f, y = 100f, width = 140f, height = 70f,
            isSource = true
        )
        val genNode = PositionedSldNode(
            id = "GEN-01",
            label = "GEN-01 500kVA",
            panelType = "GENERATOR",
            ratingText = "500 kVA 400V",
            statusColorHex = "#38BDF8",
            x = 700f, y = 100f, width = 140f, height = 70f,
            isSource = true
        )
        val atsNode = PositionedSldNode(
            id = "ATS-01",
            label = "ATS-01",
            panelType = "ATS",
            ratingText = "630A 4P • Interlocked",
            statusColorHex = "#F59E0B",
            x = 700f, y = 400f, width = 120f, height = 60f,
            isSource = true
        )
        val upsNode = PositionedSldNode(
            id = "UPS-01",
            label = "UPS-01",
            panelType = "UPS",
            ratingText = "60 kVA",
            statusColorHex = "#F59E0B",
            x = 950f, y = 400f, width = 120f, height = 60f,
            isSource = true
        )
        val mdbNode = PositionedSldNode(
            id = "MDB-1",
            label = "MDB-1 Main Switchboard",
            panelType = "MDB",
            ratingText = "2500A 3P 50kA",
            statusColorHex = "#10B981",
            x = 100f, y = 400f, width = 600f, height = 24f,
            isSource = false
        )
        val smdb1Node = PositionedSldNode(
            id = "SMDB-1",
            label = "SMDB-1 Essential",
            panelType = "SMDB",
            ratingText = "400A",
            statusColorHex = "#10B981",
            x = 200f, y = 700f, width = 140f, height = 36f,
            isSource = false,
            dataBlock = SldDataBlock(
                deviceTypeAndRating = "MCCB 400A 3P",
                cableDescription = "4x(1x185 mm²) Cu/XLPE",
                parallelRuns = 1,
                lengthM = "35",
                voltageDropPct = "0.85%",
                faultLevelKa = "25.0 kA"
            )
        )
        val smdb2Node = PositionedSldNode(
            id = "SMDB-2",
            label = "SMDB-2 Lighting Ø",
            panelType = "SMDB",
            ratingText = "250A",
            statusColorHex = "#10B981",
            x = 500f, y = 700f, width = 140f, height = 36f,
            isSource = false
        )
        val tieNode = PositionedSldNode(
            id = "TIE-01",
            label = "BUS TIE",
            panelType = "BUS_TIE",
            ratingText = "1250A",
            statusColorHex = "#10B981",
            x = 400f, y = 850f, width = 100f, height = 20f,
            isSource = false
        )
        val normalEdge = PositionedSldEdge(
            fromId = "TX-01", toId = "MDB-1", label = "Incomer",
            pathPoints = listOf(270f to 170f, 270f to 400f),
            isEmergency = false
        )
        val emergencyEdge = PositionedSldEdge(
            fromId = "GEN-01", toId = "ATS-01", label = "EMERGENCY",
            pathPoints = listOf(770f to 170f, 770f to 380f),
            isEmergency = true
        )
        val feederEdge = PositionedSldEdge(
            fromId = "MDB-1", toId = "SMDB-1", label = "Feeder",
            pathPoints = listOf(270f to 424f, 270f to 500f, 270f to 700f),
            isEmergency = false
        )
        // Crossing edge vs feederEdge -> forces DXF ARC hop bridges
        val crossingEdge = PositionedSldEdge(
            fromId = "MDB-1", toId = "SMDB-2", label = "Feeder B",
            pathPoints = listOf(100f to 600f, 570f to 600f),
            isEmergency = false
        )
        return SldEngineResult(
            nodes = listOf(txNode, genNode, atsNode, upsNode, mdbNode, smdb1Node, smdb2Node, tieNode),
            edges = listOf(normalEdge, emergencyEdge, feederEdge, crossingEdge),
            tierDatums = listOf(
                TierDatumLine(label = "Tier 0 (Sources)", y = 100f, tierIndex = 0),
                TierDatumLine(label = "Tier 1 (Main MV/LV)", y = 400f, tierIndex = 1),
                TierDatumLine(label = "Tier 2 (Sub-Distribution)", y = 700f, tierIndex = 2)
            ),
            bounds = Rect(0f, 0f, 1200f, 900f)
        )
    }

    private fun readPhysicalDxfBytes(): Pair<ByteArray, String> {
        val dxfContent = SldDxfExporter.generateDxf(createSampleEngineResult(), "Diagnostic Alfa")
        val outDir = File("build/test-output").apply { mkdirs() }
        val outFile = File(outDir, "diagnostic.dxf")
        // EXACT production write path (windows-1252 byte encoding)
        outFile.outputStream().use { out -> SldDxfExporter.writeDxfToStream(dxfContent, out) }
        val bytes = outFile.readBytes()
        return Pair(bytes, dxfContent)
    }

    private fun tokenize(rawText: String): List<GroupPair> {
        val lines = rawText.split("\n")
        val pairs = mutableListOf<GroupPair>()
        var i = 0
        while (i < lines.size) {
            val codeLine = lines[i]
            // Optional final empty line after trailing \n is legal
            if (i == lines.size - 1 && codeLine.isEmpty()) break
            val code = codeLine.trim().toIntOrNull()
            assertTrue(
                "CAD parser failure: expected integer group code at physical line ${i + 1}, found: '$codeLine'",
                code != null
            )
            assertTrue("Group code out of DXF range at line ${i + 1}: $code", code in 0..999)
            val valueLine = lines.getOrNull(i + 1)
            assertTrue(
                "CAD parser failure: truncated file — group code $code at line ${i + 1} has no value line",
                valueLine != null
            )
            pairs.add(GroupPair(code!!, valueLine!!, i + 1))
            i += 2
        }
        return pairs
    }

    private fun sectionPairs(pairs: List<GroupPair>, sectionName: String): List<GroupPair> {
        var inTarget = false
        val out = mutableListOf<GroupPair>()
        var idx = 0
        while (idx < pairs.size) {
            val p = pairs[idx]
            if (p.code == 0 && p.value == "SECTION") {
                val name = pairs.getOrNull(idx + 1)
                inTarget = name != null && name.code == 2 && name.value == sectionName
            } else if (p.code == 0 && p.value == "ENDSEC") {
                if (inTarget) { inTarget = false; break }
            } else if (inTarget) {
                out.add(p)
            }
            idx++
        }
        return out
    }

    // =====================================================================
    // Phase 3.1 — Reproduction: generate + persist the physical file
    // =====================================================================
    @Test
    fun phase3_generateRealDiagnosticFile() {
        val (bytes, content) = readPhysicalDxfBytes()
        val outFile = File("build/test-output/diagnostic.dxf")
        assertTrue("Diagnostic DXF must be written to disk", outFile.exists())
        assertTrue("Diagnostic DXF must be non-empty", outFile.length() > 0L)
        assertEquals(
            "windows-1252 byte path must not change byte count (1 char = 1 byte)",
            content.length.toLong(), bytes.size.toLong()
        )
        println("[DIAGNOSTIC] diagnostic.dxf written: ${outFile.absolutePath} (${bytes.size} bytes)")
    }

    // =====================================================================
    // Phase 3.2 — Strict alternating group code / value pair grammar
    // =====================================================================
    @Test
    fun phase3_strictAlternatingGroupCodeValuePairs() {
        val (bytes, _) = readPhysicalDxfBytes()
        val rawText = String(bytes, Charset.forName("windows-1252"))
        assertFalse("Physical file must not contain byte-level corruption (0x00)", bytes.contains(0))

        // File must terminate with the 0/EOF pair
        val trimmed = rawText.trimEnd('\n')
        assertTrue("File must end with 0/EOF pair", trimmed.endsWith("0\nEOF"))

        // Every even physical line must be an integer group code (strict CAD tokenizer)
        val pairs = tokenize(rawText)
        assertTrue("Token stream must not be empty", pairs.isNotEmpty())
        println("[DIAGNOSTIC] Tokenized ${pairs.size} group code/value pairs")
    }

    // =====================================================================
    // Phase 3.3 — Section structure: HEADER, TABLES, BLOCKS, ENTITIES, EOF
    // =====================================================================
    @Test
    fun phase3_sectionStructureIsValid() {
        val (bytes, _) = readPhysicalDxfBytes()
        val pairs = tokenize(String(bytes, Charset.forName("windows-1252")))

        val sections = mutableListOf<String>()
        var idx = 0
        while (idx < pairs.size - 1) {
            val p = pairs[idx]
            if (p.code == 0 && p.value == "SECTION") {
                val name = pairs[idx + 1]
                assertTrue("SECTION must be named with code 2", name.code == 2)
                sections.add(name.value)
                idx += 2
            } else {
                idx++
            }
        }
        assertEquals(
            "DXF must contain exactly HEADER, TABLES, BLOCKS, ENTITIES sections in canonical order",
            listOf("HEADER", "TABLES", "BLOCKS", "ENTITIES"), sections
        )
    }

    // =====================================================================
    // Phase 3.4 — TABLES audit: counts, mandatory Layer "0", STYLE/STANDARD
    // =====================================================================
    @Test
    fun phase3_layerTable_containsMandatoryLayer0() {
        val (bytes, _) = readPhysicalDxfBytes()
        val tables = sectionPairs(tokenize(String(bytes, Charset.forName("windows-1252"))), "TABLES")

        val layerNames = mutableListOf<String>()
        var i = 0
        while (i < tables.size) {
            val p = tables[i]
            if (p.code == 0 && p.value == "TABLE" && tables[i + 1].value == "LAYER") {
                var j = i + 2
                while (j < tables.size && !(tables[j].code == 0 && tables[j].value == "ENDTAB")) {
                    if (tables[j].code == 0 && tables[j].value == "LAYER") {
                        layerNames.add(tables[j + 1].value)
                    }
                    j++
                }
            }
            i++
        }
        assertTrue(
            "DXF spec violation: LAYER table does not contain MANDATORY default layer \"0\". " +
                "Layers found: $layerNames. The DXF specification requires layer 0 to exist; " +
                "strict CAD parsers reject drawings whose LAYER table lacks layer 0.",
            layerNames.contains("0")
        )
    }

    @Test
    fun phase3_styleTable_definesStandardStyleForTextEntities() {
        val (bytes, _) = readPhysicalDxfBytes()
        val pairs = tokenize(String(bytes, Charset.forName("windows-1252")))
        val tables = sectionPairs(pairs, "TABLES")
        val entities = sectionPairs(pairs, "ENTITIES")

        val textCount = entities.count { it.code == 0 && it.value == "TEXT" }
        if (textCount == 0) return

        var styleTableFound = false
        val styleNames = mutableListOf<String>()
        var i = 0
        while (i < tables.size) {
            val p = tables[i]
            if (p.code == 0 && p.value == "TABLE" && tables[i + 1].value == "STYLE") {
                styleTableFound = true
                var j = i + 2
                while (j < tables.size && !(tables[j].code == 0 && tables[j].value == "ENDTAB")) {
                    if (tables[j].code == 0 && tables[j].value == "STYLE") {
                        styleNames.add(tables[j + 1].value)
                    }
                    j++
                }
            }
            i++
        }
        assertTrue(
            "DXF spec violation: $textCount TEXT entities are emitted but the TABLES section contains " +
                "NO STYLE table. TEXT entities implicitly reference the \"STANDARD\" text style; " +
                "strict CAD parsers fail on undefined text styles. Styles found: $styleNames",
            styleTableFound && styleNames.contains("STANDARD")
        )
    }

    @Test
    fun phase3_tableCounts_matchActualEntries() {
        val (bytes, _) = readPhysicalDxfBytes()
        val tables = sectionPairs(tokenize(String(bytes, Charset.forName("windows-1252"))), "TABLES")

        var i = 0
        while (i < tables.size) {
            val p = tables[i]
            if (p.code == 0 && p.value == "TABLE") {
                val tableName = tables[i + 1].value
                val declaredCount = tables.drop(i).firstOrNull { it.code == 70 }?.value?.toIntOrNull()
                var entries = 0
                var j = i + 2
                while (j < tables.size && !(tables[j].code == 0 && tables[j].value == "ENDTAB")) {
                    if (tables[j].code == 0 && tables[j].value == tableName) entries++
                    j++
                }
                assertEquals(
                    "TABLE '$tableName' declared count (70=$declaredCount) must match actual entries ($entries)",
                    entries, declaredCount
                )
            }
            i++
        }
    }

    // =====================================================================
    // Phase 3.5 — LTYPE audit: dash pattern total length must equal code 40
    // =====================================================================
    @Test
    fun phase3_ltypePatternLengths_matchDashSegmentSum() {
        val (bytes, _) = readPhysicalDxfBytes()
        val tables = sectionPairs(tokenize(String(bytes, Charset.forName("windows-1252"))), "TABLES")

        var i = 0
        var checked = 0
        while (i < tables.size) {
            val p = tables[i]
            if (p.code == 0 && p.value == "LTYPE") {
                val name = tables[i + 1].value
                var totalLength = 0f
                var dashSum = 0f
                var j = i + 2
                while (j < tables.size && tables[j].code != 0) {
                    when (tables[j].code) {
                        40 -> totalLength = tables[j].value.toFloatOrNull() ?: 0f
                        49 -> dashSum += Math.abs(tables[j].value.toFloatOrNull() ?: 0f)
                    }
                    j++
                }
                if (dashSum > 0f) {
                    assertEquals(
                        "LTYPE '$name' total pattern length (40=$totalLength) must equal sum of dash " +
                            "segments (49 sum=$dashSum). Strict CAD parsers reject mismatched dash patterns.",
                        dashSum, totalLength, 0.001f
                    )
                    checked++
                }
            }
            i++
        }
        assertTrue("Diagnostic must have audited DASHED and CENTER linetypes", checked >= 2)
    }

    // =====================================================================
    // Phase 3.6 — Entity referential integrity + coordinate sanity
    // =====================================================================
    @Test
    fun phase3_entityReferences_resolveToTables_andCoordinatesFinite() {
        val (bytes, _) = readPhysicalDxfBytes()
        val pairs = tokenize(String(bytes, Charset.forName("windows-1252")))
        val tables = sectionPairs(pairs, "TABLES")
        val entities = sectionPairs(pairs, "ENTITIES")

        val layers = mutableSetOf<String>()
        val ltypes = mutableSetOf<String>()
        var i = 0
        while (i < tables.size) {
            when {
                tables[i].code == 0 && tables[i].value == "LAYER" -> layers.add(tables[i + 1].value)
                tables[i].code == 0 && tables[i].value == "LTYPE" -> ltypes.add(tables[i + 1].value)
            }
            i++
        }

        entities.forEach { p ->
            when (p.code) {
                8 -> assertTrue(
                    "Entity references undefined layer '${p.value}' — CAD parser fails referential lookup",
                    layers.contains(p.value)
                )
                6 -> assertTrue(
                    "Entity references undefined linetype '${p.value}' — CAD parser fails referential lookup",
                    ltypes.contains(p.value)
                )
                10, 11, 20, 21, 30, 31, 40, 50, 51 -> {
                    val v = p.value.toFloatOrNull()
                    assertTrue(
                        "Coordinate/angle value '${p.value}' (code ${p.code}) is not a valid finite float",
                        v != null && v.isFinite()
                    )
                }
            }
        }
    }
}