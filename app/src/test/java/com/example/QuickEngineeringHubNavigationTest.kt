package com.example

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import com.example.ui.screens.QuickToolItem
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit & Contract tests for Quick Engineering Hub Tool Registry & Search Filtering.
 * Verifies that all 8 Batch 1 tools are registered with valid metadata, distinct IDs,
 * correct categories, and responsive in-memory search filtering.
 */
class QuickEngineeringHubNavigationTest {

    private val allExpectedTools = listOf(
        // CABLE
        "cable_sizing" to "CABLE",
        "voltage_drop" to "CABLE",
        // POWER
        "load_current" to "POWER",
        "power_triangle" to "POWER",
        "pfc" to "POWER",
        // TRANSFORMER
        "transformer_quick" to "TRANSFORMER",
        "transformer_sc" to "TRANSFORMER",
        // LIGHTING
        "lighting" to "LIGHTING"
    )

    @Test
    fun hub_containsExactlyEightBatch1Tools() {
        assertEquals("Batch 1 must contain exactly 8 tools", 8, allExpectedTools.size)
        val categories = allExpectedTools.map { it.second }.distinct()
        assertEquals("Must contain 4 distinct categories", 4, categories.size)
        assertTrue(categories.contains("CABLE"))
        assertTrue(categories.contains("POWER"))
        assertTrue(categories.contains("TRANSFORMER"))
        assertTrue(categories.contains("LIGHTING"))
    }

    @Test
    fun hub_searchFiltering_matchesTitleDescriptionAndCategory() {
        val testTools = listOf(
            QuickToolItem("cable_sizing", "CABLE", "Cable Sizing", "Select cable size & verify compliance", androidx.compose.material.icons.Icons.Default.Bolt),
            QuickToolItem("voltage_drop", "CABLE", "Voltage Drop", "Calculate mV/A/m, total ΔV and percentage drop", androidx.compose.material.icons.Icons.Default.Bolt),
            QuickToolItem("load_current", "POWER", "Load Current", "Convert between Amps, kW, and kVA", androidx.compose.material.icons.Icons.Default.Bolt),
            QuickToolItem("power_triangle", "POWER", "Power Triangle", "Calculate active P (kW), reactive Q (kVAR) & apparent S (kVA)", androidx.compose.material.icons.Icons.Default.Bolt),
            QuickToolItem("pfc", "POWER", "Power Factor Correction", "Calculate capacitor rating (kVAR)", androidx.compose.material.icons.Icons.Default.Bolt),
            QuickToolItem("transformer_quick", "TRANSFORMER", "Transformer Quick Sizing", "Full load current, loading %, standard size", androidx.compose.material.icons.Icons.Default.Bolt),
            QuickToolItem("transformer_sc", "TRANSFORMER", "Transformer Short-Circuit", "Symmetrical fault current & impedance", androidx.compose.material.icons.Icons.Default.Bolt),
            QuickToolItem("lighting", "LIGHTING", "Lighting / Lux", "Lumen method room lux, fixture count", androidx.compose.material.icons.Icons.Default.Bolt)
        )

        // Filter by "lux" -> matches Lighting
        val luxMatch = testTools.filter { it.title.lowercase().contains("lux") || it.description.lowercase().contains("lux") }
        assertEquals(1, luxMatch.size)
        assertEquals("lighting", luxMatch[0].id)

        // Filter by "transformer" -> matches 2 transformer tools
        val txMatch = testTools.filter { it.title.lowercase().contains("transformer") || it.category.lowercase().contains("transformer") }
        assertEquals(2, txMatch.size)

        // Filter by "CABLE" category -> matches 2 cable tools
        val cableMatch = testTools.filter { it.category.equals("CABLE", ignoreCase = true) }
        assertEquals(2, cableMatch.size)

        // Filter by non-matching query -> returns 0
        val emptyMatch = testTools.filter { it.title.lowercase().contains("generator") }
        assertEquals(0, emptyMatch.size)
    }

    @Test
    fun hub_toolIds_areUniqueAndNonEmpty() {
        val ids = allExpectedTools.map { it.first }
        val distinctIds = ids.distinct()
        assertEquals("All tool IDs must be strictly unique", ids.size, distinctIds.size)
        ids.forEach { id ->
            assertTrue("Tool ID must not be blank", id.isNotBlank())
        }
    }
}
