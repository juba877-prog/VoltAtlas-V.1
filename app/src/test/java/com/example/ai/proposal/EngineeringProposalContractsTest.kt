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

class EngineeringProposalContractsTest {

    private lateinit var moshi: Moshi
    private lateinit var digestEncoder: ProposalDigestEncoder
    private lateinit var validator: ProposalValidator

    @Before
    fun setUp() {
        moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        digestEncoder = DefaultProposalDigestEncoder()
        validator = DefaultProposalValidator()
    }

    private fun sampleValidProposal(
        proposalId: String = "PROP-001",
        projectId: String = "PRJ-TEST-1",
        baseRevision: Long = 5L,
        targetEntityId: String = "101",
        operation: ProposalOperation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
        patch: CableCircuitPatch = CableCircuitPatch(
            overrideCableSize = PatchValue.set("240 mm²"),
            parallelRuns = PatchValue.set(2),
            cableType = PatchValue.set("Cu/XLPE/PVC - 4C")
        ),
        rationale: String = "Upsize cable to reduce voltage drop below 3.0%.",
        createdAt: Long = 1000L,
        expiresAt: Long? = 5000L
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
        sourceRequestId = "REQ-CALC-888",
        engineeringEvidence = ProposalEvidenceDto(
            evidenceType = "VOLTAGE_DROP_OPTIMIZATION",
            calculationReference = "IEC 60364-5-52",
            governingCriterion = "VOLTAGE_DROP",
            values = mapOf(
                "voltageDropPct" to EngineeringValue.ofDecimal(2.45, EngineeringUnit.PERCENT),
                "designCurrentA" to EngineeringValue.ofDecimal(320.0, EngineeringUnit.AMPERE)
            )
        ),
        createdAt = createdAt,
        expiresAt = expiresAt
    )

    // =========================================================================
    // 1. Serialization & Round-Trip Tests
    // =========================================================================

    @Test
    fun proposal_roundTripsWithoutInformationLoss() {
        val proposal = sampleValidProposal()
        val type = Types.newParameterizedType(EngineeringProposal::class.java, CableCircuitPatch::class.java)
        val adapter = moshi.adapter<CableEngineeringProposal>(type)

        val json = adapter.toJson(proposal)
        assertNotNull(json)
        assertTrue(json.contains("\"PROPOSAL-V1\""))
        assertTrue(json.contains("\"overrideCableSize\""))

        val deserialized = adapter.fromJson(json)
        assertNotNull(deserialized)
        assertEquals(proposal, deserialized)
        assertEquals("240 mm²", deserialized!!.patch.overrideCableSize?.value)
        assertEquals(PatchAction.SET, deserialized.patch.overrideCableSize?.action)
    }

    @Test
    fun approval_roundTripsWithoutInformationLoss() {
        val approval = ProposalApprovalGrant(
            approvalId = "APPR-999",
            proposalId = "PROP-001",
            proposalDigest = "PROPOSAL-V1:a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90",
            baseProjectRevision = 5L,
            approvedBy = "LeadEngineer@example.com",
            approvedAt = 2000L,
            expiresAt = 6000L
        )
        val adapter = moshi.adapter(ProposalApprovalGrant::class.java)

        val json = adapter.toJson(approval)
        assertNotNull(json)
        assertTrue(json.contains("LeadEngineer@example.com"))

        val deserialized = adapter.fromJson(json)
        assertNotNull(deserialized)
        assertEquals(approval, deserialized)
    }

    // =========================================================================
    // 2. Allowlist & Patch Structure Invariants
    // =========================================================================

    @Test
    fun patchDistinguishesAbsentFromExplicitClear() {
        val absentPatch = CableCircuitPatch(overrideCableSize = null)
        val clearPatch = CableCircuitPatch(overrideCableSize = PatchValue.clear())
        val setPatch = CableCircuitPatch(overrideCableSize = PatchValue.set("185 mm²"))

        assertNull("Absent field must have null wrapper", absentPatch.overrideCableSize)
        assertNotNull("Clear field must have non-null wrapper", clearPatch.overrideCableSize)
        assertEquals(PatchAction.CLEAR, clearPatch.overrideCableSize?.action)
        assertNull("Clear field must have null value", clearPatch.overrideCableSize?.value)

        assertNotNull("Set field must have non-null wrapper", setPatch.overrideCableSize)
        assertEquals(PatchAction.SET, setPatch.overrideCableSize?.action)
        assertEquals("185 mm²", setPatch.overrideCableSize?.value)

        // Digest encoding must differ between absent and explicit CLEAR
        val propAbsent = sampleValidProposal(patch = absentPatch.copy(parallelRuns = PatchValue.set(1)))
        val propClear = sampleValidProposal(patch = clearPatch.copy(parallelRuns = PatchValue.set(1)))
        val propSet = sampleValidProposal(patch = setPatch.copy(parallelRuns = PatchValue.set(1)))

        val digestAbsent = digestEncoder.computeDigest(propAbsent)
        val digestClear = digestEncoder.computeDigest(propClear)
        val digestSet = digestEncoder.computeDigest(propSet)

        assertNotEquals("Absent and Clear must generate different digests", digestAbsent, digestClear)
        assertNotEquals("Clear and Set must generate different digests", digestClear, digestSet)
        assertNotEquals("Absent and Set must generate different digests", digestAbsent, digestSet)
    }

