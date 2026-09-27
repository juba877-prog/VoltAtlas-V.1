package com.example

import com.example.data.*
import com.example.engine.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AllEnginesComprehensiveStressTest {

    private var originalDataSheet: List<DataSheetEntry> = emptyList()

    @Before
    fun setupDataSheet() {
        originalDataSheet = Catalog.dataSheet
        if (Catalog.dataSheet.isEmpty()) {
            Catalog.dataSheet = listOf(
                DataSheetEntry(16.0, 1.15, 0.082, 1.91, 1.38, 2.29),
                DataSheetEntry(70.0, 0.268, 0.077, 0.443, 0.322, 0.532),
                DataSheetEntry(185.0, 0.0991, 0.074, 0.164, 0.119, 0.197),
                DataSheetEntry(630.0, 0.0283, 0.072, 0.0469, 0.0340, 0.0563)
            )
        }
    }

    @After
    fun tearDownDataSheet() {
        Catalog.dataSheet = originalDataSheet
    }

    @Test
    fun testShortCircuitEngineDeepAttenuation() {
        val txMap = mapOf(
            "TX-1" to TransformerResult(
                connectedLoad = 1000.0,
                circuitsAssigned = 5,
                realPower = 800.0,
                demandLoad = 800.0,
                diversifiedLoad = 800.0,
                futureLoad = 800.0,
                designLoad = 800.0,
                requiredKVA = 1000.0,
                recommendedSize = 1000.0,
                selectedSize = 1000.0,
                loadingPct = 80.0,
                flc = 1443.4,
                zt = 0.0096,
                rt = 0.0014,
                xt = 0.0095,
                isc3Phase = 24.0, // 24 kA at TX terminals
                isc1Phase = 16.0,
                status = "PASS",
                z0 = 0.0095,
                vectorGroup = "Dyn11"
            )
        )

        val resolvedCircuits = listOf(
            CircuitEntity(id = 1, circuitName = "MDB", loadKVA = 800.0, fedFrom = "Transformer", transformerId = "TX-1", length_m = 10.0),
            CircuitEntity(id = 2, circuitName = "SMDB", loadKVA = 200.0, fedFrom = "MDB", transformerId = "TX-1", length_m = 50.0),
            CircuitEntity(id = 3, circuitName = "DB-BRANCH", loadKVA = 20.0, fedFrom = "SMDB", transformerId = "TX-1", length_m = 100.0)
        )

        val sizingResults = mapOf(
            1 to SizingResult(1154.0, 1250.0, "ACB", 1.0, 1154.0, "M-630", "630 mm²", 1500.0, 1500.0, 76.0, "300 mm²", "300 mm²", 1.0, 0.25, 0.25, "PASS", "", "Cu", cableOD_mm = 50.0, rUsed = 0.034, xUsed = 0.072, resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null, r20 = 0.034),
            2 to SizingResult(288.0, 315.0, "MCCB", 1.0, 288.0, "M-185", "185 mm²", 370.0, 370.0, 77.0, "95 mm²", "95 mm²", 1.5, 0.375, 0.625, "PASS", "", "Cu", cableOD_mm = 30.0, rUsed = 0.119, xUsed = 0.074, resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null, r20 = 0.119),
            3 to SizingResult(28.8, 32.0, "MCB", 1.0, 28.8, "M-16", "16 mm²", 80.0, 80.0, 36.0, "16 mm²", "16 mm² (Cu)", 2.0, 0.50, 1.125, "PASS", "", "Cu", cableOD_mm = 16.0, rUsed = 1.38, xUsed = 0.082, resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null, r20 = 1.38)
        )

        val circuitParents: Map<Int, Int> = mapOf(1 to 0, 2 to 1, 3 to 2)

        val scResults = ShortCircuitEngine.calculate(resolvedCircuits, circuitParents, sizingResults, txMap)
        assertEquals(3, scResults.size)

        val scMDB = scResults[1]!!.iscKA
        val scSMDB = scResults[2]!!.iscKA
        val scBranch = scResults[3]!!.iscKA

        assertTrue("Short circuit current must attenuate along cable length (MDB >= SMDB)", scMDB >= scSMDB)
        assertTrue("Short circuit current must attenuate further down to branch (SMDB >= Branch)", scSMDB >= scBranch)
        assertTrue("Branch short circuit current must be non-zero and non-NaN", scBranch > 0.0 && !scBranch.isNaN())
        println("ALL ENGINES STRESS: Short Circuit Attenuation: TX=24.0kA -> MDB=${scMDB}kA -> SMDB=${scSMDB}kA -> Branch=${scBranch}kA")
    }

    @Test
    fun testProtectionEngineZsAndAdsDisconnectionTime() {
        val circuit = CircuitEntity(id = 10, circuitName = "FINAL-BRANCH", length_m = 40.0, phases = "3P+N")
        val sizingResult = SizingResult(
            Ib_A = 25.0,
            deviceSize_A = 32.0,
            deviceType = "MCB",
            cTotalDerating = 1.0,
            iRequired_perCable_A = 25.0,
            productCode = "M-16",
            cableSize_mm2 = "16 mm²",
            ratedI_table_A = 80.0,
            capacity_derated_Iz_A = 80.0,
            utilizationPct = 31.25,
            neutral_mm2 = "16 mm²",
            earthCPC_mm2 = "16 mm² (Cu)", // Descriptive string format
            voltDrop_V = 1.2,
            vdPct = 0.3,
            cumulativeVDpct = 0.3,
            status = "PASS",
            failReason = "",
            cableMaterial = "Cu",
            rUsed = 1.38,
            xUsed = 0.082
        , resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null)

        val adsRes = ProtectionEngine.calculateAds(circuit, sizingResult, ze_ohm = 0.05)
        assertNotNull(adsRes)
        assertFalse("Calculated Zs must not be NaN", adsRes.calculatedZs.isNaN())
        assertTrue("R2 resistance must be properly calculated", adsRes.r2_ohm_km >= 0.0)
        println("ALL ENGINES STRESS: Zs = ${adsRes.calculatedZs} ohm, Max Zs = ${adsRes.maxZsAllowed} ohm, Status = ${adsRes.status}")
    }

    @Test
    fun testTransformerEngineMultiUnitLoadBalancing() {
        val tx1 = TransformerEntity(projectId = "PROJ-1", transformerId = "TX-1", kVA = 1000.0, percentZ = 5.0, pf = 0.8, status = "Active")
        val tx2 = TransformerEntity(projectId = "PROJ-1", transformerId = "TX-2", kVA = 1600.0, percentZ = 6.0, pf = 0.85, status = "Active")
        
        val circuits1 = listOf(CircuitEntity(id = 1, circuitName = "LOAD-1", loadKVA = 500.0, fedFrom = "Transformer", transformerId = "TX-1"))
        val circuits2 = listOf(CircuitEntity(id = 2, circuitName = "LOAD-2", loadKVA = 800.0, fedFrom = "Transformer", transformerId = "TX-2"))

        val res1 = TransformerEngine.calculate(tx1, circuits1)
        val res2 = TransformerEngine.calculate(tx2, circuits2)

        assertTrue("TX-1 loading must be non-zero and non-NaN", res1.loadingPct > 0.0)
        assertTrue("TX-2 loading must be non-zero and non-NaN", res2.loadingPct > 0.0)
        println("ALL ENGINES STRESS: Transformer 1 Loading = ${res1.loadingPct}%, Transformer 2 Loading = ${res2.loadingPct}%")
    }

    @Test
    fun testGeneratorEngineMotorStartingTransients() {
        val genSettings = GeneratorEntity(
            projectId = "PROJ-1",
            generatorPf = 0.8,
            additionalEssentialLoadKW = 150.0,
            largestMotorKW = 110.0,
            motorStartMethod = "DOL",
            motorPf = 0.85,
            stepLoadPct = 60.0,
            oversizingFactor = 1.15
        )

        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "ESSENTIAL-1", loadKVA = 200.0, generatorIncluded = true)
        )

        val genRes = GeneratorEngine.calculate(genSettings, circuits, totalConnectedLoad = 500.0)
        assertNotNull(genRes)
        assertTrue("Generator required capacity must be non-zero and non-NaN", genRes.requiredGenerator > 0.0)
        assertNotNull("Recommended generator size must be selected", genRes.finalSize)
        println("ALL ENGINES STRESS: Generator Required = ${genRes.requiredGenerator} kVA, Selected Size = ${genRes.finalSize} kVA")
    }

    @Test
    fun testAtsEngineLocalizedIcwWithstand() {
        val atsSettings = AtsEntity(projectId = "PROJ-1", systemVoltage = 400, designSafetyFactor = 1.25)
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "ATS-CIRCUIT", loadKVA = 150.0, atsIncluded = true, assignedAtsId = "ATS-01")
        )

        val atsRes = AtsEngine.calculate(
            settings = atsSettings,
            totalConnectedLoad = 400.0,
            essentialLoadKVA = 150.0,
            generatorSelectedSize = 250.0,
            transformerFLC = 721.0,
            isc3PhaseAtTransformer = 15.0, // localized downstream fault current
            circuits = circuits
        )

        assertNotNull(atsRes)
        assertEquals(15.0, atsRes.requiredIcw, 0.01)
        assertNotNull("Final ATS rating must be selected", atsRes.finalRating)
        println("ALL ENGINES STRESS: ATS Selected Rating = ${atsRes.finalRating} A, Required Icw = ${atsRes.requiredIcw} kA")
    }

    @Test
    fun testUpsEngineInverterCapacityAndBatteryAutonomy() {
        val upsSettings = UpsEntity(
            projectId = "PROJ-1",
            upsPf = 0.9,
            upsEfficiency = 95.0,
            autonomyMinutes = 60,
            agingFactor = 1.25,
            designMarginPct = 20.0
        )
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "CRITICAL-SERVER-1", loadKVA = 80.0, pf = 0.8, upsBacked = true, assignedUpsId = "UPS-01")
        )

        val upsRes = UpsEngine.calculate(upsSettings, circuits)
        assertNotNull(upsRes)
        assertEquals(80.0, upsRes.upsApparentPowerReq, 0.01)
        assertTrue("Battery Ah capacity must be calculated and non-NaN", (upsRes.batteryAh ?: 0.0) > 0.0)
        println("ALL ENGINES STRESS: UPS Apparent Power Req = ${upsRes.upsApparentPowerReq} kVA, Battery Ah = ${upsRes.batteryAh} Ah")
    }

    @Test
    fun testPfCorrectionEngineDetunedReactorBoost() {
        val pfGlobal = PfGlobalEntity(projectId = "PROJ-1", targetPf = 0.97, kvarStepSize = 50.0, systemVoltage = 400)
        val transformers = listOf(TransformerEntity(projectId = "PROJ-1", transformerId = "TX-1", pf = 0.8, status = "Active"))
        val txResults = mapOf("TX-1" to TransformerResult(connectedLoad = 500.0, circuitsAssigned = 2, realPower = 400.0, demandLoad = 400.0, diversifiedLoad = 400.0, futureLoad = 400.0, designLoad = 400.0, requiredKVA = 500.0, recommendedSize = 630.0, selectedSize = 630.0, loadingPct = 79.0, flc = 909.0, zt = 0.01, rt = 0.002, xt = 0.009, isc3Phase = 20.0, isc1Phase = 13.0, status = "PASS", z0 = 0.009, vectorGroup = "Dyn11"))

        val pfSettingsWithReactor = listOf(PfTransformerEntity(projectId = "PROJ-1", transformerId = "TX-1", detuningReactor = "Yes"))
        val pfSettingsNoReactor = listOf(PfTransformerEntity(projectId = "PROJ-1", transformerId = "TX-1", detuningReactor = "No"))

        val resReactor = PfCorrectionEngine.calculate(pfGlobal, pfSettingsWithReactor, transformers, txResults)[0]
        val resNoReactor = PfCorrectionEngine.calculate(pfGlobal, pfSettingsNoReactor, transformers, txResults)[0]

        assertTrue("Detuning reactor must increase capacitor bank current calculation by 1.075x", resReactor.bankCurrent > resNoReactor.bankCurrent)
        assertEquals(resNoReactor.bankCurrent * 1.075, resReactor.bankCurrent, 0.01)
        println("ALL ENGINES STRESS: PFC Bank Current (No Reactor) = ${resNoReactor.bankCurrent} A, (With Reactor) = ${resReactor.bankCurrent} A")
    }

    @Test
    fun testConduitFillEngineInputCalculation() {
        val fillInput = ConduitFillInput(
            circuitId = 1,
            circuitName = "FEEDER-1",
            mainCableOD_mm = 25.0,
            cpcSizeMm2 = 16.0,
            parallelRuns = 1,
            routeLength_m = 50.0
        )

        val fillRes = ConduitFillEngine.calculate(fillInput, emptyList())
        assertNotNull(fillRes)
        assertFalse("Fill ratio percent must not be NaN", fillRes.fillRatioPercent.isNaN())
        println("ALL ENGINES STRESS: Conduit Fill Status = ${fillRes.status}, Fill Ratio = ${fillRes.fillRatioPercent}%")
    }
}
