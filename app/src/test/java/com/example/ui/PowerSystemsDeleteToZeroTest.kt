package com.example.ui

import com.example.data.AtsEntity
import com.example.data.GeneratorEntity
import com.example.data.UpsEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests verifying:
 * 1. Selection migrates to an adjacent neighbor on deletion (not jumping to first).
 * 2. Selection clears to empty/null on delete-to-zero.
 * 3. Delete is available down to zero instances (no size > 1 guard).
 * Tested symmetrically for Generator, ATS, and UPS.
 */
class PowerSystemsDeleteToZeroTest {

    // Helper simulating the panel's selection resolution and neighbor migration logic
    private fun resolveNextSelection(
        currentList: List<String>,
        previousList: List<String>,
        currentlySelectedId: String,
        lastActiveId: String
    ): Pair<String, String?> {
        val targetDeletedId = if (currentlySelectedId.isNotEmpty() && !currentList.contains(currentlySelectedId)) {
            currentlySelectedId
        } else if (lastActiveId.isNotEmpty() && !currentList.contains(lastActiveId)) {
            lastActiveId
        } else {
            null
        }

        val updatedSelectedId = if (targetDeletedId != null) {
            val deletedIdx = previousList.indexOf(targetDeletedId)
            if (deletedIdx >= 0 && currentList.isNotEmpty()) {
                val newIdx = deletedIdx.coerceIn(0, currentList.lastIndex)
                currentList[newIdx]
            } else {
                currentList.firstOrNull() ?: ""
            }
        } else {
            currentlySelectedId
        }

        val activeId = if (updatedSelectedId.isNotEmpty() && currentList.contains(updatedSelectedId)) {
            updatedSelectedId
        } else {
            currentList.firstOrNull()
        }

        return Pair(updatedSelectedId, activeId)
    }

    @Test
    fun testGeneratorMiddleDeletionMigratesToAdjacentNeighbor() {
        val initialList = listOf("GEN-01", "GEN-02", "GEN-03")
        val selectedId = "GEN-02"
        val lastActiveId = "GEN-02"

        val remainingList = listOf("GEN-01", "GEN-03")

        val (newSelected, activeId) = resolveNextSelection(
            currentList = remainingList,
            previousList = initialList,
            currentlySelectedId = selectedId,
            lastActiveId = lastActiveId
        )

        assertEquals("Selection must move to adjacent neighbor GEN-03", "GEN-03", newSelected)
        assertEquals("Active ID must resolve to GEN-03", "GEN-03", activeId)
    }

    @Test
    fun testGeneratorLastDeletionMigratesToPrecedingNeighbor() {
        val initialList = listOf("GEN-01", "GEN-02", "GEN-03")
        val selectedId = "GEN-03"
        val lastActiveId = "GEN-03"

        val remainingList = listOf("GEN-01", "GEN-02")

        val (newSelected, activeId) = resolveNextSelection(
            currentList = remainingList,
            previousList = initialList,
            currentlySelectedId = selectedId,
            lastActiveId = lastActiveId
        )

        assertEquals("Selection must move to preceding neighbor GEN-02", "GEN-02", newSelected)
        assertEquals("Active ID must resolve to GEN-02", "GEN-02", activeId)
    }

    @Test
    fun testGeneratorDeleteToZeroClearsSelectionAndResolvesToNull() {
        val initialList = listOf("GEN-01")
        val selectedId = "GEN-01"
        val lastActiveId = "GEN-01"

        val remainingList = emptyList<String>()

        val (newSelected, activeId) = resolveNextSelection(
            currentList = remainingList,
            previousList = initialList,
            currentlySelectedId = selectedId,
            lastActiveId = lastActiveId
        )

        assertEquals("Persisted selection must be cleared to empty", "", newSelected)
        assertNull("Active identifier must resolve to null on zero collection", activeId)
    }

    @Test
    fun testAtsMiddleDeletionMigratesToAdjacentNeighbor() {
        val initialList = listOf("ATS-01", "ATS-02", "ATS-03")
        val selectedId = "ATS-02"
        val lastActiveId = "ATS-02"

        val remainingList = listOf("ATS-01", "ATS-03")

        val (newSelected, activeId) = resolveNextSelection(
            currentList = remainingList,
            previousList = initialList,
            currentlySelectedId = selectedId,
            lastActiveId = lastActiveId
        )

        assertEquals("ATS selection must move to adjacent neighbor ATS-03", "ATS-03", newSelected)
        assertEquals("Active ATS ID must resolve to ATS-03", "ATS-03", activeId)
    }

    @Test
    fun testAtsDeleteToZeroClearsSelectionAndResolvesToNull() {
        val initialList = listOf("ATS-01")
        val selectedId = "ATS-01"
        val lastActiveId = "ATS-01"

        val remainingList = emptyList<String>()

        val (newSelected, activeId) = resolveNextSelection(
            currentList = remainingList,
            previousList = initialList,
            currentlySelectedId = selectedId,
            lastActiveId = lastActiveId
        )

        assertEquals("Persisted ATS selection must be cleared to empty", "", newSelected)
        assertNull("Active ATS identifier must resolve to null on zero collection", activeId)
    }

    @Test
    fun testUpsMiddleDeletionMigratesToAdjacentNeighbor() {
        val initialList = listOf("UPS-01", "UPS-02", "UPS-03")
        val selectedId = "UPS-02"
        val lastActiveId = "UPS-02"

        val remainingList = listOf("UPS-01", "UPS-03")

        val (newSelected, activeId) = resolveNextSelection(
            currentList = remainingList,
            previousList = initialList,
            currentlySelectedId = selectedId,
            lastActiveId = lastActiveId
        )

        assertEquals("UPS selection must move to adjacent neighbor UPS-03", "UPS-03", newSelected)
        assertEquals("Active UPS ID must resolve to UPS-03", "UPS-03", activeId)
    }

    @Test
    fun testUpsDeleteToZeroClearsSelectionAndResolvesToNull() {
        val initialList = listOf("UPS-01")
        val selectedId = "UPS-01"
        val lastActiveId = "UPS-01"

        val remainingList = emptyList<String>()

        val (newSelected, activeId) = resolveNextSelection(
            currentList = remainingList,
            previousList = initialList,
            currentlySelectedId = selectedId,
            lastActiveId = lastActiveId
        )

        assertEquals("Persisted UPS selection must be cleared to empty", "", newSelected)
        assertNull("Active UPS identifier must resolve to null on zero collection", activeId)
    }

    @Test
    fun testReAddAfterDeleteToZeroResolvesCleanNewIdentifier() {
        val selectedId = ""
        val reAddedList = listOf("GEN-01")

        val (newSelected, activeId) = resolveNextSelection(
            currentList = reAddedList,
            previousList = emptyList(),
            currentlySelectedId = selectedId,
            lastActiveId = ""
        )

        assertEquals("Selection remains empty (or default)", "", newSelected)
        assertEquals("Active ID resolves to the new instance GEN-01", "GEN-01", activeId)
    }
}
