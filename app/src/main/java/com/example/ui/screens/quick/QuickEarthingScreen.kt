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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEarthingScreen(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    // State declarations
    var soilTypeIndex by rememberSaveable { mutableIntStateOf(1) } // 0=Marshy, 1=Clay(50), 2=Sand(250), 3=Rock(1000), 4=Custom
    var customSoilResText by rememberSaveable { mutableStateOf("50.0") }
    var targetRText by rememberSaveable { mutableStateOf("2.0") }
    var faultKaText by rememberSaveable { mutableStateOf("16.0") }
    var clearingTimeText by rememberSaveable { mutableStateOf("0.4") }
    var materialIndex by rememberSaveable { mutableIntStateOf(0) } // 0=Bare Cu(159), 1=Cu/XLPE(176), 2=Cu/PVC(143), 3=Al/XLPE(118), 4=Al/PVC(95), 5=Steel(58)
    var rodLengthText by rememberSaveable { mutableStateOf("3.0") }
    var rodDiameterText by rememberSaveable { mutableStateOf("17.0") }
    var rodSpacingText by rememberSaveable { mutableStateOf("5.0") }

    val soilType = EarthSoilType.entries[soilTypeIndex.coerceIn(0, EarthSoilType.entries.size - 1)]
    val material = EarthConductorMaterial.entries[materialIndex.coerceIn(0, EarthConductorMaterial.entries.size - 1)]

    val soilResValue = if (soilType == EarthSoilType.CUSTOM) {
        customSoilResText.toDoubleOrNull() ?: 50.0
    } else {
        soilType.defaultResistivity
    }

    val input = remember(
        soilResValue, targetRText, faultKaText, clearingTimeText, material,
        rodLengthText, rodDiameterText, rodSpacingText
    ) {
        EarthingQuickInput(
            soilResistivity_ohmM = soilResValue,
            targetResistance_ohm = targetRText.toDoubleOrNull() ?: 2.0,
            faultCurrent_kA = faultKaText.toDoubleOrNull() ?: 16.0,
            clearingTime_s = clearingTimeText.toDoubleOrNull() ?: 0.4,
            kFactor = material.kFactor,
            rodLength_m = rodLengthText.toDoubleOrNull() ?: 3.0,
            rodDiameter_mm = rodDiameterText.toDoubleOrNull() ?: 17.0,
            rodSpacing_m = rodSpacingText.toDoubleOrNull() ?: 5.0
        )
    }

    val result = remember(input) {
        QuickEarthingEngine.calculate(input)
    }

    val copySummaryAction: () -> Unit = {
        val summary = """
            ⚡ VoltAtlas Earthing & Grounding Summary (IEC 60364-5-54 / BS 7430)
            • Soil Resistivity: ${input.soilResistivity_ohmM} Ω·m (${soilType.label})
            • Target Resistance: ≤ ${input.targetResistance_ohm} Ω
            • Single Rod Resistance (R₁): ${result.singleRodResistance_ohm} Ω
            • Required Earth Pits / Rods: ${result.requiredRods} (Eff: ${(result.groupEfficiency * 100).toInt()}%)
            • Achieved Grid Resistance: ${result.achievedResistance_ohm} Ω (${if (result.isResistanceOk) "PASS" else "FAIL"})
            • Earth Fault Current: ${input.faultCurrent_kA} kA @ ${input.clearingTime_s} s
            • Protective Earth Conductor: ${result.standardConductorMm2.toInt()} mm² (Calculated: ${result.exactConductorMm2} mm², k=${material.kFactor.toInt()})
            • Compliance: ${if (result.isResistanceOk) "COMPLIANT" else "NON-COMPLIANT"}
        """.trimIndent()

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Earthing Sizing Summary", summary))
        Toast.makeText(context, "Earthing summary copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Earthing & Grounding",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ThemeTextColor
                        )
                        Text(
                            text = "IEC 60364-5-54 • BS 7430 Dwight Grid",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateTextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("earthing_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ThemeTextColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = copySummaryAction, modifier = Modifier.testTag("earthing_copy_btn")) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Summary",
                            tint = SleekPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 36.dp)
        ) {
            // ── 1. Soil & Target Card ──
            item {
                QuickCard {
                    QuickSectionTitle(title = "Soil & Target Resistance", accentColor = SleekPrimary, badgeText = "IEC 60364-5-54", badgeColor = SleekPrimary)

                    Text("Soil Classification & Resistivity (ρ):", color = SlateTextSecondary, fontSize = 12.sp)
                    QuickSegmentedControl(
                        options = listOf("Clay (50)", "Sand (250)", "Rock (1000)", "Custom"),
                        selectedIndex = when (soilTypeIndex) { 1 -> 0; 2 -> 1; 3 -> 2; else -> 3 },
                        onSelectIndex = { idx ->
                            soilTypeIndex = when (idx) { 0 -> 1; 1 -> 2; 2 -> 3; else -> 4 }
                        },
                        activeColor = SleekPrimary
                    )

                    if (soilType == EarthSoilType.CUSTOM) {
                        OutlinedTextField(
                            value = customSoilResText,
                            onValueChange = { customSoilResText = it },
                            label = { Text("Custom Soil Resistivity (Ω·m)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = targetRText,
                            onValueChange = { targetRText = it },
                            label = { Text("Target R (Ω)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = faultKaText,
                            onValueChange = { faultKaText = it },
                            label = { Text("Fault Current Ik (kA)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }

            // ── 2. Conductor Material & Rod Geometry ──
            item {
                QuickCard {
                    QuickSectionTitle(title = "Conductor & Rod Configuration", accentColor = SleekPrimary)

                    Text("Protective Conductor Insulation / k-factor:", color = SlateTextSecondary, fontSize = 12.sp)
                    QuickSegmentedControl(
                        options = listOf("Cu Bare (159)", "Cu/XLPE (176)", "Cu/PVC (143)", "Steel (58)"),
                        selectedIndex = when (materialIndex) { 0 -> 0; 1 -> 1; 2 -> 2; else -> 3 },
                        onSelectIndex = { idx ->
                            materialIndex = when (idx) { 0 -> 0; 1 -> 1; 2 -> 2; else -> 5 }
                        },
                        activeColor = SleekPrimary
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = clearingTimeText,
                            onValueChange = { clearingTimeText = it },
                            label = { Text("Fault Duration t (s)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = rodLengthText,
                            onValueChange = { rodLengthText = it },
                            label = { Text("Rod Length L (m)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = rodDiameterText,
                            onValueChange = { rodDiameterText = it },
                            label = { Text("Rod Diameter d (mm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = rodSpacingText,
                            onValueChange = { rodSpacingText = it },
                            label = { Text("Rod Spacing s (m)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }

            // ── 3. Status Banner ──
            item {
                val bannerStatus = if (result.isResistanceOk) VoltageDropComplianceStatus.PASS else VoltageDropComplianceStatus.FAIL
                val headline = if (result.isResistanceOk) "Achieved Target Grid Resistance (${result.achievedResistance_ohm} Ω ≤ ${result.targetResistance_ohm} Ω)" else "Grid Resistance Exceeds Target (${result.achievedResistance_ohm} Ω > ${result.targetResistance_ohm} Ω)"
                val subText = "${result.requiredRods} rods required in array (BS 7430 η = ${(result.groupEfficiency * 100).toInt()}%) • Earth Conductor ${result.standardConductorMm2.toInt()} mm²"
                QuickStatusBanner(status = bannerStatus, headline = headline, subText = subText)
            }

            // ── 4. Metric Hero Grid ──
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickMetricHero(
                            title = "ACHIEVED RESISTANCE",
                            value = "${String.format(Locale.US, "%.2f", result.achievedResistance_ohm)} Ω",
                            subtitle = "Target: ≤ ${result.targetResistance_ohm} Ω",
                            modifier = Modifier.weight(1f),
                            accentColor = if (result.isResistanceOk) SleekPass else SleekFail
                        )
                        QuickMetricHero(
                            title = "REQUIRED EARTH RODS",
                            value = "${result.requiredRods} Pits",
                            subtitle = "Single Rod R₁ = ${String.format(Locale.US, "%.2f", result.singleRodResistance_ohm)} Ω",
                            modifier = Modifier.weight(1f),
                            accentColor = ThemeTextColor
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickMetricHero(
                            title = "EARTH CONDUCTOR",
                            value = "${result.standardConductorMm2.toInt()} mm²",
                            subtitle = "Adiabatic: ${String.format(Locale.US, "%.2f", result.exactConductorMm2)} mm²",
                            modifier = Modifier.weight(1f),
                            accentColor = SleekPrimary
                        )
                        QuickMetricHero(
                            title = "GROUP EFFICIENCY",
                            value = "${(result.groupEfficiency * 100).toInt()}%",
                            subtitle = "Spacing s/L = ${String.format(Locale.US, "%.2f", input.rodSpacing_m / input.rodLength_m)}",
                            modifier = Modifier.weight(1f),
                            accentColor = SleekPass
                        )
                    }
                }
            }

            // ── 5. Calculation Details Accordion ──
            item {
                QuickCalculationDetailsAccordion(
                    formula = "${result.formulaDwight}\n${result.formulaAdiabatic}",
                    substitution = "${result.substitutionDwight}\n\n${result.substitutionAdiabatic}"
                )
            }

            // ── 6. Provenance Footer ──
            item {
                QuickProvenanceFooter(
                    dataSource = "Dwight (1936) & BS 7430:2011 Table 3",
                    citation = "IEC 60364-5-54 §54.2.2 (Protective earthing conductor sizing) and BS 7430 (Earthing design & rod array efficiency).",
                    items = listOf(
                        ProvenanceDetailItem("Soil Resistivity (ρ):", "${input.soilResistivity_ohmM} Ω·m (${soilType.label})"),
                        ProvenanceDetailItem("Single Rod Resistance (R₁):", "${String.format(Locale.US, "%.2f", result.singleRodResistance_ohm)} Ω"),
                        ProvenanceDetailItem("Array Spacing Ratio (s/L):", "${String.format(Locale.US, "%.2f", input.rodSpacing_m / input.rodLength_m)} (s = ${input.rodSpacing_m}m, L = ${input.rodLength_m}m)"),
                        ProvenanceDetailItem("Array Grouping Efficiency (η):", "${(result.groupEfficiency * 100).toInt()}% (BS 7430 Fig. 3)"),
                        ProvenanceDetailItem("Protective Conductor Material:", "${material.label} (k = ${material.kFactor.toInt()})"),
                        ProvenanceDetailItem("Target Grid Resistance:", "≤ ${input.targetResistance_ohm} Ω", isPrimary = true)
                    )
                )
            }
        }
    }
}
