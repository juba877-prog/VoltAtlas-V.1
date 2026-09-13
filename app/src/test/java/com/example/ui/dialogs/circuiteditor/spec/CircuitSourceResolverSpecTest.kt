package com.example.ui.dialogs.circuiteditor.spec

import com.example.data.CircuitEntity
import com.example.ui.dialogs.circuiteditor.mapper.CircuitSourceCandidates
import com.example.ui.dialogs.circuiteditor.mapper.CircuitSourceResolver
import com.example.ui.dialogs.circuiteditor.model.TransformerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

// ============================================================================
// R1A FAIL-FIRST SPECIFICATION: CircuitSourceCandidates (legacy CircuitEditDialog.kt:265-284)
// ============================================================================
class CircuitSourceCandidatesSpecTest {

    // Editing node A (id=3):
    //   T(id=1, MDB, root)
    //   S(id=2, SMDB, parent=T)   <- sibling/valid ancestor context
    //   A(id=3, DB, parent=S)     <- the circuit being edited
    //   D(id=4, DB, parent=A)     <- direct child of A  (must be excluded)
    //   DD(id=5, MDB, parent=D)   <- deep descendant of A (must be excluded)
    //   X(id=6, SMDB, parent=T)   <- unrelated panel (must remain)
    private val tree = listOf(
        CircuitEntity(id = 1, circuitName = "MDB-RootHub", panelType = "MDB"),
        CircuitEntity(id = 2, circuitName = "SMDB-LeftArm", panelType = "SMDB", parentId = 1),
        CircuitEntity(id = 3, circuitName = "DB-CenterA", panelType = "DB", parentId = 2),
        CircuitEntity(id = 4, circuitName = "DB-ChildAY", panelType = "DB", parentId = 3),
        CircuitEntity(id = 5, circuitName = "MDB-DeepZ", panelType = "MDB", parentId = 4),
        CircuitEntity(id = 6, circuitName = "SMDB-SideX", panelType = "SMDB", parentId = 1)
    )

    // 1. The four legacy special sources are present in exact legacy order.
    @Test
    fun fail_first_01_fourSpecialSourcesPresentInExactOrder() {
        val opts = CircuitSourceCandidates.resolveCandidates(tree, editingCircuitId = 3)
        assertEquals("Transformer", opts[0])
        assertEquals("Generator", opts[1])
        assertEquals("Grid", opts[2])
        assertEquals("ATS", opts[3])
    }

    // 2. The edited circuit itself is excluded (by ID).
    @Test
    fun fail_first_02_editedCircuitExcluded() {
        val opts = CircuitSourceCandidates.resolveCandidates(tree, editingCircuitId = 3)
        assertFalse("edited circuit name must not appear", opts.contains("DB-CenterA"))
    }

    // 3. A direct child of the edited circuit is excluded (would create a cycle).
    @Test
    fun fail_first_03_directChildExcluded() {
        val opts = CircuitSourceCandidates.resolveCandidates(tree, editingCircuitId = 3)
        assertFalse("direct child must be excluded", opts.contains("DB-ChildAY"))
    }

    // 4. A deep descendant of the edited circuit is excluded.
    @Test
    fun fail_first_04_deepDescendantExcluded() {
        val opts = CircuitSourceCandidates.resolveCandidates(tree, editingCircuitId = 3)
        assertFalse("deep descendant must be excluded", opts.contains("MDB-DeepZ"))
    }

    // 5. An unrelated panel remains available.
    @Test
    fun fail_first_05_unrelatedPanelRemainsAvailable() {
        val opts = CircuitSourceCandidates.resolveCandidates(tree, editingCircuitId = 3)
        assertTrue("unrelated panel must remain available", opts.contains("SMDB-SideX"))
    }

