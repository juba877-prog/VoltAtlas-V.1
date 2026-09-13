package com.example.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.data.LoadCategories
import com.example.data.SourceType
import com.example.data.resolveCableType
import com.example.ui.CableFamilyInfo
import com.example.ui.EngineeringViewModel
import com.example.ui.UiState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.catalogCableSizeOptions
import com.example.ui.components.*
import com.example.ui.dialogs.circuiteditor.model.AtsSnapshot
import com.example.ui.dialogs.circuiteditor.model.CableCatalogSnapshot
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorPreferencesSnapshot
import com.example.ui.dialogs.circuiteditor.model.GeneratorSnapshot
import com.example.ui.dialogs.circuiteditor.model.TransformerSnapshot
import com.example.ui.dialogs.circuiteditor.model.UpsSnapshot
import com.example.ui.dialogs.circuiteditor.ui.CircuitEditorParallelEntry
import com.example.ui.formatCatalogCableSizeSafe
import com.example.ui.getDistinctCatalogManufacturers
import com.example.ui.parseCableSizeSafe
import com.example.ui.parseFamilyInfo
import com.example.ui.theme.*

/**
 * R0 containment gate for the circuit editor production route.
 *
 * false (default) -> [LegacyCircuitEditDialogInternal]: last known production-safe behavior.
 * true            -> [MobileCircuitEditorRoute]: mobile-first step editor (remediation pending).
 *
 * Local compile-time containment constant only. No remote config, BuildConfig field,
 * settings UI, analytics, logging, dependency change, or rollout infrastructure.
 * Flipping to true requires explicit owner authorization (R6 controlled reactivation).
 */
private const val USE_MOBILE_CIRCUIT_EDITOR = true

@Composable
fun CircuitEditDialog(
    circuit: CircuitEntity?,
    existingCircuits: List<CircuitEntity>,
    onDismiss: () -> Unit,
    onSave: (CircuitEntity) -> Unit,
    viewModel: EngineeringViewModel? = null
) {
    if (USE_MOBILE_CIRCUIT_EDITOR) {
        MobileCircuitEditorRoute(
            circuit = circuit,
            existingCircuits = existingCircuits,
            onDismiss = onDismiss,
            onSave = onSave,
            viewModel = viewModel
        )
    } else {
        LegacyCircuitEditDialogInternal(
            circuit = circuit,
            existingCircuits = existingCircuits,
            onDismiss = onDismiss,
            onSave = onSave,
            viewModel = viewModel
        )
    }
}

/**
 * Mobile-only production route. All mobile-only Flow collection and snapshot preparation
 * lives here so the default legacy route performs no unnecessary mobile-only collection.
 */
@Composable
private fun MobileCircuitEditorRoute(
    circuit: CircuitEntity?,
    existingCircuits: List<CircuitEntity>,
    onDismiss: () -> Unit,
    onSave: (CircuitEntity) -> Unit,
    viewModel: EngineeringViewModel?
) {
    val defaultV3P = viewModel?.preferences?.defaultVoltage3Phase?.collectAsState()?.value ?: 400
    val defaultMaxVdPower = viewModel?.preferences?.maxVdLimitPower?.collectAsState()?.value ?: 5.0
    val defaultConductor = viewModel?.preferences?.defaultConductorMaterial?.collectAsState()?.value ?: "Cu"
    val defaultInsulation = viewModel?.preferences?.defaultInsulation?.collectAsState()?.value ?: "XLPE"
    val defaultCoreConfig = viewModel?.preferences?.defaultCoreConfig?.collectAsState()?.value ?: "4C"

    val preferences = remember(defaultV3P, defaultMaxVdPower, defaultConductor, defaultInsulation, defaultCoreConfig) {
        CircuitEditorPreferencesSnapshot(
            defaultVoltage3Phase = defaultV3P,
            defaultMaxVdPower = defaultMaxVdPower,
            defaultConductor = defaultConductor,
            defaultInsulation = defaultInsulation,
            defaultCoreConfig = defaultCoreConfig
        )
    }

    val transformers = viewModel?.projectTransformersFlow?.collectAsState()?.value ?: emptyList()
    val availableTransformers = remember(transformers) {
        transformers.map { TransformerSnapshot(transformerId = it.transformerId, ratingKVA = it.kVA) }
    }

    val generators = viewModel?.generatorsList?.collectAsState()?.value ?: emptyList()
    val availableGenerators = remember(generators) {
        generators.map { GeneratorSnapshot(generatorId = it.generatorId) }
    }

    val atsList = viewModel?.atsList?.collectAsState()?.value ?: emptyList()
    val availableAts = remember(atsList) {
        atsList.map { AtsSnapshot(atsId = it.atsId) }
    }

    val upsList = viewModel?.upsList?.collectAsState()?.value ?: emptyList()
    val availableUps = remember(upsList) {
        upsList.map { UpsSnapshot(upsId = it.upsId) }
    }

    val catalogSnapshot = remember {
        CableCatalogSnapshot(
            manufacturers = getDistinctCatalogManufacturers(),
            cableTypes = Catalog.cableTypes,
            cableCatalog = Catalog.cableCatalog,
            busways = Catalog.busways
        )
    }

    val scRatingText = remember(circuit?.scRatingKA) {
        circuit?.scRatingKA?.let { String.format(java.util.Locale.US, "%.2f kA", it) }
            ?: "Unavailable (Calculated after save)"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        VoltAtlasTheme {
            CircuitEditorParallelEntry(
                circuit = circuit,
                existingCircuits = existingCircuits,
                preferences = preferences,
                availableTransformers = availableTransformers,
                availableGenerators = availableGenerators,
                availableAts = availableAts,
                availableUps = availableUps,
                catalogSnapshot = catalogSnapshot,
                onDismiss = onDismiss,
                onAddTransformer = { viewModel?.addNewTransformerInline() },
                onSave = onSave,
                scRatingText = scRatingText
            )
        }
    }
}

/**
 * Retained legacy implementation of CircuitEditDialog (private, unused).
 * Preserves existing code integrity without deletion.
 */
