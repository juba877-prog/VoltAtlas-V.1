package com.example.ui.screens.circuits

import com.example.data.CircuitEntity

/**
 * Pure callback coordinator extracted from [com.example.ui.screens.CircuitsGridScreen]
 * to provide direct testability of persistence routing and override invalidation logic
 * without requiring ViewModel/Room mocks.
 */
object CircuitsGridSaveCoordinator {

    /**
     * Normalizes [saved] against [original].
     * If the manufacturer or cable type has changed, [CircuitEntity.overrideCableSize] is cleared (null).
     * Otherwise, existing [CircuitEntity.overrideCableSize] is preserved.
     */
    fun normalize(original: CircuitEntity?, saved: CircuitEntity): CircuitEntity {
        return if (
            original != null &&
            (!original.manufacturer.equals(saved.manufacturer, ignoreCase = true) ||
                !original.cableType.equals(saved.cableType, ignoreCase = true))
        ) {
            saved.copy(overrideCableSize = null)
        } else {
            saved
        }
    }

    /**
     * Handles save callback for [com.example.ui.screens.CircuitsGridScreen]:
     * - Normalizes the circuit.
     * - Routes to [onAdd] if `id == 0`, else [onUpdate].
     * - Invokes [onDismiss] to close the dialog.
     */
    fun handleSave(
        original: CircuitEntity?,
        saved: CircuitEntity,
        onAdd: (CircuitEntity) -> Unit,
        onUpdate: (CircuitEntity) -> Unit,
        onDismiss: () -> Unit
    ) {
        val normalized = normalize(original, saved)
        if (normalized.id == 0) {
            onAdd(normalized)
        } else {
            onUpdate(normalized)
        }
        onDismiss()
    }
}
