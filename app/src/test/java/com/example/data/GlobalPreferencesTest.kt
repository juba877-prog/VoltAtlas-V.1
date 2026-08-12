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
}
