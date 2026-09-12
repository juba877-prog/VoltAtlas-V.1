package com.example.ui.dialogs.circuiteditor.spec

import com.example.data.CircuitEntity
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import com.example.ui.dialogs.circuiteditor.validation.CircuitEditorValidator
import com.example.ui.dialogs.circuiteditor.validation.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM specification tests for [CircuitEditorValidator].
 *
 * Evidence Label: NEW_MAPPER_SPECIFIED
 */
class CircuitEditorValidatorSpecTest {

    private val existingList = listOf(
        CircuitEntity(id = 1, circuitName = "MDB-1"),
        CircuitEntity(id = 2, circuitName = "SMDB-East"),
        CircuitEntity(id = 3, circuitName = "Lighting DB")
    )

    @Test
    fun test01_emptyName_rejection() {
        val draft = CircuitEditorDraft(name = "   ")
        val result = CircuitEditorValidator.validateForSave(draft, existingList, 0)

        assertTrue(result is ValidationResult.Error)
        val error = result as ValidationResult.Error
        assertEquals("Circuit/Panel name cannot be empty.", error.message)
        assertEquals(CircuitWorkflowStep.IDENTITY, error.targetStep)
    }

    @Test
    fun test02_duplicateName_caseInsensitiveRejection() {
        val draft = CircuitEditorDraft(name = "  mdb-1  ")
        val result = CircuitEditorValidator.validateForSave(draft, existingList, 0)

        assertTrue(result is ValidationResult.Error)
        val error = result as ValidationResult.Error
        assertEquals("A circuit/panel named 'mdb-1' already exists — choose a different name.", error.message)
        assertEquals(CircuitWorkflowStep.IDENTITY, error.targetStep)
    }

    @Test
    fun test03_duplicateName_selfExclusionOnEdit() {
        val draft = CircuitEditorDraft(name = "MDB-1")
        // Editing circuit with id = 1 -> should not conflict with itself
        val result = CircuitEditorValidator.validateForSave(draft, existingList, 1)

        assertTrue(result is ValidationResult.Success)
    }

    @Test
    fun test04_lengthZeroOrNegative_rejection() {
        val zeroLengthDraft = CircuitEditorDraft(name = "Valid Circuit", length_m = "0")
        val resultZero = CircuitEditorValidator.validateForSave(zeroLengthDraft, existingList, 0)

        assertTrue(resultZero is ValidationResult.Error)
        val errorZero = resultZero as ValidationResult.Error
        assertEquals("Cable length must be greater than zero.", errorZero.message)
        assertEquals(CircuitWorkflowStep.FEEDER, errorZero.targetStep)

        val negativeLengthDraft = CircuitEditorDraft(name = "Valid Circuit", length_m = "-15.5")
        val resultNegative = CircuitEditorValidator.validateForSave(negativeLengthDraft, existingList, 0)

        assertTrue(resultNegative is ValidationResult.Error)
        val errorNegative = resultNegative as ValidationResult.Error
        assertEquals("Cable length must be greater than zero.", errorNegative.message)
        assertEquals(CircuitWorkflowStep.FEEDER, errorNegative.targetStep)
    }

    @Test
    fun test05_nonNumericLength_fallbackTo30_passesValidation() {
        // In legacy lines 1038-1040, length_m.toDoubleOrNull() ?: 30.0 falls back to 30.0, which is > 0
        val nonNumericDraft = CircuitEditorDraft(name = "Valid Circuit", length_m = "invalid_length")
        val result = CircuitEditorValidator.validateForSave(nonNumericDraft, existingList, 0)

        assertTrue(result is ValidationResult.Success)
    }

    @Test
    fun test06_exactValidationOrder() {
        // When both name is empty and length is <= 0, name error MUST be reported first
        val bothInvalidDraft = CircuitEditorDraft(name = "", length_m = "0")
        val result = CircuitEditorValidator.validateForSave(bothInvalidDraft, existingList, 0)

        assertTrue(result is ValidationResult.Error)
        val error = result as ValidationResult.Error
        assertEquals("Circuit/Panel name cannot be empty.", error.message)
        assertEquals(CircuitWorkflowStep.IDENTITY, error.targetStep)
    }

    @Test
    fun test07_identityStepValidation() {
        val invalidNameDraft = CircuitEditorDraft(name = "")
        val resultInvalid = CircuitEditorValidator.validateIdentityStep(invalidNameDraft, existingList, 0)
        assertTrue(resultInvalid is ValidationResult.Error)

        val validDraft = CircuitEditorDraft(name = "New Board", length_m = "0")
        // Identity step does not validate length_m
        val resultValid = CircuitEditorValidator.validateIdentityStep(validDraft, existingList, 0)
        assertTrue(resultValid is ValidationResult.Success)
    }
}
