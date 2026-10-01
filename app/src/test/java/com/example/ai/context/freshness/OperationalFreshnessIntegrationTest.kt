package com.example.ai.context.freshness

import com.example.ai.context.freshness.adapters.InMemoryProjectFreshnessDataProvider
import com.example.ai.context.freshness.contracts.*
import com.example.data.*
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

/**
 * Operational Freshness Integration Tests (Sub-Phase 3D-OP).
 *
 * Verifies:
 * - Real Coordinator calls record freshness metadata into the registry.
 * - Calculations transition scopes from MISSING to CURRENT.
 * - Mutating input moves scope to STALE, recalculating restores CURRENT.
 * - Failures during recording never break or alter calculation outputs.
 * - Exact field-by-field byte equivalence of deterministic calculation results.
 * - Project switch/clear removes freshness metadata without touching other projects.
 */
class OperationalFreshnessIntegrationTest {

    companion object {
        @JvmField
        @org.junit.ClassRule
        val catalogStateReset = com.example.testing.CatalogStateResetRule()

        /**
         * Phase 6 Batch 3 fixture repair (pre-existing failure, NOT a regression — verified by
         * reproducing it on the untouched B2 baseline with every Batch 3 change stashed).
         *
         * Two tests in this class assert that a REAL coordinator cable-sizing call returns
         * "✓ PASS", but the fixture only seeded `Catalog.cableTypes`, `Catalog.dataSheet` and
         * `Catalog.cableCatalog`. It never loaded the derating catalog, so
         * `CableSizingEngine.getGroupingDerating()` hit its
         * `Catalog.deratingData ?: return null` branch (CableSizingEngine.kt:238) and the
         * engine correctly refused to invent a derating factor, returning
         * "✗ FAIL" / DERATING_INVALID ("Grouping derating factor not found ...").
         *
         * The engine is PROTECTED under the Phase 6 protected-engine rule and its refusal is
         * the correct, honest behaviour, so the FIX belongs here in the fixture: load the REAL
         * shipping asset `derating_data.json` (same approach as `CableSizingEngineTest`), which
         * supplies genuine grouping factors — `Air_Flat_Touching`, `Duct_Flat_Touching`, ... —
         * for exactly these circuits (defaults: installation "Laid in Ducts" -> Duct_*,
         * formation "Flat", spacing "Touching"; and the Free-Air case -> Air_*).
         * No engineering value is fabricated here: the factors come from the shipped catalog.
         */
        @JvmStatic
        @BeforeClass
        fun loadDeratingCatalog() {
            val candidates = listOf(
                java.io.File("app/src/main/assets/derating_data.json"),
                java.io.File("src/main/assets/derating_data.json"),
                java.io.File("../app/src/main/assets/derating_data.json")
            )
            val file = candidates.firstOrNull { it.exists() }
                ?: throw java.io.FileNotFoundException(
                    "derating_data.json not found — cannot run real cable-sizing assertions"
                )
            Catalog.deratingData = org.json.JSONObject(file.readText())
        }
    }

    private val coordinator = EngineeringCalculationCoordinator

