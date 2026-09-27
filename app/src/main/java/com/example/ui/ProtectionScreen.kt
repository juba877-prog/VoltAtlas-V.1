package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.ui.designsystem.components.DsPillTabItem
import com.example.ui.designsystem.components.DsSegmentedPillTabRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.engine.*
import com.example.ui.components.statusColor
import com.example.ui.components.statusSymbol
import com.example.ui.components.StatusBadge
import com.example.ui.designsystem.theme.*
import com.example.ui.theme.LocalThemeIsDark
import kotlin.math.roundToInt

@Composable
fun ProtectionScreen(
    state: UiState.Success,
    onSaveEarthing: ((com.example.data.EarthingEntity) -> Unit)? = null
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf(
        DsPillTabItem(label = "Protection", icon = Icons.Filled.Security, testTag = "tab_protection", contentDescription = "Full Protection"),
        DsPillTabItem(label = "SC", icon = Icons.Filled.Bolt, testTag = "tab_sc", contentDescription = "Short Circuit"),
        DsPillTabItem(label = "Coord", icon = Icons.AutoMirrored.Filled.AltRoute, testTag = "tab_coord", contentDescription = "Coordination"),
        DsPillTabItem(label = "EFL", icon = Icons.Filled.Refresh, testTag = "tab_efl", contentDescription = "Earth Fault Loop"),
        DsPillTabItem(label = "Earthing", icon = Icons.Filled.ElectricalServices, testTag = "tab_earthing", contentDescription = "Earthing")
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DsSegmentedPillTabRow(
                tabs = tabs,
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp)) {
                when (selectedTab) {
                    0 -> FullProtectionPanel(state)
                    1 -> ShortCircuitPanel(state)
                    2 -> CoordinationPanel(state)
                    3 -> EarthFaultPanel(state)
                    4 -> EarthingPanel(state, onSaveEarthing)
                }
            }
        }
    }
}

