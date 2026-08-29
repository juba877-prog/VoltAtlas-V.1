package com.example.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlobalPreferencesTest {

    private lateinit var preferences: GlobalPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        preferences = GlobalPreferences(context)
    }

    @Test
    fun testHasCompletedFirstLaunch_DefaultsToFalseForFreshInstall() {
        // Clear preferences to simulate fresh installation state
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

    @Test
    fun testDefaultValuesMatchContract() {
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
    }

    @Test
    fun testPersistedValuesLoadedAsynchronously() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        preferences.setThemeMode("LIGHT")
        preferences.setDefaultFrequency(60)
        preferences.setDefaultVoltage3Phase(380)
        preferences.setLastOpenedProjectId("proj_123")

        val newPrefs = GlobalPreferences(context)
        newPrefs.awaitLoaded()

        assertEquals("LIGHT", newPrefs.themeMode.value)
        assertEquals(60, newPrefs.defaultFrequency.value)
        assertEquals(380, newPrefs.defaultVoltage3Phase.value)
        assertEquals("proj_123", newPrefs.lastOpenedProjectId.value)
    }
}
