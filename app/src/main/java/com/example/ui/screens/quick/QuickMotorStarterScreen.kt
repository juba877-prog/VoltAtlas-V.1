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

private val PHASE_OPTIONS = persistentListOf("3-Phase (400V)", "1-Phase (230V)")
private val STARTER_METHOD_OPTIONS = persistentListOf("DOL (6.5×)", "Y-Δ (2.2×)", "Soft (2.5×)", "VFD (1.15×)")
private val TRIP_CLASS_OPTIONS = persistentListOf("Class 10 (Standard <10s)", "Class 20 (Heavy <20s)")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMotorStarterScreen(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    // State declarations
    var motorKwText by rememberSaveable { mutableStateOf("15.0") }
    var voltageText by rememberSaveable { mutableStateOf("400") }
    var phaseIndex by rememberSaveable { mutableIntStateOf(0) } // 0=3-Phase, 1=1-Phase
    var powerFactorText by rememberSaveable { mutableStateOf("0.85") }
    var efficiencyText by rememberSaveable { mutableStateOf("90.0") }
    var starterMethodIndex by rememberSaveable { mutableIntStateOf(0) } // 0=DOL, 1=Y-Δ, 2=Soft, 3=VFD
    var tripClassIndex by rememberSaveable { mutableIntStateOf(0) } // 0=Class 10, 1=Class 20

    val starterMethod = MotorStarterType.entries[starterMethodIndex.coerceIn(0, MotorStarterType.entries.size - 1)]
    val tripClass = if (tripClassIndex == 0) "Class 10" else "Class 20"

    val input = remember(
        motorKwText, voltageText, phaseIndex, powerFactorText, efficiencyText,
        starterMethod, tripClass
    ) {
        MotorStarterQuickInput(
            motorPowerKW = motorKwText.toDoubleOrNull() ?: 15.0,
            voltageV = voltageText.toDoubleOrNull() ?: 400.0,
            isThreePhase = phaseIndex == 0,
            powerFactor = powerFactorText.toDoubleOrNull() ?: 0.85,
            efficiencyPct = efficiencyText.toDoubleOrNull() ?: 90.0,
            startingMethod = starterMethod,
            tripClass = tripClass
        )
    }

    val result = remember(input) {
        QuickMotorStarterEngine.calculate(input)
    }

    val copySummaryAction = remember(input, result, starterMethod, context) {
        {
            val contactorStr = if (result.deltaContactorKm2_A != null && result.starContactorKm3_A != null) {
                "KM1 (Line): ${result.mainContactorKm1_A.toInt()}A, KM2 (Delta): ${result.deltaContactorKm2_A.toInt()}A, KM3 (Star): ${result.starContactorKm3_A.toInt()}A"
            } else {
                "KM1 (Main): ${result.mainContactorKm1_A.toInt()}A"
            }

            val summary = """
                ⚡ VoltAtlas Motor Starter & Overload Sizing Summary (IEC 60947-4-1)
                • Motor Rating: ${input.motorPowerKW} kW @ ${input.voltageV.toInt()}V (${if (input.isThreePhase) "3-Phase" else "1-Phase"})
                • Full Load Current (In): ${result.fullLoadCurrent_A} A (PF ${input.powerFactor}, η ${input.efficiencyPct}%)
                • Starting Method: ${starterMethod.label}
                • Starting Inrush Current (Istart): ${result.startingCurrent_A} A (${String.format(Locale.US, "%.2f", starterMethod.startMultiplier)}× In)
                • Contactor Sizing (AC-3): $contactorStr
                • Thermal Overload Relay (TOR): ${result.torSettingCurrent_A} A [Range: ${result.torDialMin_A} A – ${result.torDialMax_A} A, ${result.tripClass}] (${result.torLocation})
                • Motor Circuit Breaker (MPCB): ${result.recommendedMpcb_A.toInt()} A
                • Backup Fuse: aM ${result.recommendedAmFuse_A.toInt()} A / gG ${result.recommendedGgFuse_A.toInt()} A
                • Min Feeder Cable (Iz ≥ 1.25·In): ${result.minCableAmpacity_A} A
            """.trimIndent()

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Motor Starter Summary", summary))
            Toast.makeText(context, "Motor starter summary copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    val provenanceItems = remember(input, result, starterMethod) {
        persistentListOf(
            ProvenanceDetailItem("Motor Nameplate Power:", "${input.motorPowerKW} kW @ ${input.voltageV.toInt()}V (${if (input.isThreePhase) "3-Phase" else "1-Phase"})"),
            ProvenanceDetailItem("Efficiency & Power Factor:", "η = ${input.efficiencyPct}%, cos φ = ${input.powerFactor}"),
            ProvenanceDetailItem("Starting Method & Inrush:", "${starterMethod.label} (${String.format(Locale.US, "%.2f", starterMethod.startMultiplier)}× In = ${result.startingCurrent_A} A)"),
            ProvenanceDetailItem("TOR Trip Class & Location:", "${result.tripClass} • ${result.torLocation}"),
            ProvenanceDetailItem("Backup Short-Circuit Sizing:", "MPCB ${result.recommendedMpcb_A.toInt()}A | aM ${result.recommendedAmFuse_A.toInt()}A | gG ${result.recommendedGgFuse_A.toInt()}A"),
            ProvenanceDetailItem("Min Feeder Cable Ampacity:", "Iz ≥ ${result.minCableAmpacity_A} A (125% Rule)", isPrimary = true)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Motor Starter & Overload",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ThemeTextColor
                        )
                        Text(
                            text = "IEC 60947-4-1 • IEC 60034-1 • AC-3 Contactors",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateTextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("motor_starter_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ThemeTextColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = copySummaryAction, modifier = Modifier.testTag("motor_starter_copy_btn")) {
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
            // ── 1. Motor Nameplate Specifications ──
            item {
                QuickCard {
                    QuickSectionTitle(title = "Motor Nameplate Specifications", accentColor = SleekPrimary, badgeText = "IEC 60034", badgeColor = SleekPrimary)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = motorKwText,
                            onValueChange = { motorKwText = it },
                            label = { Text("Motor Rating (kW)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = voltageText,
                            onValueChange = { voltageText = it },
                            label = { Text("Line Voltage (V)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Text("System Phase Configuration:", color = SlateTextSecondary, fontSize = 12.sp)
                    QuickSegmentedControl(
                        options = PHASE_OPTIONS,
                        selectedIndex = phaseIndex,
                        onSelectIndex = { phaseIndex = it },
                        activeColor = SleekPrimary
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = powerFactorText,
                            onValueChange = { powerFactorText = it },
                            label = { Text("Power Factor (cos φ)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = efficiencyText,
                            onValueChange = { efficiencyText = it },
                            label = { Text("Efficiency η (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }

            // ── 2. Starter & Overload Technology ──
            item {
                QuickCard {
                    QuickSectionTitle(title = "Starter Method & Protection Class", accentColor = SleekPrimary, badgeText = "IEC 60947-4-1", badgeColor = SleekPrimary)

                    Text("Starting Method:", color = SlateTextSecondary, fontSize = 12.sp)
                    QuickSegmentedControl(
                        options = STARTER_METHOD_OPTIONS,
                        selectedIndex = starterMethodIndex,
                        onSelectIndex = { starterMethodIndex = it },
                        activeColor = SleekPrimary
                    )

                    Text("Thermal Overload Trip Class:", color = SlateTextSecondary, fontSize = 12.sp)
                    QuickSegmentedControl(
                        options = TRIP_CLASS_OPTIONS,
                        selectedIndex = tripClassIndex,
                        onSelectIndex = { tripClassIndex = it },
                        activeColor = SleekPrimary
                    )
                }
            }

            // ── 3. Status Banner ──
            item {
                val contactorLabel = if (result.deltaContactorKm2_A != null && result.starContactorKm3_A != null) {
                    "KM1/KM2: ${result.mainContactorKm1_A.toInt()}A, KM3: ${result.starContactorKm3_A.toInt()}A"
                } else {
                    "KM1 (AC-3): ${result.mainContactorKm1_A.toInt()} A"
                }
                val headline = "Full Load Current: ${result.fullLoadCurrent_A} A • $contactorLabel"
                val subText = "TOR Setting: ${result.torSettingCurrent_A} A [Dial: ${result.torDialMin_A}–${result.torDialMax_A} A, ${result.tripClass}] • Starting Inrush: ${result.startingCurrent_A} A"
                QuickStatusBanner(status = VoltageDropComplianceStatus.PASS, headline = headline, subText = subText)
            }

            // ── 4. Metric Hero Grid ──
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickMetricHero(
                            title = "FULL LOAD CURRENT (In)",
                            value = "${result.fullLoadCurrent_A} A",
                            subtitle = "${input.motorPowerKW} kW @ ${input.voltageV.toInt()}V",
                            modifier = Modifier.weight(1f),
                            accentColor = SleekPass
                        )
                        QuickMetricHero(
                            title = "STARTING CURRENT",
                            value = "${result.startingCurrent_A} A",
                            subtitle = "${String.format(Locale.US, "%.2f", starterMethod.startMultiplier)}× Inrush (${starterMethod.label})",
                            modifier = Modifier.weight(1f),
                            accentColor = SleekPrimary
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickMetricHero(
                            title = "MAIN CONTACTOR (KM1)",
                            value = "${result.mainContactorKm1_A.toInt()} A",
                            subtitle = "AC-3 Rating (${if (result.deltaContactorKm2_A != null) "Line 0.58·In" else "Main 1.0·In"})",
                            modifier = Modifier.weight(1f),
                            accentColor = SleekPass
                        )
                        QuickMetricHero(
                            title = "TOR DIAL RANGE",
                            value = "${result.torSettingCurrent_A} A",
                            subtitle = "[${result.torDialMin_A} A – ${result.torDialMax_A} A, ${result.tripClass}]",
                            modifier = Modifier.weight(1f),
                            accentColor = ThemeTextColor
                        )
                    }
                }
            }

            // ── 5. Coordination & Backup Protection Card ──
            item {
                QuickCard {
                    QuickSectionTitle(title = "Short-Circuit & Feeder Coordination", accentColor = SleekPrimary)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Motor Circuit Breaker (MPCB):", color = SlateTextSecondary, fontSize = 11.sp)
                            Text("${result.recommendedMpcb_A.toInt()} A (Curve MA / D)", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Motor Backup Fuse (aM):", color = SlateTextSecondary, fontSize = 11.sp)
                            Text("${result.recommendedAmFuse_A.toInt()} A (aM accompanied)", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("General Purpose Fuse (gG):", color = SlateTextSecondary, fontSize = 11.sp)
                            Text("${result.recommendedGgFuse_A.toInt()} A (gG full-range)", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Min Feeder Cable Ampacity:", color = SlateTextSecondary, fontSize = 11.sp)
                            Text("Iz ≥ ${result.minCableAmpacity_A} A (125% rule)", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    if (result.deltaContactorKm2_A != null && result.starContactorKm3_A != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = SleekPrimary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Star-Delta (Y-Δ) Contactor Breakdown:", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("• KM1 (Line Contactor): ${result.mainContactorKm1_A.toInt()} A (AC-3 ≥ 0.58·In)", color = ThemeTextColor, fontSize = 11.sp)
                                Text("• KM2 (Delta Contactor): ${result.deltaContactorKm2_A.toInt()} A (AC-3 ≥ 0.58·In)", color = ThemeTextColor, fontSize = 11.sp)
                                Text("• KM3 (Star Contactor): ${result.starContactorKm3_A.toInt()} A (AC-3 ≥ 0.33·In)", color = ThemeTextColor, fontSize = 11.sp)
                                Text("• TOR Location: ${result.torLocation}", color = SlateTextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // ── 6. Calculation Details Accordion ──
            item {
                QuickCalculationDetailsAccordion(
                    formula = result.formulaStarter,
                    substitution = result.substitutionStarter
                )
            }

            // ── 7. Provenance Footer ──
            item {
                QuickProvenanceFooter(
                    dataSource = "IEC 60947-4-1 AC-3 / IEC 60034-1 Ratings",
                    citation = "IEC 60947-4-1 Contactors and motor-starters - Electromechanical contactors and motor-starters & IEC 60034-1 Rotating electrical machines.",
                    items = provenanceItems
                )
            }
        }
    }
}
