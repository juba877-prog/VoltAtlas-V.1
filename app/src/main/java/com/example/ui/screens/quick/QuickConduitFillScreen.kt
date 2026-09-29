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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.quick.*
import com.example.ui.theme.*
import kotlinx.collections.immutable.persistentListOf
import java.util.Locale

private val ConduitFieldMinWidth = 140.dp

@Composable
private fun ConduitInputFieldRow(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val requiredPerField = ConduitFieldMinWidth * LocalDensity.current.fontScale
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

private val CONDUIT_MATERIAL_OPTIONS = persistentListOf("Rigid PVC", "EMT Steel", "GI Steel")
private val FILL_LIMIT_OPTIONS = persistentListOf("NEC Std (53/31/40%)", "Custom Limit %")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickConduitFillScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // State declarations
    var conduitTypeIndex by rememberSaveable { mutableIntStateOf(0) } // 0=Rigid PVC, 1=EMT, 2=GI Steel
    var mainCableCountText by rememberSaveable { mutableStateOf("4") }
    var mainCableODText by rememberSaveable { mutableStateOf("18.5") }
    var cpcCableCountText by rememberSaveable { mutableStateOf("1") }
    var cpcCableODText by rememberSaveable { mutableStateOf("9.0") }
    var fillLimitModeIndex by rememberSaveable { mutableIntStateOf(0) } // 0=Standard NEC, 1=Custom %
    var customFillLimitText by rememberSaveable { mutableStateOf("40.0") }

    val conduitType = ConduitTypeOption.entries[conduitTypeIndex.coerceIn(0, ConduitTypeOption.entries.size - 1)]

    val input = remember(
        conduitType, mainCableCountText, mainCableODText, cpcCableCountText,
        cpcCableODText, fillLimitModeIndex, customFillLimitText
    ) {
        ConduitFillQuickInput(
            conduitType = conduitType,
            mainCableCount = mainCableCountText.toIntOrNull() ?: 4,
            mainCableOD_mm = mainCableODText.toDoubleOrNull() ?: 18.5,
            cpcCableCount = cpcCableCountText.toIntOrNull() ?: 1,
            cpcCableOD_mm = cpcCableODText.toDoubleOrNull() ?: 9.0,
            customFillLimitPct = if (fillLimitModeIndex == 1) customFillLimitText.toDoubleOrNull() else null
        )
    }

    val result = remember(input) {
        QuickConduitFillEngine.calculate(input)
    }

    val copySummaryAction = remember(input, result, conduitType, context) {
        {
            val summary = """
                ⚡ VoltAtlas Conduit Fill & Sizing Summary (IEC 61386 / NEC Ch. 9)
                • Conduit Type: ${conduitType.label}
                • Recommended Trade Size: ${result.recommendedTradeSize} (Internal ID: ${result.internalDiameterMm} mm)
                • Cable Configuration: ${input.mainCableCount}x Main (OD ${input.mainCableOD_mm} mm) + ${input.cpcCableCount}x CPC (OD ${input.cpcCableOD_mm} mm)
                • Total Cable Area: ${result.totalCableAreaMm2} mm²
                • Conduit Internal Area: ${result.totalInternalAreaMm2} mm²
                • Actual Fill: ${result.actualFillPercent}% (Max Allowed: ${result.allowedFillPercent}%)
                • Remaining Spare Area: ${result.remainingSpareAreaMm2} mm² (${result.spareCapacityPercent}% spare)
                • Compliance: ${if (result.isCompliant) "PASS" else "EXCEEDS FILL LIMIT"}
            """.trimIndent()

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Conduit Fill Summary", summary))
            Toast.makeText(context, "Conduit fill summary copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    val provenanceItems = remember(input, result, conduitType) {
        persistentListOf(
            ProvenanceDetailItem("Conduit Standard & Type:", "${conduitType.label} (IEC 61386)"),
            ProvenanceDetailItem("Internal Diameter (ID):", "${result.internalDiameterMm} mm"),
            ProvenanceDetailItem("Conduit Internal Area:", "${String.format(Locale.US, "%.1f", result.totalInternalAreaMm2)} mm²"),
            ProvenanceDetailItem("Total Cable Area:", "${String.format(Locale.US, "%.1f", result.totalCableAreaMm2)} mm² (${input.mainCableCount}x Main + ${input.cpcCableCount}x CPC)"),
            ProvenanceDetailItem("Max Allowed Fill Limit:", "${String.format(Locale.US, "%.0f", result.allowedFillPercent)}% (NEC Table 1)"),
            ProvenanceDetailItem("Remaining Spare Area:", "${String.format(Locale.US, "%.1f", result.remainingSpareAreaMm2)} mm² (${String.format(Locale.US, "%.1f", result.spareCapacityPercent)}% spare)", isPrimary = true)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Conduit Fill & Trade Sizing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ThemeTextColor
                        )
                        Text(
                            text = "IEC 61386-1 • NEC Chapter 9 Table 1",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateTextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("conduit_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ThemeTextColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = copySummaryAction, modifier = Modifier.testTag("conduit_copy_btn")) {
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
            // ── 1. Conduit Type & Standard ──
            item {
                QuickCard {
                    QuickSectionTitle(title = "Conduit Standards & Limits", accentColor = SleekPrimary, badgeText = "IEC 61386", badgeColor = SleekPrimary)

                    Text("Conduit Material & Standard:", color = SlateTextSecondary, fontSize = 12.sp)
                    QuickSegmentedControl(
                        options = CONDUIT_MATERIAL_OPTIONS,
                        selectedIndex = conduitTypeIndex,
                        onSelectIndex = { conduitTypeIndex = it },
                        activeColor = SleekPrimary
                    )

                    Text("Fill Capacity Profile:", color = SlateTextSecondary, fontSize = 12.sp)
                    QuickSegmentedControl(
                        options = FILL_LIMIT_OPTIONS,
                        selectedIndex = fillLimitModeIndex,
                        onSelectIndex = { fillLimitModeIndex = it },
                        activeColor = SleekPrimary
                    )

                    if (fillLimitModeIndex == 1) {
                        OutlinedTextField(
                            value = customFillLimitText,
                            onValueChange = { customFillLimitText = it },
                            label = { Text("Custom Fill Limit (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            // ── 2. Cables Configuration ──
            item {
                QuickCard {
                    QuickSectionTitle(title = "Cable Arrangement & Outside Diameters", accentColor = SleekPrimary)

                    ConduitInputFieldRow(
                        first = { fieldModifier ->
                            OutlinedTextField(
                                value = mainCableCountText,
                                onValueChange = { mainCableCountText = it },
                                label = { Text("Main Cables (Qty)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = fieldModifier.testTag("input_main_cable_count"),
                                singleLine = true
                            )
                        },
                        second = { fieldModifier ->
                            OutlinedTextField(
                                value = mainCableODText,
                                onValueChange = { mainCableODText = it },
                                label = { Text("Main Cable OD (mm)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = fieldModifier.testTag("input_main_cable_od"),
                                singleLine = true
                            )
                        }
                    )

                    ConduitInputFieldRow(
                        first = { fieldModifier ->
                            OutlinedTextField(
                                value = cpcCableCountText,
                                onValueChange = { cpcCableCountText = it },
                                label = { Text("CPC Earth Cables (Qty)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = fieldModifier.testTag("input_cpc_cable_count"),
                                singleLine = true
                            )
                        },
                        second = { fieldModifier ->
                            OutlinedTextField(
                                value = cpcCableODText,
                                onValueChange = { cpcCableODText = it },
                                label = { Text("CPC Cable OD (mm)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = fieldModifier.testTag("input_cpc_cable_od"),
                                singleLine = true
                            )
                        }
                    )
                }
            }

            // ── 3. Status Banner ──
            item {
                val bannerStatus = if (result.isCompliant) VoltageDropComplianceStatus.PASS else VoltageDropComplianceStatus.FAIL
                val headline = if (result.isCompliant) "Selected Trade Size: ${result.recommendedTradeSize} (${result.actualFillPercent}% Fill)" else "Selected Size Exceeds Fill Limit (${result.actualFillPercent}% > ${result.allowedFillPercent}%)"
                val subText = "Internal ID: ${result.internalDiameterMm} mm • Cable Area: ${result.totalCableAreaMm2} mm² / ${result.totalInternalAreaMm2} mm²"
                QuickStatusBanner(status = bannerStatus, headline = headline, subText = subText)
            }

            // ── 4. Metric Hero Grid ──
            item {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val stackMetricCards = maxWidth < 320.dp || LocalDensity.current.fontScale >= 1.3f
                    if (stackMetricCards) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickMetricHero(
                                title = "RECOMMENDED TRADE SIZE",
                                value = result.recommendedTradeSize,
                                subtitle = "Conduit ID: ${result.internalDiameterMm} mm",
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = if (result.isCompliant) SleekPass else SleekFail
                            )
                            QuickMetricHero(
                                title = "ACTUAL FILL RATIO",
                                value = "${String.format(Locale.US, "%.1f", result.actualFillPercent)}%",
                                subtitle = "Max Allowed: ${String.format(Locale.US, "%.0f", result.allowedFillPercent)}%",
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = if (result.isCompliant) SleekPass else SleekFail
                            )
                            QuickMetricHero(
                                title = "TOTAL CABLE AREA",
                                value = "${String.format(Locale.US, "%.1f", result.totalCableAreaMm2)} mm²",
                                subtitle = "Conduit Area: ${String.format(Locale.US, "%.1f", result.totalInternalAreaMm2)} mm²",
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = SleekPrimary
                            )

                            val spareColor = when {
                                result.spareCapacityPercent >= 20.0 -> SleekPass
                                result.spareCapacityPercent >= 0.0 -> SleekWarning
                                else -> SleekFail
                            }
                            QuickMetricHero(
                                title = "SPARE CROSS-SECTION",
                                value = "${String.format(Locale.US, "%.1f", result.remainingSpareAreaMm2)} mm²",
                                subtitle = "${String.format(Locale.US, "%.1f", result.spareCapacityPercent)}% spare area",
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = spareColor
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                QuickMetricHero(
                                    title = "RECOMMENDED TRADE SIZE",
                                    value = result.recommendedTradeSize,
                                    subtitle = "Conduit ID: ${result.internalDiameterMm} mm",
                                    modifier = Modifier.weight(1f),
                                    accentColor = if (result.isCompliant) SleekPass else SleekFail
                                )
                                QuickMetricHero(
                                    title = "ACTUAL FILL RATIO",
                                    value = "${String.format(Locale.US, "%.1f", result.actualFillPercent)}%",
                                    subtitle = "Max Allowed: ${String.format(Locale.US, "%.0f", result.allowedFillPercent)}%",
                                    modifier = Modifier.weight(1f),
                                    accentColor = if (result.isCompliant) SleekPass else SleekFail
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                QuickMetricHero(
                                    title = "TOTAL CABLE AREA",
                                    value = "${String.format(Locale.US, "%.1f", result.totalCableAreaMm2)} mm²",
                                    subtitle = "Conduit Area: ${String.format(Locale.US, "%.1f", result.totalInternalAreaMm2)} mm²",
                                    modifier = Modifier.weight(1f),
                                    accentColor = SleekPrimary
                                )

                                val spareColor = when {
                                    result.spareCapacityPercent >= 20.0 -> SleekPass
                                    result.spareCapacityPercent >= 0.0 -> SleekWarning
                                    else -> SleekFail
                                }
                                QuickMetricHero(
                                    title = "SPARE CROSS-SECTION",
                                    value = "${String.format(Locale.US, "%.1f", result.remainingSpareAreaMm2)} mm²",
                                    subtitle = "${String.format(Locale.US, "%.1f", result.spareCapacityPercent)}% spare area",
                                    modifier = Modifier.weight(1f),
                                    accentColor = spareColor
                                )
                            }
                        }
                    }
                }
            }

            // ── 5. Calculation Details Accordion ──
            item {
                QuickCalculationDetailsAccordion(
                    formula = result.formulaArea,
                    substitution = result.substitutionArea
                )
            }

            // ── 6. Provenance Footer ──
            item {
                QuickProvenanceFooter(
                    dataSource = "NEC Chapter 9 Table 1 & IEC 61386 Conduit Data",
                    citation = "IEC 61386-1 Conduit systems for cable management & NEC Chapter 9 Table 1 (1-wire: 53%, 2-wire: 31%, 3+ wire: 40%).",
                    items = provenanceItems
                )
            }
        }
    }
}
