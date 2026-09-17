package com.example

import com.example.data.CircuitEntity
import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.exp
import kotlin.math.sqrt

class ShortCircuitEngineTest {

    @Test
    fun testKappaPeakFactorCalculation() {
        // IEC 60909: kappa = 1.02 + 0.98 * e^(-3 * R / X)
        // For X/R = 10 (R/X = 0.1): kappa = 1.02 + 0.98 * e^(-0.3) = 1.02 + 0.98 * 0.740818 = 1.7460
        val rOverX = 0.1
        val kappaExpected = 1.02 + 0.98 * exp(-3.0 * rOverX)
        assertEquals("Kappa for X/R=10 must be approx 1.746", 1.7460, kappaExpected, 0.005)

        val txResult = TransformerResult(
            connectedLoad = 200.0,
            circuitsAssigned = 1,
            realPower = 160.0,
            demandLoad = 160.0,
            diversifiedLoad = 160.0,
            futureLoad = 160.0,
            designLoad = 160.0,
            requiredKVA = 200.0,
            recommendedSize = 200.0,
            selectedSize = 200.0,
            loadingPct = 80.0,
            flc = 288.68,
            zt = 32.0,       // mOhm
            rt = 3.184119,   // mOhm -> X/R = 10
            xt = 31.84119,   // mOhm
            isc3Phase = 7.58, // kA
            isc1Phase = 6.56,
            status = "VERIFIED",
            z0 = 30.4,
            vectorGroup = "Dyn11",
            voltage = 400.0
        )

        val transformerResults = mapOf("TX-01" to txResult)

        val mdbCircuit = CircuitEntity(
            id = 101,
            circuitName = "MDB-MAIN",
            loadKVA = 160.0,
            fedFrom = "Transformer",
            transformerId = "TX-01",
            length_m = 0.1 // minimal length at terminals
        )

        val sizingResults = mapOf(
            101 to SizingResult(
                Ib_A = 230.94,
                deviceSize_A = 250.0,
                deviceType = "MCCB",
                cTotalDerating = 0.95,
                iRequired_perCable_A = 243.09,
                productCode = "CX1-T104-G18",
                cableSize_mm2 = "150 mm²",
                ratedI_table_A = 322.0,
                capacity_derated_Iz_A = 305.9,
                utilizationPct = 75.5,
                neutral_mm2 = "70 mm²",
                earthCPC_mm2 = "70 mm²",
                voltDrop_V = 0.0,
                vdPct = 0.0,
                cumulativeVDpct = 0.0,
                status = "✓ PASS",
                failReason = "",
                cableMaterial = "Cu",
                cableOD_mm = 45.7,
                deviceBreakingKA = 50.0,
                kFactor = 143.0,
                clearingTime_s = 0.1,
                rUsed = 0.149,
                xUsed = 0.075
            , resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null)
        )

        val circuitParents = mapOf(101 to 0)

        val scResults = ShortCircuitEngine.calculate(
            resolvedCircuits = listOf(mdbCircuit),
            circuitParents = circuitParents,
            sizingResults = sizingResults,
            transformerResults = transformerResults
        )

        val res = scResults[101]
        assertNotNull(res)
        assertEquals("Isc at TX terminals match ~7.58 kA", 7.58, res!!.iscKA, 0.05)
        // ip = sqrt(2) * 1.7460 * 7.58 = 1.4142 * 1.7460 * 7.58 = 18.71 kA
        val expectedIp = sqrt(2.0) * kappaExpected * res.iscKA
        assertEquals("Peak ip match", expectedIp, res.ipKA, 0.5)
        assertTrue("Peak ip must be greater than Isc (ip > isc)", res.ipKA > res.iscKA)
    }

