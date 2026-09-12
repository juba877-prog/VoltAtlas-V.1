package com.example.ui.dialogs.circuiteditor.state

import com.example.data.CircuitEntity
import com.example.data.resolveCableType
import com.example.ui.dialogs.circuiteditor.model.CableCatalogSnapshot
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorPreferencesSnapshot
import com.example.ui.dialogs.circuiteditor.model.TransformerSnapshot

/**
 * Pure initialization factory for [CircuitEditorDraft].
 *
 * Reproduces the exact initialization logic verified in CircuitEditDialog.kt:46-123.
 */
object CircuitEditorInitializer {

    fun initialize(
        original: CircuitEntity?,
        preferences: CircuitEditorPreferencesSnapshot,
        availableTransformers: List<TransformerSnapshot>,
        catalogSnapshot: CableCatalogSnapshot
    ): CircuitEditorDraft {
        val initialManufacturer = original?.manufacturer
            ?: catalogSnapshot.manufacturers.firstOrNull()
            ?: "Elsewedy"

        val rawType = original?.cableType ?: ""
        val exactMatch = catalogSnapshot.cableTypes.firstOrNull { it.cableType.trim().equals(rawType.trim(), ignoreCase = true) }

        val initialCableType = if (exactMatch != null) {
            exactMatch.cableType
        } else if (original != null && original.cableType.isNotBlank()) {
            resolveCableType(
                rawCableType = original.cableType,
                defaultCore = preferences.defaultCoreConfig,
                defaultCond = preferences.defaultConductor,
                defaultIns = preferences.defaultInsulation
            )
        } else {
            resolveCableType(
                rawCableType = "${preferences.defaultConductor}/${preferences.defaultInsulation}/PVC",
                defaultCore = preferences.defaultCoreConfig,
                defaultCond = preferences.defaultConductor,
                defaultIns = preferences.defaultInsulation
            )
        }

        // Parse initial progressive cable filters matching CircuitEditDialog.kt:614-646
        val ct = catalogSnapshot.cableTypes.firstOrNull { it.cableType.trim().equals(initialCableType.trim(), ignoreCase = true) }
            ?: catalogSnapshot.cableTypes.firstOrNull { it.cableType.trim().equals(rawType.trim(), ignoreCase = true) }
        val cond = ct?.conductor ?: if (rawType.contains("Al", ignoreCase = true) || initialCableType.contains("Al", ignoreCase = true)) "Al" else "Cu"
        val mat = if (cond.contains("Al", ignoreCase = true)) "Aluminium" else "Copper"
        val arm = ct?.armour ?: if (rawType.contains("SWA", ignoreCase = true) || initialCableType.contains("SWA", ignoreCase = true) || rawType.contains("STA", ignoreCase = true)) "Armoured" else "None"
        val armGroup = if (arm.equals("None", ignoreCase = true) || arm.isBlank()) "Unarmoured" else "Armoured"
        val rawCores = ct?.cores ?: if (rawType.isNotBlank()) rawType else initialCableType
        val coresConfig = when {
            rawCores.contains("1C") || rawCores.contains("Single", ignoreCase = true) -> "1C"
            rawCores.contains("2C") -> "2C"
            rawCores.contains("3.5C") -> "3.5C"
            rawCores.contains("3C") -> "3C"
            rawCores.contains("4C") -> "4C"
            else -> preferences.defaultCoreConfig.ifBlank { "4C" }
        }

        val initialSupplyCategory = when {
            original?.generatorIncluded == true && original?.upsBacked == true -> "EMERGENCY + UPS"
            original?.upsBacked == true -> "UPS"
            original?.generatorIncluded == true -> "EMERGENCY"
            else -> "NORMAL"
        }

        val initialTxId = original?.transformerId
            ?: (availableTransformers.firstOrNull()?.transformerId ?: "")

        val initialMaxVD = if (original != null) {
            (original.maxVDpct * 100.0).toString()
        } else {
            preferences.defaultMaxVdPower.toString()
        }

        val initialBusbarRating = original?.busbarRatingA?.let {
            if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
        } ?: ""

        val defaultBuswayMfg = catalogSnapshot.busways.firstOrNull()?.mfg ?: "Elsewedy"
        val defaultBuswayModel = catalogSnapshot.busways.firstOrNull()?.model ?: "Power Link"

        return CircuitEditorDraft(
            name = original?.circuitName ?: "",
            panelType = original?.panelType?.ifEmpty { "LOAD" } ?: "LOAD",
            fedFrom = original?.fedFrom ?: "Transformer",
            transformerId = initialTxId,
            loadCategory = original?.loadCategory,
            loadNature = original?.loadNature ?: "Static",
            startingMethod = original?.startingMethod ?: "DOL",
            startingCurrentMultiplier = original?.startingCurrentMultiplier?.toString() ?: "",
            loadKVA = original?.loadKVA?.toString() ?: "10.0",
            pf = original?.pf?.toString() ?: "0.85",
            demandFactor = original?.demandFactor?.toString() ?: "1.0",
            voltageV = original?.voltageV?.toString() ?: preferences.defaultVoltage3Phase.toString(),
            safetyFactor = original?.safetyFactor?.toString() ?: "1.0",
            phases = original?.phases ?: "3P+N",
            isHarmonic = original?.isHarmonic ?: false,
            supplyCategory = initialSupplyCategory,
            assignedAtsId = original?.assignedAtsId ?: "None",
            assignedGeneratorId = original?.assignedGeneratorId ?: "None",
            assignedUpsId = original?.assignedUpsId ?: "None",
            feederType = original?.feederType ?: "Cable",
            manufacturer = initialManufacturer,
            cableType = initialCableType,
            parallelRuns = original?.parallelRuns?.toString() ?: "1",
            length_m = original?.length_m?.toString() ?: "30",
            maxVDpct = initialMaxVD,
            buswayMfg = original?.buswayMfg ?: defaultBuswayMfg,
            buswayModel = original?.buswayModel ?: defaultBuswayModel,
            filterMat = mat,
            filterArmour = armGroup,
            filterCores = coresConfig,
            filterQuery = "",
            installation = original?.installation ?: "Laid in Ducts",
            airTempC = original?.airTempC?.toString() ?: "40",
            gndTempC = original?.gndTempC?.toString() ?: "40",
            burialDepth_m = original?.burialDepth_m?.toString() ?: "0.6",
            soilRes = original?.soilRes?.toString() ?: "125",
            noCircuits = original?.noCircuits?.toString() ?: "1",
            formation = original?.formation ?: "Flat",
            spacing = original?.spacing ?: "Touching",
            busbarRatingA = initialBusbarRating,
            mainIncomingType = original?.mainIncomingType ?: "",
            mainIncomingCount = original?.mainIncomingCount?.toString() ?: "",
            ipRating = original?.ipRating ?: "",
            panelLocation = original?.panelLocation ?: "",
            cableEntry = original?.cableEntry ?: "",
            numberOfWays = original?.numberOfWays?.toString() ?: "",
            feederDescription = original?.feederDescription ?: ""
        )
    }
}
