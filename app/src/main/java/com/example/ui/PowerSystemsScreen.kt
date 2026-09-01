@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.engine.*
import com.example.ui.components.VoltAtlasDropdown
import com.example.ui.designsystem.components.*
import com.example.ui.designsystem.theme.*
import kotlin.math.abs

private val STANDARD_GENERATOR_SIZES_KVA = listOf(
    10.0, 15.0, 20.0, 25.0, 30.0, 40.0, 50.0, 62.5, 75.0, 100.0,
    125.0, 150.0, 200.0, 250.0, 300.0, 350.0, 400.0, 500.0, 625.0,
    750.0, 800.0, 1000.0, 1250.0, 1500.0, 2000.0, 2500.0
)

private val STANDARD_ATS_SIZES_AMP = listOf(
    32.0, 45.0, 63.0, 100.0, 125.0, 160.0, 200.0, 250.0, 400.0,
    630.0, 800.0, 1000.0, 1250.0, 1600.0, 2000.0, 3200.0
)

private val STANDARD_UPS_SIZES_KVA = listOf(
    1.0, 2.0, 3.0, 5.0, 6.0, 8.0, 10.0, 15.0, 20.0, 30.0, 40.0,
    60.0, 80.0, 100.0, 150.0, 200.0, 250.0, 300.0, 350.0, 400.0
)

private val STANDARD_PFC_STEP_SIZES_KVAR = listOf(
    10.0, 15.0, 20.0, 25.0, 30.0, 40.0, 50.0, 60.0, 75.0, 100.0
)

private fun formatRatingVal(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
}

@Composable
private fun CompactKpiTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null
) {
    Box(
        modifier = modifier
            .clip(DsShape.card)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm)
    ) {
        Column {
            Text(
                text = label,
                style = DsType.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = DsType.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (unit != null) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = unit,
                        style = DsType.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun engineLoadingBandColor(status: String, loadingPct: Double? = null): Color {
    val ext = MaterialTheme.dsExtendedColors
    val upper = status.uppercase().trim()
    return when {
        upper.contains("OVERLOADED") || upper.contains("FAIL") || upper.contains("INVALID") || (loadingPct ?: 0.0) > 100.0 -> ext.danger
        upper.contains("HIGH") || upper.contains("REVIEW") || upper.contains("SELECT") || (loadingPct ?: 0.0) > 85.0 -> ext.warning
        upper.contains("OPTIMAL") || upper.contains("VERIFIED") || upper.contains("VALID") || upper.contains("OK") -> ext.success
        else -> ext.neutral
    }
}

private fun engineStatusToVariant(status: String, loadingPct: Double? = null): DsStatusVariant {
    val upper = status.uppercase().trim()
    return when {
        upper.contains("OVERLOADED") || upper.contains("FAIL") || upper.contains("INVALID") || (loadingPct ?: 0.0) > 100.0 -> DsStatusVariant.Fail
        upper.contains("HIGH") || upper.contains("REVIEW") || upper.contains("SELECT") || (loadingPct ?: 0.0) > 85.0 -> DsStatusVariant.Warning
        upper.contains("OPTIMAL") || upper.contains("VERIFIED") || upper.contains("VALID") || upper.contains("OK") -> DsStatusVariant.Pass
        upper.contains("DATA") || upper.contains("NOT_CONFIGURED") || upper.contains("NO_LOAD") -> DsStatusVariant.Info
        else -> DsStatusVariant.Neutral
    }
}

fun formatStatusString(rawStatus: String): String {
    return when (rawStatus.uppercase().trim()) {
        "SELECT_LOADS" -> "Action Required — Select Loads"
        "DATA_REQUIRED" -> "Data Required"
        "NOT_CONFIGURED" -> "Not Configured"
        "NO_LOAD" -> "No Connected Load"
        "INVALID" -> "Engineering Review Required"
        else -> rawStatus.replace("_", " ").lowercase().split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.US) else it.toString() }
        }
    }
}

@Composable
fun PowerTopSegmentedBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        DsPillTabItem("Generator", Icons.Filled.Bolt, "tab_generator"),
        DsPillTabItem("ATS", Icons.AutoMirrored.Filled.AltRoute, "tab_ats"),
        DsPillTabItem("UPS", Icons.Filled.BatteryChargingFull, "tab_ups"),
        DsPillTabItem("PF Correction", Icons.Filled.Tune, "tab_pf")
    )
    DsSegmentedPillTabRow(
        tabs = tabs,
        selectedTabIndex = selectedTab,
        onTabSelected = onTabSelected,
        modifier = modifier
    )
}

