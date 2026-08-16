package com.example.ai.contracts

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Test

/**
 * Concrete test payload classes for verifying generic envelopes.
 */
data class TestCableSizingParams(
    val loadKVA: Double,
    val voltageV: Int,
    val length_m: Double,
    val cableType: String
)

data class TestCableSizingOutput(
    val selectedSize_mm2: String,
    val deviceSize_A: Double,
    val voltDropPct: Double
)

class CoreRuntimeContractsTest {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // =========================================================================
    // 1. Canonical EngineeringUnit & EngineeringValue Tests (Correction A)
    // =========================================================================

    @Test
    fun engineeringValue_usesTypedEngineeringUnit() {
        val current = EngineeringValue.ofDecimal(125.0, EngineeringUnit.AMPERE)
        assertEquals(EngineeringUnit.AMPERE, current.unit)
        assertEquals("A", current.unit?.symbol)
        assertEquals("125.0 A", current.formattedDisplay)
    }

    @Test
    fun engineeringValue_roundTripPreservesTypedUnit() {
        val voltage = EngineeringValue.ofDecimal(400.0, EngineeringUnit.VOLT, "400.0 V")
        val adapter = moshi.adapter(EngineeringValue::class.java)
        val json = adapter.toJson(voltage)
        assertNotNull(json)
        assertTrue(json.contains("\"unit\":\"V\"") || json.contains("\"unit\":\"VOLT\""))

        val restored = adapter.fromJson(json)
        assertNotNull(restored)
        assertEquals(EngineeringUnit.VOLT, restored!!.unit)
        assertEquals(400.0, restored.decimalValue ?: 0.0, 0.001)
        assertEquals("400.0 V", restored.formattedDisplay)
    }

    @Test
    fun engineeringValue_dimensionlessCountUsesNoneUnit() {
        val runs = EngineeringValue.ofInt(4, EngineeringUnit.NONE, "4 runs")
        assertEquals(EngineeringUnit.NONE, runs.unit)
        assertEquals(4L, runs.intValue)
        assertEquals("4 runs", runs.formattedDisplay)
    }

    @Test
    fun engineeringUnit_containsAllSupportedCanonicalUnits() {
        val units = setOf(
            EngineeringUnit.AMPERE,
            EngineeringUnit.VOLT,
            EngineeringUnit.KILOVOLT_AMPERE,
            EngineeringUnit.KILOWATT,
            EngineeringUnit.METER,
            EngineeringUnit.SQUARE_MILLIMETER,
            EngineeringUnit.OHM,
            EngineeringUnit.MILLIOHM,
            EngineeringUnit.PERCENT,
            EngineeringUnit.KILOAMPERE,
            EngineeringUnit.SECOND,
            EngineeringUnit.CELSIUS,
            EngineeringUnit.NONE
        )
        assertEquals(13, units.size)
        assertEquals("A", EngineeringUnit.AMPERE.symbol)
        assertEquals("V", EngineeringUnit.VOLT.symbol)
        assertEquals("kVA", EngineeringUnit.KILOVOLT_AMPERE.symbol)
        assertEquals("kW", EngineeringUnit.KILOWATT.symbol)
        assertEquals("m", EngineeringUnit.METER.symbol)
        assertEquals("mm²", EngineeringUnit.SQUARE_MILLIMETER.symbol)
        assertEquals("Ω", EngineeringUnit.OHM.symbol)
        assertEquals("mΩ", EngineeringUnit.MILLIOHM.symbol)
        assertEquals("%", EngineeringUnit.PERCENT.symbol)
        assertEquals("kA", EngineeringUnit.KILOAMPERE.symbol)
        assertEquals("s", EngineeringUnit.SECOND.symbol)
        assertEquals("°C", EngineeringUnit.CELSIUS.symbol)
        assertEquals("NONE", EngineeringUnit.NONE.symbol)
    }

    @Test
    fun engineeringValue_rejectsDecimalWithoutDecimalValue() {
        assertThrows(IllegalArgumentException::class.java) {
            EngineeringValue(
                kind = EngineeringValueKind.DECIMAL,
                decimalValue = null,
                unit = EngineeringUnit.AMPERE
            )
        }
    }

