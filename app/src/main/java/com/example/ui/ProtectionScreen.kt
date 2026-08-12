package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import com.example.ui.components.statusColor
import com.example.ui.components.statusSymbol
import com.example.ui.components.StatusBadge
import kotlin.math.roundToInt

@Composable
fun ProtectionScreen(
    state: UiState.Success,
    onSaveEarthing: ((com.example.data.EarthingEntity) -> Unit)? = null
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("Full Protection", "Short Circuit", "Coordination", "Earth Fault Loop", "Earthing")

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkCardBg,
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
                0 -> FullProtectionPanel(state)
                1 -> ShortCircuitPanel(state)
                2 -> CoordinationPanel(state)
                3 -> EarthFaultPanel(state)
                4 -> EarthingPanel(state, onSaveEarthing)
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
                    Text("Apply Layout", color = Color.White)
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

@Composable
fun ShortCircuitPanel(state: UiState.Success) {
    val results = state.shortCircuitResults.values.toList()
    
    val total = results.size
    val passes = results.count { it.status == "PASS" }
    val warnings = results.count { it.status == "WARNING" }
    val fails = results.count { it.status == "FAIL" }
    val missing = results.count { it.status == "DATA MISSING" }
    
    val passRate = if (total > 0) ((passes.toDouble() / total) * 100).roundToInt() else 0
    val highestIsc = results.maxOfOrNull { it.iscKA } ?: 0.0
    val validMargins = results.filter { it.status != "DATA MISSING" }.map { it.marginPct }
    val lowestMargin = if (validMargins.isNotEmpty()) validMargins.minOrNull() ?: 0.0 else 0.0

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        
        Text("Short-Circuit Verification", color = ThemeTextColor, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text("Based on IEC 60909, IEC 60364-4-43, and IEC 60364-5-54", color = SlateTextSecondary, fontSize = 11.sp, maxLines = 1)
        Spacer(modifier = Modifier.height(12.dp))

        // Dashboard
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetricCard("Total", "$total", Modifier.weight(1f))
            MetricCard("PASS", "$passes", Modifier.weight(1f), SleekPass)
            MetricCard("WARN", "$warnings", Modifier.weight(1f), SleekWarning)
            MetricCard("FAIL", "$fails", Modifier.weight(1f), SleekFail)
            MetricCard("MISSING", "$missing", Modifier.weight(1f), SlateTextSecondary)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetricCard("Pass Rate", "$passRate%", Modifier.weight(1f))
            MetricCard("Highest Isc", "${String.format("%.2f", highestIsc)} kA", Modifier.weight(1f))
            MetricCard("Lowest Margin", "${String.format("%.1f", lowestMargin)}%", Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Horizontally Scrollable Protection Table
        val horizontalScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalScrollState)
        ) {
            // Table Header
            Row(
                modifier = Modifier
                    .background(DarkBackground)
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Circuit", modifier = Modifier.width(100.dp), color = SlateTextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                Text("Source", modifier = Modifier.width(80.dp), color = SlateTextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                Text("Cable", modifier = Modifier.width(90.dp), color = SlateTextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                Text("Isc (kA)", modifier = Modifier.width(75.dp), color = SlateTextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                Text("Iw (kA)", modifier = Modifier.width(75.dp), color = SlateTextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                Text("Device (kA)", modifier = Modifier.width(85.dp), color = SlateTextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                Text("Margin %", modifier = Modifier.width(80.dp), color = SlateTextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                Text("Status", modifier = Modifier.width(85.dp), color = SlateTextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }

            // Table Rows
            results.forEach { res ->
                val circuitName = state.circuits.find { it.id == res.circuitId }?.circuitName ?: "Unknown"
                val color = when (res.status) {
                    "PASS" -> SleekPass
                    "WARNING" -> SleekWarning
                    "FAIL" -> SleekFail
                    else -> SlateTextSecondary
                }
                
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .background(DarkCardBg)
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(circuitName, modifier = Modifier.width(100.dp), color = ThemeTextColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(res.sourceName, modifier = Modifier.width(80.dp), color = ThemeTextColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(res.cableMm2, modifier = Modifier.width(90.dp), color = ThemeTextColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(String.format("%.2f", res.iscKA), modifier = Modifier.width(75.dp), color = ThemeTextColor, fontSize = 12.sp, maxLines = 1)
                        Text(String.format("%.2f", res.iwKA), modifier = Modifier.width(75.dp), color = ThemeTextColor, fontSize = 12.sp, maxLines = 1)
                        Text(String.format("%.1f", res.deviceKA), modifier = Modifier.width(85.dp), color = ThemeTextColor, fontSize = 12.sp, maxLines = 1)
                        Text(String.format("%.1f%%", res.marginPct), modifier = Modifier.width(80.dp), color = ThemeTextColor, fontSize = 12.sp, maxLines = 1)
                        Text(res.status, modifier = Modifier.width(85.dp), color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                    }
                    if (res.note.isNotEmpty()) {
                        Row(modifier = Modifier.background(DarkCardBg).padding(start = 8.dp, bottom = 8.dp, end = 8.dp)) {
                            Text("Note: ${res.note}", color = SlateTextSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
                HorizontalDivider(color = SlateTextSecondary)
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
                Text("Apply Layout", fontSize = 11.sp, color = Color.White)
            }
        }
    }
}