    @Test
    fun testDownstreamShortCircuitAttenuation() {
        val txResult = TransformerResult(
            connectedLoad = 500.0,
            circuitsAssigned = 2,
            realPower = 400.0,
            demandLoad = 400.0,
            diversifiedLoad = 400.0,
            futureLoad = 400.0,
            designLoad = 400.0,
            requiredKVA = 500.0,
            recommendedSize = 500.0,
            selectedSize = 500.0,
            loadingPct = 80.0,
            flc = 721.7,
            zt = 16.0,       // mOhm
            rt = 1.59,       // mOhm
            xt = 15.92,      // mOhm
            isc3Phase = 15.16, // kA at TX terminals
            isc1Phase = 13.13,
            status = "VERIFIED",
            z0 = 15.2,
            vectorGroup = "Dyn11",
            voltage = 400.0
        )

        val mdb = CircuitEntity(id = 1, circuitName = "MDB", loadKVA = 400.0, fedFrom = "Transformer", transformerId = "TX-01", length_m = 10.0)
        val subBoard = CircuitEntity(id = 2, circuitName = "SUB-BOARD", loadKVA = 50.0, fedFrom = "MDB", transformerId = "TX-01", length_m = 80.0)

        val sizingResults = mapOf(
            1 to SizingResult(
                Ib_A = 577.0, deviceSize_A = 630.0, deviceType = "MCCB", cTotalDerating = 1.0, iRequired_perCable_A = 577.0,
                productCode = "CX1-T104-G30", cableSize_mm2 = "300 mm²", ratedI_table_A = 629.0, capacity_derated_Iz_A = 629.0,
                utilizationPct = 91.7, neutral_mm2 = "150 mm²", earthCPC_mm2 = "150 mm²", voltDrop_V = 0.5, vdPct = 0.1,
                cumulativeVDpct = 0.1, status = "✓ PASS", failReason = "", cableMaterial = "Cu", cableOD_mm = 62.1,
                deviceBreakingKA = 50.0, kFactor = 143.0, clearingTime_s = 0.1, rUsed = 0.076, xUsed = 0.073
            , resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null),
            2 to SizingResult(
                Ib_A = 72.0, deviceSize_A = 80.0, deviceType = "MCCB", cTotalDerating = 1.0, iRequired_perCable_A = 72.0,
                productCode = "CX1-T104-G12", cableSize_mm2 = "25 mm²", ratedI_table_A = 131.0, capacity_derated_Iz_A = 131.0,
                utilizationPct = 55.0, neutral_mm2 = "25 mm²", earthCPC_mm2 = "25 mm²", voltDrop_V = 2.0, vdPct = 0.5,
                cumulativeVDpct = 0.6, status = "✓ PASS", failReason = "", cableMaterial = "Cu", cableOD_mm = 24.3,
                deviceBreakingKA = 25.0, kFactor = 143.0, clearingTime_s = 0.1, rUsed = 0.927, xUsed = 0.082
            , resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null)
        )

        val circuitParents = mapOf(1 to 0, 2 to 1)

        val scResults = ShortCircuitEngine.calculate(
            resolvedCircuits = listOf(mdb, subBoard),
            circuitParents = circuitParents,
            sizingResults = sizingResults,
            transformerResults = mapOf("TX-01" to txResult)
        )

        val scMdb = scResults[1]!!.iscKA
        val scSubBoard = scResults[2]!!.iscKA

        assertTrue("Short circuit attenuates downstream: MDB ($scMdb kA) > SubBoard ($scSubBoard kA)", scMdb > scSubBoard)
        assertTrue("SubBoard short circuit is positive", scSubBoard > 0.0)
    }

