package com.example.ai.proposal

import com.example.ai.contracts.EngineeringUnit
import com.example.ai.contracts.EngineeringValue
import com.example.ai.contracts.ExecutionMode
import com.example.ai.contracts.ExecutionModePolicy
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.text.Normalizer
import java.util.Locale

class ProposalPolicyAndDigestTest {

    private lateinit var moshi: Moshi
    private lateinit var digestEncoder: ProposalDigestEncoder
    private lateinit var policyValidator: ProposalPolicyValidator

    @Before
    fun setUp() {
        moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        digestEncoder = DefaultProposalDigestEncoder()
        policyValidator = DefaultProposalPolicyValidator(digestEncoder)
    }

    private fun sampleCableProposal(
        proposalId: String = "PROP-CABLE-01",
        projectId: String = "PRJ-TEST-1",
        baseRevision: Long = 10L,
        targetEntityId: String = "101",
        operation: ProposalOperation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
        patch: CableCircuitPatch = CableCircuitPatch(
            overrideCableSize = PatchValue.set("300 mm²"),
            parallelRuns = PatchValue.set(3),
            cableType = PatchValue.set("Cu/XLPE/PVC - 4C")
        ),
        rationale: String = "Upsize cable to reduce phase temperature rise.",
        createdAt: Long = 10_000L,
        expiresAt: Long? = 20_000L
    ): CableEngineeringProposal = EngineeringProposal(
        proposalId = proposalId,
        schemaVersion = PROPOSAL_SCHEMA_VERSION_V1,
        projectId = projectId,
        baseProjectRevision = baseRevision,
        targetEntityType = "CIRCUIT",
        targetEntityId = targetEntityId,
        operation = operation,
        patch = patch,
        rationale = rationale,
        sourceToolName = "calculate_cable_sizing",
        sourceRequestId = "REQ-CALC-999",
        engineeringEvidence = ProposalEvidenceDto(
            evidenceType = "THERMAL_DERATING",
            calculationReference = "IEC 60364-5-52 Table B.52.1",
            governingCriterion = "AMPACITY",
            values = mapOf(
                "designCurrentA" to EngineeringValue.ofDecimal(450.0, EngineeringUnit.AMPERE),
                "operatingTempC" to EngineeringValue.ofDecimal(72.5, EngineeringUnit.CELSIUS)
            )
        ),
        createdAt = createdAt,
        expiresAt = expiresAt
    )

    private fun sampleDeviceProposal(
        proposalId: String = "PROP-DEV-01",
        projectId: String = "PRJ-TEST-1",
        baseRevision: Long = 10L,
        targetEntityId: String = "101",
        patch: CableCircuitPatch = CableCircuitPatch(
            overrideDeviceSize = PatchValue.set(400.0),
            overrideDeviceType = PatchValue.set("MCCB-TM")
        ),
        createdAt: Long = 10_000L,
        expiresAt: Long? = 20_000L
    ): CableEngineeringProposal = EngineeringProposal(
        proposalId = proposalId,
        schemaVersion = PROPOSAL_SCHEMA_VERSION_V1,
        projectId = projectId,
        baseProjectRevision = baseRevision,
        targetEntityType = "CIRCUIT",
        targetEntityId = targetEntityId,
        operation = ProposalOperation.UPDATE_PROTECTIVE_DEVICE,
        patch = patch,
        rationale = "Adjust protective breaker rating to coordinate with feeder capacity.",
        sourceToolName = "calculate_protection_ads",
        sourceRequestId = "REQ-PROT-101",
        createdAt = createdAt,
        expiresAt = expiresAt
    )

    // =========================================================================
    // 1. Per-Field SET / CLEAR Policy Tests
    // =========================================================================

