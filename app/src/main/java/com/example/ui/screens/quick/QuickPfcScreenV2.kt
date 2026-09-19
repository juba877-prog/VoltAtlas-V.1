package com.example.ui.screens.quick

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.VoltAtlasTopBar
import com.example.ui.components.quick.*
import com.example.ui.theme.*
import kotlinx.collections.immutable.persistentListOf
import java.util.Locale

private val PFC_INPUT_MODE_OPTIONS = persistentListOf("Active (kW)", "Apparent (kVA)", "Current (A)")
private val PFC_PHASE_OPTIONS = persistentListOf("3P (400V)", "1P (230V)")
private val PFC_FREQ_OPTIONS = persistentListOf("50 Hz", "60 Hz")
private val PFC_CONN_OPTIONS = persistentListOf("Delta (Δ) Bank", "Star (Y) Bank")

/**
 * Immutable UI State for Power Factor Correction Screen.
 */
@Immutable
data class PfcScreenUiState(
    val inputState: PfcInputState,
    val calculationResult: PfcDetailedResult?,
    val inputModeIndex: Int = 0,
    val phaseIndex: Int = 0,
    val freqIndex: Int = 0,
    val connectionIndex: Int = 0,
    val voltageText: String = "400",
    val powerKwText: String = "100.0",
    val powerKvaText: String = "125.0",
    val currentAText: String = "180.0",
    val initialPfText: String = "0.80",
    val targetPfText: String = "0.95"
)

/**
 * Seam 1: Screen Wiring Composable.
 * Owns app dependencies (Context, Toast, ClipboardManager), durable screen state,
 * and calculation engine execution.
 */
