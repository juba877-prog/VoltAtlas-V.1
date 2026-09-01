package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.BoqEngine
import com.example.ui.UiState
import com.example.ui.designsystem.components.DsPillTabItem
import com.example.ui.designsystem.components.DsSegmentedPillTabRow
import com.example.ui.theme.*

// Combined BOQ / Procurement Screen
@Composable
fun BoqProcurementScreen(state: UiState.Success) {
    var subTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        DsPillTabItem(label = "Cables", icon = Icons.Filled.Cable, testTag = "boq_tab_cables", contentDescription = "Cables"),
        DsPillTabItem(label = "Access.", icon = Icons.Filled.Category, testTag = "boq_tab_accessories", contentDescription = "Accessories"),
        DsPillTabItem(label = "Drums", icon = Icons.Filled.Album, testTag = "boq_tab_drums", contentDescription = "Drums"),
        DsPillTabItem(label = "Conduit", icon = Icons.Filled.ViewColumn, testTag = "boq_tab_conduit", contentDescription = "Conduit Roll-Up")
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DsSegmentedPillTabRow(
                tabs = tabs,
                selectedTabIndex = subTab,
                onTabSelected = { subTab = it }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                when (subTab) {
                    0 -> BoqCablesScreen(state)
                    1 -> ProcurementScreen(state)
                    2 -> BoqDrumsView(state)
                    3 -> ConduitProcurementRollupScreen(state)
                }
            }
        }
    }
}