    @Test
    fun setOnlyFieldRejectsClear() {
        val setOnlyFields = listOf(
            CableCircuitPatch(cableType = PatchValue.clear()),
            CableCircuitPatch(manufacturer = PatchValue.clear()),
            CableCircuitPatch(installation = PatchValue.clear()),
            CableCircuitPatch(parallelRuns = PatchValue.clear()),
            CableCircuitPatch(lengthM = PatchValue.clear())
        )

        for (patch in setOnlyFields) {
            val proposal = sampleCableProposal(patch = patch)
            val result = policyValidator.validateCableProposal(proposal)
            assertTrue("SET_ONLY field must reject CLEAR action", result.isFailure)
            val errors = result.errorsOrEmpty()
            assertTrue(errors.any { it.errorCode == ProposalPolicyErrorCode.CLEAR_NOT_ALLOWED })
        }
    }

    @Test
    fun clearableOverrideCableSizeAcceptsClear() {
        val proposal = sampleCableProposal(
            patch = CableCircuitPatch(overrideCableSize = PatchValue.clear())
        )
        val result = policyValidator.validateCableProposal(proposal)
        assertTrue("overrideCableSize is SET_OR_CLEAR and must accept CLEAR action", result.isSuccess)
        val intent = result.getOrNull()
        assertNotNull(intent)
        val instr = intent!!.instructions.overrideCableSize
        assertNotNull(instr)
        assertEquals(PatchAction.CLEAR, instr!!.action)
        assertNull(instr.value)
    }

    @Test
    fun clearableDeviceSizeAcceptsClear() {
        val proposal = sampleDeviceProposal(
            patch = CableCircuitPatch(overrideDeviceSize = PatchValue.clear())
        )
        val result = policyValidator.validateCableProposal(proposal)
        assertTrue("overrideDeviceSize is SET_OR_CLEAR and must accept CLEAR action", result.isSuccess)
        val instr = result.getOrNull()!!.instructions.overrideDeviceSize
        assertNotNull(instr)
        assertEquals(PatchAction.CLEAR, instr!!.action)
        assertNull(instr.value)
    }

    @Test
    fun clearableDeviceTypeAcceptsClear() {
        val proposal = sampleDeviceProposal(
            patch = CableCircuitPatch(overrideDeviceType = PatchValue.clear())
        )
        val result = policyValidator.validateCableProposal(proposal)
        assertTrue("overrideDeviceType is SET_OR_CLEAR and must accept CLEAR action", result.isSuccess)
        val instr = result.getOrNull()!!.instructions.overrideDeviceType
        assertNotNull(instr)
        assertEquals(PatchAction.CLEAR, instr!!.action)
        assertNull(instr.value)
    }

    @Test
    fun setRequiresNonNullValue() {
        val patch = CableCircuitPatch(overrideCableSize = PatchValue(PatchAction.SET, null))
        val proposal = sampleCableProposal(patch = patch)
        val result = policyValidator.validateCableProposal(proposal)
        assertTrue(result.isFailure)
        assertTrue(result.errorsOrEmpty().any { it.errorCode == ProposalPolicyErrorCode.SET_REQUIRES_VALUE })
    }

    @Test
    fun clearRequiresNullValue() {
        val patch = CableCircuitPatch(overrideCableSize = PatchValue(PatchAction.CLEAR, "300 mm²"))
        val proposal = sampleCableProposal(patch = patch)
        val result = policyValidator.validateCableProposal(proposal)
        assertTrue(result.isFailure)
        assertTrue(result.errorsOrEmpty().any { it.errorCode == ProposalPolicyErrorCode.CLEAR_REQUIRES_NULL })
    }

    @Test
    fun absentFieldProducesNoInstruction() {
        val proposal = sampleCableProposal(
            patch = CableCircuitPatch(
                overrideCableSize = PatchValue.set("185 mm²")
            )
        )
        val result = policyValidator.validateCableProposal(proposal)
        assertTrue(result.isSuccess)
        val instructions = result.getOrNull()!!.instructions
        assertNotNull(instructions.overrideCableSize)
        assertNull("cableType was absent, instruction must be null", instructions.cableType)
        assertNull("manufacturer was absent, instruction must be null", instructions.manufacturer)
        assertNull("parallelRuns was absent, instruction must be null", instructions.parallelRuns)
        assertEquals(1, instructions.activeInstructions().size)
    }

