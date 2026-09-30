package com.example.ui.screens.quick

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickUpsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // State declarations
    var criticalLoadKvaText by rememberSaveable { mutableStateOf("60.0") }
    var powerFactorText by rememberSaveable { mutableStateOf("0.80") }
    var autonomyMinutesText by rememberSaveable { mutableStateOf("30.0") }
    var inverterEfficiencyText by rememberSaveable { mutableStateOf("92.0") }
    var batteryVoltageDcText by rememberSaveable { mutableStateOf("384.0") }
    var dodText by rememberSaveable { mutableStateOf("0.80") }

    val input = remember(
        criticalLoadKvaText, powerFactorText, autonomyMinutesText,
        inverterEfficiencyText, batteryVoltageDcText, dodText
    ) {
        UpsQuickInput(
            criticalLoadKVA = criticalLoadKvaText.toDoubleOrNull() ?: 60.0,
            powerFactor = powerFactorText.toDoubleOrNull() ?: 0.80,
            autonomyMinutes = autonomyMinutesText.toDoubleOrNull() ?: 30.0,
            inverterEfficiencyPct = inverterEfficiencyText.toDoubleOrNull() ?: 92.0,
            batteryVoltageDC = batteryVoltageDcText.toDoubleOrNull() ?: 384.0,
            depthOfDischarge = dodText.toDoubleOrNull() ?: 0.80
        )
    }

    val result = remember(input) {
        QuickUpsEngine.calculate(input)
    }

    val copySummaryAction = remember(input, result, context) {
        {
            val stringWord = if (result.batteryStringsEstimate100Ah == 1) "String" else "Strings"
            val summary = """
                ⚡ VoltAtlas UPS & Battery Sizing Summary (IEC 62040-3 / IEEE 485)
                • Critical Load: ${input.criticalLoadKVA} kVA / ${result.criticalLoadKW} kW (PF ${input.powerFactor})
                • Recommended UPS Frame: ${result.recommendedUpsSizeKVA.toInt()} kVA (Continuous Loading: ${result.upsLoadingPercent}%)
                • DC Inverter Demand: ${result.inverterDcLoadKW} kW (Inverter η: ${input.inverterEfficiencyPct}%)
                • Autonomy Time: ${input.autonomyMinutes} minutes (DoD: ${(input.depthOfDischarge * 100).toInt()}%)
                • Required Battery Energy: ${result.requiredEnergyKWh} kWh
                • Required Battery Capacity: ${result.requiredCapacityAh} Ah @ ${input.batteryVoltageDC.toInt()}V DC
                • Estimated Battery Strings: ${result.batteryStringsEstimate100Ah}x 100Ah $stringWord
            """.trimIndent()

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("UPS Sizing Summary", summary))
            Toast.makeText(context, "UPS summary copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    val stringWord = if (result.batteryStringsEstimate100Ah == 1) "String" else "Strings"
    val provenanceItems = remember(input, result) {
        persistentListOf(
            ProvenanceDetailItem("Critical Load Demand:", "${input.criticalLoadKVA} kVA / ${result.criticalLoadKW} kW (PF ${input.powerFactor})"),
            ProvenanceDetailItem("Inverter Efficiency (η):", "${input.inverterEfficiencyPct}% (DC Input: ${String.format(Locale.US, "%.1f", result.inverterDcLoadKW)} kW)"),
            ProvenanceDetailItem("Battery DC Bus Voltage:", "${input.batteryVoltageDC.toInt()} V DC"),
            ProvenanceDetailItem("Depth of Discharge (DoD):", "${(input.depthOfDischarge * 100).toInt()}% (Design Margin: 1.25 Aging)"),
            ProvenanceDetailItem("Autonomy Duration:", "${input.autonomyMinutes.toInt()} minutes @ full rated load"),
            ProvenanceDetailItem("Required Stored Energy:", "${String.format(Locale.US, "%.1f", result.requiredEnergyKWh)} kWh (${result.batteryStringsEstimate100Ah} $stringWord @ 100Ah)", isPrimary = true)
        )
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "UPS & Battery Sizing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ThemeTextColor
                        )
                        Text(
                            text = "IEC 62040-3 • IEEE 485 Battery Autonomy",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateTextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("ups_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ThemeTextColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = copySummaryAction, modifier = Modifier.testTag("ups_copy_btn")) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Summary",
                            tint = SleekPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
                scrollBehavior = scrollBehavior
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
            // ── 1. Critical Load Parameters ──
            item {
                QuickCard {
                    QuickSectionTitle(title = "Critical Load & Power Demand", accentColor = SleekPrimary, badgeText = "IEC 62040", badgeColor = SleekPrimary)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = criticalLoadKvaText,
                            onValueChange = { criticalLoadKvaText = it },
                            label = { Text("Critical Load (kVA)") },
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

            // ── 2. Battery & Inverter Specifications ──
            item {
                QuickCard {
                    QuickSectionTitle(title = "Autonomy & Battery DC Bus", accentColor = SleekPrimary)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = autonomyMinutesText,
                            onValueChange = { autonomyMinutesText = it },
                            label = { Text("Autonomy Time (min)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = inverterEfficiencyText,
                            onValueChange = { inverterEfficiencyText = it },
                            label = { Text("Inverter Efficiency (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = batteryVoltageDcText,
                            onValueChange = { batteryVoltageDcText = it },
                            label = { Text("Battery Bus Voltage (V DC)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = dodText,
                            onValueChange = { dodText = it },
                            label = { Text("Depth of Discharge (DoD)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }

            // ── 3. Status Banner ──
            item {
                val bannerWord = if (result.batteryStringsEstimate100Ah == 1) "string" else "strings"
                val bannerStatus = VoltageDropComplianceStatus.PASS
                val headline = "Recommended UPS Size: ${result.recommendedUpsSizeKVA.toInt()} kVA (Loading: ${result.upsLoadingPercent}%)"
                val subText = "Required Battery: ${result.requiredCapacityAh} Ah @ ${input.batteryVoltageDC.toInt()}V DC (${result.batteryStringsEstimate100Ah}x 100Ah $bannerWord) • Stored Energy: ${result.requiredEnergyKWh} kWh"
                QuickStatusBanner(status = bannerStatus, headline = headline, subText = subText)
            }

            // ── 4. Metric Hero Grid ──
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickMetricHero(
                            title = "RECOMMENDED UPS",
                            value = "${result.recommendedUpsSizeKVA.toInt()} kVA",
                            subtitle = "Load: ${result.criticalLoadKVA} kVA (${result.criticalLoadKW} kW)",
                            modifier = Modifier.weight(1f),
                            accentColor = SleekPass
                        )
                        QuickMetricHero(
                            title = "BATTERY CAPACITY",
                            value = "${String.format(Locale.US, "%.1f", result.requiredCapacityAh)} Ah",
                            subtitle = "@ ${input.batteryVoltageDC.toInt()}V DC bus",
                            modifier = Modifier.weight(1f),
                            accentColor = SleekPrimary
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickMetricHero(
                            title = "STORED ENERGY",
                            value = "${String.format(Locale.US, "%.1f", result.requiredEnergyKWh)} kWh",
                            subtitle = "Autonomy: ${input.autonomyMinutes.toInt()} min @ full load",
                            modifier = Modifier.weight(1f),
                            accentColor = SleekPrimary
                        )
                        QuickMetricHero(
                            title = "BATTERY STRINGS",
                            value = "${result.batteryStringsEstimate100Ah} $stringWord",
                            subtitle = "100Ah monobloc basis",
                            modifier = Modifier.weight(1f),
                            accentColor = ThemeTextColor
                        )
                    }
                }
            }

            // ── 5. Calculation Details Accordion ──
            item {
                QuickCalculationDetailsAccordion(
                    formula = result.formulaUps,
                    substitution = result.substitutionUps
                )
            }

            // ── 6. Provenance Footer ──
            item {
                QuickProvenanceFooter(
                    dataSource = "IEC 62040-3 & IEEE 485 Battery Sizing Method",
                    citation = "IEC 62040-3 Uninterruptible power systems (UPS) & IEEE 485 Recommended Practice for Sizing Lead-Acid Batteries.",
                    items = provenanceItems
                )
            }
        }
    }
}