// 4. BOQ CABLES TAB
@Composable
fun BoqCablesScreen(state: UiState.Success) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Text("BILL OF QUANTITIES — CABLES", color = ThemeTextColor, style = SectionTitle)
        }

        val kpiResult = BoqEngine.generateBoqResult(state.circuits, state.sizingResults, state.drumAllocations)
        val kpi = kpiResult.kpi

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                border = BorderStroke(1.dp, SleekPrimary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("PROJECT PROCUREMENT KPI DASHBOARD", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Circuits", color = SlateTextSecondary, fontSize = 10.sp)
                            Text("${kpi.totalCircuits}", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Total Route", color = SlateTextSecondary, fontSize = 10.sp)
                            Text("${String.format("%.1f", kpi.totalRoute_m)} m", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Total Ordered", color = SlateTextSecondary, fontSize = 10.sp)
                            Text("${String.format("%.1f", kpi.totalOrdered_m)} m", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Drums Used", color = SlateTextSecondary, fontSize = 10.sp)
                            Text("${kpi.totalDrums}", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Cable Families", color = SlateTextSecondary, fontSize = 10.sp)
                            Text("${kpi.cableFamilies}", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Scrap / Waste", color = SlateTextSecondary, fontSize = 10.sp)
                            Text("${String.format("%.1f", kpi.wastePct)}%", color = if (kpi.wastePct > 15.0) SleekWarning else SleekPass, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Total Glands", color = SlateTextSecondary, fontSize = 10.sp)
                            Text("${kpi.totalGlands}", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Devices Count", color = SlateTextSecondary, fontSize = 10.sp)
                            Text("${kpi.totalDevices}", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        if (state.boq.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Text("No cable BOQ items calculated.", modifier = Modifier.padding(16.dp), color = SlateTextSecondary, fontSize = 12.sp)
                }
            }
        } else {
            items(state.boq, key = { "${it.cableType}_${it.size}_${it.productCode}_${it.manufacturer}" }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(item.cableType, color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Size", color = SlateTextSecondary, fontSize = 10.sp)
                                Text(item.size, color = ThemeTextColor, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Total Length", color = SlateTextSecondary, fontSize = 10.sp)
                                Text("${item.totalLength_m} m", color = ThemeTextColor, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Total Weight", color = SlateTextSecondary, fontSize = 10.sp)
                                if (item.status != "OK") {
                                    Text(item.status, color = SleekFail, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("${item.totalWeight_kg} kg", color = ThemeTextColor, fontWeight = FontWeight.Bold)
                                }
                            }
                            Column {
                                Text("Total Runs", color = SlateTextSecondary, fontSize = 10.sp)
                                Text("${item.runsCount}", color = ThemeTextColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 5. PROCUREMENT & ACCESSORIES SCREEN
@Composable
fun ProcurementScreen(state: UiState.Success) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Text("RESOLVED ACCESSORIES & PATHWAYS", color = ThemeTextColor, style = SectionTitle)
        }

        if (state.procurement.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Text("No accessory procurement items calculated.", modifier = Modifier.padding(16.dp), color = SlateTextSecondary, fontSize = 12.sp)
                }
            }
        } else {
            items(state.procurement, key = { it.circuitName }) { item ->
                val glandText = if (item.glandModel.isNotBlank() && item.glandModel != "N/A") "${item.glandModel} (${item.glandDesc})" else "Not specified"
                val lugsText = if (item.lugsType.isNotBlank()) item.lugsType else "Not specified"
                val heatShrinkText = if (item.cableOD_mm > 0.0) "Heat-Shrink Sleeve (${(item.cableOD_mm + 4).toInt()}/${maxOf(1, (item.cableOD_mm / 2.5).toInt())} mm)" else "Not specified"
                val trayText = if (item.requiredTrayWidth_mm > 0.0) "${item.requiredTrayWidth_mm.toInt()} mm (${item.requiredTrayWidth_mm.toInt() / 10} cm)" else "Not specified"

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item.circuitName, color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(item.cableSize, color = SleekPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = BorderColor)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cable Glands (CMP)", color = SlateTextSecondary, fontSize = 11.sp)
                            Text(glandText, color = ThemeTextColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Compression Lugs (El-Massalla)", color = SlateTextSecondary, fontSize = 11.sp)
                            Text(lugsText, color = ThemeTextColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Heat-Shrink Sleeve", color = SlateTextSecondary, fontSize = 11.sp)
                            Text(heatShrinkText, color = ThemeTextColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Standard Cable Tray Width", color = SlateTextSecondary, fontSize = 11.sp)
                            Text(trayText, color = SleekWarning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// 6. DRUMS SCREEN
@Composable
fun BoqDrumsView(state: UiState.Success) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Text("OPTIMIZED DRUM PACKING & CUTTING PLANS", color = ThemeTextColor, style = SectionTitle)
        }

        if (state.drumAllocations.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Text("No drum schedule allocations computed.", modifier = Modifier.padding(16.dp), color = SlateTextSecondary, fontSize = 12.sp)
                }
            }
        } else {
            val grouped = state.drumAllocations.groupBy { it.cableType to it.size }

            grouped.forEach { (key, allocations) ->
                item {
                    Text(
                        text = "${key.second} - ${key.first}",
                        color = SleekSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                items(allocations, key = { "${it.cableType}_${it.size}_drum_${it.drumIndex}" }) { drum ->
                    var isExpanded by rememberSaveable { mutableStateOf(false) }
                    val efficiency = if (drum.drumCapacity_m > 0.0) (drum.totalUsedLength_m / drum.drumCapacity_m) else 0.0
                    val wastePct = if (drum.drumCapacity_m > 0.0) (drum.wasteLength_m / drum.drumCapacity_m) * 100.0 else 0.0
                    val progressColor = when {
                        efficiency >= 0.85 -> SleekPass
                        efficiency >= 0.50 -> SleekWarning
                        else -> SleekFail
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("DRUM #${drum.drumIndex}", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Capacity: ${drum.drumCapacity_m.toInt()}m", color = SlateTextSecondary, fontSize = 10.sp)
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Used: ${drum.totalUsedLength_m.toInt()}m (${String.format("%.1f", efficiency * 100.0)}%)", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Scrap: ${String.format("%.1f", drum.wasteLength_m)}m (${String.format("%.1f", wastePct)}%)", color = if (wastePct > 15.0) SleekWarning else SlateTextSecondary, fontSize = 10.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { efficiency.toFloat() },
                                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = progressColor,
                                    trackColor = BorderColor
                                )
                                Text(
                                    text = "${(efficiency * 100).toInt()}% Utilized",
                                    color = progressColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                TextButton(
                                    onClick = { isExpanded = !isExpanded },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Expand details",
                                        tint = SleekPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isExpanded) "Hide Cutting Sheet" else "View Cutting Sheet (${drum.allocatedCuts.size} cuts)",
                                        color = SleekPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = fadeIn(tween(200)) + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                                exit = fadeOut(tween(150)) + shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessLow))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DarkBackground, RoundedCornerShape(6.dp))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("REQUIRED CONTIGUOUS CUTS:", color = SlateTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    drum.allocatedCuts.forEach { cut ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "• ${cut.circuitName} (Run #${cut.runIndex})",
                                                color = ThemeTextColor,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = "${cut.length_m} m",
                                                color = SleekSecondary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConduitProcurementRollupScreen(state: UiState.Success) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("CONDUIT PROCUREMENT ROLL-UP", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        if (state.conduitProcurement.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No valid conduit records in project.", color = SlateTextSecondary, fontSize = 13.sp)
                }
            }
        }
        items(state.conduitProcurement, key = { "${it.conduitType}_${it.tradeSize}" }) { group ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${group.tradeSize} ${group.conduitType}", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("${group.totalConduitsCount} pcs | ${group.totalLength_m.toInt()} m", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Circuits Count: ${group.circuitsCount}", color = SlateTextSecondary, fontSize = 11.sp)
                        Text("Avg Fill: ${String.format("%.1f", group.avgFillPercent)}% | Worst: ${String.format("%.1f", group.worstFillPercent)}%", color = ThemeTextColor, fontSize = 11.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("PASS: ${group.passCount} | FAIL: ${group.failCount}", color = SleekPass, fontSize = 11.sp)
                        Text("REVIEW: ${group.reviewCount} | MISSING: ${group.missingCount}", color = SleekWarning, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
