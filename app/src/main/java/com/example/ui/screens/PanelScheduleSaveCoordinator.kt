package com.example.ui.screens

import com.example.data.CircuitEntity

/**
 * Pure callback coordinator extracted from [com.example.ui.screens.PanelScheduleScreen]
 * to provide direct testability of persistence routing, panel key selection, and cleanup
 * without requiring ViewModel/Room mocks.
 */
object PanelScheduleSaveCoordinator {

    private val PANEL_TYPES = setOf("MDB", "SMDB", "DB", "MCC", "PANEL", "OTHER")

    /**
     * Computes the selectedOptionKey for a saved circuit if it has an eligible panel type.
     * Follows PanelScheduleScreen rules:
     * If panelType in [PANEL_TYPES]:
     *   returns "id_${id}" if id != 0, else "name_${circuitName}"
     * Otherwise returns null.
     */
    fun computeSelectedOptionKey(circuit: CircuitEntity): String? {
        val savedPType = circuit.panelType?.trim()?.uppercase() ?: ""
        return if (savedPType in PANEL_TYPES) {
            if (circuit.id != 0) "id_${circuit.id}" else "name_${circuit.circuitName}"
        } else {
            null
        }
    }

    /**
     * Handles save callback for [com.example.ui.screens.PanelScheduleScreen]:
     * - If [isNewCircuit] is true: invokes [onAdd] and updates selectedOptionKey if eligible.
     * - If [isNewCircuit] is false: invokes [onUpdate].
     * - In all cases: invokes [onDismiss].
     */
    fun handleSave(
        savedCircuit: CircuitEntity,
        isNewCircuit: Boolean,
        onAdd: (CircuitEntity) -> Unit,
        onUpdate: (CircuitEntity) -> Unit,
        onUpdateSelectedOptionKey: (String) -> Unit,
        onDismiss: () -> Unit
    ) {
        if (isNewCircuit) {
            onAdd(savedCircuit)
            computeSelectedOptionKey(savedCircuit)?.let { onUpdateSelectedOptionKey(it) }
        } else {
            onUpdate(savedCircuit)
        }
        onDismiss()
    }
}
