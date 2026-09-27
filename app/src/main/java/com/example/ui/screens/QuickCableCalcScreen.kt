package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import com.example.ui.theme.VoltAtlasMotion
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.data.resolveCableType
import com.example.engine.SizingResult
import com.example.engine.coordinator.CableCalculationService
import com.example.ui.catalogCableSizeOptions
import com.example.ui.getDistinctCatalogManufacturers
import com.example.ui.normalizeCableOverride
import com.example.ui.normalizeDeviceSizeOverride
import com.example.ui.normalizeDeviceTypeOverride
import com.example.ui.parseFamilyInfo
import com.example.ui.standardConductorSizeOptions
import com.example.ui.standardDeviceRatingOptions
import com.example.ui.theme.*
import com.example.ui.components.VoltAtlasTopBar
import com.example.ui.designsystem.icons.ElectricalIcons
import kotlinx.coroutines.launch
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
private val CableFieldMinWidth: Dp = 130.dp

/**
 * Two-field input row for the Quick Cable scratchpad.
 *
 * LEVEL B screen-local reflow: the per-field content minimum is derived from
 * the longest engineering field labels in this screen ("Ground Temp (°C)",
 * "Override Breaker Type") plus M3 text-field horizontal padding (~130dp),
 * scaled by the current font scale. When the two fields can no longer each
 * hold their label + value inline, the pair stacks vertically — the field
 * ORDER and therefore the value/label association is preserved. No device
 * breakpoints are used.
 */