    @Test
    fun patchDoesNotExposeIdentityOrTopologyFields() {
        val declaredFieldNames = CableCircuitPatch::class.java.declaredFields.map { it.name }

        // Allowed fields
        assertTrue(declaredFieldNames.contains("overrideCableSize"))
        assertTrue(declaredFieldNames.contains("cableType"))
        assertTrue(declaredFieldNames.contains("manufacturer"))
        assertTrue(declaredFieldNames.contains("installation"))
        assertTrue(declaredFieldNames.contains("parallelRuns"))
        assertTrue(declaredFieldNames.contains("overrideDeviceSize"))
        assertTrue(declaredFieldNames.contains("overrideDeviceType"))
        assertTrue(declaredFieldNames.contains("lengthM"))

        // Forbidden identity/topology fields MUST NOT be present in patch
        assertFalse(declaredFieldNames.contains("projectId"))
        assertFalse(declaredFieldNames.contains("circuitId"))
        assertFalse(declaredFieldNames.contains("id"))
        assertFalse(declaredFieldNames.contains("parentId"))
        assertFalse(declaredFieldNames.contains("fedFrom"))
        assertFalse(declaredFieldNames.contains("transformerId"))
        assertFalse(declaredFieldNames.contains("revision"))
        assertFalse(declaredFieldNames.contains("baseProjectRevision"))
        assertFalse(declaredFieldNames.contains("loadKVA"))
        assertFalse(declaredFieldNames.contains("pf"))
        assertFalse(declaredFieldNames.contains("voltageV"))
    }

    // =========================================================================
    // 3. Validation Rules Tests
    // =========================================================================