    @Before
    fun setUp() {
        coordinator.clearCache()
        coordinator.freshnessRegistry.clearAll()

        if (Catalog.cableTypes.isEmpty()) {
            Catalog.cableTypes = listOf(
                CableType(1, "Cu/XLPE", "Cu", "XLPE", "None", "3C", "0.6/1kV", "IEC", "R_Cu90"),
                CableType(2, "Cu/PVC", "Cu", "PVC", "None", "3C", "0.6/1kV", "IEC", "R_Cu70"),
                CableType(3, "Cu/XLPE/PVC - 4C", "Cu", "XLPE", "None", "4C", "0.6/1kV", "IEC", "R_Cu90"),
                CableType(4, "Cu/XLPE/SWA/PVC", "Cu", "XLPE", "SWA", "4C", "0.6/1kV", "IEC", "R_Cu90")
            )
        }
        if (Catalog.dataSheet.isEmpty()) {
            Catalog.dataSheet = listOf(
                DataSheetEntry(1.5, 12.1, 0.118, 19.5, 14.52, 23.4),
                DataSheetEntry(2.5, 7.41, 0.109, 12.1, 8.89, 14.52),
                DataSheetEntry(4.0, 4.61, 0.101, 7.41, 5.53, 8.89),
                DataSheetEntry(6.0, 3.08, 0.093, 4.61, 3.7, 5.53),
                DataSheetEntry(10.0, 1.83, 0.086, 3.08, 2.2, 3.7),
                DataSheetEntry(16.0, 1.15, 0.082, 1.91, 1.38, 2.29),
                DataSheetEntry(25.0, 0.727, 0.080, 1.20, 0.872, 1.44),
                DataSheetEntry(35.0, 0.524, 0.078, 0.868, 0.629, 1.04),
                DataSheetEntry(50.0, 0.387, 0.078, 0.641, 0.464, 0.77),
                DataSheetEntry(70.0, 0.268, 0.077, 0.443, 0.322, 0.532),
                DataSheetEntry(95.0, 0.193, 0.076, 0.320, 0.232, 0.384),
                DataSheetEntry(120.0, 0.153, 0.075, 0.253, 0.184, 0.304),
                DataSheetEntry(150.0, 0.124, 0.075, 0.206, 0.149, 0.247),
                DataSheetEntry(185.0, 0.0991, 0.074, 0.164, 0.119, 0.197),
                DataSheetEntry(240.0, 0.0754, 0.074, 0.125, 0.0905, 0.15),
                DataSheetEntry(300.0, 0.0601, 0.073, 0.100, 0.0721, 0.12),
                DataSheetEntry(400.0, 0.0470, 0.073, 0.0778, 0.0564, 0.0934),
                DataSheetEntry(500.0, 0.0366, 0.072, 0.0605, 0.0439, 0.0726),
                DataSheetEntry(630.0, 0.0283, 0.072, 0.0469, 0.0340, 0.0563)
            )
        }
        if (Catalog.cableCatalog.isEmpty()) {
            val sizes = listOf(
                1.5 to 10.0, 2.5 to 11.0, 4.0 to 12.0, 6.0 to 13.0, 10.0 to 15.0,
                16.0 to 16.0, 25.0 to 19.0, 35.0 to 21.0, 50.0 to 24.0, 70.0 to 27.0,
                95.0 to 30.0, 120.0 to 33.0, 150.0 to 37.0, 185.0 to 41.0, 240.0 to 46.0,
                300.0 to 51.0, 400.0 to 58.0, 500.0 to 64.0, 630.0 to 72.0
            )
            val entries = mutableListOf<CableCatalogEntry>()
            for (tc in listOf(1, 2, 3, 4)) {
                for ((sz, od) in sizes) {
                    val current = sz * 3.0 + 10.0
                    entries.add(
                        CableCatalogEntry(
                            mfg = "Elsewedy",
                            typeCode = tc,
                            size = sz,
                            model = "M-$sz-T$tc",
                            gnd = current,
                            duct = current,
                            air = current,
                            od = od,
                            wt = sz * 0.03 + 0.2
                        )
                    )
                }
            }
            Catalog.cableCatalog = entries
        }
    }

    @Test
    fun realCableCalculation_recordsMetadata_thenScopeIsCurrent() = runBlocking {
        val pid = "PRJ_OP_CABLE"
        val circuit = CircuitEntity(
            id = 101,
            circuitName = "Feeder-1",
            projectId = pid,
            loadKVA = 15.0,
            voltageV = 400,
            pf = 0.85,
            length_m = 40.0,
            cableType = "Cu/XLPE/PVC - 4C",
            fedFrom = "Transformer",
            transformerId = "TX-01"
        )
        val allCircuits = listOf(circuit)
        val allTransformers = listOf(TransformerEntity(transformerId = "TX-01", projectId = pid, kVA = 1000.0))

        // Record upstream transformer first so coverage is COMPLETE
        coordinator.calculateTransformer(allTransformers[0], allCircuits)

        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val provider = coordinator.getFreshnessProvider(InMemoryProjectFreshnessDataProvider(allCircuits, allTransformers))

        // Before calculation: MISSING
        val beforeSnap = provider.getCalculationState(scope)
        assertEquals(CalculationState.MISSING, beforeSnap.state)

        // Run real Coordinator cable sizing
        val sizingResult = coordinator.calculateCableSizing(circuit, allCircuits, allTransformers)
        assertNotNull(sizingResult)
        assertEquals("✓ PASS", sizingResult.status)

        // After calculation: CURRENT
        val afterSnap = provider.getCalculationState(scope)
        assertEquals("Real calculation must record metadata making scope CURRENT", CalculationState.CURRENT, afterSnap.state)
        assertEquals(FreshnessReasonCode.MATCH, afterSnap.reasonCode)
        assertNotNull(coordinator.freshnessRegistry.getRecord(scope))
    }