@Composable
fun PowerSystemsScreen(
    state: UiState,
    generatorSettings: GeneratorEntity = GeneratorEntity(),
    atsSettings: AtsEntity = AtsEntity(),
    upsSettings: UpsEntity = UpsEntity(),
    pfGlobalSettings: PfGlobalEntity = PfGlobalEntity(),
    pfTransformerSettings: List<PfTransformerEntity> = emptyList(),
    onSaveGenerator: (GeneratorEntity) -> Unit = {},
    onSaveAts: (AtsEntity) -> Unit = {},
    onSaveUps: (UpsEntity) -> Unit = {},
    onSavePfGlobal: (PfGlobalEntity) -> Unit = {},
    onSavePfTransformer: (PfTransformerEntity) -> Unit = {},
    onAddGenerator: () -> Unit = {},
    onDeleteGenerator: (String) -> Unit = {},
    onAddAts: () -> Unit = {},
    onDeleteAts: (String) -> Unit = {},
    onAddUps: () -> Unit = {},
    onDeleteUps: (String) -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PowerTopSegmentedBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
            ) {
                when (selectedTab) {
                    0 -> GeneratorSettingsPanel(
                        state = state,
                        singleSettings = generatorSettings,
                        onSave = onSaveGenerator,
                        onAddGenerator = onAddGenerator,
                        onDeleteGenerator = onDeleteGenerator
                    )
                    1 -> AtsSettingsPanel(
                        state = state,
                        singleSettings = atsSettings,
                        onSave = onSaveAts,
                        onAddAts = onAddAts,
                        onDeleteAts = onDeleteAts
                    )
                    2 -> UpsSettingsPanel(
                        state = state,
                        singleSettings = upsSettings,
                        onSave = onSaveUps,
                        onAddUps = onAddUps,
                        onDeleteUps = onDeleteUps
                    )
                    3 -> PfCorrectionPanel(
                        state = state,
                        globalSettings = pfGlobalSettings,
                        transformerSettings = pfTransformerSettings,
                        onSaveGlobal = onSavePfGlobal,
                        onSaveTx = onSavePfTransformer
                    )
                }
            }
        }
    }
}

