package com.example.ui.conversions

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ConversionPreferencesTest {

    private lateinit var preferences: ConversionPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        preferences = ConversionPreferences(context)
        preferences.clearHistory()
    }

    @Test
    fun testAddHistoryItem_LimitTo30() {
        for (i in 1..35) {
            preferences.addHistoryItem("tool_$i", "Tool $i", "Input $i", "Result $i")
        }

        val history = preferences.getHistory()
        assertEquals("History must be capped at 30 items", 30, history.size)
        // Newest item should be at index 0
        assertEquals("tool_35", history.first().toolId)
        assertEquals("tool_6", history.last().toolId)
    }

    @Test
    fun testAddHistoryItem_Deduplication() {
        preferences.addHistoryItem("awg_mm2", "AWG to mm²", "AWG 10", "Safe Up: 6.00 mm²")
        preferences.addHistoryItem("kw_hp", "kW to HP", "10 kW", "13.410 HP")
        preferences.addHistoryItem("awg_mm2", "AWG to mm²", "AWG 10", "Safe Up: 6.00 mm² (Updated)")

        val history = preferences.getHistory()
        assertEquals("Duplicate tool+input must be replaced rather than duplicated", 2, history.size)
        assertEquals("Updated item should be at index 0", "awg_mm2", history[0].toolId)
        assertEquals("Safe Up: 6.00 mm² (Updated)", history[0].resultSummary)
        assertEquals("kw_hp", history[1].toolId)
    }

    @Test
    fun testAddHistoryItem_BlankIgnored() {
        preferences.addHistoryItem("tool1", "Tool 1", "", "Result 1")
        preferences.addHistoryItem("tool2", "Tool 2", "Input 2", "   ")
        preferences.addHistoryItem("tool3", "Tool 3", "   ", "   ")

        val history = preferences.getHistory()
        assertTrue("Blank input or result must not produce history items", history.isEmpty())
    }

    @Test
    fun testClearHistory() {
        preferences.addHistoryItem("tool1", "Tool 1", "Input 1", "Result 1")
        preferences.addHistoryItem("tool2", "Tool 2", "Input 2", "Result 2")
        assertFalse(preferences.getHistory().isEmpty())

        preferences.clearHistory()
        assertTrue("clearHistory must remove all items", preferences.getHistory().isEmpty())
    }

    @Test
    fun testFavorites_ToggleAndRetrieve() {
        assertFalse(preferences.isFavorite("tool_a"))
        assertTrue(preferences.toggleFavorite("tool_a"))
        assertTrue(preferences.isFavorite("tool_a"))
        assertTrue(preferences.getFavorites().contains("tool_a"))

        assertFalse(preferences.toggleFavorite("tool_a"))
        assertFalse(preferences.isFavorite("tool_a"))
    }

    @Test
    fun testThreadSafety_ConcurrentWrites() {
        val numThreads = 8
        val itemsPerThread = 10
        val executor = Executors.newFixedThreadPool(numThreads)
        val latch = CountDownLatch(numThreads)

        for (t in 0 until numThreads) {
            executor.submit {
                try {
                    for (i in 0 until itemsPerThread) {
                        preferences.addHistoryItem(
                            toolId = "thread_${t}_tool_$i",
                            toolTitle = "Thread $t Tool $i",
                            inputSummary = "In $i",
                            resultSummary = "Out $i"
                        )
                        preferences.getHistory()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(5, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("Concurrent writes must complete without deadlocks", completed)
        val history = preferences.getHistory()
        assertTrue("History must contain items", history.isNotEmpty())
        assertTrue("History must not exceed 30 items", history.size <= 30)
    }
}