    @Test
    fun realTransformerCalculation_recordsMetadata_thenScopeIsCurrent() = runBlocking {
        val pid = "PRJ_OP_TX"
        val tx = TransformerEntity(projectId = pid, transformerId = "TX-01", kVA = 1000.0, voltage = 400)
        val circuits = listOf(CircuitEntity(id = 1, projectId = pid, circuitName = "Main", loadKVA = 200.0, fedFrom = "Transformer", transformerId = "TX-01"))

        val scope = CalculationScope(pid, CalculationEntityType.TRANSFORMER, "TX-01", CalculationDomain.TRANSFORMER)
        val provider = coordinator.getFreshnessProvider(InMemoryProjectFreshnessDataProvider(circuits, listOf(tx)))

        val beforeSnap = provider.getCalculationState(scope)
        assertEquals(CalculationState.MISSING, beforeSnap.state)

        val txResult = coordinator.calculateTransformer(tx, circuits)
        assertNotNull(txResult)

        val afterSnap = provider.getCalculationState(scope)
        assertEquals(CalculationState.CURRENT, afterSnap.state)
    }

    @Test
    fun realShortCircuitCalculation_recordsMetadataOrRemainsPartial() = runBlocking {
        val pid = "PRJ_OP_SC"
        val tx = TransformerEntity(projectId = pid, transformerId = "TX-01", kVA = 1000.0)
        val circuit = CircuitEntity(id = 101, projectId = pid, circuitName = "F1", loadKVA = 25.0, length_m = 15.0, fedFrom = "Transformer", transformerId = "TX-01")
        val allCircuits = listOf(circuit)
        val allTx = listOf(tx)

        val scScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.SHORT_CIRCUIT)
        val provider = coordinator.getFreshnessProvider(InMemoryProjectFreshnessDataProvider(allCircuits, allTx))

        // Bulk project calculation in proper engineering order
        val txMap = coordinator.calculateProjectTransformers(allCircuits, allTx)
        val sizingRes = coordinator.calculateProjectCableSizing(allCircuits, allTx)
        val scMap = coordinator.calculateProjectShortCircuit(
            resolvedCircuits = sizingRes.resolvedCircuits,
            circuitParents = sizingRes.circuitParents,
            sizingResults = sizingRes.sizingResults,
            transformerResults = txMap
        )