@Composable
fun GeneratorSettingsPanel(
    state: UiState,
    singleSettings: GeneratorEntity,
    onSave: (GeneratorEntity) -> Unit,
    onAddGenerator: () -> Unit = {},
    onDeleteGenerator: (String) -> Unit = {}
) {
    val successState = state as? UiState.Success
    val generators = successState?.generators ?: listOf(singleSettings)
    val generatorResults = successState?.generatorResults ?: emptyMap()

    var selectedGenId by rememberSaveable { mutableStateOf("") }
    val activeGenId = if (selectedGenId.isNotEmpty() && generators.any { it.generatorId == selectedGenId }) {
        selectedGenId
    } else {
        generators.firstOrNull()?.generatorId ?: singleSettings.generatorId
    }
    val activeGen = generators.firstOrNull { it.generatorId == activeGenId }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
        contentPadding = PaddingValues(bottom = 80.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Selector Row with Add Button (No cramped label, full scrollable width)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                generators.forEach { gen ->
                    val isSelected = gen.generatorId == activeGenId
                    val res = generatorResults[gen.generatorId] ?: successState?.generatorResult
                    val ratingText = res?.finalSize?.let { " (${formatRatingVal(it)} kVA)" } ?: ""
                    DsSelectableChip(
                        text = "${gen.generatorName}$ratingText",
                        selected = isSelected,
                        onClick = { selectedGenId = gen.generatorId }
                    )
                }

                FilledTonalIconButton(
                    onClick = onAddGenerator,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Generator",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 2. Empty State or Active Generator Cards
        if (activeGen == null || generators.isEmpty()) {
            item {
                DsCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = DsSpacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = "No Generators",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(DsSpacing.md))
                        Text(
                            text = "No Generators Configured",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            style = DsType.titleMedium
                        )
                        Spacer(modifier = Modifier.height(DsSpacing.xs))
                        Text(
                            text = "Click '+' above to create an essential power source.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = DsType.bodySmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(DsSpacing.md))
                        Button(
                            onClick = onAddGenerator,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = DsShape.pill
                        ) {
                            Text("+ Add Generator", style = DsType.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            val result = generatorResults[activeGen.generatorId] ?: successState?.generatorResult

            // Card 1: Hero Sizing & Telemetry Card
            item {
                DsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeGen.generatorName,
                                color = MaterialTheme.colorScheme.primary,
                                style = DsType.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${activeGen.generatorId} • Standby Genset",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = DsType.labelSmall,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(DsSpacing.xs))
                            DsStatusBadge(
                                text = formatStatusString(result?.status ?: "DATA_REQUIRED"),
                                variant = engineStatusToVariant(result?.status ?: "", result?.loadingPct)
                            )
                        }

                        if (generators.size > 1) {
                            IconButton(
                                onClick = { onDeleteGenerator(activeGen.generatorId) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Generator",
                                    tint = MaterialTheme.dsExtendedColors.danger,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (result != null) {
                        Spacer(modifier = Modifier.height(DsSpacing.md))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(DsSpacing.sm))

                        // Loading Row
                        val bandColor = engineLoadingBandColor(result.status, result.loadingPct)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Operating Loading", color = MaterialTheme.colorScheme.onSurfaceVariant, style = DsType.labelSmall)
                            Text(
                                text = "${String.format("%.1f", result.loadingPct ?: 0.0)}%",
                                color = bandColor,
                                fontWeight = FontWeight.Bold,
                                style = DsType.titleMedium,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.height(DsSpacing.xs))
                        LinearProgressIndicator(
                            progress = { ((result.loadingPct ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(DsShape.pill),
                            color = bandColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            strokeCap = StrokeCap.Round
                        )

                        Spacer(modifier = Modifier.height(DsSpacing.md))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(DsSpacing.sm))

                        // 2x2 Compact KPI Grid
                        Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                                CompactKpiTile(
                                    label = "Recommended Sizing",
                                    value = result.finalSize?.let { formatRatingVal(it) } ?: "Auto",
                                    unit = "kVA",
                                    modifier = Modifier.weight(1f)
                                )
                                CompactKpiTile(
                                    label = "Essential Load",
                                    value = String.format("%.2f", result.essentialLoadKVA),
                                    unit = "kVA",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                                CompactKpiTile(
                                    label = "Total Connected",
                                    value = String.format("%.2f", result.totalConnectedLoad),
                                    unit = "kVA",
                                    modifier = Modifier.weight(1f)
                                )
                                CompactKpiTile(
                                    label = "Design Capacity",
                                    value = String.format("%.2f", result.designCapacity),
                                    unit = "kVA",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (result.messages.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(DsSpacing.sm))
                            result.messages.forEach { msg ->
                                Text(
                                    text = msg,
                                    color = MaterialTheme.dsExtendedColors.warning,
                                    style = DsType.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            // Card 2: Configuration & Tactile IEC Standard Override Card
            item {
                DsCard {
                    DsSectionHeader(title = "Configuration & Overrides")
                    Spacer(modifier = Modifier.height(DsSpacing.sm))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShape.card)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Start Method", style = DsType.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(activeGen.motorStartMethod, style = DsType.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column {
                            Text("Generator PF", style = DsType.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${activeGen.generatorPf}", style = DsType.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column {
                            Text("Motor PF", style = DsType.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${activeGen.motorPf}", style = DsType.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(DsSpacing.md))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShape.card)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(DsSpacing.md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Manual Size Override",
                                    style = DsType.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Select IEC commercial standard rating or enter custom",
                                    style = DsType.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = activeGen.enableManualOverride,
                                onCheckedChange = { onSave(activeGen.copy(enableManualOverride = it)) }
                            )
                        }

                        if (activeGen.enableManualOverride) {
                            Spacer(modifier = Modifier.height(DsSpacing.sm))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(DsSpacing.sm))

                            Text(
                                text = "Standard Commercial Ratings (kVA):",
                                style = DsType.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(DsSpacing.xs))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs)
                            ) {
                                STANDARD_GENERATOR_SIZES_KVA.forEach { size ->
                                    val isSelected = activeGen.manualSizeKVA != null && abs(activeGen.manualSizeKVA!! - size) < 0.01
                                    val isRec = result?.finalSize != null && abs(result.finalSize - size) < 0.01
                                    DsSelectableChip(
                                        text = if (isRec) "${formatRatingVal(size)} kVA (Rec)" else "${formatRatingVal(size)} kVA",
                                        selected = isSelected,
                                        onClick = { onSave(activeGen.copy(manualSizeKVA = size)) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(DsSpacing.sm))

                            var textBuffer by remember(activeGen.id, activeGen.manualSizeKVA) {
                                mutableStateOf(activeGen.manualSizeKVA?.let { formatRatingVal(it) } ?: "")
                            }

                            OutlinedTextField(
                                value = textBuffer,
                                onValueChange = { newText ->
                                    textBuffer = newText
                                    val trimmed = newText.trim()
                                    if (trimmed.isEmpty()) {
                                        onSave(activeGen.copy(manualSizeKVA = null))
                                    } else {
                                        trimmed.toDoubleOrNull()?.let { parsed ->
                                            onSave(activeGen.copy(manualSizeKVA = parsed))
                                        }
                                    }
                                },
                                label = { Text("Custom Rating (kVA)", style = DsType.bodySmall) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = DsShape.card,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        }
                    }
                }
            }

            // Card 3: Connected Loads Card
            item {
                val atsList = successState?.atsList ?: emptyList()
                val atsFeedingThisGen = atsList.filter {
                    it.emergencySourceId == activeGen.generatorId || (it.emergencySourceId.isNullOrBlank() && activeGen.generatorId == "GEN-01")
                }.map { it.atsId }.toSet()

                val connectedLoads = successState?.circuits?.filter { circ ->
                    if (circ.assignedGeneratorId != null) {
                        circ.assignedGeneratorId == activeGen.generatorId
                    } else if (circ.assignedAtsId != null) {
                        atsFeedingThisGen.contains(circ.assignedAtsId) || (circ.assignedAtsId == "ATS-01" && activeGen.generatorId == "GEN-01")
                    } else {
                        circ.generatorIncluded && activeGen.generatorId == "GEN-01"
                    }
                } ?: emptyList()

                DsCard {
                    DsSectionHeader(
                        title = "Connected Loads",
                        trailingAction = {
                            Text(
                                text = "${connectedLoads.size} Circuits",
                                style = DsType.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(DsSpacing.sm))

                    if (connectedLoads.isEmpty()) {
                        Text(
                            text = "No circuits currently assigned to this generator.",
                            style = DsType.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val chunked = connectedLoads.chunked(2)
                        chunked.forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                            ) {
                                pair.forEach { circ ->
                                    DsInfoChip(
                                        label = circ.circuitName,
                                        trailingValue = "%.2f kVA".format(circ.loadKVA),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(modifier = Modifier.height(DsSpacing.xs))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AtsSettingsPanel(
    state: UiState,
    singleSettings: AtsEntity,
    onSave: (AtsEntity) -> Unit,
    onAddAts: () -> Unit = {},
    onDeleteAts: (String) -> Unit = {}
) {
    val successState = state as? UiState.Success
    val atsList = successState?.atsList ?: listOf(singleSettings)
    val atsResults = successState?.atsResults ?: emptyMap()
    val circuits = successState?.circuits ?: emptyList()
    val generators = successState?.generators ?: emptyList()

    var selectedAtsId by rememberSaveable { mutableStateOf("") }
    val activeAtsId = if (selectedAtsId.isNotEmpty() && atsList.any { it.atsId == selectedAtsId }) {
        selectedAtsId
    } else {
        atsList.firstOrNull()?.atsId ?: singleSettings.atsId
    }
    val activeAts = atsList.firstOrNull { it.atsId == activeAtsId }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
        contentPadding = PaddingValues(bottom = 80.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Selector Row with Add Button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                atsList.forEach { ats ->
                    val isSelected = ats.atsId == activeAtsId
                    val res = atsResults[ats.atsId] ?: successState?.atsResult
                    val ratingText = res?.finalRating?.let { " (${formatRatingVal(it)} A)" } ?: ""
                    DsSelectableChip(
                        text = "${ats.atsName}$ratingText",
                        selected = isSelected,
                        onClick = { selectedAtsId = ats.atsId }
                    )
                }

                FilledTonalIconButton(
                    onClick = onAddAts,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add ATS",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 2. Empty State or Active ATS Cards
        if (activeAts == null || atsList.isEmpty()) {
            item {
                DsCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = DsSpacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = "No ATS",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(DsSpacing.md))
                        Text(
                            text = "No ATS Units Configured",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            style = DsType.titleMedium
                        )
                        Spacer(modifier = Modifier.height(DsSpacing.xs))
                        Text(
                            text = "Click '+' above to create an Automatic Transfer Switch.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = DsType.bodySmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(DsSpacing.md))
                        Button(
                            onClick = onAddAts,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = DsShape.pill
                        ) {
                            Text("+ Add ATS", style = DsType.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            val result = atsResults[activeAts.atsId] ?: successState?.atsResult

            val generatorLabelMap = generators.associate { gen ->
                val label = if (generators.count { it.generatorName.trim().equals(gen.generatorName.trim(), ignoreCase = true) } > 1) {
                    "${gen.generatorName} (${gen.generatorId})"
                } else {
                    gen.generatorName
                }
                label to gen.generatorId
            }
            val generatorOptionLabels = generatorLabelMap.keys.toList()
            val emergencySourceOptions = if (generatorOptionLabels.isNotEmpty()) {
                generatorOptionLabels + listOf("Transformer", "Grid") + circuits.map { it.circuitName }.distinct()
            } else {
                listOf("Generator", "Transformer", "Grid") + circuits.map { it.circuitName }.distinct()
            }

            fun resolveEmergencySourceDisplay(storedValue: String): String {
                if (storedValue.isBlank()) return ""
                val matchedById = generators.firstOrNull { it.generatorId.equals(storedValue, ignoreCase = true) }
                if (matchedById != null) {
                    return generatorLabelMap.entries.firstOrNull { it.value == matchedById.generatorId }?.key ?: matchedById.generatorName
                }
                val matchedByName = generators.filter { it.generatorName.trim().equals(storedValue.trim(), ignoreCase = true) }
                if (matchedByName.size == 1) {
                    return generatorLabelMap.entries.firstOrNull { it.value == matchedByName.single().generatorId }?.key ?: matchedByName.single().generatorName
                }
                return storedValue
            }

            val normalSourceOptions = listOf("Transformer", "Generator", "Grid") + circuits.map { it.circuitName }.distinct()
            val outputBoardCandidates = circuits.filter {
                (it.panelType == "MDB" || it.panelType == "SMDB" || it.panelType == "DB" || it.circuitName.contains("Board", true) || it.circuitName.contains("MDB", true) || it.circuitName.contains("DB", true))
                && it.circuitName != activeAts.normalSourceId
            }
            val outputBoardOptions = listOf("None") + outputBoardCandidates.map { it.circuitName }
            val currentOutputBoardName = outputBoardCandidates.find { it.id == activeAts.outputBoardId }?.circuitName ?: "None"

            // Card 1: Hero Sizing & Telemetry Card
            item {
                DsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeAts.atsName,
                                color = MaterialTheme.colorScheme.primary,
                                style = DsType.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${activeAts.atsId} • ${activeAts.atsClass}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = DsType.labelSmall,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(DsSpacing.xs))
                            DsStatusBadge(
                                text = formatStatusString(result?.status ?: "VALID_SIZED"),
                                variant = engineStatusToVariant(result?.status ?: "")
                            )
                        }

                        if (atsList.size > 1) {
                            IconButton(
                                onClick = { onDeleteAts(activeAts.atsId) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete ATS",
                                    tint = MaterialTheme.dsExtendedColors.danger,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (result != null) {
                        Spacer(modifier = Modifier.height(DsSpacing.md))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(DsSpacing.sm))

                        // 2x2 Compact KPI Grid
                        Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                                CompactKpiTile(
                                    label = "Recommended Rating",
                                    value = result.finalRating?.let { formatRatingVal(it) } ?: "Auto",
                                    unit = "A",
                                    modifier = Modifier.weight(1f)
                                )
                                CompactKpiTile(
                                    label = "Governing Current",
                                    value = String.format("%.1f", result.governingCurrent),
                                    unit = "A",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                                CompactKpiTile(
                                    label = "Required Icw",
                                    value = String.format("%.2f", result.requiredIcw),
                                    unit = "kA",
                                    modifier = Modifier.weight(1f)
                                )
                                CompactKpiTile(
                                    label = "Required Icm",
                                    value = String.format("%.2f", result.requiredIcm),
                                    unit = "kA",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(DsSpacing.sm))

                        // Technical Specs Section (Key-Value rows in surfaceContainerHigh)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DsShape.card)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(DsSpacing.xs)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Construction", style = DsType.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(result.construction, style = DsType.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Poles Configuration", style = DsType.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(result.poles, style = DsType.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Transfer Status", style = DsType.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(result.transferStatus, style = DsType.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        if (result.messages.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(DsSpacing.sm))
                            result.messages.forEach { msg ->
                                Text(
                                    text = msg,
                                    color = MaterialTheme.dsExtendedColors.warning,
                                    style = DsType.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            // Card 2: Source Routing & Override Card
            item {
                DsCard {
                    DsSectionHeader(title = "Source Routing & Overrides")
                    Spacer(modifier = Modifier.height(DsSpacing.sm))

                    VoltAtlasDropdown(
                        value = activeAts.normalSourceId ?: "",
                        options = normalSourceOptions,
                        onSelect = { onSave(activeAts.copy(normalSourceId = it.takeIf { s -> s.isNotBlank() })) },
                        label = "Normal Source"
                    )
                    Spacer(modifier = Modifier.height(DsSpacing.xs))

                    VoltAtlasDropdown(
                        value = resolveEmergencySourceDisplay(activeAts.emergencySourceId ?: ""),
                        options = emergencySourceOptions,
                        onSelect = { selectedLabel ->
                            val persistedId = generatorLabelMap[selectedLabel] ?: selectedLabel
                            onSave(activeAts.copy(emergencySourceId = persistedId.takeIf { s -> s.isNotBlank() }))
                        },
                        label = "Emergency Source"
                    )
                    Spacer(modifier = Modifier.height(DsSpacing.xs))

                    VoltAtlasDropdown(
                        value = currentOutputBoardName,
                        options = outputBoardOptions,
                        onSelect = { selectedName ->
                            val selectedId = if (selectedName == "None") null else outputBoardCandidates.find { it.circuitName == selectedName }?.id
                            onSave(activeAts.copy(outputBoardId = selectedId))
                        },
                        label = "Downstream Output Board"
                    )

                    Spacer(modifier = Modifier.height(DsSpacing.md))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShape.card)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(DsSpacing.md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Manual Rating Override",
                                    style = DsType.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Select standard IEC breaker frame (A) or enter custom",
                                    style = DsType.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = activeAts.enableManualOverride,
                                onCheckedChange = { onSave(activeAts.copy(enableManualOverride = it)) }
                            )
                        }

                        if (activeAts.enableManualOverride) {
                            Spacer(modifier = Modifier.height(DsSpacing.sm))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(DsSpacing.sm))

                            Text(
                                text = "Available IEC Breaker Frames (A):",
                                style = DsType.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(DsSpacing.xs))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs)
                            ) {
                                STANDARD_ATS_SIZES_AMP.forEach { size ->
                                    val isSelected = activeAts.manualSizeAmp != null && abs(activeAts.manualSizeAmp!! - size) < 0.01
                                    val isRec = result?.finalRating != null && abs(result.finalRating - size) < 0.01
                                    DsSelectableChip(
                                        text = if (isRec) "${formatRatingVal(size)} A (Rec)" else "${formatRatingVal(size)} A",
                                        selected = isSelected,
                                        onClick = { onSave(activeAts.copy(manualSizeAmp = size)) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(DsSpacing.sm))

                            var textBuffer by remember(activeAts.id, activeAts.manualSizeAmp) {
                                mutableStateOf(activeAts.manualSizeAmp?.let { formatRatingVal(it) } ?: "")
                            }

                            OutlinedTextField(
                                value = textBuffer,
                                onValueChange = { newText ->
                                    textBuffer = newText
                                    val trimmed = newText.trim()
                                    if (trimmed.isEmpty()) {
                                        onSave(activeAts.copy(manualSizeAmp = null))
                                    } else {
                                        trimmed.toDoubleOrNull()?.let { parsed ->
                                            onSave(activeAts.copy(manualSizeAmp = parsed))
                                        }
                                    }
                                },
                                label = { Text("Custom Rating (Amps)", style = DsType.bodySmall) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = DsShape.card,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        }
                    }
                }
            }

            // Card 3: Connected Loads Card
            item {
                val connectedLoads = circuits.filter { circ ->
                    if (circ.assignedAtsId != null) {
                        circ.assignedAtsId == activeAts.atsId
                    } else {
                        circ.atsIncluded && activeAts.atsId == "ATS-01"
                    }
                }

                DsCard {
                    DsSectionHeader(
                        title = "Connected Essential Loads",
                        trailingAction = {
                            Text(
                                text = "${connectedLoads.size} Circuits",
                                style = DsType.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(DsSpacing.sm))

                    if (connectedLoads.isEmpty()) {
                        Text(
                            text = "No circuits currently assigned to this ATS unit.",
                            style = DsType.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val chunked = connectedLoads.chunked(2)
                        chunked.forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                            ) {
                                pair.forEach { circ ->
                                    DsInfoChip(
                                        label = circ.circuitName,
                                        trailingValue = "%.2f kVA".format(circ.loadKVA),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(modifier = Modifier.height(DsSpacing.xs))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpsSettingsPanel(
    state: UiState,
    singleSettings: UpsEntity,
    onSave: (UpsEntity) -> Unit,
    onAddUps: () -> Unit = {},
    onDeleteUps: (String) -> Unit = {}
) {
    val successState = state as? UiState.Success
    val upsList = successState?.upsList ?: listOf(singleSettings)
    val upsResults = successState?.upsResults ?: emptyMap()
    val circuits = successState?.circuits ?: emptyList()

    var selectedUpsId by rememberSaveable { mutableStateOf("") }
    val activeUpsId = if (selectedUpsId.isNotEmpty() && upsList.any { it.upsId == selectedUpsId }) {
        selectedUpsId
    } else {
        upsList.firstOrNull()?.upsId ?: singleSettings.upsId
    }
    val activeUps = upsList.firstOrNull { it.upsId == activeUpsId }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
        contentPadding = PaddingValues(bottom = 80.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Selector Row with Add Button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                upsList.forEach { ups ->
                    val isSelected = ups.upsId == activeUpsId
                    val res = upsResults[ups.upsId] ?: successState?.upsResult
                    val sizeText = res?.recommendedSize?.let { " (${formatRatingVal(it)} kVA)" } ?: ""
                    DsSelectableChip(
                        text = "${ups.upsName}$sizeText",
                        selected = isSelected,
                        onClick = { selectedUpsId = ups.upsId }
                    )
                }

                FilledTonalIconButton(
                    onClick = onAddUps,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add UPS",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 2. Empty State or Active UPS Cards
        if (activeUps == null || upsList.isEmpty()) {
            item {
                DsCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = DsSpacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = "No UPS",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(DsSpacing.md))
                        Text(
                            text = "No UPS Units Configured",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            style = DsType.titleMedium
                        )
                        Spacer(modifier = Modifier.height(DsSpacing.xs))
                        Text(
                            text = "Click '+' above to create an Uninterruptible Power Supply.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = DsType.bodySmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(DsSpacing.md))
                        Button(
                            onClick = onAddUps,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = DsShape.pill
                        ) {
                            Text("+ Add UPS", style = DsType.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            val result = upsResults[activeUps.upsId] ?: successState?.upsResult

            // Card 1: Hero Sizing & Telemetry Card
            item {
                DsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeUps.upsName,
                                color = MaterialTheme.colorScheme.primary,
                                style = DsType.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${activeUps.upsId} • Online Double-Conversion",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = DsType.labelSmall,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(DsSpacing.xs))
                            DsStatusBadge(
                                text = formatStatusString(result?.status ?: "OPTIMAL"),
                                variant = engineStatusToVariant(result?.status ?: "", result?.loadingPct)
                            )
                        }

                        if (upsList.size > 1) {
                            IconButton(
                                onClick = { onDeleteUps(activeUps.upsId) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete UPS",
                                    tint = MaterialTheme.dsExtendedColors.danger,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (result != null) {
                        Spacer(modifier = Modifier.height(DsSpacing.md))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(DsSpacing.sm))

                        // Loading Row
                        val bandColor = engineLoadingBandColor(result.status, result.loadingPct)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("UPS Loading", color = MaterialTheme.colorScheme.onSurfaceVariant, style = DsType.labelSmall)
                            Text(
                                text = "${String.format("%.1f", result.loadingPct ?: 0.0)}%",
                                color = bandColor,
                                fontWeight = FontWeight.Bold,
                                style = DsType.titleMedium,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.height(DsSpacing.xs))
                        LinearProgressIndicator(
                            progress = { ((result.loadingPct ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(DsShape.pill),
                            color = bandColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            strokeCap = StrokeCap.Round
                        )

                        Spacer(modifier = Modifier.height(DsSpacing.md))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(DsSpacing.sm))

                        // 2x2 Compact KPI Grid
                        Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                                CompactKpiTile(
                                    label = "Recommended Capacity",
                                    value = result.recommendedSize?.let { formatRatingVal(it) } ?: "Auto",
                                    unit = "kVA",
                                    modifier = Modifier.weight(1f)
                                )
                                CompactKpiTile(
                                    label = "Design Capacity",
                                    value = String.format("%.2f", result.designUpsKVA),
                                    unit = "kVA",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                                CompactKpiTile(
                                    label = "Critical Load",
                                    value = String.format("%.2f", result.totalCriticalLoadKVA),
                                    unit = "kVA",
                                    modifier = Modifier.weight(1f)
                                )
                                CompactKpiTile(
                                    label = "Autonomy Backup",
                                    value = "${activeUps.autonomyMinutes}",
                                    unit = "min",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(DsSpacing.sm))

                        // Battery Bank Telemetry Section (Key-Value rows in surfaceContainerHigh)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DsShape.card)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(DsSpacing.xs)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Battery Bank Required", style = DsType.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(result.batteryBankRequired, style = DsType.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Full Load Autonomy", style = DsType.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(result.autonomyAtFullLoad, style = DsType.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        if (result.messages.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(DsSpacing.sm))
                            result.messages.forEach { msg ->
                                Text(
                                    text = msg,
                                    color = MaterialTheme.dsExtendedColors.warning,
                                    style = DsType.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            // Card 2: Configuration & Override Card
            item {
                DsCard {
                    DsSectionHeader(title = "Configuration & Overrides")
                    Spacer(modifier = Modifier.height(DsSpacing.sm))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShape.card)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Battery Type", style = DsType.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(activeUps.batteryType, style = DsType.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column {
                            Text("UPS PF", style = DsType.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${activeUps.upsPf}", style = DsType.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column {
                            Text("Efficiency", style = DsType.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${activeUps.upsEfficiency}%", style = DsType.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(DsSpacing.md))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShape.card)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(DsSpacing.md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Manual Size Override",
                                    style = DsType.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Select commercial UPS rating (kVA) or enter custom",
                                    style = DsType.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = activeUps.enableManualOverride,
                                onCheckedChange = { onSave(activeUps.copy(enableManualOverride = it)) }
                            )
                        }

                        if (activeUps.enableManualOverride) {
                            Spacer(modifier = Modifier.height(DsSpacing.sm))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(DsSpacing.sm))

                            Text(
                                text = "Commercial UPS Sizes (kVA):",
                                style = DsType.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(DsSpacing.xs))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs)
                            ) {
                                STANDARD_UPS_SIZES_KVA.forEach { size ->
                                    val isSelected = activeUps.manualSizeKVA != null && abs(activeUps.manualSizeKVA!! - size) < 0.01
                                    val isRec = result?.recommendedSize != null && abs(result.recommendedSize - size) < 0.01
                                    DsSelectableChip(
                                        text = if (isRec) "${formatRatingVal(size)} kVA (Rec)" else "${formatRatingVal(size)} kVA",
                                        selected = isSelected,
                                        onClick = { onSave(activeUps.copy(manualSizeKVA = size)) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(DsSpacing.sm))

                            var textBuffer by remember(activeUps.id, activeUps.manualSizeKVA) {
                                mutableStateOf(activeUps.manualSizeKVA?.let { formatRatingVal(it) } ?: "")
                            }

                            OutlinedTextField(
                                value = textBuffer,
                                onValueChange = { newText ->
                                    textBuffer = newText
                                    val trimmed = newText.trim()
                                    if (trimmed.isEmpty()) {
                                        onSave(activeUps.copy(manualSizeKVA = null))
                                    } else {
                                        trimmed.toDoubleOrNull()?.let { parsed ->
                                            onSave(activeUps.copy(manualSizeKVA = parsed))
                                        }
                                    }
                                },
                                label = { Text("Custom Capacity (kVA)", style = DsType.bodySmall) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = DsShape.card,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        }
                    }
                }
            }

            // Card 3: Connected Loads Card
            item {
                val connectedLoads = circuits.filter { circ ->
                    if (circ.assignedUpsId != null) {
                        circ.assignedUpsId == activeUps.upsId
                    } else {
                        circ.upsBacked && activeUps.upsId == "UPS-01"
                    }
                }

                DsCard {
                    DsSectionHeader(
                        title = "Critical Connected Loads",
                        trailingAction = {
                            Text(
                                text = "${connectedLoads.size} Circuits",
                                style = DsType.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(DsSpacing.sm))

                    if (connectedLoads.isEmpty()) {
                        Text(
                            text = "No circuits currently assigned to this UPS unit.",
                            style = DsType.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val chunked = connectedLoads.chunked(2)
                        chunked.forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                            ) {
                                pair.forEach { circ ->
                                    DsInfoChip(
                                        label = circ.circuitName,
                                        trailingValue = "%.2f kVA".format(circ.loadKVA),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(modifier = Modifier.height(DsSpacing.xs))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PfCorrectionPanel(
    state: UiState,
    globalSettings: PfGlobalEntity,
    transformerSettings: List<PfTransformerEntity>,
    onSaveGlobal: (PfGlobalEntity) -> Unit,
    onSaveTx: (PfTransformerEntity) -> Unit
) {
    val results = (state as? UiState.Success)?.pfCorrectionResults ?: emptyList()
    val transformers = (state as? UiState.Success)?.transformers ?: emptyList()

    var selectedTxId by rememberSaveable { mutableStateOf("") }
    val availableTxIds = if (results.isNotEmpty()) results.map { it.transformerId } else transformers.map { it.transformerId }
    val activeTxId = if (selectedTxId.isNotEmpty() && availableTxIds.contains(selectedTxId)) {
        selectedTxId
    } else {
        availableTxIds.firstOrNull() ?: ""
    }
    val activeResult = results.firstOrNull { it.transformerId == activeTxId } ?: results.firstOrNull()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
        contentPadding = PaddingValues(bottom = 80.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Selector Row for Transformers
        if (availableTxIds.size > 1) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs)
                    ) {
                        availableTxIds.forEach { txId ->
                            DsSelectableChip(
                                text = txId,
                                selected = txId == activeTxId,
                                onClick = { selectedTxId = txId }
                            )
                        }
                    }
                }
            }
        }

        // 2. Empty State or Active PF Correction Cards
        if (activeResult == null) {
            item {
                DsCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = DsSpacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "No PF Results",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(DsSpacing.md))
                        Text(
                            text = "No PF Correction Data Available",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            style = DsType.titleMedium
                        )
                        Spacer(modifier = Modifier.height(DsSpacing.xs))
                        Text(
                            text = "Add transformers and assign circuit loads to calculate required power factor compensation.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = DsType.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // Card 1: Power Factor Telemetry Flow & Status Card
            item {
                DsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PF Correction • ${activeResult.transformerId}",
                                color = MaterialTheme.colorScheme.primary,
                                style = DsType.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Automatic Capacitor Bank Sizing",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = DsType.labelSmall,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(DsSpacing.xs))
                            DsStatusBadge(
                                text = activeResult.status.ifBlank { "VALID" },
                                variant = engineStatusToVariant(activeResult.status)
                            )
                        }
                    }

                    if (activeResult.status.contains("REVIEW", true) || activeResult.pfState.contains("LEADING", true)) {
                        Spacer(modifier = Modifier.height(DsSpacing.sm))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DsShape.card)
                                .background(MaterialTheme.dsExtendedColors.warning.copy(alpha = 0.12f))
                                .padding(DsSpacing.md)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = MaterialTheme.dsExtendedColors.warning,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Bank capacity (${formatRatingVal(activeResult.bankCapacity)} kVAR) exceeds required (${String.format("%.2f", activeResult.requiredKvar)} kVAR). Check for leading power factor.",
                                    style = DsType.bodySmall,
                                    color = MaterialTheme.dsExtendedColors.warning
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(DsSpacing.md))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(DsSpacing.sm))

                    // Power Factor Telemetry Flow
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShape.card)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(DsSpacing.md)
                    ) {
                        Text("POWER FACTOR TELEMETRY", style = DsType.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(DsSpacing.xs))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Existing PF", style = DsType.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format("%.2f", activeResult.existingPF), style = DsType.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Text("➔", style = DsType.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Target PF", style = DsType.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format("%.2f", activeResult.targetPf), style = DsType.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("➔", style = DsType.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Corrected PF", style = DsType.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${String.format("%.4f", activeResult.correctedPF)} (${activeResult.pfState})",
                                    style = DsType.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeResult.pfState.contains("LEADING", true)) MaterialTheme.dsExtendedColors.warning else MaterialTheme.dsExtendedColors.success
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(DsSpacing.md))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(DsSpacing.sm))

                    // 2x2 Compact KPI Grid
                    Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                            CompactKpiTile(
                                label = "Existing Active Load",
                                value = String.format("%.2f", activeResult.existingKW),
                                unit = "kW",
                                modifier = Modifier.weight(1f)
                            )
                            CompactKpiTile(
                                label = "Required KVAR",
                                value = String.format("%.2f", activeResult.requiredKvar),
                                unit = "kVAR",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                            CompactKpiTile(
                                label = "Recommended Bank",
                                value = formatRatingVal(activeResult.bankCapacity),
                                unit = "kVAR",
                                modifier = Modifier.weight(1f)
                            )
                            CompactKpiTile(
                                label = "Corrected Apparent",
                                value = String.format("%.2f", activeResult.correctedKVA),
                                unit = "kVA",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Card 2: Step Selection & Configuration Card
            item {
                DsCard {
                    DsSectionHeader(title = "Capacitor Step Configuration")
                    Spacer(modifier = Modifier.height(DsSpacing.sm))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShape.card)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("System Voltage", style = DsType.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${globalSettings.systemVoltage} V", style = DsType.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column {
                            Text("Annual Hours", style = DsType.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${globalSettings.annualOperatingHours} h", style = DsType.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column {
                            Text("Loss Reduction", style = DsType.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${(globalSettings.lossReductionPct * 100).toInt()}%", style = DsType.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(DsSpacing.md))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShape.card)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(DsSpacing.md)
                    ) {
                        Text(
                            text = "Standard Capacitor Step Size (kVAR):",
                            style = DsType.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(DsSpacing.xs))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs)
                        ) {
                            STANDARD_PFC_STEP_SIZES_KVAR.forEach { stepSize ->
                                val isSelected = abs(globalSettings.kvarStepSize - stepSize) < 0.01
                                DsSelectableChip(
                                    text = "${formatRatingVal(stepSize)} kVAR",
                                    selected = isSelected,
                                    onClick = { onSaveGlobal(globalSettings.copy(kvarStepSize = stepSize)) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(DsSpacing.md))

                        var targetPfText by remember(globalSettings.targetPf) {
                            mutableStateOf(String.format("%.2f", globalSettings.targetPf))
                        }

                        OutlinedTextField(
                            value = targetPfText,
                            onValueChange = { newText ->
                                targetPfText = newText
                                newText.toDoubleOrNull()?.let { parsed ->
                                    if (parsed in 0.7..1.0) {
                                        onSaveGlobal(globalSettings.copy(targetPf = parsed))
                                    }
                                }
                            },
                            label = { Text("Target Power Factor (0.70 – 1.00)", style = DsType.bodySmall) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = DsShape.card,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }
            }
        }
    }
}
