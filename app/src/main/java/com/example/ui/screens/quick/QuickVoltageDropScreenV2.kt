package com.example.ui.screens.quick

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.example.engine.quick.QuickEngineeringDataBridge
import com.example.ui.components.quick.*
import com.example.ui.formatCatalogCableSizeSafe
import com.example.ui.screens.QuickDropdown
import com.example.ui.theme.*
import java.util.Locale

/**
 * Voltage Drop V2 Pilot Calculator Screen.
 *
 * Implements the Quick Engineering V2 reference architecture:
 * - Smart Dual-Mode Input (Direct Current Mode vs Power + PF Mode)
 * - Canonical Catalog Data Resolution (Manufacturer -> Family -> Size -> R, X, Iz)
 * - Optional Expert Manual Impedance Override
 * - Live Reactive Calculation Lifecycle with Zero Room / AI Mutation
 * - Structured Engineering Results, Compliance Status, Thermal Checks, and Provenance Disclosure
 * - Responsive Single-Column (Phone) and Two-Pane (Tablet/Wide Screen) Layout
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickVoltageDropScreenV2(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current

    // ── 1. Input State Declarations ──
    var inputModeIndex by rememberSaveable { mutableIntStateOf(0) } // 0 = Current Mode, 1 = Power Mode
    var phaseIndex by rememberSaveable { mutableIntStateOf(0) }     // 0 = 3-Phase, 1 = 1-Phase

    var voltageText by rememberSaveable { mutableStateOf("400") }
    var currentText by rememberSaveable { mutableStateOf("100.0") }
    var powerKwText by rememberSaveable { mutableStateOf("55.0") }
    var powerFactorText by rememberSaveable { mutableStateOf("0.85") }
    var lengthText by rememberSaveable { mutableStateOf("50.0") }
    var parallelRunsText by rememberSaveable { mutableStateOf("1") }
    var maxVdLimitText by rememberSaveable { mutableStateOf("3.0") }

    // Cable Catalog Selectors
    val availableManufacturers = remember { QuickEngineeringDataBridge.getAvailableManufacturers() }
    var selectedMfg by rememberSaveable { mutableStateOf(availableManufacturers.firstOrNull() ?: "Elsewedy") }

    val availableFamilies = remember(selectedMfg) { QuickEngineeringDataBridge.getAvailableFamilies(selectedMfg) }
    var selectedFamily by rememberSaveable { mutableStateOf(availableFamilies.firstOrNull() ?: "Cu/XLPE/PVC - 4C") }

    // Auto-align selected family when manufacturer changes
    LaunchedEffect(availableFamilies) {
        if (selectedFamily !in availableFamilies) {
            selectedFamily = availableFamilies.firstOrNull() ?: "Cu/XLPE/PVC - 4C"
        }
    }

    val availableSizes = remember(selectedMfg, selectedFamily) {
        QuickEngineeringDataBridge.getAvailableSizes(selectedMfg, selectedFamily)
    }
    var selectedSizeMm2 by rememberSaveable { mutableDoubleStateOf(35.0) }

    LaunchedEffect(availableSizes) {
        if (availableSizes.isNotEmpty() && selectedSizeMm2 !in availableSizes) {
            selectedSizeMm2 = if (35.0 in availableSizes) 35.0 else availableSizes.first()
        }
    }

    val availableInstallations = listOf("Laid in Free Air", "Laid in Ducts", "Laid Direct in Ground", "In Conduit", "Cable Tray")
    var selectedInstallation by rememberSaveable { mutableStateOf(availableInstallations.first()) }

    // Expert Manual Override
    var isManualOverride by rememberSaveable { mutableStateOf(false) }
    var manualRText by rememberSaveable { mutableStateOf("0.727") }
    var manualXText by rememberSaveable { mutableStateOf("0.080") }

    // ── 2. Build Typed Input State ──
    val inputState = remember(
        inputModeIndex, phaseIndex, voltageText, currentText, powerKwText, powerFactorText,
        lengthText, parallelRunsText, maxVdLimitText, selectedMfg, selectedFamily, selectedSizeMm2,
        selectedInstallation, isManualOverride, manualRText, manualXText
    ) {
        VoltageDropInputState(
            inputMode = if (inputModeIndex == 0) VoltageDropInputMode.CURRENT else VoltageDropInputMode.POWER,
            phase = if (phaseIndex == 0) VoltageDropSystemPhase.THREE_PHASE else VoltageDropSystemPhase.SINGLE_PHASE,
            voltageV = voltageText.toDoubleOrNull() ?: 400.0,
            currentA = currentText.toDoubleOrNull() ?: 0.0,
            powerKw = powerKwText.toDoubleOrNull() ?: 0.0,
            powerFactor = powerFactorText.toDoubleOrNull() ?: 0.85,
            lengthM = lengthText.toDoubleOrNull() ?: 0.0,
            parallelRuns = parallelRunsText.toIntOrNull() ?: 1,
            maxVdLimitPct = maxVdLimitText.toDoubleOrNull() ?: 3.0,
            manufacturer = selectedMfg,
            cableType = selectedFamily,
            cableSizeMm2 = selectedSizeMm2,
            installationMethod = selectedInstallation,
            isManualOverride = isManualOverride,
            manualROhmPerKm = manualRText.toDoubleOrNull() ?: 0.0,
            manualXOhmPerKm = manualXText.toDoubleOrNull() ?: 0.0
        )
    }

    // ── 3. Live Pure Calculation ──
    val calculationResult = remember(inputState) {
        VoltageDropCalculationEngine.calculate(inputState)
    }

    // Copy Summary Action
    val copySummaryAction: () -> Unit = {
        val res = calculationResult
        if (res != null) {
            val summary = """
                VoltAtlas Voltage Drop V2 Summary:
                • Status: ${res.vdCheck.status.name} (${String.format(Locale.US, "%.2f", res.voltageDropPct)}% / ${String.format(Locale.US, "%.2f", res.voltageDropV)} V)
                • System: ${if (res.isThreePhase) "3-Phase" else "1-Phase"} ${res.nominalVoltageV.toInt()} V | Current: ${String.format(Locale.US, "%.1f", res.effectiveOperatingCurrentA)} A | Length: ${inputState.lengthM} m
                • Cable: ${inputState.cableSizeMm2} mm² (${inputState.cableType}, ${inputState.manufacturer})
                • Impedance: R = ${String.format(Locale.US, "%.4f", res.resolvedROhmPerKm)} Ω/km, X = ${String.format(Locale.US, "%.4f", res.resolvedXOhmPerKm)} Ω/km
                • Unit Drop: ${String.format(Locale.US, "%.3f", res.mVperAm)} mV/A/m | Receiving: ${String.format(Locale.US, "%.2f", res.receivingVoltageV)} V
                • Compliance: ${res.vdCheck.summaryText}
            """.trimIndent()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Voltage Drop Calculation", summary))
            Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Enter valid inputs to copy calculation", Toast.LENGTH_SHORT).show()
        }
    }

    // ── 4. Scaffold Layout ──
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Voltage Drop Calculator",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ThemeTextColor
                        )
                        Text(
                            text = "Smart Catalog Resolution & IEC 60364-5-52 Compliance",
                            style = MaterialTheme.typography.labelSmall,
                            color = SleekPrimary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("voltage_drop_v2_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Hub",
                            tint = ThemeTextColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = copySummaryAction, modifier = Modifier.testTag("copy_vd_summary_btn")) {
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
                            SystemAndOperatingInputsCard(
                                inputModeIndex = inputModeIndex,
                                onSelectInputMode = { inputModeIndex = it },
                                phaseIndex = phaseIndex,
                                onSelectPhase = {
                                    phaseIndex = it
                                    voltageText = if (it == 0) "400" else "230"
                                },
                                voltageText = voltageText,
                                onVoltageChange = { voltageText = it },
                                currentText = currentText,
                                onCurrentChange = { currentText = it },
                                powerKwText = powerKwText,
                                onPowerKwChange = { powerKwText = it },
                                powerFactorText = powerFactorText,
                                onPowerFactorChange = { powerFactorText = it },
                                lengthText = lengthText,
                                onLengthChange = { lengthText = it },
                                parallelRunsText = parallelRunsText,
                                onParallelRunsChange = { parallelRunsText = it },
                                maxVdLimitText = maxVdLimitText,
                                onMaxVdLimitChange = { maxVdLimitText = it },
                                derivedCurrentA = calculationResult?.effectiveOperatingCurrentA
                            )
                        }

                        item {
                            CableSpecificationCard(
                                selectedMfg = selectedMfg,
                                availableMfgs = availableManufacturers,
                                onSelectMfg = { selectedMfg = it },
                                selectedFamily = selectedFamily,
                                availableFamilies = availableFamilies,
                                onSelectFamily = { selectedFamily = it },
                                selectedSizeMm2 = selectedSizeMm2,
                                availableSizes = availableSizes,
                                onSelectSize = { selectedSizeMm2 = it },
                                selectedInstallation = selectedInstallation,
                                availableInstallations = availableInstallations,
                                onSelectInstallation = { selectedInstallation = it },
                                isManualOverride = isManualOverride,
                                onToggleManualOverride = { isManualOverride = it },
                                manualRText = manualRText,
                                onManualRChange = { manualRText = it },
                                manualXText = manualXText,
                                onManualXChange = { manualXText = it },
                                resolvedR = calculationResult?.resolvedROhmPerKm,
                                resolvedX = calculationResult?.resolvedXOhmPerKm,
                                rColumn = calculationResult?.rColumnUsed
                            )
                        }
                    }

                    // Right Pane: Results & Checks & Provenance (50%)
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        item {
                            ResultsAndChecksSection(result = calculationResult, inputState = inputState)
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
                        SystemAndOperatingInputsCard(
                            inputModeIndex = inputModeIndex,
                            onSelectInputMode = { inputModeIndex = it },
                            phaseIndex = phaseIndex,
                            onSelectPhase = {
                                phaseIndex = it
                                voltageText = if (it == 0) "400" else "230"
                            },
                            voltageText = voltageText,
                            onVoltageChange = { voltageText = it },
                            currentText = currentText,
                            onCurrentChange = { currentText = it },
                            powerKwText = powerKwText,
                            onPowerKwChange = { powerKwText = it },
                            powerFactorText = powerFactorText,
                            onPowerFactorChange = { powerFactorText = it },
                            lengthText = lengthText,
                            onLengthChange = { lengthText = it },
                            parallelRunsText = parallelRunsText,
                            onParallelRunsChange = { parallelRunsText = it },
                            maxVdLimitText = maxVdLimitText,
                            onMaxVdLimitChange = { maxVdLimitText = it },
                            derivedCurrentA = calculationResult?.effectiveOperatingCurrentA
                        )
                    }

                    item {
                        CableSpecificationCard(
                            selectedMfg = selectedMfg,
                            availableMfgs = availableManufacturers,
                            onSelectMfg = { selectedMfg = it },
                            selectedFamily = selectedFamily,
                            availableFamilies = availableFamilies,
                            onSelectFamily = { selectedFamily = it },
                            selectedSizeMm2 = selectedSizeMm2,
                            availableSizes = availableSizes,
                            onSelectSize = { selectedSizeMm2 = it },
                            selectedInstallation = selectedInstallation,
                            availableInstallations = availableInstallations,
                            onSelectInstallation = { selectedInstallation = it },
                            isManualOverride = isManualOverride,
                            onToggleManualOverride = { isManualOverride = it },
                            manualRText = manualRText,
                            onManualRChange = { manualRText = it },
                            manualXText = manualXText,
                            onManualXChange = { manualXText = it },
                            resolvedR = calculationResult?.resolvedROhmPerKm,
                            resolvedX = calculationResult?.resolvedXOhmPerKm,
                            rColumn = calculationResult?.rColumnUsed
                        )
                    }

                    item {
                        ResultsAndChecksSection(result = calculationResult, inputState = inputState)
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
private fun SystemAndOperatingInputsCard(
    inputModeIndex: Int,
    onSelectInputMode: (Int) -> Unit,
    phaseIndex: Int,
    onSelectPhase: (Int) -> Unit,
    voltageText: String,
    onVoltageChange: (String) -> Unit,
    currentText: String,
    onCurrentChange: (String) -> Unit,
    powerKwText: String,
    onPowerKwChange: (String) -> Unit,
    powerFactorText: String,
    onPowerFactorChange: (String) -> Unit,
    lengthText: String,
    onLengthChange: (String) -> Unit,
    parallelRunsText: String,
    onParallelRunsChange: (String) -> Unit,
    maxVdLimitText: String,
    onMaxVdLimitChange: (String) -> Unit,
    derivedCurrentA: Double?
) {
    QuickCard {
        QuickSectionTitle(
            title = "System & Operating Load",
            badgeText = if (inputModeIndex == 0) "Direct Current" else "Power Mode"
        )

        // Mode Segmented Toggles
        Text("Input Mode", color = SlateTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        QuickSegmentedControl(
            options = listOf("Current Mode (Direct I)", "Power Mode (kW + cosφ)"),
            selectedIndex = inputModeIndex,
            onSelectIndex = onSelectInputMode
        )

        Text("System Phase & Topology", color = SlateTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        QuickSegmentedControl(
            options = listOf("3-Phase (400V)", "1-Phase (230V)"),
            selectedIndex = phaseIndex,
            onSelectIndex = onSelectPhase
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = voltageText,
                onValueChange = onVoltageChange,
                label = { Text("Voltage (V)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = lengthText,
                onValueChange = onLengthChange,
                label = { Text("Route Length (m)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag("v2_input_length")
            )
        }

        // Mode-Specific Input Fields
        if (inputModeIndex == 0) {
            // Direct Current Mode
            OutlinedTextField(
                value = currentText,
                onValueChange = onCurrentChange,
                label = { Text("Design Load Current (A)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("v2_input_current")
            )
        } else {
            // Power Mode
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = powerKwText,
                    onValueChange = onPowerKwChange,
                    label = { Text("Active Power (kW)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).testTag("v2_input_power")
                )
                OutlinedTextField(
                    value = powerFactorText,
                    onValueChange = onPowerFactorChange,
                    label = { Text("Power Factor (cosφ)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
            if (derivedCurrentA != null && derivedCurrentA > 0.0) {
                Surface(
                    color = SleekPrimary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, SleekPrimary)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Derived Operating Current:", color = SlateTextSecondary, fontSize = 11.sp)
                        Text(
                            text = "${String.format(Locale.US, "%.2f", derivedCurrentA)} A",
                            color = SleekPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = parallelRunsText,
                onValueChange = onParallelRunsChange,
                label = { Text("Parallel Runs (N)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = maxVdLimitText,
                onValueChange = onMaxVdLimitChange,
                label = { Text("Max Permitted VD (%)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CableSpecificationCard(
    selectedMfg: String,
    availableMfgs: List<String>,
    onSelectMfg: (String) -> Unit,
    selectedFamily: String,
    availableFamilies: List<String>,
    onSelectFamily: (String) -> Unit,
    selectedSizeMm2: Double,
    availableSizes: List<Double>,
    onSelectSize: (Double) -> Unit,
    selectedInstallation: String,
    availableInstallations: List<String>,
    onSelectInstallation: (String) -> Unit,
    isManualOverride: Boolean,
    onToggleManualOverride: (Boolean) -> Unit,
    manualRText: String,
    onManualRChange: (String) -> Unit,
    manualXText: String,
    onManualXChange: (String) -> Unit,
    resolvedR: Double?,
    resolvedX: Double?,
    rColumn: String?
) {
    QuickCard {
        QuickSectionTitle(
            title = "Cable Specification",
            accentColor = Color(0xFF10B981),
            badgeText = if (isManualOverride) "Manual Override" else "Catalog-Resolved",
            badgeColor = if (isManualOverride) SleekWarning else SleekPass
        )

        QuickDropdown(
            label = "Manufacturer",
            selected = selectedMfg,
            options = availableMfgs,
            onSelect = onSelectMfg,
            modifier = Modifier.fillMaxWidth().testTag("v2_dropdown_mfg")
        )

        QuickDropdown(
            label = "Cable Family",
            selected = selectedFamily,
            options = availableFamilies,
            onSelect = onSelectFamily,
            modifier = Modifier.fillMaxWidth().testTag("v2_dropdown_family")
        )

        val sizeOptions = remember(availableSizes) {
            availableSizes.map(::formatCatalogCableSizeSafe)
        }
        val currentSizeDisplay = formatCatalogCableSizeSafe(selectedSizeMm2)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickDropdown(
                label = "Conductor Size",
                selected = currentSizeDisplay,
                options = if (sizeOptions.isNotEmpty()) sizeOptions else listOf(currentSizeDisplay),
                onSelect = { szStr ->
                    val num = szStr.replace("mm²", "").replace("mm\u00B2", "").trim().toDoubleOrNull()
                    if (num != null) onSelectSize(num)
                },
                modifier = Modifier.weight(1f).testTag("v2_dropdown_size")
            )

            QuickDropdown(
                label = "Installation Method",
                selected = selectedInstallation,
                options = availableInstallations,
                onSelect = onSelectInstallation,
                modifier = Modifier.weight(1f)
            )
        }

        // Catalog-Resolved Impedance Info Badge
        if (!isManualOverride && resolvedR != null && resolvedX != null) {
            Surface(
                color = SurfaceElevated,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Resolved Impedance (${rColumn ?: "Datasheet"}):",
                        color = SlateTextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "R=${String.format(Locale.US, "%.4f", resolvedR)} | X=${String.format(Locale.US, "%.4f", resolvedX)} Ω/km",
                        color = SleekPass,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Expert Manual Override Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = if (isManualOverride) SleekWarning else SlateTextSecondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Expert Mode (Manual R & X Override)", color = ThemeTextColor, fontSize = 12.sp)
            }
            Switch(
                checked = isManualOverride,
                onCheckedChange = onToggleManualOverride,
                modifier = Modifier.testTag("v2_switch_manual_override")
            )
        }

        if (isManualOverride) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = manualRText,
                    onValueChange = onManualRChange,
                    label = { Text("Manual R (Ω/km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = manualXText,
                    onValueChange = onManualXChange,
                    label = { Text("Manual X (Ω/km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ResultsAndChecksSection(
    result: VoltageDropDetailedResult?,
    inputState: VoltageDropInputState
) {
    if (result == null) {
        val isMissingCatalogData = inputState.cableSizeMm2 > 0.0 &&
            !inputState.isManualOverride &&
            com.example.engine.quick.QuickEngineeringDataBridge.resolveCableImpedance(inputState.cableSizeMm2, inputState.cableType) == null

        QuickCard(
            borderColor = if (isMissingCatalogData) SleekWarning else BorderColor
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isMissingCatalogData) Icons.Default.Warning else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (isMissingCatalogData) SleekWarning else SleekPrimary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isMissingCatalogData) {
                        "Required cable reactance and resistance data is unavailable in the authoritative catalog for ${inputState.cableSizeMm2} mm². Calculation cannot be completed safely from catalog data. Enable Expert Mode to supply manual R & X parameters."
                    } else {
                        "Enter positive operating parameters and select a valid cable size to view live voltage drop results."
                    },
                    color = if (isMissingCatalogData) ThemeTextColor else SlateTextSecondary,
                    fontSize = 13.sp
                )
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Executive Status Banner
        QuickStatusBanner(
            status = result.vdCheck.status,
            headline = result.vdCheck.summaryText,
            subText = result.vdCheck.detailText
        )

        // 2. Primary Metrics Cards Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickMetricHero(
                title = "Percentage Drop (ΔV%)",
                value = "${String.format(Locale.US, "%.2f", result.voltageDropPct)} %",
                subtitle = "Limit: ${String.format(Locale.US, "%.2f", result.nominalVoltageV)} V ref",
                modifier = Modifier.weight(1f),
                accentColor = if (result.voltageDropPct <= inputState.maxVdLimitPct) SleekPass else SleekFail
            )
            QuickMetricHero(
                title = "Total Voltage Drop (ΔV)",
                value = "${String.format(Locale.US, "%.2f", result.voltageDropV)} V",
                subtitle = "Receiving: ${String.format(Locale.US, "%.1f", result.receivingVoltageV)} V",
                modifier = Modifier.weight(1f),
                accentColor = SleekPrimary
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickMetricHero(
                title = "Unit Drop Rate",
                value = "${String.format(Locale.US, "%.3f", result.mVperAm)} mV/A/m",
                subtitle = if (result.isThreePhase) "3-Phase (√3 factor)" else "1-Phase (2.0 factor)",
                modifier = Modifier.weight(1f)
            )
            QuickMetricHero(
                title = "Load Current Per Run",
                value = "${String.format(Locale.US, "%.1f", result.currentPerRunA)} A",
                subtitle = if (result.parallelRuns > 1) "${result.parallelRuns} parallel runs" else "Single run",
                modifier = Modifier.weight(1f)
            )
        }

        // 3. Engineering Checks Card
        QuickCard {
            QuickSectionTitle(title = "Engineering Checks", accentColor = SleekPrimary)
            QuickCheckRow(
                title = result.vdCheck.title,
                status = result.vdCheck.status,
                summary = result.vdCheck.summaryText,
                detail = result.vdCheck.detailText
            )
            if (result.ampacityCheck != null) {
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))
                QuickCheckRow(
                    title = result.ampacityCheck.title,
                    status = result.ampacityCheck.status,
                    summary = result.ampacityCheck.summaryText,
                    detail = result.ampacityCheck.detailText
                )
            }
        }

        // 4. Calculation Details Accordion
        QuickCalculationDetailsAccordion(
            formula = result.formulaString,
            substitution = result.substitutionString
        )

        // 5. Data Provenance & Standards Footer
        QuickProvenanceFooter(
            rColumn = result.rColumnUsed,
            resolvedR = result.resolvedROhmPerKm,
            resolvedX = result.resolvedXOhmPerKm,
            baseAmpacityA = result.baseAmpacityA,
            dataSource = result.dataSource,
            citation = result.provenanceCitation
        )
    }
}