    @Test
    fun proposalRequiresSchemaVersion() {
        val proposal = sampleValidProposal().copy(schemaVersion = "PROPOSAL-V2-UNKNOWN")
        val result = validator.validate(proposal)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.errorCode == ProposalErrorCode.INVALID_PROPOSAL && it.field == "schemaVersion" })
    }

    @Test
    fun proposalRequiresBaseRevision() {
        val proposalZero = sampleValidProposal(baseRevision = 0L)
        val resultZero = validator.validate(proposalZero)
        assertFalse(resultZero.isValid)
        assertTrue(resultZero.errors.any { it.errorCode == ProposalErrorCode.INVALID_FIELD_VALUE && it.field == "baseProjectRevision" })

        val proposalNegative = sampleValidProposal(baseRevision = -5L)
        val resultNegative = validator.validate(proposalNegative)
        assertFalse(resultNegative.isValid)
        assertTrue(resultNegative.errors.any { it.errorCode == ProposalErrorCode.INVALID_FIELD_VALUE && it.field == "baseProjectRevision" })
    }

    @Test
    fun proposalRequiresAtLeastOnePatchField() {
        val emptyPatch = CableCircuitPatch()
        val proposal = sampleValidProposal(patch = emptyPatch)
        val result = validator.validate(proposal)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.errorCode == ProposalErrorCode.INVALID_PATCH && it.field == "patch" })
    }

    @Test
    fun invalidParallelRunsIsRejected() {
        val proposalZero = sampleValidProposal(patch = CableCircuitPatch(parallelRuns = PatchValue.set(0)))
        val resZero = validator.validate(proposalZero)
        assertFalse(resZero.isValid)
        assertTrue(resZero.errors.any { it.field == "parallelRuns" })

        val proposalNeg = sampleValidProposal(patch = CableCircuitPatch(parallelRuns = PatchValue.set(-2)))
        val resNeg = validator.validate(proposalNeg)
        assertFalse(resNeg.isValid)
        assertTrue(resNeg.errors.any { it.field == "parallelRuns" })
    }

    @Test
    fun invalidLengthIsRejected() {
        val proposalZero = sampleValidProposal(patch = CableCircuitPatch(lengthM = PatchValue.set(0.0)))
        val resZero = validator.validate(proposalZero)
        assertFalse(resZero.isValid)
        assertTrue(resZero.errors.any { it.field == "lengthM" })

        val proposalNeg = sampleValidProposal(patch = CableCircuitPatch(lengthM = PatchValue.set(-15.0)))
        val resNeg = validator.validate(proposalNeg)
        assertFalse(resNeg.isValid)
        assertTrue(resNeg.errors.any { it.field == "lengthM" })
    }

    @Test
    fun invalidDeviceSizeIsRejected() {
        val proposalZero = sampleValidProposal(patch = CableCircuitPatch(overrideDeviceSize = PatchValue.set(0.0)))
        val resZero = validator.validate(proposalZero)
        assertFalse(resZero.isValid)
        assertTrue(resZero.errors.any { it.field == "overrideDeviceSize" })
    }

    @Test
    fun expiryBeforeCreationIsRejected() {
        val proposal = sampleValidProposal(createdAt = 5000L, expiresAt = 4000L)
        val result = validator.validate(proposal)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.errorCode == ProposalErrorCode.INVALID_FIELD_VALUE && it.field == "expiresAt" })

        val proposalEqual = sampleValidProposal(createdAt = 5000L, expiresAt = 5000L)
        val resultEqual = validator.validate(proposalEqual)
        assertFalse(resultEqual.isValid)
        assertTrue(resultEqual.errors.any { it.errorCode == ProposalErrorCode.INVALID_FIELD_VALUE && it.field == "expiresAt" })
    }

    @Test
    fun missingRequiredFieldsAreRejected() {
        val noProj = sampleValidProposal(projectId = "  ")
        assertFalse(validator.validate(noProj).isValid)

        val noEntity = sampleValidProposal(targetEntityId = "")
        assertFalse(validator.validate(noEntity).isValid)

        val noProposalId = sampleValidProposal(proposalId = "")
        assertFalse(validator.validate(noProposalId).isValid)
    }

    // =========================================================================
    // 4. Canonical Digest & Invariant Tests
    // =========================================================================

    @Test
    fun digestMatchesPROPOSAL_V1Format() {
        val proposal = sampleValidProposal()
        val digest = digestEncoder.computeDigest(proposal)
        assertTrue("Digest must match PROPOSAL-V1:<64 hex chars>, got: $digest",
            digest.matches(Regex("""^PROPOSAL-V1:[0-9a-f]{64}$""")))
    }

    @Test
    fun sameSemanticProposalProducesSameDigest() {
        val p1 = sampleValidProposal()
        val p2 = sampleValidProposal()

        val d1 = digestEncoder.computeDigest(p1)
        val d2 = digestEncoder.computeDigest(p2)
        assertEquals("Identical proposals must produce identical digests", d1, d2)
    }

    @Test
    fun patchFieldChangeChangesDigest() {
        val p1 = sampleValidProposal(patch = CableCircuitPatch(parallelRuns = PatchValue.set(1)))
        val p2 = sampleValidProposal(patch = CableCircuitPatch(parallelRuns = PatchValue.set(2)))

        assertNotEquals(digestEncoder.computeDigest(p1), digestEncoder.computeDigest(p2))
    }

    @Test
    fun baseRevisionChangeChangesDigest() {
        val p1 = sampleValidProposal(baseRevision = 5L)
        val p2 = sampleValidProposal(baseRevision = 6L)

        assertNotEquals(digestEncoder.computeDigest(p1), digestEncoder.computeDigest(p2))
    }

    @Test
    fun targetEntityChangeChangesDigest() {
        val p1 = sampleValidProposal(targetEntityId = "101")
        val p2 = sampleValidProposal(targetEntityId = "102")

        assertNotEquals(digestEncoder.computeDigest(p1), digestEncoder.computeDigest(p2))
    }

    @Test
    fun operationChangeChangesDigest() {
        val p1 = sampleValidProposal(operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES)
        val p2 = sampleValidProposal(operation = ProposalOperation.UPDATE_PROTECTIVE_DEVICE)

        assertNotEquals(digestEncoder.computeDigest(p1), digestEncoder.computeDigest(p2))
    }

    @Test
    fun rationaleBindingPolicyIsEnforced() {
        val p1 = sampleValidProposal(rationale = "Initial design reason.")
        val p2 = sampleValidProposal(rationale = "Modified reason after review.")

        val d1 = digestEncoder.computeDigest(p1)
        val d2 = digestEncoder.computeDigest(p2)

        assertNotEquals("Changing rationale must alter the digest to prevent post-approval text tampering", d1, d2)
    }

    @Test
    fun unicodeNfcProducesStableDigest() {
        val nfcText = Normalizer.normalize("Câble d'alimentation électrique", Normalizer.Form.NFC)
        val nfdText = Normalizer.normalize("Câble d'alimentation électrique", Normalizer.Form.NFD)

        val pNfc = sampleValidProposal(rationale = nfcText)
        val pNfd = sampleValidProposal(rationale = nfdText)

        assertEquals("NFC and NFD strings must produce identical digests due to canonical normalization",
            digestEncoder.computeDigest(pNfc), digestEncoder.computeDigest(pNfd))
    }

    @Test
    fun localeDoesNotChangeDigest() {
        val defaultLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMAN)
            val dGerman = digestEncoder.computeDigest(sampleValidProposal())

            Locale.setDefault(Locale.US)
            val dUs = digestEncoder.computeDigest(sampleValidProposal())

            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val dTurkish = digestEncoder.computeDigest(sampleValidProposal())

            assertEquals(dGerman, dUs)
            assertEquals(dUs, dTurkish)
        } finally {
            Locale.setDefault(defaultLocale)
        }
    }

    @Test
    fun mapOrJsonFieldOrderDoesNotChangeDigest() {
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

        val p1 = sampleValidProposal().copy(engineeringEvidence = ev1)
        val p2 = sampleValidProposal().copy(engineeringEvidence = ev2)

        assertEquals(digestEncoder.computeDigest(p1), digestEncoder.computeDigest(p2))
    }

    // =========================================================================
    // 5. Approval & Non-Goal Boundary Tests
    // =========================================================================

    @Test
    fun approvalBindsProposalDigest() {
        val proposal = sampleValidProposal()
        val digest = digestEncoder.computeDigest(proposal)

        val grant = ProposalApprovalGrant(
            approvalId = "APPR-1",
            proposalId = proposal.proposalId,
            proposalDigest = digest,
            baseProjectRevision = proposal.baseProjectRevision,
            approvedBy = "Engineer A",
            approvedAt = 2000L
        )

        assertEquals(proposal.proposalId, grant.proposalId)
        assertEquals(digest, grant.proposalDigest)
        assertEquals(proposal.baseProjectRevision, grant.baseProjectRevision)
    }

    @Test
    fun approvalForDifferentProposalDoesNotMatch() {
        val p1 = sampleValidProposal(baseRevision = 5L)
        val p2 = sampleValidProposal(baseRevision = 6L)

        val digest1 = digestEncoder.computeDigest(p1)
        val digest2 = digestEncoder.computeDigest(p2)

        val grantForP1 = ProposalApprovalGrant(
            approvalId = "APPR-1",
            proposalId = p1.proposalId,
            proposalDigest = digest1,
            baseProjectRevision = p1.baseProjectRevision,
            approvedBy = "Engineer A",
            approvedAt = 2000L
        )

        assertFalse("Approval digest for P1 must not match digest of P2", grantForP1.proposalDigest == digest2)
    }

    @Test
    fun approvalDtoDoesNotContainAuthoritativeConsumedBoolean() {
        val declaredFieldNames = ProposalApprovalGrant::class.java.declaredFields.map { it.name }
        assertFalse("ProposalApprovalGrant must NOT contain mutable consumption state in 7A",
            declaredFieldNames.contains("isSingleUseConsumed"))
        assertFalse("ProposalApprovalGrant must NOT contain mutable isConsumed",
            declaredFieldNames.contains("isConsumed"))
    }

    // =========================================================================
    // 6. Regression & Protected Boundaries Tests
    // =========================================================================

    @Test
    fun mutationExecutionRemainsDisabled() {
        assertFalse("ExecutionMode.MUTATION must remain non-executable in Phase 7A",
            ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
        assertTrue("ExecutionMode.PROPOSAL remains executable for formulating proposals",
            ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.PROPOSAL))
    }

    @Test
    fun noRepositoryDaoRoomOrEngineDependencyInProposalContracts() {
        val proposalClass = EngineeringProposal::class.java
        val patchClass = CableCircuitPatch::class.java
        val approvalClass = ProposalApprovalGrant::class.java

        val allClasses = listOf(proposalClass, patchClass, approvalClass)
        for (cls in allClasses) {
            val packageName = cls.`package`?.name.orEmpty()
            assertTrue("Must reside in com.example.ai.proposal", packageName.startsWith("com.example.ai.proposal"))

            // Verify no methods return or accept Room / DAO / Engine types
            for (method in cls.declaredMethods) {
                val returnType = method.returnType.name
                assertFalse(returnType.contains("RoomDatabase"))
                assertFalse(returnType.contains("Dao"))
                assertFalse(returnType.contains("EngineeringRepository"))
                assertFalse(returnType.contains("CableSizingEngine"))
            }
        }
    }
}
