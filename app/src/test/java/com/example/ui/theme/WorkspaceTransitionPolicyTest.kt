package com.example.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 8 Slice 8B — screen/workspace transition policy tests.
 *
 * Proves deterministic direction mapping, reduced-motion degradation, and
 * that emphasized (workspace entry) timing remains distinct from routine
 * (tab) timing.
 */
class WorkspaceTransitionPolicyTest {

    // ── Direction policy ────────────────────────────────────────────────────

    @Test
    fun testForwardNavigationWhenDestinationIsDeeper() {
        assertEquals(
            VoltAtlasMotion.NavigationDirection.Forward,
            VoltAtlasMotion.navigationDirection(0, 1)
        )
        assertEquals(
            VoltAtlasMotion.NavigationDirection.Forward,
            VoltAtlasMotion.navigationDirection(9, 12)
        )
    }

    @Test
    fun testBackwardNavigationOnReturn() {
        assertEquals(
            VoltAtlasMotion.NavigationDirection.Backward,
            VoltAtlasMotion.navigationDirection(1, 0)
        )
        assertEquals(
            VoltAtlasMotion.NavigationDirection.Backward,
            VoltAtlasMotion.navigationDirection(12, 9)
        )
    }

    @Test
    fun testSameIndexNavigationTreatedAsForward() {
        // Re-selecting the current destination must not flip direction.
        assertEquals(
            VoltAtlasMotion.NavigationDirection.Forward,
            VoltAtlasMotion.navigationDirection(3, 3)
        )
    }

    // ── Reduced motion path ─────────────────────────────────────────────────

    @Test
    fun testReducedMotionDegradesToMinimalFadeForTabs() {
        val reduced = VoltAtlasMotion.tabContentTransform(
            VoltAtlasMotion.NavigationDirection.Forward,
            reduceMotion = true
        )
        // Exactly the centralized minimal-fade pair — no slide, no scale.
        assertSame(VoltAtlasMotion.tabReducedMotionTransition, reduced)
    }

    @Test
    fun testReducedMotionDegradesToMinimalFadeForWorkspaceEntry() {
        val reduced = VoltAtlasMotion.workspaceEntryTransform(
            VoltAtlasMotion.NavigationDirection.Backward,
            reduceMotion = true
        )
        assertSame(VoltAtlasMotion.tabReducedMotionTransition, reduced)
    }

    @Test
    fun testForwardAndBackwardTabTransformsAreBothProduced() {
        // Both directions must produce real transitions in normal motion
        // (never null / never the reduced instance).
        val fwd = VoltAtlasMotion.tabContentTransform(
            VoltAtlasMotion.NavigationDirection.Forward,
            reduceMotion = false
        )
        val back = VoltAtlasMotion.tabContentTransform(
            VoltAtlasMotion.NavigationDirection.Backward,
            reduceMotion = false
        )
        assertTrue(fwd != VoltAtlasMotion.tabReducedMotionTransition)
        assertTrue(back != VoltAtlasMotion.tabReducedMotionTransition)
    }

    // ── Emphasized vs standard timing separation ────────────────────────────

    @Test
    fun testWorkspaceEntryUsesEmphasizedDurationDistinctFromTabs() {
        // Workspace entry is an approved emphasized moment; tabs are standard.
        assertEquals(VoltAtlasMotion.DurationEmphasized, VoltAtlasMotion.WorkspaceEntryDurationMillis)
        assertTrue(VoltAtlasMotion.WorkspaceEntryDurationMillis > VoltAtlasMotion.StandardSpatial.DurationMillis)
    }
}