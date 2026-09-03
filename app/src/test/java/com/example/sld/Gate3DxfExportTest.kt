package com.example.sld

import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Gate3DxfExportTest {

    @Test
    fun testGate3Export() {
        val jsonFile = File("Gate_3_20260816_repaired.json").takeIf { it.exists() }
            ?: File("../Gate_3_20260816_repaired.json")
        val jsonStr = jsonFile.readText()
        val root = JSONObject(jsonStr)

        val projObj = root.getJSONObject("project")
        val projId = projObj.getString("projectId")
        val projName = projObj.getString("projectName")

        val txArray = root.getJSONArray("transformers")
        val transformers = mutableListOf<TransformerEntity>()
        for (i in 0 until txArray.length()) {
            val obj = txArray.getJSONObject(i)
            transformers.add(
                TransformerEntity(
                    projectId = projId,
                    transformerId = obj.getString("transformerId"),
                    transformerName = obj.optString("transformerName", "Transformer 1"),
                    kVA = obj.optDouble("kVA", 100.0),
                    voltage = obj.optInt("voltage", 400),
                    percentZ = obj.optDouble("percentZ", 6.0),
                    vectorGroup = obj.optString("vectorGroup", "Dyn11")
                )
            )
        }

        val circArray = root.getJSONArray("circuits")
        val circuits = mutableListOf<CircuitEntity>()
        for (i in 0 until circArray.length()) {
            val obj = circArray.getJSONObject(i)
            circuits.add(
                CircuitEntity(
                    id = obj.getInt("id"),
                    projectId = projId,
                    circuitName = obj.getString("circuitName"),
                    loadKVA = obj.optDouble("loadKVA", 10.0),
                    fedFrom = obj.optString("fedFrom", ""),
                    transformerId = obj.optString("transformerId", "TX-01"),
                    length_m = obj.optDouble("length_m", 20.0),
                    panelType = obj.optString("panelType", "DB"),
                    parentId = if (obj.has("parentId") && !obj.isNull("parentId")) obj.getInt("parentId") else null
                )
            )
        }

        val layoutResult = SldLayoutEngine.calculateLayout(
            activeProjectId = projId,
            transformers = transformers,
            circuits = circuits
        )

        val dxf = SldDxfExporter.generateDxf(layoutResult, projName)
        val outFile = File("build/test-output/gate3_exported.dxf")
        outFile.parentFile?.mkdirs()
        outFile.outputStream().use { out ->
            SldDxfExporter.writeDxfToStream(dxf, out)
        }

        println("[GATE3] Exported DXF to ${outFile.absolutePath} (${outFile.length()} bytes)")
        assertTrue(outFile.exists())
    }
}
