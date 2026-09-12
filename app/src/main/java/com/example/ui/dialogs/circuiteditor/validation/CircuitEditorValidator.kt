package com.example.ui.dialogs.circuiteditor.validation

import com.example.data.CircuitEntity
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep

sealed interface ValidationResult {
    data object Success : ValidationResult
    data class Error(val message: String, val targetStep: CircuitWorkflowStep) : ValidationResult
}

/**
 * Pure validation engine for Circuit Editor.
 *
 * Implements the exact legacy validation order and messages from CircuitEditDialog.kt:1025-1042.
 */
object CircuitEditorValidator {

    /**
     * Executes the complete save validation pipeline in exact legacy order:
     * 1. Trimmed name non-empty
     * 2. Duplicate name check (case-insensitive, excluding current circuit ID)
     * 3. Cable length greater than zero (Double <= 0 rejected; non-numeric falls back to 30.0)
     */
    fun validateForSave(
        draft: CircuitEditorDraft,
        existingCircuits: List<CircuitEntity>,
        editingCircuitId: Int
    ): ValidationResult {
        val trimmedName = draft.name.trim()
        if (trimmedName.isEmpty()) {
            return ValidationResult.Error(
                message = "Circuit/Panel name cannot be empty.",
                targetStep = CircuitWorkflowStep.IDENTITY
            )
        }

        val isDuplicateName = existingCircuits.any { c ->
            (editingCircuitId == 0 || c.id != editingCircuitId) &&
                c.circuitName.trim().equals(trimmedName, ignoreCase = true)
        }
        if (isDuplicateName) {
            return ValidationResult.Error(
                message = "A circuit/panel named '$trimmedName' already exists — choose a different name.",
                targetStep = CircuitWorkflowStep.IDENTITY
            )
        }

        val doubleLengthRaw = draft.length_m.toDoubleOrNull() ?: 30.0
        if (doubleLengthRaw <= 0) {
            return ValidationResult.Error(
                message = "Cable length must be greater than zero.",
                targetStep = CircuitWorkflowStep.FEEDER
            )
        }

        return ValidationResult.Success
    }

    /**
     * Validates Step 1 (Identity) inputs for immediate step-transition gating.
     * Prevents advancing to Step 2 if name is blank or duplicate.
     */
    fun validateIdentityStep(
        draft: CircuitEditorDraft,
        existingCircuits: List<CircuitEntity>,
        editingCircuitId: Int
    ): ValidationResult {
        val trimmedName = draft.name.trim()
        if (trimmedName.isEmpty()) {
            return ValidationResult.Error(
                message = "Circuit/Panel name cannot be empty.",
                targetStep = CircuitWorkflowStep.IDENTITY
            )
        }

        val isDuplicateName = existingCircuits.any { c ->
            (editingCircuitId == 0 || c.id != editingCircuitId) &&
                c.circuitName.trim().equals(trimmedName, ignoreCase = true)
        }
        if (isDuplicateName) {
            return ValidationResult.Error(
                message = "A circuit/panel named '$trimmedName' already exists — choose a different name.",
                targetStep = CircuitWorkflowStep.IDENTITY
            )
        }

        return ValidationResult.Success
    }
}