    @Test
    fun testBreakingCapacity_catalogDevice_notFlaggedAssumed() {
        val txResult = TransformerResult(
            connectedLoad = 200.0,
            circuitsAssigned = 1,
            realPower = 160.0,
            demandLoad = 160.0,
            diversifiedLoad = 160.0,
            futureLoad = 160.0,
            designLoad = 160.0,
            requiredKVA = 200.0,
            recommendedSize = 200.0,
            selectedSize = 200.0,
            loadingPct = 80.0,
            flc = 288.68,
            zt = 32.0,
            rt = 3.18,
            xt = 31.84,
            isc3Phase = 7.58,
            isc1Phase = 6.56,
            status = "VERIFIED",
            z0 = 30.4,
            vectorGroup = "Dyn11",
            voltage = 400.0
        )

        val circuit = CircuitEntity(id = 1, circuitName = "MDB-1", loadKVA = 160.0, fedFrom = "Transformer", transformerId = "TX-01", length_m = 50.0)
        // SizingResult with real catalog provenance for NSX250 (Icu = 36.0 kA, Icm = 75.6 kA)
        val sizingResult = SizingResult(
            Ib_A = 230.94,
            deviceSize_A = 250.0,
            deviceType = "MCCB",
            cTotalDerating = 0.95,
            iRequired_perCable_A = 243.09,
            productCode = "CX1-T104-G18",
            cableSize_mm2 = "150 mm²",
            ratedI_table_A = 322.0,
            capacity_derated_Iz_A = 305.9,
            utilizationPct = 75.5,
            neutral_mm2 = "70 mm²",
            earthCPC_mm2 = "70 mm²",
            voltDrop_V = 3.284,
            vdPct = 0.82,
            cumulativeVDpct = 0.82,
            status = "✓ PASS",
            failReason = "",
            cableMaterial = "Cu",
            cableOD_mm = 45.7,
            deviceBreakingKA = 36.0,
            deviceMakingKA = 75.6,
            kFactor = 143.0,
            clearingTime_s = 0.1,
            rUsed = 0.149,
            xUsed = 0.075,
            resultStatus = SizingResultStatus.PASS,
            isAmpacityOk = true,
            isVoltageDropOk = true,
            primaryFailureCode = null,
            isDeviceFromCatalog = true,
            breakerOrderingCode = "C25F3TM250"
        )

        val scResults = ShortCircuitEngine.calculate(
            resolvedCircuits = listOf(circuit),
            circuitParents = mapOf(1 to 0),
            sizingResults = mapOf(1 to sizingResult),
            transformerResults = mapOf("TX-01" to txResult)
        )

        val res = scResults[1]
        assertNotNull(res)
        assertFalse(
            "Genuine catalog breaker with Icu=36.0 kA must NOT be flagged as assumed",
            res!!.note.contains("Breaking capacity assumed")
        )
    }

    @Test
    fun testBreakingCapacity_fallbackDevice_flaggedAssumed() {
        val txResult = TransformerResult(
            connectedLoad = 200.0,
            circuitsAssigned = 1,
            realPower = 160.0,
            demandLoad = 160.0,
            diversifiedLoad = 160.0,
            futureLoad = 160.0,
            designLoad = 160.0,
            requiredKVA = 200.0,
            recommendedSize = 200.0,
            selectedSize = 200.0,
            loadingPct = 80.0,
            flc = 288.68,
            zt = 32.0,
            rt = 3.18,
            xt = 31.84,
            isc3Phase = 7.58,
            isc1Phase = 6.56,
            status = "VERIFIED",
            z0 = 30.4,
            vectorGroup = "Dyn11",
            voltage = 400.0
        )

        val circuit = CircuitEntity(id = 2, circuitName = "CUSTOM-CIRCUIT", loadKVA = 160.0, fedFrom = "Transformer", transformerId = "TX-01", length_m = 50.0)
        // SizingResult with fallback default breaking capacity (isDeviceFromCatalog = false)
        val sizingResult = SizingResult(
            Ib_A = 230.94,
            deviceSize_A = 250.0,
            deviceType = "MCCB",
            cTotalDerating = 0.95,
            iRequired_perCable_A = 243.09,
            productCode = "CUSTOM",
            cableSize_mm2 = "150 mm²",
            ratedI_table_A = 322.0,
            capacity_derated_Iz_A = 305.9,
            utilizationPct = 75.5,
            neutral_mm2 = "70 mm²",
            earthCPC_mm2 = "70 mm²",
            voltDrop_V = 3.284,
            vdPct = 0.82,
            cumulativeVDpct = 0.82,
            status = "✓ PASS",
            failReason = "",
            cableMaterial = "Cu",
            cableOD_mm = 45.7,
            deviceBreakingKA = 36.0,
            deviceMakingKA = 0.0,
            kFactor = 143.0,
            clearingTime_s = 0.1,
            rUsed = 0.149,
            xUsed = 0.075,
            resultStatus = SizingResultStatus.PASS,
            isAmpacityOk = true,
            isVoltageDropOk = true,
            primaryFailureCode = null,
            isDeviceFromCatalog = false,
            breakerOrderingCode = null
        )

        val scResults = ShortCircuitEngine.calculate(
            resolvedCircuits = listOf(circuit),
            circuitParents = mapOf(2 to 0),
            sizingResults = mapOf(2 to sizingResult),
            transformerResults = mapOf("TX-01" to txResult)
        )

        val res = scResults[2]
        assertNotNull(res)
        assertTrue(
            "Fallback breaker without catalog match MUST be flagged as assumed",
            res!!.note.contains("Breaking capacity assumed (not from catalog) — verify")
        )
    }

