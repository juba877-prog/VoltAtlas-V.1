package com.example.ai.context

import com.example.ai.contracts.EngineeringUnit
import com.example.ai.contracts.EngineeringValue
import com.example.ai.contracts.ToolStatus
import com.example.ai.conversation.ConversationLifecycleState
import com.example.ai.conversation.ConversationTaskState
import com.example.ai.conversation.EntityBinding
import com.example.ai.conversation.SlotState
import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.context.freshness.contracts.CalculationDomain
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.runtime.ProvenanceLabel
import com.example.ai.tools.cable.*
import org.junit.Assert.*
import org.junit.Test

class ContextResolverTest {

    private val baseContext = BoundedProjectContext(
        projectId = "PROJ-001",
        projectName = "Industrial Plant Alpha",
        liveRevision = 5L,
        activeScreen = "CircuitDetails",
        activeEntityType = "CIRCUIT",
        activeEntityId = "5",
        activeEntityName = "Main Feeder 5",
        knownEntityInputs = mapOf(
            "voltage" to "400.0",
            "lengthM" to "120.0",
            "installation" to "Perforated Cable Tray"
        )
    )

    // 1. Same-project, same-entity, same-task follow-up
    @Test
    fun multiTurn_preservesEntityAndTaskContext_andAppliesTypedWhatIf() {
        val priorState = ConversationTaskState(
            taskId = "TASK-001",
            projectId = "PROJ-001",
            baseProjectRevision = 5L,
            activeEntityType = "CIRCUIT",
            activeEntityId = "5",
            activeEntityName = "Main Feeder 5",
            activeDomain = CalculationDomain.CABLE_SIZING
        )

        val resolved = ContextResolver.resolve(
            userUtterance = "طيب جرب 240",
            projectContext = baseContext,
            priorState = priorState,
            availableEntities = listOf(EntityBinding("CIRCUIT", "5", "Main Feeder 5")),
            historicalEvidence = emptyList()
        )

        assertEquals("5", resolved.activeEntity?.entityId)
        assertEquals("CIRCUIT", resolved.activeEntity?.entityType)
        assertEquals(CalculationDomain.CABLE_SIZING, resolved.activeDomain)
        assertTrue("What-if branch must be active", resolved.updatedTaskState.isWhatIfBranch)

        val targetSize = resolved.updatedTaskState.typedWhatIfOverrides["targetSize"]
        assertNotNull("targetSize must be recorded in typedWhatIfOverrides", targetSize)
        assertEquals(240.0, targetSize?.decimalValue ?: 0.0, 0.001)
        assertEquals(EngineeringUnit.SQUARE_MILLIMETER, targetSize?.unit)
    }

    // 2. Indirect references in Arabic and English
    @Test
    fun indirectReferences_resolveToPriorActiveEntity() {
        val priorCircuitState = ConversationTaskState(
            taskId = "TASK-001",
            projectId = "PROJ-001",
            baseProjectRevision = 5L,
            activeEntityType = "CIRCUIT",
            activeEntityId = "7",
            activeEntityName = "Pump Circuit"
        )

        val resolvedAr = ContextResolver.resolve(
            userUtterance = "راجع الكابل في الدائرة دي",
            projectContext = baseContext.copy(activeEntityId = "7"),
            priorState = priorCircuitState,
            availableEntities = listOf(EntityBinding("CIRCUIT", "7", "Pump Circuit")),
            historicalEvidence = emptyList()
        )
        assertEquals("7", resolvedAr.activeEntity?.entityId)

        val resolvedEn = ContextResolver.resolve(
            userUtterance = "check this circuit again",
            projectContext = baseContext.copy(activeEntityId = "7"),
            priorState = priorCircuitState,
            availableEntities = listOf(EntityBinding("CIRCUIT", "7", "Pump Circuit")),
            historicalEvidence = emptyList()
        )
        assertEquals("7", resolvedEn.activeEntity?.entityId)
    }

