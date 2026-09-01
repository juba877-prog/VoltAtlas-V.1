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

private val ATS_CATEGORY_OPTIONS = persistentListOf("AC-33B (Motor)", "AC-33A (Frequent)", "AC-31B (Resistive)")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAtsScreen(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    // State declarations
    var mainsCurrentText by rememberSaveable { mutableStateOf("380.0") }
    var generatorFlcText by rememberSaveable { mutableStateOf("400.0") }
    var faultKaText by rememberSaveable { mutableStateOf("25.0") }
    var categoryIndex by rememberSaveable { mutableIntStateOf(0) } // 0=AC-33B, 1=AC-33A, 2=AC-31B
    var detectDelayText by rememberSaveable { mutableStateOf("2.0") }
    var startDelayText by rememberSaveable { mutableStateOf("3.0") }
    var warmupDelayText by rememberSaveable { mutableStateOf("4.0") }

    val category = when (categoryIndex) {
        1 -> "AC-33A"
        2 -> "AC-31B"
        else -> "AC-33B"
    }

    val input = remember(
        mainsCurrentText, generatorFlcText, faultKaText, category,
        detectDelayText, startDelayText, warmupDelayText
    ) {
        AtsQuickInput(
            mainsCurrent_A = mainsCurrentText.toDoubleOrNull() ?: 380.0,
            generatorFLC_A = generatorFlcText.toDoubleOrNull() ?: 400.0,
            prospectiveFault_kA = faultKaText.toDoubleOrNull() ?: 25.0,
            utilizationCategory = category,
            mainsFailureDetectDelay_s = detectDelayText.toDoubleOrNull() ?: 2.0,
            generatorStartDelay_s = startDelayText.toDoubleOrNull() ?: 3.0,
            generatorWarmup_s = warmupDelayText.toDoubleOrNull() ?: 4.0
        )
    }

    val result = remember(input) {
        QuickAtsEngine.calculate(input)
    }

    val copySummaryAction = remember(input, result, context) {
        {
            val summary = """
                ⚡ VoltAtlas ATS Sizing & Withstand Summary (IEC 60947-6-1 / NFPA 110)
                • Governing Load Current: ${result.governingCurrent_A.toInt()} A (Mains ${input.mainsCurrent_A}A, Gen ${input.generatorFLC_A}A)
                • Recommended ATS Rating: ${result.recommendedRating_A.toInt()} A • ${result.recommendedConstruction}
                • Continuous Loading: ${result.atsLoadingPercent}%
                • Short-Time Withstand (Icw): ${result.shortTimeWithstandIcw_kA} kA @ 1.0s
                • Peak Making Capacity (Icm): ${result.peakMakingCapacityIcm_kA} kA (IEC 60947-6-1 Table 7)
                • Total Transfer Time: ${result.totalTransferTimeToGen_s} s (${if (result.isNfpa110Compliant) "NFPA 110 Type 10 Compliant ≤10s" else "EXCEEDS 10s LIMIT"})
                • Category: ${input.utilizationCategory}
            """.trimIndent()

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("ATS Sizing Summary", summary))
            Toast.makeText(context, "ATS summary copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    val provenanceItems = remember(input, result) {
        persistentListOf(
            ProvenanceDetailItem("Governing Load Current:", "${result.governingCurrent_A.toInt()} A (Mains ${input.mainsCurrent_A}A, Gen ${input.generatorFLC_A}A)"),
            ProvenanceDetailItem("Utilization Category:", "${input.utilizationCategory} (Transfer Switch Duty)"),
            ProvenanceDetailItem("Short-Time Withstand (Icw):", "${result.shortTimeWithstandIcw_kA} kA @ 1.0s"),
            ProvenanceDetailItem("Peak Making Capacity (Icm):", "${String.format(Locale.US, "%.1f", result.peakMakingCapacityIcm_kA)} kA peak (IEC Table 7)"),
            ProvenanceDetailItem("Transfer Timing Breakdown:", "Detect ${input.mainsFailureDetectDelay_s}s + Start ${input.generatorStartDelay_s}s + Warmup ${input.generatorWarmup_s}s = ${result.totalTransferTimeToGen_s}s"),
            ProvenanceDetailItem("NFPA 110 Type 10 Status:", if (result.isNfpa110Compliant) "Compliant (≤10s)" else "Exceeds 10s Limit", isPrimary = true)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ATS Sizing & Withstand",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ThemeTextColor
                        )
                        Text(
                            text = "IEC 60947-6-1 • NFPA 110 Type 10 • Peak Making Icm",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateTextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("ats_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ThemeTextColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = copySummaryAction, modifier = Modifier.testTag("ats_copy_btn")) {
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
            // ── 1. Source Currents & Short-Circuit ──
            item {
                QuickCard {
                    QuickSectionTitle(title = "Dual Source Demand & Short-Circuit", accentColor = SleekPrimary, badgeText = "IEC 60947-6-1", badgeColor = SleekPrimary)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = mainsCurrentText,
                            onValueChange = { mainsCurrentText = it },
                            label = { Text("Mains Load (A)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = generatorFlcText,
                            onValueChange = { generatorFlcText = it },
                            label = { Text("Generator FLC (A)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = faultKaText,
                            onValueChange = { faultKaText = it },
                            label = { Text("Prospective Isc (kA)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Text("Utilization Category:", color = SlateTextSecondary, fontSize = 12.sp)
                    QuickSegmentedControl(
                        options = ATS_CATEGORY_OPTIONS,
                        selectedIndex = categoryIndex,
                        onSelectIndex = { categoryIndex = it },
                        activeColor = SleekPrimary
                    )
                }
            }

            // ── 2. Transition & Timing Delays ──
            item {
                QuickCard {
                    QuickSectionTitle(title = "Transfer Timing & NFPA 110 Type 10", accentColor = SleekPrimary)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = detectDelayText,
                            onValueChange = { detectDelayText = it },
                            label = { Text("Mains Detect (s)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = startDelayText,
                            onValueChange = { startDelayText = it },
                            label = { Text("Gen Start Delay (s)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = warmupDelayText,
                            onValueChange = { warmupDelayText = it },
                            label = { Text("Warmup (s)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }

            // ── 3. Status Banner ──
            item {
                val bannerStatus = if (result.isNfpa110Compliant) VoltageDropComplianceStatus.PASS else VoltageDropComplianceStatus.WARNING
                val headline = "Recommended ATS: ${result.recommendedRating_A.toInt()} A • ${result.recommendedConstruction}"
                val subText = "Total Transfer Time: ${result.totalTransferTimeToGen_s} s (${if (result.isNfpa110Compliant) "NFPA 110 Type 10 Pass ≤10s" else "Exceeds 10s"}) • Withstand Icw: ${result.shortTimeWithstandIcw_kA} kA"
                QuickStatusBanner(status = bannerStatus, headline = headline, subText = subText)
            }

            // ── 4. Metric Hero Grid ──
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickMetricHero(
                            title = "ATS RATING",
                            value = "${result.recommendedRating_A.toInt()} A",
                            subtitle = "Governing: ${result.governingCurrent_A.toInt()} A (${result.atsLoadingPercent}%)",
                            modifier = Modifier.weight(1f),
                            accentColor = SleekPass
                        )
                        QuickMetricHero(
                            title = "WITHSTAND (Icw)",
                            value = "${result.shortTimeWithstandIcw_kA} kA",
                            subtitle = "1.0s Duration",
                            modifier = Modifier.weight(1f),
                            accentColor = SleekPrimary
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickMetricHero(
                            title = "MAKING CAPACITY (Icm)",
                            value = "${String.format(Locale.US, "%.1f", result.peakMakingCapacityIcm_kA)} kA",
                            subtitle = "IEC 60947-6-1 Table 7",
                            modifier = Modifier.weight(1f),
                            accentColor = SleekPrimary
                        )
                        QuickMetricHero(
                            title = "TRANSFER TIME",
                            value = "${result.totalTransferTimeToGen_s} s",
                            subtitle = if (result.isNfpa110Compliant) "NFPA 110 Pass (≤10s)" else "Exceeds 10s",
                            modifier = Modifier.weight(1f),
                            accentColor = if (result.isNfpa110Compliant) SleekPass else SleekWarning
                        )
                    }
                }
            }

            // ── 5. Calculation Details Accordion ──
            item {
                QuickCalculationDetailsAccordion(
                    formula = result.formulaAts,
                    substitution = result.substitutionAts
                )
            }

            // ── 6. Provenance Footer ──
            item {
                QuickProvenanceFooter(
                    dataSource = "IEC 60947-6-1 Table 7 & NFPA 110 Emergency Power",
                    citation = "IEC 60947-6-1 Multiple function equipment - Transfer switching equipment & NFPA 110 Standard for Emergency and Standby Power Systems.",
                    items = provenanceItems
                )
            }
        }
    }
}