    @Test
    fun engineeringValue_rejectsMultipleCanonicalValues() {
        assertThrows(IllegalArgumentException::class.java) {
            EngineeringValue(
                kind = EngineeringValueKind.DECIMAL,
                decimalValue = 125.0,
                textValue = "125 A"
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            EngineeringValue(
                kind = EngineeringValueKind.INTEGER,
                intValue = 3L,
                booleanValue = true
            )
        }
    }

    @Test
    fun engineeringValue_missingRejectsNonNullCanonicalValue() {
        assertThrows(IllegalArgumentException::class.java) {
            EngineeringValue(
                kind = EngineeringValueKind.MISSING,
                decimalValue = 0.0
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            EngineeringValue(
                kind = EngineeringValueKind.MISSING,
                intValue = 0L
            )
        }
    }

    // =========================================================================
    // 2. ToolResultEnvelope Direct Construction Invariants (Correction B)
    // =========================================================================

    @Test
    fun errorStatus_rejectsNonNullResult() {
        val output = TestCableSizingOutput("50 mm²", 100.0, 1.8)
        val error = StructuredError(errorCode = "ERR", message = "Failure")

        assertThrows(IllegalArgumentException::class.java) {
            ToolResultEnvelope(
                requestId = "REQ-01",
                toolName = "CalculateCableSizing",
                projectId = "PRJ_01",
                status = ToolStatus.ERROR,
                result = output,
                errors = listOf(error),
                completedAt = System.currentTimeMillis()
            )
        }
    }

    @Test
    fun invalidInput_rejectsNonNullResult() {
        val output = TestCableSizingOutput("50 mm²", 100.0, 1.8)
        val error = StructuredError(errorCode = "INV", message = "Invalid input")

        assertThrows(IllegalArgumentException::class.java) {
            ToolResultEnvelope(
                requestId = "REQ-02",
                toolName = "CalculateCableSizing",
                projectId = "PRJ_01",
                status = ToolStatus.INVALID_INPUT,
                result = output,
                errors = listOf(error),
                completedAt = System.currentTimeMillis()
            )
        }
    }

    @Test
    fun incomplete_rejectsNonNullResult() {
        val output = TestCableSizingOutput("50 mm²", 100.0, 1.8)
        val error = StructuredError(errorCode = "INC", message = "Missing length")

        assertThrows(IllegalArgumentException::class.java) {
            ToolResultEnvelope(
                requestId = "REQ-03",
                toolName = "CalculateCableSizing",
                projectId = "PRJ_01",
                status = ToolStatus.INCOMPLETE,
                result = output,
                errors = listOf(error),
                completedAt = System.currentTimeMillis()
            )
        }
    }

    @Test
    fun notSupported_rejectsNonNullResult() {
        val output = TestCableSizingOutput("50 mm²", 100.0, 1.8)
        val error = StructuredError(errorCode = "UNSUPP", message = "Unsupported")

        assertThrows(IllegalArgumentException::class.java) {
            ToolResultEnvelope(
                requestId = "REQ-04",
                toolName = "CalculateCableSizing",
                projectId = "PRJ_01",
                status = ToolStatus.NOT_SUPPORTED,
                result = output,
                errors = listOf(error),
                completedAt = System.currentTimeMillis()
            )
        }
    }

    @Test
    fun cancelled_rejectsNonNullResult() {
        val output = TestCableSizingOutput("50 mm²", 100.0, 1.8)

        assertThrows(IllegalArgumentException::class.java) {
            ToolResultEnvelope(
                requestId = "REQ-05",
                toolName = "CalculateCableSizing",
                projectId = "PRJ_01",
                status = ToolStatus.CANCELLED,
                result = output,
                completedAt = System.currentTimeMillis()
            )
        }
    }

    @Test
    fun running_rejectsNonNullResult() {
        val output = TestCableSizingOutput("50 mm²", 100.0, 1.8)

        assertThrows(IllegalArgumentException::class.java) {
            ToolResultEnvelope(
                requestId = "REQ-06",
                toolName = "CalculateCableSizing",
                projectId = "PRJ_01",
                status = ToolStatus.RUNNING,
                result = output,
                completedAt = null
            )
        }
    }

    @Test
    fun revalidating_rejectsNonNullResult() {
        val output = TestCableSizingOutput("50 mm²", 100.0, 1.8)

        assertThrows(IllegalArgumentException::class.java) {
            ToolResultEnvelope(
                requestId = "REQ-07",
                toolName = "CalculateCableSizing",
                projectId = "PRJ_01",
                status = ToolStatus.REVALIDATING,
                result = output,
                completedAt = null
            )
        }
    }

    @Test
    fun engineeringFail_requiresResult() {
        assertThrows(IllegalArgumentException::class.java) {
            ToolResultEnvelope<TestCableSizingOutput>(
                requestId = "REQ-08",
                toolName = "CalculateCableSizing",
                projectId = "PRJ_01",
                status = ToolStatus.FAIL,
                result = null,
                completedAt = System.currentTimeMillis()
            )
        }
    }

    @Test
    fun engineeringWarning_requiresResult() {
        assertThrows(IllegalArgumentException::class.java) {
            ToolResultEnvelope<TestCableSizingOutput>(
                requestId = "REQ-09",
                toolName = "CalculateCableSizing",
                projectId = "PRJ_01",
                status = ToolStatus.WARNING,
                result = null,
                completedAt = System.currentTimeMillis()
            )
        }
    }

    @Test
    fun pass_requiresCompletedAt() {
        val output = TestCableSizingOutput("50 mm²", 100.0, 1.8)
        assertThrows(IllegalArgumentException::class.java) {
            ToolResultEnvelope(
                requestId = "REQ-10",
                toolName = "CalculateCableSizing",
                projectId = "PRJ_01",
                status = ToolStatus.PASS,
                result = output,
                completedAt = null
            )
        }
    }

    @Test
    fun running_requiresNullCompletedAt() {
        assertThrows(IllegalArgumentException::class.java) {
            ToolResultEnvelope<TestCableSizingOutput>(
                requestId = "REQ-11",
                toolName = "CalculateCableSizing",
                projectId = "PRJ_01",
                status = ToolStatus.RUNNING,
                completedAt = System.currentTimeMillis()
            )
        }
    }

    @Test
    fun stale_requiresExplicitStaleEvidenceOrNullResult() {
        val output = TestCableSizingOutput("50 mm²", 100.0, 1.8)

        // Non-null result without provenance is rejected
        assertThrows(IllegalArgumentException::class.java) {
            ToolResultEnvelope(
                requestId = "REQ-12",
                toolName = "CalculateCableSizing",
                projectId = "PRJ_01",
                status = ToolStatus.STALE,
                result = output,
                provenance = null,
                completedAt = System.currentTimeMillis()
            )
        }

        // Non-null result with provenance is accepted
        val provenance = ProvenanceMetadata(
            engineName = "CableSizingEngine",
            projectId = "PRJ_01",
            projectRevision = 1L
        )
        val validStale = ToolResultEnvelope(
            requestId = "REQ-13",
            toolName = "CalculateCableSizing",
            projectId = "PRJ_01",
            status = ToolStatus.STALE,
            result = output,
            provenance = provenance,
            completedAt = System.currentTimeMillis()
        )
        assertEquals(ToolStatus.STALE, validStale.status)
        assertNotNull(validStale.provenance)
    }

    // =========================================================================
    // 3. Typed Request Authorization for Mutation (Correction C)
    // =========================================================================

    @Test
    fun mutationRequest_returnsBlockedAuthorization() {
        val request = ToolRequestEnvelope(
            requestId = "REQ-MUT-01",
            projectId = "PRJ_01",
            toolName = "UpdateCircuitFeeder",
            executionMode = ExecutionMode.MUTATION,
            parameters = "params"
        )
        val auth = ToolRequestExecutionPolicy.validate(request)
        assertTrue(auth is ExecutionAuthorization.Blocked)
    }

    @Test
    fun mutationAuthorization_containsStructuredError() {
        val request = ToolRequestEnvelope(
            requestId = "REQ-MUT-02",
            projectId = "PRJ_01",
            toolName = "UpdateCircuitFeeder",
            executionMode = ExecutionMode.MUTATION,
            parameters = "params"
        )
        val auth = ToolRequestExecutionPolicy.validate(request) as ExecutionAuthorization.Blocked
        assertNotNull(auth.error)
        assertFalse(auth.error.isRecoverable)
    }

    @Test
    fun mutationAuthorization_usesStableErrorCode() {
        val request = ToolRequestEnvelope(
            requestId = "REQ-MUT-03",
            projectId = "PRJ_01",
            toolName = "UpdateCircuitFeeder",
            executionMode = ExecutionMode.MUTATION,
            parameters = "params"
        )
        val auth = ToolRequestExecutionPolicy.validate(request) as ExecutionAuthorization.Blocked
        assertEquals("MUTATION_NOT_AVAILABLE", auth.error.errorCode)
    }

    @Test
    fun nonMutationRequests_returnAllowedAuthorization() {
        val readOnlyReq = ToolRequestEnvelope(
            requestId = "REQ-01",
            projectId = "PRJ_01",
            toolName = "GetCircuit",
            executionMode = ExecutionMode.READ_ONLY,
            parameters = "params"
        )
        assertEquals(ExecutionAuthorization.Allowed, ToolRequestExecutionPolicy.validate(readOnlyReq))

        val calcReq = ToolRequestEnvelope(
            requestId = "REQ-02",
            projectId = "PRJ_01",
            toolName = "CalculateCableSizing",
            executionMode = ExecutionMode.LIVE_CALCULATION,
            parameters = "params"
        )
        assertEquals(ExecutionAuthorization.Allowed, ToolRequestExecutionPolicy.validate(calcReq))

        val whatIfReq = ToolRequestEnvelope(
            requestId = "REQ-03",
            projectId = "PRJ_01",
            toolName = "SimulateLoading",
            executionMode = ExecutionMode.WHAT_IF,
            parameters = "params"
        )
        assertEquals(ExecutionAuthorization.Allowed, ToolRequestExecutionPolicy.validate(whatIfReq))

        val validateReq = ToolRequestEnvelope(
            requestId = "REQ-04",
            projectId = "PRJ_01",
            toolName = "ValidateSld",
            executionMode = ExecutionMode.VALIDATE_ONLY,
            parameters = "params"
        )
        assertEquals(ExecutionAuthorization.Allowed, ToolRequestExecutionPolicy.validate(validateReq))
    }

    @Test
    fun proposalRequest_isAllowedButDoesNotAuthorizeMutation() {
        val proposalReq = ToolRequestEnvelope(
            requestId = "REQ-PROP-01",
            projectId = "PRJ_01",
            toolName = "ProposeCableUpsize",
            executionMode = ExecutionMode.PROPOSAL,
            parameters = "params"
        )
        val auth = ToolRequestExecutionPolicy.validate(proposalReq)
        assertEquals(ExecutionAuthorization.Allowed, auth)
        assertNotEquals(ExecutionMode.MUTATION, proposalReq.executionMode)
    }

    @Test
    fun mutationRequest_cannotReturnAllowedInCurrentPhase() {
        val mutationReq = ToolRequestEnvelope(
            requestId = "REQ-MUT-99",
            projectId = "PRJ_01",
            toolName = "DirectMutate",
            executionMode = ExecutionMode.MUTATION,
            parameters = "params"
        )
        val auth = ToolRequestExecutionPolicy.validate(mutationReq)
        assertNotEquals(ExecutionAuthorization.Allowed, auth)
    }

    @Test
    fun requestAuthorization_hasNoRoomViewModelFirebaseOrEngineDependency() {
        val policyClass = ToolRequestExecutionPolicy::class.java
        for (method in policyClass.declaredMethods) {
            for (param in method.parameterTypes) {
                assertFalse(param.name.startsWith("androidx.room"))
                assertFalse(param.name.startsWith("androidx.lifecycle"))
                assertFalse(param.name.startsWith("com.google.firebase"))
                assertFalse(param.name.startsWith("com.example.engine"))
            }
        }
    }

    // =========================================================================
    // 4. Completed vs Execution Failure Factory Tests
    // =========================================================================

    @Test
    fun completedEnvelope_allowsPassWithResult() {
        val output = TestCableSizingOutput("50 mm²", 100.0, 1.8)
        val envelope = ToolResultEnvelope.completed(
            requestId = "REQ-COMP-01",
            toolName = "CalculateCableSizing",
            projectId = "PRJ_01",
            status = ToolStatus.PASS,
            result = output
        )
        assertEquals(ToolStatus.PASS, envelope.status)
        assertEquals(output, envelope.result)
        assertTrue(envelope.errors.isEmpty())
    }

    @Test
    fun completedEnvelope_allowsWarningWithResult() {
        val output = TestCableSizingOutput("50 mm²", 100.0, 2.9)
        val envelope = ToolResultEnvelope.completed(
            requestId = "REQ-COMP-02",
            toolName = "CalculateCableSizing",
            projectId = "PRJ_01",
            status = ToolStatus.WARNING,
            result = output
        )
        assertEquals(ToolStatus.WARNING, envelope.status)
        assertEquals(output, envelope.result)
        assertTrue(envelope.errors.isEmpty())
    }

    @Test
    fun completedEnvelope_allowsEngineeringFailWithResult() {
        val output = TestCableSizingOutput("25 mm²", 63.0, 6.2)
        val envelope = ToolResultEnvelope.completed(
            requestId = "REQ-COMP-03",
            toolName = "CalculateCableSizing",
            projectId = "PRJ_01",
            status = ToolStatus.FAIL,
            result = output
        )
        assertEquals(ToolStatus.FAIL, envelope.status)
        assertEquals(output, envelope.result)
        assertTrue(envelope.errors.isEmpty())
    }

    @Test
    fun completedEnvelope_rejectsExecutionErrorStatus() {
        val output = TestCableSizingOutput("50 mm²", 100.0, 1.8)
        assertThrows(IllegalArgumentException::class.java) {
            ToolResultEnvelope.completed(
                requestId = "REQ-COMP-ERR",
                toolName = "CalculateCableSizing",
                projectId = "PRJ_01",
                status = ToolStatus.ERROR,
                result = output
            )
        }
    }

    @Test
    fun executionFailure_rejectsPassWarningRunningAndRevalidating() {
        val error = listOf(StructuredError(errorCode = "ERR", message = "Failure"))

        assertThrows(IllegalArgumentException::class.java) {
            ToolResultEnvelope.executionFailure<TestCableSizingOutput>(
                requestId = "REQ-01",
                toolName = "CalculateCableSizing",
                projectId = "PRJ_01",
                status = ToolStatus.PASS,
                errors = error
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            ToolResultEnvelope.executionFailure<TestCableSizingOutput>(
                requestId = "REQ-02",
                toolName = "CalculateCableSizing",
                projectId = "PRJ_01",
                status = ToolStatus.WARNING,
                errors = error
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            ToolResultEnvelope.executionFailure<TestCableSizingOutput>(
                requestId = "REQ-03",
                toolName = "CalculateCableSizing",
                projectId = "PRJ_01",
                status = ToolStatus.RUNNING,
                errors = error
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            ToolResultEnvelope.executionFailure<TestCableSizingOutput>(
                requestId = "REQ-04",
                toolName = "CalculateCableSizing",
                projectId = "PRJ_01",
                status = ToolStatus.REVALIDATING,
                errors = error
            )
        }
    }

    @Test
    fun completedEngineeringFail_roundTripsWithResultAndChecksWithoutErrors() {
        val output = TestCableSizingOutput("25 mm²", 63.0, 6.2)
        val check = EngineeringCheck(
            checkId = "CHECK_VOLT_DROP",
            checkName = "Permissible Voltage Drop",
            status = ToolStatus.FAIL,
            actualValue = EngineeringValue.ofDecimal(6.2, EngineeringUnit.PERCENT),
            requiredLimit = EngineeringValue.ofDecimal(3.0, EngineeringUnit.PERCENT),
            comparisonCriterion = "<=",
            standardReference = "IEC 60364-5-52",
            message = "Voltage drop 6.2% exceeds maximum allowable 3.0% threshold"
        )
        val envelope = ToolResultEnvelope.fail(
            requestId = "REQ-FAIL-RT",
            toolName = "CalculateCableSizing",
            projectId = "PRJ_01",
            result = output,
            checks = listOf(check)
        )

        val type = Types.newParameterizedType(ToolResultEnvelope::class.java, TestCableSizingOutput::class.java)
        val adapter = moshi.adapter<ToolResultEnvelope<TestCableSizingOutput>>(type)

        val json = adapter.toJson(envelope)
        assertNotNull(json)

        val restored = adapter.fromJson(json)
        assertNotNull(restored)
        assertEquals(ToolStatus.FAIL, restored!!.status)
        assertNotNull(restored.result)
        assertEquals("25 mm²", restored.result?.selectedSize_mm2)
        assertEquals(6.2, restored.result?.voltDropPct ?: 0.0, 0.001)
        assertEquals(1, restored.checks.size)
        assertEquals("CHECK_VOLT_DROP", restored.checks[0].checkId)
        assertTrue(restored.errors.isEmpty())
    }

    // =========================================================================
    // 5. Generic Serialization, Assumptions, and Provenance Tests
    // =========================================================================

    @Test
    fun toolRequestEnvelope_roundTripsWithTypedParameters() {
        val params = TestCableSizingParams(
            loadKVA = 45.0,
            voltageV = 400,
            length_m = 35.0,
            cableType = "Cu/XLPE/PVC - 4C"
        )
        val request = ToolRequestEnvelope(
            contractVersion = 1,
            requestId = "REQ-001",
            conversationId = "CONV-123",
            projectId = "PRJ_GATE3",
            projectRevision = 1L,
            entityType = "CIRCUIT",
            entityId = "101",
            toolName = "CalculateCableSizing",
            executionMode = ExecutionMode.LIVE_CALCULATION,
            parameters = params,
            requestedAt = 1770000000000L,
            requestedBy = "AI_ORCHESTRATOR"
        )

        val type = Types.newParameterizedType(ToolRequestEnvelope::class.java, TestCableSizingParams::class.java)
        val adapter = moshi.adapter<ToolRequestEnvelope<TestCableSizingParams>>(type)

        val json = adapter.toJson(request)
        assertNotNull(json)
        assertTrue(json.contains("\"requestId\":\"REQ-001\""))
        assertTrue(json.contains("\"toolName\":\"CalculateCableSizing\""))
        assertTrue(json.contains("\"loadKVA\":45.0"))

        val deserialized = adapter.fromJson(json)
        assertNotNull(deserialized)
        assertEquals(request.contractVersion, deserialized!!.contractVersion)
        assertEquals(request.requestId, deserialized.requestId)
        assertEquals(request.projectId, deserialized.projectId)
        assertEquals(request.projectRevision, deserialized.projectRevision)
        assertEquals(request.entityType, deserialized.entityType)
        assertEquals(request.entityId, deserialized.entityId)
        assertEquals(request.toolName, deserialized.toolName)
        assertEquals(request.executionMode, deserialized.executionMode)
        assertEquals(request.parameters.loadKVA, deserialized.parameters.loadKVA, 0.001)
        assertEquals(request.parameters.cableType, deserialized.parameters.cableType)
    }

    @Test
    fun toolResultEnvelope_roundTripsWithoutLosingChecksAssumptionsOrProvenance() {
        val output = TestCableSizingOutput(
            selectedSize_mm2 = "50 mm²",
            deviceSize_A = 100.0,
            voltDropPct = 1.85
        )
        val check1 = EngineeringCheck(
            checkId = "CHECK_VOLT_DROP",
            checkName = "Permissible Voltage Drop",
            status = ToolStatus.PASS,
            actualValue = EngineeringValue.ofDecimal(1.85, EngineeringUnit.PERCENT, "1.85%"),
            requiredLimit = EngineeringValue.ofDecimal(3.0, EngineeringUnit.PERCENT, "3.0%"),
            comparisonCriterion = "<=",
            standardReference = "IEC 60364-5-52",
            message = "Voltage drop 1.85% is within allowable 3.0% limit."
        )
        val check2 = EngineeringCheck(
            checkId = "CHECK_AMPACITY",
            checkName = "Current-Carrying Capacity (Iz >= In)",
            status = ToolStatus.PASS,
            actualValue = EngineeringValue.ofDecimal(128.0, EngineeringUnit.AMPERE, "128.0 A"),
            requiredLimit = EngineeringValue.ofDecimal(100.0, EngineeringUnit.AMPERE, "100.0 A"),
            comparisonCriterion = ">=",
            standardReference = "IEC 60364-5-52 Table B.52.1",
            message = "Derated ampacity Iz (128.0 A) exceeds breaker rating In (100.0 A)."
        )
        val assumption = EngineeringAssumption(
            assumptionId = "ASSUMP_AMBIENT_TEMP",
            parameterName = "Ambient Temperature",
            assumedValue = EngineeringValue.ofInt(40, EngineeringUnit.CELSIUS, "40°C"),
            sourceType = AssumptionSourceType.METHODOLOGY_DEFAULT,
            description = "Standard regional ambient temperature applied for air duct derating.",
            isUserOverridden = false,
            isSafetyCritical = false
        )
        val provenance = ProvenanceMetadata(
            contractVersion = 1,
            engineName = "CableSizingEngine",
            engineVersion = "1.0.0",
            methodologyReference = "IEC 60364-5-52",
            calculatedAt = 1770000000500L,
            projectId = "PRJ_GATE3",
            projectRevision = 2L,
            inputSignature = 12345678,
            dependencySignature = 87654321,
            sourceKind = "DETERMINISTIC_ENGINE"
        )

        val resultEnvelope = ToolResultEnvelope.pass(
            requestId = "REQ-002",
            toolName = "CalculateCableSizing",
            projectId = "PRJ_GATE3",
            result = output,
            projectRevision = 2L,
            checks = listOf(check1, check2),
            assumptions = listOf(assumption),
            provenance = provenance,
            startedAt = 1770000000450L
        )

        val type = Types.newParameterizedType(ToolResultEnvelope::class.java, TestCableSizingOutput::class.java)
        val adapter = moshi.adapter<ToolResultEnvelope<TestCableSizingOutput>>(type)

        val json = adapter.toJson(resultEnvelope)
        assertNotNull(json)

        val restored = adapter.fromJson(json)
        assertNotNull(restored)
        assertEquals(ToolStatus.PASS, restored!!.status)
        assertEquals(output.selectedSize_mm2, restored.result?.selectedSize_mm2)
        assertEquals(2, restored.checks.size)
        assertEquals("CHECK_VOLT_DROP", restored.checks[0].checkId)
        assertEquals(1.85, restored.checks[0].actualValue?.decimalValue ?: 0.0, 0.001)
        assertEquals(1, restored.assumptions.size)
        assertEquals("ASSUMP_AMBIENT_TEMP", restored.assumptions[0].assumptionId)
        assertEquals(40L, restored.assumptions[0].assumedValue.intValue)
        assertNotNull(restored.provenance)
        assertEquals("CableSizingEngine", restored.provenance?.engineName)
        assertEquals("IEC 60364-5-52", restored.provenance?.methodologyReference)
    }

    @Test
    fun engineeringAssumption_usesTypedValueNotAny() {
        val zeAssumption = EngineeringAssumption(
            assumptionId = "ASSUMP_ZE",
            parameterName = "Source Earth Loop Impedance (Ze)",
            assumedValue = EngineeringValue.ofDecimal(0.01, EngineeringUnit.OHM, "0.01 Ω"),
            sourceType = AssumptionSourceType.METHODOLOGY_DEFAULT,
            description = "Conservative default earth impedance per VoltAtlas distribution standard",
            isUserOverridden = false,
            isSafetyCritical = true
        )

        assertEquals("ASSUMP_ZE", zeAssumption.assumptionId)
        assertEquals(AssumptionSourceType.METHODOLOGY_DEFAULT, zeAssumption.sourceType)
        assertEquals(0.01, zeAssumption.assumedValue.decimalValue ?: 0.0, 0.0001)
        assertEquals(EngineeringUnit.OHM, zeAssumption.assumedValue.unit)
        assertTrue(zeAssumption.isSafetyCritical)
        assertFalse(zeAssumption.isUserOverridden)
    }

    @Test
    fun missingResult_isNullAndNeverFabricatedAsZero() {
        val missingEnvelope = ToolResultEnvelope.incomplete<TestCableSizingOutput>(
            requestId = "REQ-INCOMPLETE-01",
            toolName = "CalculateCableSizing",
            projectId = "PRJ_01",
            missingDataReason = "Cable length is 0 or negative; calculation aborted.",
            failingParameter = "length_m"
        )

        assertNull("Missing/incomplete calculation must have null result, never zero-filled fake output", missingEnvelope.result)
        assertEquals(ToolStatus.INCOMPLETE, missingEnvelope.status)
        assertEquals(1, missingEnvelope.errors.size)
    }

    @Test
    fun methodologyDefaultAndDerivedValuePreserveDifferentProvenance() {
        val methodDefault = EngineeringAssumption(
            assumptionId = "ASSUMP_DEFAULT_PF",
            parameterName = "Power Factor",
            assumedValue = EngineeringValue.ofDecimal(0.85, null, "0.85"),
            sourceType = AssumptionSourceType.METHODOLOGY_DEFAULT,
            description = "Standard design power factor when unspecified"
        )
        val derivedValue = EngineeringAssumption(
            assumptionId = "ASSUMP_TX_FLC",
            parameterName = "Transformer Full Load Current",
            assumedValue = EngineeringValue.ofDecimal(1154.70, EngineeringUnit.AMPERE, "1154.70 A"),
            sourceType = AssumptionSourceType.DERIVED,
            description = "Derived secondary FLC for 800 kVA @ 400 V incomer selectivity comparison"
        )

        assertEquals(AssumptionSourceType.METHODOLOGY_DEFAULT, methodDefault.sourceType)
        assertEquals(AssumptionSourceType.DERIVED, derivedValue.sourceType)
        assertNotEquals(methodDefault.sourceType, derivedValue.sourceType)
    }

    @Test
    fun contractVersion_survivesRoundTrip() {
        val request = ToolRequestEnvelope(
            contractVersion = 1,
            requestId = "REQ-VER-01",
            projectId = "PRJ_01",
            toolName = "CheckCoordination",
            parameters = "SimpleParam"
        )

        val type = Types.newParameterizedType(ToolRequestEnvelope::class.java, String::class.java)
        val adapter = moshi.adapter<ToolRequestEnvelope<String>>(type)
        val json = adapter.toJson(request)
        val restored = adapter.fromJson(json)

        assertNotNull(restored)
        assertEquals(1, restored!!.contractVersion)
    }

    @Test
    fun allOfficialToolStatuses_areRepresentable() {
        val expectedStatuses = setOf(
            ToolStatus.PASS,
            ToolStatus.FAIL,
            ToolStatus.WARNING,
            ToolStatus.INCOMPLETE,
            ToolStatus.INVALID_INPUT,
            ToolStatus.STALE,
            ToolStatus.RUNNING,
            ToolStatus.REVALIDATING,
            ToolStatus.NOT_SUPPORTED,
            ToolStatus.ERROR,
            ToolStatus.CANCELLED
        )

        assertEquals(11, expectedStatuses.size)
        for (status in expectedStatuses) {
            val name = status.name
            val parsed = ToolStatus.valueOf(name)
            assertEquals(status, parsed)
        }
    }
}