    // 3. Ambiguous entity detection fails closed without guessing
    @Test
    fun ambiguousEntity_failsClosedWithCandidateList() {
        val candidates = listOf(
            EntityBinding("CIRCUIT", "C-01", "Feeder Branch Substation A"),
            EntityBinding("CIRCUIT", "C-02", "Feeder Branch Substation B")
        )

        val resolved = ContextResolver.resolve(
            userUtterance = "راجع الدائرة الفرعية",
            projectContext = baseContext.copy(activeEntityId = null),
            priorState = null,
            availableEntities = candidates,
            historicalEvidence = emptyList()
        )

        assertTrue("Entity ambiguity must be flagged", resolved.isAmbiguousEntity)
        assertEquals(2, resolved.ambiguousCandidates.size)
        assertNull("Ambiguous entity must not pick a random entity", resolved.activeEntity)
    }

    // 4. Contradiction detection between user statement and project truth
    @Test
    fun contradictionDetection_detectsConflictWithoutMutatingDatabase() {
        val resolved = ContextResolver.resolve(
            userUtterance = "الكابل طوله 80 متر احسب عليه",
            projectContext = baseContext, // baseContext has lengthM = 120.0
            priorState = null,
            availableEntities = listOf(EntityBinding("CIRCUIT", "5", "Main Feeder 5")),
            historicalEvidence = emptyList()
        )

        assertEquals(1, resolved.detectedContradictions.size)
        val contradiction = resolved.detectedContradictions[0]
        assertEquals("lengthM", contradiction.fieldName)
        assertEquals("120.0", contradiction.projectValue)
        assertEquals("80.0", contradiction.userValue)
        assertEquals(120.0, contradiction.typedProjectValue?.decimalValue ?: 0.0, 0.001)
        assertEquals(80.0, contradiction.typedUserValue?.decimalValue ?: 0.0, 0.001)

        val slot = resolved.resolvedSlots["lengthM"]
        assertNotNull(slot)
        assertEquals(SlotState.CONTRADICTORY, slot?.state)
    }

    // 5. Missing inputs remain missing; never fabricate defaults
    @Test
    fun missingInputs_remainMissing_neverFabricatesDefaults() {
        val blankContext = BoundedProjectContext(
            projectId = "PROJ-002",
            projectName = "Blank Project",
            liveRevision = 1L,
            activeScreen = "NewCircuit",
            activeEntityType = "CIRCUIT",
            activeEntityId = "new-circuit",
            knownEntityInputs = emptyMap() // No voltage, length, etc.
        )

        val resolved = ContextResolver.resolve(
            userUtterance = "احسب كابل",
            projectContext = blankContext,
            priorState = null,
            availableEntities = emptyList(),
            historicalEvidence = emptyList()
        )

        assertTrue(
            "Voltage must be identified as missing",
            resolved.missingBlockingSlots.contains("voltage")
        )
        assertFalse(
            "Must never assume or fabricate 400V as default",
            resolved.resolvedSlots.containsKey("voltage") && resolved.resolvedSlots["voltage"]?.value == "400"
        )
        assertFalse(
            "Must never assume or fabricate 230V as default",
            resolved.resolvedSlots.containsKey("voltage") && resolved.resolvedSlots["voltage"]?.value == "230"
        )
    }

