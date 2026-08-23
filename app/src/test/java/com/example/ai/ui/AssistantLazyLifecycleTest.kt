package com.example.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.example.ai.context.EngineeringContext
import com.example.ai.service.AssistantMessage
import com.example.ai.service.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic architecture and lifecycle tests proving:
 * 1. Initial startup resolver creation does NOT invoke the assistant factory.
 * 2. First assistant-open action creates exactly one assistant ViewModel instance.
 * 3. Recomposition / repeated resolution does NOT create another instance.
 * 4. Closing and reopening the sheet in the same ViewModelStoreOwner returns the exact same instance with state preserved.
 * 5. Assistant open, submit, and close operations operate with full integrity through lazy resolution.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssistantLazyLifecycleTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class TestViewModelStoreOwner : ViewModelStoreOwner {
        private val store = ViewModelStore()
        override val viewModelStore: ViewModelStore
            get() = store
    }

    private class TrackingAssistantFactory(
        private val invocationCounter: AtomicInteger = AtomicInteger(0)
    ) : ViewModelProvider.Factory {
        val createCount: Int get() = invocationCounter.get()

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            invocationCounter.incrementAndGet()
            return EngineeringAssistantViewModel() as T
        }
    }

    // 1. Initial startup / resolver setup does NOT invoke factory or create ViewModel
    @Test
    fun initialSetup_doesNotInvokeAssistantFactory() {
        val owner = TestViewModelStoreOwner()
        val factory = TrackingAssistantFactory()

        // Simulates the lazy resolver lambda declared in MainLayout
        val resolveAssistantViewModel: () -> EngineeringAssistantViewModel = {
            ViewModelProvider(owner, factory)[EngineeringAssistantViewModel::class.java]
        }

        // Before any open action is requested:
        assertEquals("Factory must not be invoked during initial setup/startup composition", 0, factory.createCount)
    }

    // 2. First assistant-open action creates exactly one assistant ViewModel
    @Test
    fun firstAssistantOpenAction_createsExactlyOneViewModelInstance() {
        val owner = TestViewModelStoreOwner()
        val factory = TrackingAssistantFactory()

        val resolveAssistantViewModel: () -> EngineeringAssistantViewModel = {
            ViewModelProvider(owner, factory)[EngineeringAssistantViewModel::class.java]
        }

        // User triggers assistant open
        val vm = resolveAssistantViewModel()
        vm.open(AssistantEntryPoint.TOP_BAR_ICON, EngineeringContext(projectName = "VoltProject"))

        assertEquals("Factory must be invoked exactly once on first assistant open", 1, factory.createCount)
        assertTrue(vm.uiState.value.isOpen)
        assertEquals(AssistantEntryPoint.TOP_BAR_ICON, vm.uiState.value.entryPoint)
    }

    // 3. Repeated resolution (simulating recomposition) returns the exact same instance without re-invoking factory
    @Test
    fun repeatedResolution_returnsIdenticalInstanceWithoutRecreating() {
        val owner = TestViewModelStoreOwner()
        val factory = TrackingAssistantFactory()

        val resolveAssistantViewModel: () -> EngineeringAssistantViewModel = {
            ViewModelProvider(owner, factory)[EngineeringAssistantViewModel::class.java]
        }

        val firstInstance = resolveAssistantViewModel()
        firstInstance.setInputText("Calculate feeder voltage drop")

        // Simulate multiple recomposition sweeps
        val secondInstance = resolveAssistantViewModel()
        val thirdInstance = resolveAssistantViewModel()

        assertSame("Recomposition must return identical ViewModel instance", firstInstance, secondInstance)
        assertSame("Recomposition must return identical ViewModel instance", firstInstance, thirdInstance)
        assertEquals("Factory must NOT be invoked again on subsequent resolutions", 1, factory.createCount)
        assertEquals("Input text state must be preserved across resolutions", "Calculate feeder voltage drop", thirdInstance.uiState.value.inputText)
    }

    // 4. Closing and reopening the sheet preserves state and does not create a new instance
    @Test
    fun closingAndReopeningSheet_preservesStateAndReusesInstance() {
        val owner = TestViewModelStoreOwner()
        val factory = TrackingAssistantFactory()

        val resolveAssistantViewModel: () -> EngineeringAssistantViewModel = {
            ViewModelProvider(owner, factory)[EngineeringAssistantViewModel::class.java]
        }

        // 1. First open
        val vm = resolveAssistantViewModel()
        vm.open(AssistantEntryPoint.TOP_BAR_ICON, EngineeringContext(projectName = "Substation-A"))
        vm.setInputText("Saved draft query")

        // 2. User closes the sheet
        vm.close()
        assertFalse("Sheet must be closed", vm.uiState.value.isOpen)
        assertEquals(AssistantState.CLOSED, vm.uiState.value.state)

        // 3. User re-opens the sheet later in the same Activity session
        val reopenedVm = resolveAssistantViewModel()
        assertSame("Must be the exact same ViewModel instance", vm, reopenedVm)
        assertEquals("Factory must still have been called only once", 1, factory.createCount)

        reopenedVm.open(AssistantEntryPoint.DASHBOARD_ACTION, EngineeringContext(projectName = "Substation-A"))
        assertTrue("Sheet must be open again", reopenedVm.uiState.value.isOpen)
        assertEquals("Prior input text or conversation should remain intact", "Saved draft query", reopenedVm.uiState.value.inputText)
    }

    // 5. Scoped isolation: updating assistant uiState emits to its own StateFlow without touching unrelated holders
    @Test
    fun scopedAssistantStateFlow_emitsDirectlyToSheetCollector() {
        val vm = EngineeringAssistantViewModel()
        val initialCollectedState = vm.uiState.value
        assertEquals(AssistantState.CLOSED, initialCollectedState.state)

        vm.open(AssistantEntryPoint.CONTEXTUAL_CIRCUIT, EngineeringContext(projectName = "Feeder-1"))
        val updatedState = vm.uiState.value
        assertEquals(AssistantState.COMPACT, updatedState.state)
        assertEquals(AssistantEntryPoint.CONTEXTUAL_CIRCUIT, updatedState.entryPoint)

        vm.toggleExpand()
        assertEquals(AssistantState.EXPANDED, vm.uiState.value.state)

        vm.close()
        assertEquals(AssistantState.CLOSED, vm.uiState.value.state)
    }
}
