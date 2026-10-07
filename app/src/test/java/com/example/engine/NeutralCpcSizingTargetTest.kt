package com.example.engine

import com.example.data.CircuitEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Target test suite for EE-07 (Neutral and Protective Conductor Sizing & Overrides).
 *
 * Verifies Option B requirements:
 * T1. Valid overrideNeutralSize overrides neutral_mm2 and sets isNeutralOverridden = true.
 * T2. Valid overrideEarthSize overrides earthCPC_mm2, cpcAreaMm2, and sets isEarthOverridden = true.
 * T3. Both overrides applied simultaneously.
 * T4. Null, blank, or "AUTO" neutral and earth overrides preserve default geometric/harmonic calculation.
 * T5. Invalid non-numeric overrideNeutralSize is rejected with CableFailureCode.INCOMPLETE_INPUT.
 * T6. Non-standard overrideNeutralSize is rejected with CableFailureCode.INCOMPLETE_INPUT.
 * T7. Zero or negative overrideNeutralSize is rejected with CableFailureCode.INCOMPLETE_INPUT.
 * T8. Invalid non-numeric or non-standard overrideEarthSize is rejected with CableFailureCode.INCOMPLETE_INPUT.
 * T9. Overriding overrideEarthSize affects ADS Zs calculation (R2 loop impedance) without altering phase withstand.
 * T10. Phase thermal withstand (k²S² ≥ Isc²t) in ProtectionEngine is strictly invariant to neutral/earth overrides.
 */
class NeutralCpcSizingTargetTest {

