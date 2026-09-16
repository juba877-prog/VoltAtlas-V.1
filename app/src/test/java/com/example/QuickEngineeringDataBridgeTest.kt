package com.example

import com.example.data.CableCatalogEntry
import com.example.data.CableType
import com.example.data.Catalog
import com.example.data.DataSheetEntry
import com.example.engine.quick.QuickEngineeringDataBridge
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class QuickEngineeringDataBridgeTest {

    companion object {
        @JvmField
        @org.junit.ClassRule
        val catalogStateReset = com.example.testing.CatalogStateResetRule()

        @JvmStatic
        @BeforeClass
        fun setupCatalog() {
            // Load real assets if not already populated
            val assetsDir = File("src/main/assets")
            val cableTypesFile = File(assetsDir, "cable_types.json")
            val dataSheetFile = File(assetsDir, "data_sheet.json")
            val cableCatalogFile = File(assetsDir, "cable_catalog.json")
            val deratingDataFile = File(assetsDir, "derating_data.json")

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

            if (deratingDataFile.exists()) {
                Catalog.deratingData = JSONObject(deratingDataFile.readText())
            }
        }
    }

    @Test
    fun bridge_resolvesCuXLPE_mapsToRCu90() {
        val resolved = QuickEngineeringDataBridge.resolveCableImpedance(
            sizeMm2 = 35.0,
            cableType = "Cu/XLPE/PVC - 4C"
        )
        assertNotNull("Impedance for 35mm2 Cu/XLPE must resolve", resolved)
        assertEquals("Must use R_Cu90 (90°C)", "R_Cu90 (90°C)", resolved!!.rColumnUsed)
        assertTrue("R must be > 0", resolved.rOhmPerKm > 0.0)
        assertTrue("X must be > 0", resolved.xOhmPerKm > 0.0)

        // Verify against dataSheet entry directly
        val ds = Catalog.dataSheet.first { it.size == 35.0 }
        assertEquals(ds.R_Cu90, resolved.rOhmPerKm, 0.0001)
        assertEquals(ds.X, resolved.xOhmPerKm, 0.0001)
    }

    @Test
    fun bridge_resolvesCuPVC_mapsToRCu70() {
        val resolved = QuickEngineeringDataBridge.resolveCableImpedance(
            sizeMm2 = 50.0,
            cableType = "Cu/PVC/PVC - 4C"
        )
        assertNotNull("Impedance for 50mm2 Cu/PVC must resolve", resolved)
        assertEquals("Must use R_Cu70 (70°C)", "R_Cu70 (70°C)", resolved!!.rColumnUsed)

        val ds = Catalog.dataSheet.first { it.size == 50.0 }
        assertEquals(ds.R_Cu70, resolved.rOhmPerKm, 0.0001)
    }

    @Test
    fun bridge_resolvesAlXLPE_mapsToRAl90() {
        val resolved = QuickEngineeringDataBridge.resolveCableImpedance(
            sizeMm2 = 95.0,
            cableType = "Al/XLPE/PVC - 4C"
        )
        assertNotNull("Impedance for 95mm2 Al/XLPE must resolve", resolved)
        assertEquals("Must use R_Al90 (90°C)", "R_Al90 (90°C)", resolved!!.rColumnUsed)

        val ds = Catalog.dataSheet.first { it.size == 95.0 }
        assertEquals(ds.R_Al90, resolved.rOhmPerKm, 0.0001)
    }

    @Test
    fun bridge_resolvesBaseAmpacity_fromCableCatalog() {
        val ampacity = QuickEngineeringDataBridge.resolveBaseAmpacity(
            sizeMm2 = 35.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air"
        )
        assertNotNull("Base ampacity for Elsewedy 35mm2 in Free Air must resolve", ampacity)
        assertTrue("Ampacity must be > 50A", ampacity!! > 50.0)
    }

    @Test
    fun bridge_handlesNonExistentSize_returnsNullWithoutCrash() {
        val resolved = QuickEngineeringDataBridge.resolveCableImpedance(
            sizeMm2 = 9999.0,
            cableType = "Cu/XLPE/PVC - 4C"
        )
        assertNull("Non-existent size 9999mm2 must return null", resolved)

        val ampacity = QuickEngineeringDataBridge.resolveBaseAmpacity(
            sizeMm2 = 9999.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air"
        )
        assertNull("Non-existent size ampacity must return null", ampacity)
    }

    @Test
    fun bridge_manufacturersAndFamilies_matchAuthoritativeCatalog() {
        val mfgs = QuickEngineeringDataBridge.getAvailableManufacturers()
        assertTrue("Manufacturers must contain Elsewedy", mfgs.contains("Elsewedy"))
        assertTrue("Manufacturers must contain Riyadh Cables", mfgs.contains("Riyadh Cables"))
        assertTrue("Manufacturers must contain Ducab", mfgs.contains("Ducab"))

        val elsewedyFamilies = QuickEngineeringDataBridge.getAvailableFamilies("Elsewedy")
        assertTrue("Elsewedy families must not be empty", elsewedyFamilies.isNotEmpty())

        val elsewedySizes = QuickEngineeringDataBridge.getAvailableSizes("Elsewedy", elsewedyFamilies.first())
        assertTrue("Elsewedy sizes must not be empty", elsewedySizes.isNotEmpty())
        assertTrue("Elsewedy sizes must contain standard sizes", elsewedySizes.contains(25.0) || elsewedySizes.contains(35.0))
    }
}