    // =========================================================================
    // AUDIT-5 P0 item 4 (E-25, E-10): Typed conductor data.
    // Busway display label must NOT be parsed as 800 mm² in ShortCircuitEngine.
    // Must return DATA MISSING.
    // =========================================================================

    @Test
    fun testShortCircuit_buswayCableSize_doesNotParse800mm2_failsWithDataMissing() {
        val txResult = TransformerResult(
            connectedLoad = 200.0,
            circuitsAssigned = 1,
            realPower = 160.0,
            demandLoad = 160.0,
            diversifiedLoad = 160.0,
            futureLoad = 160.0,
            designLoad = 160.0,
            requiredKVA = 200.0,
            recommendedSize = 200.0,
            selectedSize = 200.0,
            loadingPct = 80.0,
            flc = 288.68,
            zt = 32.0,
            rt = 3.18,
            xt = 31.84,
            isc3Phase = 7.58,
            isc1Phase = 6.56,
            status = "VERIFIED",
            z0 = 30.4,
            vectorGroup = "Dyn11",
            voltage = 400.0
        )
        val circuit = CircuitEntity(
            id = 201,
            circuitName = "BUSWAY-FEEDER",
            loadKVA = 160.0,
            fedFrom = "Transformer",
            transformerId = "TX-01",
            length_m = 10.0
        )
        val sizingResult = SizingResult(
            Ib_A = 230.94,
            deviceSize_A = 800.0,
            deviceType = "MCCB",
            cTotalDerating = 1.0,
            iRequired_perCable_A = 230.94,
            productCode = "Elsewedy Spine 800A Cu",
            cableSize_mm2 = "800A Busway (Cu)",
            ratedI_table_A = 800.0,
            capacity_derated_Iz_A = 800.0,
            utilizationPct = 28.8,
            neutral_mm2 = "Full (Integral)",
            earthCPC_mm2 = "Integral Housing",
            voltDrop_V = 0.5,
            vdPct = 0.12,
            cumulativeVDpct = 0.12,
            status = "✓ PASS",
            failReason = "",
            cableMaterial = "Cu",
            insulation = "Air/Film Insulated",
            kFactor = 143.0,
            deviceBreakingKA = 50.0,
            cableOD_mm = 100.0,
            rUsed = 0.02,
            xUsed = 0.02,
            clearingTime_s = 0.1,
            resultStatus = SizingResultStatus.PASS,
            isAmpacityOk = true,
            isVoltageDropOk = true,
            primaryFailureCode = null
        )

        val scResults = ShortCircuitEngine.calculate(
            resolvedCircuits = listOf(circuit),
            circuitParents = mapOf(201 to 0),
            sizingResults = mapOf(201 to sizingResult),
            transformerResults = mapOf("TX-01" to txResult)
        )

        val res = scResults[201]
        assertNotNull(res)
        // Pre-fix: parseNumericSize("800A Busway (Cu)") -> 800.0 mm² -> iwKA = 114.4 kA -> PASS
        // Post-fix: Busway or missing conductor area cannot be parsed as mm² -> DATA MISSING
        assertEquals("DATA MISSING", res!!.status)
        assertEquals("-", res.cableCheck)
    }
}