    // 6. A valid existing parent/source remains available where legacy allows it.
    @Test
    fun fail_first_06_validExistingParentRemainsAvailable() {
        val opts = CircuitSourceCandidates.resolveCandidates(tree, editingCircuitId = 3)
        assertTrue("existing sibling panel (S) must remain available", opts.contains("SMDB-LeftArm"))
        assertTrue("root panel (T) must remain available", opts.contains("MDB-RootHub"))
    }

    // 7. A missing parent record terminates deterministically without looping.
    @Test
    fun fail_first_07_missingParentRecordTerminatesSafely() {
        val broken = listOf(
            CircuitEntity(id = 1, circuitName = "MDB-Core", panelType = "MDB"),
            CircuitEntity(id = 2, circuitName = "DB-Orphan", panelType = "DB", parentId = 999)
        )
        val opts = CircuitSourceCandidates.resolveCandidates(broken, editingCircuitId = 1)
        assertTrue("candidate with missing parent record must remain", opts.contains("DB-Orphan"))
    }

    // 8. A pre-existing malformed parent cycle terminates safely and does not offer an unsafe candidate.
    @Test
    fun fail_first_08_malformedCycleTerminatesSafely() {
        val malformed = listOf(
            // id=1 <-> id=2 parent cycle (pre-existing corruption in the data)
            CircuitEntity(id = 1, circuitName = "MDB-LoopOne", panelType = "MDB", parentId = 2),
            CircuitEntity(id = 2, circuitName = "SMDB-LoopTwo", panelType = "SMDB", parentId = 1),
            // Editing id=3.
            CircuitEntity(id = 3, circuitName = "DB-Victim", panelType = "DB", parentId = 1)
        )
        // Candidate id=4 is parented under id=2 which sits inside the cycle; the walk from 4
        // must terminate on the visited set after reaching the pre-existing cycle, and must not
        // exclude valid candidates simply because the data contains a malformed cycle elsewhere.
        val withSafeCandidate = malformed + CircuitEntity(id = 4, circuitName = "DB-InLoopChild", panelType = "DB", parentId = 2)

        val opts = CircuitSourceCandidates.resolveCandidates(withSafeCandidate, editingCircuitId = 3)
        assertFalse("edited circuit itself must be excluded", opts.contains("DB-Victim"))
        assertTrue("valid candidate must remain available even with a malformed cycle elsewhere",
            opts.contains("DB-InLoopChild"))
        assertTrue("four special sources remain first and unaffected", opts[0] == "Transformer")
    }

    // 9. Duplicate panel names are handled by the legacy ID/name contract:
    //    exclusion is by ID; names are deduplicated via distinct().
    @Test
    fun fail_first_09_duplicatePanelNamesFollowLegacyIdNameContract() {
        val dupes = listOf(
            CircuitEntity(id = 1, circuitName = "MDB-Parent", panelType = "MDB"),
            CircuitEntity(id = 3, circuitName = "DB-Shared", panelType = "DB", parentId = 1),
            CircuitEntity(id = 7, circuitName = "DB-Shared", panelType = "DB", parentId = 1)
        )
        val opts = CircuitSourceCandidates.resolveCandidates(dupes, editingCircuitId = 3)
        // The edited circuit (id=3, name DB-Shared) is excluded by ID; the other (id=7)
        // shares the name so the name still appears, deduplicated to exactly one occurrence.
        assertEquals("same-named other board emitted exactly once", 1, opts.filter { it == "DB-Shared" }.size)
    }

    // 10. Add mode (editingCircuitId == 0) performs NO cycle filtering, matching legacy
    //     parentOptions with circuit == null (CircuitEditDialog.kt:277 "else existingCircuits").
    @Test
    fun fail_first_10_addModeOffersAllBoards() {
        val opts = CircuitSourceCandidates.resolveCandidates(tree, editingCircuitId = 0)
        assertTrue("add mode must offer all boards", opts.contains("DB-CenterA"))
        assertTrue("add mode must offer the root", opts.contains("MDB-RootHub"))
        assertTrue("add mode must offer the unrelated panel", opts.contains("SMDB-SideX"))
    }
}
