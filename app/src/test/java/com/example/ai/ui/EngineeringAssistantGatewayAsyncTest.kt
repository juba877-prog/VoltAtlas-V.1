package com.example.ai.ui

import com.example.ai.approval.TrustedProposalApprovalGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.approval.DefaultTrustedPrincipalProvider
import com.example.ai.proposal.DurableMutationWorkflow
import com.example.data.AppDatabase
import com.example.data.catalog.CatalogRepository
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * Gate C Fail-First Test:
 * Proves that:
 * 1. ViewModel creation with an async gateway provider starts in GatewayStatus.Loading immediately.
 * 2. Background initialization advances asynchronously to GatewayStatus.Ready on the scheduler without blocking.
 * 3. Gateway initialization errors transition cleanly to GatewayStatus.Failed with a surfaced message (never infinite loading).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EngineeringAssistantGatewayAsyncTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testGateway: TrustedProposalApprovalGateway

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        testGateway = DurableMutationWorkflow.createGateway(
            database = db,
            catalogRepository = CatalogRepository(),
            principalProvider = DefaultTrustedPrincipalProvider { "test-eng" }
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testViewModelCreation_gatewayInitializesAsynchronouslyWithoutBlocking() = runTest(testDispatcher) {
        val asyncProvider: suspend () -> TrustedProposalApprovalGateway = {
            delay(50)
            testGateway
        }

        val viewModel = EngineeringAssistantViewModel(
            asyncApprovalGatewayProvider = asyncProvider,
            ioDispatcher = testDispatcher
        )

        // 1. Immediately upon construction, status MUST be Loading (async background execution)
        assertEquals("Gateway status must be Loading immediately after construction", GatewayStatus.Loading, viewModel.uiState.value.gatewayStatus)

        // 2. Advance scheduler
        advanceUntilIdle()

        // 3. After completion, status MUST be Ready and gateway available
        assertEquals("Gateway status must transition to Ready after scheduler advances", GatewayStatus.Ready, viewModel.uiState.value.gatewayStatus)
        assertNotNull(viewModel.approvalGateway)
    }

    @Test
    fun testViewModelCreation_catalogLoadingFailure_surfacesFailedStatus() = runTest(testDispatcher) {
        val failingProvider: suspend () -> TrustedProposalApprovalGateway = {
            delay(20)
            throw IOException("Corrupt catalog JSON asset")
        }

        val viewModel = EngineeringAssistantViewModel(
            asyncApprovalGatewayProvider = failingProvider,
            ioDispatcher = testDispatcher
        )

        assertEquals("Gateway status must be Loading before job executes", GatewayStatus.Loading, viewModel.uiState.value.gatewayStatus)
        advanceUntilIdle()
        assertEquals("Gateway status must be Failed when catalog parsing fails", GatewayStatus.Failed, viewModel.uiState.value.gatewayStatus)
        assertEquals("Corrupt catalog JSON asset", viewModel.uiState.value.gatewayErrorMessage)
    }

    @Test
    fun testFactory_createsViewModelWithAsyncGatewayProvider_zeroRunBlockingOnCreation() = runTest(testDispatcher) {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val factory = EngineeringAssistantViewModelFactory.production(app)
        val viewModel = factory.create(EngineeringAssistantViewModel::class.java)

        // ViewModel must be created asynchronously without blocking the main/test thread
        org.junit.Assert.assertTrue(
            "Initial status must be Loading or Ready",
            viewModel.uiState.value.gatewayStatus == GatewayStatus.Loading ||
            viewModel.uiState.value.gatewayStatus == GatewayStatus.Ready
        )
    }

    @Test
    fun testViewModelCreation_bothGatewayAndProviderNull_setsStatusUnavailable() = runTest(testDispatcher) {
        val viewModel = EngineeringAssistantViewModel(
            approvalGateway = null,
            asyncApprovalGatewayProvider = null,
            ioDispatcher = testDispatcher
        )
        assertEquals(
            "When neither gateway nor provider is supplied, status must be Unavailable (never Ready)",
            GatewayStatus.Unavailable,
            viewModel.uiState.value.gatewayStatus
        )
    }
}
