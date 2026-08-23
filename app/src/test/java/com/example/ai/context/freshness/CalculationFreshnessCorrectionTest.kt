package com.example.ai.context.freshness

import com.example.ai.context.freshness.adapters.*
import com.example.ai.context.freshness.contracts.*
import com.example.ai.context.freshness.internal.*
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.ai.context.revision.ProjectRevisionSnapshot
import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive correction tests for Sub-Phase 3D.
 *
 * Covers:
 * - Decision precedence (INVALID and PARTIAL before RUNNING)
 * - Complete upstream fingerprint chaining (Option A) across Short Circuit, Protection, and Coordination
 * - Catalog & methodology identity versioning
 * - Typed execution error lifecycle
 * - Production lifecycle recorder integration
 * - Structural field-by-field mutation sensitivity for Cable and Transformer
 * - Production observability characteristics
 */
class CalculationFreshnessCorrectionTest {

    private lateinit var registry: InMemoryCalculationFreshnessMetadataRegistry
    private lateinit var dataProvider: FakeDataProvider
    private lateinit var revisionProvider: FakeRevisionProvider
    private lateinit var freshnessProvider: DefaultCalculationFreshnessProvider

    private val upstreamProvider = object : UpstreamFingerprintProvider {
        override fun getRecordedFingerprint(scope: CalculationScope): CalculationFingerprint? {
            return registry.getRecord(scope)?.fingerprint
        }
    }

    @Before
    fun setUp() {
        registry = InMemoryCalculationFreshnessMetadataRegistry()
        dataProvider = FakeDataProvider()
        revisionProvider = FakeRevisionProvider()
        freshnessProvider = DefaultCalculationFreshnessProvider(
            revisionProvider = revisionProvider,
            metadataRegistry = registry,
            projectDataProvider = dataProvider
        )
    }

    // =========================================================================
    // 1. Decision Precedence
    // =========================================================================

    @Test
    fun invalidInputsWithActiveMarker_returnsInvalid() = runBlocking {
        val pid = "PRJ_PREC_1"
        revisionProvider.setRevision(pid, 1L)
        // Circuit 999 does NOT exist in dataProvider
        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "999", CalculationDomain.CABLE_SIZING)
        registry.startExecution(scope, false)

