package com.example.sld

import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.data.GeneratorEntity
import com.example.data.AtsEntity
import com.example.data.BusTieEntity
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SldDxfRealWorldExportTest {

    @Test
    fun testRealWorldProjectDxfExport() {
        val projId = "REAL_PROJECT_01"

        val transformers = listOf(
            TransformerEntity(
                projectId = projId,
                transformerId = "TX-01",
                transformerName = "Main Substation TX",
                kVA = 1500.0,
                voltage = 400,
                percentZ = 5.75,
                vectorGroup = "Dyn11"
            )
        )

        val circuits = listOf(
            CircuitEntity(
                id = 1,
                projectId = projId,
                circuitName = "MDB-MAIN",
                loadKVA = 1200.0,
                panelType = "MDB",
                fedFrom = "Transformer",
                transformerId = "TX-01",
                length_m = 25.0
            ),
            CircuitEntity(
                id = 2,
                projectId = projId,
                circuitName = "SMDB-HVAC",
                loadKVA = 350.0,
                panelType = "SMDB",
                parentId = 1,
                fedFrom = "MDB-MAIN",
                length_m = 65.0
            ),
            CircuitEntity(
                id = 3,
                projectId = projId,
                circuitName = "SMDB-LIGHTING",
                loadKVA = 180.0,
                panelType = "SMDB",
                parentId = 1,
                fedFrom = "MDB-MAIN",
                length_m = 40.0
            ),
            CircuitEntity(
                id = 4,
                projectId = projId,
                circuitName = "DB-FLOOR-1",
                loadKVA = 45.0,
                panelType = "DB",
                parentId = 3,
                fedFrom = "SMDB-LIGHTING",
                length_m = 30.0
            ),
            CircuitEntity(
                id = 5,
                projectId = projId,
                circuitName = "MCC-CHILLERS",
                loadKVA = 250.0,
                panelType = "MCC",
                parentId = 2,
                fedFrom = "SMDB-HVAC",
                length_m = 50.0
            )
        )

        val generators = listOf(
            GeneratorEntity(
                projectId = projId,
                generatorId = "GEN-01",
                generatorName = "Emergency Genset",
                manualSizeKVA = 800.0
            )
        )

        val atsList = listOf(
            AtsEntity(
                projectId = projId,
                atsId = "ATS-01",
                atsName = "Automatic Transfer Switch",
                manualSizeAmp = 1250.0
            )
        )

        val busTies = listOf(
            BusTieEntity(
                projectId = projId,
                busTieId = "TIE-01",
                sourceBoardId = 1L,
                targetBoardId = 2L,
                breakerRatingA = 630.0
            )
        )

        val layoutResult = SldLayoutEngine.calculateLayout(
            activeProjectId = projId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList,
            busTies = busTies
        )

        val dxfContent = SldDxfExporter.generateDxf(layoutResult, "Real World Substation")
        val outDir = File("build/test-output").apply { mkdirs() }
        val outFile = File(outDir, "real_world_export.dxf")
        outFile.outputStream().use { out ->
            SldDxfExporter.writeDxfToStream(dxfContent, out)
        }

        println("[TEST] Exported real world DXF: ${outFile.absolutePath} (${outFile.length()} bytes)")
        assertTrue(outFile.exists())
        assertTrue(outFile.length() > 1000)
    }
}
