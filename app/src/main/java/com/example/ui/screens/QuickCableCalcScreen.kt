package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.engine.SizingResult
import com.example.engine.coordinator.CableCalculationService
import com.example.ui.theme.*
import java.util.Locale

/**
 * Quick Cable Sizing Screen (Stateless Scratchpad).
 *
 * Runs full cable sizing (sizing + voltage drop + deratings + protective device + CPC)
 * directly against [CableCalculationService.calculateSingleCircuit], bypassing:
 * - Room / AppDatabase (no persistence)
 * - EngineeringRepository
 * - Coordinator freshness registry and sizingCache
 * - Project revision tracking
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCableCalcScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Direct Inputs
    var loadKvaText by rememberSaveable { mutableStateOf("50.0") }
    var voltageText by rememberSaveable { mutableStateOf("400") }
    var phases by rememberSaveable { mutableStateOf("3P+N") }
    var pfText by rememberSaveable { mutableStateOf("0.80") }
    var safetyFactorText by rememberSaveable { mutableStateOf("1.00") }
    var lengthText by rememberSaveable { mutableStateOf("50.0") }
    var maxVdPctText by rememberSaveable { mutableStateOf("3.0") }
    var parallelRunsText by rememberSaveable { mutableStateOf("1") }
    var feederType by rememberSaveable { mutableStateOf("Cable") }
    var cableType by rememberSaveable { mutableStateOf("Cu/XLPE/PVC - 4C") }
    var manufacturer by rememberSaveable { mutableStateOf("Elsewedy") }
    var installation by rememberSaveable { mutableStateOf("Laid in Free Air") }

    // Environmental / Derating Inputs
    var airTempText by rememberSaveable { mutableStateOf("40") }
    var gndTempText by rememberSaveable { mutableStateOf("35") }
    var burialDepthText by rememberSaveable { mutableStateOf("0.8") }
    var soilResText by rememberSaveable { mutableStateOf("120") }
    var noCircuitsText by rememberSaveable { mutableStateOf("1") }
    var formation by rememberSaveable { mutableStateOf("Flat") }
    var spacing by rememberSaveable { mutableStateOf("Touching") }

    // Overrides (Optional)
    var showOverrides by rememberSaveable { mutableStateOf(false) }
    var overrideDeviceType by rememberSaveable { mutableStateOf("Auto") }
    var overrideDeviceSizeText by rememberSaveable { mutableStateOf("") }
    var overrideCableSize by rememberSaveable { mutableStateOf("") }
    var overrideNeutralSize by rememberSaveable { mutableStateOf("") }
    var overrideEarthSize by rememberSaveable { mutableStateOf("") }

    // Calculation State
    var calculationResult by remember { mutableStateOf<SizingResult?>(null) }
    var calculatedCircuit by remember { mutableStateOf<CircuitEntity?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val availableCableTypes = remember(Catalog.cableTypes) {
        if (Catalog.cableTypes.isNotEmpty()) {
            Catalog.cableTypes.map { it.cableType }.distinct()
        } else {
            listOf("Cu/XLPE/PVC - 4C", "Cu/XLPE/SWA/PVC", "Cu/PVC", "Al/XLPE/STA/PVC - 4C", "Cu/XLPE")
        }
    }

    val availableManufacturers = listOf("Elsewedy", "Nexans", "Riyadh Cables", "Ducab", "Oman Cables")
    val availableInstallations = listOf("Laid in Free Air", "Laid in Ducts", "Laid Direct in Ground", "Cable Tray", "In Conduit")
    val availablePhases = listOf("3P+N", "1P+N", "3P")
    val availableFormations = listOf("Flat", "Trefoil")
    val availableSpacings = listOf("Touching", "0.15m", "0.30m")
    val availableBreakerTypes = listOf("Auto", "MCB", "MCCB", "ACB")

    fun performCalculation() {
        val loadKva = loadKvaText.toDoubleOrNull()
        val voltage = voltageText.toIntOrNull()
        val pf = pfText.toDoubleOrNull()
        val safetyFactor = safetyFactorText.toDoubleOrNull()
        val length = lengthText.toDoubleOrNull()
        val maxVdPct = maxVdPctText.toDoubleOrNull()
        val parallelRuns = parallelRunsText.toIntOrNull()
        val airTemp = airTempText.toIntOrNull() ?: 40
        val gndTemp = gndTempText.toIntOrNull() ?: 35
        val burialDepth = burialDepthText.toDoubleOrNull() ?: 0.8
        val soilRes = soilResText.toIntOrNull() ?: 120
        val noCircuits = noCircuitsText.toIntOrNull() ?: 1

        if (loadKva == null || loadKva < 0.0) {
            errorMessage = "Please enter a valid positive Load (kVA)."
            return
        }
        if (voltage == null || voltage <= 0) {
            errorMessage = "Please enter a valid Voltage (> 0V)."
            return
        }
        if (pf == null || pf <= 0.0 || pf > 1.0) {
            errorMessage = "Power Factor must be between 0.01 and 1.00."
            return
        }
        if (safetyFactor == null || safetyFactor <= 0.0) {
            errorMessage = "Safety factor must be greater than 0."
            return
        }
        if (length == null || length <= 0.0) {
            errorMessage = "Cable length must be greater than 0 meters."
            return
        }
        if (maxVdPct == null || maxVdPct <= 0.0) {
            errorMessage = "Max Voltage Drop limit must be greater than 0%."
            return
        }
        if (parallelRuns == null || parallelRuns <= 0) {
            errorMessage = "Parallel runs must be at least 1."
            return
        }

        val circuit = CircuitEntity(
            id = 1,
            projectId = "QUICK_CALC",
            circuitName = "Quick Feeder",
            loadKVA = loadKva,
            voltageV = voltage,
            phases = phases,
            pf = pf,
            safetyFactor = safetyFactor,
            length_m = length,
            maxVDpct = maxVdPct / 100.0,
            parallelRuns = parallelRuns,
            feederType = feederType,
            cableType = cableType,
            manufacturer = manufacturer,
            installation = installation,
            airTempC = airTemp,
            gndTempC = gndTemp,
            burialDepth_m = burialDepth,
            soilRes = soilRes,
            noCircuits = noCircuits,
            formation = formation,
            spacing = spacing,
            fedFrom = "Grid",
            transformerId = "",
            overrideDeviceType = overrideDeviceType.takeIf { it != "Auto" && it.isNotBlank() },
            overrideDeviceSize = overrideDeviceSizeText.toDoubleOrNull(),
            overrideCableSize = overrideCableSize.takeIf { it.isNotBlank() },
            overrideNeutralSize = overrideNeutralSize.takeIf { it.isNotBlank() },
            overrideEarthSize = overrideEarthSize.takeIf { it.isNotBlank() }
        )

        try {
            val result = CableCalculationService.calculateSingleCircuit(
                circuit = circuit,
                parentCumulativeVD = 0.0,
                upstreamCircuits = emptyList(),
                transformers = emptyList()
            )
            calculationResult = result
            calculatedCircuit = circuit
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = "Calculation error: ${e.message}"
        }
    }

    fun copySummaryToClipboard() {
        val res = calculationResult ?: return
        val circ = calculatedCircuit ?: return

        val summary = buildString {
            appendLine("=== VOLTATLAS QUICK CABLE SIZING SUMMARY ===")
            appendLine("Status: ${res.status}")
            if (res.failReason.isNotBlank()) {
                appendLine("Fail Reason: ${res.failReason}")
            }
            appendLine("--- Inputs ---")
            appendLine("Load: ${circ.loadKVA} kVA @ ${circ.voltageV}V (${circ.phases}, PF ${circ.pf}, SF ${circ.safetyFactor})")
            appendLine("Length: ${circ.length_m} m | Limit VD: ${String.format(Locale.US, "%.1f", circ.maxVDpct * 100.0)}%")
            appendLine("Cable Spec: ${circ.cableType} (${circ.manufacturer})")
            appendLine("Installation: ${circ.installation} | Parallel Runs: ${circ.parallelRuns}")
            appendLine("Environmental: Air ${circ.airTempC}°C, Gnd ${circ.gndTempC}°C, Soil ${circ.soilRes}, Circuits ${circ.noCircuits}")
            appendLine("--- Outputs ---")
            appendLine("Operating Current (Ib): ${String.format(Locale.US, "%.2f", res.Ib_A)} A")
            appendLine("Protective Device: ${res.deviceSize_A} A ${res.deviceType}")
            appendLine("Selected Cable: ${circ.parallelRuns}x (${res.cableSize_mm2}) ${res.cableMaterial}/${res.insulation}")
            appendLine("Tabulated Rating (Itab): ${String.format(Locale.US, "%.2f", res.ratedI_table_A)} A")
            appendLine("Derated Ampacity (Iz): ${String.format(Locale.US, "%.2f", res.capacity_derated_Iz_A)} A (Derating: ${String.format(Locale.US, "%.3f", res.cTotalDerating)})")
            appendLine("Thermal Utilization: ${String.format(Locale.US, "%.1f", res.utilizationPct)}%")
            appendLine("Voltage Drop: ${String.format(Locale.US, "%.2f", res.voltDrop_V)} V (${String.format(Locale.US, "%.2f", res.vdPct * 100.0)}%)")
            appendLine("Conductors: Neutral ${res.neutral_mm2} mm² | Earth/CPC ${res.earthCPC_mm2} mm²")
            if (res.cableOD_mm > 0.0) {
                appendLine("Outer Diameter: Phase ${String.format(Locale.US, "%.1f", res.cableOD_mm)} mm | Earth ${String.format(Locale.US, "%.1f", res.earthOD_mm)} mm")
            }
            appendLine("============================================")
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("VoltAtlas Cable Calculation", summary)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Calculation summary copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Quick Cable Sizing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ThemeTextColor
                        )
                        Text(
                            text = "Stateless Scratchpad (In-Memory)",
                            style = MaterialTheme.typography.labelSmall,
                            color = SleekPrimary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("quick_calc_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ThemeTextColor
                        )
                    }
                },
                actions = {
                    if (calculationResult != null) {
                        IconButton(onClick = { copySummaryToClipboard() }, modifier = Modifier.testTag("copy_summary_btn")) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Summary",
                                tint = SleekPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkCardBg)
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error Message Banner
            if (errorMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SleekFail.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, SleekFail),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = SleekFail)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = errorMessage!!, color = SleekFail, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Results Section (if available)
            if (calculationResult != null) {
                item {
                    QuickCalcResultCard(
                        result = calculationResult!!,
                        circuit = calculatedCircuit!!,
                        onCopy = { copySummaryToClipboard() }
                    )
                }
            }

            // 1. Direct Electrical Parameters Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(ElectricalIcons.Circuits, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("1. Electrical & Load Inputs", fontWeight = FontWeight.Bold, color = ThemeTextColor, fontSize = 15.sp)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = loadKvaText,
                                onValueChange = { loadKvaText = it },
                                label = { Text("Load (kVA)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f).testTag("input_load_kva")
                            )
                            OutlinedTextField(
                                value = voltageText,
                                onValueChange = { voltageText = it },
                                label = { Text("Voltage (V)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).testTag("input_voltage")
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DropdownSelector(
                                label = "Phases",
                                selected = phases,
                                options = availablePhases,
                                onSelect = { phases = it },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = pfText,
                                onValueChange = { pfText = it },
                                label = { Text("Power Factor (pf)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f).testTag("input_pf")
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = lengthText,
                                onValueChange = { lengthText = it },
                                label = { Text("Length (m)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f).testTag("input_length")
                            )
                            OutlinedTextField(
                                value = maxVdPctText,
                                onValueChange = { maxVdPctText = it },
                                label = { Text("Max VD Limit (%)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f).testTag("input_max_vd")
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = safetyFactorText,
                                onValueChange = { safetyFactorText = it },
                                label = { Text("Safety Factor") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = parallelRunsText,
                                onValueChange = { parallelRunsText = it },
                                label = { Text("Parallel Runs") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 2. Cable Specification & Installation
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("2. Cable & Installation Method", fontWeight = FontWeight.Bold, color = ThemeTextColor, fontSize = 15.sp)
                        }

                        DropdownSelector(
                            label = "Cable Type / Family",
                            selected = cableType,
                            options = availableCableTypes,
                            onSelect = { cableType = it },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DropdownSelector(
                                label = "Manufacturer",
                                selected = manufacturer,
                                options = availableManufacturers,
                                onSelect = { manufacturer = it },
                                modifier = Modifier.weight(1f)
                            )
                            DropdownSelector(
                                label = "Installation",
                                selected = installation,
                                options = availableInstallations,
                                onSelect = { installation = it },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 3. Environmental & Derating Factors
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("3. Environmental Derating", fontWeight = FontWeight.Bold, color = ThemeTextColor, fontSize = 15.sp)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = airTempText,
                                onValueChange = { airTempText = it },
                                label = { Text("Air Temp (°C)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = gndTempText,
                                onValueChange = { gndTempText = it },
                                label = { Text("Ground Temp (°C)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = burialDepthText,
                                onValueChange = { burialDepthText = it },
                                label = { Text("Burial Depth (m)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = soilResText,
                                onValueChange = { soilResText = it },
                                label = { Text("Soil Res (K·m/W)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = noCircuitsText,
                                onValueChange = { noCircuitsText = it },
                                label = { Text("Grouped Circuits") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            DropdownSelector(
                                label = "Formation",
                                selected = formation,
                                options = availableFormations,
                                onSelect = { formation = it },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 4. Manual Overrides (Expandable)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showOverrides = !showOverrides },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("4. Manual Overrides (Optional)", fontWeight = FontWeight.Bold, color = ThemeTextColor, fontSize = 15.sp)
                            }
                            Icon(
                                imageVector = if (showOverrides) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = SlateTextSecondary
                            )
                        }

                        AnimatedVisibility(visible = showOverrides) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DropdownSelector(
                                        label = "Override Breaker Type",
                                        selected = overrideDeviceType,
                                        options = availableBreakerTypes,
                                        onSelect = { overrideDeviceType = it },
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = overrideDeviceSizeText,
                                        onValueChange = { overrideDeviceSizeText = it },
                                        label = { Text("Breaker Rating (A)") },
                                        placeholder = { Text("Auto") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                OutlinedTextField(
                                    value = overrideCableSize,
                                    onValueChange = { overrideCableSize = it },
                                    label = { Text("Override Cable Size (e.g. 3x70+35)") },
                                    placeholder = { Text("Auto") },
                                    modifier = Modifier.fillMaxWidth().testTag("input_override_cable")
                                )

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = overrideNeutralSize,
                                        onValueChange = { overrideNeutralSize = it },
                                        label = { Text("Neutral (mm²)") },
                                        placeholder = { Text("Auto") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = overrideEarthSize,
                                        onValueChange = { overrideEarthSize = it },
                                        label = { Text("Earth/CPC (mm²)") },
                                        placeholder = { Text("Auto") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Calculate Button Action
            item {
                Button(
                    onClick = { performCalculation() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("calculate_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.horizontalGradient(colors = listOf(SleekPrimary, Color(0xFF2563EB)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Calculate, contentDescription = null, tint = ThemeTextColor, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Calculate Cable Sizing", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickCalcResultCard(
    result: SizingResult,
    circuit: CircuitEntity,
    onCopy: () -> Unit
) {
    val isPass = result.status.contains("PASS")
    val isWarn = result.status.contains("WARN")
    val statusColor = if (isPass) SleekPass else if (isWarn) SleekWarning else SleekFail

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, statusColor),
        modifier = Modifier.fillMaxWidth().testTag("result_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isPass) Icons.Default.CheckCircle else if (isWarn) Icons.Default.Warning else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Result: ${result.status}",
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        fontSize = 16.sp
                    )
                }

                OutlinedButton(
                    onClick = onCopy,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, SleekPrimary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Summary", color = SleekPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (result.failReason.isNotBlank()) {
                Text(
                    text = result.failReason,
                    color = SleekFail,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider(color = BorderColor)

            // Primary Sizing Output
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ResultMetricBox(
                    title = "Selected Cable Size",
                    value = "${circuit.parallelRuns}x (${result.cableSize_mm2})",
                    subtitle = "${result.cableMaterial}/${result.insulation} (${circuit.manufacturer})",
                    modifier = Modifier.weight(1f),
                    accentColor = SleekPrimary
                )

                ResultMetricBox(
                    title = "Protective Device",
                    value = "${result.deviceSize_A.toInt()} A",
                    subtitle = "${result.deviceType} Frame",
                    modifier = Modifier.weight(1f),
                    accentColor = SleekPass
                )
            }

            // Current & Ampacity Breakdown
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallMetricBox(title = "Design Current (Ib)", value = "${String.format(Locale.US, "%.1f", result.Ib_A)} A", modifier = Modifier.weight(1f))
                SmallMetricBox(title = "Tabulated (Itab)", value = "${String.format(Locale.US, "%.1f", result.ratedI_table_A)} A", modifier = Modifier.weight(1f))
                SmallMetricBox(title = "Derated (Iz)", value = "${String.format(Locale.US, "%.1f", result.capacity_derated_Iz_A)} A", modifier = Modifier.weight(1f))
                SmallMetricBox(title = "Utilization", value = "${String.format(Locale.US, "%.1f", result.utilizationPct)}%", modifier = Modifier.weight(1f))
            }

            // Voltage Drop Breakdown
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Voltage Drop (ΔV)", color = SlateTextSecondary, fontSize = 12.sp)
                        Text(
                            text = "${String.format(Locale.US, "%.2f", result.voltDrop_V)} V  (${String.format(Locale.US, "%.2f", result.vdPct * 100.0)}%)",
                            fontWeight = FontWeight.Bold,
                            color = if (result.vdPct <= circuit.maxVDpct) SleekPass else SleekFail,
                            fontSize = 15.sp
                        )
                    }

                    Text(
                        text = "Limit: ${String.format(Locale.US, "%.1f", circuit.maxVDpct * 100.0)}%",
                        color = SlateTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Conductors & Physical Geometry
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallMetricBox(title = "Neutral Conductor", value = "${result.neutral_mm2} mm²", modifier = Modifier.weight(1f))
                SmallMetricBox(title = "Earth / CPC", value = "${result.earthCPC_mm2} mm²", modifier = Modifier.weight(1f))
                if (result.cableOD_mm > 0.0) {
                    SmallMetricBox(title = "Phase OD", value = "${String.format(Locale.US, "%.1f", result.cableOD_mm)} mm", modifier = Modifier.weight(1f))
                    SmallMetricBox(title = "Earth OD", value = "${String.format(Locale.US, "%.1f", result.earthOD_mm)} mm", modifier = Modifier.weight(1f))
                }
            }

            // Total Derating Factor
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total Derating Factor (C_total):", color = SlateTextSecondary, fontSize = 12.sp)
                Text(
                    text = String.format(Locale.US, "%.3f", result.cTotalDerating),
                    fontWeight = FontWeight.Bold,
                    color = ThemeTextColor,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ResultMetricBox(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    accentColor: Color = SleekPrimary
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = SlateTextSecondary, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, color = accentColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = SlateTextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SmallMetricBox(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(title, color = SlateTextSecondary, fontSize = 10.sp, maxLines = 1)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, color = ThemeTextColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
