package com.example.ui.dialogs.circuiteditor.mapper

import com.example.data.CircuitEntity
import com.example.data.DomainNormalization
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import kotlin.math.max

/**
 * Pure entity mapper transforming [CircuitEditorDraft] into [CircuitEntity].
 *
 * Implements the exact 62-parameter mapping, coercion, nulling, and preservation rules
 * verified from CircuitEditDialog.kt:1043-1183.
 */
object CircuitEntityMapper {

    fun mapToEntity(
        draft: CircuitEditorDraft,
        original: CircuitEntity?,
        resolution: CircuitSourceResolution
    ): CircuitEntity {
        val trimmedName = draft.name.trim()

        val doubleLengthRaw = draft.length_m.toDoubleOrNull() ?: 30.0
        val doubleLength = doubleLengthRaw
        val doubleKva = max(0.0, draft.loadKVA.toDoubleOrNull() ?: 10.0)
        val doublePf = (draft.pf.toDoubleOrNull() ?: 0.8).coerceIn(0.1, 1.0)
        val doubleDemandFactor = (draft.demandFactor.toDoubleOrNull() ?: 1.0).coerceIn(0.0, 1.0)
        val doubleSafety = max(1.0, draft.safetyFactor.toDoubleOrNull() ?: 1.0)
        val intVoltage = draft.voltageV.toIntOrNull() ?: 400
        val intParallel = draft.parallelRuns.toIntOrNull() ?: 1
        val doubleMaxVD = (draft.maxVDpct.toDoubleOrNull() ?: 3.0) / 100.0
        val intAirTemp = draft.airTempC.toIntOrNull() ?: 40
        val intGndTemp = draft.gndTempC.toIntOrNull() ?: 40
        val doubleDepth = draft.burialDepth_m.toDoubleOrNull() ?: 0.6
        val intSoilRes = draft.soilRes.toIntOrNull() ?: 125
        val intNoCircuits = draft.noCircuits.toIntOrNull() ?: 1

        val isGen = draft.supplyCategory == "EMERGENCY" || draft.supplyCategory == "EMERGENCY + UPS"
        val isUps = draft.supplyCategory == "UPS" || draft.supplyCategory == "EMERGENCY + UPS"

        val assignedGenId = draft.assignedGeneratorId.takeIf { it.isNotBlank() && it != "None" }
        val assignedAtsId = draft.assignedAtsId.takeIf { it.isNotBlank() && it != "None" }
        val assignedUpsId = draft.assignedUpsId.takeIf { it.isNotBlank() && it != "None" }

        val finalStartingMethod = if (draft.loadNature == "Motor") draft.startingMethod else null
        val finalStartingMultiplier = if (draft.loadNature == "Motor") draft.startingCurrentMultiplier.toDoubleOrNull() else null

        val finalBusbarRatingA = draft.busbarRatingA.toDoubleOrNull()
        val finalMainIncomingType = draft.mainIncomingType.ifBlank { null }
        val finalMainIncomingCount = draft.mainIncomingCount.toIntOrNull()
        val finalIpRating = draft.ipRating.ifBlank { null }
        val finalPanelLocation = draft.panelLocation.ifBlank { null }
        val finalCableEntry = draft.cableEntry.ifBlank { null }
        val finalFeederDescription = draft.feederDescription.ifBlank { null }
        val finalNumberOfWays = draft.numberOfWays.toIntOrNull()
        val finalBuswayMfg = if (draft.feederType == "Busway") draft.buswayMfg else null
        val finalBuswayModel = if (draft.feederType == "Busway") draft.buswayModel else null

        return original?.copy(
            circuitName = trimmedName,
            loadKVA = doubleKva,
            fedFrom = draft.fedFrom,
            transformerId = resolution.finalTxId,
            loadType = draft.supplyCategory,
            phases = DomainNormalization.normalizePhases(draft.phases),
            voltageV = intVoltage,
            pf = doublePf,
            safetyFactor = doubleSafety,
            manufacturer = draft.manufacturer,
            cableType = DomainNormalization.normalizeCableType(draft.cableType),
            installation = draft.installation,
            parallelRuns = intParallel,
            airTempC = intAirTemp,
            gndTempC = intGndTemp,
            burialDepth_m = doubleDepth,
            soilRes = intSoilRes,
            noCircuits = intNoCircuits,
            formation = draft.formation,
            spacing = draft.spacing,
            length_m = doubleLength,
            maxVDpct = doubleMaxVD,
            generatorIncluded = isGen,
            atsIncluded = isGen,
            assignedGeneratorId = assignedGenId,
            assignedAtsId = assignedAtsId,
            assignedUpsId = assignedUpsId,
            upsBacked = isUps,
            isHarmonic = draft.isHarmonic,
            loadNature = draft.loadNature,
            startingMethod = finalStartingMethod,
            startingCurrentMultiplier = finalStartingMultiplier,
            panelType = draft.panelType,
            parentId = resolution.calculatedParentId,
            level = resolution.calculatedLevel,
            sourceType = resolution.calculatedSourceType,
            demandFactor = doubleDemandFactor,
            loadCategory = draft.loadCategory,
            busbarRatingA = finalBusbarRatingA,
            scRatingKA = original.scRatingKA,
            mainIncomingType = finalMainIncomingType,
            mainIncomingCount = finalMainIncomingCount,
            ipRating = finalIpRating,
            panelLocation = finalPanelLocation,
            cableEntry = finalCableEntry,
            feederDescription = finalFeederDescription,
            numberOfWays = finalNumberOfWays,
            feederType = draft.feederType,
            buswayMfg = finalBuswayMfg,
            buswayModel = finalBuswayModel
        ) ?: CircuitEntity(
            circuitName = trimmedName,
            loadKVA = doubleKva,
            fedFrom = draft.fedFrom,
            transformerId = resolution.finalTxId,
            loadType = draft.supplyCategory,
            phases = DomainNormalization.normalizePhases(draft.phases),
            voltageV = intVoltage,
            pf = doublePf,
            safetyFactor = doubleSafety,
            manufacturer = draft.manufacturer,
            cableType = DomainNormalization.normalizeCableType(draft.cableType),
            installation = draft.installation,
            parallelRuns = intParallel,
            airTempC = intAirTemp,
            gndTempC = intGndTemp,
            burialDepth_m = doubleDepth,
            soilRes = intSoilRes,
            noCircuits = intNoCircuits,
            formation = draft.formation,
            spacing = draft.spacing,
            length_m = doubleLength,
            maxVDpct = doubleMaxVD,
            generatorIncluded = isGen,
            atsIncluded = isGen,
            assignedGeneratorId = assignedGenId,
            assignedAtsId = assignedAtsId,
            assignedUpsId = assignedUpsId,
            upsBacked = isUps,
            isHarmonic = draft.isHarmonic,
            loadNature = draft.loadNature,
            startingMethod = finalStartingMethod,
            startingCurrentMultiplier = finalStartingMultiplier,
            panelType = draft.panelType,
            parentId = resolution.calculatedParentId,
            level = resolution.calculatedLevel,
            sourceType = resolution.calculatedSourceType,
            demandFactor = doubleDemandFactor,
            loadCategory = draft.loadCategory,
            busbarRatingA = finalBusbarRatingA,
            scRatingKA = null,
            mainIncomingType = finalMainIncomingType,
            mainIncomingCount = finalMainIncomingCount,
            ipRating = finalIpRating,
            panelLocation = finalPanelLocation,
            cableEntry = finalCableEntry,
            feederDescription = finalFeederDescription,
            numberOfWays = finalNumberOfWays,
            feederType = draft.feederType,
            buswayMfg = finalBuswayMfg,
            buswayModel = finalBuswayModel
        )
    }
}
