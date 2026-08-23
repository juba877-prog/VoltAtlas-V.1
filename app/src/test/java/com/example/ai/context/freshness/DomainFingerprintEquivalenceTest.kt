package com.example.ai.context.freshness

import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.ai.context.freshness.adapters.*
import com.example.ai.context.freshness.contracts.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DomainFingerprintEquivalenceTest {

    private val cableAdapter = CableSizingFreshnessAdapter()
    private val txAdapter = TransformerFreshnessAdapter()
    private val scAdapter = ShortCircuitFreshnessAdapter()
    private val protAdapter = ProtectionAdsFreshnessAdapter()
    private val coordAdapter = CoordinationFreshnessAdapter()

    private class MockUpstreamProvider(
        private val fingerprints: Map<CalculationScope, CalculationFingerprint> = emptyMap()
    ) : UpstreamFingerprintProvider {
        override fun getRecordedFingerprint(scope: CalculationScope): CalculationFingerprint? {
            return fingerprints[scope]
        }
    }

    private val pid = "PRJ_EQUIVALENCE"

    private val sampleTransformers = listOf(
        TransformerEntity(
            projectId = pid,
            transformerId = "TX-01",
            transformerName = "Main Substation Tx",
            kVA = 1600.0,
            voltage = 400,
            percentZ = 6.0,
            xr = 7.0,
            txType = "Oil",
            sourceType = "Grid"
        ),
        TransformerEntity(
            projectId = pid,
            transformerId = "TX-02",
            transformerName = "Auxiliary Tx",
            kVA = 800.0,
            voltage = 400,
            percentZ = 5.0,
            xr = 4.5
        )
    )

    private val sampleCircuits = listOf(
        CircuitEntity(
            id = 1,
            circuitName = "Main-Incomer",
            projectId = pid,
            loadKVA = 500.0,
            voltageV = 400,
            pf = 0.88,
            length_m = 25.0,
            cableType = "Cu/XLPE",
            installation = "In air",
            fedFrom = "Transformer",
            transformerId = "TX-01"
        ),
        CircuitEntity(
            id = 2,
            circuitName = "Sub-Distribution-A",
            projectId = pid,
            loadKVA = 150.0,
            voltageV = 400,
            pf = 0.85,
            length_m = 40.0,
            cableType = "Cu/XLPE",
            installation = "Duct",
            fedFrom = "Main-Incomer",
            parentId = 1,
            transformerId = ""
        ),
        CircuitEntity(
            id = 3,
            circuitName = "Final-Branch-1",
            projectId = pid,
            loadKVA = 30.0,
            voltageV = 400,
            pf = 0.82,
            length_m = 60.0,
            cableType = "Cu/PVC",
            installation = "Conduit",
            fedFrom = "Sub-Distribution-A",
            parentId = 2,
            transformerId = ""
        )
    )

    @Test
    fun testCableSizing_fingerprintEquivalence() = runBlocking {
        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "2", CalculationDomain.CABLE_SIZING)
        val dataProvider = InMemoryProjectFreshnessDataProvider(sampleCircuits, sampleTransformers)

        val suspendRes = cableAdapter.resolveFingerprint(scope, dataProvider, null)
        val syncRes = cableAdapter.resolveFingerprintFromEntities(scope, sampleCircuits, sampleTransformers, null)

        assertTrue(suspendRes is FingerprintResolution.Resolved)
        assertTrue(syncRes is FingerprintResolution.Resolved)

        val fpSuspend = (suspendRes as FingerprintResolution.Resolved).fingerprint
        val fpSync = (syncRes as FingerprintResolution.Resolved).fingerprint

        assertEquals("Cable sizing inputSignature must be identical", fpSuspend.inputSignature, fpSync.inputSignature)
        assertEquals("Cable sizing dependencySignature must be identical", fpSuspend.dependencySignature, fpSync.dependencySignature)
        assertEquals("Cable sizing methodologySignature must be identical", fpSuspend.methodologySignature, fpSync.methodologySignature)
        assertEquals("Cable sizing engineSignature must be identical", fpSuspend.engineSignature, fpSync.engineSignature)
    }

    @Test
    fun testTransformer_fingerprintEquivalence() = runBlocking {
        val scope = CalculationScope(pid, CalculationEntityType.TRANSFORMER, "TX-01", CalculationDomain.TRANSFORMER)
        val dataProvider = InMemoryProjectFreshnessDataProvider(sampleCircuits, sampleTransformers)

        val suspendRes = txAdapter.resolveFingerprint(scope, dataProvider, null)
        val syncRes = txAdapter.resolveFingerprintFromEntities(scope, sampleCircuits, sampleTransformers, null)

        assertTrue(suspendRes is FingerprintResolution.Resolved)
        assertTrue(syncRes is FingerprintResolution.Resolved)

        val fpSuspend = (suspendRes as FingerprintResolution.Resolved).fingerprint
        val fpSync = (syncRes as FingerprintResolution.Resolved).fingerprint

        assertEquals("Transformer inputSignature must be identical", fpSuspend.inputSignature, fpSync.inputSignature)
        assertEquals("Transformer dependencySignature must be identical", fpSuspend.dependencySignature, fpSync.dependencySignature)
        assertEquals("Transformer methodologySignature must be identical", fpSuspend.methodologySignature, fpSync.methodologySignature)
        assertEquals("Transformer engineSignature must be identical", fpSuspend.engineSignature, fpSync.engineSignature)
    }

    @Test
    fun testShortCircuit_fingerprintEquivalence() = runBlocking {
        val upstreamScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "1", CalculationDomain.SHORT_CIRCUIT)
        val mockUpstreamFp = CalculationFingerprint(
            inputSignature = "UPSTREAM_IN_123",
            dependencySignature = "UPSTREAM_DEP_123",
            methodologySignature = "IEC60909",
            engineSignature = "ShortCircuitEngine"
        )
        val upstreamProvider = MockUpstreamProvider(mapOf(upstreamScope to mockUpstreamFp))

        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "2", CalculationDomain.SHORT_CIRCUIT)
        val dataProvider = InMemoryProjectFreshnessDataProvider(sampleCircuits, sampleTransformers)

        val suspendRes = scAdapter.resolveFingerprint(scope, dataProvider, upstreamProvider)
        val syncRes = scAdapter.resolveFingerprintFromEntities(scope, sampleCircuits, sampleTransformers, upstreamProvider)

        assertTrue(suspendRes is FingerprintResolution.Resolved)
        assertTrue(syncRes is FingerprintResolution.Resolved)

        val fpSuspend = (suspendRes as FingerprintResolution.Resolved).fingerprint
        val fpSync = (syncRes as FingerprintResolution.Resolved).fingerprint

        assertEquals("Short circuit inputSignature must be identical", fpSuspend.inputSignature, fpSync.inputSignature)
        assertEquals("Short circuit dependencySignature must be identical", fpSuspend.dependencySignature, fpSync.dependencySignature)
        assertEquals("Short circuit methodologySignature must be identical", fpSuspend.methodologySignature, fpSync.methodologySignature)
        assertEquals("Short circuit engineSignature must be identical", fpSuspend.engineSignature, fpSync.engineSignature)
    }

    @Test
    fun testProtectionAds_fingerprintEquivalence() = runBlocking {
        val upstreamScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "2", CalculationDomain.CABLE_SIZING)
        val mockUpstreamFp = CalculationFingerprint(
            inputSignature = "CABLE_SIZING_IN_ABC",
            dependencySignature = "CABLE_SIZING_DEP_ABC",
            methodologySignature = "BS7671",
            engineSignature = "CableSizingEngine"
        )
        val upstreamProvider = MockUpstreamProvider(mapOf(upstreamScope to mockUpstreamFp))

        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "3", CalculationDomain.PROTECTION_ADS)
        val dataProvider = InMemoryProjectFreshnessDataProvider(sampleCircuits, sampleTransformers)

        val suspendRes = protAdapter.resolveFingerprint(scope, dataProvider, upstreamProvider)
        val syncRes = protAdapter.resolveFingerprintFromEntities(scope, sampleCircuits, sampleTransformers, upstreamProvider)

        assertTrue(suspendRes is FingerprintResolution.Resolved)
        assertTrue(syncRes is FingerprintResolution.Resolved)

        val fpSuspend = (suspendRes as FingerprintResolution.Resolved).fingerprint
        val fpSync = (syncRes as FingerprintResolution.Resolved).fingerprint

        assertEquals("Protection/ADS inputSignature must be identical", fpSuspend.inputSignature, fpSync.inputSignature)
        assertEquals("Protection/ADS dependencySignature must be identical", fpSuspend.dependencySignature, fpSync.dependencySignature)
        assertEquals("Protection/ADS methodologySignature must be identical", fpSuspend.methodologySignature, fpSync.methodologySignature)
        assertEquals("Protection/ADS engineSignature must be identical", fpSuspend.engineSignature, fpSync.engineSignature)
    }

    @Test
    fun testCoordination_fingerprintEquivalence() = runBlocking {
        val upstreamScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "2", CalculationDomain.PROTECTION_ADS)
        val mockUpstreamFp = CalculationFingerprint(
            inputSignature = "PROT_IN_XYZ",
            dependencySignature = "PROT_DEP_XYZ",
            methodologySignature = "IEC60947",
            engineSignature = "ProtectionEngine"
        )
        val upstreamProvider = MockUpstreamProvider(mapOf(upstreamScope to mockUpstreamFp))

        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "3", CalculationDomain.COORDINATION)
        val dataProvider = InMemoryProjectFreshnessDataProvider(sampleCircuits, sampleTransformers)

        val suspendRes = coordAdapter.resolveFingerprint(scope, dataProvider, upstreamProvider)
        val syncRes = coordAdapter.resolveFingerprintFromEntities(scope, sampleCircuits, sampleTransformers, upstreamProvider)

        assertTrue(suspendRes is FingerprintResolution.Resolved)
        assertTrue(syncRes is FingerprintResolution.Resolved)

        val fpSuspend = (suspendRes as FingerprintResolution.Resolved).fingerprint
        val fpSync = (syncRes as FingerprintResolution.Resolved).fingerprint

        assertEquals("Coordination inputSignature must be identical", fpSuspend.inputSignature, fpSync.inputSignature)
        assertEquals("Coordination dependencySignature must be identical", fpSuspend.dependencySignature, fpSync.dependencySignature)
        assertEquals("Coordination methodologySignature must be identical", fpSuspend.methodologySignature, fpSync.methodologySignature)
        assertEquals("Coordination engineSignature must be identical", fpSuspend.engineSignature, fpSync.engineSignature)
    }

    @Test
    fun testReorderedInputs_preservesCanonicalOrderingAndHash() = runBlocking {
        val reorderedCircuits = sampleCircuits.reversed()
        val reorderedTransformers = sampleTransformers.reversed()

        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "3", CalculationDomain.CABLE_SIZING)

        val normalRes = cableAdapter.resolveFingerprintFromEntities(scope, sampleCircuits, sampleTransformers, null)
        val reorderedRes = cableAdapter.resolveFingerprintFromEntities(scope, reorderedCircuits, reorderedTransformers, null)

        assertTrue(normalRes is FingerprintResolution.Resolved)
        assertTrue(reorderedRes is FingerprintResolution.Resolved)

        val fpNormal = (normalRes as FingerprintResolution.Resolved).fingerprint
        val fpReordered = (reorderedRes as FingerprintResolution.Resolved).fingerprint

        assertEquals("Reordered list input must produce identical inputSignature", fpNormal.inputSignature, fpReordered.inputSignature)
        assertEquals("Reordered list input must produce identical dependencySignature", fpNormal.dependencySignature, fpReordered.dependencySignature)
    }

    @Test
    fun testEmptyEntities_returnsConsistentInvalidState() = runBlocking {
        val emptyCircuits = emptyList<CircuitEntity>()
        val emptyTransformers = emptyList<TransformerEntity>()
        val dataProvider = InMemoryProjectFreshnessDataProvider(emptyCircuits, emptyTransformers)

        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "999", CalculationDomain.CABLE_SIZING)

        val suspendRes = cableAdapter.resolveFingerprint(scope, dataProvider, null)
        val syncRes = cableAdapter.resolveFingerprintFromEntities(scope, emptyCircuits, emptyTransformers, null)

        assertTrue(suspendRes is FingerprintResolution.Invalid)
        assertTrue(syncRes is FingerprintResolution.Invalid)
        assertEquals((suspendRes as FingerprintResolution.Invalid).reason, (syncRes as FingerprintResolution.Invalid).reason)
    }

    @Test
    fun testUpstreamDependencyChange_updatesFingerprintIdentically() = runBlocking {
        val upstreamScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "2", CalculationDomain.CABLE_SIZING)

        val fp1 = CalculationFingerprint(
            inputSignature = "IN_V1",
            dependencySignature = "DEP_V1",
            methodologySignature = "M1",
            engineSignature = "E1"
        )
        val fp2 = CalculationFingerprint(
            inputSignature = "IN_V2",
            dependencySignature = "DEP_V2",
            methodologySignature = "M2",
            engineSignature = "E2"
        )

        val provider1 = MockUpstreamProvider(mapOf(upstreamScope to fp1))
        val provider2 = MockUpstreamProvider(mapOf(upstreamScope to fp2))

        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "2", CalculationDomain.SHORT_CIRCUIT)

        val res1 = scAdapter.resolveFingerprintFromEntities(scope, sampleCircuits, sampleTransformers, provider1)
        val res2 = scAdapter.resolveFingerprintFromEntities(scope, sampleCircuits, sampleTransformers, provider2)

        assertTrue(res1 is FingerprintResolution.Resolved)
        assertTrue(res2 is FingerprintResolution.Resolved)

        val depSig1 = (res1 as FingerprintResolution.Resolved).fingerprint.dependencySignature
        val depSig2 = (res2 as FingerprintResolution.Resolved).fingerprint.dependencySignature

        assertNotEquals("Different upstream fingerprint must produce different dependencySignature", depSig1, depSig2)
    }
}
