package com.example.ui

import com.example.data.AtsEntity
import com.example.data.GeneratorEntity
import com.example.data.UpsEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests verifying:
 * 1. Selection migrates to an adjacent neighbor on deletion (by list position, not jumping to first).
 * 2. Selection clears to empty/null on delete-to-zero.
 * 3. Delete is available down to zero instances.
 * All tests call the shared production pure function [resolveEquipmentSelection] directly.
 */
class PowerSystemsDeleteToZeroTest {

    @Test
    fun testGeneratorMiddleDeletionMigratesToAdjacentNeighbor() {
        val idA = "synth-gen-alpha"
        val idB = "synth-gen-beta"
        val idC = "synth-gen-gamma"
        val initialList = listOf(idA, idB, idC)
        val selectedId = idB
        val lastActiveId = idB

        // Delete middle item idB; remaining list retains relative order
        val remainingList = listOf(idA, idC)

        val resolution = resolveEquipmentSelection(
            currentIds = remainingList,
            previousIds = initialList,
            selectedId = selectedId,
            lastActiveId = lastActiveId
        )

        // The slot formerly occupied by index 1 (idB) is now occupied by index 1 (idC)
        assertEquals("Selection must move to adjacent neighbor at same index", idC, resolution.updatedSelectedId)
        assertEquals("Active ID must resolve to adjacent neighbor", idC, resolution.activeId)
    }

    @Test
    fun testGeneratorLastDeletionMigratesToPrecedingNeighbor() {
        val idA = "synth-gen-alpha"
        val idB = "synth-gen-beta"
        val idC = "synth-gen-gamma"
        val initialList = listOf(idA, idB, idC)
        val selectedId = idC
        val lastActiveId = idC

        // Delete last item idC
        val remainingList = listOf(idA, idB)

        val resolution = resolveEquipmentSelection(
            currentIds = remainingList,
            previousIds = initialList,
            selectedId = selectedId,
            lastActiveId = lastActiveId
        )

        // Deleting the end item must clamp index to remainingList.lastIndex (idB), not jump to index 0
        assertEquals("Selection must move to preceding neighbor", idB, resolution.updatedSelectedId)
        assertEquals("Active ID must resolve to preceding neighbor", idB, resolution.activeId)
    }

    @Test
    fun testGeneratorDeleteToZeroClearsSelectionAndResolvesToNull() {
        val idA = "synth-gen-sole"
        val initialList = listOf(idA)
        val selectedId = idA
        val lastActiveId = idA

        val remainingList = emptyList<String>()

        val resolution = resolveEquipmentSelection(
            currentIds = remainingList,
            previousIds = initialList,
            selectedId = selectedId,
            lastActiveId = lastActiveId
        )

        assertEquals("Persisted selection must be cleared to empty string", "", resolution.updatedSelectedId)
        assertNull("Active identifier must resolve to null on zero collection", resolution.activeId)
    }

    @Test
    fun testAtsMiddleDeletionMigratesToAdjacentNeighbor() {
        val id1 = "synth-ats-101"
        val id2 = "synth-ats-102"
        val id3 = "synth-ats-103"
        val initialList = listOf(id1, id2, id3)
        val selectedId = id2
        val lastActiveId = id2

        val remainingList = listOf(id1, id3)

        val resolution = resolveEquipmentSelection(
            currentIds = remainingList,
            previousIds = initialList,
            selectedId = selectedId,
            lastActiveId = lastActiveId
        )

        assertEquals("ATS selection must move to adjacent neighbor", id3, resolution.updatedSelectedId)
        assertEquals("Active ATS ID must resolve to adjacent neighbor", id3, resolution.activeId)
    }

    @Test
    fun testAtsDeleteToZeroClearsSelectionAndResolvesToNull() {
        val id1 = "synth-ats-sole"
        val initialList = listOf(id1)
        val selectedId = id1
        val lastActiveId = id1

        val remainingList = emptyList<String>()

        val resolution = resolveEquipmentSelection(
            currentIds = remainingList,
            previousIds = initialList,
            selectedId = selectedId,
            lastActiveId = lastActiveId
        )

        assertEquals("Persisted ATS selection must be cleared to empty string", "", resolution.updatedSelectedId)
        assertNull("Active ATS identifier must resolve to null on zero collection", resolution.activeId)
    }

