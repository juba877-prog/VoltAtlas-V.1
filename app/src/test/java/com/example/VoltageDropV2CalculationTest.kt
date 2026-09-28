package com.example

import com.example.data.CableCatalogEntry
import com.example.data.CableType
import com.example.data.Catalog
import com.example.data.DataSheetEntry
import com.example.engine.converters.ElectricalConverters
import com.example.ui.screens.quick.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

class VoltageDropV2CalculationTest {

    companion object {
        @JvmField
        @org.junit.ClassRule
        val catalogStateReset = com.example.testing.CatalogStateResetRule()

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
    fun testThreePhaseCalculation_exactParityWithConverters() {
        val input = VoltageDropInputState(
            inputMode = VoltageDropInputMode.CURRENT,
            phase = VoltageDropSystemPhase.THREE_PHASE,
            voltageV = 400.0,
            currentA = 100.0,
            lengthM = 50.0,
            powerFactor = 0.85,
            parallelRuns = 1,
            maxVdLimitPct = 3.0,
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/PVC - 4C",
            cableSizeMm2 = 35.0
        )

        val result = VoltageDropCalculationEngine.calculate(input)
        assertNotNull("Result must not be null", result)

        val ds = Catalog.dataSheet.first { it.size == 35.0 }
        val rawExpected = ElectricalConverters.cableVoltageDrop(
            rOhmPerKm = ds.R_Cu90,
            xOhmPerKm = ds.X,
            amps = 100.0,
            lengthM = 50.0,
            cosPhi = 0.85,
            isThreePhase = true,
            voltageV = 400.0
        )

        assertEquals("mV/A/m must match ElectricalConverters", rawExpected.mVperAm, result!!.mVperAm, 0.0001)
        assertEquals("vDrop must match ElectricalConverters", rawExpected.vDrop, result.voltageDropV, 0.0001)
        assertEquals("vDropPct must match ElectricalConverters", rawExpected.vDropPct, result.voltageDropPct, 0.0001)
        assertEquals("Receiving voltage must be 400 - vDrop", 400.0 - rawExpected.vDrop, result.receivingVoltageV, 0.0001)
    }

    @Test
    fun testSinglePhaseCalculation_usesTwoWireFactor() {
        val input = VoltageDropInputState(
            inputMode = VoltageDropInputMode.CURRENT,
            phase = VoltageDropSystemPhase.SINGLE_PHASE,
            voltageV = 230.0,
            currentA = 40.0,
            lengthM = 30.0,
            powerFactor = 0.90,
            parallelRuns = 1,
            maxVdLimitPct = 3.0,
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/PVC - 4C",
            cableSizeMm2 = 16.0
        )

        val result = VoltageDropCalculationEngine.calculate(input)
        assertNotNull("1-Phase calculation must produce result", result)
        assertFalse("isThreePhase must be false", result!!.isThreePhase)

        val ds = Catalog.dataSheet.first { it.size == 16.0 }
        val rawExpected = ElectricalConverters.cableVoltageDrop(
            rOhmPerKm = ds.R_Cu90,
            xOhmPerKm = ds.X,
            amps = 40.0,
            lengthM = 30.0,
            cosPhi = 0.90,
            isThreePhase = false,
            voltageV = 230.0
        )

        assertEquals("1-Phase mV/A/m must match", rawExpected.mVperAm, result.mVperAm, 0.0001)
        assertEquals("1-Phase vDrop must match", rawExpected.vDrop, result.voltageDropV, 0.0001)
    }

    @Test
    fun testPowerMode_derivesCurrentAuthoritatively() {
        val pKw = 55.0
        val pf = 0.85
        val v = 400.0

        val input = VoltageDropInputState(
            inputMode = VoltageDropInputMode.POWER,
            phase = VoltageDropSystemPhase.THREE_PHASE,
            voltageV = v,
            powerKw = pKw,
            powerFactor = pf,
            lengthM = 60.0,
            parallelRuns = 1,
            cableSizeMm2 = 50.0
        )

        val result = VoltageDropCalculationEngine.calculate(input)
        assertNotNull("Power mode calculation must succeed", result)
        assertTrue("isCurrentDerived must be true", result!!.isCurrentDerived)

        val expectedAmps = (pKw * 1000.0) / (sqrt(3.0) * v * pf)
        assertEquals("Derived current must equal formula", expectedAmps, result.effectiveOperatingCurrentA, 0.01)
    }

    @Test
    fun testParallelRuns_halvesVoltageDrop() {
        val input1 = VoltageDropInputState(
            currentA = 200.0,
            lengthM = 100.0,
            parallelRuns = 1,
            cableSizeMm2 = 70.0
        )
        val res1 = VoltageDropCalculationEngine.calculate(input1)!!

        val input2 = input1.copy(parallelRuns = 2)
        val res2 = VoltageDropCalculationEngine.calculate(input2)!!

        assertEquals("Current per run with N=2 must be half", 100.0, res2.currentPerRunA, 0.001)
        assertEquals("Voltage drop with N=2 must be half of N=1", res1.voltageDropV / 2.0, res2.voltageDropV, 0.01)
        assertEquals("Percentage drop with N=2 must be half of N=1", res1.voltageDropPct / 2.0, res2.voltageDropPct, 0.01)
    }

    @Test
    fun testComplianceCheck_boundariesAndMargins() {
        // Compliant case: limit = 3.0%, drop = ~1.4%
        val passInput = VoltageDropInputState(
            currentA = 80.0,
            lengthM = 40.0,
            maxVdLimitPct = 3.0,
            cableSizeMm2 = 35.0
        )
        val passRes = VoltageDropCalculationEngine.calculate(passInput)!!
        assertEquals("Status must be PASS", VoltageDropComplianceStatus.PASS, passRes.vdCheck.status)
        assertTrue("Margin must be positive", passRes.vdCheck.detailText.contains("+"))

        // Failing case: limit = 1.0%, drop = ~2.5%
        val failInput = VoltageDropInputState(
            currentA = 150.0,
            lengthM = 80.0,
            maxVdLimitPct = 1.0,
            cableSizeMm2 = 25.0
        )
        val failRes = VoltageDropCalculationEngine.calculate(failInput)!!
        assertEquals("Status must be FAIL", VoltageDropComplianceStatus.FAIL, failRes.vdCheck.status)
        assertTrue("Detail text must indicate negative margin or exceedance", failRes.vdCheck.summaryText.contains("exceeds"))
    }

    @Test
    fun testAmpacityCheck_overloadWarning() {
        // 16 mm2 Elsewedy Cu/XLPE in Free Air has Iz around 80-100 A
        val overloadedInput = VoltageDropInputState(
            currentA = 250.0,
            cableSizeMm2 = 16.0,
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/PVC - 4C",
            installationMethod = "Laid in Free Air"
        )
        val result = VoltageDropCalculationEngine.calculate(overloadedInput)!!
        assertNotNull("Ampacity check must be present", result.ampacityCheck)
        assertEquals("Overloaded current must trigger WARNING", VoltageDropComplianceStatus.WARNING, result.ampacityCheck!!.status)
        assertTrue("Summary must state overload", result.ampacityCheck!!.summaryText.contains("Overload"))
    }

    /**
     * N1 downstream: an unsupported installation method must surface an explicit non-PASS result.
     * The check may never be dropped from the result, and no Air/Duct/Buried value may be
     * fabricated in its place.
     */
    @Test
    fun testAmpacityCheck_unsupportedInstallation_surfacesDataMissing() {
        val input = VoltageDropInputState(
            currentA = 100.0,
            lengthM = 50.0,
            cableSizeMm2 = 35.0,
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/PVC - 4C",
            installationMethod = "In Conduit"
        )

        val result = VoltageDropCalculationEngine.calculate(input)!!
        assertNotNull("Ampacity check must never be omitted silently", result.ampacityCheck)

        val check = result.ampacityCheck!!
        assertEquals(
            "Unresolved ampacity must be DATA MISSING",
            VoltageDropComplianceStatus.DATA_MISSING,
            check.status
        )
        assertNotEquals(
            "Never PASS when the base ampacity is unresolved",
            VoltageDropComplianceStatus.PASS,
            check.status
        )
        assertTrue(
            "Summary must carry the DATA MISSING wording",
            check.summaryText.startsWith("DATA MISSING")
        )
        assertTrue(
            "Summary must name the offending installation method",
            check.summaryText.contains("'In Conduit'")
        )
        assertTrue("Detail must explain the supported bases", check.detailText.isNotBlank())
        assertNull("No fabricated ampacity value may be published", result.baseAmpacityA)
    }

    @Test
    fun testManualOverride_usesSuppliedImpedanceAndLabelsDataSource() {
        val overrideInput = VoltageDropInputState(
            isManualOverride = true,
            manualROhmPerKm = 0.500,
            manualXOhmPerKm = 0.090,
            currentA = 100.0,
            lengthM = 50.0
        )

        val result = VoltageDropCalculationEngine.calculate(overrideInput)!!
        assertEquals("R must be manual R", 0.500, result.resolvedROhmPerKm, 0.0001)
        assertEquals("X must be manual X", 0.090, result.resolvedXOhmPerKm, 0.0001)
        assertEquals("DataSource must indicate MANUAL OVERRIDE", "MANUAL OVERRIDE", result.dataSource)
        assertTrue("isManualOverride must be true", result.isManualOverride)
    }

    @Test
    fun testInvalidInputs_returnsNullSafely() {
        val zeroVoltInput = VoltageDropInputState(voltageV = 0.0)
        assertNull("Zero voltage must return null", VoltageDropCalculationEngine.calculate(zeroVoltInput))

        val zeroLenInput = VoltageDropInputState(lengthM = 0.0)
        assertNull("Zero length must return null", VoltageDropCalculationEngine.calculate(zeroLenInput))

        val zeroRunsInput = VoltageDropInputState(parallelRuns = 0)
        assertNull("Zero parallel runs must return null", VoltageDropCalculationEngine.calculate(zeroRunsInput))

        val zeroCurrentInput = VoltageDropInputState(inputMode = VoltageDropInputMode.CURRENT, currentA = 0.0)
        assertNull("Zero current must return null", VoltageDropCalculationEngine.calculate(zeroCurrentInput))
    }

    @Test
    fun testExactCatalogX_usedFromDataSheet() {
        val input = VoltageDropInputState(
            currentA = 100.0,
            lengthM = 50.0,
            cableSizeMm2 = 35.0,
            cableType = "Cu/XLPE/PVC - 4C"
        )
        val result = VoltageDropCalculationEngine.calculate(input)
        assertNotNull("Catalog calculation for 35 mm2 must succeed", result)

        val dsEntry = Catalog.dataSheet.first { it.size == 35.0 }
        assertEquals("Exact catalog X must be used from data_sheet.json", dsEntry.X, result!!.resolvedXOhmPerKm, 0.0001)
        assertEquals("DataSource must be Catalog (data_sheet.json)", "Catalog (data_sheet.json)", result.dataSource)
    }

    @Test
    fun testMissingCatalogData_returnsNullAndDoesNotSilentlyFabricateFallback() {
        // Size 999.0 mm2 is not in data_sheet.json
        val missingDataInput = VoltageDropInputState(
            currentA = 100.0,
            lengthM = 50.0,
            cableSizeMm2 = 999.0,
            isManualOverride = false
        )
        val result = VoltageDropCalculationEngine.calculate(missingDataInput)
        assertNull("Missing catalog data must return null and never silently substitute 0.08 fallback", result)
    }

    @Test
    fun testManualOverrideX_usedWhenExplicitlyEnabled() {
        val manualInput = VoltageDropInputState(
            isManualOverride = true,
            manualROhmPerKm = 0.350,
            manualXOhmPerKm = 0.095,
            currentA = 100.0,
            lengthM = 50.0
        )
        val result = VoltageDropCalculationEngine.calculate(manualInput)
        assertNotNull("Manual override calculation must succeed", result)
        assertEquals("Manual R must be used", 0.350, result!!.resolvedROhmPerKm, 0.0001)
        assertEquals("Manual X must be used", 0.095, result.resolvedXOhmPerKm, 0.0001)
        assertEquals("DataSource must be MANUAL OVERRIDE", "MANUAL OVERRIDE", result.dataSource)
    }
}