    // =========================================================================
    // 2. Operation-to-Field Compatibility Tests
    // =========================================================================

    @Test
    fun updateCableAttributesRejectsDeviceFields() {
        val proposal = sampleCableProposal(
            operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
            patch = CableCircuitPatch(overrideDeviceSize = PatchValue.set(250.0))
        )
        val result = policyValidator.validateCableProposal(proposal)
        assertTrue(result.isFailure)
        assertTrue(result.errorsOrEmpty().any {
            it.errorCode == ProposalPolicyErrorCode.FIELD_NOT_ALLOWED_FOR_OPERATION && it.field == "overrideDeviceSize"
        })
    }

    @Test
    fun updateProtectiveDeviceRejectsCableFields() {
        val proposal = sampleDeviceProposal(
            patch = CableCircuitPatch(overrideCableSize = PatchValue.set("240 mm²"))
        )
        val result = policyValidator.validateCableProposal(proposal)
        assertTrue(result.isFailure)
        assertTrue(result.errorsOrEmpty().any {
            it.errorCode == ProposalPolicyErrorCode.FIELD_NOT_ALLOWED_FOR_OPERATION && it.field == "overrideCableSize"
        })
    }

    @Test
    fun mixedOperationPatchIsRejected() {
        val proposal = sampleCableProposal(
            operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
            patch = CableCircuitPatch(
                overrideCableSize = PatchValue.set("240 mm²"),
                overrideDeviceSize = PatchValue.set(400.0)
            )
        )
        val result = policyValidator.validateCableProposal(proposal)
        assertTrue(result.isFailure)
        assertTrue(result.errorsOrEmpty().any { it.errorCode == ProposalPolicyErrorCode.MIXED_OPERATION_FIELDS })
    }

    @Test
    fun emptyPatchIsRejected() {
        val proposal = sampleCableProposal(patch = CableCircuitPatch())
        val result = policyValidator.validateCableProposal(proposal)
        assertTrue(result.isFailure)
        assertTrue(result.errorsOrEmpty().any { it.errorCode == ProposalPolicyErrorCode.EMPTY_PATCH })
    }

    // =========================================================================
    // 3. Field Bounds & Expiration Validation Tests
    // =========================================================================

    @Test
    fun invalidParallelRunsIsRejected() {
        val proposalZero = sampleCableProposal(patch = CableCircuitPatch(parallelRuns = PatchValue.set(0)))
        val resZero = policyValidator.validateCableProposal(proposalZero)
        assertTrue(resZero.isFailure)
        assertTrue(resZero.errorsOrEmpty().any { it.field == "parallelRuns" && it.errorCode == ProposalPolicyErrorCode.INVALID_FIELD_VALUE })

        val proposalNeg = sampleCableProposal(patch = CableCircuitPatch(parallelRuns = PatchValue.set(-3)))
        val resNeg = policyValidator.validateCableProposal(proposalNeg)
        assertTrue(resNeg.isFailure)
        assertTrue(resNeg.errorsOrEmpty().any { it.field == "parallelRuns" })
    }

    @Test
    fun invalidLengthIsRejected() {
        val proposalZero = sampleCableProposal(patch = CableCircuitPatch(lengthM = PatchValue.set(0.0)))
        val resZero = policyValidator.validateCableProposal(proposalZero)
        assertTrue(resZero.isFailure)
        assertTrue(resZero.errorsOrEmpty().any { it.field == "lengthM" && it.errorCode == ProposalPolicyErrorCode.INVALID_FIELD_VALUE })
    }

    @Test
    fun invalidDeviceSizeIsRejected() {
        val proposalZero = sampleDeviceProposal(patch = CableCircuitPatch(overrideDeviceSize = PatchValue.set(0.0)))
        val resZero = policyValidator.validateCableProposal(proposalZero)
        assertTrue(resZero.isFailure)
        assertTrue(resZero.errorsOrEmpty().any { it.field == "overrideDeviceSize" && it.errorCode == ProposalPolicyErrorCode.INVALID_FIELD_VALUE })
    }

