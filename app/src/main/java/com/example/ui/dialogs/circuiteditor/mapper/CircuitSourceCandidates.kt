package com.example.ui.dialogs.circuiteditor.mapper

import com.example.data.CircuitEntity

/**
 * Pure computation of the legacy parent/source option list for the Identity step.
 *
 * Reconstructs the exact legacy algorithm from CircuitEditDialog.kt:265-284:
 *
 * 1. Four fixed special sources always lead the list, in legacy order, and are never
 *    filtered by cycle logic:
 *    Transformer, Generator, Grid, ATS
 * 2. Edit mode (editingCircuitId != 0):
 *      - exclude the edited circuit itself by ID (candidate.id == editingCircuitId);
 *      - walk each candidate's parentId chain upward through the board map; exclude the
 *        candidate if any ancestor's ID equals the edited circuit's ID (selecting such a
 *        candidate as fedFrom would make the edited circuit its own direct or indirect
 *        descendant, creating a cycle).
 *    Add mode (editingCircuitId == 0): NO cycle filtering — legacy `circuit == null`
 *    branch (CircuitEditDialog.kt:277 `else existingCircuits`).
 * 3. Board eligibility (both modes): panelType trimmed + uppercased must be one of
 *    {MDB, SMDB, DB, MCC, PANEL, OTHER}, OR the circuit has children detected by
 *    parentId link or case-insensitive fedFrom name link (legacy :280-281).
 * 4. Names are emitted in existingCircuits order and deduplicated with distinct()
 *    (legacy :283). Exclusion is by ID; duplicate names follow the legacy ID/name
 *    contract (name survives via the other same-named board, emitted exactly once).
 * 5. Safety improvement explicitly mandated by the owner: the legacy parent walk
 *    (legacy :271-274) infinite-loops on pre-existing malformed parent cycles
 *    (e.g., A.parentId=B, B.parentId=A). A visited-ID set terminates the walk safely
 *    after the first revisit; ancestor exclusion is still detected before termination
 *    because the edited-circuit check runs before the visited check at every step.
 *
 * Parent matching semantics are preserved: parent resolution elsewhere uses exact
 * case-sensitive circuitName matching (CircuitSourceResolver.kt:30), so this helper
 * performs no case normalization on names and no normalization of panelType beyond
 * the legacy trim + uppercase used for eligibility only.
 */
object CircuitSourceCandidates {

    /** Fixed special sources in exact legacy order (CircuitEditDialog.kt:283). */
    val SPECIAL_SOURCES: List<String> = listOf("Transformer", "Generator", "Grid", "ATS")

    /** Legacy panel-type whitelist (CircuitEditDialog.kt:280). */
    private val LEGACY_PANEL_TYPES = setOf("MDB", "SMDB", "DB", "MCC", "PANEL", "OTHER")

    /**
     * Returns the complete fedFrom option list for the Identity step:
     * [Transformer, Generator, Grid, ATS] + eligible board names (distinct).
     */
    fun resolveCandidates(
        existingCircuits: List<CircuitEntity>,
        editingCircuitId: Int
    ): List<String> {
        val validBoards = if (editingCircuitId == 0) {
            existingCircuits
        } else {
            val boardMap = existingCircuits.associateBy { it.id }
            existingCircuits.filter { candidate ->
                if (candidate.id == editingCircuitId) return@filter false
                val visited = mutableSetOf<Int>()
                var pId = candidate.parentId
                while (pId != null) {
                    if (pId == editingCircuitId) return@filter false
                    if (!visited.add(pId)) break
                    pId = boardMap[pId]?.parentId
                }
                true
            }
        }
        val candidatePanels = validBoards.filter { c ->
            val pType = c.panelType?.trim()?.uppercase() ?: ""
            pType in LEGACY_PANEL_TYPES ||
                existingCircuits.any { child ->
                    child.parentId == c.id ||
                        child.fedFrom.trim().equals(c.circuitName.trim(), ignoreCase = true)
                }
        }
        return SPECIAL_SOURCES + candidatePanels.map { it.circuitName }.distinct()
    }
}