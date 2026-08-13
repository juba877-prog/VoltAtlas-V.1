@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.VoltAtlasMotion
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.engine.*

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

private fun formatRatingVal(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
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
    val tabs = listOf("Generator", "ATS", "UPS", "PF Correction")

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkBackground,
            contentColor = ThemeTextColor,
            edgePadding = 8.dp
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false) }
                )
            }
        }

        Box(modifier = Modifier.weight(1f).padding(16.dp)) {
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
                3 -> PfCorrectionPanel(state, pfGlobalSettings, pfTransformerSettings, onSavePfGlobal, onSavePfTransformer)
            }
        }
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

    Column(modifier = Modifier.fillMaxSize()) {
        // Section Header Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GENERATOR SIZING ENGINE (${generators.size})",
                fontSize = 16.sp,
                color = ThemeTextColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onAddGenerator,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, SleekPrimary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = SleekPrimary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("+ Add Generator", fontSize = 12.sp, color = SleekPrimary, maxLines = 1)
            }
        }

        if (generators.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Inbox, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No Generators Configured", color = ThemeTextColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Click '+ Add Generator' above to create an essential power source.", color = SlateTextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onAddGenerator, colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)) {
                        Text("+ Add Generator")
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(generators, key = { _, item -> item.generatorId }) { index, item ->
                    val defaultExpanded = generators.size == 1 || index == 0
                    val instanceResult = generatorResults[item.generatorId] ?: successState?.generatorResult
                    GeneratorInstanceCard(
                        item = item,
                        result = instanceResult,
                        state = state,
                        defaultExpanded = defaultExpanded,
                        onSave = onSave,
                        onDelete = { onDeleteGenerator(item.generatorId) }
                    )
                }
            }
        }
    }
}