    @Test
    fun blankStringSetIsRejected() {
        val proposal = sampleCableProposal(patch = CableCircuitPatch(cableType = PatchValue.set("   ")))
        val result = policyValidator.validateCableProposal(proposal)
        assertTrue(result.isFailure)
        assertTrue(result.errorsOrEmpty().any { it.field == "cableType" && it.errorCode == ProposalPolicyErrorCode.INVALID_FIELD_VALUE })
    }

    @Test
    fun expiredProposalIsRejectedUsingInjectedClock() {
        val proposal = sampleCableProposal(createdAt = 1000L, expiresAt = 5000L)

        // Valid when evaluated before expiry
        val validResult = policyValidator.validateCableProposal(proposal, currentTimeMillis = 4000L)
        assertTrue(validResult.isSuccess)

        // Rejected when evaluated after expiry
        val expiredResult = policyValidator.validateCableProposal(proposal, currentTimeMillis = 6000L)
        assertTrue(expiredResult.isFailure)
        assertTrue(expiredResult.errorsOrEmpty().any { it.errorCode == ProposalPolicyErrorCode.PROPOSAL_EXPIRED })
    }

    // =========================================================================
    // 4. Validated Intent Contract Invariants
    // =========================================================================

    @Test
    fun validatedIntentDoesNotClaimApproval() {
        val proposal = sampleCableProposal()
        val result = policyValidator.validateCableProposal(proposal)
        assertTrue(result.isSuccess)
        val intent = result.getOrNull()!!

        val declaredFieldNames = ValidatedProposalIntent::class.java.declaredFields.map { it.name }
        assertFalse(declaredFieldNames.contains("isApproved"))
        assertFalse(declaredFieldNames.contains("approvalGrant"))
        assertFalse(declaredFieldNames.contains("approvalId"))
        assertFalse(declaredFieldNames.contains("isSingleUseConsumed"))
    }

    @Test
    fun validatedIntentDoesNotClaimCurrentRevision() {
        val proposal = sampleCableProposal(baseRevision = 10L)
        val result = policyValidator.validateCableProposal(proposal)
        assertTrue(result.isSuccess)
        val intent = result.getOrNull()!!

        assertEquals(10L, intent.baseProjectRevision)
        val declaredFieldNames = ValidatedProposalIntent::class.java.declaredFields.map { it.name }
        assertFalse(declaredFieldNames.contains("currentRevision"))
        assertFalse(declaredFieldNames.contains("isRevisionCurrent"))
    }

    @Test
    fun validatedIntentHasNoRepositoryOrDaoDependency() {
        val classesToCheck = listOf(
            ValidatedProposalIntent::class.java,
            CablePatchInstructions::class.java,
            FieldInstruction::class.java,
            ProposalPolicyResult::class.java
        )

        for (cls in classesToCheck) {
            for (method in cls.declaredMethods) {
                val returnType = method.returnType.name
                assertFalse(returnType.contains("RoomDatabase"))
                assertFalse(returnType.contains("Dao"))
                assertFalse(returnType.contains("EngineeringRepository"))
            }
        }
    }

    @Test
    fun mutationExecutionRemainsDisabled() {
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }

    // =========================================================================
    // 5. Canonical Digest Hardening & Invariant Tests
    // =========================================================================

    @Test
    fun sameSemanticProposalProducesSameDigest() {
        val p1 = sampleCableProposal()
        val p2 = sampleCableProposal()
        assertEquals(digestEncoder.computeDigest(p1), digestEncoder.computeDigest(p2))
    }

