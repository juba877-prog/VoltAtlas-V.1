package com.example.ui.dialogs.circuiteditor.ui.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.VoltAtlasDropdown
import com.example.ui.components.VoltAtlasTextField
import com.example.ui.dialogs.circuiteditor.model.CableCatalogSnapshot
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.ui.CableFamilyPicker

private data class ParsedCableFamilyInfo(
    val originalType: String,
    val material: String,
    val armourGroup: String,
    val exactArmour: String,
    val coresConfig: String,
    val insulation: String,
    val displayLabel: String
)

/**
 * Stage C3 Feeder Selection step content.
 *
 * Supports single-choice branch switching between Cable and Busway:
 * - Cable branch: progressive cascading filters (Manufacturer -> Material -> Armour -> Cores -> Search/Family),
 *   read-only derived insulation, and CableFamilyPicker launcher.
 * - Busway branch: Busway manufacturer and model selections.
 * - Shared engineering inputs: Parallel runs, Length (m), and Max Voltage Drop (%).
 */
@Composable
fun FeederStepContent(
    draft: CircuitEditorDraft,
    onDraftChange: (CircuitEditorDraft) -> Unit,
    catalogSnapshot: CableCatalogSnapshot = CableCatalogSnapshot(),
    modifier: Modifier = Modifier
) {
    var isPickerOpen by rememberSaveable { mutableStateOf(false) }

    val parseFamilyInfo = remember(catalogSnapshot.cableTypes) {
        { rawType: String ->
            val ct = catalogSnapshot.cableTypes.firstOrNull { it.cableType.trim().equals(rawType.trim(), ignoreCase = true) }
            val cond = ct?.conductor ?: if (rawType.contains("Al", ignoreCase = true)) "Al" else "Cu"
            val mat = if (cond.contains("Al", ignoreCase = true)) "Aluminium" else "Copper"
            val arm = ct?.armour ?: if (rawType.contains("SWA", ignoreCase = true) || rawType.contains("STA", ignoreCase = true)) "Armoured" else "None"
            val armGroup = if (arm.equals("None", ignoreCase = true) || arm.isBlank()) "Unarmoured" else "Armoured"
            val rawCores = ct?.cores ?: rawType
            val coresConfig = when {
                rawCores.contains("1C") || rawCores.contains("Single", ignoreCase = true) -> "1C"
                rawCores.contains("2C") -> "2C"
                rawCores.contains("3.5C") -> "3.5C"
                rawCores.contains("3C") -> "3C"
                rawCores.contains("4C") -> "4C"
                else -> "4C"
            }
            val ins = ct?.insulation ?: if (rawType.contains("XLPE", ignoreCase = true)) "XLPE" else "PVC"
            ParsedCableFamilyInfo(
                originalType = ct?.cableType ?: rawType,
                material = mat,
                armourGroup = armGroup,
                exactArmour = arm,
                coresConfig = coresConfig,
                insulation = ins,
                displayLabel = "$mat • $armGroup • $coresConfig • $ins"
            )
        }
    }

    val allCatalogFamilies = remember(draft.manufacturer, catalogSnapshot) {
        val matching = catalogSnapshot.cableTypes.filter { type ->
            catalogSnapshot.cableCatalog.any { cat ->
                cat.typeCode == type.code && cat.mfg.trim().equals(draft.manufacturer.trim(), ignoreCase = true)
            }
        }.map { it.cableType }.distinct()
        if (matching.isNotEmpty()) {
            matching
        } else if (draft.cableType.isNotBlank()) {
            listOf(draft.cableType)
        } else {
            emptyList()
        }
    }

    val allParsedFamilies = remember(allCatalogFamilies) {
        allCatalogFamilies.map { parseFamilyInfo(it) }
    }

    val matchingFamilies = remember(draft.filterMat, draft.filterArmour, draft.filterCores, draft.filterQuery, allParsedFamilies) {
        allParsedFamilies.filter {
            it.material == draft.filterMat &&
            it.armourGroup == draft.filterArmour &&
            it.coresConfig == draft.filterCores &&
            (draft.filterQuery.isBlank() || it.originalType.contains(draft.filterQuery, ignoreCase = true) || it.displayLabel.contains(draft.filterQuery, ignoreCase = true))
        }
    }

    Box(modifier = modifier.fillMaxWidth().testTag("circuit_editor_step_feeder")) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Feeder Type Selector (Cable vs Busway)
            VoltAtlasDropdown(
                value = draft.feederType,
                options = listOf("Cable", "Busway"),
                onSelect = { onDraftChange(draft.copy(feederType = it)) },
                label = "Feeder Type",
                modifier = Modifier.testTag("input_feeder_type")
            )

            if (draft.feederType == "Cable") {
                // Manufacturer Dropdown
                val manufacturerOptions = remember(catalogSnapshot) {
                    val dynamicList = catalogSnapshot.manufacturers.ifEmpty {
                        catalogSnapshot.cableCatalog.map { it.mfg.trim() }.filter { it.isNotBlank() }.distinct().sorted()
                    }
                    if (dynamicList.isEmpty()) listOf("Elsewedy", "IEC-Generic") else dynamicList
                }

                VoltAtlasDropdown(
                    value = draft.manufacturer,
                    options = manufacturerOptions,
                    onSelect = { selectedMfg ->
                        val validTypes = catalogSnapshot.cableTypes.filter { type ->
                            catalogSnapshot.cableCatalog.any { cat ->
                                cat.typeCode == type.code && cat.mfg.trim().equals(selectedMfg.trim(), ignoreCase = true)
                            }
                        }.map { it.cableType }.distinct().sorted()

                        val parsedValid = validTypes.map { parseFamilyInfo(it) }
                        val matchingExisting = if (validTypes.contains(draft.cableType)) {
                            parsedValid.firstOrNull { it.originalType == draft.cableType }
                        } else null

                        val bestMatch = matchingExisting
                            ?: parsedValid.firstOrNull {
                                it.material == draft.filterMat && it.armourGroup == draft.filterArmour && it.coresConfig == draft.filterCores
                            }
                            ?: parsedValid.firstOrNull {
                                it.material == draft.filterMat && it.armourGroup == draft.filterArmour
                            }
                            ?: parsedValid.firstOrNull {
                                it.material == draft.filterMat
                            }
                            ?: parsedValid.firstOrNull()

                        val newCableType = bestMatch?.originalType ?: (validTypes.firstOrNull() ?: "")
                        val finalMat = bestMatch?.material ?: draft.filterMat
                        val finalArmour = bestMatch?.armourGroup ?: draft.filterArmour
                        val finalCores = bestMatch?.coresConfig ?: draft.filterCores

                        onDraftChange(
                            draft.copy(
                                manufacturer = selectedMfg,
                                cableType = newCableType,
                                filterMat = finalMat,
                                filterArmour = finalArmour,
                                filterCores = finalCores
                            )
                        )
                    },
                    label = "Manufacturer / Cable Source",
                    modifier = Modifier.testTag("input_manufacturer")
                )

                // Conductor Material Filter Chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Conductor Material",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Copper", "Aluminium").forEach { mat ->
                            val isAvail = allParsedFamilies.isEmpty() || allParsedFamilies.any { it.material == mat }
                            FilterChip(
                                selected = draft.filterMat == mat,
                                onClick = {
                                    if (isAvail) {
                                        val candidate = allParsedFamilies.firstOrNull {
                                            it.material == mat && it.armourGroup == draft.filterArmour && it.coresConfig == draft.filterCores
                                        }
                                        if (candidate != null) {
                                            onDraftChange(draft.copy(filterMat = mat, cableType = candidate.originalType))
                                        } else {
                                            val alt = allParsedFamilies.firstOrNull { it.material == mat }
                                            if (alt != null) {
                                                onDraftChange(
                                                    draft.copy(
                                                        filterMat = mat,
                                                        filterArmour = alt.armourGroup,
                                                        filterCores = alt.coresConfig,
                                                        cableType = alt.originalType
                                                    )
                                                )
                                            } else {
                                                onDraftChange(draft.copy(filterMat = mat))
                                            }
                                        }
                                    }
                                },
                                label = { Text(if (isAvail) mat else "$mat (N/A)", fontSize = 12.sp) },
                                enabled = isAvail,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("filter_mat_$mat")
                            )
                        }
                    }
                }

                // Armour Construction Filter Chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Armour Construction",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Unarmoured", "Armoured").forEach { arm ->
                            val isAvail = allParsedFamilies.isEmpty() || allParsedFamilies.any { it.material == draft.filterMat && it.armourGroup == arm }
                            FilterChip(
                                selected = draft.filterArmour == arm,
                                onClick = {
                                    if (isAvail) {
                                        val candidate = allParsedFamilies.firstOrNull {
                                            it.material == draft.filterMat && it.armourGroup == arm && it.coresConfig == draft.filterCores
                                        }
                                        if (candidate != null) {
                                            onDraftChange(draft.copy(filterArmour = arm, cableType = candidate.originalType))
                                        } else {
                                            val alt = allParsedFamilies.firstOrNull { it.material == draft.filterMat && it.armourGroup == arm }
                                            if (alt != null) {
                                                onDraftChange(
                                                    draft.copy(
                                                        filterArmour = arm,
                                                        filterCores = alt.coresConfig,
                                                        cableType = alt.originalType
                                                    )
                                                )
                                            } else {
                                                onDraftChange(draft.copy(filterArmour = arm))
                                            }
                                        }
                                    }
                                },
                                label = { Text(if (isAvail) arm else "$arm (N/A)", fontSize = 12.sp) },
                                enabled = isAvail,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("filter_armour_$arm")
                            )
                        }
                    }
                }

                // Core Configuration Dropdown
                val availableCores = remember(draft.filterMat, draft.filterArmour, allParsedFamilies) {
                    val cores = allParsedFamilies
                        .filter { it.material == draft.filterMat && it.armourGroup == draft.filterArmour }
                        .map { it.coresConfig }
                        .distinct()
                    if (cores.isEmpty()) listOf("1C", "2C", "3C", "3.5C", "4C") else cores
                }

                VoltAtlasDropdown(
                    value = draft.filterCores,
                    options = availableCores,
                    onSelect = { core ->
                        val candidate = allParsedFamilies.firstOrNull {
                            it.material == draft.filterMat && it.armourGroup == draft.filterArmour && it.coresConfig == core
                        }
                        if (candidate != null) {
                            onDraftChange(draft.copy(filterCores = core, cableType = candidate.originalType))
                        } else {
                            onDraftChange(draft.copy(filterCores = core))
                        }
                    },
                    label = "Core Configuration",
                    modifier = Modifier.testTag("input_core_config")
                )

                // Search visibility rule: candidate-count > 3
                if (matchingFamilies.size > 3) {
                    VoltAtlasTextField(
                        value = draft.filterQuery,
                        onValueChange = { onDraftChange(draft.copy(filterQuery = it)) },
                        label = "Search compatible families..."
                    )
                }

                // Selected Cable Family (Single unified picker entry surface)
                val currentFamilyLabel = remember(draft.cableType) {
                    if (draft.cableType.isNotBlank()) draft.cableType else "Select Cable Family"
                }

                Surface(
                    onClick = { isPickerOpen = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("input_cable_type"),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Selected Cable Family",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = currentFamilyLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap to choose from compatible families",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Open cable family picker",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("open_cable_family_picker")
                        )
                    }
                }

                // Read-only Derived Insulation Information
                val derivedInsulation = remember(draft.cableType) {
                    parseFamilyInfo(draft.cableType).insulation
                }
                Text(
                    text = "Derived Insulation: $derivedInsulation (Derived from selected family)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Busway Branch
                val buswayMfgOptions = remember(catalogSnapshot) {
                    val dynamicList = catalogSnapshot.busways.map { it.mfg.trim() }.filter { it.isNotBlank() }.distinct().sorted()
                    if (dynamicList.isEmpty()) listOf("Elsewedy") else dynamicList
                }

                VoltAtlasDropdown(
                    value = draft.buswayMfg,
                    options = buswayMfgOptions,
                    onSelect = { onDraftChange(draft.copy(buswayMfg = it)) },
                    label = "Busway Manufacturer",
                    modifier = Modifier.testTag("input_busway_mfg")
                )

                val buswayModelOptions = remember(draft.buswayMfg, catalogSnapshot) {
                    val dynamicModels = catalogSnapshot.busways
                        .filter { draft.buswayMfg.isBlank() || draft.buswayMfg == "All / Any" || it.mfg.equals(draft.buswayMfg, ignoreCase = true) }
                        .map { it.model.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()
                    if (dynamicModels.isEmpty()) listOf("All / Any") else (dynamicModels + "All / Any").distinct()
                }

                VoltAtlasDropdown(
                    value = draft.buswayModel,
                    options = buswayModelOptions,
                    onSelect = { onDraftChange(draft.copy(buswayModel = it)) },
                    label = "Busway Model",
                    modifier = Modifier.testTag("input_busway_model")
                )
            }

            // Shared Engineering Inputs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    VoltAtlasTextField(
                        value = draft.parallelRuns,
                        onValueChange = { onDraftChange(draft.copy(parallelRuns = it)) },
                        label = "Parallel Runs",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.testTag("input_parallel_runs")
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    VoltAtlasTextField(
                        value = draft.length_m,
                        onValueChange = { onDraftChange(draft.copy(length_m = it)) },
                        label = "Length",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = {
                            Text(
                                text = "m",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        },
                        modifier = Modifier.testTag("input_length")
                    )
                }
            }

            VoltAtlasTextField(
                value = draft.maxVDpct,
                onValueChange = { onDraftChange(draft.copy(maxVDpct = it)) },
                label = "Max Voltage Drop (%)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailingIcon = {
                    Text(
                        text = "%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                modifier = Modifier.testTag("input_max_vd")
            )
        }

        // Compatible Candidate Families for current filters only
        val compatibleCandidateFamilies = remember(draft.filterMat, draft.filterArmour, draft.filterCores, allParsedFamilies) {
            allParsedFamilies.filter {
                it.material == draft.filterMat &&
                it.armourGroup == draft.filterArmour &&
                it.coresConfig == draft.filterCores
            }.map { it.originalType }.distinct()
        }

        // Cable Family Picker Modal
        CableFamilyPicker(
            isOpen = isPickerOpen,
            currentFamily = draft.cableType,
            candidateFamilies = compatibleCandidateFamilies,
            onConfirm = { confirmedFamily ->
                onDraftChange(draft.copy(cableType = confirmedFamily))
                isPickerOpen = false
            },
            onCancel = {
                isPickerOpen = false
            }
        )
    }
}
