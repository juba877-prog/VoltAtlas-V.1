package com.example

import com.example.data.CableCatalogEntry
import com.example.data.CableType
import com.example.data.Catalog
import com.example.data.DataSheetEntry
import com.example.ui.screens.quick.VoltageDropCalculationEngine
import com.example.ui.screens.quick.VoltageDropInputState
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class VoltageDropV2ProvenanceTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun setupCatalog() {
            val assetsDir = File("src/main/assets")
            val cableTypesFile = File(assetsDir, "cable_types.json")
            val dataSheetFile = File(assetsDir, "data_sheet.json")
            val cableCatalogFile = File(assetsDir, "cable_catalog.json")

            if (cableTypesFile.exists()) {
                val array = JSONArray(cableTypesFile.readText())
                val list = mutableListOf<CableType>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        CableType(
                            code = obj.getInt("code"),
                            cableType = obj.getString("cableType"),
                            conductor = obj.getString("conductor"),
                            insulation = obj.getString("insulation"),
                            armour = obj.getString("armour"),
                            cores = obj.getString("cores"),
                            voltage = obj.getString("voltage"),
                            standard = obj.getString("standard"),
                            rColumn = obj.getString("rColumn")
                        )
                    )
                }
                Catalog.cableTypes = list
            }

            if (dataSheetFile.exists()) {
                val array = JSONArray(dataSheetFile.readText())
                val list = mutableListOf<DataSheetEntry>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        DataSheetEntry(
                            size = obj.getDouble("size"),
                            R_Cu20 = if (obj.has("R_Cu20")) obj.getDouble("R_Cu20") else obj.getDouble("R_Cu70"),
                            X = obj.getDouble("X"),
                            R_Al20 = if (obj.has("R_Al20")) obj.getDouble("R_Al20") else obj.getDouble("R_Al70"),
                            R_Cu_70_est = if (obj.has("R_Cu_70_est")) obj.getDouble("R_Cu_70_est") else obj.getDouble("R_Cu90"),
                            R_Al_70_est = if (obj.has("R_Al_70_est")) obj.getDouble("R_Al_70_est") else obj.getDouble("R_Al90")
                        )
                    )
                }
                Catalog.dataSheet = list
            }

            if (cableCatalogFile.exists()) {
                val array = JSONArray(cableCatalogFile.readText())
                val list = mutableListOf<CableCatalogEntry>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        CableCatalogEntry(
                            mfg = obj.getString("mfg"),
                            typeCode = obj.getInt("typeCode"),
                            size = obj.getDouble("size"),
                            model = obj.optString("model", "Model"),
                            gnd = obj.getDouble("gnd"),
                            duct = obj.getDouble("duct"),
                            air = obj.getDouble("air"),
                            od = obj.optDouble("od", 0.0),
                            wt = obj.optDouble("wt", 0.0)
                        )
                    )
                }
                Catalog.cableCatalog = list
            }
        }
    }

    @Test
    fun provenance_sizeChange_modifiesImpedanceAndVoltageDropDeterministically() {
        val baseInput = VoltageDropInputState(
            currentA = 100.0,
            lengthM = 50.0,
            cableType = "Cu/XLPE/PVC - 4C",
            cableSizeMm2 = 16.0
        )
        val res16 = VoltageDropCalculationEngine.calculate(baseInput)!!

        val res70 = VoltageDropCalculationEngine.calculate(baseInput.copy(cableSizeMm2 = 70.0))!!

        val ds16 = Catalog.dataSheet.first { it.size == 16.0 }
        val ds70 = Catalog.dataSheet.first { it.size == 70.0 }

        // 1. Proves R and X come directly from Catalog.dataSheet
        assertEquals(ds16.R_Cu90, res16.resolvedROhmPerKm, 0.0001)
        assertEquals(ds70.R_Cu90, res70.resolvedROhmPerKm, 0.0001)
        assertEquals(ds16.X, res16.resolvedXOhmPerKm, 0.0001)
        assertEquals(ds70.X, res70.resolvedXOhmPerKm, 0.0001)

        // 2. Proves 70mm2 has lower resistance and strictly lower voltage drop than 16mm2
        assertTrue("70mm2 R must be less than 16mm2 R", res70.resolvedROhmPerKm < res16.resolvedROhmPerKm)
        assertTrue("70mm2 ΔV must be less than 16mm2 ΔV", res70.voltageDropV < res16.voltageDropV)
        assertTrue("70mm2 ΔV% must be less than 16mm2 ΔV%", res70.voltageDropPct < res16.voltageDropPct)
    }

    @Test
    fun provenance_materialChange_modifiesResistanceColumn() {
        val cuInput = VoltageDropInputState(
            cableType = "Cu/XLPE/PVC - 4C",
            cableSizeMm2 = 50.0
        )
        val cuRes = VoltageDropCalculationEngine.calculate(cuInput)!!

        val alInput = VoltageDropInputState(
            cableType = "Al/XLPE/PVC - 4C",
            cableSizeMm2 = 50.0
        )
        val alRes = VoltageDropCalculationEngine.calculate(alInput)!!

        val ds50 = Catalog.dataSheet.first { it.size == 50.0 }

        assertEquals("Copper XLPE must resolve R_Cu90", ds50.R_Cu90, cuRes.resolvedROhmPerKm, 0.0001)
        assertEquals("Aluminium XLPE must resolve R_Al90", ds50.R_Al_90(), alRes.resolvedROhmPerKm, 0.0001)
        assertTrue("Aluminium 50mm2 must have higher resistance than Copper 50mm2", alRes.resolvedROhmPerKm > cuRes.resolvedROhmPerKm)
    }

    @Test
    fun provenance_metadataReportsCatalogSource() {
        val input = VoltageDropInputState(
            cableType = "Cu/XLPE/PVC - 4C",
            cableSizeMm2 = 35.0,
            isManualOverride = false
        )
        val res = VoltageDropCalculationEngine.calculate(input)!!

        assertEquals("Data source must state Catalog", "Catalog (data_sheet.json)", res.dataSource)
        assertFalse("isManualOverride must be false", res.isManualOverride)
        assertTrue("Citation must cite IEC 60364-5-52", res.provenanceCitation.contains("IEC 60364-5-52"))
    }

    private fun DataSheetEntry.R_Al_90(): Double = this.R_Al90
}