    @Test
    fun absentAndClearProduceDifferentDigests() {
        val pAbsent = sampleCableProposal(
            patch = CableCircuitPatch(
                overrideCableSize = null,
                parallelRuns = PatchValue.set(2)
            )
        )
        val pClear = sampleCableProposal(
            patch = CableCircuitPatch(
                overrideCableSize = PatchValue.clear(),
                parallelRuns = PatchValue.set(2)
            )
        )
        assertNotEquals(digestEncoder.computeDigest(pAbsent), digestEncoder.computeDigest(pClear))
    }

    @Test
    fun clearAndSetProduceDifferentDigests() {
        val pClear = sampleCableProposal(
            patch = CableCircuitPatch(
                overrideCableSize = PatchValue.clear(),
                parallelRuns = PatchValue.set(2)
            )
        )
        val pSet = sampleCableProposal(
            patch = CableCircuitPatch(
                overrideCableSize = PatchValue.set("300 mm²"),
                parallelRuns = PatchValue.set(2)
            )
        )
        assertNotEquals(digestEncoder.computeDigest(pClear), digestEncoder.computeDigest(pSet))
    }

    @Test
    fun patchActionChangeChangesDigest() {
        val p1 = sampleDeviceProposal(patch = CableCircuitPatch(overrideDeviceSize = PatchValue.set(400.0)))
        val p2 = sampleDeviceProposal(patch = CableCircuitPatch(overrideDeviceSize = PatchValue.clear()))
        assertNotEquals(digestEncoder.computeDigest(p1), digestEncoder.computeDigest(p2))
    }

    @Test
    fun operationChangeChangesDigest() {
        val p1 = sampleCableProposal(operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES)
        val p2 = sampleCableProposal(operation = ProposalOperation.UPDATE_PROTECTIVE_DEVICE)
        assertNotEquals(digestEncoder.computeDigest(p1), digestEncoder.computeDigest(p2))
    }

    @Test
    fun baseRevisionChangeChangesDigest() {
        val p1 = sampleCableProposal(baseRevision = 10L)
        val p2 = sampleCableProposal(baseRevision = 11L)
        assertNotEquals(digestEncoder.computeDigest(p1), digestEncoder.computeDigest(p2))
    }

    @Test
    fun targetChangeChangesDigest() {
        val p1 = sampleCableProposal(targetEntityId = "101")
        val p2 = sampleCableProposal(targetEntityId = "102")
        assertNotEquals(digestEncoder.computeDigest(p1), digestEncoder.computeDigest(p2))
    }

    @Test
    fun rationaleChangeChangesDigest() {
        val p1 = sampleCableProposal(rationale = "Rationale A")
        val p2 = sampleCableProposal(rationale = "Rationale B")
        assertNotEquals("Digest-bound rationale ensures tampering alters the digest",
            digestEncoder.computeDigest(p1), digestEncoder.computeDigest(p2))
    }

    @Test
    fun unicodeNfcProducesStableDigest() {
        val nfc = Normalizer.normalize("Câble spécial", Normalizer.Form.NFC)
        val nfd = Normalizer.normalize("Câble spécial", Normalizer.Form.NFD)

        val p1 = sampleCableProposal(rationale = nfc)
        val p2 = sampleCableProposal(rationale = nfd)

        assertEquals(digestEncoder.computeDigest(p1), digestEncoder.computeDigest(p2))
    }

    @Test
    fun localeDoesNotChangeDigest() {
        val defaultLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMAN)
            val dGerman = digestEncoder.computeDigest(sampleCableProposal())

            Locale.setDefault(Locale.US)
            val dUs = digestEncoder.computeDigest(sampleCableProposal())

            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val dTurkish = digestEncoder.computeDigest(sampleCableProposal())