    // 6. Project switching isolation: Project A state never leaks into Project B
    @Test
    fun projectSwitching_isolatesContext_wipesOldEntityAndOverrides() {
        val priorStateProjectA = ConversationTaskState(
            taskId = "TASK-PROJ-A",
            projectId = "PROJ-A",
            baseProjectRevision = 3L,
            activeEntityType = "CIRCUIT",
            activeEntityId = "101",
            activeEntityName = "Project A Feeder",
            isWhatIfBranch = true,
            typedWhatIfOverrides = mapOf("targetSize" to EngineeringValue.ofDecimal(240.0, EngineeringUnit.SQUARE_MILLIMETER)),
            evidenceReferences = listOf("EVID-A-1")
        )

        val projectBContext = BoundedProjectContext(
            projectId = "PROJ-B",
            projectName = "Project B Factory",
            liveRevision = 1L,
            activeScreen = "Overview",
            activeEntityType = null,
            activeEntityId = null
        )

        val resolved = ContextResolver.resolve(
            userUtterance = "كيف حال المشروع؟",
            projectContext = projectBContext,
            priorState = priorStateProjectA,
            availableEntities = emptyList(),
            historicalEvidence = emptyList()
        )

        assertEquals("PROJ-B", resolved.updatedTaskState.projectId)
        assertNull("Project A entity must not leak into Project B", resolved.activeEntity)
        assertTrue("What-if overrides from Project A must be wiped", resolved.updatedTaskState.typedWhatIfOverrides.isEmpty())
        assertTrue("Evidence references from Project A must be wiped", resolved.updatedTaskState.evidenceReferences.isEmpty())
    }

    // 7. Entity switching isolation: switching to Circuit 2 clears Circuit 1 overrides
    @Test
    fun entitySwitching_clearsPriorEntitySpecificOverrides() {
        val priorStateCircuit1 = ConversationTaskState(
            taskId = "TASK-001",
            projectId = "PROJ-001",
            baseProjectRevision = 5L,
            activeEntityType = "CIRCUIT",
            activeEntityId = "1",
            activeEntityName = "Circuit 1",
            isWhatIfBranch = true,
            typedWhatIfOverrides = mapOf("targetSize" to EngineeringValue.ofDecimal(185.0, EngineeringUnit.SQUARE_MILLIMETER)),
            evidenceReferences = listOf("EVID-C1-1")
        )

        val resolved = ContextResolver.resolve(
            userUtterance = "راجع الدائرة 2",
            projectContext = baseContext.copy(activeEntityId = "2", activeEntityName = "Circuit 2"),
            priorState = priorStateCircuit1,
            availableEntities = listOf(EntityBinding("CIRCUIT", "2", "Circuit 2")),
            historicalEvidence = emptyList()
        )

        assertEquals("2", resolved.activeEntity?.entityId)
        assertTrue(
            "Circuit 1 what-if overrides must not leak into Circuit 2",
            resolved.updatedTaskState.typedWhatIfOverrides.isEmpty()
        )
        assertTrue(
            "Circuit 1 evidence references must not leak into Circuit 2",
            resolved.updatedTaskState.evidenceReferences.isEmpty()
        )
    }

    // 8. Domain switching isolation: switching to transformer drops cable candidate overrides
    @Test
    fun domainSwitching_clearsIncompatibleOverrides() {
        val priorStateCable = ConversationTaskState(
            taskId = "TASK-001",
            projectId = "PROJ-001",
            baseProjectRevision = 5L,
            activeEntityType = "CIRCUIT",
            activeEntityId = "5",
            activeDomain = CalculationDomain.CABLE_SIZING,
            isWhatIfBranch = true,
            typedWhatIfOverrides = mapOf("targetSize" to EngineeringValue.ofDecimal(240.0, EngineeringUnit.SQUARE_MILLIMETER))
        )

        val resolved = ContextResolver.resolve(
            userUtterance = "احسب قدرة المحول الرئيسي",
            projectContext = baseContext.copy(activeEntityType = "TRANSFORMER", activeEntityId = "TX-1"),
            priorState = priorStateCable,
            availableEntities = listOf(EntityBinding("TRANSFORMER", "TX-1", "Main Transformer")),
            historicalEvidence = emptyList()
        )

        assertEquals(CalculationDomain.TRANSFORMER, resolved.activeDomain)
        assertFalse(
            "Cable sizing candidate size must not leak into transformer calculation",
            resolved.updatedTaskState.typedWhatIfOverrides.containsKey("targetSize")
        )
    }

