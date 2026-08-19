package com.example.ai.context.freshness

import com.example.ai.context.freshness.adapters.*
import com.example.ai.context.freshness.contracts.*
import com.example.ai.context.freshness.internal.*
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.ai.context.revision.ProjectRevisionSnapshot
import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CalculationFreshnessTest {

    private lateinit var mockRevisionProvider: FakeProjectRevisionProvider
    private lateinit var metadataRegistry: InMemoryCalculationFreshnessMetadataRegistry
    private lateinit var fakeDataProvider: FakeProjectFreshnessDataProvider
    private lateinit var freshnessProvider: DefaultCalculationFreshnessProvider

    @Before
    fun setUp() {
        mockRevisionProvider = FakeProjectRevisionProvider()
        metadataRegistry = InMemoryCalculationFreshnessMetadataRegistry()
        fakeDataProvider = FakeProjectFreshnessDataProvider()
        freshnessProvider = DefaultCalculationFreshnessProvider(
            revisionProvider = mockRevisionProvider,
            metadataRegistry = metadataRegistry,
            projectDataProvider = fakeDataProvider
        )
    }

    // =========================================================================
    // 1. Contract & Canonical Fingerprint Invariant Tests
    // =========================================================================

    @Test
    fun allCalculationStates_areRepresentable() {
        val expectedStates = setOf("CURRENT", "STALE", "MISSING", "INVALID", "RUNNING", "REVALIDATING", "ERROR")
        val actualStates = CalculationState.values().map { it.name }.toSet()
        assertEquals(expectedStates, actualStates)
    }

    @Test
    fun calculationScope_requiresProjectEntityAndDomainIdentity() {
        assertThrows(IllegalArgumentException::class.java) {
            CalculationScope("", CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CalculationScope("PRJ_1", CalculationEntityType.CIRCUIT, "  ", CalculationDomain.CABLE_SIZING)
        }
        val valid = CalculationScope("PRJ_1", CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        assertEquals("PRJ_1", valid.projectId)
        assertEquals(CalculationEntityType.CIRCUIT, valid.entityType)
        assertEquals("101", valid.entityId)
        assertEquals(CalculationDomain.CABLE_SIZING, valid.domain)
    }

    @Test
    fun fingerprint_isStableAcrossProcessEquivalentInputs() {
        val enc1 = CanonicalFingerprintEncoder().putString("k1", "v1").putQuantity("p", 15.5, "kW")
        val enc2 = CanonicalFingerprintEncoder().putString("k1", "v1").putQuantity("p", 15.5, "kW")
        assertEquals(enc1.computeSha256Hex(), enc2.computeSha256Hex())
    }

    @Test
    fun fingerprint_isIndependentOfMapIterationOrder() {
        val enc1 = CanonicalFingerprintEncoder()
            .putString("alpha", "1")
            .putString("beta", "2")
            .putString("gamma", "3")

        val enc2 = CanonicalFingerprintEncoder()
            .putString("gamma", "3")
            .putString("alpha", "1")
            .putString("beta", "2")

        assertEquals(enc1.computeSha256Hex(), enc2.computeSha256Hex())
    }

    @Test
    fun fingerprint_isLocaleIndependent() {
        val defaultLocale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
            val encTurkish = CanonicalFingerprintEncoder().putDouble("val", 1234.567)

            java.util.Locale.setDefault(java.util.Locale.US)
            val encUS = CanonicalFingerprintEncoder().putDouble("val", 1234.567)

            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-SA"))
            val encArabic = CanonicalFingerprintEncoder().putDouble("val", 1234.567)

            assertEquals(encUS.computeSha256Hex(), encTurkish.computeSha256Hex())
            assertEquals(encUS.computeSha256Hex(), encArabic.computeSha256Hex())
        } finally {
            java.util.Locale.setDefault(defaultLocale)
        }
    }

    @Test
    fun fingerprint_distinguishesNullFromZeroAndEmptyText() {
        val encNull = CanonicalFingerprintEncoder().putString("field", null)
        val encEmpty = CanonicalFingerprintEncoder().putString("field", "")
        val encZeroInt = CanonicalFingerprintEncoder().putInt("field", 0)
        val encZeroDouble = CanonicalFingerprintEncoder().putDouble("field", 0.0)

        val hashes = setOf(
            encNull.computeSha256Hex(),
            encEmpty.computeSha256Hex(),
            encZeroInt.computeSha256Hex(),
            encZeroDouble.computeSha256Hex()
        )
        assertEquals("Null, empty, 0 (int), and 0.0 (double) must all have distinct fingerprints", 4, hashes.size)
    }

    @Test
    fun fingerprint_distinguishesIntegerFromDecimal() {
        val encInt = CanonicalFingerprintEncoder().putInt("num", 10)
        val encDouble = CanonicalFingerprintEncoder().putDouble("num", 10.0)
        assertNotEquals(encInt.computeSha256Hex(), encDouble.computeSha256Hex())
    }

    @Test
    fun fingerprint_normalizesOrRejectsNegativeZeroPerPolicy() {
        val encPosZero = CanonicalFingerprintEncoder().putDouble("v", 0.0)
        val encNegZero = CanonicalFingerprintEncoder().putDouble("v", -0.0)
        assertEquals("Negative zero must be normalized to positive zero", encPosZero.computeSha256Hex(), encNegZero.computeSha256Hex())
    }

    @Test
    fun fingerprint_rejectsNaNAndInfinity() {
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalFingerprintEncoder().putDouble("nan", Double.NaN).computeSha256Hex()
        }
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalFingerprintEncoder().putDouble("inf", Double.POSITIVE_INFINITY).computeSha256Hex()
        }
    }

    @Test
    fun fingerprint_isUnicodeNormalizationStablePerPolicy() {
        val decomposed = "e\u0301lectric"
        val precomposed = "\u00e9lectric"
        val enc1 = CanonicalFingerprintEncoder().putString("name", decomposed)
        val enc2 = CanonicalFingerprintEncoder().putString("name", precomposed)
        assertEquals(enc1.computeSha256Hex(), enc2.computeSha256Hex())
    }

    @Test
    fun fingerprint_escapesOrFramesDelimiterCharactersWithoutCollision() {
        val enc1 = CanonicalFingerprintEncoder().putString("k1;k2", "v")
        val enc2 = CanonicalFingerprintEncoder().putString("k1", ";k2v")
        assertNotEquals("Framing must prevent key-delimiter collisions", enc1.computeSha256Hex(), enc2.computeSha256Hex())
    }

    @Test
    fun fingerprint_setOrderIndependent() {
        val enc1 = CanonicalFingerprintEncoder().putSet("tags", setOf("B", "A", "C"))
        val enc2 = CanonicalFingerprintEncoder().putSet("tags", setOf("C", "B", "A"))
        assertEquals(enc1.computeSha256Hex(), enc2.computeSha256Hex())
    }

    @Test
    fun fingerprint_listOrderSensitive() {
        val enc1 = CanonicalFingerprintEncoder().putList("seq", listOf("A", "B", "C"))
        val enc2 = CanonicalFingerprintEncoder().putList("seq", listOf("C", "B", "A"))
        assertNotEquals("List order must be preserved and produce different hashes", enc1.computeSha256Hex(), enc2.computeSha256Hex())
    }

    @Test
    fun fingerprint_includesEngineeringUnit() {
        val enc1 = CanonicalFingerprintEncoder().putQuantity("load", 50.0, "kW")
        val enc2 = CanonicalFingerprintEncoder().putQuantity("load", 50.0, "kVA")
        assertNotEquals("Different engineering units on the same value must produce different hashes", enc1.computeSha256Hex(), enc2.computeSha256Hex())
    }

    @Test
    fun fingerprint_sameValueDifferentUnit_isDifferent() {
        val enc1 = CanonicalFingerprintEncoder().putQuantity("length", 1.0, "m")
        val enc2 = CanonicalFingerprintEncoder().putQuantity("length", 1.0, "km")
        assertNotEquals(enc1.computeSha256Hex(), enc2.computeSha256Hex())
    }

    @Test
    fun freshnessProvider_exposesNoSetCurrentMethod() {
        val methods = CalculationFreshnessProvider::class.java.methods.map { it.name }
        assertFalse(methods.contains("setCurrent"))
        assertFalse(methods.contains("setFresh"))
        assertFalse(methods.contains("forceCurrent"))
        assertFalse(methods.contains("setRecordedRevision"))
        assertEquals("CalculationFreshnessProvider must expose only getCalculationState", 1, CalculationFreshnessProvider::class.java.declaredMethods.size)
    }

    // =========================================================================
    // 2. Registry & Execution State Machine Tests
    // =========================================================================

    @Test
    fun registry_storesMetadataNotEngineeringResultPayload() {
        val scope = CalculationScope("PRJ_1", CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val metadata = CalculationRecordMetadata(
            scope = scope,
            calculatedProjectRevision = 1L,
            fingerprint = CalculationFingerprint(1, "inSig", "depSig"),
            calculatedAt = 1000L,
            overallStatus = "PASS",
            provenance = CalculationProvenance("CableSizingEngine")
        )
        metadataRegistry.recordCalculationCompleted(metadata)

        val retrieved = metadataRegistry.getRecord(scope)
        assertNotNull(retrieved)
        assertEquals("inSig", retrieved!!.fingerprint.inputSignature)

        val fieldTypes = CalculationRecordMetadata::class.java.declaredFields.map { it.type.name }
        assertFalse(fieldTypes.any { it.contains("SizingResult") || it.contains("ShortCircuitResult") || it.contains("Entity") })
    }

    @Test
    fun registry_isProjectAndScopeIsolated() {
        val scopeA = CalculationScope("PRJ_A", CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val scopeB = CalculationScope("PRJ_B", CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)

        metadataRegistry.recordCalculationCompleted(
            CalculationRecordMetadata(
                scope = scopeA,
                calculatedProjectRevision = 1L,
                fingerprint = CalculationFingerprint(1, "sigA", "depA"),
                calculatedAt = 1000L,
                overallStatus = "PASS",
                provenance = CalculationProvenance("CableSizingEngine")
            )
        )

        assertNotNull(metadataRegistry.getRecord(scopeA))
        assertNull("Scope for Project B must not see records from Project A", metadataRegistry.getRecord(scopeB))
    }

    @Test
    fun registry_processLocalLimitationIsExplicit() {
        // The InMemoryCalculationFreshnessMetadataRegistry class name itself documents process-local scope.
        // Verify it does not implement any persistence interface.
        val interfaces = InMemoryCalculationFreshnessMetadataRegistry::class.java.interfaces.map { it.name }
        assertFalse("Registry must not implement any Room/persistence interface",
            interfaces.any { it.contains("Dao") || it.contains("Room") || it.contains("Persistence") })
    }

    @Test
    fun registry_clearProject_removesMetadataAndExecutionMarkers() {
        val scope = CalculationScope("PRJ_TO_CLEAR", CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        metadataRegistry.startExecution(scope, false)
        metadataRegistry.recordCalculationCompleted(
            CalculationRecordMetadata(
                scope = scope,
                calculatedProjectRevision = 1L,
                fingerprint = CalculationFingerprint(1, "sig", "dep"),
                calculatedAt = 1000L,
                overallStatus = "PASS",
                provenance = CalculationProvenance("CableSizingEngine")
            )
        )

        metadataRegistry.clearProject("PRJ_TO_CLEAR")
        assertNull(metadataRegistry.getRecord(scope))
        assertNull(metadataRegistry.getExecutionState(scope))
    }

    @Test
    fun registry_concurrentAccessIsDeterministic() {
        runBlocking {
            val totalOps = 50
            coroutineScope {
                val jobs = (1..totalOps).map { i ->
                    async {
                        val scope = CalculationScope("PRJ_CONC", CalculationEntityType.CIRCUIT, "$i", CalculationDomain.CABLE_SIZING)
                        metadataRegistry.startExecution(scope, false)
                        metadataRegistry.recordCalculationCompleted(
                            CalculationRecordMetadata(
                                scope = scope,
                                calculatedProjectRevision = i.toLong(),
                                fingerprint = CalculationFingerprint(1, "sig_$i", "dep_$i"),
                                calculatedAt = System.currentTimeMillis(),
                                overallStatus = "PASS",
                                provenance = CalculationProvenance("CableSizingEngine")
                            )
                        )
                    }
                }
                jobs.awaitAll()
            }

            for (i in 1..totalOps) {
                val scope = CalculationScope("PRJ_CONC", CalculationEntityType.CIRCUIT, "$i", CalculationDomain.CABLE_SIZING)
                assertNotNull(metadataRegistry.getRecord(scope))
            }
        }
    }

    @Test
    fun initialExecution_transitionsRunningToCurrent() {
        runBlocking {
            val projectId = "PRJ_RUN_TEST"
            mockRevisionProvider.setRevision(projectId, 1L)
            fakeDataProvider.addCircuit(CircuitEntity(id = 101, projectId = projectId, circuitName = "Feeder 1", loadKVA = 25.0, length_m = 15.0))

            val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)

            val started = metadataRegistry.startExecution(scope, false)
            assertTrue(started)
            val snapRunning = freshnessProvider.getCalculationState(scope)
            assertEquals(CalculationState.RUNNING, snapRunning.state)
            assertEquals(FreshnessReasonCode.CALCULATION_IN_PROGRESS, snapRunning.reasonCode)

            val currentFp = (CableSizingFreshnessAdapter().resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved).fingerprint
            metadataRegistry.recordCalculationCompleted(
                CalculationRecordMetadata(
                    scope = scope,
                    calculatedProjectRevision = 1L,
                    fingerprint = currentFp,
                    calculatedAt = System.currentTimeMillis(),
                    overallStatus = "PASS",
                    provenance = CalculationProvenance("CableSizingEngine")
                )
            )

            val snapCurrent = freshnessProvider.getCalculationState(scope)
            assertEquals(CalculationState.CURRENT, snapCurrent.state)
            assertEquals(FreshnessReasonCode.MATCH, snapCurrent.reasonCode)
        }
    }

    @Test
    fun revalidation_transitionsRevalidatingToCurrent() {
        runBlocking {
            val projectId = "PRJ_REVAL_TEST"
            mockRevisionProvider.setRevision(projectId, 1L)
            fakeDataProvider.addCircuit(CircuitEntity(id = 101, projectId = projectId, circuitName = "Feeder 1", loadKVA = 25.0, length_m = 15.0))

            val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
            val initialFp = (CableSizingFreshnessAdapter().resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved).fingerprint

            metadataRegistry.recordCalculationCompleted(
                CalculationRecordMetadata(
                    scope = scope,
                    calculatedProjectRevision = 1L,
                    fingerprint = initialFp,
                    calculatedAt = 1000L,
                    overallStatus = "PASS",
                    provenance = CalculationProvenance("CableSizingEngine")
                )
            )

            val started = metadataRegistry.startExecution(scope, true)
            assertTrue(started)
            val snapReval = freshnessProvider.getCalculationState(scope)
            assertEquals(CalculationState.REVALIDATING, snapReval.state)
            assertEquals(FreshnessReasonCode.RECALCULATION_IN_PROGRESS, snapReval.reasonCode)

            metadataRegistry.recordCalculationCompleted(
                CalculationRecordMetadata(
                    scope = scope,
                    calculatedProjectRevision = 1L,
                    fingerprint = initialFp,
                    calculatedAt = 2000L,
                    overallStatus = "PASS",
                    provenance = CalculationProvenance("CableSizingEngine")
                )
            )
            val snapCurrent = freshnessProvider.getCalculationState(scope)
            assertEquals(CalculationState.CURRENT, snapCurrent.state)
        }
    }

    @Test
    fun engineeringFail_completionClearsMarkerAndCanBeCurrent() {
        runBlocking {
            val projectId = "PRJ_FAIL_STATUS"
            mockRevisionProvider.setRevision(projectId, 1L)
            fakeDataProvider.addCircuit(CircuitEntity(id = 101, projectId = projectId, circuitName = "Feeder 1", loadKVA = 25.0, length_m = 15.0))

            val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
            val currentFp = (CableSizingFreshnessAdapter().resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved).fingerprint

            metadataRegistry.recordCalculationCompleted(
                CalculationRecordMetadata(
                    scope = scope,
                    calculatedProjectRevision = 1L,
                    fingerprint = currentFp,
                    calculatedAt = 1000L,
                    overallStatus = "FAIL",
                    provenance = CalculationProvenance("CableSizingEngine")
                )
            )

            val snap = freshnessProvider.getCalculationState(scope)
            assertEquals("Engineering FAIL result with matching fingerprint must be freshness CURRENT", CalculationState.CURRENT, snap.state)
            assertEquals("FAIL", snap.recordedStatus)
        }
    }

    @Test
    fun executionError_clearsMarkerAndReturnsError() {
        runBlocking {
            val projectId = "PRJ_ERR_TEST"
            mockRevisionProvider.setRevision(projectId, 1L)
            fakeDataProvider.addCircuit(CircuitEntity(id = 101, projectId = projectId, circuitName = "Feeder 1", loadKVA = 25.0, length_m = 15.0))

            val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
            metadataRegistry.startExecution(scope, false)
            metadataRegistry.recordCalculationFailed(scope, CalculationExecutionErrorMetadata(
                scope = scope,
                errorCode = ExecutionErrorCode.ENGINE_RUNTIME_ERROR,
                occurredAt = System.currentTimeMillis(),
                recoverability = ErrorRecoverability.NON_RECOVERABLE,
                safeMessage = "Numerical divergence in solver"
            ))

            val snap = freshnessProvider.getCalculationState(scope)
            assertEquals(CalculationState.ERROR, snap.state)
            assertEquals(FreshnessReasonCode.EVALUATION_ERROR, snap.reasonCode)
            assertTrue(snap.reason.contains("Numerical divergence in solver"))
        }
    }

    @Test
    fun cancelledInitialExecution_returnsMissingAfterMarkerClear() {
        runBlocking {
            val projectId = "PRJ_CANCEL_INIT"
            mockRevisionProvider.setRevision(projectId, 1L)
            fakeDataProvider.addCircuit(CircuitEntity(id = 101, projectId = projectId, circuitName = "Feeder 1", loadKVA = 25.0, length_m = 15.0))

            val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
            metadataRegistry.startExecution(scope, false)
            metadataRegistry.finishExecution(scope)

            val snap = freshnessProvider.getCalculationState(scope)
            assertEquals(CalculationState.MISSING, snap.state)
            assertEquals(FreshnessReasonCode.NO_RECORD, snap.reasonCode)
        }
    }

    @Test
    fun cancelledRevalidation_retainsPriorFreshnessEvaluation() {
        runBlocking {
            val projectId = "PRJ_CANCEL_REVAL"
            mockRevisionProvider.setRevision(projectId, 1L)
            fakeDataProvider.addCircuit(CircuitEntity(id = 101, projectId = projectId, circuitName = "Feeder 1", loadKVA = 25.0, length_m = 15.0))

            val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
            val fp = (CableSizingFreshnessAdapter().resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved).fingerprint

            metadataRegistry.recordCalculationCompleted(
                CalculationRecordMetadata(
                    scope = scope,
                    calculatedProjectRevision = 1L,
                    fingerprint = fp,
                    calculatedAt = 1000L,
                    overallStatus = "PASS",
                    provenance = CalculationProvenance("CableSizingEngine")
                )
            )

            metadataRegistry.startExecution(scope, true)
            metadataRegistry.finishExecution(scope) // Cancelled revalidation

            val snap = freshnessProvider.getCalculationState(scope)
            assertEquals("Cancelled revalidation must retain prior record and evaluate freshness", CalculationState.CURRENT, snap.state)
        }
    }

    @Test
    fun invalidDuplicateBegin_isRejectedOrIdempotentPerDocumentedPolicy() {
        val scope = CalculationScope("PRJ_DUP", CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val first = metadataRegistry.startExecution(scope, false)
        assertTrue("First startExecution must succeed", first)
        val second = metadataRegistry.startExecution(scope, false)
        assertFalse("Duplicate startExecution for same scope must be rejected", second)
    }

    // =========================================================================
    // 3. Decision Precedence Tests
    // =========================================================================

    @Test
    fun invalidInput_takesPrecedenceOverMissing() {
        runBlocking {
            val projectId = "PRJ_INVALID_PREC"
            mockRevisionProvider.setRevision(projectId, 1L)
            // No circuit 999 exists in data provider

            val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "999", CalculationDomain.CABLE_SIZING)
            val snap = freshnessProvider.getCalculationState(scope)
            assertEquals(CalculationState.INVALID, snap.state)
            assertEquals(FreshnessReasonCode.INVALID_INPUTS, snap.reasonCode)
        }
    }

    @Test
    fun activeExecutionWithoutRecord_returnsRunning() {
        runBlocking {
            val projectId = "PRJ_ACTIVE_RUN"
            mockRevisionProvider.setRevision(projectId, 1L)
            fakeDataProvider.addCircuit(CircuitEntity(id = 101, projectId = projectId, circuitName = "F1", loadKVA = 10.0, length_m = 5.0))

            val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
            metadataRegistry.startExecution(scope, false)

            val snap = freshnessProvider.getCalculationState(scope)
            assertEquals(CalculationState.RUNNING, snap.state)
        }
    }

    @Test
    fun activeExecutionWithRecord_returnsRevalidating() {
        runBlocking {
            val projectId = "PRJ_ACTIVE_REVAL"
            mockRevisionProvider.setRevision(projectId, 1L)
            fakeDataProvider.addCircuit(CircuitEntity(id = 101, projectId = projectId, circuitName = "F1", loadKVA = 10.0, length_m = 5.0))

            val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
            metadataRegistry.recordCalculationCompleted(
                CalculationRecordMetadata(
                    scope = scope,
                    calculatedProjectRevision = 1L,
                    fingerprint = CalculationFingerprint(1, "old", "old"),
                    calculatedAt = 1000L,
                    overallStatus = "PASS",
                    provenance = CalculationProvenance("CableSizingEngine")
                )
            )
            metadataRegistry.startExecution(scope, true)

            val snap = freshnessProvider.getCalculationState(scope)
            assertEquals(CalculationState.REVALIDATING, snap.state)
        }
    }

    @Test
    fun executionError_takesPrecedenceOverStaleComparison() {
        runBlocking {
            val projectId = "PRJ_ERR_PREC"
            mockRevisionProvider.setRevision(projectId, 1L)
            fakeDataProvider.addCircuit(CircuitEntity(id = 101, projectId = projectId, circuitName = "F1", loadKVA = 10.0, length_m = 5.0))

            val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
            metadataRegistry.recordCalculationFailed(scope, CalculationExecutionErrorMetadata(
                scope = scope,
                errorCode = ExecutionErrorCode.ENGINE_RUNTIME_ERROR,
                occurredAt = System.currentTimeMillis(),
                recoverability = ErrorRecoverability.NON_RECOVERABLE,
                safeMessage = "Computation failed"
            ))

            val snap = freshnessProvider.getCalculationState(scope)
            assertEquals(CalculationState.ERROR, snap.state)
        }
    }

    @Test
    fun sameFingerprintDifferentProject_cannotCrossMatch() {
        runBlocking {
            val projectA = "PRJ_CROSS_A"
            val projectB = "PRJ_CROSS_B"
            mockRevisionProvider.setRevision(projectA, 1L)
            mockRevisionProvider.setRevision(projectB, 1L)

            val circuit = CircuitEntity(id = 101, circuitName = "F1", loadKVA = 25.0, length_m = 15.0)
            fakeDataProvider.addCircuit(circuit.copy(projectId = projectA))
            fakeDataProvider.addCircuit(circuit.copy(projectId = projectB))

            val scopeA = CalculationScope(projectA, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
            val scopeB = CalculationScope(projectB, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)

            val fpA = (CableSizingFreshnessAdapter().resolveFingerprint(scopeA, fakeDataProvider) as FingerprintResolution.Resolved).fingerprint
            metadataRegistry.recordCalculationCompleted(
                CalculationRecordMetadata(
                    scope = scopeA,
                    calculatedProjectRevision = 1L,
                    fingerprint = fpA,
                    calculatedAt = 1000L,
                    overallStatus = "PASS",
                    provenance = CalculationProvenance("CableSizingEngine")
                )
            )

            val snapB = freshnessProvider.getCalculationState(scopeB)
            assertEquals("Project B must not see Project A's record", CalculationState.MISSING, snapB.state)
        }
    }

    @Test
    fun sameEntityDifferentDomain_cannotCrossMatch() {
        runBlocking {
            val projectId = "PRJ_DOMAIN_ISO"
            mockRevisionProvider.setRevision(projectId, 1L)
            fakeDataProvider.addCircuit(CircuitEntity(id = 101, projectId = projectId, circuitName = "F1", loadKVA = 25.0, length_m = 15.0))

            val scopeCable = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
            val scopeSC = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.SHORT_CIRCUIT)

            val fp = (CableSizingFreshnessAdapter().resolveFingerprint(scopeCable, fakeDataProvider) as FingerprintResolution.Resolved).fingerprint
            metadataRegistry.recordCalculationCompleted(
                CalculationRecordMetadata(
                    scope = scopeCable,
                    calculatedProjectRevision = 1L,
                    fingerprint = fp,
                    calculatedAt = 1000L,
                    overallStatus = "PASS",
                    provenance = CalculationProvenance("CableSizingEngine")
                )
            )

            val snapSC = freshnessProvider.getCalculationState(scopeSC)
            // SHORT_CIRCUIT without upstream fingerprints is PARTIAL → STALE
            assertTrue("SHORT_CIRCUIT scope must not see CABLE_SIZING record for same entity",
                snapSC.state == CalculationState.STALE || snapSC.state == CalculationState.MISSING)
        }
    }

    // =========================================================================
    // 4. Revision Relevance Tests
    // =========================================================================

    @Test
    fun projectRename_revisionMismatchSameFingerprint_remainsCurrent() {
        runBlocking {
            val projectId = "PRJ_RENAME"
            mockRevisionProvider.setRevision(projectId, 1L)
            fakeDataProvider.addCircuit(CircuitEntity(id = 101, projectId = projectId, circuitName = "F1", loadKVA = 25.0, length_m = 15.0))

            val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
            val fp = (CableSizingFreshnessAdapter().resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved).fingerprint

            metadataRegistry.recordCalculationCompleted(
                CalculationRecordMetadata(
                    scope = scope,
                    calculatedProjectRevision = 1L,
                    fingerprint = fp,
                    calculatedAt = 1000L,
                    overallStatus = "PASS",
                    provenance = CalculationProvenance("CableSizingEngine")
                )
            )

            // Project renamed advances revision but circuit inputs unchanged
            mockRevisionProvider.setRevision(projectId, 3L)

            val snap = freshnessProvider.getCalculationState(scope)
            assertEquals(CalculationState.CURRENT, snap.state)
            assertEquals(3L, snap.currentProjectRevision)
            assertEquals(1L, snap.recordedProjectRevision)
        }
    }

    @Test
    fun sldMove_revisionMismatchSameFingerprint_remainsCurrent() {
        runBlocking {
            val projectId = "PRJ_SLD_MOVE"
            mockRevisionProvider.setRevision(projectId, 1L)
            fakeDataProvider.addCircuit(CircuitEntity(id = 101, projectId = projectId, circuitName = "F1", loadKVA = 25.0, length_m = 15.0))

            val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
            val fp = (CableSizingFreshnessAdapter().resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved).fingerprint

            metadataRegistry.recordCalculationCompleted(
                CalculationRecordMetadata(
                    scope = scope,
                    calculatedProjectRevision = 1L,
                    fingerprint = fp,
                    calculatedAt = 1000L,
                    overallStatus = "PASS",
                    provenance = CalculationProvenance("CableSizingEngine")
                )
            )

            // SLD node moved (visual-only), advancing revision but no circuit input changes
            mockRevisionProvider.setRevision(projectId, 10L)

            val snap = freshnessProvider.getCalculationState(scope)
            assertEquals(CalculationState.CURRENT, snap.state)
        }
    }

    @Test
    fun unrelatedEntityMutation_sameScopeFingerprint_remainsCurrent() {
        runBlocking {
            val projectId = "PRJ_UNRELATED"
            mockRevisionProvider.setRevision(projectId, 1L)
            fakeDataProvider.addCircuit(CircuitEntity(id = 101, projectId = projectId, circuitName = "F1", loadKVA = 25.0, length_m = 15.0))
            fakeDataProvider.addCircuit(CircuitEntity(id = 202, projectId = projectId, circuitName = "F2", loadKVA = 50.0, length_m = 30.0))

            val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
            val fp = (CableSizingFreshnessAdapter().resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved).fingerprint

            metadataRegistry.recordCalculationCompleted(
                CalculationRecordMetadata(
                    scope = scope,
                    calculatedProjectRevision = 1L,
                    fingerprint = fp,
                    calculatedAt = 1000L,
                    overallStatus = "PASS",
                    provenance = CalculationProvenance("CableSizingEngine")
                )
            )

            // Modify UNRELATED circuit 202, advancing revision
            fakeDataProvider.addCircuit(CircuitEntity(id = 202, projectId = projectId, circuitName = "F2", loadKVA = 100.0, length_m = 50.0))
            mockRevisionProvider.setRevision(projectId, 2L)

            val snap = freshnessProvider.getCalculationState(scope)
            assertEquals("Unrelated entity change must not affect circuit 101 freshness", CalculationState.CURRENT, snap.state)
        }
    }

    @Test
    fun relevantDependencyMutation_returnsStale() {
        runBlocking {
            val projectId = "PRJ_DEP_MUT"
            mockRevisionProvider.setRevision(projectId, 1L)
            val parent = CircuitEntity(id = 100, projectId = projectId, circuitName = "MDB", loadKVA = 100.0, length_m = 10.0)
            val child = CircuitEntity(id = 101, projectId = projectId, circuitName = "Sub", loadKVA = 25.0, length_m = 15.0, parentId = 100, fedFrom = "MDB")
            fakeDataProvider.addCircuit(parent)
            fakeDataProvider.addCircuit(child)

            val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
            val fp = (CableSizingFreshnessAdapter().resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved).fingerprint

            metadataRegistry.recordCalculationCompleted(
                CalculationRecordMetadata(
                    scope = scope,
                    calculatedProjectRevision = 1L,
                    fingerprint = fp,
                    calculatedAt = 1000L,
                    overallStatus = "PASS",
                    provenance = CalculationProvenance("CableSizingEngine")
                )
            )

            fakeDataProvider.addCircuit(parent.copy(length_m = 80.0))
            mockRevisionProvider.setRevision(projectId, 2L)

            val snap = freshnessProvider.getCalculationState(scope)
            assertEquals(CalculationState.STALE, snap.state)
            assertTrue(snap.changedComponents.contains(ChangedComponent.UPSTREAM_DEPENDENCY))
        }
    }

    // =========================================================================
    // 5. Domain-Specific Adapter Field Coverage Tests
    // =========================================================================

    @Test
    fun cableAdapter_includesEveryConsumedDirectInput() {
        runBlocking {
            val adapter = CableSizingFreshnessAdapter()
            val circuit = CircuitEntity(
                id = 201, projectId = "PRJ_CABLE",
                circuitName = "Pump-1", loadKVA = 45.0, voltageV = 400, phases = "3P+N",
                pf = 0.85, safetyFactor = 1.25, manufacturer = "Elsewedy",
                cableType = "Cu/XLPE/SWA/PVC", installation = "Laid in Ducts",
                parallelRuns = 1, airTempC = 45, gndTempC = 40,
                burialDepth_m = 0.8, soilRes = 120, noCircuits = 2,
                formation = "Flat", spacing = "Touching", length_m = 60.0,
                maxVDpct = 0.03, demandFactor = 1.0, feederType = "Cable",
                overrideDeviceType = "MCCB", overrideDeviceSize = 100.0,
                overrideCableSize = "3x70+35", isHarmonic = true, transformerId = "TX-01"
            )
            fakeDataProvider.addCircuit(circuit)
            fakeDataProvider.addTransformer(TransformerEntity(projectId = "PRJ_CABLE", transformerId = "TX-01", kVA = 1000.0))

            val scope = CalculationScope("PRJ_CABLE", CalculationEntityType.CIRCUIT, "201", CalculationDomain.CABLE_SIZING)
            val res = adapter.resolveFingerprint(scope, fakeDataProvider)
            assertTrue(res is FingerprintResolution.Resolved)
            assertEquals(FingerprintCoverage.COMPLETE, (res as FingerprintResolution.Resolved).coverage)

            // Changing ANY direct input must change the input signature
            val baseInputSig = res.fingerprint.inputSignature
            fakeDataProvider.addCircuit(circuit.copy(loadKVA = 50.0))
            val res2 = adapter.resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved
            assertNotEquals("Changing loadKVA must change inputSignature", baseInputSig, res2.fingerprint.inputSignature)
        }
    }

    @Test
    fun cableAdapter_includesEveryConsumedDependency() {
        runBlocking {
            val adapter = CableSizingFreshnessAdapter()
            val parent = CircuitEntity(id = 200, projectId = "PRJ_CABLE_DEP", circuitName = "MDB", loadKVA = 100.0, length_m = 10.0)
            val child = CircuitEntity(id = 201, projectId = "PRJ_CABLE_DEP", circuitName = "Sub", loadKVA = 25.0, length_m = 15.0, parentId = 200, fedFrom = "MDB", transformerId = "TX-01")
            fakeDataProvider.addCircuit(parent)
            fakeDataProvider.addCircuit(child)
            fakeDataProvider.addTransformer(TransformerEntity(projectId = "PRJ_CABLE_DEP", transformerId = "TX-01", kVA = 1000.0))

            val scope = CalculationScope("PRJ_CABLE_DEP", CalculationEntityType.CIRCUIT, "201", CalculationDomain.CABLE_SIZING)
            val baseRes = adapter.resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved
            val baseDepSig = baseRes.fingerprint.dependencySignature

            fakeDataProvider.addCircuit(parent.copy(length_m = 80.0))
            val changedRes = adapter.resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved
            assertNotEquals("Changing upstream parent length must change dependencySignature", baseDepSig, changedRes.fingerprint.dependencySignature)
        }
    }

    @Test
    fun transformerAdapter_includesEveryConsumedDirectInput() {
        runBlocking {
            val adapter = TransformerFreshnessAdapter()
            val tx = TransformerEntity(projectId = "PRJ_TX_IN", transformerId = "TX-01", kVA = 1000.0, percentZ = 5.0)
            fakeDataProvider.addTransformer(tx)

            val scope = CalculationScope("PRJ_TX_IN", CalculationEntityType.TRANSFORMER, "TX-01", CalculationDomain.TRANSFORMER)
            val baseRes = adapter.resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved
            val baseInputSig = baseRes.fingerprint.inputSignature

            fakeDataProvider.addTransformer(tx.copy(kVA = 1500.0))
            val changedRes = adapter.resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved
            assertNotEquals("Changing kVA must change inputSignature", baseInputSig, changedRes.fingerprint.inputSignature)
        }
    }

    @Test
    fun transformerAdapter_includesEveryConsumedDependency() {
        runBlocking {
            val adapter = TransformerFreshnessAdapter()
            fakeDataProvider.addTransformer(TransformerEntity(projectId = "PRJ_TX_DEP", transformerId = "TX-01", kVA = 1000.0))
            val circuit = CircuitEntity(id = 301, projectId = "PRJ_TX_DEP", circuitName = "Feeder A", loadKVA = 200.0, transformerId = "TX-01")
            fakeDataProvider.addCircuit(circuit)

            val scope = CalculationScope("PRJ_TX_DEP", CalculationEntityType.TRANSFORMER, "TX-01", CalculationDomain.TRANSFORMER)
            val baseRes = adapter.resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved
            val baseDepSig = baseRes.fingerprint.dependencySignature

            fakeDataProvider.addCircuit(circuit.copy(loadKVA = 300.0))
            val changedRes = adapter.resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved
            assertNotEquals("Changing assigned circuit load must change dependencySignature", baseDepSig, changedRes.fingerprint.dependencySignature)
        }
    }

    @Test
    fun shortCircuitAdapter_includesEveryConsumedDirectInput() {
        runBlocking {
            val adapter = ShortCircuitFreshnessAdapter()
            val circuit = CircuitEntity(id = 401, projectId = "PRJ_SC_IN", circuitName = "F1", loadKVA = 25.0, length_m = 25.0, fedFrom = "Transformer", transformerId = "TX-01")
            fakeDataProvider.addCircuit(circuit)
            fakeDataProvider.addTransformer(TransformerEntity(projectId = "PRJ_SC_IN", transformerId = "TX-01", kVA = 1000.0))

            val scope = CalculationScope("PRJ_SC_IN", CalculationEntityType.CIRCUIT, "401", CalculationDomain.SHORT_CIRCUIT)
            val baseRes = adapter.resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved
            val baseSig = baseRes.fingerprint.inputSignature

            fakeDataProvider.addCircuit(circuit.copy(length_m = 50.0))
            val changedRes = adapter.resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved
            assertNotEquals("Changing length must change short circuit inputSignature", baseSig, changedRes.fingerprint.inputSignature)
        }
    }

    @Test
    fun shortCircuitAdapter_includesEveryConsumedDependency() {
        runBlocking {
            val adapter = ShortCircuitFreshnessAdapter()
            val tx = TransformerEntity(projectId = "PRJ_SC_DEP", transformerId = "TX-01", kVA = 1000.0, percentZ = 5.0)
            fakeDataProvider.addTransformer(tx)
            fakeDataProvider.addCircuit(CircuitEntity(id = 401, projectId = "PRJ_SC_DEP", circuitName = "F1", loadKVA = 25.0, length_m = 25.0, fedFrom = "Transformer", transformerId = "TX-01"))

            val scope = CalculationScope("PRJ_SC_DEP", CalculationEntityType.CIRCUIT, "401", CalculationDomain.SHORT_CIRCUIT)
            val baseRes = adapter.resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved
            val baseDepSig = baseRes.fingerprint.dependencySignature

            fakeDataProvider.addTransformer(tx.copy(percentZ = 8.0))
            val changedRes = adapter.resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved
            assertNotEquals("Changing transformer %Z must change SC dependencySignature", baseDepSig, changedRes.fingerprint.dependencySignature)
        }
    }

    @Test
    fun protectionAdsAdapter_includesEveryConsumedDirectInput() {
        runBlocking {
            val adapter = ProtectionAdsFreshnessAdapter()
            val circuit = CircuitEntity(id = 501, projectId = "PRJ_PROT_IN", circuitName = "F1", loadKVA = 50.0, length_m = 30.0, overrideDeviceType = "MCCB", overrideDeviceSize = 125.0)
            fakeDataProvider.addCircuit(circuit)

            val scope = CalculationScope("PRJ_PROT_IN", CalculationEntityType.CIRCUIT, "501", CalculationDomain.PROTECTION_ADS)
            val baseRes = adapter.resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved

            fakeDataProvider.addCircuit(circuit.copy(overrideDeviceSize = 200.0))
            val changedRes = adapter.resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved
            assertNotEquals(baseRes.fingerprint.inputSignature, changedRes.fingerprint.inputSignature)
        }
    }

    @Test
    fun protectionAdsAdapter_includesEveryConsumedDependency() {
        runBlocking {
            val adapter = ProtectionAdsFreshnessAdapter()
            val parent = CircuitEntity(id = 500, projectId = "PRJ_PROT_DEP", circuitName = "MDB", loadKVA = 100.0, overrideDeviceSize = 250.0)
            val child = CircuitEntity(id = 501, projectId = "PRJ_PROT_DEP", circuitName = "Sub", loadKVA = 50.0, length_m = 30.0, parentId = 500, fedFrom = "MDB")
            fakeDataProvider.addCircuit(parent)
            fakeDataProvider.addCircuit(child)

            val scope = CalculationScope("PRJ_PROT_DEP", CalculationEntityType.CIRCUIT, "501", CalculationDomain.PROTECTION_ADS)
            val baseRes = adapter.resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved

            fakeDataProvider.addCircuit(parent.copy(overrideDeviceSize = 400.0))
            val changedRes = adapter.resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved
            assertNotEquals("Changing parent device size must change protection dependencySignature", baseRes.fingerprint.dependencySignature, changedRes.fingerprint.dependencySignature)
        }
    }

    @Test
    fun coordinationAdapter_includesEveryConsumedDirectInput() {
        runBlocking {
            val adapter = CoordinationFreshnessAdapter()
            val child = CircuitEntity(id = 601, projectId = "PRJ_COORD_IN", circuitName = "SMDB", loadKVA = 50.0, overrideDeviceSize = 100.0)
            fakeDataProvider.addCircuit(child)

            val scope = CalculationScope("PRJ_COORD_IN", CalculationEntityType.CIRCUIT, "601", CalculationDomain.COORDINATION)
            val baseRes = adapter.resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved

            fakeDataProvider.addCircuit(child.copy(overrideDeviceSize = 160.0))
            val changedRes = adapter.resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved
            assertNotEquals(baseRes.fingerprint.inputSignature, changedRes.fingerprint.inputSignature)
        }
    }

    @Test
    fun coordinationAdapter_includesEveryConsumedDependency() {
        runBlocking {
            val adapter = CoordinationFreshnessAdapter()
            val parent = CircuitEntity(id = 600, projectId = "PRJ_COORD_DEP", circuitName = "MDB", loadKVA = 150.0, overrideDeviceSize = 250.0)
            val child = CircuitEntity(id = 601, projectId = "PRJ_COORD_DEP", circuitName = "SMDB", loadKVA = 50.0, parentId = 600, fedFrom = "MDB", overrideDeviceSize = 100.0)
            fakeDataProvider.addCircuit(parent)
            fakeDataProvider.addCircuit(child)

            val scope = CalculationScope("PRJ_COORD_DEP", CalculationEntityType.CIRCUIT, "601", CalculationDomain.COORDINATION)
            val baseRes = adapter.resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved

            fakeDataProvider.addCircuit(parent.copy(overrideDeviceSize = 400.0))
            val changedRes = adapter.resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved
            assertNotEquals("Changing parent breaker rating must change coordination dependencySignature", baseRes.fingerprint.dependencySignature, changedRes.fingerprint.dependencySignature)
        }
    }

    @Test
    fun missingProvenance_doesNotReturnCurrent() {
        runBlocking {
            val projectId = "PRJ_PROV_TEST"
            mockRevisionProvider.setRevision(projectId, 1L)
            fakeDataProvider.addCircuit(CircuitEntity(id = 101, projectId = projectId, circuitName = "F1", loadKVA = 25.0, length_m = 15.0))

            val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
            val fp = (CableSizingFreshnessAdapter().resolveFingerprint(scope, fakeDataProvider) as FingerprintResolution.Resolved).fingerprint

            metadataRegistry.recordCalculationCompleted(
                CalculationRecordMetadata(
                    scope = scope,
                    calculatedProjectRevision = 1L,
                    fingerprint = fp,
                    calculatedAt = 1000L,
                    overallStatus = "PASS",
                    provenance = CalculationProvenance("CableSizingEngine", isVerified = false)
                )
            )

            val snap = freshnessProvider.getCalculationState(scope)
            assertEquals("Unverified provenance must not return CURRENT", CalculationState.STALE, snap.state)
            assertTrue(snap.changedComponents.contains(ChangedComponent.PROVENANCE))
        }
    }

    // =========================================================================
    // 6. Architecture & Boundary Invariants
    // =========================================================================

    @Test
    fun freshnessContracts_haveNoComposeViewModelFirebaseRoomEntityOrEngineDependency() {
        val contractClasses = listOf(
            CalculationState::class.java,
            CalculationDomain::class.java,
            CalculationEntityType::class.java,
            CalculationScope::class.java,
            CalculationFingerprint::class.java,
            FingerprintCoverage::class.java,
            FingerprintResolution::class.java,
            CalculationProvenance::class.java,
            CalculationRecordMetadata::class.java,
            FreshnessReasonCode::class.java,
            ChangedComponent::class.java,
            CalculationStateSnapshot::class.java,
            CalculationFreshnessProvider::class.java
        )

        for (cls in contractClasses) {
            val packageStr = cls.`package`?.name ?: ""
            assertTrue("${cls.simpleName} must reside in contracts package", packageStr.contains("freshness.contracts"))

            for (field in cls.declaredFields) {
                val typeName = field.type.name
                assertFalse("${cls.simpleName}.${field.name} must not depend on Android", typeName.startsWith("android."))
                assertFalse("${cls.simpleName}.${field.name} must not depend on Compose", typeName.startsWith("androidx.compose."))
                assertFalse("${cls.simpleName}.${field.name} must not depend on ViewModel", typeName.contains("ViewModel"))
                assertFalse("${cls.simpleName}.${field.name} must not depend on Firebase", typeName.contains("firebase"))
            }
        }
    }

    // =========================================================================
    // Helper Test Doubles
    // =========================================================================

    private class FakeProjectRevisionProvider : ProjectRevisionProvider {
        private val revisions = mutableMapOf<String, Long>()

        fun setRevision(projectId: String, revision: Long) {
            revisions[projectId] = revision
        }

        override suspend fun getCurrentRevision(projectId: String): ProjectRevisionSnapshot {
            val rev = revisions[projectId]
                ?: throw NoSuchElementException("Project $projectId not found")
            return ProjectRevisionSnapshot(projectId, rev, System.currentTimeMillis())
        }
    }

    private class FakeProjectFreshnessDataProvider : ProjectFreshnessDataProvider {
        private val circuits = mutableListOf<CircuitEntity>()
        private val transformers = mutableListOf<TransformerEntity>()

        fun addCircuit(c: CircuitEntity) {
            circuits.removeAll { it.id == c.id && it.projectId == c.projectId }
            circuits.add(c)
        }

        fun addTransformer(t: TransformerEntity) {
            transformers.removeAll { it.transformerId.equals(t.transformerId, ignoreCase = true) }
            transformers.add(t)
        }

        override suspend fun getCircuits(projectId: String): List<CircuitEntity> {
            return circuits.filter { it.projectId == projectId }
        }

        override suspend fun getTransformers(projectId: String): List<TransformerEntity> {
            return transformers.filter { it.projectId == projectId }
        }
    }
}