@Composable
@Suppress("UnusedPrivateMember", "UNUSED_PARAMETER")
private fun LegacyCircuitEditDialogInternal(
    circuit: CircuitEntity?,
    existingCircuits: List<CircuitEntity>,
    onDismiss: () -> Unit,
    onSave: (CircuitEntity) -> Unit,
    viewModel: EngineeringViewModel? = null
) {
    val defaultV3P = viewModel?.preferences?.defaultVoltage3Phase?.collectAsState()?.value ?: 400
    val defaultMaxVdPower = viewModel?.preferences?.maxVdLimitPower?.collectAsState()?.value ?: 5.0
    val defaultConductor = viewModel?.preferences?.defaultConductorMaterial?.collectAsState()?.value ?: "Cu"
    val defaultInsulation = viewModel?.preferences?.defaultInsulation?.collectAsState()?.value ?: "XLPE"
    val defaultCoreConfig = viewModel?.preferences?.defaultCoreConfig?.collectAsState()?.value ?: "4C"

    val transformers = viewModel?.projectTransformersFlow?.collectAsState()?.value ?: emptyList()
    val txOptions = remember(transformers) {
        if (transformers.isEmpty()) listOf("➕ Add Transformer") else transformers.map { it.transformerId } + listOf("➕ Add Another Transformer")
    }
    var expandedTxDropdown by remember { mutableStateOf(false) }

    var name by rememberSaveable { mutableStateOf(circuit?.circuitName ?: "") }
    var loadKVA by rememberSaveable { mutableStateOf(circuit?.loadKVA?.toString() ?: "10.0") }
    var fedFrom by rememberSaveable { mutableStateOf(circuit?.fedFrom ?: "Transformer") }
    var transformerId by rememberSaveable { mutableStateOf(circuit?.transformerId ?: (transformers.firstOrNull()?.transformerId ?: "")) }
    var loadType by rememberSaveable { mutableStateOf(
        when {
            circuit?.generatorIncluded == true && circuit?.upsBacked == true -> "EMERGENCY + UPS"
            circuit?.upsBacked == true -> "UPS"
            circuit?.generatorIncluded == true -> "EMERGENCY"
            else -> "NORMAL"
        }
    ) }
    var assignedGeneratorId by rememberSaveable { mutableStateOf(circuit?.assignedGeneratorId ?: "None") }
    var assignedAtsId by rememberSaveable { mutableStateOf(circuit?.assignedAtsId ?: "None") }
    var assignedUpsId by rememberSaveable { mutableStateOf(circuit?.assignedUpsId ?: "None") }
    val projectGenerators = viewModel?.generatorsList?.collectAsState()?.value ?: emptyList()
    val generatorOptions = remember(projectGenerators) { listOf("None") + projectGenerators.map { it.generatorId } }
    val projectAtsList = viewModel?.atsList?.collectAsState()?.value ?: emptyList()
    val atsOptions = remember(projectAtsList) { listOf("None") + projectAtsList.map { it.atsId } }
    val projectUpsList = viewModel?.upsList?.collectAsState()?.value ?: emptyList()
    val upsOptions = remember(projectUpsList) { listOf("None") + projectUpsList.map { it.upsId } }
    var phases by rememberSaveable { mutableStateOf(circuit?.phases ?: "3P+N") }
    var voltageV by rememberSaveable { mutableStateOf(circuit?.voltageV?.toString() ?: defaultV3P.toString()) }
    var pf by rememberSaveable { mutableStateOf(circuit?.pf?.toString() ?: "0.85") }
    var safetyFactor by rememberSaveable { mutableStateOf(circuit?.safetyFactor?.toString() ?: "1.0") }
    var manufacturer by rememberSaveable { mutableStateOf(circuit?.manufacturer ?: "Elsewedy") }
    val initialCableType = remember(circuit, defaultConductor, defaultInsulation, defaultCoreConfig) {
        if (circuit != null && circuit.cableType.isNotBlank()) {
            resolveCableType(circuit.cableType, defaultCoreConfig, defaultConductor, defaultInsulation)
        } else {
            resolveCableType("$defaultConductor/$defaultInsulation/PVC", defaultCoreConfig, defaultConductor, defaultInsulation)
        }
    }
    var cableType by rememberSaveable { mutableStateOf(initialCableType) }
    var installation by rememberSaveable { mutableStateOf(circuit?.installation ?: "Laid in Ducts") }
    var parallelRuns by rememberSaveable { mutableStateOf(circuit?.parallelRuns?.toString() ?: "1") }
    var airTempC by rememberSaveable { mutableStateOf(circuit?.airTempC?.toString() ?: "40") }
    var gndTempC by rememberSaveable { mutableStateOf(circuit?.gndTempC?.toString() ?: "40") }
    var burialDepth_m by rememberSaveable { mutableStateOf(circuit?.burialDepth_m?.toString() ?: "0.6") }
    var soilRes by rememberSaveable { mutableStateOf(circuit?.soilRes?.toString() ?: "125") }
    var noCircuits by rememberSaveable { mutableStateOf(circuit?.noCircuits?.toString() ?: "1") }
    var formation by rememberSaveable { mutableStateOf(circuit?.formation ?: "Flat") }
    var spacing by rememberSaveable { mutableStateOf(circuit?.spacing ?: "Touching") }
    var length_m by rememberSaveable { mutableStateOf(circuit?.length_m?.toString() ?: "30") }
    var maxVDpct by rememberSaveable { mutableStateOf(if (circuit != null) (circuit.maxVDpct * 100.0).toString() else defaultMaxVdPower.toString()) }
    var loadNature by rememberSaveable { mutableStateOf(circuit?.loadNature ?: "Static") }
    var startingMethod by rememberSaveable { mutableStateOf(circuit?.startingMethod ?: "DOL") }
    var startingCurrentMultiplier by rememberSaveable { mutableStateOf(circuit?.startingCurrentMultiplier?.toString() ?: "") }
    var isHarmonic by rememberSaveable { mutableStateOf(circuit?.isHarmonic ?: false) }
    var panelType by rememberSaveable { mutableStateOf(circuit?.panelType?.ifEmpty { "LOAD" } ?: "LOAD") }
    var loadCategory by rememberSaveable { mutableStateOf(circuit?.loadCategory) }
    var demandFactor by rememberSaveable { mutableStateOf(circuit?.demandFactor?.toString() ?: "1.0") }

    var busbarRatingA by rememberSaveable { mutableStateOf(circuit?.busbarRatingA?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var mainIncomingType by rememberSaveable { mutableStateOf(circuit?.mainIncomingType ?: "") }
    var mainIncomingCount by rememberSaveable { mutableStateOf(circuit?.mainIncomingCount?.toString() ?: "") }
    var ipRating by rememberSaveable { mutableStateOf(circuit?.ipRating ?: "") }
    var panelLocation by rememberSaveable { mutableStateOf(circuit?.panelLocation ?: "") }
    var cableEntry by rememberSaveable { mutableStateOf(circuit?.cableEntry ?: "") }
    var feederDescription by rememberSaveable { mutableStateOf(circuit?.feederDescription ?: "") }
    var numberOfWays by rememberSaveable { mutableStateOf(circuit?.numberOfWays?.toString() ?: "") }
    var feederType by rememberSaveable { mutableStateOf(circuit?.feederType ?: "Cable") }
    var buswayMfg by rememberSaveable { mutableStateOf(circuit?.buswayMfg ?: "Elsewedy") }
    var buswayModel by rememberSaveable { mutableStateOf(circuit?.buswayModel ?: "Power Link") }
    var isPanelHeaderExpanded by rememberSaveable { mutableStateOf(circuit?.panelType != null && circuit.panelType != "LOAD") }

    val parentOptions = remember(existingCircuits, circuit) {
        val validBoards = if (circuit != null) {
            val boardMap = existingCircuits.associateBy { it.id }
            existingCircuits.filter { candidate ->
                if (candidate.id == circuit.id) return@filter false
                var pId = candidate.parentId
                while (pId != null) {
                    if (pId == circuit.id) return@filter false
                    pId = boardMap[pId]?.parentId
                }
                true
            }
        } else existingCircuits
        val candidatePanels = validBoards.filter { c ->
            val pType = c.panelType?.trim()?.uppercase() ?: ""
            pType in setOf("MDB", "SMDB", "DB", "MCC", "PANEL", "OTHER") ||
                    existingCircuits.any { child -> child.parentId == c.id || child.fedFrom.trim().equals(c.circuitName.trim(), ignoreCase = true) }
        }
        listOf("Transformer", "Generator", "Grid", "ATS") + candidatePanels.map { it.circuitName }.distinct()
    }
    val catalogTypeOptions = remember(manufacturer) {
        Catalog.cableTypes.filter { type ->
            Catalog.cableCatalog.any { catalog ->
                catalog.typeCode == type.code && catalog.mfg.trim().equals(manufacturer.trim(), ignoreCase = true)
            }
        }.map { it.cableType }.distinct().sorted()
    }
    val cableTypeOptions = if (catalogTypeOptions.isNotEmpty()) {
        catalogTypeOptions
    } else {
        listOf(cableType)
    }

    // Dropdowns open/close state
    var expandedLoadType by rememberSaveable { mutableStateOf(false) }
    var expandedPhases by rememberSaveable { mutableStateOf(false) }
    var expandedInstallation by rememberSaveable { mutableStateOf(false) }
    var expandedFormation by rememberSaveable { mutableStateOf(false) }
    var expandedSpacing by rememberSaveable { mutableStateOf(false) }
    var expandedFedFrom by rememberSaveable { mutableStateOf(false) }
    var expandedCableType by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (circuit == null) "Add New Circuit" else "Edit Circuit ${circuit.circuitName}",
                color = ThemeTextColor,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        containerColor = DarkCardBg,
        text = {
            var isIdentityExpanded by rememberSaveable { mutableStateOf(true) }
            var isLoadExpanded by rememberSaveable { mutableStateOf(true) }
            var isCableExpanded by rememberSaveable { mutableStateOf(false) }
            var isInstallationExpanded by rememberSaveable { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = StatusFail,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // --- GROUP 1: IDENTITY & SOURCE ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isIdentityExpanded = !isIdentityExpanded }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("1. Identity & Source", style = CardTitle, color = SleekPrimary)
                    Icon(
                        imageVector = if (isIdentityExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = "Expand identity",
                        tint = SleekPrimary
                    )
                }
                HorizontalDivider(color = BorderColor)

                if (isIdentityExpanded) {
                    VoltAtlasTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            errorMessage = null
                        },
                        label = "Circuit / Board Name",
                        modifier = Modifier.testTag("input_circuit_name")
                    )

                    VoltAtlasDropdown(
                        value = if (panelType.isEmpty()) "LOAD" else panelType,
                        options = listOf("LOAD", "DB", "SMDB", "MDB", "MCC", "PANEL", "OTHER"),
                        onSelect = { panelType = it },
                        label = "Board Type (Classification)"
                    )

                    VoltAtlasDropdown(
                        value = fedFrom,
                        options = parentOptions,
                        onSelect = { item ->
                            fedFrom = item
                            if (item == "Transformer") {
                                if (transformers.isEmpty()) {
                                    val newTxId = viewModel?.addNewTransformerInline() ?: "TX-01"
                                    transformerId = newTxId
                                } else if (transformerId.isEmpty() || !transformers.any { it.transformerId == transformerId }) {
                                    transformerId = transformers.firstOrNull()?.transformerId ?: ""
                                }
                            } else {
                                val parentCirc = existingCircuits.firstOrNull { it.circuitName == item }
                                if (parentCirc != null) {
                                    transformerId = parentCirc.transformerId
                                }
                            }
                        },
                        label = "Fed From (Parent Source / Board)",
                        modifier = Modifier.testTag("input_fed_from")
                    )

                    val parentBoard = remember(fedFrom, existingCircuits) {
                        existingCircuits.firstOrNull { it.circuitName == fedFrom }
                    }
                    val computedLevel = remember(parentBoard, fedFrom) {
                        if (parentBoard == null) 0 else (parentBoard.level ?: 0) + 1
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Computed Hierarchy Depth:", style = BodySecondary, color = SlateTextSecondary)
                        VoltAtlasStatusBadge(
                            text = "Level $computedLevel ${if (computedLevel == 0) "(Root)" else ""}",
                            color = SleekPrimary
                        )
                    }

                    if (fedFrom == "Transformer") {
                        val selectedTxDisplay = remember(transformers, transformerId) {
                            if (transformers.isEmpty()) {
                                "➕ Add Transformer"
                            } else if (transformerId.isEmpty()) {
                                transformers.firstOrNull()?.transformerId ?: "➕ Add Transformer"
                            } else {
                                transformerId
                            }
                        }

                        VoltAtlasDropdown(
                            value = selectedTxDisplay,
                            options = txOptions,
                            onSelect = { selected ->
                                if (selected == "➕ Add Transformer" || selected == "➕ Add Another Transformer") {
                                    val newTxId = viewModel?.addNewTransformerInline() ?: "TX-01"
                                    transformerId = newTxId
                                } else {
                                    transformerId = selected
                                }
                            },
                            label = "Transformer ID",
                            modifier = Modifier.testTag("input_transformer_id")
                        )
                    }
                }

                // --- GROUP 2: LOAD ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isLoadExpanded = !isLoadExpanded }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("2. Load Specifications", style = CardTitle, color = SleekPrimary)
                    Icon(
                        imageVector = if (isLoadExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = "Expand load",
                        tint = SleekPrimary
                    )
                }
                HorizontalDivider(color = BorderColor)

                if (isLoadExpanded) {
                    val categoryOptions = listOf("Unspecified (None)") + LoadCategories.OPTIONS.map { it.label }
                    val currentCategoryLabel = LoadCategories.findByCode(loadCategory)?.label ?: "Unspecified (None)"

                    VoltAtlasDropdown(
                        value = currentCategoryLabel,
                        options = categoryOptions,
                        onSelect = { selectedLabel ->
                            if (selectedLabel.startsWith("Unspecified")) {
                                loadCategory = null
                            } else {
                                val opt = LoadCategories.OPTIONS.firstOrNull { it.label == selectedLabel }
                                if (opt != null) {
                                    loadCategory = opt.code
                                    pf = opt.defaultPf.toString()
                                    demandFactor = opt.defaultDf.toString()
                                }
                            }
                        },
                        label = "Load Category (Classification & Default Suggestion)"
                    )

                    if (loadCategory == "MTR") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = StatusWarning.copy(alpha = 0.12f)),
                            border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = StatusWarning,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Note: Motor category affects load reporting only. Manually verify breaker curve (C/D) and starting-current cable sizing per IEC 60947-4-1 — this is not yet automated.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusWarning,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    VoltAtlasDropdown(
                        value = loadNature,
                        options = listOf("Static", "Motor"),
                        onSelect = { loadNature = it },
                        label = "Load Nature"
                    )

                    if (loadNature == "Motor") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                VoltAtlasDropdown(
                                    value = startingMethod,
                                    options = listOf("DOL", "Star-Delta", "Soft-Starter", "VFD"),
                                    onSelect = { startingMethod = it },
                                    label = "Starting Method"
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                VoltAtlasTextField(
                                    value = startingCurrentMultiplier,
                                    onValueChange = { startingCurrentMultiplier = it },
                                    label = "Start Current Multiplier",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            VoltAtlasTextField(
                                value = loadKVA,
                                onValueChange = { loadKVA = it },
                                label = "Load",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                trailingIcon = { Text("kVA", style = Caption, color = SlateTextSecondary, modifier = Modifier.padding(end = 12.dp)) },
                                modifier = Modifier.testTag("input_load_kva")
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            VoltAtlasTextField(
                                value = pf,
                                onValueChange = { pf = it },
                                label = "Power Factor",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.testTag("input_pf")
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            VoltAtlasTextField(
                                value = demandFactor,
                                onValueChange = { demandFactor = it },
                                label = "Demand Factor (DF)",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.testTag("input_demand_factor")
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            VoltAtlasTextField(
                                value = voltageV,
                                onValueChange = { voltageV = it },
                                label = "Voltage",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                trailingIcon = { Text("V", style = Caption, color = SlateTextSecondary, modifier = Modifier.padding(end = 12.dp)) },
                                modifier = Modifier.testTag("input_voltage")
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            VoltAtlasTextField(
                                value = safetyFactor,
                                onValueChange = { safetyFactor = it },
                                label = "Safety Factor",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.testTag("input_safety_factor")
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            VoltAtlasDropdown(
                                value = phases,
                                options = listOf("3P+N", "1P+N", "3P"),
                                onSelect = { phases = it },
                                label = "Phases"
                            )
                        }
                    }

                    VoltAtlasDropdown(
                        value = loadType,
                        options = listOf("NORMAL", "EMERGENCY", "UPS", "EMERGENCY + UPS"),
                        onSelect = { loadType = it },
                        label = "Supply Category (NORMAL / EMERGENCY / UPS)"
                    )

                    if (loadType.contains("EMERGENCY")) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                VoltAtlasDropdown(
                                    value = assignedAtsId,
                                    options = atsOptions,
                                    onSelect = { assignedAtsId = it },
                                    label = "Assigned ATS Source"
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                VoltAtlasDropdown(
                                    value = assignedGeneratorId,
                                    options = generatorOptions,
                                    onSelect = { assignedGeneratorId = it },
                                    label = "Assigned Generator Source"
                                )
                            }
                        }
                    }

                    if (loadType.contains("UPS")) {
                        Spacer(modifier = Modifier.height(8.dp))
                        VoltAtlasDropdown(
                            value = assignedUpsId,
                            options = upsOptions,
                            onSelect = { assignedUpsId = it },
                            label = "Assigned UPS Source"
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = isHarmonic,
                            onCheckedChange = { isHarmonic = it },
                            modifier = Modifier.testTag("checkbox_is_harmonic")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Non-linear / harmonic-heavy load (derates neutral)",
                            style = BodySecondary,
                            color = ThemeTextColor
                        )
                    }
                }

                // --- GROUP 3: CABLE PREFERENCES ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCableExpanded = !isCableExpanded }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("3. Cable Preferences", style = CardTitle, color = SleekPrimary)
                    Icon(
                        imageVector = if (isCableExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = "Expand cable",
                        tint = SleekPrimary
                    )
                }
                HorizontalDivider(color = BorderColor)

                if (isCableExpanded) {
                    VoltAtlasDropdown(
                        value = feederType,
                        options = listOf("Cable", "Busway"),
                        onSelect = { feederType = it },
                        label = "Feeder Type",
                        modifier = Modifier.testTag("input_feeder_type")
                    )

                    if (feederType == "Busway") {
                        val buswayMfgOptions = remember {
                            val dynamicList = Catalog.busways.map { it.mfg.trim() }.filter { it.isNotBlank() }.distinct().sorted()
                            if (dynamicList.isEmpty()) listOf("Elsewedy") else dynamicList
                        }
                        VoltAtlasDropdown(
                            value = buswayMfg,
                            options = buswayMfgOptions,
                            onSelect = { buswayMfg = it },
                            label = "Busway Manufacturer",
                            modifier = Modifier.testTag("input_busway_mfg")
                        )

                        val buswayModelOptions = remember(buswayMfg) {
                            val dynamicModels = Catalog.busways
                                .filter { buswayMfg.isBlank() || buswayMfg == "All / Any" || it.mfg.equals(buswayMfg, ignoreCase = true) }
                                .map { it.model.trim() }
                                .filter { it.isNotBlank() }
                                .distinct()
                                .sorted()
                            if (dynamicModels.isEmpty()) listOf("All / Any") else (dynamicModels + "All / Any").distinct()
                        }
                        VoltAtlasDropdown(
                            value = buswayModel,
                            options = buswayModelOptions,
                            onSelect = { buswayModel = it },
                            label = "Busway Model (Conductor / Family)",
                            modifier = Modifier.testTag("input_busway_model")
                        )
                    }

                    val manufacturerOptions = remember {
                        val dynamicList = Catalog.cableCatalog
                            .map { it.mfg.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .sorted()
                        if (dynamicList.isEmpty()) listOf("Elsewedy", "IEC-Generic") else dynamicList
                    }

                    VoltAtlasDropdown(
                        value = manufacturer,
                        options = manufacturerOptions,
                        onSelect = { selected ->
                            manufacturer = selected
                            val validTypes = Catalog.cableTypes.filter { type ->
                                Catalog.cableCatalog.any { cat ->
                                    cat.typeCode == type.code && cat.mfg.trim().equals(selected.trim(), ignoreCase = true)
                                }
                            }.map { it.cableType }.distinct().sorted()
                            if (validTypes.isNotEmpty() && !validTypes.contains(cableType)) {
                                cableType = validTypes.firstOrNull() ?: ""
                            }
                        },
                        label = "Manufacturer / Cable Source",
                        modifier = Modifier.testTag("input_manufacturer")
                    )

                    // Progressive Cascading Filter Cable Selector
                    val parseFamilyInfo = remember {
                        { rawType: String ->
                            val resolved = resolveCableType(rawType, defaultCoreConfig, defaultConductor, defaultInsulation)
                            val ct = Catalog.cableTypes.firstOrNull { it.cableType.trim().equals(resolved.trim(), ignoreCase = true) }
                                ?: Catalog.cableTypes.firstOrNull { it.cableType.trim().equals(rawType.trim(), ignoreCase = true) }
                            val cond = ct?.conductor ?: if (rawType.contains("Al", ignoreCase = true)) "Al" else "Cu"
                            val mat = if (cond.contains("Al", ignoreCase = true)) "Aluminium" else "Copper"
                            val arm = ct?.armour ?: if (rawType.contains("SWA", ignoreCase = true)) "SWA" else "None"
                            val armGroup = if (arm.equals("None", ignoreCase = true) || arm.isBlank()) "Unarmoured" else "Armoured"
                            val rawCores = ct?.cores ?: rawType
                            val coresConfig = when {
                                rawCores.contains("1C") || rawCores.contains("Single", ignoreCase = true) -> "1C"
                                rawCores.contains("2C") -> "2C"
                                rawCores.contains("3.5C") -> "3.5C"
                                rawCores.contains("3C") -> "3C"
                                rawCores.contains("4C") -> "4C"
                                else -> defaultCoreConfig.ifBlank { "4C" }
                            }
                            val ins = ct?.insulation ?: if (rawType.contains("XLPE", ignoreCase = true)) "XLPE" else "PVC"
                            CableFamilyInfo(
                                originalType = ct?.cableType ?: rawType,
                                material = mat,
                                armourGroup = armGroup,
                                exactArmour = arm,
                                coresConfig = coresConfig,
                                insulation = ins,
                                displayLabel = "$mat • $armGroup (${if (arm != "None") arm else "Plain"}) • $coresConfig • $ins"
                            )
                        }
                    }

                    val initialFamilyInfo = remember(cableType) { parseFamilyInfo(cableType) }
                    var filterMat by remember { mutableStateOf(initialFamilyInfo.material) }
                    var filterArmour by remember { mutableStateOf(initialFamilyInfo.armourGroup) }
                    var filterCores by remember { mutableStateOf(initialFamilyInfo.coresConfig) }
                    var filterQuery by remember { mutableStateOf("") }

                    val allCatalogFamilies = remember(manufacturer) {
                        val matching = Catalog.cableTypes.filter { type ->
                            Catalog.cableCatalog.any { cat -> cat.typeCode == type.code && cat.mfg.trim().equals(manufacturer.trim(), ignoreCase = true) }
                        }.map { it.cableType }.distinct()
                        if (matching.isNotEmpty()) matching else listOf(cableType)
                    }

                    val allParsedFamilies = remember(allCatalogFamilies) {
                        allCatalogFamilies.map { parseFamilyInfo(it) }
                    }

                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Conductor Material Filter", color = SlateTextSecondary, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Copper", "Aluminium").forEach { mat ->
                                val isAvail = allParsedFamilies.any { it.material == mat }
                                FilterChip(
                                    selected = filterMat == mat,
                                    onClick = {
                                        if (isAvail) {
                                            filterMat = mat
                                            val candidate = allParsedFamilies.firstOrNull { it.material == mat && it.armourGroup == filterArmour && it.coresConfig == filterCores }
                                            if (candidate != null) {
                                                cableType = candidate.originalType
                                            } else {
                                                val alt = allParsedFamilies.firstOrNull { it.material == mat }
                                                if (alt != null) {
                                                    filterArmour = alt.armourGroup
                                                    filterCores = alt.coresConfig
                                                    cableType = alt.originalType
                                                }
                                            }
                                        }
                                    },
                                    label = { Text(if (isAvail) mat else "$mat (N/A)", fontSize = 11.sp) },
                                    enabled = isAvail,
                                    modifier = Modifier.weight(1f).testTag("filter_mat_$mat")
                                )
                            }
                        }

                        Text("Armour Construction Filter", color = SlateTextSecondary, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Unarmoured", "Armoured").forEach { arm ->
                                val isAvail = allParsedFamilies.any { it.material == filterMat && it.armourGroup == arm }
                                FilterChip(
                                    selected = filterArmour == arm,
                                    onClick = {
                                        if (isAvail) {
                                            filterArmour = arm
                                            val candidate = allParsedFamilies.firstOrNull { it.material == filterMat && it.armourGroup == arm && it.coresConfig == filterCores }
                                            if (candidate != null) {
                                                cableType = candidate.originalType
                                            } else {
                                                val alt = allParsedFamilies.firstOrNull { it.material == filterMat && it.armourGroup == arm }
                                                if (alt != null) {
                                                    filterCores = alt.coresConfig
                                                    cableType = alt.originalType
                                                }
                                            }
                                        }
                                    },
                                    label = { Text(if (isAvail) arm else "$arm (N/A)", fontSize = 11.sp) },
                                    enabled = isAvail,
                                    modifier = Modifier.weight(1f).testTag("filter_armour_$arm")
                                )
                            }
                        }

                        val availableCores = remember(filterMat, filterArmour, allParsedFamilies) {
                            allParsedFamilies.filter { it.material == filterMat && it.armourGroup == filterArmour }
                                .map { it.coresConfig }.distinct()
                        }

                        VoltAtlasDropdown(
                            value = when (filterCores) {
                                "1C" -> "1C — Single Core"
                                "2C" -> "2C — Single Phase"
                                "3C" -> "3C — Three Phase, No Neutral"
                                "3.5C" -> "3.5C — Three Phase + Reduced Neutral"
                                "4C" -> "4C — Three Phase + Full Neutral"
                                else -> filterCores
                            },
                            options = availableCores,
                            onSelect = { core ->
                                filterCores = core
                                val cand = allParsedFamilies.firstOrNull { it.material == filterMat && it.armourGroup == filterArmour && it.coresConfig == core }
                                if (cand != null) {
                                    cableType = cand.originalType
                                }
                            },
                            label = "Core Configuration",
                            modifier = Modifier.testTag("input_core_config")
                        )

                        val matchingFamilies = remember(filterMat, filterArmour, filterCores, filterQuery, allParsedFamilies) {
                            allParsedFamilies.filter {
                                it.material == filterMat &&
                                it.armourGroup == filterArmour &&
                                it.coresConfig == filterCores &&
                                (filterQuery.isBlank() || it.originalType.contains(filterQuery, ignoreCase = true) || it.displayLabel.contains(filterQuery, ignoreCase = true))
                            }
                        }

                        if (matchingFamilies.size > 3) {
                            VoltAtlasTextField(
                                value = filterQuery,
                                onValueChange = { filterQuery = it },
                                label = "Search compatible families..."
                            )
                        }

                        val currentParsed = remember(cableType) { parseFamilyInfo(cableType) }
                        VoltAtlasDropdown(
                            value = currentParsed.displayLabel,
                            options = matchingFamilies.map { it.originalType },
                            onSelect = { selectedType ->
                                cableType = selectedType
                            },
                            label = "Selected Cable Family",
                            modifier = Modifier.testTag("input_cable_type")
                        )
                    }

                    VoltAtlasDropdown(
                        value = installation,
                        options = listOf("Laid in Ducts", "Laid Direct in Ground", "Laid in Free Air"),
                        onSelect = { installation = it },
                        label = "Installation Method"
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            VoltAtlasTextField(
                                value = parallelRuns,
                                onValueChange = { parallelRuns = it },
                                label = "Parallel Runs",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.testTag("input_parallel_runs")
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            VoltAtlasTextField(
                                value = length_m,
                                onValueChange = { length_m = it },
                                label = "Length",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                trailingIcon = { Text("m", style = Caption, color = SlateTextSecondary, modifier = Modifier.padding(end = 12.dp)) },
                                modifier = Modifier.testTag("input_length")
                            )
                        }
                    }

                    VoltAtlasTextField(
                        value = maxVDpct,
                        onValueChange = { maxVDpct = it },
                        label = "Max Voltage Drop (%)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.testTag("input_max_vd")
                    )
                }

                // --- GROUP 4: INSTALLATION CONDITIONS ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isInstallationExpanded = !isInstallationExpanded }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("4. Installation Conditions", style = CardTitle, color = SleekPrimary)
                    Icon(
                        imageVector = if (isInstallationExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = "Expand installation",
                        tint = SleekPrimary
                    )
                }
                HorizontalDivider(color = BorderColor)

                if (isInstallationExpanded) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            VoltAtlasTextField(
                                value = airTempC,
                                onValueChange = { airTempC = it },
                                label = "Air Temp",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                trailingIcon = { Text("°C", style = Caption, color = SlateTextSecondary, modifier = Modifier.padding(end = 12.dp)) },
                                modifier = Modifier.testTag("input_air_temp")
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            VoltAtlasTextField(
                                value = gndTempC,
                                onValueChange = { gndTempC = it },
                                label = "Gnd Temp",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                trailingIcon = { Text("°C", style = Caption, color = SlateTextSecondary, modifier = Modifier.padding(end = 12.dp)) },
                                modifier = Modifier.testTag("input_gnd_temp")
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            VoltAtlasTextField(
                                value = burialDepth_m,
                                onValueChange = { burialDepth_m = it },
                                label = "Burial Depth",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                trailingIcon = { Text("m", style = Caption, color = SlateTextSecondary, modifier = Modifier.padding(end = 12.dp)) },
                                modifier = Modifier.testTag("input_burial_depth")
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            VoltAtlasTextField(
                                value = soilRes,
                                onValueChange = { soilRes = it },
                                label = "Soil Res",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                trailingIcon = { Text("Ωm", style = Caption, color = SlateTextSecondary, modifier = Modifier.padding(end = 12.dp)) },
                                modifier = Modifier.testTag("input_soil_res")
                            )
                        }
                    }

                    VoltAtlasTextField(
                        value = noCircuits,
                        onValueChange = { noCircuits = it },
                        label = "Grouping (No. of Circuits)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.testTag("input_no_circuits")
                    )

                    VoltAtlasDropdown(
                        value = formation,
                        options = listOf("Flat", "Trefoil"),
                        onSelect = { formation = it },
                        label = "Formation"
                    )

                    VoltAtlasDropdown(
                        value = spacing,
                        options = listOf("Touching", "0.15m", "0.30m"),
                        onSelect = { spacing = it },
                        label = "Spacing"
                    )
                }

                if (panelType != "LOAD" && panelType.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPanelHeaderExpanded = !isPanelHeaderExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "5. Panel Header Equipment Data",
                            style = CardTitle,
                            color = SleekPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { isPanelHeaderExpanded = !isPanelHeaderExpanded }) {
                            Icon(
                                imageVector = if (isPanelHeaderExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle Panel Header Section",
                                tint = SleekPrimary
                            )
                        }
                    }
                    HorizontalDivider(color = BorderColor)

                    if (isPanelHeaderExpanded) {
                        val uiStateVal = viewModel?.uiState?.collectAsState()?.value as? UiState.Success
                        val calculatedIscKA = if (circuit != null) uiStateVal?.shortCircuitResults?.get(circuit.id)?.iscKA else null
                        val scText = calculatedIscKA?.let { String.format(java.util.Locale.US, "%.2f kA", it) } ?: "N/A (Calculated after save)"

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                VoltAtlasTextField(
                                    value = busbarRatingA,
                                    onValueChange = { busbarRatingA = it },
                                    label = "Busbar Rating (A)",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.testTag("input_edit_dialog_busbar_rating")
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                VoltAtlasTextField(
                                    value = scText,
                                    onValueChange = {},
                                    label = "Short Circuit (kA) [Calculated]",
                                    readOnly = true,
                                    modifier = Modifier.testTag("display_edit_dialog_sc_rating")
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                VoltAtlasDropdown(
                                    value = mainIncomingType,
                                    options = listOf("", "MCCB", "ACB", "LBS", "Switch-Disconnector", "Fuse-Switch"),
                                    onSelect = { mainIncomingType = it },
                                    label = "Main Incoming Type",
                                    modifier = Modifier.testTag("input_edit_dialog_main_incoming_type")
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                VoltAtlasTextField(
                                    value = mainIncomingCount,
                                    onValueChange = { mainIncomingCount = it },
                                    label = "Incoming Count",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.testTag("input_edit_dialog_main_incoming_count")
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                VoltAtlasDropdown(
                                    value = ipRating,
                                    options = listOf("", "IP20", "IP41", "IP54", "IP55", "IP65"),
                                    onSelect = { ipRating = it },
                                    label = "IP Rating",
                                    modifier = Modifier.testTag("input_edit_dialog_ip_rating")
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                VoltAtlasDropdown(
                                    value = cableEntry,
                                    options = listOf("", "Top", "Bottom", "Top/Bottom"),
                                    onSelect = { cableEntry = it },
                                    label = "Cable Entry",
                                    modifier = Modifier.testTag("input_edit_dialog_cable_entry")
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                VoltAtlasTextField(
                                    value = panelLocation,
                                    onValueChange = { panelLocation = it },
                                    label = "Location",
                                    modifier = Modifier.testTag("input_edit_dialog_location")
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                VoltAtlasTextField(
                                    value = numberOfWays,
                                    onValueChange = { numberOfWays = it },
                                    label = "Number of Ways",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.testTag("input_edit_dialog_number_of_ways")
                                )
                            }
                        }

                        VoltAtlasTextField(
                            value = feederDescription,
                            onValueChange = { feederDescription = it },
                            label = "Feeder / Supply Cable Description",
                            modifier = Modifier.testTag("input_edit_dialog_feeder_description")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isEmpty()) {
                        errorMessage = "Circuit/Panel name cannot be empty."
                        return@Button
                    }
                    val isDuplicateName = existingCircuits.any { c ->
                        (circuit == null || circuit.id == 0 || c.id != circuit.id) && c.circuitName.trim().equals(trimmedName, ignoreCase = true)
                    }
                    if (isDuplicateName) {
                        errorMessage = "A circuit/panel named '$trimmedName' already exists — choose a different name."
                        return@Button
                    }

                    val doubleLengthRaw = length_m.toDoubleOrNull() ?: 30.0
                    if (doubleLengthRaw <= 0) {
                        errorMessage = "Cable length must be greater than zero."
                        return@Button
                    }
                    val doubleLength = doubleLengthRaw
                    val doubleKva = kotlin.math.max(0.0, loadKVA.toDoubleOrNull() ?: 10.0)
                    val doublePf = (pf.toDoubleOrNull() ?: 0.8).coerceIn(0.1, 1.0)
                    val doubleDemandFactor = (demandFactor.toDoubleOrNull() ?: 1.0).coerceIn(0.0, 1.0)
                    val doubleSafety = kotlin.math.max(1.0, safetyFactor.toDoubleOrNull() ?: 1.0)
                    val intVoltage = voltageV.toIntOrNull() ?: 400
                    val intParallel = parallelRuns.toIntOrNull() ?: 1
                    val doubleMaxVD = (maxVDpct.toDoubleOrNull() ?: 3.0) / 100.0
                    val intAirTemp = airTempC.toIntOrNull() ?: 40
                    val intGndTemp = gndTempC.toIntOrNull() ?: 40
                    val doubleDepth = burialDepth_m.toDoubleOrNull() ?: 0.6
                    val intSoilRes = soilRes.toIntOrNull() ?: 125
                    val intNoCircuits = noCircuits.toIntOrNull() ?: 1

                    val isGen = loadType == "EMERGENCY" || loadType == "EMERGENCY + UPS"
                    val isUps = loadType == "UPS" || loadType == "EMERGENCY + UPS"

                    val finalTxId = if (fedFrom == "Transformer" && (transformerId.isEmpty() || transformerId.contains("Add Transformer"))) {
                        if (transformers.isNotEmpty()) {
                            transformers.firstOrNull()?.transformerId ?: ""
                        } else {
                            viewModel?.addNewTransformerInline() ?: "TX-01"
                        }
                    } else {
                        transformerId
                    }

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

                    val savedCircuit = circuit?.copy(
                        circuitName = name.trim().takeIf { it.isNotEmpty() } ?: "Unnamed Circuit",
                        loadKVA = doubleKva,
                        fedFrom = fedFrom,
                        transformerId = finalTxId,
                        loadType = loadType,
                        phases = phases,
                        voltageV = intVoltage,
                        pf = doublePf,
                        safetyFactor = doubleSafety,
                        manufacturer = manufacturer,
                        cableType = cableType,
                        installation = installation,
                        parallelRuns = intParallel,
                        airTempC = intAirTemp,
                        gndTempC = intGndTemp,
                        burialDepth_m = doubleDepth,
                        soilRes = intSoilRes,
                        noCircuits = intNoCircuits,
                        formation = formation,
                        spacing = spacing,
                        length_m = doubleLength,
                        maxVDpct = doubleMaxVD,
                        generatorIncluded = isGen,
                        atsIncluded = isGen,
                        assignedGeneratorId = assignedGeneratorId.takeIf { it.isNotBlank() && it != "None" },
                        assignedAtsId = assignedAtsId.takeIf { it.isNotBlank() && it != "None" },
                        assignedUpsId = assignedUpsId.takeIf { it.isNotBlank() && it != "None" },
                        upsBacked = isUps,
                        isHarmonic = isHarmonic,
                        loadNature = loadNature,
                        startingMethod = if (loadNature == "Motor") startingMethod else null,
                        startingCurrentMultiplier = if (loadNature == "Motor") startingCurrentMultiplier.toDoubleOrNull() else null,
                        panelType = panelType,
                        parentId = calculatedParentId,
                        level = calculatedLevel,
                        sourceType = calculatedSourceType,
                        demandFactor = doubleDemandFactor,
                        loadCategory = loadCategory,
                        busbarRatingA = busbarRatingA.toDoubleOrNull(),
                        scRatingKA = circuit?.scRatingKA,
                        mainIncomingType = mainIncomingType.ifBlank { null },
                        mainIncomingCount = mainIncomingCount.toIntOrNull(),
                        ipRating = ipRating.ifBlank { null },
                        panelLocation = panelLocation.ifBlank { null },
                        cableEntry = cableEntry.ifBlank { null },
                        feederDescription = feederDescription.ifBlank { null },
                        numberOfWays = numberOfWays.toIntOrNull(),
                        feederType = feederType,
                        buswayMfg = if (feederType == "Busway") buswayMfg else null,
                        buswayModel = if (feederType == "Busway") buswayModel else null
                    ) ?: CircuitEntity(
                        circuitName = name.trim().takeIf { it.isNotEmpty() } ?: "Unnamed Circuit",
                        loadKVA = doubleKva,
                        fedFrom = fedFrom,
                        transformerId = finalTxId,
                        loadType = loadType,
                        phases = phases,
                        voltageV = intVoltage,
                        pf = doublePf,
                        safetyFactor = doubleSafety,
                        manufacturer = manufacturer,
                        cableType = cableType,
                        installation = installation,
                        parallelRuns = intParallel,
                        airTempC = intAirTemp,
                        gndTempC = intGndTemp,
                        burialDepth_m = doubleDepth,
                        soilRes = intSoilRes,
                        noCircuits = intNoCircuits,
                        formation = formation,
                        spacing = spacing,
                        length_m = doubleLength,
                        maxVDpct = doubleMaxVD,
                        generatorIncluded = isGen,
                        atsIncluded = isGen,
                        assignedGeneratorId = assignedGeneratorId.takeIf { it.isNotBlank() && it != "None" },
                        assignedAtsId = assignedAtsId.takeIf { it.isNotBlank() && it != "None" },
                        assignedUpsId = assignedUpsId.takeIf { it.isNotBlank() && it != "None" },
                        upsBacked = isUps,
                        isHarmonic = isHarmonic,
                        loadNature = loadNature,
                        startingMethod = if (loadNature == "Motor") startingMethod else null,
                        startingCurrentMultiplier = if (loadNature == "Motor") startingCurrentMultiplier.toDoubleOrNull() else null,
                        panelType = panelType,
                        parentId = calculatedParentId,
                        level = calculatedLevel,
                        sourceType = calculatedSourceType,
                        demandFactor = doubleDemandFactor,
                        loadCategory = loadCategory,
                        busbarRatingA = busbarRatingA.toDoubleOrNull(),
                        scRatingKA = circuit?.scRatingKA,
                        mainIncomingType = mainIncomingType.ifBlank { null },
                        mainIncomingCount = mainIncomingCount.toIntOrNull(),
                        ipRating = ipRating.ifBlank { null },
                        panelLocation = panelLocation.ifBlank { null },
                        cableEntry = cableEntry.ifBlank { null },
                        feederDescription = feederDescription.ifBlank { null },
                        numberOfWays = numberOfWays.toIntOrNull(),
                        feederType = feederType,
                        buswayMfg = if (feederType == "Busway") buswayMfg else null,
                        buswayModel = if (feederType == "Busway") buswayModel else null
                    )

                    onSave(savedCircuit)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                modifier = Modifier.testTag("save_circuit_btn")
            ) {
                Text("Save", color = MaterialTheme.colorScheme.onPrimary)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Text("Cancel", color = ThemeTextColor)
            }
        }
    )
}
