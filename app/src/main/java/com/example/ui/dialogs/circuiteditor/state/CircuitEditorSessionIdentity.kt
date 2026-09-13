package com.example.ui.dialogs.circuiteditor.state

import com.example.data.CircuitEntity

/**
 * Distinguishes the logical editor session so that draft/workflow state is never reused
 * across sessions.
 *
 * Origins (all reachable with the current unchanged public API):
 *  - [CircuitEditorSessionOrigin.EDIT]: existing circuit edit (circuit != null, id != 0).
 *    Identified by ID + trimmed name + project, so replacing circuit A with circuit B
 *    while a composition is retained yields a different identity and reinitializes.
 *  - [CircuitEditorSessionOrigin.ADD_NULL]: CircuitsGrid Add (circuit == null).
 *  - [CircuitEditorSessionOrigin.ADD_STUB_ZERO]: PanelSchedule Add stub (circuit != null
 *    with id == 0). Identified additionally by trimmed name + project.
 *
 * Null and zero-ID Add sessions cannot collide with each other (distinct origins) and
 * cannot leak into a later same-kind Add session with the current callers: both callers
 * remove the dialog from composition on dismissal (CircuitsGridScreen sets showDialog =
 * false; PanelScheduleScreen sets circuitToEditInDialog = null), which disposes the
 * saveable state registry entries for the session. Within a retained composition an
 * equal-stub swap (same name/project/id) would map to an equal identity; current callers
 * never perform such a swap without first leaving composition.
 */
enum class CircuitEditorSessionOrigin {
    EDIT,
    ADD_NULL,
    ADD_STUB_ZERO
}

data class CircuitEditorSessionIdentity(
    val origin: CircuitEditorSessionOrigin,
    val circuitId: Int,
    val circuitName: String,
    val projectId: String
) {
    companion object {
        fun forCircuit(circuit: CircuitEntity?): CircuitEditorSessionIdentity = when {
            circuit == null ->
                CircuitEditorSessionIdentity(
                    origin = CircuitEditorSessionOrigin.ADD_NULL,
                    circuitId = 0,
                    circuitName = "",
                    projectId = ""
                )
            circuit.id == 0 ->
                CircuitEditorSessionIdentity(
                    origin = CircuitEditorSessionOrigin.ADD_STUB_ZERO,
                    circuitId = 0,
                    circuitName = circuit.circuitName.trim(),
                    projectId = circuit.projectId
                )
            else ->
                CircuitEditorSessionIdentity(
                    origin = CircuitEditorSessionOrigin.EDIT,
                    circuitId = circuit.id,
                    circuitName = circuit.circuitName.trim(),
                    projectId = circuit.projectId
                )
        }
    }
}