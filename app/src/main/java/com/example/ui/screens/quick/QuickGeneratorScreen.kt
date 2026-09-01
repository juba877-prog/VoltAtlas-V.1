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
fun QuickGeneratorScreen(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    // State declarations
    var continuousLoadKwText by rememberSaveable { mutableStateOf("120.0") }
    var powerFactorText by rememberSaveable { mutableStateOf("0.80") }
    var demandFactorText by rememberSaveable { mutableStateOf("1.0") }
    var largestMotorKwText by rememberSaveable { mutableStateOf("30.0") }
    var startMethodIndex by rememberSaveable { mutableIntStateOf(0) } // 0=DOL(6x), 1=SD(2.5x), 2=SoftStart(1.8x), 3=VFD(1.2x)
    var maxDipPctText by rememberSaveable { mutableStateOf("15.0") }
    var xdPctText by rememberSaveable { mutableStateOf("15.0") }

    val startMethod = MotorStartMethod.entries[startMethodIndex.coerceIn(0, MotorStartMethod.entries.size - 1)]

    val input = remember(
        continuousLoadKwText, powerFactorText, demandFactorText, largestMotorKwText,
        startMethod, maxDipPctText, xdPctText
    ) {
        GeneratorQuickInput(
            continuousLoadKW = continuousLoadKwText.toDoubleOrNull() ?: 120.0,
            powerFactor = powerFactorText.toDoubleOrNull() ?: 0.80,
            demandFactor = demandFactorText.toDoubleOrNull() ?: 1.0,
            largestMotorKW = largestMotorKwText.toDoubleOrNull() ?: 30.0,
            motorStartMethod = startMethod,
            maxPermittedVoltageDipPct = maxDipPctText.toDoubleOrNull() ?: 15.0,
            subtransientReactanceXdPct = xdPctText.toDoubleOrNull() ?: 15.0
        )
    }

    val result = remember(input) {
        QuickGeneratorEngine.calculate(input)
    }

    val copySummaryAction: () -> Unit = {
        val summary = """
            ⚡ VoltAtlas Generator Quick Sizing & Voltage Dip Summary (ISO 8528)
            • Continuous Essential Load: ${input.continuousLoadKW} kW (${result.continuousLoadKVA} kVA @ PF ${input.powerFactor})
            • Largest Motor: ${input.largestMotorKW} kW (${startMethod.label})
            • Motor Starting Demand: ${result.motorStartingKVA} kVA
            • Recommended Generator Rating: ${result.recommendedSizeKVA.toInt()} kVA (Prime / PRP)
            • Continuous Loading: ${result.continuousLoadingPercent}% (${if (result.isPrimeLoadingOk) "Optimal ≤80%" else "High >80%"})
            • Estimated Transient Voltage Dip: ${result.estimatedVoltageDipPercent}% (${if (result.isVoltageDipOk) "PASS ≤${input.maxPermittedVoltageDipPct}%" else "EXCEEDS LIMIT"})
            • Subtransient Reactance (X"d): ${input.subtransientReactanceXdPct}%
        """.trimIndent()

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Generator Sizing Summary", summary))
        Toast.makeText(context, "Generator summary copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Generator Quick Sizing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ThemeTextColor
                        )
                        Text(
                            text = "ISO 8528-1 • ISO 8528-5 • Motor Starting Voltage Dip",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateTextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("generator_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ThemeTextColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = copySummaryAction, modifier = Modifier.testTag("generator_copy_btn")) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // ── 1. Steady-State Load Parameters ──
            item {
                QuickCard {
                    QuickSectionTitle(title = "Continuous Steady-State Load", accentColor = Color(0xFF06B6D4), badgeText = "ISO 8528", badgeColor = Color(0xFF06B6D4))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = continuousLoadKwText,
                            onValueChange = { continuousLoadKwText = it },
                            label = { Text("Continuous Load (kW)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = powerFactorText,
                            onValueChange = { powerFactorText = it },
                            label = { Text("Power Factor (cos φ)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }

            // ── 2. Motor Starting & Alternator Characteristics ──
            item {
                QuickCard {
                    QuickSectionTitle(title = "Motor Starting & Alternator Dynamics", accentColor = Color(0xFF06B6D4))

                    OutlinedTextField(
                        value = largestMotorKwText,
                        onValueChange = { largestMotorKwText = it },
                        label = { Text("Largest Motor Rating (kW)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text("Motor Starting Method:", color = SlateTextSecondary, fontSize = 12.sp)
                    QuickSegmentedControl(
                        options = listOf("DOL (6.0×)", "Y-Δ (2.5×)", "Soft-Start (1.8×)", "VFD (1.2×)"),
                        selectedIndex = startMethodIndex,
                        onSelectIndex = { startMethodIndex = it },
                        activeColor = Color(0xFF06B6D4)
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = maxDipPctText,
                            onValueChange = { maxDipPctText = it },
                            label = { Text("Max Voltage Dip (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = xdPctText,
                            onValueChange = { xdPctText = it },
                            label = { Text("Alternator X\"d (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }

            // ── 3. Status Banner ──
            item {
                val bannerStatus = if (result.isPrimeLoadingOk && result.isVoltageDipOk) VoltageDropComplianceStatus.PASS else if (!result.isPrimeLoadingOk) VoltageDropComplianceStatus.FAIL else VoltageDropComplianceStatus.WARNING
                val headline = if (result.isPrimeLoadingOk && result.isVoltageDipOk) "Recommended: ${result.recommendedSizeKVA.toInt()} kVA (Loading: ${result.continuousLoadingPercent}%)" else "Generator Sizing Verification Alert"
                val subText = "Transient Voltage Dip: ${result.estimatedVoltageDipPercent}% (Limit: ≤${input.maxPermittedVoltageDipPct}%) • Motor Starting: ${result.motorStartingKVA} kVA"
                QuickStatusBanner(status = bannerStatus, headline = headline, subText = subText)
            }

            // ── 4. Metric Hero Grid ──
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickMetricHero(
                            title = "GENERATOR RATING",
                            value = "${result.recommendedSizeKVA.toInt()} kVA",
                            subtitle = "Continuous Load: ${result.continuousLoadKVA} kVA",
                            modifier = Modifier.weight(1f),
                            accentColor = SleekPass
                        )
                        QuickMetricHero(
                            title = "PRIME LOADING",
                            value = "${String.format(Locale.US, "%.1f", result.continuousLoadingPercent)}%",
                            subtitle = "ISO 8528 Limit: ≤ 80.0%",
                            modifier = Modifier.weight(1f),
                            accentColor = if (result.isPrimeLoadingOk) SleekPass else SleekFail
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickMetricHero(
                            title = "MOTOR STARTING",
                            value = "${result.motorStartingKVA} kVA",
                            subtitle = startMethod.label,
                            modifier = Modifier.weight(1f),
                            accentColor = Color(0xFF06B6D4)
                        )
                        QuickMetricHero(
                            title = "VOLTAGE DIP",
                            value = "${String.format(Locale.US, "%.1f", result.estimatedVoltageDipPercent)}%",
                            subtitle = "Max Allowed: ≤ ${input.maxPermittedVoltageDipPct}%",
                            modifier = Modifier.weight(1f),
                            accentColor = if (result.isVoltageDipOk) SleekPass else SleekWarning
                        )
                    }
                }
            }

            // ── 5. Calculation Details Accordion ──
            item {
                QuickCalculationDetailsAccordion(
                    formula = result.formulaSizing,
                    substitution = result.substitutionSizing
                )
            }

            // ── 6. Provenance Footer ──
            item {
                QuickProvenanceFooter(
                    rColumn = "Alternator Subtransient Reactance X\"d = ${input.subtransientReactanceXdPct}%",
                    resolvedR = result.continuousLoadKVA,
                    resolvedX = result.motorStartingKVA,
                    baseAmpacityA = null,
                    dataSource = "ISO 8528-1/5 Prime Power Ratings & IEC 60034-1",
                    citation = "ISO 8528-1 Reciprocating internal combustion engine driven alternating current generating sets & ISO 8528-5."
                )
            }
        }
    }
}