            assertEquals(dGerman, dUs)
            assertEquals(dUs, dTurkish)
        } finally {
            Locale.setDefault(defaultLocale)
        }
    }

    @Test
    fun evidenceMapOrderDoesNotChangeDigest() {
        val ev1 = ProposalEvidenceDto(
            evidenceType = "EVAL",
            values = sortedMapOf(
                "A" to EngineeringValue.ofDecimal(10.0, EngineeringUnit.AMPERE),
                "B" to EngineeringValue.ofDecimal(20.0, EngineeringUnit.VOLT)
            )
        )
        val ev2 = ProposalEvidenceDto(
            evidenceType = "EVAL",
            values = linkedMapOf(
                "B" to EngineeringValue.ofDecimal(20.0, EngineeringUnit.VOLT),
                "A" to EngineeringValue.ofDecimal(10.0, EngineeringUnit.AMPERE)
            )
        )

        val p1 = sampleCableProposal().copy(engineeringEvidence = ev1)
        val p2 = sampleCableProposal().copy(engineeringEvidence = ev2)

        assertEquals(digestEncoder.computeDigest(p1), digestEncoder.computeDigest(p2))
    }

    @Test
    fun jsonFieldOrderDoesNotChangeDigest() {
        val type = Types.newParameterizedType(EngineeringProposal::class.java, CableCircuitPatch::class.java)
        val adapter = moshi.adapter<CableEngineeringProposal>(type)

        val original = sampleCableProposal()
        val json = adapter.toJson(original)
        val deserialized = adapter.fromJson(json)!!

        assertEquals(digestEncoder.computeDigest(original), digestEncoder.computeDigest(deserialized))
    }

    @Test
    fun runtimeHashCodeIsNotUsed() {
        val p1 = sampleCableProposal()
        val p2 = sampleCableProposal()
        assertNotSame(p1, p2)
        assertEquals(digestEncoder.computeDigest(p1), digestEncoder.computeDigest(p2))
    }

    @Test
    fun encodingFailureReturnsDigestGenerationFailed() {
        val brokenEncoder = object : ProposalDigestEncoder {
            override fun canonicalBytes(proposal: EngineeringProposal<*>): ByteArray = throw RuntimeException("Simulated framing crash")
            override fun computeDigest(proposal: EngineeringProposal<*>): String = throw RuntimeException("Simulated digest crash")
        }
        val validator = DefaultProposalPolicyValidator(brokenEncoder)
        val proposal = sampleCableProposal()

        val result = validator.validateCableProposal(proposal)
        assertTrue(result.isFailure)
        assertTrue(result.errorsOrEmpty().any { it.errorCode == ProposalPolicyErrorCode.DIGEST_GENERATION_FAILED })
    }

    @Test
    fun encodingFailureProducesNoValidatedIntent() {
        val brokenEncoder = object : ProposalDigestEncoder {
            override fun canonicalBytes(proposal: EngineeringProposal<*>): ByteArray = throw RuntimeException("Fail")
            override fun computeDigest(proposal: EngineeringProposal<*>): String = throw RuntimeException("Fail")
        }
        val validator = DefaultProposalPolicyValidator(brokenEncoder)
        val result = validator.validateCableProposal(sampleCableProposal())

        assertNull("Failed validation must produce null validated intent", result.getOrNull())
    }

    @Test
    fun validationErrorsAreDeterministic() {
        val invalidProposal = sampleCableProposal(
            patch = CableCircuitPatch(
                cableType = PatchValue.clear(),
                parallelRuns = PatchValue.set(-5)
            )
        )

        val r1 = policyValidator.validateCableProposal(invalidProposal)
        val r2 = policyValidator.validateCableProposal(invalidProposal)

        assertEquals(r1.errorsOrEmpty(), r2.errorsOrEmpty())
    }

    @Test
    fun validationErrorsAreSanitized() {
        val invalidProposal = sampleCableProposal(
            patch = CableCircuitPatch(
                parallelRuns = PatchValue.set(-5)
            )
        )
        val result = policyValidator.validateCableProposal(invalidProposal)
        val errors = result.errorsOrEmpty()
        for (err in errors) {
            assertFalse("Error message must not contain raw stack trace", err.message.contains("Exception at "))
            assertFalse("Error message must not contain database queries", err.message.contains("SELECT "))
            assertFalse("Error message must not contain file paths", err.message.contains("E:\\"))
        }
    }
}
