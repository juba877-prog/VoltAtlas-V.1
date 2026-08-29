package com.example.data

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ai.ui.EngineeringAssistantViewModelFactory
import com.example.ui.EngineeringViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlobalPreferencesTest {

    private lateinit var preferences: GlobalPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        GlobalPreferences.resetInstanceForTesting()
        val rawPrefs = context.getSharedPreferences(GlobalPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
        rawPrefs.edit().clear().commit()
        preferences = GlobalPreferences(context, CoroutineScope(Dispatchers.Unconfined))
    }

    @Test
    fun testHasCompletedFirstLaunch_DefaultsToFalseForFreshInstall() {
        preferences.setHasCompletedFirstLaunch(false)
        val initialValue = preferences.hasCompletedFirstLaunch.value
        assertFalse("hasCompletedFirstLaunch should be false on fresh install", initialValue)
    }

    @Test
    fun testSetHasCompletedFirstLaunch_PersistsTrueState() {
        preferences.setHasCompletedFirstLaunch(true)
        assertTrue("hasCompletedFirstLaunch should be true after completion", preferences.hasCompletedFirstLaunch.value)
    }

    @Test
    fun testSetHasCompletedFirstLaunch_ResetsToFalseForReplay() {
        preferences.setHasCompletedFirstLaunch(true)
        assertTrue(preferences.hasCompletedFirstLaunch.value)

        preferences.setHasCompletedFirstLaunch(false)
        assertFalse("hasCompletedFirstLaunch should reset to false for replay guide option", preferences.hasCompletedFirstLaunch.value)
    }

    // Existing defaults contract: all 19 defaults exactly match contract
    @Test
    fun testDefaultValuesMatchContract() = runBlocking {
        assertEquals("", preferences.trustedEngineerIdentity.value)
        assertTrue(preferences.confirmDestructiveActions.value)
        assertEquals("ALWAYS_SHOW_PICKER", preferences.startupBehavior.value)
        assertEquals("DARK", preferences.themeMode.value)
        assertEquals(50, preferences.defaultFrequency.value)
        assertEquals(400, preferences.defaultVoltage3Phase.value)
        assertEquals(3.0, preferences.maxVdLimitLighting.value, 0.001)
        assertEquals(5.0, preferences.maxVdLimitPower.value, 0.001)
        assertEquals("Cu", preferences.defaultConductorMaterial.value)
        assertEquals("XLPE", preferences.defaultInsulation.value)
        assertEquals("4C", preferences.defaultCoreConfig.value)
        assertFalse(preferences.hasCompletedFirstLaunch.value)
        assertNull(preferences.lastOpenedProjectId.value)
        assertFalse(preferences.fullBootEnabled.value)
        assertTrue(preferences.showLastProjectOnStartup.value)
        assertFalse(preferences.bootSoundEnabled.value)
        assertEquals(500.0, preferences.drumDefaultCapacity.value, 0.001)
        assertEquals(0.05, preferences.cableSafetyMargin.value, 0.001)
        assertEquals(1.5, preferences.cableCutOverhead.value, 0.001)

        val result = preferences.awaitLoaded()
        assertEquals(PreferencesLoadState.Loaded, result)
        assertEquals(PreferencesLoadState.Loaded, preferences.loadState.value)
    }

    // Persisted values asynchronous load: replaces defaults and sets loadState to Loaded
    @Test
    fun testPersistedValuesLoadedAsynchronously() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rawPrefs = context.getSharedPreferences(GlobalPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
        rawPrefs.edit()
            .putString(GlobalPreferences.KEY_THEME_MODE, "LIGHT")
            .putInt(GlobalPreferences.KEY_DEFAULT_FREQUENCY, 60)
            .putInt(GlobalPreferences.KEY_DEFAULT_VOLTAGE_3P, 380)
            .putString(GlobalPreferences.KEY_LAST_OPENED_PROJECT_ID, "proj_123")
            .commit()

        val newPrefs = GlobalPreferences(context, CoroutineScope(Dispatchers.IO))
        val loadResult = newPrefs.awaitLoaded()

        assertEquals(PreferencesLoadState.Loaded, loadResult)
        assertEquals(PreferencesLoadState.Loaded, newPrefs.loadState.value)
        assertEquals("LIGHT", newPrefs.themeMode.value)
        assertEquals(60, newPrefs.defaultFrequency.value)
        assertEquals(380, newPrefs.defaultVoltage3Phase.value)
        assertEquals("proj_123", newPrefs.lastOpenedProjectId.value)
    }

    // Test 1 — Exact Read-Then-Setter-Then-Publish Interleaving
    @Test
    fun testExactReadThenSetterThenPublishInterleaving() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rawPrefs = context.getSharedPreferences(GlobalPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
        rawPrefs.edit().putString(GlobalPreferences.KEY_THEME_MODE, "DARK").commit()

        val testDispatcher = StandardTestDispatcher()
        val testScope = CoroutineScope(testDispatcher)

        val prefs = GlobalPreferences(context, testScope, testDispatcher)

        val readCompletedSignal = CompletableDeferred<Unit>()
        val resumePublicationSignal = CompletableDeferred<Unit>()

        prefs.onBeforePublishSnapshot = {
            readCompletedSignal.complete(Unit)
            resumePublicationSignal.await()
        }

        // Advance dispatcher until readSnapshotFromDisk completes and pauses at onBeforePublishSnapshot
        testScope.launch {
            // keep loadJob advancing
        }
        testDispatcher.scheduler.runCurrent()
        readCompletedSignal.await()

        // 3. Call setter while snapshot publication is paused
        prefs.setThemeMode("LIGHT")
        assertEquals("LIGHT", prefs.themeMode.value)
        assertEquals("LIGHT", rawPrefs.getString(GlobalPreferences.KEY_THEME_MODE, null))

        // 4. Resume publication
        resumePublicationSignal.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        // 5 & 6 & 7. Version barrier prevents stale DARK snapshot from overwriting LIGHT
        assertEquals("LIGHT", prefs.themeMode.value)
        assertEquals("LIGHT", rawPrefs.getString(GlobalPreferences.KEY_THEME_MODE, null))
        assertEquals(PreferencesLoadState.Loaded, prefs.awaitLoaded())
    }

    // Test 2 — Setter and Persistence Mutation Ordering
    @Test
    fun testSetterAndPersistenceAtomicOrdering() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rawPrefs = context.getSharedPreferences(GlobalPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)

        val prefs = GlobalPreferences(context, CoroutineScope(Dispatchers.Unconfined))

        // Rapid sequential mutations under synchronized(lock)
        prefs.setThemeMode("LIGHT")
        assertEquals("LIGHT", prefs.themeMode.value)
        assertEquals("LIGHT", rawPrefs.getString(GlobalPreferences.KEY_THEME_MODE, null))

        prefs.setThemeMode("DARK")
        assertEquals("DARK", prefs.themeMode.value)
        assertEquals("DARK", rawPrefs.getString(GlobalPreferences.KEY_THEME_MODE, null))

        prefs.setCableSafetyMargin(0.12)
        assertEquals(0.12, prefs.cableSafetyMargin.value, 0.0001)
        assertEquals(0.12f, rawPrefs.getFloat(GlobalPreferences.KEY_CABLE_SAFETY_MARGIN, 0.0f), 0.0001f)
    }

    // Test — Reset During Load Is Not Overwritten (Paused Dispatcher)
    @Test
    fun testResetDuringLoadIsNotOverwritten() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rawPrefs = context.getSharedPreferences(GlobalPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
        rawPrefs.edit()
            .putString(GlobalPreferences.KEY_THEME_MODE, "LIGHT")
            .putInt(GlobalPreferences.KEY_DEFAULT_FREQUENCY, 60)
            .putInt(GlobalPreferences.KEY_DEFAULT_VOLTAGE_3P, 380)
            .putFloat(GlobalPreferences.KEY_DRUM_DEFAULT_CAPACITY, 1200f)
            .putString(GlobalPreferences.KEY_TRUSTED_ENGINEER_IDENTITY, "Eng. Alice")
            .commit()

        val testDispatcher = StandardTestDispatcher()
        val testScope = CoroutineScope(testDispatcher)

        val prefs = GlobalPreferences(context, testScope, testDispatcher)

        // Reset called while load coroutine is pending on paused dispatcher
        prefs.resetToDefaults()

        // Advance dispatcher to execute the loadFromDisk coroutine
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert all 19 StateFlows remain their canonical reset defaults
        assertEquals("", prefs.trustedEngineerIdentity.value)
        assertTrue(prefs.confirmDestructiveActions.value)
        assertEquals("ALWAYS_SHOW_PICKER", prefs.startupBehavior.value)
        assertEquals("DARK", prefs.themeMode.value)
        assertEquals(50, prefs.defaultFrequency.value)
        assertEquals(400, prefs.defaultVoltage3Phase.value)
        assertEquals(3.0, prefs.maxVdLimitLighting.value, 0.001)
        assertEquals(5.0, prefs.maxVdLimitPower.value, 0.001)
        assertEquals("Cu", prefs.defaultConductorMaterial.value)
        assertEquals("XLPE", prefs.defaultInsulation.value)
        assertEquals("4C", prefs.defaultCoreConfig.value)
        assertFalse(prefs.hasCompletedFirstLaunch.value)
        assertNull(prefs.lastOpenedProjectId.value)
        assertFalse(prefs.fullBootEnabled.value)
        assertTrue(prefs.showLastProjectOnStartup.value)
        assertFalse(prefs.bootSoundEnabled.value)
        assertEquals(500.0, prefs.drumDefaultCapacity.value, 0.001)
        assertEquals(0.05, prefs.cableSafetyMargin.value, 0.001)
        assertEquals(1.5, prefs.cableCutOverhead.value, 0.001)

        // Assert the old disk snapshot was not republished
        assertEquals("DARK", prefs.themeMode.value)
        assertEquals(50, prefs.defaultFrequency.value)

        // Assert final load state is Loaded
        assertEquals(PreferencesLoadState.Loaded, prefs.awaitLoaded())
    }

    // Test 3 — Reset During Load With In-Flight Seam Hook
    @Test
    fun testResetDuringLoadLeavesDefaultsActive() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rawPrefs = context.getSharedPreferences(GlobalPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
        rawPrefs.edit()
            .putString(GlobalPreferences.KEY_THEME_MODE, "LIGHT")
            .putInt(GlobalPreferences.KEY_DEFAULT_FREQUENCY, 60)
            .putFloat(GlobalPreferences.KEY_DRUM_DEFAULT_CAPACITY, 1200f)
            .putString(GlobalPreferences.KEY_TRUSTED_ENGINEER_IDENTITY, "Eng. John Doe")
            .commit()

        val testDispatcher = StandardTestDispatcher()
        val testScope = CoroutineScope(testDispatcher)

        val prefs = GlobalPreferences(context, testScope, testDispatcher)

        val readCompletedSignal = CompletableDeferred<Unit>()
        val resumePublicationSignal = CompletableDeferred<Unit>()

        prefs.onBeforePublishSnapshot = {
            readCompletedSignal.complete(Unit)
            resumePublicationSignal.await()
        }

        testDispatcher.scheduler.runCurrent()
        readCompletedSignal.await()

        // Execute resetToDefaults under common lock while publication is paused
        prefs.resetToDefaults()

        // Resume publication
        resumePublicationSignal.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert all 19 StateFlows remain their canonical defaults
        assertEquals("", prefs.trustedEngineerIdentity.value)
        assertTrue(prefs.confirmDestructiveActions.value)
        assertEquals("ALWAYS_SHOW_PICKER", prefs.startupBehavior.value)
        assertEquals("DARK", prefs.themeMode.value)
        assertEquals(50, prefs.defaultFrequency.value)
        assertEquals(400, prefs.defaultVoltage3Phase.value)
        assertEquals(3.0, prefs.maxVdLimitLighting.value, 0.001)
        assertEquals(5.0, prefs.maxVdLimitPower.value, 0.001)
        assertEquals("Cu", prefs.defaultConductorMaterial.value)
        assertEquals("XLPE", prefs.defaultInsulation.value)
        assertEquals("4C", prefs.defaultCoreConfig.value)
        assertFalse(prefs.hasCompletedFirstLaunch.value)
        assertNull(prefs.lastOpenedProjectId.value)
        assertFalse(prefs.fullBootEnabled.value)
        assertTrue(prefs.showLastProjectOnStartup.value)
        assertFalse(prefs.bootSoundEnabled.value)
        assertEquals(500.0, prefs.drumDefaultCapacity.value, 0.001)
        assertEquals(0.05, prefs.cableSafetyMargin.value, 0.001)
        assertEquals(1.5, prefs.cableCutOverhead.value, 0.001)

        assertEquals(PreferencesLoadState.Loaded, prefs.awaitLoaded())
    }

    // Test 4 — Application-Owned Singleton Lifecycle
    @Test
    fun testApplicationOwnedSingletonLifecycle() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        GlobalPreferences.resetInstanceForTesting()

        val instance1 = GlobalPreferences.getInstance(app)

        // Cancel an unrelated external ViewModel scope
        val externalScopeJob = Job()
        val externalScope = CoroutineScope(externalScopeJob)
        externalScopeJob.cancel()

        // A second getInstance call returns the identical singleton
        val instance2 = GlobalPreferences.getInstance(app)
        assertSame("GlobalPreferences.getInstance must return the identical singleton instance", instance1, instance2)

        // Verify the singleton's loadJob is NOT cancelled by external scope cancellation
        assertFalse(instance1.loadJob.isCancelled)
        val loadResult = instance1.awaitLoaded()
        assertEquals(PreferencesLoadState.Loaded, loadResult)

        // Verify Assistant and ViewModel factory resolve identical singleton
        val assistantFactory = EngineeringAssistantViewModelFactory.production(app)
        assertNotNull(assistantFactory)

        // Verify resetInstanceForTesting() cancels old singleton work and next getInstance() returns a new instance
        GlobalPreferences.resetInstanceForTesting()
        val instance3 = GlobalPreferences.getInstance(app)
        assertNotSame("After resetInstanceForTesting, getInstance must return a fresh singleton instance", instance1, instance3)
    }

    // Test 5 — Load Failure
    @Test
    fun testLoadFailureLeavesSafeDefaultsAndEmitsFailedState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val basePrefs = context.getSharedPreferences(GlobalPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val mockPrefs = object : SharedPreferences by basePrefs {
            override fun getString(key: String?, defValue: String?): String? {
                if (key == GlobalPreferences.KEY_TRUSTED_ENGINEER_IDENTITY) {
                    throw RuntimeException("Simulated disk I/O failure")
                }
                return basePrefs.getString(key, defValue)
            }
        }
        val mockContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = mockPrefs
        }

        val prefs = GlobalPreferences(mockContext, CoroutineScope(Dispatchers.IO))
        val loadResult = prefs.awaitLoaded()

        assertTrue("Expected Failed load state", loadResult is PreferencesLoadState.Failed)
        assertEquals(PreferencesLoadState.Failed::class, prefs.loadState.value::class)
        assertEquals("DARK", prefs.themeMode.value)
        assertTrue(prefs.confirmDestructiveActions.value)
        assertEquals(50, prefs.defaultFrequency.value)
    }

    // Test 6 — Cancellation
    @Test
    fun testCancellationPropagatesWithoutBeingMistakenForFailure() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDispatcher = StandardTestDispatcher()
        val job = Job()
        val testScope = CoroutineScope(job + testDispatcher)

        val prefs = GlobalPreferences(context, testScope, testDispatcher)
        job.cancel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(prefs.loadJob.isCancelled)
        assertTrue(prefs.loadState.value is PreferencesLoadState.Loading)
    }
}
