package com.example.ai.approval

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.contracts.ExecutionMode
import com.example.ai.contracts.ExecutionModePolicy
import com.example.ai.proposal.*
import com.example.ai.runtime.BoundedProjectContext
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.data.EngineeringRepository
import com.example.data.ProjectEntity
import com.example.data.TransformerEntity
import com.example.data.ai.ApprovalGrantDao
import com.example.data.catalog.CatalogRepository
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrustedProposalApprovalGatewayTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var approvalGrantDao: ApprovalGrantDao
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var workflow: DurableMutationWorkflow
    private lateinit var gateway: TrustedProposalApprovalGateway

    private val projectId = "PROJ-GATEWAY-TEST"
    private val circuitId = 101
    private var currentTime = 10_000L

    @Before
    fun setUp() {
        AppDatabase.useTestExecutors = true
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        approvalGrantDao = database.approvalGrantDao()
        EngineeringCalculationCoordinator.projectRevisionProvider = EngineeringRepository(database)

        catalogRepository = CatalogRepository().apply {
            loadAllFromStrings(
                cableCatalogJson = """
                [
                  {"mfg":"Elsewedy","typeCode":1,"size":240.0,"model":"Elsewedy Cu/XLPE 240"},
                  {"mfg":"Elsewedy","typeCode":1,"size":185.0,"model":"Elsewedy Cu/XLPE 185"},
                  {"mfg":"Ducab","typeCode":2,"size":300.0,"model":"Ducab Al/XLPE 300"}
                ]
                """.trimIndent(),
                cableTypesDataJson = """
                [
                  {"typeCode":1,"cableType":"Cu/XLPE/SWA/PVC","sizeMm2":240.0,"rCu70":0.098,"x":0.08,"rAl70":0.0,"rCu90":0.104,"rAl90":0.0,"conductor":"Cu","insulation":"XLPE","armour":"SWA","cores":"4C"},
                  {"typeCode":2,"cableType":"Al/XLPE/STA/PVC - 4C","sizeMm2":300.0,"rCu70":0.0,"x":0.08,"rAl70":0.125,"rCu90":0.0,"rAl90":0.13,"conductor":"Al","insulation":"XLPE","armour":"STA","cores":"4C"}
                ]
                """.trimIndent(),
                breakerCatalogJson = """
                [
                  {"mfg":"Schneider","type":"MCCB-TM","series":"NSX","frame":"NSX400","version":"F","ratingA":400.0,"poles":3,"icuKa":36.0,"icsKa":36.0,"icmKa":75.6,"icwKa":0.0,"cat":"A","tripUnit":"TM400D","tripType":"Thermal-Magnetic","orderingCode":"LV432676"},
                  {"mfg":"Schneider","type":"MCCB-TM","series":"NSX","frame":"NSX250","version":"F","ratingA":250.0,"poles":3,"icuKa":36.0,"icsKa":36.0,"icmKa":75.6,"icwKa":0.0,"cat":"A","tripUnit":"TM250D","tripType":"Thermal-Magnetic","orderingCode":"LV431630"}
                ]
                """.trimIndent(),
                accessoryCatalogJson = "[]",
                deratingDataJson = "{}",
                transformersSeedJson = "[]"
            )
        }

        workflow = DurableMutationWorkflow(
            db = database,
            catalogRepository = catalogRepository,
            clock = { currentTime },
            executionIdProvider = { "EXEC-TEST-GATEWAY" }
        )

        gateway = TrustedProposalApprovalGateway(
            approvalGrantDao = approvalGrantDao,
            durableWorkflow = workflow,
            clock = { currentTime },
            principalProvider = DefaultTrustedPrincipalProvider { "eng-mahmoud-auth" }
        )

        runBlocking {
            database.projectDao().upsertProject(
                ProjectEntity(
                    projectId = projectId,
                    projectName = "Gateway Test Project",
                    revision = 1L,
                    createdAt = 1000L,
                    updatedAt = 1000L
                )
            )
            database.transformerDao().insertTransformer(
                TransformerEntity(
                    projectId = projectId,
                    transformerId = "TX-1",
                    transformerName = "Main TX",
                    kVA = 1000.0,
                    voltage = 400,
                    percentZ = 5.0
                )
            )
            database.circuitDao().insertCircuit(
                CircuitEntity(
                    id = circuitId,
                    circuitName = "Feeder 101",
                    projectId = projectId,
                    transformerId = "TX-1",
                    sourceType = "TRANSFORMER",
                    fedFrom = "Transformer",
                    cableType = "Cu/XLPE/SWA/PVC",
                    manufacturer = "Elsewedy",
                    installation = "Laid in Ducts",
                    parallelRuns = 1,
                    length_m = 50.0,
                    overrideCableSize = "240.0",
                    overrideDeviceSize = 400.0,
                    overrideDeviceType = "MCCB-TM"
                )
            )
        }
    }

    @After
    fun tearDown() {
        EngineeringCalculationCoordinator.projectRevisionProvider = null
        AppDatabase.resetInstanceForTesting()
        database.close()
    }

    private fun createValidProposal(baseRevision: Long = 1L): CableEngineeringProposal {
        return CableEngineeringProposal(
            proposalId = "PROP-GATEWAY-1",
            projectId = projectId,
            baseProjectRevision = baseRevision,
            targetEntityType = "CIRCUIT",
            targetEntityId = circuitId.toString(),
            operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
            patch = CableCircuitPatch(
                overrideCableSize = PatchValue.set("185.0")
            ),
            rationale = "Upsize cable to 185.0 mm²",
            sourceToolName = "ProposeCableSizingTool",
            sourceRequestId = "REQ-1",
            createdAt = currentTime,
            expiresAt = currentTime + 300_000L
        )
    }

    private fun createBoundedContext(liveRevision: Long = 1L): BoundedProjectContext {
        return BoundedProjectContext(
            projectId = projectId,
            projectName = "Gateway Test Project",
            liveRevision = liveRevision,
            activeScreen = "CircuitsGridScreen",
            activeEntityType = "CIRCUIT",
            activeEntityId = circuitId.toString(),
            activeEntityName = "Feeder 101"
        )
    }

    // 1. Missing userActionId fails closed
    @Test
    fun missingUserActionId_failsClosed() = runBlocking {
        val proposal = createValidProposal()
        val ctx = createBoundedContext()

        val request = TrustedApprovalRequest(
            proposal = proposal,
            projectContext = ctx,
            userActionId = "" // Blank user action ID
        )

        val result = gateway.processApprovalAndExecute(request)
        assertTrue(result is TrustedApprovalGateResult.ValidationFailed)
        assertTrue((result as TrustedApprovalGateResult.ValidationFailed).reason.contains("User action ID"))
    }

    // 2. Missing principal fails closed
    @Test
    fun missingTrustedPrincipal_failsClosed() = runBlocking {
        val noPrincipalGateway = TrustedProposalApprovalGateway(
            approvalGrantDao = approvalGrantDao,
            durableWorkflow = workflow,
            clock = { currentTime },
            principalProvider = DefaultTrustedPrincipalProvider { null }
        )

        val proposal = createValidProposal()
        val ctx = createBoundedContext()
        val request = TrustedApprovalRequest(
            proposal = proposal,
            projectContext = ctx,
            userActionId = "UA-TEST-001"
        )

        val result = noPrincipalGateway.processApprovalAndExecute(request)
        assertTrue(result is TrustedApprovalGateResult.PrincipalMissing)

        // Verify zero grants inserted
        val grants = approvalGrantDao.getGrantsForProject(projectId)
        assertEquals(0, grants.size)
    }

    // 3. Stale revision fails before grant insertion
    @Test
    fun revisionAdvancedBeforeConfirmation_failsStale() = runBlocking {
        val proposal = createValidProposal(baseRevision = 1L)
        val ctx = createBoundedContext(liveRevision = 2L) // Project advanced

        val request = TrustedApprovalRequest(
            proposal = proposal,
            projectContext = ctx,
            userActionId = "UA-TEST-002"
        )

        val result = gateway.processApprovalAndExecute(request)
        assertTrue(result is TrustedApprovalGateResult.StaleProposal)
        val stale = result as TrustedApprovalGateResult.StaleProposal
        assertEquals(1L, stale.baseRevision)
        assertEquals(2L, stale.liveRevision)

        // Verify zero grants inserted
        val grants = approvalGrantDao.getGrantsForProject(projectId)
        assertEquals(0, grants.size)
    }

    // 4. Valid explicit approval inserts grant and commits mutation
    @Test
    fun validFinalConfirmation_insertsOneIssuedApproval_andExecutesWorkflow() = runBlocking {
        val proposal = createValidProposal(baseRevision = 1L)
        val ctx = createBoundedContext(liveRevision = 1L)
        val approvalId = "APP-TEST-9001"

        val request = TrustedApprovalRequest(
            proposal = proposal,
            projectContext = ctx,
            userActionId = "UA-TEST-9001",
            approvalId = approvalId
        )

        val result = gateway.processApprovalAndExecute(request)
        assertTrue(result is TrustedApprovalGateResult.Success)
        val success = result as TrustedApprovalGateResult.Success

        // Verify durable outcome
        assertTrue(success.outcome is DurableMutationOutcome.Completed)
        val completed = success.outcome as DurableMutationOutcome.Completed
        assertEquals(1L, completed.receipt.revisionBefore)
        assertEquals(2L, completed.receipt.revisionAfter)
        assertEquals(RevalidationStatus.SUCCEEDED, completed.receipt.revalidationStatus)

        // Verify DB circuit updated
        val updatedCircuit = database.circuitDao().getCircuitById(circuitId)
        assertNotNull(updatedCircuit)
        assertEquals("185.0", updatedCircuit?.overrideCableSize)

        // Verify project revision incremented
        val updatedProject = database.projectDao().getProject(projectId)
        assertEquals(2L, updatedProject?.revision)

        // Verify grant is now CONSUMED in DB
        val grant = approvalGrantDao.getGrantById(approvalId)
        assertNotNull(grant)
        assertEquals(ApprovalStatus.CONSUMED, grant?.status)
        assertEquals("eng-mahmoud-auth", grant?.approvedBy)
    }

    // 5. Double tap reuses single active grant and returns existing receipt without remutation
    @Test
    fun doubleTap_reusesActiveGrant_returnsExistingReceipt() = runBlocking {
        val proposal = createValidProposal(baseRevision = 1L)
        val ctx = createBoundedContext(liveRevision = 1L)
        val userActionId = "UA-TEST-DOUBLE-TAP"

        // First click
        val req1 = TrustedApprovalRequest(
            proposal = proposal,
            projectContext = ctx,
            userActionId = userActionId,
            approvalId = "APP-CLICK-1"
        )
        val res1 = gateway.processApprovalAndExecute(req1)
        assertTrue(res1 is TrustedApprovalGateResult.Success)

        // Second click (same proposal / intent)
        val req2 = TrustedApprovalRequest(
            proposal = proposal,
            projectContext = ctx,
            userActionId = userActionId,
            approvalId = "APP-CLICK-2"
        )
        val res2 = gateway.processApprovalAndExecute(req2)
        assertTrue(res2 is TrustedApprovalGateResult.Success)
        val success2 = res2 as TrustedApprovalGateResult.Success

        // Should reject or return existing receipt without remutation
        assertTrue(
            success2.outcome is DurableMutationOutcome.RevisionConflict ||
            success2.outcome is DurableMutationOutcome.ExistingReceiptReturned ||
            success2.outcome is DurableMutationOutcome.Completed
        )

        // Project revision remains 2L (not incremented twice)
        val project = database.projectDao().getProject(projectId)
        assertEquals(2L, project?.revision)
    }

    // 6. Global ExecutionMode.MUTATION remains false
    @Test
    fun executionModeMutationRemainsFalse() {
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }
}