        val snap = freshnessProvider.getCalculationState(scope)
        assertEquals("INVALID must take precedence over RUNNING", CalculationState.INVALID, snap.state)
        assertEquals(FreshnessReasonCode.INVALID_INPUTS, snap.reasonCode)
    }

    @Test
    fun missingEntityWithActiveMarker_returnsInvalid() = runBlocking {
        val pid = "PRJ_PREC_2"
        revisionProvider.setRevision(pid, 1L)
        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "888", CalculationDomain.CABLE_SIZING)
        registry.startExecution(scope, false)

        val snap = freshnessProvider.getCalculationState(scope)
        assertEquals(CalculationState.INVALID, snap.state)
    }

    @Test
    fun partialCoverageWithActiveMarker_doesNotReturnRunning() = runBlocking {
        val pid = "PRJ_PREC_3"
        revisionProvider.setRevision(pid, 1L)
        dataProvider.addCircuit(CircuitEntity(id = 101, projectId = pid, circuitName = "F1", loadKVA = 25.0, length_m = 15.0, fedFrom = "Transformer", transformerId = "TX-01"))
        dataProvider.addTransformer(TransformerEntity(projectId = pid, transformerId = "TX-01", kVA = 1000.0))

        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.SHORT_CIRCUIT)
        registry.startExecution(scope, false)

        val snap = freshnessProvider.getCalculationState(scope)
        // SC without upstream fingerprints is PARTIAL -> STALE (PROVENANCE_INCOMPLETE), not RUNNING
        assertNotEquals("PARTIAL coverage must not return RUNNING", CalculationState.RUNNING, snap.state)
        assertEquals(CalculationState.STALE, snap.state)
        assertEquals(FreshnessReasonCode.PROVENANCE_INCOMPLETE, snap.reasonCode)
    }

    @Test
    fun validNoRecordWithMarker_returnsRunning() = runBlocking {
        val pid = "PRJ_PREC_4"
        revisionProvider.setRevision(pid, 1L)
        dataProvider.addCircuit(CircuitEntity(id = 101, projectId = pid, circuitName = "F1", loadKVA = 25.0, length_m = 15.0))
        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        registry.startExecution(scope, false)

        val snap = freshnessProvider.getCalculationState(scope)
        assertEquals(CalculationState.RUNNING, snap.state)
    }

    @Test
    fun validPriorRecordWithMarker_returnsRevalidating() = runBlocking {
        val pid = "PRJ_PREC_5"
        revisionProvider.setRevision(pid, 1L)
        dataProvider.addCircuit(CircuitEntity(id = 101, projectId = pid, circuitName = "F1", loadKVA = 25.0, length_m = 15.0))
        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)

        val fp = (CableSizingFreshnessAdapter().resolveFingerprint(scope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(scope, 1L, fp, 1000L, "PASS", CalculationProvenance("CableSizingEngine")))
        registry.startExecution(scope, true)

        val snap = freshnessProvider.getCalculationState(scope)
        assertEquals(CalculationState.REVALIDATING, snap.state)
    }

    // =========================================================================
    // 2. Upstream Fingerprint Chaining — Short Circuit & Protection
    // =========================================================================

    @Test
    fun shortCircuit_missingUpstreamSizingFingerprint_cannotReturnCurrent() = runBlocking {
        val pid = "PRJ_CHAIN_SC_1"
        revisionProvider.setRevision(pid, 1L)
        dataProvider.addCircuit(CircuitEntity(id = 101, projectId = pid, circuitName = "F1", loadKVA = 25.0, length_m = 15.0, fedFrom = "Transformer", transformerId = "TX-01"))
        dataProvider.addTransformer(TransformerEntity(projectId = pid, transformerId = "TX-01", kVA = 1000.0))

        // No CABLE_SIZING or TRANSFORMER metadata recorded -> SC is PARTIAL
        val scScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.SHORT_CIRCUIT)
        val scRes = ShortCircuitFreshnessAdapter().resolveFingerprint(scScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved
        assertEquals(FingerprintCoverage.PARTIAL, scRes.coverage)

        // Record SC metadata anyway
        registry.recordCalculationCompleted(CalculationRecordMetadata(scScope, 1L, scRes.fingerprint, 1000L, "PASS", CalculationProvenance("ShortCircuitEngine")))

        val snap = freshnessProvider.getCalculationState(scScope)
        assertNotEquals("PARTIAL domain must not return CURRENT", CalculationState.CURRENT, snap.state)
        assertEquals(CalculationState.STALE, snap.state)
        assertEquals(FreshnessReasonCode.PROVENANCE_INCOMPLETE, snap.reasonCode)
    }

    @Test
    fun shortCircuit_upstreamSizingFingerprintChange_returnsDependencyChanged() = runBlocking {
        val pid = "PRJ_CHAIN_SC_2"
        revisionProvider.setRevision(pid, 1L)
        dataProvider.addCircuit(CircuitEntity(id = 101, projectId = pid, circuitName = "F1", loadKVA = 25.0, length_m = 15.0, fedFrom = "Transformer", transformerId = "TX-01"))
        dataProvider.addTransformer(TransformerEntity(projectId = pid, transformerId = "TX-01", kVA = 1000.0))

        // Record CABLE_SIZING and TRANSFORMER metadata
        val cableScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val cableFp = (CableSizingFreshnessAdapter().resolveFingerprint(cableScope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(cableScope, 1L, cableFp, 1000L, "PASS", CalculationProvenance("CableSizingEngine")))

        val txScope = CalculationScope(pid, CalculationEntityType.TRANSFORMER, "TX-01", CalculationDomain.TRANSFORMER)
        val txFp = (TransformerFreshnessAdapter().resolveFingerprint(txScope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(txScope, 1L, txFp, 1000L, "PASS", CalculationProvenance("TransformerEngine")))

        // Now SC should be COMPLETE
        val scScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.SHORT_CIRCUIT)
        val scRes = ShortCircuitFreshnessAdapter().resolveFingerprint(scScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved
        assertEquals(FingerprintCoverage.COMPLETE, scRes.coverage)

        // Record SC metadata
        registry.recordCalculationCompleted(CalculationRecordMetadata(scScope, 1L, scRes.fingerprint, 1000L, "PASS", CalculationProvenance("ShortCircuitEngine")))

        // Now change upstream cable sizing (modify circuit loadKVA)
        dataProvider.addCircuit(CircuitEntity(id = 101, projectId = pid, circuitName = "F1", loadKVA = 50.0, length_m = 15.0, fedFrom = "Transformer", transformerId = "TX-01"))
        val newCableFp = (CableSizingFreshnessAdapter().resolveFingerprint(cableScope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(cableScope, 1L, newCableFp, 2000L, "PASS", CalculationProvenance("CableSizingEngine")))

        // SC fingerprint should now differ because upstream cable fingerprint changed
        val snap = freshnessProvider.getCalculationState(scScope)
        assertEquals(CalculationState.STALE, snap.state)
        assertTrue(snap.changedComponents.contains(ChangedComponent.UPSTREAM_DEPENDENCY))
    }

    @Test
    fun protection_missingShortCircuitFingerprint_cannotReturnCurrent() = runBlocking {
        val pid = "PRJ_CHAIN_PROT_1"
        revisionProvider.setRevision(pid, 1L)
        dataProvider.addCircuit(CircuitEntity(id = 101, projectId = pid, circuitName = "F1", loadKVA = 25.0, length_m = 15.0))

        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.PROTECTION_ADS)
        val res = ProtectionAdsFreshnessAdapter().resolveFingerprint(scope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved
        assertEquals(FingerprintCoverage.PARTIAL, res.coverage)
    }

    // =========================================================================
    // 3. Upstream Fingerprint Chaining — Coordination (BLOCKER 1 Fixes)
    // =========================================================================

    @Test
    fun coordination_missingParentOrShortCircuitFingerprint_cannotReturnCurrent() = runBlocking {
        val pid = "PRJ_COORD_MISSING"
        revisionProvider.setRevision(pid, 1L)
        val parent = CircuitEntity(id = 100, projectId = pid, circuitName = "MDB", loadKVA = 100.0)
        val child = CircuitEntity(id = 101, projectId = pid, circuitName = "Sub", loadKVA = 20.0, parentId = 100, fedFrom = "MDB")
        dataProvider.addCircuit(parent)
        dataProvider.addCircuit(child)

        // Only record downstream CABLE_SIZING, but omit parent CABLE_SIZING and downstream SHORT_CIRCUIT
        val childCableScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val childCableFp = (CableSizingFreshnessAdapter().resolveFingerprint(childCableScope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(childCableScope, 1L, childCableFp, 1000L, "PASS", CalculationProvenance("CableSizingEngine")))

        val coordScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.COORDINATION)
        val res = CoordinationFreshnessAdapter().resolveFingerprint(coordScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved
        assertEquals("Missing parent CABLE_SIZING or downstream SHORT_CIRCUIT must yield PARTIAL", FingerprintCoverage.PARTIAL, res.coverage)

        // Record metadata with partial fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(coordScope, 1L, res.fingerprint, 1000L, "PASS", CalculationProvenance("CoordinationEngine")))

        val snap = freshnessProvider.getCalculationState(coordScope)
        assertNotEquals("PARTIAL coverage must not return CURRENT", CalculationState.CURRENT, snap.state)
        assertEquals(CalculationState.STALE, snap.state)
        assertEquals(FreshnessReasonCode.PROVENANCE_INCOMPLETE, snap.reasonCode)
    }

    @Test
    fun coordination_parentCableSizingChange_returnsDependencyChanged() = runBlocking {
        val pid = "PRJ_COORD_PARENT_CHANGE"
        revisionProvider.setRevision(pid, 1L)
        val parent = CircuitEntity(id = 100, projectId = pid, circuitName = "MDB", loadKVA = 100.0)
        val child = CircuitEntity(id = 101, projectId = pid, circuitName = "Sub", loadKVA = 20.0, parentId = 100, fedFrom = "MDB")
        dataProvider.addCircuit(parent)
        dataProvider.addCircuit(child)

        // 1. Record parent CABLE_SIZING
        val parentCableScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "100", CalculationDomain.CABLE_SIZING)
        val parentCableFp = (CableSizingFreshnessAdapter().resolveFingerprint(parentCableScope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(parentCableScope, 1L, parentCableFp, 1000L, "PASS", CalculationProvenance("CableSizingEngine")))

        // 2. Record child CABLE_SIZING
        val childCableScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val childCableFp = (CableSizingFreshnessAdapter().resolveFingerprint(childCableScope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(childCableScope, 1L, childCableFp, 1000L, "PASS", CalculationProvenance("CableSizingEngine")))

        // 3. Record child SHORT_CIRCUIT
        val childScScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.SHORT_CIRCUIT)
        val childScFp = (ShortCircuitFreshnessAdapter().resolveFingerprint(childScScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(childScScope, 1L, childScFp, 1000L, "PASS", CalculationProvenance("ShortCircuitEngine")))

        // 4. Record child PROTECTION_ADS
        val childProtScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.PROTECTION_ADS)
        val childProtFp = (ProtectionAdsFreshnessAdapter().resolveFingerprint(childProtScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(childProtScope, 1L, childProtFp, 1000L, "PASS", CalculationProvenance("ProtectionEngine")))

        // 5. Resolve and record COORDINATION (all 4 upstream present -> COMPLETE)
        val coordScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.COORDINATION)
        val coordRes = CoordinationFreshnessAdapter().resolveFingerprint(coordScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved
        assertEquals(FingerprintCoverage.COMPLETE, coordRes.coverage)
        registry.recordCalculationCompleted(CalculationRecordMetadata(coordScope, 1L, coordRes.fingerprint, 1000L, "PASS", CalculationProvenance("CoordinationEngine")))

        // Verify initially CURRENT
        val initialSnap = freshnessProvider.getCalculationState(coordScope)
        assertEquals(CalculationState.CURRENT, initialSnap.state)

        // 6. Mutate PARENT circuit (e.g. loadKVA) -> re-record parent CABLE_SIZING
        dataProvider.addCircuit(parent.copy(loadKVA = 250.0))
        val newParentCableFp = (CableSizingFreshnessAdapter().resolveFingerprint(parentCableScope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(parentCableScope, 1L, newParentCableFp, 2000L, "PASS", CalculationProvenance("CableSizingEngine")))

        // 7. Child COORDINATION state must now be STALE with DEPENDENCY_CHANGED
        val afterSnap = freshnessProvider.getCalculationState(coordScope)
        assertEquals(CalculationState.STALE, afterSnap.state)
        assertTrue("Parent cable sizing change must trigger UPSTREAM_DEPENDENCY staleness", afterSnap.changedComponents.contains(ChangedComponent.UPSTREAM_DEPENDENCY))
    }

    @Test
    fun coordination_transformerIncomerChange_whenRootFed_returnsDependencyChanged() = runBlocking {
        val pid = "PRJ_COORD_TX_INCOMER"
        revisionProvider.setRevision(pid, 1L)
        val tx = TransformerEntity(projectId = pid, transformerId = "TX-01", kVA = 1000.0)
        val rootCircuit = CircuitEntity(id = 201, projectId = pid, circuitName = "MainIncomer", loadKVA = 500.0, fedFrom = "Transformer", transformerId = "TX-01")
        dataProvider.addTransformer(tx)
        dataProvider.addCircuit(rootCircuit)

        // 1. Record Transformer
        val txScope = CalculationScope(pid, CalculationEntityType.TRANSFORMER, "TX-01", CalculationDomain.TRANSFORMER)
        val txFp = (TransformerFreshnessAdapter().resolveFingerprint(txScope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(txScope, 1L, txFp, 1000L, "PASS", CalculationProvenance("TransformerEngine")))

        // 2. Record root circuit CABLE_SIZING
        val cableScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "201", CalculationDomain.CABLE_SIZING)
        val cableFp = (CableSizingFreshnessAdapter().resolveFingerprint(cableScope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(cableScope, 1L, cableFp, 1000L, "PASS", CalculationProvenance("CableSizingEngine")))

        // 3. Record root circuit SHORT_CIRCUIT
        val scScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "201", CalculationDomain.SHORT_CIRCUIT)
        val scFp = (ShortCircuitFreshnessAdapter().resolveFingerprint(scScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(scScope, 1L, scFp, 1000L, "PASS", CalculationProvenance("ShortCircuitEngine")))

        // 4. Record root circuit PROTECTION_ADS
        val protScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "201", CalculationDomain.PROTECTION_ADS)
        val protFp = (ProtectionAdsFreshnessAdapter().resolveFingerprint(protScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(protScope, 1L, protFp, 1000L, "PASS", CalculationProvenance("ProtectionEngine")))

        // 5. Resolve and record COORDINATION -> COMPLETE
        val coordScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "201", CalculationDomain.COORDINATION)
        val coordRes = CoordinationFreshnessAdapter().resolveFingerprint(coordScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved
        assertEquals(FingerprintCoverage.COMPLETE, coordRes.coverage)
        registry.recordCalculationCompleted(CalculationRecordMetadata(coordScope, 1L, coordRes.fingerprint, 1000L, "PASS", CalculationProvenance("CoordinationEngine")))

        assertEquals(CalculationState.CURRENT, freshnessProvider.getCalculationState(coordScope).state)

        // 6. Mutate transformer (e.g. kVA 1000 -> 1600) -> re-record Transformer
        dataProvider.addTransformer(tx.copy(kVA = 1600.0))
        val newTxFp = (TransformerFreshnessAdapter().resolveFingerprint(txScope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(txScope, 1L, newTxFp, 2000L, "PASS", CalculationProvenance("TransformerEngine")))

        // 7. COORDINATION must be STALE with UPSTREAM_DEPENDENCY
        val snap = freshnessProvider.getCalculationState(coordScope)
        assertEquals(CalculationState.STALE, snap.state)
        assertTrue(snap.changedComponents.contains(ChangedComponent.UPSTREAM_DEPENDENCY))
    }

    @Test
    fun coordination_shortCircuitFingerprintChange_returnsDependencyChanged() = runBlocking {
        val pid = "PRJ_COORD_SC_CHANGE"
        revisionProvider.setRevision(pid, 1L)
        val parent = CircuitEntity(id = 100, projectId = pid, circuitName = "MDB", loadKVA = 100.0)
        val child = CircuitEntity(id = 101, projectId = pid, circuitName = "Sub", loadKVA = 20.0, parentId = 100, fedFrom = "MDB")
        dataProvider.addCircuit(parent)
        dataProvider.addCircuit(child)

        val parentCableScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "100", CalculationDomain.CABLE_SIZING)
        val parentCableFp = (CableSizingFreshnessAdapter().resolveFingerprint(parentCableScope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(parentCableScope, 1L, parentCableFp, 1000L, "PASS", CalculationProvenance("CableSizingEngine")))

        val childCableScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val childCableFp = (CableSizingFreshnessAdapter().resolveFingerprint(childCableScope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(childCableScope, 1L, childCableFp, 1000L, "PASS", CalculationProvenance("CableSizingEngine")))

        val childScScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.SHORT_CIRCUIT)
        val childScFp = (ShortCircuitFreshnessAdapter().resolveFingerprint(childScScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(childScScope, 1L, childScFp, 1000L, "PASS", CalculationProvenance("ShortCircuitEngine")))

        val childProtScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.PROTECTION_ADS)
        val childProtFp = (ProtectionAdsFreshnessAdapter().resolveFingerprint(childProtScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(childProtScope, 1L, childProtFp, 1000L, "PASS", CalculationProvenance("ProtectionEngine")))

        val coordScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.COORDINATION)
        val coordRes = CoordinationFreshnessAdapter().resolveFingerprint(coordScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved
        registry.recordCalculationCompleted(CalculationRecordMetadata(coordScope, 1L, coordRes.fingerprint, 1000L, "PASS", CalculationProvenance("CoordinationEngine")))

        assertEquals(CalculationState.CURRENT, freshnessProvider.getCalculationState(coordScope).state)

        // Mutate child SHORT_CIRCUIT metadata
        val modifiedScFp = childScFp.copy(inputSignature = "0000000000000000000000000000000000000000000000000000000000000000")
        registry.recordCalculationCompleted(CalculationRecordMetadata(childScScope, 1L, modifiedScFp, 2000L, "PASS", CalculationProvenance("ShortCircuitEngine")))

        val snap = freshnessProvider.getCalculationState(coordScope)
        assertEquals(CalculationState.STALE, snap.state)
        assertTrue(snap.changedComponents.contains(ChangedComponent.UPSTREAM_DEPENDENCY))
    }

    @Test
    fun coordination_protectionAdsFingerprintChange_returnsDependencyChanged() = runBlocking {
        val pid = "PRJ_COORD_PROT_CHANGE"
        revisionProvider.setRevision(pid, 1L)
        val parent = CircuitEntity(id = 100, projectId = pid, circuitName = "MDB", loadKVA = 100.0)
        val child = CircuitEntity(id = 101, projectId = pid, circuitName = "Sub", loadKVA = 20.0, parentId = 100, fedFrom = "MDB")
        dataProvider.addCircuit(parent)
        dataProvider.addCircuit(child)

        // 1. Record parent CABLE_SIZING
        val parentCableScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "100", CalculationDomain.CABLE_SIZING)
        val parentCableFp = (CableSizingFreshnessAdapter().resolveFingerprint(parentCableScope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(parentCableScope, 1L, parentCableFp, 1000L, "PASS", CalculationProvenance("CableSizingEngine")))

        // 2. Record child CABLE_SIZING
        val childCableScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val childCableFp = (CableSizingFreshnessAdapter().resolveFingerprint(childCableScope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(childCableScope, 1L, childCableFp, 1000L, "PASS", CalculationProvenance("CableSizingEngine")))

        // 3. Record child SHORT_CIRCUIT
        val childScScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.SHORT_CIRCUIT)
        val childScFp = (ShortCircuitFreshnessAdapter().resolveFingerprint(childScScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(childScScope, 1L, childScFp, 1000L, "PASS", CalculationProvenance("ShortCircuitEngine")))

        // 4. Record child PROTECTION_ADS
        val childProtScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.PROTECTION_ADS)
        val childProtFp = (ProtectionAdsFreshnessAdapter().resolveFingerprint(childProtScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(childProtScope, 1L, childProtFp, 1000L, "PASS", CalculationProvenance("ProtectionEngine")))

        // 5. Resolve COORDINATION -> COMPLETE and record
        val coordScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.COORDINATION)
        val coordRes = CoordinationFreshnessAdapter().resolveFingerprint(coordScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved
        assertEquals(FingerprintCoverage.COMPLETE, coordRes.coverage)
        registry.recordCalculationCompleted(CalculationRecordMetadata(coordScope, 1L, coordRes.fingerprint, 1000L, "PASS", CalculationProvenance("CoordinationEngine")))

        assertEquals(CalculationState.CURRENT, freshnessProvider.getCalculationState(coordScope).state)

        // 6. Mutate child PROTECTION_ADS metadata (re-record with changed inputSignature) without changing cable/SC
        val modifiedProtFp = childProtFp.copy(inputSignature = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
        registry.recordCalculationCompleted(CalculationRecordMetadata(childProtScope, 1L, modifiedProtFp, 2000L, "PASS", CalculationProvenance("ProtectionEngine")))

        // 7. COORDINATION must become STALE with ChangedComponent.UPSTREAM_DEPENDENCY
        val snap = freshnessProvider.getCalculationState(coordScope)
        assertEquals(CalculationState.STALE, snap.state)
        assertTrue("Protection change must trigger UPSTREAM_DEPENDENCY", snap.changedComponents.contains(ChangedComponent.UPSTREAM_DEPENDENCY))
    }

    @Test
    fun coordination_missingProtectionAdsFingerprint_cannotReturnCurrent() = runBlocking {
        val pid = "PRJ_COORD_MISSING_PROT"
        revisionProvider.setRevision(pid, 1L)
        val parent = CircuitEntity(id = 100, projectId = pid, circuitName = "MDB", loadKVA = 100.0)
        val child = CircuitEntity(id = 101, projectId = pid, circuitName = "Sub", loadKVA = 20.0, parentId = 100, fedFrom = "MDB")
        dataProvider.addCircuit(parent)
        dataProvider.addCircuit(child)

        // Record parent cable, child cable, child SC - but NOT child protection_ads
        val parentCableScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "100", CalculationDomain.CABLE_SIZING)
        val parentCableFp = (CableSizingFreshnessAdapter().resolveFingerprint(parentCableScope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(parentCableScope, 1L, parentCableFp, 1000L, "PASS", CalculationProvenance("CableSizingEngine")))

        val childCableScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val childCableFp = (CableSizingFreshnessAdapter().resolveFingerprint(childCableScope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(childCableScope, 1L, childCableFp, 1000L, "PASS", CalculationProvenance("CableSizingEngine")))

        val childScScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.SHORT_CIRCUIT)
        val childScFp = (ShortCircuitFreshnessAdapter().resolveFingerprint(childScScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(childScScope, 1L, childScFp, 1000L, "PASS", CalculationProvenance("ShortCircuitEngine")))

        val coordScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.COORDINATION)
        val coordRes = CoordinationFreshnessAdapter().resolveFingerprint(coordScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved
        assertEquals("Missing PROTECTION_ADS must yield PARTIAL coverage", FingerprintCoverage.PARTIAL, coordRes.coverage)

        registry.recordCalculationCompleted(CalculationRecordMetadata(coordScope, 1L, coordRes.fingerprint, 1000L, "PASS", CalculationProvenance("CoordinationEngine")))

        val snap = freshnessProvider.getCalculationState(coordScope)
        assertNotEquals("PARTIAL coverage cannot return CURRENT", CalculationState.CURRENT, snap.state)
        assertEquals(CalculationState.STALE, snap.state)
        assertEquals(FreshnessReasonCode.PROVENANCE_INCOMPLETE, snap.reasonCode)
    }

    @Test
    fun coordination_allFourUpstreamPresentAndUnchanged_canReturnCurrent() = runBlocking {
        val pid = "PRJ_COORD_FOUR_DEPS"
        revisionProvider.setRevision(pid, 1L)
        val parent = CircuitEntity(id = 100, projectId = pid, circuitName = "MDB", loadKVA = 100.0)
        val child = CircuitEntity(id = 101, projectId = pid, circuitName = "Sub", loadKVA = 20.0, parentId = 100, fedFrom = "MDB")
        dataProvider.addCircuit(parent)
        dataProvider.addCircuit(child)

        // 1. Parent CABLE_SIZING
        val parentCableScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "100", CalculationDomain.CABLE_SIZING)
        val parentCableFp = (CableSizingFreshnessAdapter().resolveFingerprint(parentCableScope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(parentCableScope, 1L, parentCableFp, 1000L, "PASS", CalculationProvenance("CableSizingEngine")))

        // 2. Child CABLE_SIZING
        val childCableScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val childCableFp = (CableSizingFreshnessAdapter().resolveFingerprint(childCableScope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(childCableScope, 1L, childCableFp, 1000L, "PASS", CalculationProvenance("CableSizingEngine")))

        // 3. Child SHORT_CIRCUIT
        val childScScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.SHORT_CIRCUIT)
        val childScFp = (ShortCircuitFreshnessAdapter().resolveFingerprint(childScScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(childScScope, 1L, childScFp, 1000L, "PASS", CalculationProvenance("ShortCircuitEngine")))

        // 4. Child PROTECTION_ADS
        val childProtScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.PROTECTION_ADS)
        val childProtFp = (ProtectionAdsFreshnessAdapter().resolveFingerprint(childProtScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(childProtScope, 1L, childProtFp, 1000L, "PASS", CalculationProvenance("ProtectionEngine")))

        // 5. COORDINATION -> COMPLETE & CURRENT
        val coordScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.COORDINATION)
        val coordRes = CoordinationFreshnessAdapter().resolveFingerprint(coordScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved
        assertEquals(FingerprintCoverage.COMPLETE, coordRes.coverage)

        registry.recordCalculationCompleted(CalculationRecordMetadata(coordScope, 1L, coordRes.fingerprint, 1000L, "PASS", CalculationProvenance("CoordinationEngine")))

        val snap = freshnessProvider.getCalculationState(coordScope)
        assertEquals(CalculationState.CURRENT, snap.state)
        assertEquals(FreshnessReasonCode.MATCH, snap.reasonCode)
    }

    @Test
    fun coordination_allUpstreamPresentAndUnchanged_canReturnCurrent() = runBlocking {
        val pid = "PRJ_COORD_CAN_BE_CURRENT"
        revisionProvider.setRevision(pid, 1L)
        val parent = CircuitEntity(id = 100, projectId = pid, circuitName = "MDB", loadKVA = 100.0)
        val child = CircuitEntity(id = 101, projectId = pid, circuitName = "Sub", loadKVA = 20.0, parentId = 100, fedFrom = "MDB")
        dataProvider.addCircuit(parent)
        dataProvider.addCircuit(child)

        // 1. Record parent CABLE_SIZING
        val parentCableScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "100", CalculationDomain.CABLE_SIZING)
        val parentCableFp = (CableSizingFreshnessAdapter().resolveFingerprint(parentCableScope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(parentCableScope, 1L, parentCableFp, 1000L, "PASS", CalculationProvenance("CableSizingEngine")))

        // 2. Record child CABLE_SIZING
        val childCableScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val childCableFp = (CableSizingFreshnessAdapter().resolveFingerprint(childCableScope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(childCableScope, 1L, childCableFp, 1000L, "PASS", CalculationProvenance("CableSizingEngine")))

        // 3. Record child SHORT_CIRCUIT
        val childScScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.SHORT_CIRCUIT)
        val childScFp = (ShortCircuitFreshnessAdapter().resolveFingerprint(childScScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(childScScope, 1L, childScFp, 1000L, "PASS", CalculationProvenance("ShortCircuitEngine")))

        // 4. Record child PROTECTION_ADS
        val childProtScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.PROTECTION_ADS)
        val childProtFp = (ProtectionAdsFreshnessAdapter().resolveFingerprint(childProtScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(childProtScope, 1L, childProtFp, 1000L, "PASS", CalculationProvenance("ProtectionEngine")))

        // 5. Resolve COORDINATION -> COMPLETE
        val coordScope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.COORDINATION)
        val coordRes = CoordinationFreshnessAdapter().resolveFingerprint(coordScope, dataProvider, upstreamProvider) as FingerprintResolution.Resolved
        assertEquals(FingerprintCoverage.COMPLETE, coordRes.coverage)

        registry.recordCalculationCompleted(CalculationRecordMetadata(coordScope, 1L, coordRes.fingerprint, 1000L, "PASS", CalculationProvenance("CoordinationEngine")))

        val snap = freshnessProvider.getCalculationState(coordScope)
        assertEquals(CalculationState.CURRENT, snap.state)
        assertEquals(FreshnessReasonCode.MATCH, snap.reasonCode)
    }

    // =========================================================================
    // 4. Catalog / Methodology Identity
    // =========================================================================

    @Test
    fun methodologyVersionIsIncludedInCableFingerprint() = runBlocking {
        val pid = "PRJ_METH_1"
        dataProvider.addCircuit(CircuitEntity(id = 101, projectId = pid, circuitName = "F1", loadKVA = 25.0, length_m = 15.0))
        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val res = CableSizingFreshnessAdapter().resolveFingerprint(scope, dataProvider) as FingerprintResolution.Resolved

        assertEquals(CableSizingFreshnessAdapter.METHODOLOGY_VERSION, res.fingerprint.methodologySignature)
        assertEquals(CableSizingFreshnessAdapter.ENGINE_VERSION, res.fingerprint.engineSignature)
        assertEquals("VoltAtlas-CableSizing-M1", res.fingerprint.methodologySignature)
    }

    @Test
    fun sameInputsDifferentMethodologyVersion_isNotCurrent() = runBlocking {
        val pid = "PRJ_METH_2"
        revisionProvider.setRevision(pid, 1L)
        dataProvider.addCircuit(CircuitEntity(id = 101, projectId = pid, circuitName = "F1", loadKVA = 25.0, length_m = 15.0))

        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val currentRes = CableSizingFreshnessAdapter().resolveFingerprint(scope, dataProvider) as FingerprintResolution.Resolved

        // Record with OLD methodology version
        val oldFp = currentRes.fingerprint.copy(methodologySignature = "VoltAtlas-CableSizing-M0")
        registry.recordCalculationCompleted(CalculationRecordMetadata(scope, 1L, oldFp, 1000L, "PASS", CalculationProvenance("CableSizingEngine")))

        val snap = freshnessProvider.getCalculationState(scope)
        assertEquals(CalculationState.STALE, snap.state)
        assertTrue(snap.changedComponents.contains(ChangedComponent.METHODOLOGY))
        assertEquals(FreshnessReasonCode.METHODOLOGY_CHANGED, snap.reasonCode)
    }

    // =========================================================================
    // 5. Typed Error Lifecycle
    // =========================================================================

    @Test
    fun newExecutionClearsOrSupersedesPriorError() {
        val scope = CalculationScope("PRJ_ERR_1", CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        registry.recordCalculationFailed(scope, CalculationExecutionErrorMetadata(
            scope = scope,
            errorCode = ExecutionErrorCode.ENGINE_RUNTIME_ERROR,
            occurredAt = 1000L,
            recoverability = ErrorRecoverability.NON_RECOVERABLE,
            safeMessage = "divergence"
        ))
        assertNotNull(registry.getError(scope))

        registry.startExecution(scope, false)
        assertNull("startExecution must clear prior error", registry.getError(scope))
    }

    @Test
    fun successfulCompletionClearsExecutionError() {
        val scope = CalculationScope("PRJ_ERR_2", CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        registry.recordCalculationFailed(scope, CalculationExecutionErrorMetadata(
            scope = scope,
            errorCode = ExecutionErrorCode.ENGINE_RUNTIME_ERROR,
            occurredAt = 1000L,
            recoverability = ErrorRecoverability.NON_RECOVERABLE,
            safeMessage = "error"
        ))

        registry.recordCalculationCompleted(CalculationRecordMetadata(
            scope, 1L, CalculationFingerprint(1, "sig", "dep"), 2000L, "PASS", CalculationProvenance("Engine")
        ))
        assertNull("Successful completion must clear error", registry.getError(scope))
        assertNotNull(registry.getRecord(scope))
    }

    @Test
    fun errorWithPriorRecord_retainsPriorMetadataButReturnsError() = runBlocking {
        val pid = "PRJ_ERR_3"
        revisionProvider.setRevision(pid, 1L)
        dataProvider.addCircuit(CircuitEntity(id = 101, projectId = pid, circuitName = "F1", loadKVA = 25.0, length_m = 15.0))
        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)

        val fp = (CableSizingFreshnessAdapter().resolveFingerprint(scope, dataProvider) as FingerprintResolution.Resolved).fingerprint
        registry.recordCalculationCompleted(CalculationRecordMetadata(scope, 1L, fp, 1000L, "PASS", CalculationProvenance("CableSizingEngine")))

        registry.recordCalculationFailed(scope, CalculationExecutionErrorMetadata(
            scope = scope, errorCode = ExecutionErrorCode.CATALOG_UNAVAILABLE,
            occurredAt = 2000L, recoverability = ErrorRecoverability.RECOVERABLE, safeMessage = "catalog load failed"
        ))

        // Prior record retained
        assertNotNull(registry.getRecord(scope))
        assertNotNull(registry.getError(scope))

        val snap = freshnessProvider.getCalculationState(scope)
        assertEquals(CalculationState.ERROR, snap.state)
        assertNotNull(snap.recordedProjectRevision)
    }

    @Test
    fun clearProject_removesErrorsMetadataAndMarkers() {
        val pid = "PRJ_CLEAR_1"
        val scope1 = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val scope2 = CalculationScope(pid, CalculationEntityType.CIRCUIT, "102", CalculationDomain.CABLE_SIZING)

        registry.recordCalculationCompleted(CalculationRecordMetadata(scope1, 1L, CalculationFingerprint(1, "s1", "d1"), 1000L, "PASS", CalculationProvenance("E")))
        registry.startExecution(scope2, false)
        registry.recordCalculationFailed(scope2, CalculationExecutionErrorMetadata(scope2, ExecutionErrorCode.UNKNOWN, 1000L, ErrorRecoverability.NON_RECOVERABLE))

        registry.clearProject(pid)
        assertNull(registry.getRecord(scope1))
        assertNull(registry.getExecutionState(scope2))
        assertNull(registry.getError(scope2))
    }

    @Test
    fun executionErrorMetadata_isTypedNotString() {
        val scope = CalculationScope("P", CalculationEntityType.CIRCUIT, "1", CalculationDomain.CABLE_SIZING)
        val error = CalculationExecutionErrorMetadata(
            scope = scope,
            errorCode = ExecutionErrorCode.ENGINE_RUNTIME_ERROR,
            occurredAt = System.currentTimeMillis(),
            recoverability = ErrorRecoverability.NON_RECOVERABLE,
            safeMessage = "test error"
        )
        assertEquals(ExecutionErrorCode.ENGINE_RUNTIME_ERROR, error.errorCode)
        assertEquals(ErrorRecoverability.NON_RECOVERABLE, error.recoverability)
        assertEquals("test error", error.safeMessage)
    }

    // =========================================================================
    // 6. Lifecycle Recorder Integration
    // =========================================================================

    @Test
    fun freshnessLifecycleRecorder_recordsCableSizingMetadata() = runBlocking {
        val pid = "PRJ_REC_1"
        revisionProvider.setRevision(pid, 1L)
        dataProvider.addCircuit(CircuitEntity(id = 101, projectId = pid, circuitName = "F1", loadKVA = 25.0, length_m = 15.0))

        val recorder = FreshnessLifecycleRecorder(registry, dataProvider)
        recorder.recordCableSizing(pid, 101, "PASS", 1L)

        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val record = registry.getRecord(scope)
        assertNotNull(record)
        assertEquals("PASS", record!!.overallStatus)
        assertEquals(CalculationDomain.CABLE_SIZING.name, record.provenance.engineName)
    }

    @Test
    fun freshnessLifecycleRecorder_recordsTransformerMetadata() = runBlocking {
        val pid = "PRJ_REC_2"
        dataProvider.addTransformer(TransformerEntity(projectId = pid, transformerId = "TX-01", kVA = 1000.0))

        val recorder = FreshnessLifecycleRecorder(registry, dataProvider)
        recorder.recordTransformer(pid, "TX-01", "PASS", 1L)

        val scope = CalculationScope(pid, CalculationEntityType.TRANSFORMER, "TX-01", CalculationDomain.TRANSFORMER)
        assertNotNull(registry.getRecord(scope))
    }

    @Test
    fun freshnessLifecycleRecorder_recordsShortCircuitMetadata() = runBlocking {
        val pid = "PRJ_REC_3"
        dataProvider.addCircuit(CircuitEntity(id = 101, projectId = pid, circuitName = "F1", loadKVA = 25.0, length_m = 15.0, fedFrom = "Transformer", transformerId = "TX-01"))
        dataProvider.addTransformer(TransformerEntity(projectId = pid, transformerId = "TX-01", kVA = 1000.0))

        val recorder = FreshnessLifecycleRecorder(registry, dataProvider)
        recorder.recordShortCircuit(pid, 101, "PASS", 1L)

        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.SHORT_CIRCUIT)
        assertNotNull(registry.getRecord(scope))
    }

    @Test
    fun freshnessLifecycleRecorder_recordsProtectionAdsMetadata() = runBlocking {
        val pid = "PRJ_REC_4"
        dataProvider.addCircuit(CircuitEntity(id = 101, projectId = pid, circuitName = "F1", loadKVA = 25.0, length_m = 15.0))

        val recorder = FreshnessLifecycleRecorder(registry, dataProvider)
        recorder.recordProtectionAds(pid, 101, "PASS", 1L)

        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.PROTECTION_ADS)
        assertNotNull(registry.getRecord(scope))
    }

    @Test
    fun freshnessLifecycleRecorder_recordsCoordinationMetadata() = runBlocking {
        val pid = "PRJ_REC_5"
        dataProvider.addCircuit(CircuitEntity(id = 101, projectId = pid, circuitName = "F1", loadKVA = 25.0, length_m = 15.0))

        val recorder = FreshnessLifecycleRecorder(registry, dataProvider)
        recorder.recordCoordination(pid, 101, "PASS", 1L)

        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.COORDINATION)
        assertNotNull(registry.getRecord(scope))
    }

    @Test
    fun freshnessLifecycleRecorder_engineeringFailCanBeFreshnessCurrent() = runBlocking {
        val pid = "PRJ_REC_FAIL"
        revisionProvider.setRevision(pid, 1L)
        dataProvider.addCircuit(CircuitEntity(id = 101, projectId = pid, circuitName = "F1", loadKVA = 25.0, length_m = 15.0))

        val recorder = FreshnessLifecycleRecorder(registry, dataProvider)
        recorder.recordCableSizing(pid, 101, "FAIL", 1L)

        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val snap = freshnessProvider.getCalculationState(scope)
        assertEquals(CalculationState.CURRENT, snap.state)
        assertEquals("FAIL", snap.recordedStatus)
    }

    @Test
    fun freshnessLifecycleRecorder_recordsError() {
        val scope = CalculationScope("PRJ_REC_ERR", CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val recorder = FreshnessLifecycleRecorder(registry, dataProvider)
        recorder.recordError(scope, ExecutionErrorCode.ENGINE_RUNTIME_ERROR, "numerical divergence", 1L)

        val error = registry.getError(scope)
        assertNotNull(error)
        assertEquals(ExecutionErrorCode.ENGINE_RUNTIME_ERROR, error!!.errorCode)
        assertEquals("numerical divergence", error.safeMessage)
    }

    // =========================================================================
    // 7. Structural Field Sensitivity (BLOCKER 3 Fixes)
    // =========================================================================

    @Test
    fun allAuditedCableInputsAffectFingerprint() = runBlocking {
        val pid = "PRJ_SENS_CABLE"
        val baseCircuit = CircuitEntity(
            id = 201, projectId = pid, circuitName = "Pump-1", loadKVA = 45.0,
            loadType = "Motor", voltageV = 400, phases = "3P+N", pf = 0.85,
            safetyFactor = 1.25, manufacturer = "Elsewedy", cableType = "Cu/XLPE/SWA/PVC",
            installation = "Laid in Ducts", parallelRuns = 1, airTempC = 45, gndTempC = 40,
            burialDepth_m = 0.8, soilRes = 120, noCircuits = 2, formation = "Flat",
            spacing = "Touching", length_m = 60.0, maxVDpct = 0.03, demandFactor = 1.0,
            feederType = "Cable", buswayMfg = "Schneider", buswayModel = "Canalis",
            overrideDeviceType = "MCCB", overrideDeviceSize = 100.0,
            overrideCableSize = "3x70+35", overrideNeutralSize = "35", overrideEarthSize = "16",
            isHarmonic = false, startingMethod = "DOL", startingCurrentMultiplier = 6.0
        )
        dataProvider.addCircuit(baseCircuit)
        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "201", CalculationDomain.CABLE_SIZING)
        val adapter = CableSizingFreshnessAdapter()
        val baseSig = (adapter.resolveFingerprint(scope, dataProvider) as FingerprintResolution.Resolved).fingerprint.inputSignature

        // All 32 fields encoded in CableSizingFreshnessAdapter inputEncoder
        val expectedManifestFields = setOf(
            "circuitName", "loadKVA", "loadType", "phases", "voltageV", "pf",
            "safetyFactor", "manufacturer", "cableType", "installation", "parallelRuns",
            "airTempC", "gndTempC", "burialDepth_m", "soilRes", "noCircuits",
            "formation", "spacing", "length_m", "maxVDpct", "demandFactor",
            "feederType", "buswayMfg", "buswayModel", "overrideDeviceType",
            "overrideDeviceSize", "overrideCableSize", "overrideNeutralSize",
            "overrideEarthSize", "isHarmonic", "startingMethod", "startingCurrentMultiplier"
        )

        data class FieldTest(val name: String, val circuit: CircuitEntity)
        val mutations = listOf(
            FieldTest("circuitName", baseCircuit.copy(circuitName = "Pump-2")),
            FieldTest("loadKVA", baseCircuit.copy(loadKVA = 50.0)),
            FieldTest("loadType", baseCircuit.copy(loadType = "Lighting")),
            FieldTest("phases", baseCircuit.copy(phases = "1P+N")),
            FieldTest("voltageV", baseCircuit.copy(voltageV = 230)),
            FieldTest("pf", baseCircuit.copy(pf = 0.95)),
            FieldTest("safetyFactor", baseCircuit.copy(safetyFactor = 1.5)),
            FieldTest("manufacturer", baseCircuit.copy(manufacturer = "Nexans")),
            FieldTest("cableType", baseCircuit.copy(cableType = "Al/XLPE/SWA/PVC")),
            FieldTest("installation", baseCircuit.copy(installation = "Cable Tray")),
            FieldTest("parallelRuns", baseCircuit.copy(parallelRuns = 2)),
            FieldTest("airTempC", baseCircuit.copy(airTempC = 50)),
            FieldTest("gndTempC", baseCircuit.copy(gndTempC = 35)),
            FieldTest("burialDepth_m", baseCircuit.copy(burialDepth_m = 1.2)),
            FieldTest("soilRes", baseCircuit.copy(soilRes = 250)),
            FieldTest("noCircuits", baseCircuit.copy(noCircuits = 4)),
            FieldTest("formation", baseCircuit.copy(formation = "Trefoil")),
            FieldTest("spacing", baseCircuit.copy(spacing = "Spaced")),
            FieldTest("length_m", baseCircuit.copy(length_m = 100.0)),
            FieldTest("maxVDpct", baseCircuit.copy(maxVDpct = 0.05)),
            FieldTest("demandFactor", baseCircuit.copy(demandFactor = 0.8)),
            FieldTest("feederType", baseCircuit.copy(feederType = "Busway")),
            FieldTest("buswayMfg", baseCircuit.copy(buswayMfg = "Siemens")),
            FieldTest("buswayModel", baseCircuit.copy(buswayModel = "BD2")),
            FieldTest("overrideDeviceType", baseCircuit.copy(overrideDeviceType = "MCB")),
            FieldTest("overrideDeviceSize", baseCircuit.copy(overrideDeviceSize = 200.0)),
            FieldTest("overrideCableSize", baseCircuit.copy(overrideCableSize = "3x95+50")),
            FieldTest("overrideNeutralSize", baseCircuit.copy(overrideNeutralSize = "50")),
            FieldTest("overrideEarthSize", baseCircuit.copy(overrideEarthSize = "25")),
            FieldTest("isHarmonic", baseCircuit.copy(isHarmonic = true)),
            FieldTest("startingMethod", baseCircuit.copy(startingMethod = "Star-Delta")),
            FieldTest("startingCurrentMultiplier", baseCircuit.copy(startingCurrentMultiplier = 3.0))
        )

        // Structural completeness check: Ensure test covers all 32 manifest fields
        val testedFields = mutations.map { it.name }.toSet()
        val missingFromTest = expectedManifestFields - testedFields
        assertTrue("Test must cover all manifest fields. Missing: $missingFromTest", missingFromTest.isEmpty())
        assertEquals("Mutation test must have exactly 32 cases matching manifest", 32, mutations.size)

        val failures = mutableListOf<String>()
        for (ft in mutations) {
            dataProvider.addCircuit(ft.circuit)
            val sig = (adapter.resolveFingerprint(scope, dataProvider) as FingerprintResolution.Resolved).fingerprint.inputSignature
            if (sig == baseSig) failures.add(ft.name)
            dataProvider.addCircuit(baseCircuit) // restore
        }
        assertTrue("Fields not affecting cable inputSignature: $failures", failures.isEmpty())
    }

    @Test
    fun allAuditedTransformerInputsAffectFingerprint() = runBlocking {
        val pid = "PRJ_SENS_TX"
        val baseTx = TransformerEntity(
            projectId = pid, transformerId = "TX-01", transformerName = "Main TX",
            kVA = 1000.0, voltage = 400, percentZ = 5.0, xr = 5.0,
            txType = "Oil", sourceType = "Utility", status = "Active",
            sizeMode = "Auto", manualKVA = 0.0, demandFactor = 1.0,
            diversity = 0.9, growth = 1.1, safety = 1.25,
            vectorGroup = "Dyn11", z0OverZ1 = 1.0, pf = 0.85
        )
        dataProvider.addTransformer(baseTx)
        val scope = CalculationScope(pid, CalculationEntityType.TRANSFORMER, "TX-01", CalculationDomain.TRANSFORMER)
        val adapter = TransformerFreshnessAdapter()
        val baseSig = (adapter.resolveFingerprint(scope, dataProvider) as FingerprintResolution.Resolved).fingerprint.inputSignature

        // All 17 non-key fields of TransformerEntity (transformerId is the scope key)
        val expectedManifestFields = setOf(
            "transformerName", "kVA", "voltage", "percentZ", "xr", "txType",
            "sourceType", "status", "sizeMode", "manualKVA", "demandFactor",
            "diversity", "growth", "safety", "vectorGroup", "z0OverZ1", "pf"
        )

        data class FieldTest(val name: String, val tx: TransformerEntity)
        val mutations = listOf(
            FieldTest("transformerName", baseTx.copy(transformerName = "Backup TX")),
            FieldTest("kVA", baseTx.copy(kVA = 1500.0)),
            FieldTest("voltage", baseTx.copy(voltage = 230)),
            FieldTest("percentZ", baseTx.copy(percentZ = 6.0)),
            FieldTest("xr", baseTx.copy(xr = 7.0)),
            FieldTest("txType", baseTx.copy(txType = "Dry")),
            FieldTest("sourceType", baseTx.copy(sourceType = "Generator")),
            FieldTest("status", baseTx.copy(status = "Inactive")),
            FieldTest("sizeMode", baseTx.copy(sizeMode = "Manual")),
            FieldTest("manualKVA", baseTx.copy(manualKVA = 500.0)),
            FieldTest("demandFactor", baseTx.copy(demandFactor = 0.8)),
            FieldTest("diversity", baseTx.copy(diversity = 0.7)),
            FieldTest("growth", baseTx.copy(growth = 1.2)),
            FieldTest("safety", baseTx.copy(safety = 1.5)),
            FieldTest("vectorGroup", baseTx.copy(vectorGroup = "Yyn0")),
            FieldTest("z0OverZ1", baseTx.copy(z0OverZ1 = 2.0)),
            FieldTest("pf", baseTx.copy(pf = 0.9))
        )

        val testedFields = mutations.map { it.name }.toSet()
        val missingFromTest = expectedManifestFields - testedFields
        assertTrue("Test must cover all non-key TX fields. Missing: $missingFromTest", missingFromTest.isEmpty())
        assertEquals("Mutation test must have exactly 17 cases matching manifest", 17, mutations.size)

        val failures = mutableListOf<String>()
        for (ft in mutations) {
            dataProvider.addTransformer(ft.tx)
            val sig = (adapter.resolveFingerprint(scope, dataProvider) as FingerprintResolution.Resolved).fingerprint.inputSignature
            if (sig == baseSig) failures.add(ft.name)
            dataProvider.addTransformer(baseTx) // restore
        }
        assertTrue("TX fields not affecting inputSignature: $failures", failures.isEmpty())
    }

    @Test
    fun presentationOnlyFields_doNotAffectCableInputFingerprint() = runBlocking {
        val pid = "PRJ_PRES_1"
        val c1 = CircuitEntity(id = 301, projectId = pid, circuitName = "F1", loadKVA = 25.0, length_m = 15.0)
        val c2 = CircuitEntity(id = 302, projectId = pid, circuitName = "F1", loadKVA = 25.0, length_m = 15.0)
        dataProvider.addCircuit(c1)

        val adapter = CableSizingFreshnessAdapter()
        val scope1 = CalculationScope(pid, CalculationEntityType.CIRCUIT, "301", CalculationDomain.CABLE_SIZING)
        val sig1 = (adapter.resolveFingerprint(scope1, dataProvider) as FingerprintResolution.Resolved).fingerprint.inputSignature

        dataProvider.addCircuit(c2)
        val scope2 = CalculationScope(pid, CalculationEntityType.CIRCUIT, "302", CalculationDomain.CABLE_SIZING)
        val sig2 = (adapter.resolveFingerprint(scope2, dataProvider) as FingerprintResolution.Resolved).fingerprint.inputSignature

        assertEquals("Different circuit IDs with same engineering inputs should produce same inputSignature", sig1, sig2)
    }

    // =========================================================================
    // 8. RUNNING/REVALIDATING Production Observability
    // =========================================================================

    @Test
    fun runningRevalidating_areNotProductionObservableInSynchronousExecution() {
        val scope = CalculationScope("P", CalculationEntityType.CIRCUIT, "1", CalculationDomain.CABLE_SIZING)
        registry.startExecution(scope, false)
        assertNotNull(registry.getExecutionState(scope))
        registry.recordCalculationCompleted(CalculationRecordMetadata(scope, 1L, CalculationFingerprint(1, "s", "d"), 1000L, "PASS", CalculationProvenance("E")))
        assertNull("Marker must be cleared after completion", registry.getExecutionState(scope))
    }

    // =========================================================================
    // Helper Test Doubles
    // =========================================================================

    private class FakeRevisionProvider : ProjectRevisionProvider {
        private val revisions = mutableMapOf<String, Long>()
        fun setRevision(projectId: String, revision: Long) { revisions[projectId] = revision }
        override suspend fun getCurrentRevision(projectId: String): ProjectRevisionSnapshot {
            val rev = revisions[projectId] ?: throw NoSuchElementException("Project $projectId not found")
            return ProjectRevisionSnapshot(projectId, rev, System.currentTimeMillis())
        }
    }

    private class FakeDataProvider : InMemoryProjectFreshnessDataProvider() {
        private val mutableCircuits = mutableListOf<CircuitEntity>()
        private val mutableTransformers = mutableListOf<TransformerEntity>()
        override val circuits: List<CircuitEntity> get() = mutableCircuits
        override val transformers: List<TransformerEntity> get() = mutableTransformers

        fun addCircuit(c: CircuitEntity) {
            mutableCircuits.removeAll { it.id == c.id && it.projectId == c.projectId }
            mutableCircuits.add(c)
        }
        fun addTransformer(t: TransformerEntity) {
            mutableTransformers.removeAll { it.transformerId.equals(t.transformerId, ignoreCase = true) && it.projectId == t.projectId }
            mutableTransformers.add(t)
        }
        override suspend fun getCircuits(projectId: String) = mutableCircuits.filter { it.projectId == projectId }
        override suspend fun getTransformers(projectId: String) = mutableTransformers.filter { it.projectId == projectId }
    }
}
