package com.example.ui.dialogs.circuiteditor.spec

import com.example.data.CircuitEntity
import com.example.ui.dialogs.circuiteditor.mapper.CircuitSourceResolver
import com.example.ui.dialogs.circuiteditor.model.TransformerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure JVM specification tests for [CircuitSourceResolver].
 *
 * Evidence Label: NEW_MAPPER_SPECIFIED
 */
class CircuitSourceResolverSpecTest {

    private val transformers = listOf(
        TransformerSnapshot(transformerId = "TR-01", ratingKVA = 1000.0),
        TransformerSnapshot(transformerId = "TR-02", ratingKVA = 1600.0)
    )

    private val existingCircuits = listOf(
        CircuitEntity(id = 10, circuitName = "MDB-1", level = 0, transformerId = "TR-01"),
        CircuitEntity(id = 20, circuitName = "SMDB-East", level = 1, transformerId = "TR-01")
    )

    @Test
    fun test01_fedFromTransformer_resolution() {
        val res = CircuitSourceResolver.resolveSourceHierarchy(
            fedFrom = "Transformer",
            draftTransformerId = "",
            existingCircuits = existingCircuits,
            availableTransformers = transformers
        )

        assertEquals("TR-01", res.finalTxId)
        assertNull(res.calculatedParentId)
        assertEquals(0, res.calculatedLevel)
        assertEquals("TRANSFORMER", res.calculatedSourceType)
    }

    @Test
    fun test02_fedFromFixedSources_generator_grid_ats() {
        val gen = CircuitSourceResolver.resolveSourceHierarchy("Generator", "", existingCircuits, transformers)
        assertEquals("GENERATOR", gen.calculatedSourceType)
        assertEquals(0, gen.calculatedLevel)
        assertNull(gen.calculatedParentId)

        val grid = CircuitSourceResolver.resolveSourceHierarchy("Grid", "", existingCircuits, transformers)
        assertEquals("GRID", grid.calculatedSourceType)

        val utilityGrid = CircuitSourceResolver.resolveSourceHierarchy("Utility Grid", "", existingCircuits, transformers)
        assertEquals("GRID", utilityGrid.calculatedSourceType)

        val ats = CircuitSourceResolver.resolveSourceHierarchy("ATS", "", existingCircuits, transformers)
        assertEquals("ATS", ats.calculatedSourceType)
    }

    @Test
    fun test03_fedFromParentBoard_levelAndTransformerInheritance() {
        val res = CircuitSourceResolver.resolveSourceHierarchy(
            fedFrom = "SMDB-East",
            draftTransformerId = "",
            existingCircuits = existingCircuits,
            availableTransformers = transformers
        )

        assertEquals(20, res.calculatedParentId)
        assertEquals(2, res.calculatedLevel) // parent level 1 + 1 = 2
        assertEquals("PANEL", res.calculatedSourceType)
        assertEquals("TR-01", res.finalTxId) // inherited from parent
    }

    @Test
    fun test04_parentMatchCaseSensitivity() {
        // In legacy line 1070: existingCircuits.firstOrNull { it.circuitName == fedFrom }
        // Case mismatch does NOT match parentBoardObj
        val res = CircuitSourceResolver.resolveSourceHierarchy(
            fedFrom = "smdb-east",
            draftTransformerId = "TR-FALLBACK",
            existingCircuits = existingCircuits,
            availableTransformers = transformers
        )

        assertNull(res.parentBoardObj)
        assertNull(res.calculatedParentId)
        assertEquals(0, res.calculatedLevel)
        assertEquals("TRANSFORMER", res.calculatedSourceType)
        assertEquals("TR-FALLBACK", res.finalTxId)
    }
}
