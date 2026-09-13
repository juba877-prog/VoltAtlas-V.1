package com.example.ui.dialogs.circuiteditor.mapper

import com.example.data.CircuitEntity
import com.example.data.SourceType
import com.example.ui.dialogs.circuiteditor.model.TransformerSnapshot

/**
 * Result of pure source hierarchy and transformer resolution.
 */
data class CircuitSourceResolution(
    val finalTxId: String,
    val parentBoardObj: CircuitEntity?,
    val calculatedParentId: Int?,
    val calculatedLevel: Int,
    val calculatedSourceType: String
)

/**
 * Pure resolver deriving parent hierarchy, level, sourceType, and transformer inheritance
 * matching legacy CircuitEditDialog.kt lines 1060-1079.
 */
object CircuitSourceResolver {

    fun resolveSourceHierarchy(
        fedFrom: String,
        draftTransformerId: String,
        existingCircuits: List<CircuitEntity>,
        availableTransformers: List<TransformerSnapshot>
    ): CircuitSourceResolution {
        val parentBoardObj = existingCircuits.firstOrNull { it.circuitName == fedFrom }
        val calculatedParentId = parentBoardObj?.id
        val calculatedLevel = if (parentBoardObj == null) 0 else (parentBoardObj.level ?: 0) + 1
        val calculatedSourceType = when (fedFrom.uppercase()) {
            "GENERATOR" -> SourceType.GENERATOR.name
            "GRID", "UTILITY GRID" -> SourceType.GRID.name
            "ATS" -> "ATS"
            "TRANSFORMER" -> SourceType.TRANSFORMER.name
            else -> if (parentBoardObj != null) SourceType.PANEL.name else SourceType.TRANSFORMER.name
        }

        val finalTxId = if (fedFrom == "Transformer" && (draftTransformerId.isEmpty() || draftTransformerId.contains("Add Transformer"))) {
            if (availableTransformers.isNotEmpty()) {
                availableTransformers.firstOrNull()?.transformerId ?: ""
            } else {
                // No real transformer exists and this pure resolver cannot create one: return the
                // exact CircuitEntity.transformerId absent representation, which is the empty
                // string (Entities.kt:23 `val transformerId: String = ""`).
                // No fabricated fallback is permitted here.
                ""
            }
        } else if (parentBoardObj != null && parentBoardObj.transformerId.isNotBlank()) {
            parentBoardObj.transformerId
        } else {
            draftTransformerId
        }

        return CircuitSourceResolution(
            finalTxId = finalTxId,
            parentBoardObj = parentBoardObj,
            calculatedParentId = calculatedParentId,
            calculatedLevel = calculatedLevel,
            calculatedSourceType = calculatedSourceType
        )
    }
}
