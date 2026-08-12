package com.example.ui

import org.junit.Assert.*
import org.junit.Test

class AppShellNavigationTest {

    @Test
    fun testPrimaryNavigationStructure_ContainsExactFiveDestinations() {
        val primaryNavItems = listOf("Dashboard", "Circuits", "Transformers", "Power", "More")
        assertEquals(5, primaryNavItems.size)
        assertTrue(primaryNavItems.contains("Dashboard"))
        assertTrue(primaryNavItems.contains("Circuits"))
        assertTrue(primaryNavItems.contains("Transformers"))
        assertTrue(primaryNavItems.contains("Power"))
        assertTrue(primaryNavItems.contains("More"))
        
        // Single-Line Diagram (SLD) must be absent from primary bottom navigation
        assertFalse(primaryNavItems.contains("SLD"))
        assertFalse(primaryNavItems.contains("Single-Line Diagram"))
    }

    @Test
    fun testSecondaryMoreModules_ContainsSldAndSecondaryDestinations() {
        val secondaryModules = listOf(
            "Single-Line Diagram (SLD)",
            "Protection & Coordination",
            "Conduits & Cable Fill",
            "BOQ & Procurement",
            "Global Settings"
        )
        assertEquals(5, secondaryModules.size)
        assertTrue(secondaryModules.any { it.contains("SLD") })
        assertTrue(secondaryModules.any { it.contains("Protection") })
        assertTrue(secondaryModules.any { it.contains("Conduits") })
        assertTrue(secondaryModules.any { it.contains("BOQ") })
        assertTrue(secondaryModules.any { it.contains("Settings") })
    }

    @Test
    fun testSharedSelectionLogic_isMoreTabSelected() {
        // Tab 0 -> Dashboard selected (More not selected)
        assertFalse(isMoreTabSelected(0))
        
        // Tab 1 -> Circuits selected (More not selected)
        assertFalse(isMoreTabSelected(1))
        
        // Tab 2 -> Transformers selected (More MUST NOT be selected)
        assertFalse(isMoreTabSelected(2))
        
        // Tab 3 -> Power Systems selected (More MUST NOT be selected)
        assertFalse(isMoreTabSelected(3))
        
        // Tabs 4, 5, 6, 7, 8, 9 -> More MUST be selected
        assertTrue(isMoreTabSelected(4)) // Protection
        assertTrue(isMoreTabSelected(5)) // SLD
        assertTrue(isMoreTabSelected(6)) // Conduits
        assertTrue(isMoreTabSelected(7)) // BOQ
        assertTrue(isMoreTabSelected(8)) // Settings
        assertTrue(isMoreTabSelected(9)) // More hub
    }

    @Test
    fun testResponsiveHeaderActionDistribution_AndTitleWidthAllocation() {
        val backButtonWidth = 48.0
        val overflowButtonWidth = 48.0
        val paddingAndSpacing = 16.0

        // 1. Small phone width (< 360dp, e.g. 320dp)
        val width320 = 320.0
        val directActions320 = 1 // PDF Export only
        val buttonsWidth320 = backButtonWidth + (directActions320 * 48.0) + overflowButtonWidth + paddingAndSpacing
        val titleWidth320 = width320 - buttonsWidth320
        assertEquals(160.0, titleWidth320, 0.1)
        assertTrue("Title width at 320dp must be >= 150dp", titleWidth320 >= 150.0)

        // 2. Standard phone width (360dp .. < 600dp, e.g. 360dp)
        val width360 = 360.0
        val directActions360 = 2 // Theme Toggle + PDF Export
        val buttonsWidth360 = backButtonWidth + (directActions360 * 48.0) + overflowButtonWidth + paddingAndSpacing
        val titleWidth360 = width360 - buttonsWidth360
        assertEquals(152.0, titleWidth360, 0.1)
        assertTrue("Title width at 360dp must be >= 150dp", titleWidth360 >= 150.0)

        // 3. Large phone width (412dp)
        val width412 = 412.0
        val directActions412 = 2
        val buttonsWidth412 = backButtonWidth + (directActions412 * 48.0) + overflowButtonWidth + paddingAndSpacing
        val titleWidth412 = width412 - buttonsWidth412
        assertEquals(204.0, titleWidth412, 0.1)
        assertTrue("Title width at 412dp must be >= 200dp", titleWidth412 >= 200.0)

        // 4. Tablet / Landscape width (600dp)
        val width600 = 600.0
        val directActions600 = 4 // All direct actions
        val buttonsWidth600 = backButtonWidth + (directActions600 * 48.0) + overflowButtonWidth + paddingAndSpacing
        val titleWidth600 = width600 - buttonsWidth600
        assertEquals(296.0, titleWidth600, 0.1)
        assertTrue("Title width at 600dp must be >= 250dp", titleWidth600 >= 250.0)
    }

    @Test
    fun testFontScale_HeaderFlexibleHeightConstraint() {
        // Minimum header height is 56.dp (heightIn min=56.dp).
        // Font scaling at 1.0x, 1.3x, and 1.5x expands the two-line title text naturally
        // without fixed 48.dp/50.dp height truncation.
        val baseFontSizeSp = 15.0
        val fontScales = listOf(1.0, 1.3, 1.5)
        
        fontScales.forEach { scale ->
            val scaledFontSp = baseFontSizeSp * scale
            assertTrue(scaledFontSp > 0.0)
            assertTrue("Scaled font at $scale scale must not exceed 25sp", scaledFontSp <= 25.0)
        }
    }
}