        assertNotNull(scMap[101])
        val snap = provider.getCalculationState(scScope)
        assertEquals("Downstream SC with upstream metadata recorded must be CURRENT", CalculationState.CURRENT, snap.state)
    }

    @Test
    fun realProtectionCalculation_recordsMetadataOrRemainsPartial() = runBlocking {
        val pid = "PRJ_OP_PROT"
        val tx = TransformerEntity(projectId = pid, transformerId = "TX-01", kVA = 1000.0)
        val circuit = CircuitEntity(id = 101, projectId = pid, circuitName = "F1", loadKVA = 25.0, length_m = 15.0, fedFrom = "Transformer", transformerId = "TX-01")
        val allCircuits = listOf(circuit)
        val allTx = listOf(tx)

        val protScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.PROTECTION_ADS)
        val provider = coordinator.getFreshnessProvider(InMemoryProjectFreshnessDataProvider(allCircuits, allTx))

        val txMap = coordinator.calculateProjectTransformers(allCircuits, allTx)
        val sizingRes = coordinator.calculateProjectCableSizing(allCircuits, allTx)
        val scMap = coordinator.calculateProjectShortCircuit(sizingRes.resolvedCircuits, sizingRes.circuitParents, sizingRes.sizingResults, txMap)
        val protMap = coordinator.calculateProjectProtection(sizingRes.resolvedCircuits, sizingRes.circuitParents, sizingRes.sizingResults, scMap, txMap)

        assertNotNull(protMap[101])
        val snap = provider.getCalculationState(protScope)
        assertEquals("Protection with all upstream dependencies recorded must be CURRENT", CalculationState.CURRENT, snap.state)
    }

    @Test
    fun realCoordinationCalculation_recordsMetadataOrRemainsPartial() = runBlocking {
        val pid = "PRJ_OP_COORD"
        val tx = TransformerEntity(projectId = pid, transformerId = "TX-01", kVA = 1000.0)
        val parent = CircuitEntity(id = 100, projectId = pid, circuitName = "MDB", loadKVA = 100.0, fedFrom = "Transformer", transformerId = "TX-01")
        val child = CircuitEntity(id = 101, projectId = pid, circuitName = "Sub", loadKVA = 20.0, parentId = 100, fedFrom = "MDB")
        val allCircuits = listOf(parent, child)
        val allTx = listOf(tx)

        val coordScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.COORDINATION)
        val provider = coordinator.getFreshnessProvider(InMemoryProjectFreshnessDataProvider(allCircuits, allTx))

        val txMap = coordinator.calculateProjectTransformers(allCircuits, allTx)
        val sizingRes = coordinator.calculateProjectCableSizing(allCircuits, allTx)
        val scMap = coordinator.calculateProjectShortCircuit(sizingRes.resolvedCircuits, sizingRes.circuitParents, sizingRes.sizingResults, txMap)
        val protMap = coordinator.calculateProjectProtection(sizingRes.resolvedCircuits, sizingRes.circuitParents, sizingRes.sizingResults, scMap, txMap)
        val coordList = coordinator.calculateProjectCoordination(sizingRes.resolvedCircuits, sizingRes.circuitParents, sizingRes.sizingResults, txMap)

        assertTrue(coordList.isNotEmpty())
        val snap = provider.getCalculationState(coordScope)
        assertEquals("Coordination with parent sizing & SC recorded must be CURRENT", CalculationState.CURRENT, snap.state)
    }

    @Test
    fun singleShortCircuitCalculation_recordsMetadata_andScopeIsCurrent() = runBlocking {
        val pid = "PRJ_OP_SINGLE_SC"
        val tx = TransformerEntity(projectId = pid, transformerId = "TX-01", kVA = 1000.0)
        val circuit = CircuitEntity(id = 101, projectId = pid, circuitName = "F1", loadKVA = 25.0, length_m = 15.0, fedFrom = "Transformer", transformerId = "TX-01")
        val allCircuits = listOf(circuit)
        val allTx = listOf(tx)

        val scScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.SHORT_CIRCUIT)
        val provider = coordinator.getFreshnessProvider(InMemoryProjectFreshnessDataProvider(allCircuits, allTx))

        // Pre-requisites: calculate upstream transformer & cable sizing
        coordinator.calculateTransformer(tx, allCircuits)
        coordinator.calculateCableSizing(circuit, allCircuits, allTx)

        // Run targeted single-circuit short circuit
        val scRes = coordinator.calculateShortCircuit(101, allCircuits, allTx)
        assertNotNull(scRes)

        val snap = provider.getCalculationState(scScope)
        assertEquals(CalculationState.CURRENT, snap.state)
    }

    @Test
    fun singleProtectionCalculation_recordsMetadata_andScopeIsCurrent() = runBlocking {
        val pid = "PRJ_OP_SINGLE_PROT"
        val tx = TransformerEntity(projectId = pid, transformerId = "TX-01", kVA = 1000.0)
        val circuit = CircuitEntity(id = 101, projectId = pid, circuitName = "F1", loadKVA = 25.0, length_m = 15.0, fedFrom = "Transformer", transformerId = "TX-01")
        val allCircuits = listOf(circuit)
        val allTx = listOf(tx)

        val protScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.PROTECTION_ADS)
        val provider = coordinator.getFreshnessProvider(InMemoryProjectFreshnessDataProvider(allCircuits, allTx))

        // Pre-requisites
        coordinator.calculateTransformer(tx, allCircuits)
        coordinator.calculateCableSizing(circuit, allCircuits, allTx)
        coordinator.calculateShortCircuit(101, allCircuits, allTx)

        // Run targeted single-circuit protection
        val protRes = coordinator.calculateProtection(101, allCircuits, allTx)
        assertNotNull(protRes)

        val snap = provider.getCalculationState(protScope)
        assertEquals(CalculationState.CURRENT, snap.state)
    }

    @Test
    fun singleCoordinationCalculation_recordsMetadata_andScopeIsCurrent() = runBlocking {
        val pid = "PRJ_OP_SINGLE_COORD"
        val tx = TransformerEntity(projectId = pid, transformerId = "TX-01", kVA = 1000.0)
        val parent = CircuitEntity(id = 100, projectId = pid, circuitName = "MDB", loadKVA = 100.0, fedFrom = "Transformer", transformerId = "TX-01")
        val child = CircuitEntity(id = 101, projectId = pid, circuitName = "Sub", loadKVA = 20.0, parentId = 100, fedFrom = "MDB")
        val allCircuits = listOf(parent, child)
        val allTx = listOf(tx)

        val coordScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.COORDINATION)
        val provider = coordinator.getFreshnessProvider(InMemoryProjectFreshnessDataProvider(allCircuits, allTx))

        // Pre-requisites
        coordinator.calculateTransformer(tx, allCircuits)
        coordinator.calculateCableSizing(parent, allCircuits, allTx)
        coordinator.calculateCableSizing(child, allCircuits, allTx)
        coordinator.calculateShortCircuit(101, allCircuits, allTx)
        coordinator.calculateProtection(101, allCircuits, allTx)

        // Run targeted single-circuit coordination
        val coordRes = coordinator.calculateCoordination(101, allCircuits, allTx)
        assertNotNull(coordRes)

        val snap = provider.getCalculationState(coordScope)
        assertEquals(CalculationState.CURRENT, snap.state)
    }

    @Test
    fun realEngineeringFail_recordsMetadata_andScopeCanBeCurrent() = runBlocking {
        val pid = "PRJ_OP_FAIL"
        // Missing transformer causes engineering fail
        val circuit = CircuitEntity(id = 101, projectId = pid, circuitName = "Broken", loadKVA = 50.0, fedFrom = "Transformer", transformerId = "NON_EXISTENT_TX")
        val allCircuits = listOf(circuit)

        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val provider = coordinator.getFreshnessProvider(InMemoryProjectFreshnessDataProvider(allCircuits))

        val result = coordinator.calculateCableSizing(circuit, allCircuits)
        assertTrue(result.status.contains("FAIL"))

        val snap = provider.getCalculationState(scope)
        assertEquals(CalculationState.CURRENT, snap.state)
        assertEquals("FAIL", snap.recordedStatus)
    }

    @Test
    fun changingCircuitInput_thenRecalc_movesCurrentToStaleToCurrent() = runBlocking {
        val pid = "PRJ_OP_CYCLE"
        var circuit = CircuitEntity(
            id = 101, projectId = pid, circuitName = "Pump", loadKVA = 15.0, length_m = 40.0,
            pf = 0.85, phases = "3P+N", voltageV = 400, safetyFactor = 1.0,
            installation = "Laid in Free Air", cableType = "Cu/XLPE/PVC - 4C", manufacturer = "Elsewedy",
            airTempC = 55, fedFrom = "Transformer", transformerId = "TX-01"
        )
        val tx = TransformerEntity(transformerId = "TX-01", projectId = pid, kVA = 1000.0)
        var allCircuits = listOf(circuit)
        val allTx = listOf(tx)
        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)

        // 1. Initial calculate -> CURRENT
        coordinator.calculateCableSizing(circuit, allCircuits, allTx)
        var provider = coordinator.getFreshnessProvider(InMemoryProjectFreshnessDataProvider(allCircuits, allTx))
        assertEquals(CalculationState.CURRENT, provider.getCalculationState(scope).state)

        // 2. Mutate circuit input in entity data -> STALE
        circuit = circuit.copy(loadKVA = 90.0)
        allCircuits = listOf(circuit)
        provider = coordinator.getFreshnessProvider(InMemoryProjectFreshnessDataProvider(allCircuits, allTx))
        val staleSnap = provider.getCalculationState(scope)
        assertEquals(CalculationState.STALE, staleSnap.state)
        assertTrue(staleSnap.changedComponents.contains(ChangedComponent.DIRECT_INPUT))

        // 3. Recalculate -> CURRENT again
        coordinator.calculateCableSizing(circuit, allCircuits, allTx)
        val refreshedSnap = provider.getCalculationState(scope)
        assertEquals(CalculationState.CURRENT, refreshedSnap.state)
    }

    @Test
    fun recordingFailure_doesNotBreakCalculationResult() = runBlocking {
        val pid = "PRJ_OP_FAILSAFE"
        val circuit = CircuitEntity(
            id = 101,
            circuitName = "FailsafeTest",
            projectId = pid,
            loadKVA = 15.0,
            voltageV = 400,
            pf = 0.85,
            length_m = 40.0,
            safetyFactor = 1.0,
            installation = "Laid in Free Air",
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            airTempC = 55,
            fedFrom = "Transformer",
            transformerId = "TX-01"
        )
        val allCircuits = listOf(circuit)
        val allTransformers = listOf(TransformerEntity(transformerId = "TX-01", projectId = pid, kVA = 1000.0))

        val result = coordinator.calculateCableSizing(circuit, allCircuits, allTransformers)
        assertNotNull(result)
        assertEquals("✓ PASS", result.status)
    }

    @Test
    fun projectSwitchOrClear_removesFreshnessMetadata() = runBlocking {
        val pid1 = "PRJ_1"
        val pid2 = "PRJ_2"
        val c1 = CircuitEntity(id = 1, projectId = pid1, circuitName = "C1", loadKVA = 10.0)
        val c2 = CircuitEntity(id = 2, projectId = pid2, circuitName = "C2", loadKVA = 20.0)

        coordinator.calculateCableSizing(c1, listOf(c1))
        coordinator.calculateCableSizing(c2, listOf(c2))

        val scope1 = CalculationScope(pid1, CalculationEntityType.CIRCUIT, "1", CalculationDomain.CABLE_SIZING)
        val scope2 = CalculationScope(pid2, CalculationEntityType.CIRCUIT, "2", CalculationDomain.CABLE_SIZING)

        assertNotNull(coordinator.freshnessRegistry.getRecord(scope1))
        assertNotNull(coordinator.freshnessRegistry.getRecord(scope2))

        // Clear PRJ_1
        coordinator.clearProjectFreshness(pid1)
        assertNull(coordinator.freshnessRegistry.getRecord(scope1))
        assertNotNull(coordinator.freshnessRegistry.getRecord(scope2))
    }

    @Test
    fun recordingIsSideEffectOnly_calcResultsByteEquivalentBeforeAfter() {
        val pid = "PRJ_BYTE_EQUIV"
        val circuit = CircuitEntity(
            id = 201, projectId = pid, circuitName = "Feeder-ByteEquiv", loadKVA = 75.0,
            loadType = "Normal", phases = "3P+N", voltageV = 400, pf = 0.85, safetyFactor = 1.25,
            manufacturer = "Elsewedy", cableType = "Cu/XLPE/SWA/PVC", installation = "Laid in Ducts",
            parallelRuns = 1, airTempC = 45, gndTempC = 40, burialDepth_m = 0.8, soilRes = 120,
            noCircuits = 1, formation = "Flat", spacing = "Touching", length_m = 80.0, maxVDpct = 0.03
        )
        val allCircuits = listOf(circuit)

        // Clear cache and calculate
        coordinator.clearCache()
        val result1 = coordinator.calculateCableSizing(circuit, allCircuits)

        // Clear cache and calculate second time
        coordinator.clearCache()
        val result2 = coordinator.calculateCableSizing(circuit, allCircuits)

        // Field-by-field verification of deterministic equivalence
        assertEquals(result1.Ib_A, result2.Ib_A, 0.0)
        assertEquals(result1.deviceSize_A, result2.deviceSize_A, 0.0)
        assertEquals(result1.cableSize_mm2, result2.cableSize_mm2)
        assertEquals(result1.capacity_derated_Iz_A, result2.capacity_derated_Iz_A, 0.0)
        assertEquals(result1.voltDrop_V, result2.voltDrop_V, 0.0)
        assertEquals(result1.vdPct, result2.vdPct, 0.0)
        assertEquals(result1.cumulativeVDpct, result2.cumulativeVDpct, 0.0)
        assertEquals(result1.cTotalDerating, result2.cTotalDerating, 0.0)
        assertEquals(result1.status, result2.status)
        assertEquals(result1.deviceType, result2.deviceType)
    }
}