@Composable
fun QuickPfcScreenV2(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // ── 1. Reactive UI Input States ──
    var inputModeIndex by rememberSaveable { mutableIntStateOf(0) } // 0: kW, 1: kVA, 2: Amps
    var phaseIndex by rememberSaveable { mutableIntStateOf(0) } // 0: 3-Phase, 1: 1-Phase
    var voltageText by rememberSaveable { mutableStateOf("400") }
    var powerKwText by rememberSaveable { mutableStateOf("100.0") }
    var powerKvaText by rememberSaveable { mutableStateOf("125.0") }
    var currentAText by rememberSaveable { mutableStateOf("180.0") }
    var initialPfText by rememberSaveable { mutableStateOf("0.80") }
    var targetPfText by rememberSaveable { mutableStateOf("0.95") }
    var freqIndex by rememberSaveable { mutableIntStateOf(0) } // 0: 50 Hz, 1: 60 Hz
    var connectionIndex by rememberSaveable { mutableIntStateOf(0) } // 0: Delta, 1: Star

    val currentInputMode = when (inputModeIndex) {
        0 -> PfcInputMode.ACTIVE_POWER_KW
        1 -> PfcInputMode.APPARENT_POWER_KVA
        else -> PfcInputMode.CURRENT_AMPS
    }

    val currentPhase = if (phaseIndex == 0) PfcSystemPhase.THREE_PHASE else PfcSystemPhase.SINGLE_PHASE
    val currentFreq = if (freqIndex == 0) PfcFrequency.FREQ_50HZ else PfcFrequency.FREQ_60HZ
    val currentConn = if (connectionIndex == 0) PfcCapacitorConnection.DELTA else PfcCapacitorConnection.STAR

    // ── 2. Build Typed Input State ──
    val inputState = remember(
        inputModeIndex, phaseIndex, voltageText, powerKwText, powerKvaText, currentAText,
        initialPfText, targetPfText, freqIndex, connectionIndex
    ) {
        PfcInputState(
            inputMode = currentInputMode,
            phase = currentPhase,
            voltageV = voltageText.toDoubleOrNull() ?: 400.0,
            powerKwText = powerKwText,
            powerKvaText = powerKvaText,
            currentAText = currentAText,
            initialPfText = initialPfText,
            targetPfText = targetPfText,
            frequency = currentFreq,
            connection = currentConn
        )
    }

    // ── 3. Live Pure Calculation ──
    val calculationResult = remember(inputState) {
        PfcCalculationEngine.calculate(inputState)
    }

    val uiState = PfcScreenUiState(
        inputState = inputState,
        calculationResult = calculationResult,
        inputModeIndex = inputModeIndex,
        phaseIndex = phaseIndex,
        freqIndex = freqIndex,
        connectionIndex = connectionIndex,
        voltageText = voltageText,
        powerKwText = powerKwText,
        powerKvaText = powerKvaText,
        currentAText = currentAText,
        initialPfText = initialPfText,
        targetPfText = targetPfText
    )

    // Copy Summary Action
    val copySummaryAction: () -> Unit = {
        val res = calculationResult
        if (res != null) {
            val summary = """
                VoltAtlas Power Factor Correction V2 Summary:
                • Required Compensation: ${String.format(Locale.US, "%.2f", res.requiredQcKvar)} kVAR
                • PF Correction: ${String.format(Locale.US, "%.2f", res.initialPf)} → ${String.format(Locale.US, "%.2f", res.targetPf)}
                • System: ${if (res.isThreePhase) "3-Phase" else "1-Phase"} ${res.voltageV.toInt()} V @ ${res.frequencyHz.toInt()} Hz
                • Powers: P = ${String.format(Locale.US, "%.2f", res.activePowerKw)} kW | S₁ = ${String.format(Locale.US, "%.2f", res.initialApparentKva)} kVA → S₂ = ${String.format(Locale.US, "%.2f", res.targetApparentKva)} kVA (ΔS = ${String.format(Locale.US, "%.2f", res.releasedCapacityKva)} kVA released)
                • Reactive: Q₁ = ${String.format(Locale.US, "%.2f", res.initialReactiveKvar)} kVAR → Q₂ = ${String.format(Locale.US, "%.2f", res.targetReactiveKvar)} kVAR
                • Current Relief: I₁ = ${String.format(Locale.US, "%.2f", res.initialCurrentA)} A → I₂ = ${String.format(Locale.US, "%.2f", res.targetCurrentA)} A (ΔI = ${String.format(Locale.US, "%.2f", res.currentReductionA)} A, ${String.format(Locale.US, "%.1f", res.currentReductionPct)}%)
                • Bank Sizing: ${String.format(Locale.US, "%.1f", res.capacitanceMicroFarad)} µF per ${if (res.connection == PfcCapacitorConnection.DELTA) "branch (Delta)" else "phase"}
                • Citation: ${res.provenanceCitation}
            """.trimIndent()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("PFC Calculation", summary))
            Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Enter valid inputs to copy calculation", Toast.LENGTH_SHORT).show()
        }
    }

    QuickPfcContent(
        uiState = uiState,
        onSelectInputMode = { inputModeIndex = it },
        onSelectPhase = {
            phaseIndex = it
            voltageText = if (it == 0) "400" else "230"
        },
        onSelectFreq = { freqIndex = it },
        onSelectConnection = { connectionIndex = it },
        onVoltageChange = { voltageText = it },
        onPowerKwChange = { powerKwText = it },
        onPowerKvaChange = { powerKvaText = it },
        onCurrentAChange = { currentAText = it },
        onInitialPfChange = { initialPfText = it },
        onTargetPfChange = { targetPfText = it },
        onBack = onBack,
        onCopySummary = copySummaryAction
    )
}