    @Test
    fun testUpsMiddleDeletionMigratesToAdjacentNeighbor() {
        val id1 = "synth-ups-east"
        val id2 = "synth-ups-central"
        val id3 = "synth-ups-west"
        val initialList = listOf(id1, id2, id3)
        val selectedId = id2
        val lastActiveId = id2

        val remainingList = listOf(id1, id3)

        val resolution = resolveEquipmentSelection(
            currentIds = remainingList,
            previousIds = initialList,
            selectedId = selectedId,
            lastActiveId = lastActiveId
        )

        assertEquals("UPS selection must move to adjacent neighbor", id3, resolution.updatedSelectedId)
        assertEquals("Active UPS ID must resolve to adjacent neighbor", id3, resolution.activeId)
    }

    @Test
    fun testUpsDeleteToZeroClearsSelectionAndResolvesToNull() {
        val id1 = "synth-ups-sole"
        val initialList = listOf(id1)
        val selectedId = id1
        val lastActiveId = id1

        val remainingList = emptyList<String>()

        val resolution = resolveEquipmentSelection(
            currentIds = remainingList,
            previousIds = initialList,
            selectedId = selectedId,
            lastActiveId = lastActiveId
        )

        assertEquals("Persisted UPS selection must be cleared to empty string", "", resolution.updatedSelectedId)
        assertNull("Active UPS identifier must resolve to null on zero collection", resolution.activeId)
    }

    @Test
    fun testReAddAfterDeleteToZeroResolvesCleanNewIdentifier() {
        val selectedId = ""
        val newId = "synth-newly-added"
        val reAddedList = listOf(newId)

        val resolution = resolveEquipmentSelection(
            currentIds = reAddedList,
            previousIds = emptyList(),
            selectedId = selectedId,
            lastActiveId = ""
        )

        assertEquals("Selection remains empty until user explicitly clicks chip", "", resolution.updatedSelectedId)
        assertEquals("Active ID resolves to the first element of the new collection", newId, resolution.activeId)
    }

    @Test
    fun testGenericMultiInstanceAdjacencyMigrationByListPosition() {
        // Generic fixture with 4 synthetic instances to verify strict positional adjacency
        val items = listOf("device-001", "device-002", "device-003", "device-004")

        // 1. Delete middle element index 1 ("device-002") -> should migrate to element now at index 1 ("device-003")
        val afterDel1 = listOf(items[0], items[2], items[3])
        val res1 = resolveEquipmentSelection(
            currentIds = afterDel1,
            previousIds = items,
            selectedId = items[1],
            lastActiveId = items[1]
        )
        assertEquals("Must select item now occupying the deleted index position", items[2], res1.updatedSelectedId)
        assertEquals(items[2], res1.activeId)

        // 2. Delete middle element index 2 ("device-003") from afterDel1 -> should migrate to element now at index 2 ("device-004")
        val afterDel2 = listOf(items[0], items[3])
        val res2 = resolveEquipmentSelection(
            currentIds = afterDel2,
            previousIds = afterDel1,
            selectedId = items[2],
            lastActiveId = items[2]
        )
        assertEquals("Must select item now occupying index 1 (clamped to remaining lastIndex)", items[3], res2.updatedSelectedId)
        assertEquals(items[3], res2.activeId)

        // 3. Delete end element ("device-004") from afterDel2 -> should migrate to preceding element ("device-001")
        val afterDel3 = listOf(items[0])
        val res3 = resolveEquipmentSelection(
            currentIds = afterDel3,
            previousIds = afterDel2,
            selectedId = items[3],
            lastActiveId = items[3]
        )
        assertEquals("Must select preceding item when end element is deleted", items[0], res3.updatedSelectedId)
        assertEquals(items[0], res3.activeId)
    }
}
