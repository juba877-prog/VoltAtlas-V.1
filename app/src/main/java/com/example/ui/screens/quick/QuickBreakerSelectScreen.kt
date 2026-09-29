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

private val POLES_OPTIONS = persistentListOf("3P", "4P (3P+N)", "1P+N")
private val DEVICE_TYPE_OPTIONS = persistentListOf("Auto (Optimal)", "MCB (≤63A)", "MCCB", "ACB (≥800A)")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickBreakerSelectScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // State declarations
    var loadCurrentText by rememberSaveable { mutableStateOf("45.0") }
    var prospectiveFaultText by rememberSaveable { mutableStateOf("16.0") }
    var polesIndex by rememberSaveable { mutableIntStateOf(0) } // 0=3P, 1=4P, 2=1P
    var deviceTypeIndex by rememberSaveable { mutableIntStateOf(0) } // 0=AUTO, 1=MCB, 2=MCCB, 3=ACB
    var upstreamRatingText by rememberSaveable { mutableStateOf("100.0") }

    val poles = when (polesIndex) {
        0 -> 3
        1 -> 4
        else -> 1
    }

    val deviceTypeFilter = when (deviceTypeIndex) {
        1 -> "MCB"
        2 -> "MCCB"
        3 -> "ACB"
        else -> "AUTO"
    }

    val input = remember(loadCurrentText, prospectiveFaultText, poles, deviceTypeFilter, upstreamRatingText) {
        BreakerSelectQuickInput(
            loadCurrent_A = loadCurrentText.toDoubleOrNull() ?: 45.0,
            prospectiveFault_kA = prospectiveFaultText.toDoubleOrNull() ?: 16.0,
            poles = poles,
            deviceTypeFilter = deviceTypeFilter,
            upstreamRating_A = upstreamRatingText.toDoubleOrNull()
        )
    }

    val result = remember(input) {
        QuickBreakerSelectEngine.calculate(input)
    }

    val copySummaryAction = remember(input, result, context) {
        {
            val summary = """
                ⚡ VoltAtlas Circuit Breaker Selection & Selectivity Summary (IEC 60947-2)
                • Load Current (Ib): ${input.loadCurrent_A} A
                • Prospective Fault (Isc): ${input.prospectiveFault_kA} kA
                • Selected Breaker: ${result.frameDescription} (${result.continuousRating_A.toInt()}A, ${result.breakingCapacityIcu_kA} kA)
                • Trip Unit: ${result.tripUnit} • Ordering Code: ${result.orderingCode}
                • Breaking Margin: ${result.breakingMarginPct}% (Icu ${result.breakingCapacityIcu_kA} kA vs Isc ${input.prospectiveFault_kA} kA)
                • Service Breaking (Ics): ${result.serviceCapacityIcs_kA} kA • Making Capacity (Icm): ${result.makingCapacityIcm_kA} kA
                • Thermal Selectivity (≥1.6): ${result.thermalSelectivityRatio?.let { "$it (${if (result.isThermalSelectivityOk) "PASS" else "FAIL"})" } ?: "N/A"}
                • Magnetic Selectivity (≥2.0): ${result.magneticSelectivityRatio?.let { "$it (${if (result.isMagneticSelectivityOk) "PASS" else "WARNING"})" } ?: "N/A"}
                • Status: ${result.status}
            """.trimIndent()

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Breaker Selection Summary", summary))
            Toast.makeText(context, "Breaker selection summary copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    val provenanceItems = remember(input, result) {
        persistentListOf(
            ProvenanceDetailItem("Selected Breaker Frame:", "${result.frameDescription} (${result.continuousRating_A.toInt()} A)"),
            ProvenanceDetailItem("Ultimate Breaking Capacity (Icu):", "${result.breakingCapacityIcu_kA} kA @ 400V (Margin: +${result.breakingMarginPct}%)"),
            ProvenanceDetailItem("Service Breaking Capacity (Ics):", "${result.serviceCapacityIcs_kA} kA (${if (result.breakingCapacityIcu_kA > 0) ((result.serviceCapacityIcs_kA / result.breakingCapacityIcu_kA) * 100).toInt() else 100}% Icu)"),
            ProvenanceDetailItem("Rated Making Capacity (Icm):", "${result.makingCapacityIcm_kA} kA peak"),
            ProvenanceDetailItem("Thermal Selectivity Screening:", result.thermalSelectivityRatio?.let { "Ratio $it:1 (${if (result.isThermalSelectivityOk) "Pass ≥1.6" else "Fail <1.6"})" } ?: "N/A"),
            ProvenanceDetailItem("Magnetic Selectivity Screening:", result.magneticSelectivityRatio?.let { "Ratio $it:1 (${if (result.isMagneticSelectivityOk) "Pass ≥2.0" else "Warning <2.0"})" } ?: "N/A"),
            ProvenanceDetailItem("Catalog Ordering Code:", result.orderingCode, isPrimary = true)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Breaker Selection & Selectivity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ThemeTextColor
                        )
                        Text(
                            text = "IEC 60947-2 • IEC 60898-1 • Selectivity Check",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateTextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("breaker_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ThemeTextColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = copySummaryAction, modifier = Modifier.testTag("breaker_copy_btn")) {
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
            // ── 1. Load Current & Short Circuit ──
            item {
                QuickCard {
                    QuickSectionTitle(title = "Design Parameters & Short Circuit", accentColor = SleekPrimary, badgeText = "IEC 60947-2", badgeColor = SleekPrimary)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = loadCurrentText,
                            onValueChange = { loadCurrentText = it },
                            label = { Text("Load Current Ib (A)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = prospectiveFaultText,
                            onValueChange = { prospectiveFaultText = it },
                            label = { Text("Fault Current Isc (kA)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Text("Poles & Technology:", color = SlateTextSecondary, fontSize = 12.sp)
                    QuickSegmentedControl(
                        options = POLES_OPTIONS,
                        selectedIndex = polesIndex,
                        onSelectIndex = { polesIndex = it },
                        activeColor = SleekPrimary
                    )

                    Text("Device Category:", color = SlateTextSecondary, fontSize = 12.sp)
                    QuickSegmentedControl(
                        options = DEVICE_TYPE_OPTIONS,
                        selectedIndex = deviceTypeIndex,
                        onSelectIndex = { deviceTypeIndex = it },
                        activeColor = SleekPrimary
                    )
                }
            }

            // ── 2. Upstream Selectivity Screening Card ──
            item {
                QuickCard {
                    QuickSectionTitle(title = "Upstream Device for Selectivity Check", accentColor = SleekPrimary)

                    OutlinedTextField(
                        value = upstreamRatingText,
                        onValueChange = { upstreamRatingText = it },
                        label = { Text("Upstream Breaker Rating In,up (A)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // ── 3. Engineering Disclaimer Badge ──
            item {
                Surface(
                    color = SleekPrimary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SleekPrimary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Screening rule-of-thumb applied; consult manufacturer energy discrimination (I²t) and cascading tables for total selectivity confirmation.",
                            color = SlateTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ── 4. Status Banner ──
            item {
                val bannerStatus = when {
                    result.selectedBreaker == null -> VoltageDropComplianceStatus.FAIL
                    result.thermalSelectivityRatio != null && !result.isThermalSelectivityOk -> VoltageDropComplianceStatus.FAIL
                    result.thermalSelectivityRatio != null && !result.isMagneticSelectivityOk -> VoltageDropComplianceStatus.WARNING
                    else -> VoltageDropComplianceStatus.PASS
                }
                val headline = if (result.selectedBreaker != null) "Selected: ${result.frameDescription} (${result.continuousRating_A.toInt()}A)" else "No Matching Breaker in Catalog"
                val subText = "Breaking Capacity: ${result.breakingCapacityIcu_kA} kA (Margin: +${result.breakingMarginPct}%) • Part No: ${result.orderingCode}"
                QuickStatusBanner(status = bannerStatus, headline = headline, subText = subText)
            }

            // ── 5. Metric Hero Grid ──
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickMetricHero(
                            title = "RATING (In)",
                            value = "${result.continuousRating_A.toInt()} A",
                            subtitle = "Load Ib = ${input.loadCurrent_A} A",
                            modifier = Modifier.weight(1f),
                            accentColor = SleekPass
                        )
                        QuickMetricHero(
                            title = "BREAKING CAPACITY (Icu)",
                            value = "${result.breakingCapacityIcu_kA} kA",
                            subtitle = "Ics = ${result.serviceCapacityIcs_kA} kA (+${result.breakingMarginPct}%)",
                            modifier = Modifier.weight(1f),
                            accentColor = if (result.breakingCapacityIcu_kA >= input.prospectiveFault_kA) SleekPass else SleekFail
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickMetricHero(
                            title = "THERMAL SELECTIVITY",
                            value = result.thermalSelectivityRatio?.let { "Ratio $it:1" } ?: "N/A",
                            subtitle = if (result.isThermalSelectivityOk) "Pass (≥1.6)" else "Fail (<1.6)",
                            modifier = Modifier.weight(1f),
                            accentColor = if (result.isThermalSelectivityOk) SleekPass else SleekFail
                        )
                        QuickMetricHero(
                            title = "ORDERING CODE",
                            value = result.orderingCode,
                            subtitle = result.tripUnit,
                            modifier = Modifier.weight(1f),
                            accentColor = SleekPrimary
                        )
                    }
                }
            }

            // ── 6. Calculation Details Accordion ──
            item {
                val thermalRatioStr = result.thermalSelectivityRatio?.toString() ?: "N/A"
                QuickCalculationDetailsAccordion(
                    formula = "In ≥ Ib  |  Icu ≥ Isc  |  Thermal Ratio = In,up / In,down ≥ 1.6  |  Magnetic Ratio ≥ 2.0",
                    substitution = "In = ${result.continuousRating_A.toInt()}A ≥ ${input.loadCurrent_A}A  |  Icu = ${result.breakingCapacityIcu_kA}kA ≥ ${input.prospectiveFault_kA}kA (Margin: ${result.breakingMarginPct}%)\nSelectivity Ratio = ${input.upstreamRating_A ?: 0}A / ${result.continuousRating_A.toInt()}A = $thermalRatioStr:1"
                )
            }

            // ── 7. Provenance Footer ──
            item {
                QuickProvenanceFooter(
                    dataSource = "Schneider Acti9 & ComPacT NSX / Siemens Sentron / ABB Smax Catalog",
                    citation = "IEC 60947-2 (Low-voltage switchgear and controlgear - Circuit-breakers) & IEC 60898-1.",
                    items = provenanceItems
                )
            }
        }
    }
}
