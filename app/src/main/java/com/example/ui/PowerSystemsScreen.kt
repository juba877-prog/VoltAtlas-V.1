package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.engine.*

@Composable
fun PowerSystemsScreen(
    state: UiState,
    generatorSettings: GeneratorEntity,
    atsSettings: AtsEntity,
    upsSettings: UpsEntity,
    pfGlobalSettings: PfGlobalEntity,
    pfTransformerSettings: List<PfTransformerEntity>,
    onSaveGenerator: (GeneratorEntity) -> Unit,
    onSaveAts: (AtsEntity) -> Unit,
    onSaveUps: (UpsEntity) -> Unit,
    onSavePfGlobal: (PfGlobalEntity) -> Unit,
    onSavePfTransformer: (PfTransformerEntity) -> Unit
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
                0 -> GeneratorSettingsPanel(state, generatorSettings, onSaveGenerator)
                1 -> AtsSettingsPanel(state, atsSettings, onSaveAts)
                2 -> UpsSettingsPanel(state, upsSettings, onSaveUps)
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
fun GeneratorSettingsPanel(state: UiState, settings: GeneratorEntity, onSave: (GeneratorEntity) -> Unit) {
    val result = (state as? UiState.Success)?.generatorResult
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("Generator Sizing Engine", fontSize = 18.sp, color = ThemeTextColor, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(modifier = Modifier.height(12.dp))
        
        if (result != null) {
            Card(colors = CardDefaults.cardColors(containerColor = DarkCardBg), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Status: ${formatStatusString(result.status)}", color = SleekPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("Total Connected Load: ${String.format("%.2f", result.totalConnectedLoad)} kVA", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                    Text("Essential Load (Connected): ${String.format("%.2f", result.essentialLoadKVA)} kVA", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                    Text("Required Capacity (/GenPF): ${String.format("%.2f", result.requiredGenerator)} kVA", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                    Text("Design Capacity: ${String.format("%.2f", result.designCapacity)} kVA", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                    if (result.finalSize != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Recommended Size: ${result.finalSize} kVA", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                        Text("Loading: ${String.format("%.1f", result.loadingPct ?: 0.0)}%", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                    }
                    if (result.messages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        result.messages.forEach { msg ->
                            Text(msg, color = SleekWarning, fontSize = 12.sp)
                        }
                    }
                    
                    val connectedLoads = (state as? UiState.Success)?.circuits?.filter { it.generatorIncluded } ?: emptyList()
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
fun AtsSettingsPanel(state: UiState, settings: AtsEntity, onSave: (AtsEntity) -> Unit) {
    val result = (state as? UiState.Success)?.atsResult
    var normalSource by remember { mutableStateOf(settings.normalSourceId ?: "") }
    var emergencySource by remember { mutableStateOf(settings.emergencySourceId ?: "") }
    var outputBoardId by remember { mutableStateOf(settings.outputBoardId) }

    LaunchedEffect(settings.normalSourceId) {
        normalSource = settings.normalSourceId ?: ""
    }

    LaunchedEffect(settings.emergencySourceId) {
        emergencySource = settings.emergencySourceId ?: ""
    }

    LaunchedEffect(settings.outputBoardId) {
        outputBoardId = settings.outputBoardId
    }

    val successState = state as? UiState.Success
    val circuits = successState?.circuits ?: emptyList()
    val sourceOptions = listOf("Transformer", "Generator", "Grid") + circuits.map { it.circuitName }.distinct()
    
    // Only boards (MDB, SMDB, DB) can be ATS output boards.
    // Also prevent selecting a board that is already the normal source.
    val outputBoardCandidates = circuits.filter { 
        (it.panelType == "MDB" || it.panelType == "SMDB" || it.panelType == "DB" || it.circuitName.contains("Board", true) || it.circuitName.contains("MDB", true) || it.circuitName.contains("DB", true))
        && it.circuitName != normalSource
    }
    val outputBoardOptions = listOf("None") + outputBoardCandidates.map { it.circuitName }
    val currentOutputBoardName = outputBoardCandidates.find { it.id == outputBoardId }?.circuitName ?: "None"

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("ATS Sizing Engine", fontSize = 18.sp, color = ThemeTextColor, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(colors = CardDefaults.cardColors(containerColor = DarkCardBg), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("ATS Connections", color = SleekPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                com.example.ui.components.VoltAtlasDropdown(
                    value = normalSource,
                    options = sourceOptions,
                    onSelect = { 
                        normalSource = it
                        onSave(settings.copy(normalSourceId = it.takeIf { s -> s.isNotBlank() }))
                    },
                    label = "Normal Source"
                )
                com.example.ui.components.VoltAtlasDropdown(
                    value = emergencySource,
                    options = sourceOptions,
                    onSelect = { 
                        emergencySource = it
                        onSave(settings.copy(emergencySourceId = it.takeIf { s -> s.isNotBlank() }))
                    },
                    label = "Emergency Source"
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                com.example.ui.components.VoltAtlasDropdown(
                    value = currentOutputBoardName,
                    options = outputBoardOptions,
                    onSelect = { selectedName ->
                        val selectedId = if (selectedName == "None") null else outputBoardCandidates.find { it.circuitName == selectedName }?.id
                        outputBoardId = selectedId
                        onSave(settings.copy(outputBoardId = selectedId))
                    },
                    label = "Downstream Output Board"
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (result != null) {
            Card(colors = CardDefaults.cardColors(containerColor = DarkCardBg), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Status: ${formatStatusString(result.status)}", color = SleekPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("Selected Load: ${String.format("%.2f", result.selectedLoad)} kVA", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                    Text("Governing Current: ${String.format("%.1f", result.governingCurrent)} A", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                    if (result.finalRating != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Recommended Rating: ${result.finalRating} A", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                        Text("Construction: ${result.construction}", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                        Text("Poles: ${result.poles}", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                        Text("Required Icw: ${String.format("%.2f", result.requiredIcw)} kA", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                        Text("Required Icm: ${String.format("%.2f", result.requiredIcm)} kA", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                        Text("Transfer Status: ${result.transferStatus}", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                    }
                    if (result.messages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        result.messages.forEach { msg ->
                            Text(msg, color = SleekWarning, fontSize = 12.sp)
                        }
                    }
                    
                    val connectedLoads = (state as? UiState.Success)?.circuits?.filter { it.atsIncluded } ?: emptyList()
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
fun UpsSettingsPanel(state: UiState, settings: UpsEntity, onSave: (UpsEntity) -> Unit) {
    val result = (state as? UiState.Success)?.upsResult
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("UPS Sizing Engine", fontSize = 18.sp, color = ThemeTextColor, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(modifier = Modifier.height(12.dp))
        
        if (result != null) {
            Card(colors = CardDefaults.cardColors(containerColor = DarkCardBg), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Status: ${formatStatusString(result.status)}", color = SleekPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("Critical Load: ${String.format("%.2f", result.totalCriticalLoadKVA)} kVA", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                    Text("Design Capacity: ${String.format("%.2f", result.designUpsKVA)} kVA", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                    if (result.recommendedSize != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Recommended Size: ${result.recommendedSize} kVA", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                        Text("Battery Bank: ${result.batteryBankRequired}", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                        Text("Autonomy: ${result.autonomyAtFullLoad}", color = ThemeTextColor, fontSize = 13.sp, maxLines = 1)
                    }
                    if (result.messages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        result.messages.forEach { msg ->
                            Text(msg, color = SleekWarning, fontSize = 12.sp)
                        }
                    }
                    
                    val connectedLoads = (state as? UiState.Success)?.circuits?.filter { it.upsBacked } ?: emptyList()
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