@Composable
fun EarthingPanel(
    state: UiState.Success,
    onSaveEarthing: ((com.example.data.EarthingEntity) -> Unit)? = null
) {
    val earthingSettings = state.earthingEntity ?: com.example.data.EarthingEntity()
    val result = state.earthingResult

    var soilRes by remember(earthingSettings) { mutableStateOf(earthingSettings.soilResistivity_ohmM.toString()) }
    var rodL by remember(earthingSettings) { mutableStateOf(earthingSettings.rodLength_m.toString()) }
    var rodD by remember(earthingSettings) { mutableStateOf(earthingSettings.rodDiameter_mm.toString()) }
    var rodSpacing by remember(earthingSettings) { mutableStateOf(earthingSettings.rodSpacing_m.toString()) }
    var ringCond by remember(earthingSettings) { mutableStateOf(earthingSettings.ringConductor_mm2.toString()) }
    var ringH by remember(earthingSettings) { mutableStateOf(earthingSettings.ringBurialDepth_m.toString()) }
    var disconnectT by remember(earthingSettings) { mutableStateOf(earthingSettings.disconnectionTime_s.toString()) }
    var projectType by remember(earthingSettings) { mutableStateOf(earthingSettings.projectType) }

    var isAutoDesignEnabled by remember(earthingSettings) { mutableStateOf(earthingSettings.isAutoDesignEnabled) }
    var targetOverride by remember(earthingSettings) { mutableStateOf(earthingSettings.targetResistanceOverride_ohm?.toString() ?: "") }
    var soilSource by remember(earthingSettings) { mutableStateOf(earthingSettings.soilResistivitySource) }
    var marginPct by remember(earthingSettings) { mutableStateOf(earthingSettings.designMarginPct.toString()) }
    var siteLength by remember(earthingSettings) { mutableStateOf(earthingSettings.siteLength_m?.toString() ?: "") }
    var siteWidth by remember(earthingSettings) { mutableStateOf(earthingSettings.siteWidth_m?.toString() ?: "") }
    var candidateToConfirm by remember { mutableStateOf<com.example.engine.ElectrodeCandidate?>(null) }

    val projectTypeOptions = listOf("Commercial", "Villa", "Residential", "Industrial")

    if (candidateToConfirm != null) {
        val candidate = candidateToConfirm!!
        AlertDialog(
            onDismissRequest = { candidateToConfirm = null },
            title = { Text("Apply Recommended Layout?", color = ThemeTextColor, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will overwrite your earthing parameters with:\n" +
                    "• Rod Length: ${candidate.rodLength_m} m\n" +
                    "• Rod Diameter: ${candidate.rodDiameter_mm} mm\n" +
                    "• Rod Spacing: ${candidate.rodSpacing_m} m\n\n" +
                    "Achieved resistance will be recalculated.",
                    color = SlateTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cand = candidateToConfirm!!
                        candidateToConfirm = null
                        val updated = earthingSettings.copy(
                            rodLength_m = cand.rodLength_m,
                            rodDiameter_mm = cand.rodDiameter_mm,
                            rodSpacing_m = cand.rodSpacing_m,
                            autoDesignAppliedConfigId = cand.id
                        )
                        onSaveEarthing?.invoke(updated)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)
                ) {
                    Text("Apply Layout", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { candidateToConfirm = null }) {
                    Text("Cancel", color = SlateTextSecondary)
                }
            },
            containerColor = DarkCardBg
        )
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Auto Earthing System", color = ThemeTextColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("IEC 60364-5-54 / BS 7430 — Rod resistance, conductor & loop verification", color = SlateTextSecondary, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Card(colors = CardDefaults.cardColors(containerColor = DarkCardBg), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("EARTHING SYSTEM INPUTS", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.example.ui.components.VoltAtlasTextField(
                        value = soilRes,
                        onValueChange = {
                            soilRes = it
                            it.toDoubleOrNull()?.let { v -> onSaveEarthing?.invoke(earthingSettings.copy(soilResistivity_ohmM = v)) }
                        },
                        label = "Soil Resistivity ρ (Ω·m)",
                        modifier = Modifier.weight(1f)
                    )
                    com.example.ui.components.VoltAtlasDropdown(
                        value = projectType,
                        options = projectTypeOptions,
                        onSelect = {
                            projectType = it
                            onSaveEarthing?.invoke(earthingSettings.copy(projectType = it))
                        },
                        label = "Project Type",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.example.ui.components.VoltAtlasTextField(
                        value = rodL,
                        onValueChange = {
                            rodL = it
                            it.toDoubleOrNull()?.let { v -> onSaveEarthing?.invoke(earthingSettings.copy(rodLength_m = v)) }
                        },
                        label = "Rod Length L (m)",
                        modifier = Modifier.weight(1f)
                    )
                    com.example.ui.components.VoltAtlasTextField(
                        value = rodD,
                        onValueChange = {
                            rodD = it
                            it.toDoubleOrNull()?.let { v -> onSaveEarthing?.invoke(earthingSettings.copy(rodDiameter_mm = v)) }
                        },
                        label = "Rod Diam d (mm)",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.example.ui.components.VoltAtlasTextField(
                        value = rodSpacing,
                        onValueChange = {
                            rodSpacing = it
                            it.toDoubleOrNull()?.let { v -> onSaveEarthing?.invoke(earthingSettings.copy(rodSpacing_m = v)) }
                        },
                        label = "Rod Spacing (m)",
                        modifier = Modifier.weight(1f)
                    )
                    com.example.ui.components.VoltAtlasTextField(
                        value = ringCond,
                        onValueChange = {
                            ringCond = it
                            it.toDoubleOrNull()?.let { v -> onSaveEarthing?.invoke(earthingSettings.copy(ringConductor_mm2 = v)) }
                        },
                        label = "Ring Cond (mm²)",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.example.ui.components.VoltAtlasTextField(
                        value = ringH,
                        onValueChange = {
                            ringH = it
                            it.toDoubleOrNull()?.let { v -> onSaveEarthing?.invoke(earthingSettings.copy(ringBurialDepth_m = v)) }
                        },
                        label = "Ring Depth h (m)",
                        modifier = Modifier.weight(1f)
                    )
                    com.example.ui.components.VoltAtlasTextField(
                        value = disconnectT,
                        onValueChange = {
                            disconnectT = it
                            it.toDoubleOrNull()?.let { v -> onSaveEarthing?.invoke(earthingSettings.copy(disconnectionTime_s = v)) }
                        },
                        label = "Disconnect Time t (s)",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Target Resistance Auto-Design Card
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = BorderStroke(1.dp, if (isAutoDesignEnabled) SleekPrimary.copy(alpha = 0.5f) else BorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TARGET RESISTANCE AUTO-DESIGN", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Opt-in electrode search & configuration layout recommendations", color = SlateTextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isAutoDesignEnabled,
                        onCheckedChange = { checked ->
                            isAutoDesignEnabled = checked
                            onSaveEarthing?.invoke(earthingSettings.copy(isAutoDesignEnabled = checked))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = SleekPrimary, checkedTrackColor = SleekPrimary.copy(alpha = 0.3f))
                    )
                }

                if (isAutoDesignEnabled) {
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.example.ui.components.VoltAtlasTextField(
                            value = targetOverride,
                            onValueChange = {
                                targetOverride = it
                                val v = it.toDoubleOrNull()
                                onSaveEarthing?.invoke(earthingSettings.copy(targetResistanceOverride_ohm = v))
                            },
                            label = "Target R (Ω) [Blank = Default]",
                            modifier = Modifier.weight(1f)
                        )
                        com.example.ui.components.VoltAtlasDropdown(
                            value = soilSource,
                            options = listOf("MEASURED", "SOIL_REPORT", "ASSUMED"),
                            onSelect = {
                                soilSource = it
                                onSaveEarthing?.invoke(earthingSettings.copy(soilResistivitySource = it))
                            },
                            label = "Soil Data Source",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (soilSource.equals("ASSUMED", ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SleekWarning.copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, SleekWarning.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "⚠️ Assumed soil data — verify with a Wenner 4-pin test before final construction.",
                                color = SleekWarning,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.example.ui.components.VoltAtlasTextField(
                            value = marginPct,
                            onValueChange = {
                                marginPct = it
                                it.toDoubleOrNull()?.let { v -> onSaveEarthing?.invoke(earthingSettings.copy(designMarginPct = v)) }
                            },
                            label = "Safety Margin (%)",
                            modifier = Modifier.weight(1f)
                        )
                        com.example.ui.components.VoltAtlasTextField(
                            value = siteLength,
                            onValueChange = {
                                siteLength = it
                                val v = it.toDoubleOrNull()
                                onSaveEarthing?.invoke(earthingSettings.copy(siteLength_m = v))
                            },
                            label = "Site Length (m)",
                            modifier = Modifier.weight(1f)
                        )
                        com.example.ui.components.VoltAtlasTextField(
                            value = siteWidth,
                            onValueChange = {
                                siteWidth = it
                                val v = it.toDoubleOrNull()
                                onSaveEarthing?.invoke(earthingSettings.copy(siteWidth_m = v))
                            },
                            label = "Site Width (m)",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    val autoRes = result?.autoDesignResult
                    if (autoRes != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = BorderColor)
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "Design Target: ${String.format("%.2f", autoRes.effectiveTargetResistance_ohm)} Ω | Design Limit: ${String.format("%.2f", autoRes.designLimitResistance_ohm)} Ω",
                            color = SleekPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (!autoRes.feasible) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SleekFail.copy(alpha = 0.1f)),
                                border = BorderStroke(1.dp, SleekFail.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("❌ NO FEASIBLE CONFIGURATION", color = SleekFail, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Could not achieve target resistance of ${autoRes.designLimitResistance_ohm} Ω within rod limit.", color = ThemeTextColor, fontSize = 11.sp)
                                    Text("Recommendation: Consider soil chemical treatment, deep boreholes, or an enhanced ring mesh.", color = SlateTextSecondary, fontSize = 11.sp)
                                }
                            }
                        } else {
                            autoRes.recommended?.let { rec ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("RECOMMENDED CONFIGURATION", color = SleekPass, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                ElectrodeCandidateCard(
                                    candidate = rec,
                                    isRecommended = true,
                                    onApply = { candidateToConfirm = it }
                                )
                            }

                            if (autoRes.alternatives.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("ALTERNATIVE CONFIGURATIONS", color = SlateTextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                autoRes.alternatives.forEach { alt ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    ElectrodeCandidateCard(
                                        candidate = alt,
                                        isRecommended = false,
                                        onApply = { candidateToConfirm = it }
                                    )
                                }
                            }
                        }

                        if (autoRes.warnings.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            autoRes.warnings.forEach { w ->
                                Text(w, color = SleekWarning, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (result != null) {
            val statusColor = statusColor(result.status)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("VERIFICATION RESULTS", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(result.status, color = statusColor, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))

                    Text("Soil Classification: ${result.soilClass}", color = ThemeTextColor, fontSize = 13.sp)
                    Text("Single Rod Resistance R1: ${String.format("%.2f", result.singleRodResistance_ohm)} Ω", color = ThemeTextColor, fontSize = 13.sp)
                    Text("Required Rods / Earth Pits: ${result.requiredRods} rods / ${result.earthPits} pits", color = ThemeTextColor, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Achieved R: ${String.format("%.2f", result.achievedResistance_ohm)} Ω", color = if (result.achievedResistance_ohm <= result.targetResistance_ohm) SleekPass else SleekFail, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Target R: ${String.format("%.2f", result.targetResistance_ohm)} Ω", color = ThemeTextColor, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Text("Prospective Fault Ipf: ${String.format("%.2f", result.prospectiveFault_A)} A", color = ThemeTextColor, fontSize = 13.sp)
                    Text("Corrected Fault Icorr: ${String.format("%.2f", result.correctedFault_A)} A", color = ThemeTextColor, fontSize = 13.sp)
                    Text("Earth Conductor Size (Exact → Std): ${String.format("%.2f", result.earthConductorExact_mm2)} mm² → ${result.earthConductorStandard_mm2.toInt()} mm²", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Main Equipotential Bonding: ${result.equipotentialBonding_mm2.toInt()} mm²", color = ThemeTextColor, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Earth Loop Zs: ${String.format("%.4f", result.zs_ohm)} Ω", color = ThemeTextColor, fontSize = 13.sp)
                        Text("Max Zs Allowed: ${String.format("%.4f", result.zsMax_ohm)} Ω", color = ThemeTextColor, fontSize = 13.sp)
                    }
                    Text("Loop Check: ${result.loopCheck}", color = statusColor(result.loopCheck), fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    if (result.messages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        result.messages.forEach { msg ->
                            Text(msg, color = if (result.overallCompliant) SleekPass else SleekWarning, fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun FullProtectionPanel(state: UiState.Success) {
    val results = state.protectionResults.values.toList()
    
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Full Protection Verification", color = ThemeTextColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("IEC 60364-4-43 / IEC 60947-2 Overload (Ib ≤ In ≤ Iz), Short-Circuit (Icu ≥ Isc) & Selectivity", color = SlateTextSecondary, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(16.dp))

        if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No circuits available to evaluate protection.", color = SlateTextSecondary)
            }
        } else {
            results.forEach { res ->
                val statusColor = statusColor(res.finalStatus)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(res.circuitName, color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(res.finalStatus, color = statusColor, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Overload (Ib ≤ In ≤ Iz): ${res.overloadStatus}", color = statusColor(res.overloadStatus), fontSize = 11.sp)
                            Text("Icu ≥ Isc: ${res.shortCircuitStatus}", color = statusColor(res.shortCircuitStatus), fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ib = ${String.format("%.2f", res.Ib_A)}A | In = ${res.In_A.toInt()}A | Iz = ${String.format("%.2f", res.Iz_A)}A", color = SlateTextSecondary, fontSize = 10.sp)
                            Text("Isc = ${String.format("%.2f", res.iscKA)}kA | Icu = ${String.format("%.1f", res.icuKA)}kA", color = SlateTextSecondary, fontSize = 10.sp)
                        }
                        if (res.messages.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            res.messages.forEach { msg ->
                                Text(msg, color = if (res.finalStatus == "FAIL") SleekFail else SleekWarning, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CoordinationPanel(state: UiState.Success) {
    val results = state.coordinationResults
    
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Breaker Coordination / Selectivity", color = ThemeTextColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Verified based on IEC 60947 current-ratio rules (1.6x overload, 2.0x magnetic)", color = SlateTextSecondary, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(16.dp))

        if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hierarchical parent-child board relationships found to coordinate.", color = SlateTextSecondary)
            }
        } else {
            results.forEach { res ->
                val upstreamResult = state.sizingResults.values.find { res.upstreamName.contains(it.deviceType) } // Simplified finding for UI
                
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, if (res.status.contains("OK")) SleekPass.copy(alpha = 0.3f) else SleekWarning.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Downstream: ${res.downstreamName}", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Upstream: ${res.upstreamName}", color = SlateTextSecondary, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Ratio: ${res.ratio}", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(res.status, color = if (res.status.contains("OK")) SleekPass else SleekWarning, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(res.reason, color = ThemeTextColor, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun EarthFaultPanel(state: UiState.Success) {
    val results = state.adsResults.values.toList()
    
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Earth Fault & ADS Verification", color = ThemeTextColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Automatic Disconnection of Supply (ADS) per IEC 60364-4-41 (0.4s limit)", color = SlateTextSecondary, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(16.dp))

        results.forEach { res ->
            val statusCol = statusColor(res.status)
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                border = BorderStroke(1.dp, statusCol.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(res.circuitName, color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(res.status, color = statusCol, fontWeight = FontWeight.Black)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Calc Loop Zs", color = SlateTextSecondary, fontSize = 10.sp)
                            Text("${String.format("%.2f", res.calculatedZs)} Ω", color = ThemeTextColor, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Max Zs Allowed", color = SlateTextSecondary, fontSize = 10.sp)
                            Text("${String.format("%.2f", res.maxZsAllowed)} Ω", color = ThemeTextColor, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Disconnect Time", color = SlateTextSecondary, fontSize = 10.sp)
                            Text(res.disconnectionTime, color = ThemeTextColor)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (res.calculatedZs / res.maxZsAllowed).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = statusCol,
                        trackColor = DarkBackground
                    )
                    Text(
                        "Safety Margin: ${String.format("%.1f%%", (1.0 - (res.calculatedZs / res.maxZsAllowed)) * 100)}",
                        color = SlateTextSecondary, fontSize = 10.sp, modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

fun calculateCircuitMarginPct(res: ShortCircuitResult): Double {
    if (res.iscKA <= 0.0) return res.marginPct
    val cableMargin = if (res.iwKA > 0.0) ((res.iwKA - res.iscKA) / res.iscKA) * 100.0 else Double.POSITIVE_INFINITY
    val deviceMargin = if (res.deviceKA > 0.0) ((res.deviceKA - res.iscKA) / res.iscKA) * 100.0 else Double.POSITIVE_INFINITY
    val margin = minOf(cableMargin, deviceMargin)
    return if (margin.isFinite()) margin else res.marginPct
}

@Composable
fun getStatusPillColors(status: String): Triple<Color, Color, Color> {
    val isDark = LocalThemeIsDark.current
    return when (status.uppercase().trim()) {
        "PASS" -> if (isDark) {
            Triple(Color(0xFF123D26), Color(0xFFB6F0CE), Color(0xFF5BD98A))
        } else {
            Triple(Color(0xFFD1F2E2), Color(0xFF14532D), Color(0xFF16A34A))
        }
        "WARNING", "WARN" -> if (isDark) {
            Triple(Color(0xFF3E2E08), Color(0xFFFBE1A6), Color(0xFFF0B542))
        } else {
            Triple(Color(0xFFFEF3C7), Color(0xFF92400E), Color(0xFFD97706))
        }
        "FAIL" -> if (isDark) {
            Triple(Color(0xFF4A1513), Color(0xFFFFD5D2), Color(0xFFFF6A66))
        } else {
            Triple(Color(0xFFFEE2E2), Color(0xFF991B1B), Color(0xFFDC2626))
        }
        else -> if (isDark) {
            Triple(Color(0xFF1B2127), Color(0xFFA9B2BB), Color(0xFF94A3B8))
        } else {
            Triple(Color(0xFFF1F5F9), Color(0xFF475569), Color(0xFF64748B))
        }
    }
}

@Composable
fun ShortCircuitPanel(state: UiState.Success) {
    val results = state.shortCircuitResults.values.toList()
    
    val total = results.size
    val passes = results.count { it.status == "PASS" }
    val warnings = results.count { it.status == "WARNING" }
    val fails = results.count { it.status == "FAIL" }
    
    val passRate = if (total > 0) ((passes.toDouble() / total) * 100).roundToInt() else 0
    // DATA MISSING rows carry iscKA = 0.0 by design; they must not be counted as a real
    // fault level when summarizing the project.
    val highestIsc = results.filter { it.status != "DATA MISSING" && it.iscKA > 0.0 }
        .maxOfOrNull { it.iscKA } ?: 0.0
    val validMargins = results.filter { it.status != "DATA MISSING" }.map { calculateCircuitMarginPct(it) }
    val lowestMargin = if (validMargins.isNotEmpty()) validMargins.minOrNull() ?: 0.0 else 0.0

    val isDark = LocalThemeIsDark.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Column(modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(
                text = "Short-Circuit Verification",
                color = MaterialTheme.colorScheme.onSurface,
                style = DsType.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "IEC 60909 · IEC 60364-4-43 · IEC 60947-2",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = DsType.bodySmall,
                maxLines = 1
            )
        }

        // Summary Stat Row (TOTAL / PASS / WARN / FAIL)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatTile(
                label = "TOTAL",
                value = "$total",
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "PASS",
                value = "$passes",
                containerColor = if (isDark) Color(0xFF123D26) else Color(0xFFD1F2E2),
                contentColor = if (isDark) Color(0xFFB6F0CE) else Color(0xFF14532D),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "WARN",
                value = "$warnings",
                containerColor = if (isDark) Color(0xFF3E2E08) else Color(0xFFFEF3C7),
                contentColor = if (isDark) Color(0xFFFBE1A6) else Color(0xFF92400E),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "FAIL",
                value = "$fails",
                containerColor = if (isDark) Color(0xFF4A1513) else Color(0xFFFEE2E2),
                contentColor = if (isDark) Color(0xFFFFD5D2) else Color(0xFF991B1B),
                modifier = Modifier.weight(1f)
            )
        }

        // KPI Row (Pass Rate, Highest Isc, Lowest Margin)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiTile(
                label = "Pass Rate",
                value = "$passRate%",
                modifier = Modifier.weight(1f)
            )
            KpiTile(
                label = "Highest Isc",
                value = "${String.format(java.util.Locale.US, "%.2f", highestIsc)} kA",
                modifier = Modifier.weight(1f)
            )
            KpiTile(
                label = "Lowest Margin",
                value = "${String.format(java.util.Locale.US, "%.1f", lowestMargin)}%",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Circuit Cards — elevated, delineated cards with status stripe and 16dp gap
        if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No short-circuit calculations available.",
                    style = DsType.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                results.forEach { res ->
                    val circuitName = state.circuits.find { it.id == res.circuitId }?.circuitName ?: "Circuit ${res.circuitId}"
                    ShortCircuitCard(res = res, circuitName = circuitName, isDark = isDark)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ShortCircuitCard(
    res: com.example.engine.ShortCircuitResult,
    circuitName: String,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val (statusContainer, statusContent, statusDot) = getStatusPillColors(res.status)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(18.dp), clip = false),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // 4dp Left Status Stripe (PASS green / WARN amber / FAIL red)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(statusDot)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top Row: Circuit Name + Status Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = circuitName,
                        style = DsType.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Status Pill with dot
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(statusContainer)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(statusDot)
                            )
                            Text(
                                text = res.status,
                                style = DsType.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = statusContent
                            )
                        }
                    }
                }

                // Meta Tags Row (Source + Cable)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Source tag
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = res.sourceName,
                                style = DsType.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Cable tag
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GridOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = res.cableMm2,
                                style = DsType.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 3-Up Metrics Row (Isc, Iw, Device Icu)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricItemTile(
                        label = "Isc",
                        value = String.format(java.util.Locale.US, "%.2f", res.iscKA),
                        unit = "kA",
                        modifier = Modifier.weight(1f)
                    )
                    MetricItemTile(
                        label = "Iw (withstand)",
                        value = String.format(java.util.Locale.US, "%.2f", res.iwKA),
                        unit = "kA",
                        modifier = Modifier.weight(1f)
                    )
                    MetricItemTile(
                        label = "Device Icu",
                        value = String.format(java.util.Locale.US, "%.1f", res.deviceKA),
                        unit = "kA",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Margin Line
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Margin",
                        style = DsType.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // widthIn(min) instead of fixed width: at fontScale
                        // 1.3+ the label grows instead of clipping, and the
                        // weighted margin bar absorbs the difference. Keeps the
                        // 48dp aligned label column at fontScale 1.0
                        // (pixel-identical rendering there).
                        modifier = Modifier.widthIn(min = 48.dp)
                    )

                    val marginPct = calculateCircuitMarginPct(res)
                    val isFail = res.status == "FAIL" || marginPct < 0.0
                    val isWarnOrLow = res.status == "WARNING" || res.status == "WARN" || (marginPct in 0.0..10.0)

                    val barColors = when {
                        isFail -> if (isDark) {
                            listOf(Color(0xFFF0554C), Color(0xFFFF6A66))
                        } else {
                            listOf(Color(0xFFEF4444), Color(0xFFDC2626))
                        }
                        isWarnOrLow -> if (isDark) {
                            listOf(Color(0xFFD49B28), Color(0xFFF0B542))
                        } else {
                            listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                        }
                        else -> if (isDark) {
                            listOf(Color(0xFF3FBF74), Color(0xFF5BD98A))
                        } else {
                            listOf(Color(0xFF22C55E), Color(0xFF16A34A))
                        }
                    }

                    val marginTextColor = when {
                        isFail -> if (isDark) Color(0xFFFF6A66) else Color(0xFFDC2626)
                        isWarnOrLow -> if (isDark) Color(0xFFF0B542) else Color(0xFFD97706)
                        else -> if (isDark) Color(0xFF5BD98A) else Color(0xFF16A34A)
                    }

                    // Margin Bar
                    val progressFraction = (marginPct / 100.0).coerceIn(0.0, 1.0).toFloat()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = if (marginPct > 0.0) progressFraction.coerceAtLeast(0.05f) else 0f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    brush = Brush.horizontalGradient(colors = barColors)
                                )
                        )
                    }

                    Text(
                        text = String.format(java.util.Locale.US, "%.1f%%", marginPct),
                        style = DsType.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = marginTextColor,
                        textAlign = TextAlign.End,
                        // widthIn(min) instead of fixed width: at fontScale 1.3+
                        // a large percentage (e.g. 125.0%) grows instead of
                        // clipping its leading digits (right-aligned clip would
                        // make an ambiguous value). Bar absorbs the difference.
                        modifier = Modifier.widthIn(min = 68.dp)
                    )
                }

                // Warning / Note Banner if present
                if (res.note.isNotEmpty()) {
                    val noteBg = if (isDark) Color(0xFF3E2E08) else Color(0xFFFEF3C7)
                    val noteFg = if (isDark) Color(0xFFFBE1A6) else Color(0xFF92400E)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(noteBg)
                            .padding(horizontal = 11.dp, vertical = 9.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = noteFg,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = res.note,
                                style = DsType.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 14.sp),
                                color = noteFg
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = DsType.labelSmall.copy(fontSize = 10.5.sp, letterSpacing = 0.4.sp),
                fontWeight = FontWeight.SemiBold,
                color = contentColor.copy(alpha = 0.85f),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = DsType.titleLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun KpiTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = label,
                style = DsType.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                style = DsType.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MetricItemTile(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Column {
            Text(
                text = label,
                style = DsType.labelSmall.copy(fontSize = 10.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = DsType.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Width allocation (same contract as DsMetricTile, Phase 2):
                    // the unit is measured first and can never be pushed out of
                    // the tile; the value ellipsizes only when it alone cannot
                    // fit. Pixel-identical when value + unit fit on one line.
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = unit,
                    style = DsType.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, modifier: Modifier = Modifier, color: Color = DarkCardBg) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, color = SlateTextSecondary, fontSize = 10.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ElectrodeCandidateCard(
    candidate: com.example.engine.ElectrodeCandidate,
    isRecommended: Boolean,
    onApply: (com.example.engine.ElectrodeCandidate) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isRecommended) SleekPass.copy(alpha = 0.08f) else DarkCardBg),
        border = BorderStroke(1.dp, if (isRecommended) SleekPass.copy(alpha = 0.4f) else BorderColor),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(candidate.title, color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "Calculated R: ${String.format("%.2f", candidate.calculatedResistance_ohm)} Ω | " +
                    "Single Rod R1: ${String.format("%.2f", candidate.singleRodResistance_ohm)} Ω | " +
                    "Efficiency η: ${(candidate.groupEfficiency * 100).toInt()}%",
                    color = SlateTextSecondary,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onApply(candidate) },
                colors = ButtonDefaults.buttonColors(containerColor = if (isRecommended) SleekPass else SleekPrimary),
                modifier = Modifier.height(36.dp)
            ) {
                Text("Apply Layout", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