    // 9. Revision advance: historical evidence is never deleted, marked stale
    @Test
    fun revisionAdvance_preservesHistoricalEvidence_marksStale() {
        val details = CableSizingDetailsDto(
            Ib_A = 410.0,
            deviceSize_A = 500.0,
            deviceType = "MCCB",
            cTotalDerating = 0.85,
            iRequired_perCable_A = 482.0,
            productCode = "XLPE-CU-300",
            cableSize_mm2 = "300",
            ratedI_table_A = 610.0,
            capacity_derated_Iz_A = 520.0,
            utilizationPct = 78.8,
            neutral_mm2 = "150",
            earthCPC_mm2 = "150",
            voltDrop_V = 8.4,
            vdPct = 2.1,
            cumulativeVDpct = 2.1,
            status = "PASS"
        )
        val sizingResult = CableSizingExecutionResult(
            projectId = "PROJ-001",
            circuitId = "5",
            circuitName = "Main Feeder 5",
            projectRevision = 10L,
            freshnessState = "CURRENT",
            isRecalculated = false,
            overallStatus = CheckStatus.PASS,
            governingCriterion = GoverningCriterion.AMPACITY,
            governingReason = "Iz >= In",
            details = details
        )
        val rev10Evidence = EngineeringEvidenceRecord(
            evidenceId = "EVID-REV10-001",
            requestId = "REQ-001",
            toolName = "calculate_cable_sizing",
            projectId = "PROJ-001",
            entityType = "CIRCUIT",
            entityId = "5",
            projectRevision = 10L,
            executionStatus = ToolStatus.PASS,
            result = sizingResult,
            freshnessState = "CURRENT"
        )

        val currentContextRev11 = baseContext.copy(liveRevision = 11L)

        val resolved = ContextResolver.resolve(
            userUtterance = "ما هو الكابل المناسب الآن؟",
            projectContext = currentContextRev11,
            priorState = null,
            availableEntities = listOf(EntityBinding("CIRCUIT", "5", "Main Feeder 5")),
            historicalEvidence = listOf(rev10Evidence)
        )

        assertEquals("Historical evidence must be preserved", 1, resolved.evidenceSummaries.size)
        val summary = resolved.evidenceSummaries[0]
        assertEquals("EVID-REV10-001", summary.evidenceId)
        assertEquals("STALE_REVISION", summary.freshnessState)
        assertTrue("Stale evidence detection flag must be true", resolved.isStaleEvidenceDetected)
    }

    // 10. Bounded evidence summary produces clean prompt line without raw payload dumping
    @Test
    fun boundedEvidenceSummary_formatsCleanPromptLine() {
        val summary = BoundedEvidenceSummary(
            evidenceId = "EVID-001",
            toolName = "calculate_cable_sizing",
            domain = CalculationDomain.CABLE_SIZING,
            projectId = "PROJ-001",
            entityType = "CIRCUIT",
            entityId = "5",
            projectRevision = 5L,
            executionStatus = ToolStatus.PASS,
            freshnessState = "FRESH",
            verifiedSelectedSize = "300",
            verifiedCapacityIz = "520.0",
            verifiedDesignCurrentIb = "410.0",
            verifiedVoltageDropPct = "2.1",
            verifiedDeviceRating = "500.0",
            governingCriterion = "Iz >= In",
            overallCompliance = "PASS"
        )

        val line = summary.toBoundedPromptLine()
        assertTrue(line.contains("Ref: EVID-001"))
        assertTrue(line.contains("Domain: CABLE_SIZING"))
        assertTrue(line.contains("Conductor: 300"))
        assertTrue(line.contains("Iz: 520.0 A"))
        assertTrue(line.contains("VD: 2.1%"))
        assertTrue(line.contains("Governing: Iz >= In"))
        assertTrue(line.contains("Compliance: PASS"))
        assertFalse(line.contains("rawPayload"))
    }
}