@Composable
private fun CableInputFieldRow(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val requiredPerField = CableFieldMinWidth * LocalDensity.current.fontScale
        val stackVertically = maxWidth / 2 < requiredPerField
        if (stackVertically) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                first(Modifier.weight(1f))
                second(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Immutable UI State for Quick Cable Sizing Screen.
 */
@Immutable
data class CableCalcScreenUiState(
    val loadKvaText: String = "50.0",
    val voltageText: String = "400",
    val phases: String = "3P+N",
    val pfText: String = "0.80",
    val safetyFactorText: String = "1.00",
    val lengthText: String = "50.0",
    val maxVdPctText: String = "3.0",
    val parallelRunsText: String = "1",
    val feederType: String = "Cable",
    val manufacturer: String = "Elsewedy",
    val cableType: String = "",
    val installation: String = "Laid in Free Air",
    val airTempText: String = "40",
    val gndTempText: String = "35",
    val burialDepthText: String = "0.8",
    val soilResText: String = "120",
    val noCircuitsText: String = "1",
    val formation: String = "Flat",
    val spacing: String = "Touching",
    val showOverrides: Boolean = false,
    val overrideDeviceType: String = "Auto",
    val overrideDeviceRating: String = "Auto",
    val overrideCableSize: String = "Auto",
    val overrideNeutralSize: String = "Auto",
    val overrideEarthSize: String = "Auto",
    val calculationResult: SizingResult? = null,
    val calculatedCircuit: CircuitEntity? = null,
    val errorMessage: String? = null,
    val availableManufacturers: List<String> = emptyList(),
    val availablePhases: List<String> = listOf("3P+N", "1P+N", "3P"),
    val availableInstallations: List<String> = listOf("Laid in Free Air", "Laid in Ducts", "Laid Direct in Ground", "In Conduit", "Cable Tray"),
    val availableFormations: List<String> = listOf("Flat", "Trefoil"),
    val availableBreakerTypes: List<String> = listOf("Auto", "MCB", "MCCB", "ACB", "Switch-Disconnector"),
    val availableRatings: List<String> = emptyList(),
    val availableCableSizes: List<String> = emptyList(),
    val availableConductorSizes: List<String> = emptyList()
)

/**
 * Seam 1: Screen Wiring Composable.
 * Owns app dependencies (Context, Toast, ClipboardManager), durable screen state,
 * and calculation engine execution.
 */
@Composable
fun QuickCableCalcScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Intercept system/hardware/gesture back to return to ProjectPickerScreen
    BackHandler(onBack = onBack)

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

    // Catalog-Driven Manufacturer Selection
    val availableManufacturers = remember(Catalog.cableCatalog) {
        getDistinctCatalogManufacturers()
    }
    var manufacturer by rememberSaveable { mutableStateOf(availableManufacturers.firstOrNull() ?: "Elsewedy") }

    // Initial Cable Type from Catalog
    val initialCableType = remember(manufacturer, Catalog.cableCatalog, Catalog.cableTypes) {
        val validForMfg = Catalog.cableTypes.filter { type ->
            Catalog.cableCatalog.any { cat ->
                cat.typeCode == type.code && cat.mfg.trim().equals(manufacturer.trim(), ignoreCase = true)
            }
        }.map { it.cableType }.distinct().sorted()
        validForMfg.firstOrNull() ?: resolveCableType("Cu/XLPE/PVC - 4C", "4C", "Cu", "XLPE")
    }
    var cableType by rememberSaveable { mutableStateOf(initialCableType) }
    var installation by rememberSaveable { mutableStateOf("Laid in Free Air") }

    // Environmental / Derating Inputs
    var airTempText by rememberSaveable { mutableStateOf("40") }
    var gndTempText by rememberSaveable { mutableStateOf("35") }
    var burialDepthText by rememberSaveable { mutableStateOf("0.8") }
    var soilResText by rememberSaveable { mutableStateOf("120") }
    var noCircuitsText by rememberSaveable { mutableStateOf("1") }
    var formation by rememberSaveable { mutableStateOf("Flat") }
    var spacing by rememberSaveable { mutableStateOf("Touching") }

    // Overrides (Selectors / Dropdowns from Catalog & Standards)
    var showOverrides by rememberSaveable { mutableStateOf(false) }
    var overrideDeviceType by rememberSaveable { mutableStateOf("Auto") }
    var overrideDeviceRating by rememberSaveable { mutableStateOf("Auto") }
    var overrideCableSize by rememberSaveable { mutableStateOf("Auto") }
    var overrideNeutralSize by rememberSaveable { mutableStateOf("Auto") }
    var overrideEarthSize by rememberSaveable { mutableStateOf("Auto") }

    // Calculation State
    var calculationResult by remember { mutableStateOf<SizingResult?>(null) }
    var calculatedCircuit by remember { mutableStateOf<CircuitEntity?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val availableRatings = remember {
        listOf("Auto") + standardDeviceRatingOptions()
    }

    val availableCableSizes = remember(manufacturer, cableType, Catalog.cableCatalog, Catalog.cableTypes) {
        listOf("Auto") + catalogCableSizeOptions(manufacturer, cableType)
    }

    val availableConductorSizes = remember {
        listOf("Auto") + standardConductorSizeOptions()
    }

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

        val parsedOverrideCable = normalizeCableOverride(overrideCableSize)
        val parsedOverrideRating = normalizeDeviceSizeOverride(overrideDeviceRating)
        val parsedOverrideDevType = normalizeDeviceTypeOverride(overrideDeviceType)
        val parsedOverrideNeutral = normalizeCableOverride(overrideNeutralSize)
        val parsedOverrideEarth = normalizeCableOverride(overrideEarthSize)

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
            overrideDeviceType = parsedOverrideDevType,
            overrideDeviceSize = parsedOverrideRating,
            overrideCableSize = parsedOverrideCable,
            overrideNeutralSize = parsedOverrideNeutral,
            overrideEarthSize = parsedOverrideEarth
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

            // Auto-scroll to result card placed below Calculate button
            coroutineScope.launch {
                listState.animateScrollToItem(index = 5)
            }
        } catch (e: Exception) {
            errorMessage = "Calculation error: ${e.message}"
        }
    }

    val copySummaryAction: () -> Unit = {
        val res = calculationResult
        val circ = calculatedCircuit
        if (res != null && circ != null) {
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
                appendLine("Voltage Drop: ${String.format(Locale.US, "%.2f", res.voltDrop_V)} V (${String.format(Locale.US, "%.2f", res.vdPct)}%)")
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
    }

    val uiState = CableCalcScreenUiState(
        loadKvaText = loadKvaText,
        voltageText = voltageText,
        phases = phases,
        pfText = pfText,
        safetyFactorText = safetyFactorText,
        lengthText = lengthText,
        maxVdPctText = maxVdPctText,
        parallelRunsText = parallelRunsText,
        feederType = feederType,
        manufacturer = manufacturer,
        cableType = cableType,
        installation = installation,
        airTempText = airTempText,
        gndTempText = gndTempText,
        burialDepthText = burialDepthText,
        soilResText = soilResText,
        noCircuitsText = noCircuitsText,
        formation = formation,
        spacing = spacing,
        showOverrides = showOverrides,
        overrideDeviceType = overrideDeviceType,
        overrideDeviceRating = overrideDeviceRating,
        overrideCableSize = overrideCableSize,
        overrideNeutralSize = overrideNeutralSize,
        overrideEarthSize = overrideEarthSize,
        calculationResult = calculationResult,
        calculatedCircuit = calculatedCircuit,
        errorMessage = errorMessage,
        availableManufacturers = availableManufacturers,
        availableRatings = availableRatings,
        availableCableSizes = availableCableSizes,
        availableConductorSizes = availableConductorSizes
    )

    QuickCableCalcContent(
        uiState = uiState,
        onLoadKvaChange = { loadKvaText = it },
        onVoltageChange = { voltageText = it },
        onPhasesChange = { phases = it },
        onPfChange = { pfText = it },
        onSafetyFactorChange = { safetyFactorText = it },
        onLengthChange = { lengthText = it },
        onMaxVdPctChange = { maxVdPctText = it },
        onParallelRunsChange = { parallelRunsText = it },
        onManufacturerChange = { selected ->
            manufacturer = selected
            val validTypes = Catalog.cableTypes.filter { type ->
                Catalog.cableCatalog.any { cat ->
                    cat.typeCode == type.code && cat.mfg.trim().equals(selected.trim(), ignoreCase = true)
                }
            }.map { it.cableType }.distinct().sorted()
            if (validTypes.isNotEmpty() && !validTypes.contains(cableType)) {
                cableType = validTypes.firstOrNull() ?: ""
            }
        },
        onCableTypeChange = { cableType = it },
        onInstallationChange = { installation = it },
        onAirTempChange = { airTempText = it },
        onGndTempChange = { gndTempText = it },
        onBurialDepthChange = { burialDepthText = it },
        onSoilResChange = { soilResText = it },
        onNoCircuitsChange = { noCircuitsText = it },
        onFormationChange = { formation = it },
        onSpacingChange = { spacing = it },
        onToggleOverrides = { showOverrides = !showOverrides },
        onOverrideDeviceTypeChange = { overrideDeviceType = it },
        onOverrideDeviceRatingChange = { overrideDeviceRating = it },
        onOverrideCableSizeChange = { overrideCableSize = it },
        onOverrideNeutralSizeChange = { overrideNeutralSize = it },
        onOverrideEarthSizeChange = { overrideEarthSize = it },
        onCalculate = { performCalculation() },
        onBack = onBack,
        onCopySummary = copySummaryAction,
        listState = listState
    )
}

/**
 * Seam 3: Pure Previewable Content Composable for Quick Cable Sizing.
 *
 * Free of platform services (Context, Clipboard, Toast), receiving immutable
 * UI state and plain lambda callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCableCalcContent(
    uiState: CableCalcScreenUiState,
    onLoadKvaChange: (String) -> Unit,
    onVoltageChange: (String) -> Unit,
    onPhasesChange: (String) -> Unit,
    onPfChange: (String) -> Unit,
    onSafetyFactorChange: (String) -> Unit,
    onLengthChange: (String) -> Unit,
    onMaxVdPctChange: (String) -> Unit,
    onParallelRunsChange: (String) -> Unit,
    onManufacturerChange: (String) -> Unit,
    onCableTypeChange: (String) -> Unit,
    onInstallationChange: (String) -> Unit,
    onAirTempChange: (String) -> Unit,
    onGndTempChange: (String) -> Unit,
    onBurialDepthChange: (String) -> Unit,
    onSoilResChange: (String) -> Unit,
    onNoCircuitsChange: (String) -> Unit,
    onFormationChange: (String) -> Unit,
    onSpacingChange: (String) -> Unit,
    onToggleOverrides: () -> Unit,
    onOverrideDeviceTypeChange: (String) -> Unit,
    onOverrideDeviceRatingChange: (String) -> Unit,
    onOverrideCableSizeChange: (String) -> Unit,
    onOverrideNeutralSizeChange: (String) -> Unit,
    onOverrideEarthSizeChange: (String) -> Unit,
    onCalculate: () -> Unit,
    onBack: () -> Unit,
    onCopySummary: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val focusManager = LocalFocusManager.current

    // Dynamic Catalog Family Resolver & Cascading Filters
    val allCatalogFamilies = remember(uiState.manufacturer, uiState.cableType) {
        val matching = Catalog.cableTypes.filter { type ->
            Catalog.cableCatalog.any { cat -> cat.typeCode == type.code && cat.mfg.trim().equals(uiState.manufacturer.trim(), ignoreCase = true) }
        }.map { it.cableType }.distinct().sorted()
        if (matching.isNotEmpty()) matching else listOf(uiState.cableType)
    }

    val allParsedFamilies = remember(allCatalogFamilies) {
        allCatalogFamilies.map { parseFamilyInfo(it) }
    }

    val initialFamilyInfo = remember(uiState.cableType) { parseFamilyInfo(uiState.cableType) }
    var filterMat by remember(uiState.cableType) { mutableStateOf(initialFamilyInfo.material) }
    var filterArmour by remember(uiState.cableType) { mutableStateOf(initialFamilyInfo.armourGroup) }
    var filterCores by remember(uiState.cableType) { mutableStateOf(initialFamilyInfo.coresConfig) }
    var filterQuery by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            VoltAtlasTopBar(
                title = "Quick Cable Sizing",
                subtitle = "Stateless Scratchpad (In-Memory)",
                onBack = onBack,
                backDescription = "Back",
                backButtonTestTag = "quick_calc_back_btn",
                actions = {
                    if (uiState.calculationResult != null) {
                        IconButton(onClick = onCopySummary, modifier = Modifier.testTag("copy_summary_btn")) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Summary",
                                tint = SleekPrimary
                            )
                        }
                    }
                }
            )
        },
        containerColor = ThemeBackground
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.errorMessage != null) {
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
                            Text(text = uiState.errorMessage, color = SleekFail, fontSize = 13.sp)
                        }
                    }
                }
            }

            // 1. Direct Electrical Parameters Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ThemeSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ThemeBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(ElectricalIcons.Circuits, contentDescription = null, tint = ThemePrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("1. Electrical & Load Inputs", fontWeight = FontWeight.Bold, color = ThemeTextPrimary, fontSize = 15.sp)
                        }

                        CableInputFieldRow(
                            first = { fieldModifier ->
                                OutlinedTextField(
                                    value = uiState.loadKvaText,
                                    onValueChange = onLoadKvaChange,
                                    label = { Text("Load (kVA)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                                    modifier = fieldModifier.testTag("input_load_kva")
                                )
                            },
                            second = { fieldModifier ->
                                OutlinedTextField(
                                    value = uiState.voltageText,
                                    onValueChange = onVoltageChange,
                                    label = { Text("Voltage (V)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                                    modifier = fieldModifier.testTag("input_voltage")
                                )
                            }
                        )

                        CableInputFieldRow(
                            first = { fieldModifier ->
                                DropdownSelector(
                                    label = "Phases",
                                    selected = uiState.phases,
                                    options = uiState.availablePhases,
                                    onSelect = onPhasesChange,
                                    modifier = fieldModifier
                                )
                            },
                            second = { fieldModifier ->
                                OutlinedTextField(
                                    value = uiState.pfText,
                                    onValueChange = onPfChange,
                                    label = { Text("Power Factor (pf)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                                    modifier = fieldModifier.testTag("input_pf")
                                )
                            }
                        )

                        CableInputFieldRow(
                            first = { fieldModifier ->
                                OutlinedTextField(
                                    value = uiState.lengthText,
                                    onValueChange = onLengthChange,
                                    label = { Text("Length (m)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                                    modifier = fieldModifier.testTag("input_length")
                                )
                            },
                            second = { fieldModifier ->
                                OutlinedTextField(
                                    value = uiState.maxVdPctText,
                                    onValueChange = onMaxVdPctChange,
                                    label = { Text("Max VD Limit (%)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                                    modifier = fieldModifier.testTag("input_max_vd")
                                )
                            }
                        )

                        CableInputFieldRow(
                            first = { fieldModifier ->
                                OutlinedTextField(
                                    value = uiState.safetyFactorText,
                                    onValueChange = onSafetyFactorChange,
                                    label = { Text("Safety Factor") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                                    modifier = fieldModifier
                                )
                            },
                            second = { fieldModifier ->
                                OutlinedTextField(
                                    value = uiState.parallelRunsText,
                                    onValueChange = onParallelRunsChange,
                                    label = { Text("Parallel Runs") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                    modifier = fieldModifier
                                )
                            }
                        )
                    }
                }
            }

            // 2. Cable Specification & Installation (Catalog-Driven)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ThemeSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ThemeBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = ThemePrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("2. Cable & Installation Method", fontWeight = FontWeight.Bold, color = ThemeTextPrimary, fontSize = 15.sp)
                        }

                        DropdownSelector(
                            label = "Manufacturer / Cable Source",
                            selected = uiState.manufacturer,
                            options = uiState.availableManufacturers,
                            onSelect = onManufacturerChange,
                            modifier = Modifier.fillMaxWidth().testTag("input_manufacturer")
                        )

                        // Conductor Material Filter
                        Text("Conductor Material Filter", color = SlateTextSecondary, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Copper", "Aluminium").forEach { mat ->
                                val isAvail = allParsedFamilies.any { it.material == mat }
                                FilterChip(
                                    selected = filterMat == mat,
                                    onClick = {
                                        if (isAvail) {
                                            filterMat = mat
                                            val candidate = allParsedFamilies.firstOrNull { it.material == mat && it.armourGroup == filterArmour && it.coresConfig == filterCores }
                                            if (candidate != null) {
                                                onCableTypeChange(candidate.originalType)
                                            } else {
                                                val alt = allParsedFamilies.firstOrNull { it.material == mat }
                                                if (alt != null) {
                                                    filterArmour = alt.armourGroup
                                                    filterCores = alt.coresConfig
                                                    onCableTypeChange(alt.originalType)
                                                }
                                            }
                                        }
                                    },
                                    label = { Text(if (isAvail) mat else "$mat (N/A)", fontSize = 11.sp) },
                                    enabled = isAvail,
                                    modifier = Modifier.weight(1f).testTag("filter_mat_$mat")
                                )
                            }
                        }

                        // Armour Construction Filter
                        Text("Armour Construction Filter", color = SlateTextSecondary, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Unarmoured", "Armoured").forEach { arm ->
                                val isAvail = allParsedFamilies.any { it.material == filterMat && it.armourGroup == arm }
                                FilterChip(
                                    selected = filterArmour == arm,
                                    onClick = {
                                        if (isAvail) {
                                            filterArmour = arm
                                            val candidate = allParsedFamilies.firstOrNull { it.material == filterMat && it.armourGroup == arm && it.coresConfig == filterCores }
                                            if (candidate != null) {
                                                onCableTypeChange(candidate.originalType)
                                            } else {
                                                val alt = allParsedFamilies.firstOrNull { it.material == filterMat && it.armourGroup == arm }
                                                if (alt != null) {
                                                    filterCores = alt.coresConfig
                                                    onCableTypeChange(alt.originalType)
                                                }
                                            }
                                        }
                                    },
                                    label = { Text(if (isAvail) arm else "$arm (N/A)", fontSize = 11.sp) },
                                    enabled = isAvail,
                                    modifier = Modifier.weight(1f).testTag("filter_armour_$arm")
                                )
                            }
                        }

                        val availableCores = remember(filterMat, filterArmour, allParsedFamilies) {
                            allParsedFamilies.filter { it.material == filterMat && it.armourGroup == filterArmour }
                                .map { it.coresConfig }.distinct()
                        }

                        DropdownSelector(
                            label = "Core Configuration",
                            selected = when (filterCores) {
                                "1C" -> "1C — Single Core"
                                "2C" -> "2C — Single Phase"
                                "3C" -> "3C — Three Phase, No Neutral"
                                "3.5C" -> "3.5C — Three Phase + Reduced Neutral"
                                "4C" -> "4C — Three Phase + Full Neutral"
                                else -> filterCores
                            },
                            options = availableCores,
                            onSelect = { core ->
                                filterCores = core
                                val cand = allParsedFamilies.firstOrNull { it.material == filterMat && it.armourGroup == filterArmour && it.coresConfig == core }
                                if (cand != null) {
                                    onCableTypeChange(cand.originalType)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("input_core_config")
                        )

                        val matchingFamilies = remember(filterMat, filterArmour, filterCores, filterQuery, allParsedFamilies) {
                            allParsedFamilies.filter {
                                it.material == filterMat &&
                                it.armourGroup == filterArmour &&
                                it.coresConfig == filterCores &&
                                (filterQuery.isBlank() || it.originalType.contains(filterQuery, ignoreCase = true) || it.displayLabel.contains(filterQuery, ignoreCase = true))
                            }
                        }

                        val currentParsed = remember(uiState.cableType) { parseFamilyInfo(uiState.cableType) }
                        DropdownSelector(
                            label = "Selected Cable Family",
                            selected = currentParsed.displayLabel,
                            options = matchingFamilies.map { it.originalType },
                            onSelect = { selectedType ->
                                onCableTypeChange(selectedType)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("input_cable_type")
                        )

                        DropdownSelector(
                            label = "Installation Method",
                            selected = uiState.installation,
                            options = uiState.availableInstallations,
                            onSelect = onInstallationChange,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 3. Environmental & Derating Factors
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ThemeSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ThemeBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = ThemePrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("3. Environmental Derating", fontWeight = FontWeight.Bold, color = ThemeTextPrimary, fontSize = 15.sp)
                        }

                        CableInputFieldRow(
                            first = { fieldModifier ->
                                OutlinedTextField(
                                    value = uiState.airTempText,
                                    onValueChange = onAirTempChange,
                                    label = { Text("Air Temp (°C)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                                    modifier = fieldModifier
                                )
                            },
                            second = { fieldModifier ->
                                OutlinedTextField(
                                    value = uiState.gndTempText,
                                    onValueChange = onGndTempChange,
                                    label = { Text("Ground Temp (°C)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                                    modifier = fieldModifier
                                )
                            }
                        )

                        CableInputFieldRow(
                            first = { fieldModifier ->
                                OutlinedTextField(
                                    value = uiState.burialDepthText,
                                    onValueChange = onBurialDepthChange,
                                    label = { Text("Burial Depth (m)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                                    modifier = fieldModifier
                                )
                            },
                            second = { fieldModifier ->
                                OutlinedTextField(
                                    value = uiState.soilResText,
                                    onValueChange = onSoilResChange,
                                    label = { Text("Soil Res (K·m/W)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                                    modifier = fieldModifier
                                )
                            }
                        )

                        CableInputFieldRow(
                            first = { fieldModifier ->
                                OutlinedTextField(
                                    value = uiState.noCircuitsText,
                                    onValueChange = onNoCircuitsChange,
                                    label = { Text("Grouped Circuits") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                    modifier = fieldModifier
                                )
                            },
                            second = { fieldModifier ->
                                DropdownSelector(
                                    label = "Formation",
                                    selected = uiState.formation,
                                    options = uiState.availableFormations,
                                    onSelect = onFormationChange,
                                    modifier = fieldModifier
                                )
                            }
                        )
                    }
                }
            }

            // 4. Manual Overrides (Catalog-Driven Selectors, Expandable)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ThemeSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ThemeBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleOverrides() },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = ThemePrimary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("4. Manual Overrides (Optional Selectors)", fontWeight = FontWeight.Bold, color = ThemeTextPrimary, fontSize = 15.sp)
                            }
                            Icon(
                                imageVector = if (uiState.showOverrides) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = ThemeTextSecondary
                            )
                        }

                        AnimatedVisibility(
                            visible = uiState.showOverrides,
                            enter = VoltAtlasMotion.expandableEnter(),
                            exit = VoltAtlasMotion.expandableExit()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                CableInputFieldRow(
                                    first = { fieldModifier ->
                                        DropdownSelector(
                                            label = "Override Breaker Type",
                                            selected = uiState.overrideDeviceType,
                                            options = uiState.availableBreakerTypes,
                                            onSelect = onOverrideDeviceTypeChange,
                                            modifier = fieldModifier.testTag("override_breaker_type_dropdown")
                                        )
                                    },
                                    second = { fieldModifier ->
                                        DropdownSelector(
                                            label = "Override Rating (A)",
                                            selected = uiState.overrideDeviceRating,
                                            options = uiState.availableRatings,
                                            onSelect = onOverrideDeviceRatingChange,
                                            modifier = fieldModifier.testTag("override_breaker_rating_dropdown")
                                        )
                                    }
                                )

                                DropdownSelector(
                                    label = "Override Cable Size (Cross-Section)",
                                    selected = uiState.overrideCableSize,
                                    options = uiState.availableCableSizes,
                                    onSelect = onOverrideCableSizeChange,
                                    modifier = Modifier.fillMaxWidth().testTag("override_cable_size_dropdown")
                                )

                                CableInputFieldRow(
                                    first = { fieldModifier ->
                                        DropdownSelector(
                                            label = "Override Neutral",
                                            selected = uiState.overrideNeutralSize,
                                            options = uiState.availableConductorSizes,
                                            onSelect = onOverrideNeutralSizeChange,
                                            modifier = fieldModifier.testTag("override_neutral_dropdown")
                                        )
                                    },
                                    second = { fieldModifier ->
                                        DropdownSelector(
                                            label = "Override Earth/CPC",
                                            selected = uiState.overrideEarthSize,
                                            options = uiState.availableConductorSizes,
                                            onSelect = onOverrideEarthSizeChange,
                                            modifier = fieldModifier.testTag("override_earth_dropdown")
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 5. Calculate Button Action
            item {
                Button(
                    onClick = onCalculate,
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
                            Icon(Icons.Default.Calculate, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Calculate Cable Sizing", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }

            // 6. Result Card Placed Below Calculate Button (Auto-scrolled on calculate)
            if (uiState.calculationResult != null && uiState.calculatedCircuit != null) {
                item {
                    QuickCalcResultCard(
                        result = uiState.calculationResult,
                        circuit = uiState.calculatedCircuit,
                        onCopy = onCopySummary
                    )
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
                colors = CardDefaults.cardColors(containerColor = ThemeSurfaceElevated),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Voltage Drop (ΔV)", color = ThemeTextSecondary, fontSize = 12.sp)
                        Text(
                            text = "${String.format(Locale.US, "%.2f", result.voltDrop_V)} V  (${String.format(Locale.US, "%.2f", result.vdPct)}%)",
                            fontWeight = FontWeight.Bold,
                            color = if (result.vdPct <= circuit.maxVDpct * 100.0) SleekPass else SleekFail,
                            fontSize = 15.sp
                        )
                    }

                    Text(
                        text = "Limit: ${String.format(Locale.US, "%.1f", circuit.maxVDpct * 100.0)}%",
                        color = ThemeTextSecondary,
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
                Text("Total Derating Factor (C_total):", color = ThemeTextSecondary, fontSize = 12.sp)
                Text(
                    text = String.format(Locale.US, "%.3f", result.cTotalDerating),
                    fontWeight = FontWeight.Bold,
                    color = ThemeTextPrimary,
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
        colors = CardDefaults.cardColors(containerColor = ThemeSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ThemeBorderSubtle),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = ThemeTextSecondary, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, color = accentColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = ThemeTextSecondary, fontSize = 11.sp)
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
        colors = CardDefaults.cardColors(containerColor = ThemeSurface),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                title,
                color = ThemeTextSecondary,
                fontSize = 10.sp,
                // Up to four of these boxes share one row; the title must wrap
                // (and only then ellipsize) instead of hard-clipping mid-word
                // on narrow phones or at enlarged font scale.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, color = ThemeTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PREVIEWS
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Quick Cable Calc Screen - Phone", showBackground = true)
@Composable
private fun PreviewQuickCableCalcContentPhone() {
    VoltAtlasTheme(darkTheme = true) {
        QuickCableCalcContent(
            uiState = CableCalcScreenUiState(
                loadKvaText = "50.0",
                voltageText = "400",
                phases = "3P+N",
                pfText = "0.80",
                safetyFactorText = "1.00",
                lengthText = "50.0",
                maxVdPctText = "3.0",
                parallelRunsText = "1",
                cableType = "Cu/XLPE/PVC - 4C",
                manufacturer = "Elsewedy"
            ),
            onLoadKvaChange = {},
            onVoltageChange = {},
            onPhasesChange = {},
            onPfChange = {},
            onSafetyFactorChange = {},
            onLengthChange = {},
            onMaxVdPctChange = {},
            onParallelRunsChange = {},
            onManufacturerChange = {},
            onCableTypeChange = {},
            onInstallationChange = {},
            onAirTempChange = {},
            onGndTempChange = {},
            onBurialDepthChange = {},
            onSoilResChange = {},
            onNoCircuitsChange = {},
            onFormationChange = {},
            onSpacingChange = {},
            onToggleOverrides = {},
            onOverrideDeviceTypeChange = {},
            onOverrideDeviceRatingChange = {},
            onOverrideCableSizeChange = {},
            onOverrideNeutralSizeChange = {},
            onOverrideEarthSizeChange = {},
            onCalculate = {},
            onBack = {},
            onCopySummary = {}
        )
    }
}

