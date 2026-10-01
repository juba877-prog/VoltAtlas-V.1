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
            val ratingStr = result.continuousRating_A?.let { "${it.toInt()}A" } ?: "DATA MISSING"
            val icuStr = result.breakingCapacityIcu_kA?.let { "$it kA" } ?: "DATA MISSING"
            val summary = """
                ⚡ VoltAtlas Circuit Breaker Selection & Selectivity Summary (IEC 60947-2)
                • Load Current (Ib): ${input.loadCurrent_A} A
                • Prospective Fault (Isc): ${input.prospectiveFault_kA} kA
                • Selected Breaker: ${result.frameDescription ?: "DATA MISSING (no catalog match)"} ($ratingStr, $icuStr)
                • Trip Unit: ${result.tripUnit ?: "unavailable"} • Ordering Code: ${result.orderingCode ?: "unavailable"}
                • Breaking Margin: ${result.breakingMarginPct?.let { "$it%" } ?: "DATA MISSING"} (Icu $icuStr vs Isc ${input.prospectiveFault_kA} kA)
                • Service Breaking (Ics): ${result.serviceCapacityIcs_kA?.let { "$it kA" } ?: "DATA MISSING"} • Making Capacity (Icm): ${result.makingCapacityIcm_kA?.let { "$it kA" } ?: "DATA MISSING"}
                • Thermal Selectivity (≥1.6): ${result.thermalSelectivityRatio?.let { "$it (${if (result.isThermalSelectivityOk == true) "PASS" else "FAIL"})" } ?: "N/A — no breaker selected"}
                • Magnetic Selectivity (≥2.0): ${result.magneticSelectivityRatio?.let { "$it (${if (result.isMagneticSelectivityOk == true) "PASS" else "WARNING"})" } ?: "N/A — no breaker selected"}
                • Status: ${result.status}
            """.trimIndent()

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Breaker Selection Summary", summary))
            Toast.makeText(context, "Breaker selection summary copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    val provenanceItems = remember(input, result) {
        val baseItems =
            if (result.selectedBreaker == null) {
                persistentListOf(
                    ProvenanceDetailItem(
                        "Selected Breaker Frame:",
                        if (result.isCatalogMissing) "DATA MISSING — breaker catalog not loaded" else "DATA MISSING — no catalog row matches ${input.loadCurrent_A} A / ${input.poles}P / Isc ${input.prospectiveFault_kA} kA"
                    ),
                    ProvenanceDetailItem("Ultimate Breaking Capacity (Icu):", "DATA MISSING — not asserted without a selected breaker"),
                    ProvenanceDetailItem("Service Breaking Capacity (Ics):", "DATA MISSING — not asserted without a selected breaker"),
                    ProvenanceDetailItem("Rated Making Capacity (Icm):", "DATA MISSING — not asserted without a selected breaker")
                )
            } else {
                persistentListOf(
                    ProvenanceDetailItem("Selected Breaker Frame:", "${result.frameDescription} (${result.continuousRating_A?.toInt()} A)"),
                    ProvenanceDetailItem("Ultimate Breaking Capacity (Icu):", "${result.breakingCapacityIcu_kA} kA @ 400V (Margin: +${result.breakingMarginPct}%)"),
                    ProvenanceDetailItem("Service Breaking Capacity (Ics):", "${result.serviceCapacityIcs_kA} kA (${if ((result.breakingCapacityIcu_kA ?: 0.0) > 0) ((result.serviceCapacityIcs_kA ?: 0.0) / result.breakingCapacityIcu_kA!! * 100).toInt() else 100}% Icu)"),
                    ProvenanceDetailItem("Rated Making Capacity (Icm):", "${result.makingCapacityIcm_kA} kA peak")
                )
            }
        baseItems
            .add(
                ProvenanceDetailItem(
                    "Thermal Selectivity Screening:",
                    result.thermalSelectivityRatio?.let { "Ratio $it:1 (${if (result.isThermalSelectivityOk == true) "Pass ≥1.6" else "Fail <1.6"})" } ?: "DATA MISSING — no breaker selected"
                )
            )
            .add(
                ProvenanceDetailItem(
                    "Magnetic Selectivity Screening:",
                    result.magneticSelectivityRatio?.let { "Ratio $it:1 (${if (result.isMagneticSelectivityOk == true) "Pass ≥2.0" else "Warning <2.0"})" } ?: "DATA MISSING — no breaker selected"
                )
            )
            .add(ProvenanceDetailItem("Catalog Ordering Code:", result.orderingCode ?: "unavailable", isPrimary = true))
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                    result.selectedBreaker == null -> VoltageDropComplianceStatus.DATA_MISSING
                    result.thermalSelectivityRatio != null && result.isThermalSelectivityOk == false -> VoltageDropComplianceStatus.FAIL
                    result.thermalSelectivityRatio != null && result.isMagneticSelectivityOk == false -> VoltageDropComplianceStatus.WARNING
                    else -> VoltageDropComplianceStatus.PASS
                }
                val headline = if (result.selectedBreaker != null) {
                    "Selected: ${result.frameDescription} (${result.continuousRating_A?.toInt()}A)"
                } else if (result.isCatalogMissing) {
                    "Catalog Not Loaded"
                } else {
                    "No Matching Breaker in Catalog"
                }
                val subText = if (result.selectedBreaker != null) {
                    "Breaking Capacity: ${result.breakingCapacityIcu_kA} kA (Margin: +${result.breakingMarginPct}%) • Part No: ${result.orderingCode}"
                } else {
                    "DATA MISSING — no breaker selected; no Icu/Ics/Icm asserted • Ib ${input.loadCurrent_A} A • ${input.poles}P • Isc ${input.prospectiveFault_kA} kA"
                }
                QuickStatusBanner(status = bannerStatus, headline = headline, subText = subText)
            }

            // ── 5. Metric Hero Grid ──
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickMetricHero(
                            title = "RATING (In)",
                            value = result.continuousRating_A?.let { "${it.toInt()} A" } ?: "DATA MISSING",
                            subtitle = if (result.selectedBreaker != null) "Load Ib = ${input.loadCurrent_A} A" else "No catalog selection",
                            modifier = Modifier.weight(1f),
                            accentColor = if (result.selectedBreaker != null) SleekPass else SleekPrimary
                        )
                        QuickMetricHero(
                            title = "BREAKING CAPACITY (Icu)",
                            value = result.breakingCapacityIcu_kA?.let { "$it kA" } ?: "DATA MISSING",
                            subtitle = if (result.selectedBreaker != null) {
                                "Ics = ${result.serviceCapacityIcs_kA} kA (+${result.breakingMarginPct}%)"
                            } else {
                                "Not asserted without selection"
                            },
                            modifier = Modifier.weight(1f),
                            accentColor = if (result.selectedBreaker != null && result.breakingCapacityIcu_kA != null &&
                                result.breakingCapacityIcu_kA >= input.prospectiveFault_kA
                            ) SleekPass else SleekPrimary
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickMetricHero(
                            title = "THERMAL SELECTIVITY",
                            value = result.thermalSelectivityRatio?.let { "Ratio $it:1" } ?: "DATA MISSING",
                            subtitle = when {
                                result.thermalSelectivityRatio == null -> "No breaker selected"
                                result.isThermalSelectivityOk == true -> "Pass (≥1.6)"
                                else -> "Fail (<1.6)"
                            },
                            modifier = Modifier.weight(1f),
                            accentColor = when {
                                result.thermalSelectivityRatio == null -> SleekPrimary
                                result.isThermalSelectivityOk == true -> SleekPass
                                else -> SleekFail
                            }
                        )
                        QuickMetricHero(
                            title = "ORDERING CODE",
                            value = result.orderingCode ?: "unavailable",
                            subtitle = result.tripUnit ?: "No breaker selected",
                            modifier = Modifier.weight(1f),
                            accentColor = SleekPrimary
                        )
                    }
                }
            }

            // ── 6. Calculation Details Accordion ──
            item {
                val thermalRatioStr = result.thermalSelectivityRatio?.toString() ?: "N/A"
                val substitution = if (result.selectedBreaker == null) {
                    "In = DATA MISSING ≥ ${input.loadCurrent_A}A (no catalog match)  |  Icu = DATA MISSING vs Isc ${input.prospectiveFault_kA}kA  |  Selectivity not screened without a selected breaker"
                } else {
                    "In = ${result.continuousRating_A?.toInt()}A ≥ ${input.loadCurrent_A}A  |  Icu = ${result.breakingCapacityIcu_kA}kA ≥ ${input.prospectiveFault_kA}kA (Margin: ${result.breakingMarginPct}%)\nSelectivity Ratio = ${input.upstreamRating_A ?: 0}A / ${result.continuousRating_A?.toInt()}A = $thermalRatioStr:1"
                }
                QuickCalculationDetailsAccordion(
                    formula = "In ≥ Ib  |  Icu ≥ Isc  |  Thermal Ratio = In,up / In,down ≥ 1.6  |  Magnetic Ratio ≥ 2.0",
                    substitution = substitution
                )
            }

            // ── 7. Provenance Footer ──
            item {
                QuickProvenanceFooter(
                    dataSource = if (result.selectedBreaker != null) {
                        "Schneider Acti9 & ComPacT NSX / Siemens Sentron / ABB Smax Catalog"
                    } else {
                        "Canonical breaker catalog (${if (result.isCatalogMissing) "not loaded" else "no matching row"}) — DATA MISSING"
                    },
                    citation = "IEC 60947-2 (Low-voltage switchgear and controlgear - Circuit-breakers) & IEC 60898-1.",
                    items = provenanceItems
                )
            }
        }
    }
}
