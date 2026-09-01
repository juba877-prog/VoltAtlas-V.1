package com.example.ui.screens.quick

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.quick.*
import com.example.ui.theme.*
import kotlinx.collections.immutable.persistentListOf
import java.util.Locale

private val LOAD_KNOWN_QUANTITY_OPTIONS = persistentListOf("Apparent S (kVA)", "Active P (kW)", "Current I (A)")
private val LOAD_PHASE_OPTIONS = persistentListOf("3P (400V)", "1P (230V)")

/**
 * Load Current & Power Conversion V2 Screen.
 *
 * Implements the Quick Engineering V2 reference architecture:
 * - kVA as a first-class engineering quantity.
 * - Dynamic mode selection: Known kVA, Known kW, or Known Line Current.
 * - Conditional Power Factor visibility (never requested when mathematically unnecessary).
 * - Live reactive calculation with zero Room / ViewModel / AI dependencies.
 * - Step-by-step mathematical substitution breakdown and engineering provenance disclosure.
 * - Responsive Single-Column (Phone) and Two-Pane (Tablet/Wide Screen) Layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLoadCurrentScreenV2(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current

    // ── 1. Input State Declarations ──
    var knownQuantityIndex by rememberSaveable { mutableIntStateOf(0) } // 0 = kVA, 1 = kW, 2 = Amps
    var phaseIndex by rememberSaveable { mutableIntStateOf(0) }         // 0 = 3-Phase, 1 = 1-Phase

    var voltageText by rememberSaveable { mutableStateOf("400") }
    var sKvaText by rememberSaveable { mutableStateOf("100.0") }
    var pKwText by rememberSaveable { mutableStateOf("80.0") }
    var ampsText by rememberSaveable { mutableStateOf("100.0") }
    var powerFactorText by rememberSaveable { mutableStateOf("0.85") }

    // Optional Active Power toggles for kVA and Amps modes
    var calculateActivePowerWithKva by rememberSaveable { mutableStateOf(false) }
    var calculateActivePowerWithAmps by rememberSaveable { mutableStateOf(false) }

    // ── 2. Build Typed Input State ──
    val inputState = remember(
        knownQuantityIndex, phaseIndex, voltageText, sKvaText, pKwText, ampsText,
        powerFactorText, calculateActivePowerWithKva, calculateActivePowerWithAmps
    ) {
        val mode = when (knownQuantityIndex) {
            0 -> LoadCurrentKnownQuantity.KVA_APPARENT
            1 -> LoadCurrentKnownQuantity.KW_ACTIVE
            else -> LoadCurrentKnownQuantity.AMPS_CURRENT
        }
        LoadCurrentInputState(
            knownQuantity = mode,
            phase = if (phaseIndex == 0) VoltageDropSystemPhase.THREE_PHASE else VoltageDropSystemPhase.SINGLE_PHASE,
            voltageV = voltageText.toDoubleOrNull() ?: 400.0,
            sKvaText = sKvaText,
            pKwText = pKwText,
            ampsText = ampsText,
            powerFactorText = powerFactorText,
            calculateActivePowerWithKva = calculateActivePowerWithKva,
            calculateActivePowerWithAmps = calculateActivePowerWithAmps
        )
    }

    // ── 3. Live Pure Calculation ──
    val calculationResult = remember(inputState) {
        LoadCurrentCalculationEngine.calculate(inputState)
    }

    // Copy Summary Action
    val copySummaryAction: () -> Unit = {
        val res = calculationResult
        if (res != null) {
            val pfString = if (res.powerFactor != null) " | PF = ${String.format(Locale.US, "%.2f", res.powerFactor)}" else ""
            val pKwString = if (res.pKw != null) " | Active: ${String.format(Locale.US, "%.2f", res.pKw)} kW" else ""
            val summary = """
                VoltAtlas Load Current & Power Summary:
                • Primary Output: ${res.primaryLabel} = ${res.primaryValueFormatted} ${res.primaryUnit}
                • System: ${if (res.isThreePhase) "3-Phase" else "1-Phase"} ${res.voltageV.toInt()} V
                • Operating Current: ${String.format(Locale.US, "%.2f", res.amps)} A
                • Apparent Power: ${String.format(Locale.US, "%.2f", res.sKva)} kVA$pKwString$pfString
            """.trimIndent()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Load Current Summary", summary))
            Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Enter valid positive values to copy summary", Toast.LENGTH_SHORT).show()
        }
    }

    // ── 4. Scaffold Layout ──
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Load Current Calculator",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ThemeTextColor
                        )
                        Text(
                            text = "Apparent (kVA) & Active Power / Current Conversion",
                            style = MaterialTheme.typography.labelSmall,
                            color = SleekPrimary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("load_current_v2_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Hub",
                            tint = ThemeTextColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = copySummaryAction, modifier = Modifier.testTag("copy_load_current_summary_btn")) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Summary",
                            tint = SleekPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkCardBg)
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
                            LoadCurrentInputsCard(
                                knownQuantityIndex = knownQuantityIndex,
                                onSelectKnownQuantity = { knownQuantityIndex = it },
                                phaseIndex = phaseIndex,
                                onSelectPhase = {
                                    phaseIndex = it
                                    voltageText = if (it == 0) "400" else "230"
                                },
                                voltageText = voltageText,
                                onVoltageChange = { voltageText = it },
                                sKvaText = sKvaText,
                                onSKvaChange = { sKvaText = it },
                                pKwText = pKwText,
                                onPKwChange = { pKwText = it },
                                ampsText = ampsText,
                                onAmpsChange = { ampsText = it },
                                powerFactorText = powerFactorText,
                                onPowerFactorChange = { powerFactorText = it },
                                calculateActivePowerWithKva = calculateActivePowerWithKva,
                                onToggleActivePowerWithKva = { calculateActivePowerWithKva = it },
                                calculateActivePowerWithAmps = calculateActivePowerWithAmps,
                                onToggleActivePowerWithAmps = { calculateActivePowerWithAmps = it }
                            )
                        }
                    }

                    // Right Pane: Results & Details (50%)
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        item {
                            LoadCurrentResultsSection(result = calculationResult)
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
                        LoadCurrentInputsCard(
                            knownQuantityIndex = knownQuantityIndex,
                            onSelectKnownQuantity = { knownQuantityIndex = it },
                            phaseIndex = phaseIndex,
                            onSelectPhase = {
                                phaseIndex = it
                                voltageText = if (it == 0) "400" else "230"
                            },
                            voltageText = voltageText,
                            onVoltageChange = { voltageText = it },
                            sKvaText = sKvaText,
                            onSKvaChange = { sKvaText = it },
                            pKwText = pKwText,
                            onPKwChange = { pKwText = it },
                            ampsText = ampsText,
                            onAmpsChange = { ampsText = it },
                            powerFactorText = powerFactorText,
                            onPowerFactorChange = { powerFactorText = it },
                            calculateActivePowerWithKva = calculateActivePowerWithKva,
                            onToggleActivePowerWithKva = { calculateActivePowerWithKva = it },
                            calculateActivePowerWithAmps = calculateActivePowerWithAmps,
                            onToggleActivePowerWithAmps = { calculateActivePowerWithAmps = it }
                        )
                    }

                    item {
                        LoadCurrentResultsSection(result = calculationResult)
                    }
                }
            }
        }
    }
}

// =============================================================================
// SUB-COMPONENTS & SECTIONS
// =============================================================================

@Composable
private fun LoadCurrentInputsCard(
    knownQuantityIndex: Int,
    onSelectKnownQuantity: (Int) -> Unit,
    phaseIndex: Int,
    onSelectPhase: (Int) -> Unit,
    voltageText: String,
    onVoltageChange: (String) -> Unit,
    sKvaText: String,
    onSKvaChange: (String) -> Unit,
    pKwText: String,
    onPKwChange: (String) -> Unit,
    ampsText: String,
    onAmpsChange: (String) -> Unit,
    powerFactorText: String,
    onPowerFactorChange: (String) -> Unit,
    calculateActivePowerWithKva: Boolean,
    onToggleActivePowerWithKva: (Boolean) -> Unit,
    calculateActivePowerWithAmps: Boolean,
    onToggleActivePowerWithAmps: (Boolean) -> Unit
) {
    QuickCard {
        QuickSectionTitle(
            title = "Conversion Mode & System",
            badgeText = when (knownQuantityIndex) {
                0 -> "Known: kVA"
                1 -> "Known: kW"
                else -> "Known: Amps"
            }
        )

        // 1. Known Quantity Selector
        Text("Primary Known Quantity", color = SlateTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        QuickSegmentedControl(
            options = LOAD_KNOWN_QUANTITY_OPTIONS,
            selectedIndex = knownQuantityIndex,
            onSelectIndex = onSelectKnownQuantity
        )

        // 2. System Topology & Voltage
        Text("System Phase & Voltage", color = SlateTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.weight(1.2f)) {
                QuickSegmentedControl(
                    options = LOAD_PHASE_OPTIONS,
                    selectedIndex = phaseIndex,
                    onSelectIndex = onSelectPhase
                )
            }
            OutlinedTextField(
                value = voltageText,
                onValueChange = onVoltageChange,
                label = { Text("Line Voltage (V)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(0.8f).testTag("v2_input_voltage")
            )
        }

        HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))

        // 3. Mode-Specific Load Inputs
        when (knownQuantityIndex) {
            0 -> {
                // Known kVA
                OutlinedTextField(
                    value = sKvaText,
                    onValueChange = onSKvaChange,
                    label = { Text("Apparent Power S (kVA)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("v2_input_s_kva")
                )

                // Optional Active Power Resolution
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Resolve Active Power P (kW) with PF", color = ThemeTextColor, fontSize = 12.sp)
                    Switch(
                        checked = calculateActivePowerWithKva,
                        onCheckedChange = onToggleActivePowerWithKva,
                        modifier = Modifier.testTag("v2_toggle_active_power_kva")
                    )
                }

                if (calculateActivePowerWithKva) {
                    OutlinedTextField(
                        value = powerFactorText,
                        onValueChange = onPowerFactorChange,
                        label = { Text("Power Factor (cosφ)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("v2_input_pf")
                    )
                }
            }

            1 -> {
                // Known kW (Requires PF)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pKwText,
                        onValueChange = onPKwChange,
                        label = { Text("Active Power P (kW)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("v2_input_p_kw")
                    )
                    OutlinedTextField(
                        value = powerFactorText,
                        onValueChange = onPowerFactorChange,
                        label = { Text("Power Factor (cosφ)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("v2_input_pf")
                    )
                }
            }

            2 -> {
                // Known Line Current (Amps)
                OutlinedTextField(
                    value = ampsText,
                    onValueChange = onAmpsChange,
                    label = { Text("Operating Line Current I (Amps)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("v2_input_current_amps")
                )

                // Optional Active Power Resolution
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Resolve Active Power P (kW) with PF", color = ThemeTextColor, fontSize = 12.sp)
                    Switch(
                        checked = calculateActivePowerWithAmps,
                        onCheckedChange = onToggleActivePowerWithAmps,
                        modifier = Modifier.testTag("v2_toggle_active_power_amps")
                    )
                }

                if (calculateActivePowerWithAmps) {
                    OutlinedTextField(
                        value = powerFactorText,
                        onValueChange = onPowerFactorChange,
                        label = { Text("Power Factor (cosφ)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("v2_input_pf")
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadCurrentResultsSection(
    result: LoadCurrentDetailedResult?
) {
    if (result == null) {
        QuickCard {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = SleekPrimary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Enter valid positive operating values to view live electrical conversion results.",
                    color = SlateTextSecondary,
                    fontSize = 13.sp
                )
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Primary Hero Metric Card
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
                    Text(text = result.primaryLabel, color = SlateTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Surface(
                        color = SleekPrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, SleekPrimary)
                    ) {
                        Text(
                            text = if (result.isThreePhase) "3-Phase @ ${result.voltageV.toInt()}V" else "1-Phase @ ${result.voltageV.toInt()}V",
                            color = SleekPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = result.primaryValueFormatted,
                        color = ThemeTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = result.primaryUnit,
                        color = SleekPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }

        // 2. Secondary Metrics Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickMetricHero(
                title = "Apparent Power (S)",
                value = "${String.format(Locale.US, "%.2f", result.sKva)} kVA",
                subtitle = "Source: ${result.sKvaSource.name}",
                modifier = Modifier.weight(1f),
                accentColor = if (result.sKvaSource == LoadCurrentSourceType.USER_INPUT) SleekPrimary else Color(0xFF10B981)
            )
            QuickMetricHero(
                title = "Line Current (I)",
                value = "${String.format(Locale.US, "%.2f", result.amps)} A",
                subtitle = "Source: ${result.ampsSource.name}",
                modifier = Modifier.weight(1f),
                accentColor = if (result.ampsSource == LoadCurrentSourceType.USER_INPUT) SleekPrimary else Color(0xFF10B981)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickMetricHero(
                title = "Active Power (P)",
                value = if (result.pKw != null) "${String.format(Locale.US, "%.2f", result.pKw)} kW" else "Not Requested",
                subtitle = if (result.pKw != null) "Source: ${result.pKwSource.name}" else "PF omitted",
                modifier = Modifier.weight(1f),
                accentColor = if (result.pKw != null) SleekPass else SlateTextSecondary
            )
            QuickMetricHero(
                title = "Power Factor (cosφ)",
                value = if (result.powerFactor != null) String.format(Locale.US, "%.2f", result.powerFactor) else "Not Required",
                subtitle = if (result.qKvar != null) "Q = ${String.format(Locale.US, "%.2f", result.qKvar)} kVAR" else "Direct S ↔ I Mode",
                modifier = Modifier.weight(1f),
                accentColor = if (result.powerFactor != null) Color(0xFFF59E0B) else SlateTextSecondary
            )
        }

        // 3. Calculation Details Accordion
        QuickCalculationDetailsAccordion(
            formula = result.formulaString,
            substitution = result.substitutionString
        )

        // 4. Provenance & Standards Footer
        QuickProvenanceFooter(
            rColumn = "N/A (Pure AC Power Conversion)",
            resolvedR = 0.0,
            resolvedX = 0.0,
            baseAmpacityA = null,
            dataSource = "Internal Deterministic Authority (ElectricalConverters)",
            citation = result.provenanceCitation
        )
    }
}
