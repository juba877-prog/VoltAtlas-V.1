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
class TrustedApprovalEndToEndTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var workflow: DurableMutationWorkflow

    private val projectId = "PROJ-APPROVAL-E2E"
    private val circuitId = 301
    private var currentTime = 10_000L

    @Before
    fun setUp() {
        AppDatabase.useTestExecutors = true
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        EngineeringCalculationCoordinator.projectRevisionProvider = EngineeringRepository(database)

        catalogRepository = CatalogRepository().apply {
            loadAllFromStrings(
                cableCatalogJson = """
                [
                  {"mfg":"Elsewedy","typeCode":1,"size":240.0,"model":"Elsewedy Cu/XLPE 240"},
                  {"mfg":"Elsewedy","typeCode":1,"size":300.0,"model":"Elsewedy Cu/XLPE 300"}
                ]
                """.trimIndent(),
                cableTypesDataJson = """
                [
                  {"typeCode":1,"cableType":"Cu/XLPE/SWA/PVC","sizeMm2":240.0,"rCu70":0.098,"x":0.08,"rAl70":0.0,"rCu90":0.104,"rAl90":0.0,"conductor":"Cu","insulation":"XLPE","armour":"SWA","cores":"4C"},
                  {"typeCode":1,"cableType":"Cu/XLPE/SWA/PVC","sizeMm2":300.0,"rCu70":0.078,"x":0.08,"rAl70":0.0,"rCu90":0.082,"rAl90":0.0,"conductor":"Cu","insulation":"XLPE","armour":"SWA","cores":"4C"}
                ]
                """.trimIndent(),
                breakerCatalogJson = """
                [
                  {"mfg":"Schneider","type":"MCCB-TM","series":"NSX","frame":"NSX400","version":"F","ratingA":400.0,"poles":3,"icuKa":36.0,"icsKa":36.0,"icmKa":75.6,"icwKa":0.0,"cat":"A","tripUnit":"TM400D","tripType":"Thermal-Magnetic","orderingCode":"LV432676"}
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
            executionIdProvider = { "EXEC-E2E-APPROVAL" }
        )

        runBlocking {
            database.projectDao().upsertProject(
                ProjectEntity(
                    projectId = projectId,
                    projectName = "Approval E2E Project",
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
                    circuitName = "Feeder 301",
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
        AppDatabase.useTestExecutors = false
        database.close()
    }

    private fun createProposal(): CableEngineeringProposal {
        return CableEngineeringProposal(
            proposalId = "PROP-E2E-301",
            projectId = projectId,
            baseProjectRevision = 1L,
            targetEntityType = "CIRCUIT",
            targetEntityId = circuitId.toString(),
            operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
            patch = CableCircuitPatch(overrideCableSize = PatchValue.set("300.0")),
            rationale = "Upsize 301 to 300.0 mm²",
            sourceToolName = "ProposeCableSizingTool",
            sourceRequestId = "REQ-E2E"
        )
    }

    private fun createContext(): BoundedProjectContext {
        return BoundedProjectContext(
            projectId = projectId,
            projectName = "Approval E2E Project",
            liveRevision = 1L,
            activeScreen = "CircuitsGridScreen",
            activeEntityType = "CIRCUIT",
            activeEntityId = circuitId.toString(),
            activeEntityName = "Feeder 301"
        )
    }

    // Mode 1: No trusted authority configured -> fails closed with PrincipalMissing
    @Test
    fun unconfiguredAuthority_failsClosed_createsZeroWrites() = runBlocking {
        val failClosedGateway = TrustedProposalApprovalGateway(
            approvalGrantDao = database.approvalGrantDao(),
            durableWorkflow = workflow,
            clock = { currentTime },
            principalProvider = DefaultTrustedPrincipalProvider() // Default has no supplier, returns null
        )

        val request = TrustedApprovalRequest(
            proposal = createProposal(),
            projectContext = createContext(),
            userActionId = "UA-FAIL-CLOSED-01"
        )

        val result = failClosedGateway.processApprovalAndExecute(request)
        assertTrue(result is TrustedApprovalGateResult.PrincipalMissing)

        // Verify zero grants, zero receipts, revision unchanged
        assertEquals(0, database.approvalGrantDao().getGrantsForProject(projectId).size)
        assertEquals(0, database.mutationExecutionReceiptDao().getReceiptsForProject(projectId).size)
        assertEquals(1L, database.projectDao().getProject(projectId)?.revision)
    }

    // Mode 2: Injected trusted test authority -> executes successfully, binds approvedBy
    @Test
    fun injectedTrustedAuthority_executesSuccessfully_bindsApprovedBy() = runBlocking {
        val trustedAuthority = "eng-mahmoud-verified-lead"
        val activeGateway = TrustedProposalApprovalGateway(
            approvalGrantDao = database.approvalGrantDao(),
            durableWorkflow = workflow,
            clock = { currentTime },
            principalProvider = DefaultTrustedPrincipalProvider { trustedAuthority }
        )

        val proposal = createProposal()
        val request = TrustedApprovalRequest(
            proposal = proposal,
            projectContext = createContext(),
            userActionId = "UA-AUTH-E2E-01",
            approvalId = "APP-AUTH-E2E-01"
        )

        val result = activeGateway.processApprovalAndExecute(request)
        assertTrue(result is TrustedApprovalGateResult.Success)

        // Verify grant in Room has approvedBy bound to trustedAuthority
        val grant = database.approvalGrantDao().getGrantById("APP-AUTH-E2E-01")
        assertNotNull(grant)
        assertEquals(trustedAuthority, grant?.approvedBy)
        assertEquals(ApprovalStatus.CONSUMED, grant?.status)

        // Verify revision incremented exactly once
        assertEquals(2L, database.projectDao().getProject(projectId)?.revision)

        // Verify circuit updated
        val updatedCircuit = database.circuitDao().getCircuitById(circuitId)
        assertEquals("300.0", updatedCircuit?.overrideCableSize)

        // Verify receipt created
        val receipts = database.mutationExecutionReceiptDao().getReceiptsForProject(projectId)
        assertEquals(1, receipts.size)
        assertEquals(RevalidationStatus.SUCCEEDED, receipts.first().revalidationStatus)
    }

    // Global mutation policy remains disabled
    @Test
    fun executionModeMutationRemainsFalse() {
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }
}
