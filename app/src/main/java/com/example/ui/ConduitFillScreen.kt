package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.example.ui.theme.VoltAtlasMotion
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.data.ConduitSettingsEntity
import com.example.ui.components.statusColor
import com.example.ui.components.statusSymbol
import com.example.ui.components.StatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConduitFillScreen(viewModel: EngineeringViewModel) {
    val stateVal by viewModel.uiState.collectAsState()
    val state = stateVal as? UiState.Success ?: return

    val circuits = state.circuits
    val results = state.conduitResults

    var selectedFilter by rememberSaveable { mutableStateOf("ALL") }
    var expandedDetailId by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedCircuitForEdit by rememberSaveable { mutableStateOf<CircuitEntity?>(null) }

    val totalCircuitsCount = circuits.size
    val passCount = results.values.count { it.status == "PASS" }
    val failCount = results.values.count { it.status == "FAIL" }
    val reviewCount = results.values.count { it.status == "REVIEW REQUIRED" }
    val missingCount = results.values.count { it.status == "DATA MISSING" || it.status == "NO STANDARD SIZE AVAILABLE" }

    val filteredResults = remember(circuits, results, selectedFilter) {
        circuits.filter { circ ->
            val res = results[circ.id]
            when (selectedFilter) {
                "PASS" -> res?.status == "PASS"
                "FAIL" -> res?.status == "FAIL"
                "REVIEW" -> res?.status == "REVIEW REQUIRED"
                "MISSING" -> res?.status == "DATA MISSING" || res?.status == "NO STANDARD SIZE AVAILABLE"
                else -> true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(12.dp)
            .testTag("conduit_screen")
    ) {
        // Summary KPI Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("CONDUIT FILL & CAPACITY", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Total Circuits: $totalCircuitsCount", color = ThemeTextColor, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$passCount", color = SleekPass, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("PASS", color = SleekPass, fontSize = 9.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$failCount", color = SleekFail, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("FAIL", color = SleekFail, fontSize = 9.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$reviewCount", color = SleekWarning, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("REVIEW", color = SleekWarning, fontSize = 9.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$missingCount", color = SlateTextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("MISSING", color = SlateTextSecondary, fontSize = 9.sp)
                    }
                }
            }
        }

        // Status Filter Chips - horizontally scrollable to prevent overflow
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("ALL" to "All", "PASS" to "Pass", "FAIL" to "Fail", "REVIEW" to "Review", "MISSING" to "Missing").forEach { (key, label) ->
                FilterChip(
                    selected = selectedFilter == key,
                    onClick = { selectedFilter = key },
                    label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SleekPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = SleekPrimary
                    )
                )
            }
        }

        // List of Conduit Cards
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            if (filteredResults.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No conduit records found for selected filter.", color = SlateTextSecondary, fontSize = 13.sp)
                    }
                }
            }

            items(filteredResults, key = { it.id }) { circ ->
                val res = results[circ.id]
                val isExpanded = expandedDetailId == circ.id
                val sizingRes = state.sizingResults[circ.id]

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("conduit_card_${circ.id}"),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, statusColor(res?.status ?: ""))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(circ.circuitName, color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = "${sizingRes?.cableSize_mm2 ?: (circ.overrideCableSize ?: "Auto")} ${circ.cableType}",
                                    color = SlateTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Shared Status Badge
                            StatusBadge(status = res?.status ?: "DATA MISSING")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (res != null && res.status != "NOT APPLICABLE") {
                            // Section 7 Fixed Labels
                            // 3-column grid with proper weight distribution
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Main Cable OD", color = SlateTextSecondary, fontSize = 9.sp, maxLines = 1)
                                    Text("${String.format("%.1f", res.mainCableOD_mm)} mm", color = ThemeTextColor, fontSize = 11.sp, maxLines = 1)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Main Cable Area", color = SlateTextSecondary, fontSize = 9.sp, maxLines = 1)
                                    Text("${String.format("%.1f", res.mainCableAreaMm2)} mm²", color = ThemeTextColor, fontSize = 11.sp, maxLines = 1)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("CPC Cable OD", color = SlateTextSecondary, fontSize = 9.sp, maxLines = 1)
                                    Text(
                                        text = if (res.cpcOD_mm > 0.0) "${String.format("%.1f", res.cpcOD_mm)} mm" else if (res.isProvisionalWithoutCPC) "REQUIRED" else "N/A",
                                        color = if (res.isProvisionalWithoutCPC) SleekWarning else ThemeTextColor,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("CPC Cable Area", color = SlateTextSecondary, fontSize = 9.sp, maxLines = 1)
                                    Text(
                                        text = if (res.cpcCableAreaMm2 > 0.0) "${String.format("%.1f", res.cpcCableAreaMm2)} mm²" else if (res.isProvisionalWithoutCPC) "REQUIRED" else "N/A",
                                        color = if (res.isProvisionalWithoutCPC) SleekWarning else ThemeTextColor,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Conduit", color = SlateTextSecondary, fontSize = 9.sp, maxLines = 1)
                                    Text(
                                        text = if (res.recommendedTradeSize != "N/A") "${res.recommendedTradeSize} (${res.conduitsRequired}x)" else "N/A",
                                        color = SleekPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Fill / Limit", color = SlateTextSecondary, fontSize = 9.sp, maxLines = 1)
                                    Text(
                                        text = "${String.format("%.1f", res.fillRatioPercent)}% / ${res.allowedFillPercent.toInt()}%",
                                        color = if (res.fillRatioPercent > res.allowedFillPercent) SleekFail else ThemeTextColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }

                            if (res.isProvisionalWithoutCPC) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = SleekWarning.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, SleekWarning),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "PROVISIONAL — CPC NOT INCLUDED",
                                        color = SleekWarning,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Action row: Edit Override & Show Calculation Details
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { expandedDetailId = if (isExpanded) null else circ.id },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (isExpanded) "Hide Calculation" else "View Calculation", fontSize = 11.sp, color = SleekPrimary)
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = SleekPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = { selectedCircuitForEdit = circ },
                                border = BorderStroke(1.dp, BorderColor),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = "Edit", modifier = Modifier.size(14.dp), tint = ThemeTextColor)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Configure Conduit", fontSize = 10.sp, color = ThemeTextColor)
                            }
                        }

                        // Detailed Calculation Breakdown (Expandable)
                        AnimatedVisibility(
                            visible = isExpanded && res != null,
                            enter = fadeIn(animationSpec = VoltAtlasMotion.fadeEnterSpec) + expandVertically(animationSpec = spring(stiffness = VoltAtlasMotion.SpringStiffnessLow)),
                            exit = fadeOut(animationSpec = VoltAtlasMotion.fadeExitSpec) + shrinkVertically(animationSpec = spring(stiffness = VoltAtlasMotion.SpringStiffnessLow))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkBackground, shape = RoundedCornerShape(6.dp))
                                    .padding(10.dp)
                            ) {
                                Text("CALCULATION DETAILS & PROVENANCE", color = SleekPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("• Main Cable OD: ${res?.mainCableOD_mm ?: 0.0} mm", color = ThemeTextColor, fontSize = 11.sp)
                                Text("• Main Cable Area: ${String.format("%.2f", res?.mainCableAreaMm2 ?: 0.0)} mm²", color = ThemeTextColor, fontSize = 11.sp)
                                Text("• CPC Cable OD: ${if ((res?.cpcOD_mm ?: 0.0) > 0.0) "${res?.cpcOD_mm} mm" else "DATA REQUIRED"}", color = ThemeTextColor, fontSize = 11.sp)
                                Text("• CPC Cable Area: ${String.format("%.2f", res?.cpcCableAreaMm2 ?: 0.0)} mm²", color = ThemeTextColor, fontSize = 11.sp)
                                Text("• Total Occupied Area: ${String.format("%.2f", res?.totalCableAreaMm2 ?: 0.0)} mm²", color = ThemeTextColor, fontSize = 11.sp)
                                Text("• Conduit Internal Diameter: ${res?.internalDiameterMm ?: 0.0} mm", color = ThemeTextColor, fontSize = 11.sp)
                                Text("• Conduit Internal Area: ${String.format("%.2f", res?.conduitAreaMm2 ?: 0.0)} mm²", color = ThemeTextColor, fontSize = 11.sp)
                                Text("• Fill Ratio: ${String.format("%.2f", res?.fillRatioPercent ?: 0.0)}%", color = ThemeTextColor, fontSize = 11.sp)
                                Text("• Allowed Limit: ${res?.allowedFillPercent ?: 40.0}%", color = ThemeTextColor, fontSize = 11.sp)
                                Text("• CPC Product: ${res?.cpcProductCode} (${res?.cpcManufacturer})", color = ThemeTextColor, fontSize = 11.sp)
                                Text("• CPC Provenance: ${res?.cpcProvenance}", color = SlateTextSecondary, fontSize = 10.sp)
                                Text("• Design Profile: ${res?.profile ?: "PROJECT_DESIGN"}", color = ThemeTextColor, fontSize = 11.sp)
                                Text("• Provenance: ${res?.provenance ?: "PROJECT_DESIGN_RULE"}", color = SlateTextSecondary, fontSize = 10.sp)

                                if (res?.validationMessages?.isNotEmpty() == true) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    res.validationMessages.forEach { msg ->
                                        Text("⚠️ $msg", color = SleekWarning, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Conduit Override Editor Dialog
    val circ = selectedCircuitForEdit
    if (circ != null) {
        val existingResult = results[circ.id]

        var routingMode by rememberSaveable { mutableStateOf("ONE_PER_CONDUIT") }
        var designProfile by rememberSaveable { mutableStateOf("PROJECT_DESIGN") }
        var customFillPctText by rememberSaveable { mutableStateOf("40.0") }
        var isDirectBuried by rememberSaveable { mutableStateOf(false) }
        var cpcArrangement by rememberSaveable { mutableStateOf("INTERNAL_CORE") }
        var manualOverrideTradeSize by rememberSaveable { mutableStateOf<String?>(null) }
        var manualCpcODText by rememberSaveable { mutableStateOf("") }
        var expandedManualDrop by rememberSaveable { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { selectedCircuitForEdit = null },
            title = { Text("Configure Conduit - ${circ.circuitName}", color = ThemeTextColor, fontSize = 15.sp) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Direct Buried Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Direct Buried / Not In Conduit", color = ThemeTextColor, fontSize = 12.sp)
                        Switch(checked = isDirectBuried, onCheckedChange = { isDirectBuried = it })
                    }

                    if (!isDirectBuried) {
                        // Routing Mode
                        Text("Routing Mode", color = SleekPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = routingMode == "ONE_PER_CONDUIT",
                                onClick = { routingMode = "ONE_PER_CONDUIT" },
                                label = { Text("1 Cable / Conduit", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = routingMode == "ALL_IN_ONE",
                                onClick = { routingMode = "ALL_IN_ONE" },
                                label = { Text("All Parallel In 1 Conduit", fontSize = 11.sp) }
                            )
                        }

                        // Design Profile
                        Text("Design Profile & Fill Limit", color = SleekPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = designProfile == "PROJECT_DESIGN",
                                onClick = { designProfile = "PROJECT_DESIGN" },
                                label = { Text("Project (40%)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = designProfile == "NEC_CHAPTER_9",
                                onClick = { designProfile = "NEC_CHAPTER_9" },
                                label = { Text("NEC Ch. 9", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = designProfile == "CUSTOM",
                                onClick = { designProfile = "CUSTOM" },
                                label = { Text("Custom", fontSize = 11.sp) }
                            )
                        }

                        if (designProfile == "CUSTOM") {
                            OutlinedTextField(
                                value = customFillPctText,
                                onValueChange = { customFillPctText = it },
                                label = { Text("Custom Fill Limit %") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // CPC Arrangement
                        Text("CPC / Earth Conductor Arrangement", color = SleekPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        listOf(
                            "INTERNAL_CORE" to "Multicore internal core",
                            "SEPARATE_CONDUIT" to "Separate cable in conduit",
                            "EXTERNAL" to "Installed outside conduit",
                            "ARMOURED" to "Armour / sheath used"
                        ).forEach { (value, label) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = cpcArrangement == value,
                                    onClick = { cpcArrangement = value }
                                )
                                Text(label, color = ThemeTextColor, fontSize = 11.sp)
                            }
                        }

                        if (cpcArrangement == "SEPARATE_CONDUIT") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkBackground),
                                border = BorderStroke(1.dp, BorderColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("CPC DETAILS", color = SleekPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("• CPC Size: ${existingResult?.cpcSizeMm2 ?: 0.0} mm²", color = ThemeTextColor, fontSize = 11.sp)
                                    Text("• CPC Material: ${existingResult?.cpcMaterial ?: "Cu"}", color = ThemeTextColor, fontSize = 11.sp)
                                    Text("• Resolved Product: ${existingResult?.cpcProductCode ?: "N/A"}", color = ThemeTextColor, fontSize = 11.sp)
                                    Text("• Resolved OD: ${if ((existingResult?.cpcOD_mm ?: 0.0) > 0.0) "${existingResult?.cpcOD_mm} mm" else "DATA REQUIRED"}", color = ThemeTextColor, fontSize = 11.sp)

                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = manualCpcODText,
                                        onValueChange = { manualCpcODText = it },
                                        label = { Text("Manual CPC OD (mm)") },
                                        placeholder = { Text("e.g. 10.2") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Manual Conduit Size Override
                        Text("Conduit Size Override", color = SleekPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = manualOverrideTradeSize ?: "AUTO (Recommended)",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Trade Size") },
                                trailingIcon = {
                                    IconButton(onClick = { expandedManualDrop = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = expandedManualDrop,
                                onDismissRequest = { expandedManualDrop = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("AUTO (Recommended)", color = ThemeTextColor) },
                                    onClick = {
                                        manualOverrideTradeSize = null
                                        expandedManualDrop = false
                                    }
                                )
                                Catalog.conduits.forEach { entry ->
                                    DropdownMenuItem(
                                        text = { Text(entry.tradeSize, color = ThemeTextColor) },
                                        onClick = {
                                            manualOverrideTradeSize = entry.tradeSize
                                            expandedManualDrop = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val entity = ConduitSettingsEntity(
                            projectId = circ.projectId,
                            circuitId = circ.id,
                            routingMode = routingMode,
                            designProfile = designProfile,
                            customFillLimitPct = customFillPctText.toDoubleOrNull() ?: 40.0,
                            isDirectBuried = isDirectBuried,
                            cpcArrangement = cpcArrangement,
                            manualConduitOverride = manualOverrideTradeSize,
                            manualCpcOD = manualCpcODText.toDoubleOrNull()
                        )
                        viewModel.saveConduitSetting(entity)
                        selectedCircuitForEdit = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val entity = ConduitSettingsEntity(
                                projectId = circ.projectId,
                                circuitId = circ.id,
                                routingMode = "ONE_PER_CONDUIT",
                                designProfile = "PROJECT_DESIGN",
                                customFillLimitPct = 40.0,
                                isDirectBuried = false,
                                cpcArrangement = "INTERNAL_CORE",
                                manualConduitOverride = null,
                                manualCpcOD = null
                            )
                            viewModel.saveConduitSetting(entity)
                            selectedCircuitForEdit = null
                        }
                    ) {
                        Text("Reset to Auto", color = SleekWarning, fontSize = 11.sp)
                    }

                    TextButton(onClick = { selectedCircuitForEdit = null }) {
                        Text("Cancel", color = SlateTextSecondary, fontSize = 11.sp)
                    }
                }
            },
            containerColor = DarkCardBg
        )
    }
}