/**
 * Seam 3: Pure Previewable Content Composable for Power Factor Correction.
 *
 * Free of platform services (Context, Clipboard, Toast), receiving immutable
 * UI state and plain lambda callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPfcContent(
    uiState: PfcScreenUiState,
    onSelectInputMode: (Int) -> Unit,
    onSelectPhase: (Int) -> Unit,
    onSelectFreq: (Int) -> Unit,
    onSelectConnection: (Int) -> Unit,
    onVoltageChange: (String) -> Unit,
    onPowerKwChange: (String) -> Unit,
    onPowerKvaChange: (String) -> Unit,
    onCurrentAChange: (String) -> Unit,
    onInitialPfChange: (String) -> Unit,
    onTargetPfChange: (String) -> Unit,
    onBack: () -> Unit,
    onCopySummary: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            VoltAtlasTopBar(
                title = "Power Factor Correction",
                subtitle = "Target PF, Reactive Relief & Capacitor Sizing",
                onBack = onBack,
                backDescription = "Back to Hub",
                backButtonTestTag = "pfc_v2_back_btn",
                actions = {
                    IconButton(onClick = onCopySummary, modifier = Modifier.testTag("copy_pfc_summary_btn")) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Summary",
                            tint = SleekPrimary
                        )
                    }
                }
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth >= 700.dp

            if (isWideScreen) {
                // ── Two-Pane Tablet / Landscape Layout ──
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Pane: Inputs (50%)
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        item {
                            PfcInputsSection(
                                inputModeIndex = uiState.inputModeIndex,
                                onSelectInputMode = onSelectInputMode,
                                phaseIndex = uiState.phaseIndex,
                                onSelectPhase = onSelectPhase,
                                voltageText = uiState.voltageText,
                                onVoltageChange = onVoltageChange,
                                powerKwText = uiState.powerKwText,
                                onPowerKwChange = onPowerKwChange,
                                powerKvaText = uiState.powerKvaText,
                                onPowerKvaChange = onPowerKvaChange,
                                currentAText = uiState.currentAText,
                                onCurrentAChange = onCurrentAChange,
                                initialPfText = uiState.initialPfText,
                                onInitialPfChange = onInitialPfChange,
                                targetPfText = uiState.targetPfText,
                                onTargetPfChange = onTargetPfChange,
                                freqIndex = uiState.freqIndex,
                                onSelectFreq = onSelectFreq,
                                connectionIndex = uiState.connectionIndex,
                                onSelectConnection = onSelectConnection
                            )
                        }
                    }

                    // Right Pane: Results & Checks & Breakdown (50%)
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        item {
                            PfcResultsSection(result = uiState.calculationResult, inputState = uiState.inputState)
                        }
                    }
                }
            } else {
                // ── Single-Column Phone Layout ──
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
                ) {
                    item {
                        PfcInputsSection(
                            inputModeIndex = uiState.inputModeIndex,
                            onSelectInputMode = onSelectInputMode,
                            phaseIndex = uiState.phaseIndex,
                            onSelectPhase = onSelectPhase,
                            voltageText = uiState.voltageText,
                            onVoltageChange = onVoltageChange,
                            powerKwText = uiState.powerKwText,
                            onPowerKwChange = onPowerKwChange,
                            powerKvaText = uiState.powerKvaText,
                            onPowerKvaChange = onPowerKvaChange,
                            currentAText = uiState.currentAText,
                            onCurrentAChange = onCurrentAChange,
                            initialPfText = uiState.initialPfText,
                            onInitialPfChange = onInitialPfChange,
                            targetPfText = uiState.targetPfText,
                            onTargetPfChange = onTargetPfChange,
                            freqIndex = uiState.freqIndex,
                            onSelectFreq = onSelectFreq,
                            connectionIndex = uiState.connectionIndex,
                            onSelectConnection = onSelectConnection
                        )
                    }

                    item {
                        PfcResultsSection(result = uiState.calculationResult, inputState = uiState.inputState)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// INPUT SECTION COMPOSABLES
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PfcInputsSection(
    inputModeIndex: Int,
    onSelectInputMode: (Int) -> Unit,
    phaseIndex: Int,
    onSelectPhase: (Int) -> Unit,
    voltageText: String,
    onVoltageChange: (String) -> Unit,
    powerKwText: String,
    onPowerKwChange: (String) -> Unit,
    powerKvaText: String,
    onPowerKvaChange: (String) -> Unit,
    currentAText: String,
    onCurrentAChange: (String) -> Unit,
    initialPfText: String,
    onInitialPfChange: (String) -> Unit,
    targetPfText: String,
    onTargetPfChange: (String) -> Unit,
    freqIndex: Int,
    onSelectFreq: (Int) -> Unit,
    connectionIndex: Int,
    onSelectConnection: (Int) -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Operating Mode & System Topology
        QuickCard {
            QuickSectionTitle(
                title = "Operating Mode & Topology",
                accentColor = SleekPrimary,
                badgeText = when (inputModeIndex) {
                    0 -> "Active Power (kW)"
                    1 -> "Apparent Power (kVA)"
                    else -> "Measured Current (A)"
                }
            )

            Text("Primary Load Quantity", color = SlateTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            QuickSegmentedControl(
                options = PFC_INPUT_MODE_OPTIONS,
                selectedIndex = inputModeIndex,
                onSelectIndex = onSelectInputMode,
                modifier = Modifier.testTag("pfc_v2_input_mode_tabs")
            )

            Text("System Phase & Voltage", color = SlateTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.weight(1.2f)) {
                    QuickSegmentedControl(
                        options = PFC_PHASE_OPTIONS,
                        selectedIndex = phaseIndex,
                        onSelectIndex = onSelectPhase,
                        modifier = Modifier.testTag("pfc_v2_phase_tabs")
                    )
                }
                OutlinedTextField(
                    value = voltageText,
                    onValueChange = onVoltageChange,
                    label = { Text("Line Voltage (V)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.weight(0.8f).testTag("pfc_v2_voltage_input")
                )
            }

            HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))

            // Dynamic Primary Load Field
            when (inputModeIndex) {
                0 -> {
                    OutlinedTextField(
                        value = powerKwText,
                        onValueChange = onPowerKwChange,
                        label = { Text("Active Power P (kW)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth().testTag("pfc_v2_kw_input")
                    )
                }
                1 -> {
                    OutlinedTextField(
                        value = powerKvaText,
                        onValueChange = onPowerKvaChange,
                        label = { Text("Apparent Power S₁ (kVA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth().testTag("pfc_v2_kva_input")
                    )
                }
                2 -> {
                    OutlinedTextField(
                        value = currentAText,
                        onValueChange = onCurrentAChange,
                        label = { Text("Measured Line Current I₁ (Amps)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth().testTag("pfc_v2_amps_input")
                    )
                }
            }
        }

        // 2. Power Factor Settings
        QuickCard {
            QuickSectionTitle(
                title = "Power Factor & Target",
                accentColor = Color(0xFFF59E0B),
                badgeText = "cosφ₁ → cosφ₂"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = initialPfText,
                    onValueChange = onInitialPfChange,
                    label = { Text("Initial PF (cosφ₁)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Right) }),
                    modifier = Modifier.weight(1f).testTag("pfc_v2_initial_pf_input")
                )

                OutlinedTextField(
                    value = targetPfText,
                    onValueChange = onTargetPfChange,
                    label = { Text("Target PF (cosφ₂)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.weight(1f).testTag("pfc_v2_target_pf_input")
                )
            }
        }

        // 3. Capacitor Bank Specifications
        QuickCard {
            QuickSectionTitle(
                title = "Capacitor Bank Specifications",
                accentColor = Color(0xFF10B981),
                badgeText = if (phaseIndex == 0) (if (connectionIndex == 0) "Delta (Δ)" else "Star (Y)") else "Single-Phase"
            )

            Text("System Frequency", color = SlateTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            QuickSegmentedControl(
                options = PFC_FREQ_OPTIONS,
                selectedIndex = freqIndex,
                onSelectIndex = onSelectFreq,
                modifier = Modifier.testTag("pfc_v2_freq_tabs")
            )

            if (phaseIndex == 0) {
                Text("Capacitor Bank Connection", color = SlateTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                QuickSegmentedControl(
                    options = PFC_CONN_OPTIONS,
                    selectedIndex = connectionIndex,
                    onSelectIndex = onSelectConnection,
                    modifier = Modifier.testTag("pfc_v2_conn_tabs")
                )
            } else {
                Surface(
                    color = SurfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Single-phase configuration automatically applied.",
                            color = SlateTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RESULTS SECTION COMPOSABLES
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PfcResultsSection(
    result: PfcDetailedResult?,
    inputState: PfcInputState
) {
    if (result == null) {
        val c1 = inputState.initialPfText.toDoubleOrNull()
        val c2 = inputState.targetPfText.toDoubleOrNull()
        val isInvalidPf = c1 != null && c2 != null && c2 <= c1

        QuickCard(
            borderColor = if (isInvalidPf) SleekWarning else BorderColor
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isInvalidPf) Icons.Default.Warning else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (isInvalidPf) SleekWarning else SleekPrimary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isInvalidPf) {
                        "Target Power Factor (cosφ₂) must be strictly greater than Initial Power Factor (cosφ₁)."
                    } else {
                        "Enter valid positive electrical parameters (P/S/I > 0, 0 < Initial PF < Target PF ≤ 1.0, V > 0) to view live capacitor compensation results."
                    },
                    color = if (isInvalidPf) ThemeTextColor else SlateTextSecondary,
                    fontSize = 13.sp
                )
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Primary Hero Result Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, SleekPrimary),
            modifier = Modifier.fillMaxWidth().testTag("hero_metric_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "REQUIRED CAPACITOR BANK", color = SlateTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Surface(
                        color = SleekPass.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, SleekPass)
                    ) {
                        Text(
                            text = "${result.connection.label} @ ${result.frequencyHz.toInt()} Hz",
                            color = SleekPass,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(Locale.US, "%.2f", result.requiredQcKvar),
                        color = ThemeTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "kVAR",
                        color = SleekPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    text = "Power Factor: ${String.format(Locale.US, "%.2f", result.initialPf)} → ${String.format(Locale.US, "%.2f", result.targetPf)} (${if (result.isThreePhase) "3-Phase" else "1-Phase"} ${result.voltageV.toInt()} V)",
                    color = SlateTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // 2. Power Triangle & System Power Summary Card
        QuickCard {
            QuickSectionTitle(
                title = "System Power Summary",
                accentColor = SleekPrimary,
                badgeText = "P / S / Q"
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickMetricHero(
                    title = "Active Power (P)",
                    value = "${String.format(Locale.US, "%.2f", result.activePowerKw)} kW",
                    subtitle = "Constant Active Load",
                    modifier = Modifier.weight(1f),
                    accentColor = SleekPrimary
                )
                QuickMetricHero(
                    title = "Required Compensation (Qc)",
                    value = "${String.format(Locale.US, "%.2f", result.requiredQcKvar)} kVAR",
                    subtitle = "ΔQ = ${String.format(Locale.US, "%.2f", result.reactiveReductionKvar)} kVAR",
                    modifier = Modifier.weight(1f),
                    accentColor = SleekPass
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickMetricHero(
                    title = "Initial Apparent (S₁)",
                    value = "${String.format(Locale.US, "%.2f", result.initialApparentKva)} kVA",
                    subtitle = "PF₁ = ${String.format(Locale.US, "%.2f", result.initialPf)}",
                    modifier = Modifier.weight(1f),
                    accentColor = SlateTextSecondary
                )
                QuickMetricHero(
                    title = "Target Apparent (S₂)",
                    value = "${String.format(Locale.US, "%.2f", result.targetApparentKva)} kVA",
                    subtitle = "PF₂ = ${String.format(Locale.US, "%.2f", result.targetPf)}",
                    modifier = Modifier.weight(1f),
                    accentColor = Color(0xFF10B981)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickMetricHero(
                    title = "Initial Reactive (Q₁)",
                    value = "${String.format(Locale.US, "%.2f", result.initialReactiveKvar)} kVAR",
                    subtitle = "tanφ₁ = ${String.format(Locale.US, "%.4f", result.initialTanPhi)}",
                    modifier = Modifier.weight(1f),
                    accentColor = SlateTextSecondary
                )
                QuickMetricHero(
                    title = "Target Reactive (Q₂)",
                    value = "${String.format(Locale.US, "%.2f", result.targetReactiveKvar)} kVAR",
                    subtitle = "tanφ₂ = ${String.format(Locale.US, "%.4f", result.targetTanPhi)}",
                    modifier = Modifier.weight(1f),
                    accentColor = Color(0xFFF59E0B)
                )
            }

            // Released Transformer / System Capacity
            Surface(
                color = SurfaceElevated,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Released Transformer Capacity (ΔS):", color = SlateTextSecondary, fontSize = 12.sp)
                    Text(
                        text = "${String.format(Locale.US, "%.2f", result.releasedCapacityKva)} kVA",
                        color = SleekPass,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3. Line Current Impact Card
        QuickCard {
            QuickSectionTitle(
                title = "Line Current Relief",
                accentColor = Color(0xFF10B981),
                badgeText = "-${String.format(Locale.US, "%.1f", result.currentReductionPct)}% Relief"
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickMetricHero(
                    title = "Current Before (I₁)",
                    value = "${String.format(Locale.US, "%.2f", result.initialCurrentA)} A",
                    subtitle = "At PF₁ = ${String.format(Locale.US, "%.2f", result.initialPf)}",
                    modifier = Modifier.weight(1f),
                    accentColor = SlateTextSecondary
                )
                QuickMetricHero(
                    title = "Current After (I₂)",
                    value = "${String.format(Locale.US, "%.2f", result.targetCurrentA)} A",
                    subtitle = "At PF₂ = ${String.format(Locale.US, "%.2f", result.targetPf)}",
                    modifier = Modifier.weight(1f),
                    accentColor = SleekPass
                )
            }

            Surface(
                color = SurfaceElevated,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Line Current Reduction (ΔI):", color = SlateTextSecondary, fontSize = 12.sp)
                    Text(
                        text = "${String.format(Locale.US, "%.2f", result.currentReductionA)} A (${String.format(Locale.US, "%.1f", result.currentReductionPct)}%)",
                        color = SleekPass,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4. Capacitor Hardware Sizing Card
        QuickCard {
            QuickSectionTitle(
                title = "Capacitor Bank Sizing",
                accentColor = Color(0xFFF59E0B),
                badgeText = result.connection.label
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickMetricHero(
                    title = "Capacitance per ${if (result.connection == PfcCapacitorConnection.DELTA) "Branch (Delta)" else "Phase"}",
                    value = "${String.format(Locale.US, "%.1f", result.capacitanceMicroFarad)} µF",
                    subtitle = "Nominal rating @ ${result.voltageV.toInt()} V",
                    modifier = Modifier.weight(1f),
                    accentColor = Color(0xFFF59E0B)
                )
                QuickMetricHero(
                    title = "Bank Configuration",
                    value = "${result.connection.label}",
                    subtitle = "Frequency: ${result.frequencyHz.toInt()} Hz",
                    modifier = Modifier.weight(1f),
                    accentColor = SleekPrimary
                )
            }
        }

        // 5. Multi-Step Calculation Accordion
        PfcStepDetailsAccordion(steps = result.steps)

        // 6. Provenance & Standards Footer
        Surface(
            color = DarkBackground,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Data Provenance & Engineering Standards",
                        fontWeight = FontWeight.Bold,
                        color = ThemeTextColor,
                        fontSize = 12.sp
                    )
                }
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Calculation Core:", color = SlateTextSecondary, fontSize = 11.sp)
                    Text("ElectricalConverters (Pure Math)", color = ThemeTextColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Methodology:", color = SlateTextSecondary, fontSize = 11.sp)
                    Text("IEC 60831 / IEEE 141 Shunt Capacitor Sizing", color = SleekPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = result.provenanceCitation,
                    color = SlateTextSecondary.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun PfcStepDetailsAccordion(
    steps: List<PfcCalculationStep>
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Functions, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Step-by-Step Mathematical Substitutions",
                        fontWeight = FontWeight.SemiBold,
                        color = ThemeTextColor,
                        fontSize = 13.sp
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = SlateTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = com.example.ui.theme.VoltAtlasMotion.expandableEnter(),
                exit = com.example.ui.theme.VoltAtlasMotion.expandableExit()
            ) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    steps.forEach { step ->
                        HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))
                        Text(step.title, color = ThemeTextColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                        Text("Governing Formula:", color = SlateTextSecondary, fontSize = 11.sp)
                        Surface(
                            color = DarkBackground,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = step.formula,
                                color = SleekPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        Text("Numerical Substitution & Result:", color = SlateTextSecondary, fontSize = 11.sp)
                        Surface(
                            color = DarkBackground,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = step.substitution,
                                    color = ThemeTextColor,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "→ ${step.resultText}",
                                    color = SleekPass,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PREVIEWS
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Quick PFC Screen - Phone", showBackground = true)
@Composable
private fun PreviewQuickPfcContentPhone() {
    VoltAtlasTheme(darkTheme = true) {
        val dummyInput = PfcInputState(
            inputMode = PfcInputMode.ACTIVE_POWER_KW,
            phase = PfcSystemPhase.THREE_PHASE,
            voltageV = 400.0,
            powerKwText = "100.0",
            powerKvaText = "125.0",
            currentAText = "180.0",
            initialPfText = "0.80",
            targetPfText = "0.95",
            frequency = PfcFrequency.FREQ_50HZ,
            connection = PfcCapacitorConnection.DELTA
        )
        val dummyResult = PfcCalculationEngine.calculate(dummyInput)
        QuickPfcContent(
            uiState = PfcScreenUiState(
                inputState = dummyInput,
                calculationResult = dummyResult
            ),
            onSelectInputMode = {},
            onSelectPhase = {},
            onSelectFreq = {},
            onSelectConnection = {},
            onVoltageChange = {},
            onPowerKwChange = {},
            onPowerKvaChange = {},
            onCurrentAChange = {},
            onInitialPfChange = {},
            onTargetPfChange = {},
            onBack = {},
            onCopySummary = {}
        )
    }
}