@Composable
fun GeneratorInstanceCard(
    item: GeneratorEntity,
    result: GeneratorResult?,
    state: UiState,
    defaultExpanded: Boolean,
    onSave: (GeneratorEntity) -> Unit,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(defaultExpanded) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Collapsed Summary Line
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${item.generatorName} (${item.generatorId})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ThemeTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "Rec. Size: ${result?.finalSize ?: "Auto"} kVA • Start: ${item.motorStartMethod}",
                        fontSize = 12.sp,
                        color = SlateTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Generator", tint = SleekWarning, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle",
                        tint = SleekPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            val reduceMotion = LocalReduceMotion.current
            AnimatedVisibility(
                visible = isExpanded,
                enter = if (reduceMotion) fadeIn(animationSpec = VoltAtlasMotion.instantFadeSpec) else fadeIn(animationSpec = VoltAtlasMotion.fadeEnterSpec) + expandVertically(animationSpec = spring(stiffness = VoltAtlasMotion.SpringStiffnessLow)),
                exit = if (reduceMotion) fadeOut(animationSpec = VoltAtlasMotion.instantFadeSpec) else fadeOut(animationSpec = VoltAtlasMotion.fadeExitSpec) + shrinkVertically(animationSpec = spring(stiffness = VoltAtlasMotion.SpringStiffnessLow))
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(bottom = 12.dp))
                    if (result != null) {
                        Text("Status: ${formatStatusString(result.status)}", color = SleekPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("Total Connected Load: ${String.format("%.2f", result.totalConnectedLoad)} kVA", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                        Text("Essential Load (Connected): ${String.format("%.2f", result.essentialLoadKVA)} kVA", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                        Text("Required Capacity (/GenPF): ${String.format("%.2f", result.requiredGenerator)} kVA", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                        Text("Design Capacity: ${String.format("%.2f", result.designCapacity)} kVA", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                        if (result.finalSize != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Recommended Size: ${result.finalSize} kVA", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                            Text("Loading: ${String.format("%.1f", result.loadingPct ?: 0.0)}%", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                        }
                        if (result.messages.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            result.messages.forEach { msg ->
                                Text(msg, color = SleekWarning, fontSize = 12.sp)
                            }
                        }
                    }

                    // Manual Override Controls
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Enable Manual Size Override", fontSize = 13.sp, color = ThemeTextColor, modifier = Modifier.weight(1f))
                        Switch(
                            checked = item.enableManualOverride,
                            onCheckedChange = { onSave(item.copy(enableManualOverride = it)) }
                        )
                    }
                    if (item.enableManualOverride) {
                        Spacer(modifier = Modifier.height(8.dp))
                        var textBuffer by remember(item.id, item.manualSizeKVA) {
                            mutableStateOf(item.manualSizeKVA?.let { formatRatingVal(it) } ?: "")
                        }
                        var dropdownExpanded by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExposedDropdownMenuBox(
                                expanded = dropdownExpanded,
                                onExpandedChange = { dropdownExpanded = !dropdownExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = if (item.manualSizeKVA != null) "${formatRatingVal(item.manualSizeKVA!!)} kVA" else "Standard Size",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Standard Size") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false }
                                ) {
                                    STANDARD_GENERATOR_SIZES_KVA.forEach { size ->
                                        DropdownMenuItem(
                                            text = { Text("${formatRatingVal(size)} kVA") },
                                            onClick = {
                                                dropdownExpanded = false
                                                textBuffer = formatRatingVal(size)
                                                onSave(item.copy(manualSizeKVA = size))
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = textBuffer,
                                onValueChange = { newText ->
                                    textBuffer = newText
                                    val trimmed = newText.trim()
                                    if (trimmed.isEmpty()) {
                                        onSave(item.copy(manualSizeKVA = null))
                                    } else {
                                        trimmed.toDoubleOrNull()?.let { parsed ->
                                            onSave(item.copy(manualSizeKVA = parsed))
                                        }
                                    }
                                },
                                label = { Text("Custom (kVA)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    val success = state as? UiState.Success
                    val atsList = success?.atsList ?: emptyList()
                    val atsFeedingThisGen = atsList.filter { it.emergencySourceId == item.generatorId || (it.emergencySourceId.isNullOrBlank() && item.generatorId == "GEN-01") }.map { it.atsId }.toSet()
                    val connectedLoads = success?.circuits?.filter { circ ->
                        if (circ.assignedGeneratorId != null) {
                            circ.assignedGeneratorId == item.generatorId
                        } else if (circ.assignedAtsId != null) {
                            atsFeedingThisGen.contains(circ.assignedAtsId) || (circ.assignedAtsId == "ATS-01" && item.generatorId == "GEN-01")
                        } else {
                            circ.generatorIncluded && item.generatorId == "GEN-01"
                        }
                    } ?: emptyList()

                    val mismatchedAtsCircuits = connectedLoads.filter { circ ->
                        if (circ.assignedAtsId != null) {
                            val ats = atsList.find { it.atsId == circ.assignedAtsId }
                            ats != null && ats.emergencySourceId != null && ats.emergencySourceId != item.generatorId
                        } else false
                    }
                    if (mismatchedAtsCircuits.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ℹ️ Note: Direct assignment to ${item.generatorId} takes priority for generator sizing. ${mismatchedAtsCircuits.size} circuit(s) specify ATS sources routing to a different generator.",
                            color = SleekWarning,
                            fontSize = 12.sp
                        )
                    }

                    val aggregatedKW = connectedLoads.sumOf { it.loadKVA * it.pf }
                    if (aggregatedKW > 0.0 && item.additionalEssentialLoadKW > 0.0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ℹ️ Note: Manual additional load (${"%.2f".format(item.additionalEssentialLoadKW)} kW) is added on top of ${"%.2f".format(aggregatedKW)} kW aggregated from assigned circuits. Ensure this manual value does not double-count already-assigned circuits.",
                            color = SleekWarning,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("CONNECTED LOADS", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                    if (connectedLoads.isEmpty()) {
                        Text("No loads selected", color = SlateTextSecondary, fontSize = 12.sp)
                    } else {
                        connectedLoads.forEach { circ ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(circ.circuitName, color = ThemeTextColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${String.format("%.2f", circ.loadKVA)} kVA", color = ThemeTextColor, fontSize = 12.sp, maxLines = 1)
                            }
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

    Column(modifier = Modifier.fillMaxSize()) {
        // Section Header Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AUTOMATIC TRANSFER SWITCH (ATS) (${atsList.size})",
                fontSize = 16.sp,
                color = ThemeTextColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onAddAts,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, SleekPrimary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = SleekPrimary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("+ Add ATS", fontSize = 12.sp, color = SleekPrimary, maxLines = 1)
            }
        }

        if (atsList.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Inbox, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No ATS Units Configured", color = ThemeTextColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Click '+ Add ATS' above to create an Automatic Transfer Switch.", color = SlateTextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onAddAts, colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)) {
                        Text("+ Add ATS")
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(atsList, key = { _, item -> item.atsId }) { index, item ->
                    val defaultExpanded = atsList.size == 1 || index == 0
                    val instanceResult = atsResults[item.atsId] ?: successState?.atsResult
                    AtsInstanceCard(
                        item = item,
                        result = instanceResult,
                        state = state,
                        defaultExpanded = defaultExpanded,
                        onSave = onSave,
                        onDelete = { onDeleteAts(item.atsId) }
                    )
                }
            }
        }
    }
}

@Composable
fun AtsInstanceCard(
    item: AtsEntity,
    result: AtsResult?,
    state: UiState,
    defaultExpanded: Boolean,
    onSave: (AtsEntity) -> Unit,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(defaultExpanded) }

    var normalSource by remember { mutableStateOf(item.normalSourceId ?: "") }
    var emergencySource by remember { mutableStateOf(item.emergencySourceId ?: "") }
    var outputBoardId by remember { mutableStateOf(item.outputBoardId) }

    LaunchedEffect(item.normalSourceId) { normalSource = item.normalSourceId ?: "" }
    LaunchedEffect(item.emergencySourceId) { emergencySource = item.emergencySourceId ?: "" }
    LaunchedEffect(item.outputBoardId) { outputBoardId = item.outputBoardId }

    val successState = state as? UiState.Success
    val circuits = successState?.circuits ?: emptyList()
    val sourceOptions = listOf("Transformer", "Generator", "Grid") + circuits.map { it.circuitName }.distinct()
    val outputBoardCandidates = circuits.filter {
        (it.panelType == "MDB" || it.panelType == "SMDB" || it.panelType == "DB" || it.circuitName.contains("Board", true) || it.circuitName.contains("MDB", true) || it.circuitName.contains("DB", true))
        && it.circuitName != normalSource
    }
    val outputBoardOptions = listOf("None") + outputBoardCandidates.map { it.circuitName }
    val currentOutputBoardName = outputBoardCandidates.find { it.id == outputBoardId }?.circuitName ?: "None"

    Card(
        modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Collapsed Summary Line
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${item.atsName} (${item.atsId})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ThemeTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "Rating: ${result?.finalRating ?: "Auto"} A • Normal: ${item.normalSourceId ?: "Grid"} → Emerg: ${item.emergencySourceId ?: "Gen"}",
                        fontSize = 12.sp,
                        color = SlateTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete ATS", tint = SleekWarning, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle",
                        tint = SleekPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            val reduceMotion = LocalReduceMotion.current
            AnimatedVisibility(
                visible = isExpanded,
                enter = if (reduceMotion) fadeIn(animationSpec = VoltAtlasMotion.instantFadeSpec) else fadeIn(animationSpec = VoltAtlasMotion.fadeEnterSpec) + expandVertically(animationSpec = spring(stiffness = VoltAtlasMotion.SpringStiffnessLow)),
                exit = if (reduceMotion) fadeOut(animationSpec = VoltAtlasMotion.instantFadeSpec) else fadeOut(animationSpec = VoltAtlasMotion.fadeExitSpec) + shrinkVertically(animationSpec = spring(stiffness = VoltAtlasMotion.SpringStiffnessLow))
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(bottom = 12.dp))

                    // Group 1: Source Configuration
                    Text("SOURCE CONFIGURATION", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    com.example.ui.components.VoltAtlasDropdown(
                        value = normalSource,
                        options = sourceOptions,
                        onSelect = {
                            normalSource = it
                            onSave(item.copy(normalSourceId = it.takeIf { s -> s.isNotBlank() }))
                        },
                        label = "Normal Source"
                    )
                    com.example.ui.components.VoltAtlasDropdown(
                        value = emergencySource,
                        options = sourceOptions,
                        onSelect = {
                            emergencySource = it
                            onSave(item.copy(emergencySourceId = it.takeIf { s -> s.isNotBlank() }))
                        },
                        label = "Emergency Source"
                    )
                    com.example.ui.components.VoltAtlasDropdown(
                        value = currentOutputBoardName,
                        options = outputBoardOptions,
                        onSelect = { selectedName ->
                            val selectedId = if (selectedName == "None") null else outputBoardCandidates.find { it.circuitName == selectedName }?.id
                            outputBoardId = selectedId
                            onSave(item.copy(outputBoardId = selectedId))
                        },
                        label = "Downstream Output Board"
                    )

                    // Group 2: Ratings & Compliance Results
                    if (result != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("RATINGS & COMPLIANCE", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Status: ${formatStatusString(result.status)}", color = SleekPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("Selected Load: ${String.format("%.2f", result.selectedLoad)} kVA", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                        Text("Governing Current: ${String.format("%.1f", result.governingCurrent)} A", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                        if (result.finalRating != null) {
                            Text("Recommended Rating: ${result.finalRating} A", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                            Text("Construction: ${result.construction}", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                            Text("Poles: ${result.poles}", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                            Text("Required Icw: ${String.format("%.2f", result.requiredIcw)} kA", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                            Text("Required Icm: ${String.format("%.2f", result.requiredIcm)} kA", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                            Text("Transfer Status: ${result.transferStatus}", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                        }
                        if (result.messages.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            result.messages.forEach { msg ->
                                Text(msg, color = SleekWarning, fontSize = 12.sp)
                            }
                        }
                    }

                    // Group 3: Manual Override Controls
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("MANUAL OVERRIDE", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Enable Manual Rating Override", fontSize = 13.sp, color = ThemeTextColor, modifier = Modifier.weight(1f))
                        Switch(
                            checked = item.enableManualOverride,
                            onCheckedChange = { onSave(item.copy(enableManualOverride = it)) }
                        )
                    }
                    if (item.enableManualOverride) {
                        Spacer(modifier = Modifier.height(8.dp))
                        var textBuffer by remember(item.id, item.manualSizeAmp) {
                            mutableStateOf(item.manualSizeAmp?.let { formatRatingVal(it) } ?: "")
                        }
                        var dropdownExpanded by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExposedDropdownMenuBox(
                                expanded = dropdownExpanded,
                                onExpandedChange = { dropdownExpanded = !dropdownExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = if (item.manualSizeAmp != null) "${formatRatingVal(item.manualSizeAmp!!)} A" else "Standard Frame",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Standard Frame") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false }
                                ) {
                                    STANDARD_ATS_SIZES_AMP.forEach { size ->
                                        DropdownMenuItem(
                                            text = { Text("${formatRatingVal(size)} A") },
                                            onClick = {
                                                dropdownExpanded = false
                                                textBuffer = formatRatingVal(size)
                                                onSave(item.copy(manualSizeAmp = size))
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = textBuffer,
                                onValueChange = { newText ->
                                    textBuffer = newText
                                    val trimmed = newText.trim()
                                    if (trimmed.isEmpty()) {
                                        onSave(item.copy(manualSizeAmp = null))
                                    } else {
                                        trimmed.toDoubleOrNull()?.let { parsed ->
                                            onSave(item.copy(manualSizeAmp = parsed))
                                        }
                                    }
                                },
                                label = { Text("Custom (Amps)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    val connectedLoads = (state as? UiState.Success)?.circuits?.filter { circ ->
                        if (circ.assignedAtsId != null) {
                            circ.assignedAtsId == item.atsId
                        } else {
                            circ.atsIncluded && item.atsId == "ATS-01"
                        }
                    } ?: emptyList()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("CONNECTED LOADS", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                    if (connectedLoads.isEmpty()) {
                        Text("No loads selected", color = SlateTextSecondary, fontSize = 12.sp)
                    } else {
                        connectedLoads.forEach { circ ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(circ.circuitName, color = ThemeTextColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${String.format("%.2f", circ.loadKVA)} kVA", color = ThemeTextColor, fontSize = 12.sp, maxLines = 1)
                            }
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

    Column(modifier = Modifier.fillMaxSize()) {
        // Section Header Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "UPS SIZING ENGINE (${upsList.size})",
                fontSize = 16.sp,
                color = ThemeTextColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onAddUps,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, SleekPrimary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = SleekPrimary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("+ Add UPS", fontSize = 12.sp, color = SleekPrimary, maxLines = 1)
            }
        }

        if (upsList.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Inbox, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No UPS Units Configured", color = ThemeTextColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Click '+ Add UPS' above to create an Uninterruptible Power Supply.", color = SlateTextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onAddUps, colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)) {
                        Text("+ Add UPS")
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(upsList, key = { _, item -> item.upsId }) { index, item ->
                    val defaultExpanded = upsList.size == 1 || index == 0
                    val instanceResult = upsResults[item.upsId] ?: successState?.upsResult
                    UpsInstanceCard(
                        item = item,
                        result = instanceResult,
                        state = state,
                        defaultExpanded = defaultExpanded,
                        onSave = onSave,
                        onDelete = { onDeleteUps(item.upsId) }
                    )
                }
            }
        }
    }
}

@Composable
fun UpsInstanceCard(
    item: UpsEntity,
    result: UpsResult?,
    state: UiState,
    defaultExpanded: Boolean,
    onSave: (UpsEntity) -> Unit,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(defaultExpanded) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Collapsed Summary Line
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${item.upsName} (${item.upsId})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ThemeTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "Rec. Size: ${result?.recommendedSize ?: "Auto"} kVA • Battery: ${item.autonomyMinutes} min (${item.batteryType})",
                        fontSize = 12.sp,
                        color = SlateTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete UPS", tint = SleekWarning, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle",
                        tint = SleekPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            val reduceMotion = LocalReduceMotion.current
            AnimatedVisibility(
                visible = isExpanded,
                enter = if (reduceMotion) fadeIn(animationSpec = VoltAtlasMotion.instantFadeSpec) else fadeIn(animationSpec = VoltAtlasMotion.fadeEnterSpec) + expandVertically(animationSpec = spring(stiffness = VoltAtlasMotion.SpringStiffnessLow)),
                exit = if (reduceMotion) fadeOut(animationSpec = VoltAtlasMotion.instantFadeSpec) else fadeOut(animationSpec = VoltAtlasMotion.fadeExitSpec) + shrinkVertically(animationSpec = spring(stiffness = VoltAtlasMotion.SpringStiffnessLow))
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(bottom = 12.dp))
                    if (result != null) {
                        Text("Status: ${formatStatusString(result.status)}", color = SleekPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("Critical Load: ${String.format("%.2f", result.totalCriticalLoadKVA)} kVA", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                        Text("Design Capacity: ${String.format("%.2f", result.designUpsKVA)} kVA", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                        if (result.recommendedSize != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Recommended Size: ${result.recommendedSize} kVA", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                            Text("Battery Bank: ${result.batteryBankRequired}", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                            Text("Autonomy: ${result.autonomyAtFullLoad}", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                        }
                        if (result.messages.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            result.messages.forEach { msg ->
                                Text(msg, color = SleekWarning, fontSize = 12.sp)
                            }
                        }
                    }

                    // Manual Override Controls
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Enable Manual Size Override", fontSize = 13.sp, color = ThemeTextColor, modifier = Modifier.weight(1f))
                        Switch(
                            checked = item.enableManualOverride,
                            onCheckedChange = { onSave(item.copy(enableManualOverride = it)) }
                        )
                    }
                    if (item.enableManualOverride) {
                        Spacer(modifier = Modifier.height(8.dp))
                        var textBuffer by remember(item.id, item.manualSizeKVA) {
                            mutableStateOf(item.manualSizeKVA?.let { formatRatingVal(it) } ?: "")
                        }
                        var dropdownExpanded by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExposedDropdownMenuBox(
                                expanded = dropdownExpanded,
                                onExpandedChange = { dropdownExpanded = !dropdownExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = if (item.manualSizeKVA != null) "${formatRatingVal(item.manualSizeKVA!!)} kVA" else "Standard Size",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Standard Size") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false }
                                ) {
                                    STANDARD_UPS_SIZES_KVA.forEach { size ->
                                        DropdownMenuItem(
                                            text = { Text("${formatRatingVal(size)} kVA") },
                                            onClick = {
                                                dropdownExpanded = false
                                                textBuffer = formatRatingVal(size)
                                                onSave(item.copy(manualSizeKVA = size))
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = textBuffer,
                                onValueChange = { newText ->
                                    textBuffer = newText
                                    val trimmed = newText.trim()
                                    if (trimmed.isEmpty()) {
                                        onSave(item.copy(manualSizeKVA = null))
                                    } else {
                                        trimmed.toDoubleOrNull()?.let { parsed ->
                                            onSave(item.copy(manualSizeKVA = parsed))
                                        }
                                    }
                                },
                                label = { Text("Custom (kVA)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    val connectedLoads = (state as? UiState.Success)?.circuits?.filter { circ ->
                        if (circ.assignedUpsId != null) {
                            circ.assignedUpsId == item.upsId
                        } else {
                            circ.upsBacked && item.upsId == "UPS-01"
                        }
                    } ?: emptyList()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("CONNECTED LOADS", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                    if (connectedLoads.isEmpty()) {
                        Text("No loads selected", color = SlateTextSecondary, fontSize = 12.sp)
                    } else {
                        connectedLoads.forEach { circ ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(circ.circuitName, color = ThemeTextColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${String.format("%.2f", circ.loadKVA)} kVA", color = ThemeTextColor, fontSize = 12.sp, maxLines = 1)
                            }
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
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("PF Correction Engine", fontSize = 18.sp, color = ThemeTextColor, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(modifier = Modifier.height(12.dp))
        
        results.forEach { result ->
            Card(colors = CardDefaults.cardColors(containerColor = DarkCardBg), modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Transformer: ${result.transformerId}", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                    Text("Status: ${result.status}", color = SleekPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("Existing PF: ${result.existingPF} -> Target PF: ${result.targetPf}", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                    Text("Existing Load: ${String.format("%.2f", result.existingKW)} kW", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                    Text("Required KVAR: ${String.format("%.2f", result.requiredKvar)}", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                    Text("Recommended Bank: ${result.bankCapacity} KVAR", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                    Text("Corrected PF: ${String.format("%.4f", result.correctedPF)} (${result.pfState})", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                    Text("Corrected Apparent Power: ${String.format("%.2f", result.correctedKVA)} kVA", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