    companion object {
        @JvmField
        @org.junit.ClassRule
        val catalogStateReset = com.example.testing.CatalogStateResetRule()

        private fun findAssetFile(filename: String): java.io.File {
            val candidates = listOf(
                java.io.File("app/src/main/assets/$filename"),
                java.io.File("src/main/assets/$filename"),
                java.io.File("../app/src/main/assets/$filename")
            )
            return candidates.firstOrNull { it.exists() }
                ?: error("Asset not found: $filename")
        }

        private inline fun <reified T> loadCatalogArray(filename: String, map: (org.json.JSONObject) -> T): List<T> {
            val arr = org.json.JSONArray(findAssetFile(filename).readText(Charsets.UTF_8))
            return (0 until arr.length()).map { map(arr.getJSONObject(it)) }
        }

        @org.junit.BeforeClass
        @JvmStatic
        fun setUpClass() {
            com.example.data.Catalog.cableTypes = loadCatalogArray("cable_types.json") { obj ->
                com.example.data.CableType(
                    code = com.example.data.catalog.BreakerParser.parsePoles(obj.opt("typeCode") ?: obj.opt("code")),
                    cableType = obj.optString("cableType", "Standard"),
                    conductor = obj.optString("conductor", "Cu"),
                    insulation = obj.optString("insulation", "XLPE"),
                    armour = obj.optString("armour", "None"),
                    cores = obj.optString("cores", "4C"),
                    voltage = obj.optString("voltage", "0.6/1"),
                    standard = obj.optString("standard", "IEC 60502"),
                    rColumn = obj.optString("rColumn", "R_Cu90")
                )
            }
            com.example.data.Catalog.dataSheet = loadCatalogArray("data_sheet.json") { obj ->
                com.example.data.DataSheetEntry(
                    size = obj.getDouble("size"),
                    R_Cu20 = if (obj.has("R_Cu20")) obj.getDouble("R_Cu20") else obj.getDouble("R_Cu70"),
                    X = obj.getDouble("X"),
                    R_Al20 = if (obj.has("R_Al20")) obj.getDouble("R_Al20") else obj.getDouble("R_Al70"),
                    R_Cu_70_est = if (obj.has("R_Cu_70_est")) obj.getDouble("R_Cu_70_est") else obj.getDouble("R_Cu90"),
                    R_Al_70_est = if (obj.has("R_Al_70_est")) obj.getDouble("R_Al_70_est") else obj.getDouble("R_Al90")
                )
            }
            com.example.data.Catalog.cableCatalog = loadCatalogArray("cable_catalog.json") { obj ->
                com.example.data.CableCatalogEntry(
                    mfg = obj.optString("mfg", obj.optString("Manufacturer", "Generic")),
                    typeCode = com.example.data.catalog.BreakerParser.parsePoles(obj.opt("typeCode") ?: obj.opt("TypeCode") ?: obj.opt("Code")),
                    size = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("size") ?: obj.opt("SZ (mm²)") ?: obj.opt("Size")),
                    model = obj.optString("model", obj.optString("Model", "")),
                    gnd = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("gnd") ?: obj.opt("GND")),
                    duct = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("duct") ?: obj.opt("DUCT")),
                    air = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("air") ?: obj.opt("AIR")),
                    od = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("od") ?: obj.opt("OD")),
                    wt = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("wt") ?: obj.opt("WT"))
                )
            }
            com.example.data.Catalog.breakers = loadCatalogArray("breaker_catalog.json") { obj ->
                com.example.data.BreakerEntry(
                    mfg = obj.optString("mfg", obj.optString("Source", "Generic")),
                    type = obj.optString("type", "MCCB"),
                    series = obj.optString("series", obj.optString("Series", "")),
                    frame = if (obj.has("frameA")) obj.optString("frameA", "") else obj.optString("frame", obj.optString("Frame", "")),
                    version = obj.optString("version", obj.optString("Version", "")),
                    rating_A = com.example.data.catalog.BreakerParser.parseRating(obj.opt("ratingA") ?: obj.opt("rating_A") ?: obj.opt("In (A)")),
                    poles = com.example.data.catalog.BreakerParser.parsePoles(obj.opt("poles") ?: obj.opt("Poles")),
                    icu_kA = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("icuKa") ?: obj.opt("icu_kA") ?: obj.opt("Icu@415V (kA)")),
                    ics_kA = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("icsKa") ?: obj.opt("ics_kA") ?: obj.opt("Ics@415V (kA)")),
                    icm_kA = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("icmKa") ?: obj.opt("icm_kA") ?: obj.opt("Icm@415V (kA)")),
                    tripUnit = obj.optString("tripUnit", obj.optString("Trip Unit", "Thermal-Magnetic"))
                )
            }
            com.example.data.Catalog.deratingData = org.json.JSONObject(findAssetFile("derating_data.json").readText(Charsets.UTF_8))
            com.example.data.Catalog.invalidateIndexes()
        }
    }

    private fun baseCircuit(
        overrideCable: String? = "50",
        overrideNeutral: String? = null,
        overrideEarth: String? = null
    ) = CircuitEntity(
        id = 201,
        circuitName = "FEEDER-TARGET",
        loadKVA = 60.0,
        voltageV = 400,
        phases = "3P+N",
        cableType = "Cu/XLPE/PVC - 4C",
        manufacturer = "Elsewedy",
        installation = "Laid in Ducts",
        parallelRuns = 1,
        overrideCableSize = overrideCable,
        overrideNeutralSize = overrideNeutral,
        overrideEarthSize = overrideEarth,
        airTempC = 40,
        gndTempC = 40,
        burialDepth_m = 0.6,
        soilRes = 125,
        maxVDpct = 0.04,
        length_m = 25.0
    )

    @Test
    fun testT1_validNeutralOverride_isAppliedAndFlagged() {
        val circuit = baseCircuit(overrideNeutral = "70 mm²")
        val sizing = CableSizingEngine.sizeCircuit(circuit, 0.0)

        assertEquals("✓ PASS", sizing.status)
        assertEquals("70 mm²", sizing.neutral_mm2)
        assertTrue("isNeutralOverridden must be true", sizing.isNeutralOverridden)
        assertFalse("isEarthOverridden must remain false", sizing.isEarthOverridden)
        assertEquals("25 mm²", sizing.earthCPC_mm2) // Unaltered geometric CPC
    }

    @Test
    fun testT2_validEarthOverride_isAppliedAndFlagged() {
        val circuit = baseCircuit(overrideEarth = "35 mm²")
        val sizing = CableSizingEngine.sizeCircuit(circuit, 0.0)

        assertEquals("✓ PASS", sizing.status)
        assertEquals("35 mm²", sizing.earthCPC_mm2)
        assertEquals(35.0, sizing.cpcAreaMm2 ?: 0.0, 1e-6)
        assertTrue("isEarthOverridden must be true", sizing.isEarthOverridden)
        assertFalse("isNeutralOverridden must remain false", sizing.isNeutralOverridden)
        assertEquals("25 mm²", sizing.neutral_mm2) // Unaltered geometric neutral
    }

    @Test
    fun testT3_bothOverrides_appliedSimultaneously() {
        val circuit = baseCircuit(overrideNeutral = "70", overrideEarth = "35")
        val sizing = CableSizingEngine.sizeCircuit(circuit, 0.0)

        assertEquals("✓ PASS", sizing.status)
        assertEquals("70 mm²", sizing.neutral_mm2)
        assertEquals("35 mm²", sizing.earthCPC_mm2)
        assertEquals(35.0, sizing.cpcAreaMm2 ?: 0.0, 1e-6)
        assertTrue("isNeutralOverridden must be true", sizing.isNeutralOverridden)
        assertTrue("isEarthOverridden must be true", sizing.isEarthOverridden)
    }

    @Test
    fun testT4_autoAndNullOverrides_preserveGeometricDefaults() {
        // null override
        val sizingNull = CableSizingEngine.sizeCircuit(baseCircuit(overrideNeutral = null, overrideEarth = null), 0.0)
        assertEquals("25 mm²", sizingNull.neutral_mm2)
        assertEquals("25 mm²", sizingNull.earthCPC_mm2)
        assertFalse(sizingNull.isNeutralOverridden)
        assertFalse(sizingNull.isEarthOverridden)

        // AUTO override
        val sizingAuto = CableSizingEngine.sizeCircuit(baseCircuit(overrideNeutral = "AUTO", overrideEarth = "Auto"), 0.0)
        assertEquals("25 mm²", sizingAuto.neutral_mm2)
        assertEquals("25 mm²", sizingAuto.earthCPC_mm2)
        assertFalse(sizingAuto.isNeutralOverridden)
        assertFalse(sizingAuto.isEarthOverridden)

        // Blank override
        val sizingBlank = CableSizingEngine.sizeCircuit(baseCircuit(overrideNeutral = "   ", overrideEarth = ""), 0.0)
        assertEquals("25 mm²", sizingBlank.neutral_mm2)
        assertEquals("25 mm²", sizingBlank.earthCPC_mm2)
        assertFalse(sizingBlank.isNeutralOverridden)
        assertFalse(sizingBlank.isEarthOverridden)
    }

    @Test
    fun testT5_invalidNonNumericNeutralOverride_failsWithIncompleteInput() {
        val circuit = baseCircuit(overrideNeutral = "invalid")
        val sizing = CableSizingEngine.sizeCircuit(circuit, 0.0)

        assertEquals("✗ FAIL", sizing.status)
        assertEquals(SizingResultStatus.INCOMPLETE, sizing.resultStatus)
        assertEquals(CableFailureCode.INCOMPLETE_INPUT, sizing.primaryFailureCode)
        assertTrue(sizing.failReason.contains("Manual neutral size 'invalid' is invalid."))
    }

    @Test
    fun testT6_nonStandardNeutralOverride_failsWithIncompleteInput() {
        val circuit = baseCircuit(overrideNeutral = "42 mm²")
        val sizing = CableSizingEngine.sizeCircuit(circuit, 0.0)

        assertEquals("✗ FAIL", sizing.status)
        assertEquals(SizingResultStatus.INCOMPLETE, sizing.resultStatus)
        assertEquals(CableFailureCode.INCOMPLETE_INPUT, sizing.primaryFailureCode)
        assertTrue(sizing.failReason.contains("Manual neutral size '42 mm²' is invalid."))
    }

    @Test
    fun testT7_negativeOrZeroNeutralOverride_failsWithIncompleteInput() {
        val circuitNegative = baseCircuit(overrideNeutral = "-10")
        val sizingNeg = CableSizingEngine.sizeCircuit(circuitNegative, 0.0)
        assertEquals(SizingResultStatus.INCOMPLETE, sizingNeg.resultStatus)
        assertEquals(CableFailureCode.INCOMPLETE_INPUT, sizingNeg.primaryFailureCode)

        val circuitZero = baseCircuit(overrideNeutral = "0")
        val sizingZero = CableSizingEngine.sizeCircuit(circuitZero, 0.0)
        assertEquals(SizingResultStatus.INCOMPLETE, sizingZero.resultStatus)
        assertEquals(CableFailureCode.INCOMPLETE_INPUT, sizingZero.primaryFailureCode)
    }

    @Test
    fun testT8_invalidEarthOverride_failsWithIncompleteInput() {
        val circuitInvalid = baseCircuit(overrideEarth = "invalid")
        val sizingInv = CableSizingEngine.sizeCircuit(circuitInvalid, 0.0)
        assertEquals(SizingResultStatus.INCOMPLETE, sizingInv.resultStatus)
        assertEquals(CableFailureCode.INCOMPLETE_INPUT, sizingInv.primaryFailureCode)

        val circuitNonStd = baseCircuit(overrideEarth = "13")
        val sizingNonStd = CableSizingEngine.sizeCircuit(circuitNonStd, 0.0)
        assertEquals(SizingResultStatus.INCOMPLETE, sizingNonStd.resultStatus)
        assertEquals(CableFailureCode.INCOMPLETE_INPUT, sizingNonStd.primaryFailureCode)

        val circuitZero = baseCircuit(overrideEarth = "0")
        val sizingZero = CableSizingEngine.sizeCircuit(circuitZero, 0.0)
        assertEquals(SizingResultStatus.INCOMPLETE, sizingZero.resultStatus)
        assertEquals(CableFailureCode.INCOMPLETE_INPUT, sizingZero.primaryFailureCode)
    }

    @Test
    fun testT9_earthOverride_affectsLoopImpedanceInAds() {
        val circuitDefault = baseCircuit(overrideEarth = null)
        val sizingDefault = CableSizingEngine.sizeCircuit(circuitDefault, 0.0)
        val adsDefault = ProtectionEngine.calculateAds(circuitDefault, sizingDefault, 0.05, true)

        val circuitLargeCpc = baseCircuit(overrideEarth = "50")
        val sizingLargeCpc = CableSizingEngine.sizeCircuit(circuitLargeCpc, 0.0)
        val adsLargeCpc = ProtectionEngine.calculateAds(circuitLargeCpc, sizingLargeCpc, 0.05, true)

        // Sizing CPC 25 mm² vs overridden CPC 50 mm²: larger CPC has lower R2 resistance,
        // so calculatedZs must be strictly lower for the 50 mm² CPC!
        assertTrue(
            "Calculated Zs with 50 mm² CPC (${adsLargeCpc.calculatedZs}) must be less than with 25 mm² CPC (${adsDefault.calculatedZs})",
            adsLargeCpc.calculatedZs < adsDefault.calculatedZs
        )
    }

    @Test
    fun testT10_phaseWithstand_strictlyInvariantToNeutralAndEarthOverrides() {
        val circuitDefault = baseCircuit(overrideNeutral = null, overrideEarth = null)
        val sizingDefault = CableSizingEngine.sizeCircuit(circuitDefault, 0.0)

        val circuitOverridden = baseCircuit(overrideNeutral = "70", overrideEarth = "16")
        val sizingOverridden = CableSizingEngine.sizeCircuit(circuitOverridden, 0.0)

        val sc = ShortCircuitResult(
            circuitId = 201,
            sourceName = "FEEDER-TARGET",
            cableMm2 = "50 mm²",
            iscKA = 12.0,
            iwKA = 22.6,
            deviceKA = 36.0,
            deviceCheck = "PASS",
            cableCheck = "PASS",
            marginPct = 40.0,
            status = "PASS",
            note = ""
        )

        val protDefault = ProtectionEngine.evaluateProtection(circuitDefault, sizingDefault, sc, 160.0)
        val protOverridden = ProtectionEngine.evaluateProtection(circuitOverridden, sizingOverridden, sc, 160.0)

        // Phase conductor withstand status and final status must be IDENTICAL
        assertEquals(protDefault.cableWithstandStatus, protOverridden.cableWithstandStatus)
        assertEquals(protDefault.shortCircuitStatus, protOverridden.shortCircuitStatus)
        assertEquals(protDefault.finalStatus, protOverridden.finalStatus)
    }
}
