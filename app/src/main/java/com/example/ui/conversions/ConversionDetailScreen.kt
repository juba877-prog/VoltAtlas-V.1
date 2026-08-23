package com.example.ui.conversions

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.engine.converters.CapacitorConnectionType
import com.example.engine.converters.ElectricalConverters
import com.example.ui.theme.SleekPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversionDetailScreen(
    tool: ConversionTool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Intercept Android System Back Button so it returns to Conversions List instead of exiting to Dashboard
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    val prefs = remember(context) { ConversionPreferences(context) }
    val swgTable = remember(context) { ConversionCatalogLoader.loadSwgTable(context) }
    val awgOptions = remember(context) { ConversionCatalogLoader.loadAwgOptions(context) }

    var isFavorite by remember { mutableStateOf(prefs.isFavorite(tool.id)) }

    // Input States
    var inputVal1 by remember { mutableStateOf(tool.defaultInput) }
    var inputVal2 by remember { mutableStateOf(tool.defaultInput2) }
    var inputVal3 by remember { mutableStateOf(tool.defaultInput3) }
    var inputVal4 by remember { mutableStateOf(tool.defaultInput4) }

    // Variant Selection State
    var selectedVariantId by remember {
        mutableStateOf(tool.variants.firstOrNull()?.id ?: "")
    }

    // AWG / SWG Dropdown States
    var selectedAwg by remember { mutableStateOf("10") }
    var selectedSwg by remember { mutableStateOf("10") }

    // Live Result Calculation
    val outputResult: ConversionOutput = remember(
        tool.id, inputVal1, inputVal2, inputVal3, inputVal4, selectedVariantId, selectedAwg, selectedSwg, swgTable
    ) {
        calculateOutput(
            tool = tool,
            input1 = inputVal1,
            input2 = inputVal2,
            input3 = inputVal3,
            input4 = inputVal4,
            variantId = selectedVariantId,
            selectedAwg = selectedAwg,
            selectedSwg = selectedSwg,
            swgTable = swgTable
        )
    }

    // Auto-record calculation history whenever output changes to a valid result (debounced 500ms, executed on Dispatchers.IO)
    LaunchedEffect(outputResult) {
        val payload = when (outputResult) {
            is ConversionOutput.CableResult -> {
                val inputSummary = when (tool.inputType) {
                    "awg_selector" -> "AWG $selectedAwg"
                    "swg_selector" -> "SWG $selectedSwg"
                    else -> "$inputVal1 kcmil"
                }
                val resSummary = "Safe Up: ${formatNominal(outputResult.result.safeUpNominalMm2)} mm² (True: ${String.format(Locale.US, "%.2f", outputResult.result.trueAreaMm2)} mm²)"
                Triple(tool.id, inputSummary, resSummary)
            }

            is ConversionOutput.SingleValueResult -> {
                val inputSummary = "$inputVal1 ${tool.inputLabelEn}"
                val resSummary = "${String.format(Locale.US, "%.3f", outputResult.value)} ${outputResult.unitEn}"
                Triple(tool.id, inputSummary, resSummary)
            }

            is ConversionOutput.MultiValueResult -> {
                val inputSummary = "$inputVal1 / $inputVal2"
                val resSummary = outputResult.values.joinToString("; ") { "${it.first}: ${it.second}" }
                Triple(tool.id, inputSummary, resSummary)
            }

            is ConversionOutput.TextResult -> {
                val inputSummary = "$inputVal1 mm²"
                val resSummary = outputResult.textEn
                Triple(tool.id, inputSummary, resSummary)
            }

            else -> null
        } ?: return@LaunchedEffect

        // Debounce: Wait 500 ms of inactivity before persisting to disk
        delay(500L)

        // Persist asynchronously on Dispatchers.IO to guarantee zero Main thread disk / JSON operations
        withContext(Dispatchers.IO) {
            prefs.addHistoryItem(payload.first, tool.titleEn, payload.second, payload.third)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Conversions List",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = ConversionCatalogLoader.getIcon(tool.icon),
                    contentDescription = tool.titleEn,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = tool.titleEn,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // Favorite Star Button
                IconButton(onClick = {
                    isFavorite = prefs.toggleFavorite(tool.id)
                }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Description Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = tool.descriptionEn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Input Controls Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Inputs",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // AWG Selector Dropdown
                    if (tool.inputType == "awg_selector") {
                        DropdownSelector(
                            label = "Select AWG Gauge",
                            options = awgOptions,
                            selectedOption = selectedAwg,
                            onOptionSelected = { selectedAwg = it }
                        )
                    }

                    // SWG Selector Dropdown
                    if (tool.inputType == "swg_selector") {
                        val swgKeys = remember(swgTable) { swgTable.keys.toList() }
                        DropdownSelector(
                            label = "Select SWG Gauge",
                            options = swgKeys,
                            selectedOption = selectedSwg,
                            onOptionSelected = { selectedSwg = it }
                        )
                    }

                    // Standard Numeric Input 1
                    if (tool.inputType == "numeric" || tool.inputType == "numeric_with_variant" || tool.inputType == "dual_numeric" || tool.inputType == "dual_numeric_with_variant" || tool.inputType == "cap_inputs" || tool.inputType == "numeric_with_unit_dropdown" || tool.inputType == "vdrop_inputs") {
                        OutlinedTextField(
                            value = inputVal1,
                            onValueChange = { inputVal1 = it },
                            label = { Text(tool.inputLabelEn) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Dual Numeric Input 2
                    if (tool.inputType == "dual_numeric" || tool.inputType == "dual_numeric_with_variant" || tool.inputType == "cap_inputs" || tool.inputType == "vdrop_inputs") {
                        tool.inputLabel2En?.let { lblEn ->
                            OutlinedTextField(
                                value = inputVal2,
                                onValueChange = { inputVal2 = it },
                                label = { Text(lblEn) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Cap / VDrop Inputs 3
                    if (tool.inputType == "cap_inputs" || tool.inputType == "vdrop_inputs") {
                        tool.inputLabel3En?.let { lblEn ->
                            OutlinedTextField(
                                value = inputVal3,
                                onValueChange = { inputVal3 = it },
                                label = { Text(lblEn) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // VDrop Input 4 (Cable Length)
                    if (tool.inputType == "vdrop_inputs") {
                        OutlinedTextField(
                            value = inputVal4,
                            onValueChange = { inputVal4 = it },
                            label = { Text("Cable Length (m)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Variant Dropdown Selector
                    if (tool.variants.isNotEmpty()) {
                        tool.variantLabelEn?.let { vLblEn ->
                            Text(
                                text = vLblEn,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val variantOptions = tool.variants.map { it.labelEn }
                        val currentVariantIndex = tool.variants.indexOfFirst { it.id == selectedVariantId }.coerceAtLeast(0)
                        val selectedLabel = variantOptions.getOrElse(currentVariantIndex) { "" }

                        DropdownSelector(
                            label = tool.variantLabelEn ?: "Select Option",
                            options = variantOptions,
                            selectedOption = selectedLabel,
                            onOptionSelected = { chosen ->
                                val idx = variantOptions.indexOf(chosen)
                                if (idx >= 0 && idx < tool.variants.size) {
                                    selectedVariantId = tool.variants[idx].id
                                }
                            }
                        )
                    }
                }
            }

            // Results Section
            when (outputResult) {
                is ConversionOutput.CableResult -> {
                    CableResultCard(result = outputResult.result)
                }

                is ConversionOutput.SingleValueResult -> {
                    SingleValueResultCard(result = outputResult)
                }

                is ConversionOutput.MultiValueResult -> {
                    MultiValueResultCard(result = outputResult)
                }

                is ConversionOutput.TextResult -> {
                    TextResultCard(result = outputResult)
                }

                is ConversionOutput.Invalid -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Invalid Input",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = outputResult.messageEn,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 3-NUMBER CABLE RESULT CARD:
 * Compact, clean layout with spaced labels/values and horizontal RECOMMENDED badge.
 */
@Composable
fun CableResultCard(result: com.example.engine.converters.CableConversionResult) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val copyText = "Cable Cross-Section: Safe Up = ${formatNominal(result.safeUpNominalMm2)} mm², Nearest = ${formatNominal(result.nearestNominalMm2)} mm², True = ${String.format(Locale.US, "%.3f", result.trueAreaMm2)} mm²"

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Cable Calculation Result",
                        tint = SleekPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Conductor Cross-Section Results",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Copy Button
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(copyText))
                        Toast.makeText(context, "Copied result to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Result",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider()

            // 1. True Geometric Area
            CableResultRow(
                titleEn = "True Area (Geometric)",
                valueText = "${String.format(Locale.US, "%.3f", result.trueAreaMm2)} mm²",
                badgeText = "Exact",
                badgeColor = MaterialTheme.colorScheme.secondaryContainer,
                badgeTextColor = MaterialTheme.colorScheme.onSecondaryContainer
            )

            // 2. Nearest IEC Standard Nominal
            CableResultRow(
                titleEn = "Nearest Standard (IEC 60228)",
                valueText = "${formatNominal(result.nearestNominalMm2)} mm²",
                badgeText = "Closest",
                badgeColor = MaterialTheme.colorScheme.surfaceVariant,
                badgeTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 3. Safe Up-Size (COMPACT RECOMMENDED CARD)
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                shape = RoundedCornerShape(10.dp),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Safe Up-Size (Recommended)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${formatNominal(result.safeUpNominalMm2)} mm²",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = SleekPrimary,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Text(
                            text = "RECOMMENDED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = "Formula: IEC 60228 / BS 7671 Conductor Nominal Sizing Table",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CableResultRow(
    titleEn: String,
    valueText: String,
    badgeText: String,
    badgeColor: androidx.compose.ui.graphics.Color,
    badgeTextColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = titleEn,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                color = badgeColor,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.wrapContentWidth()
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeTextColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun SingleValueResultCard(result: ConversionOutput.SingleValueResult) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copyText = "${String.format(Locale.US, "%.3f", result.value)} ${result.unitEn}"

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Converted Result",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Copy Button
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(copyText))
                    Toast.makeText(context, "Copied result to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Result",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${String.format(Locale.US, "%.3f", result.value)} ${result.unitEn}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            result.noteEn?.let { nEn ->
                HorizontalDivider()
                Text(
                    text = nEn,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MultiValueResultCard(result: ConversionOutput.MultiValueResult) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copyText = result.values.joinToString("; ") { "${it.first}: ${it.second}" }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Calculation Results",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Copy Button
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(copyText))
                    Toast.makeText(context, "Copied results to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Results",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    result.values.forEach { (lbl, valStr) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = lbl,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = valStr,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            result.noteEn?.let { nEn ->
                HorizontalDivider()
                Text(
                    text = nEn,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TextResultCard(result: ConversionOutput.TextResult) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copyText = result.textEn

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Equivalent AWG Gauge",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Copy Button
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(copyText))
                    Toast.makeText(context, "Copied gauge to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Gauge",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = result.textEn,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSelector(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun formatNominal(v: Double): String {
    return if (v % 1.0 == 0.0) {
        String.format(Locale.US, "%.0f", v)
    } else {
        String.format(Locale.US, "%.2f", v)
    }
}

private fun calculateOutput(
    tool: ConversionTool,
    input1: String,
    input2: String,
    input3: String,
    input4: String,
    variantId: String,
    selectedAwg: String,
    selectedSwg: String,
    swgTable: Map<String, SwgGaugeEntry>
): ConversionOutput {
    val d1 = input1.toDoubleOrNull()

    return when (tool.id) {
        "awg_to_mm2" -> {
            val gaugeNum = ElectricalConverters.parseAwgGauge(selectedAwg)
            if (gaugeNum == null) {
                ConversionOutput.Invalid()
            } else {
                val res = ElectricalConverters.awgToMm2(gaugeNum)
                ConversionOutput.CableResult(res)
            }
        }

        "swg_to_mm2" -> {
            val entry = swgTable[selectedSwg]
            if (entry == null) {
                ConversionOutput.Invalid("Invalid SWG Gauge")
            } else {
                val res = ElectricalConverters.swgToMm2(entry.diameterMm)
                ConversionOutput.CableResult(res)
            }
        }

        "kcmil_to_mm2" -> {
            if (d1 == null || d1 <= 0) {
                ConversionOutput.Invalid()
            } else {
                val res = ElectricalConverters.kcmilToMm2(d1)
                ConversionOutput.CableResult(res)
            }
        }

        "mm2_to_awg" -> {
            if (d1 == null || d1 <= 0) {
                ConversionOutput.Invalid()
            } else {
                val awgText = ElectricalConverters.mm2ToAwg(d1)
                ConversionOutput.TextResult(
                    textEn = awgText
                )
            }
        }

        "voltage_drop_mv" -> {
            val r = d1
            val x = input2.toDoubleOrNull()
            val amps = input3.toDoubleOrNull()
            val lenM = input4.toDoubleOrNull()
            if (r == null || r < 0 || x == null || x < 0 || amps == null || amps <= 0 || lenM == null || lenM <= 0) {
                ConversionOutput.Invalid("Enter valid Resistance (Ω/km), Reactance (Ω/km), Amps (A), and Length (m)")
            } else {
                val res = ElectricalConverters.cableVoltageDrop(r, x, amps, lenM, cosPhi = 0.8, isThreePhase = true, voltageV = 400.0)
                ConversionOutput.MultiValueResult(
                    values = listOf(
                        "Unit Voltage Drop" to "${String.format(Locale.US, "%.3f", res.mVperAm)} mV/A/m",
                        "Total Voltage Drop ΔV" to "${String.format(Locale.US, "%.3f", res.vDrop)} V",
                        "Percentage Voltage Drop ΔV%" to "${String.format(Locale.US, "%.2f", res.vDropPct)} %"
                    ),
                    noteEn = "BS 7671 / IEC 60364 Formula: mV/A/m = √3 × (R cosφ + X sinφ) | ΔV = (mV/A/m × I × L) / 1000"
                )
            }
        }

        "power_triangle" -> {
            val cosPhi = input2.toDoubleOrNull() ?: 0.8
            if (d1 == null || d1 <= 0 || cosPhi < 0.1 || cosPhi > 1.0) {
                ConversionOutput.Invalid("Enter valid Apparent Power S (kVA) and cosφ (0.1 - 1.0)")
            } else {
                val res = ElectricalConverters.powerTriangleFromS(d1, cosPhi)
                ConversionOutput.MultiValueResult(
                    values = listOf(
                        "Active Power P" to "${String.format(Locale.US, "%.3f", res.pKw)} kW",
                        "Reactive Power Q" to "${String.format(Locale.US, "%.3f", res.qKvar)} kVAR",
                        "Apparent Power S" to "${String.format(Locale.US, "%.3f", res.sKva)} kVA",
                        "Power Factor cosφ" to "${String.format(Locale.US, "%.2f", res.cosPhi)} (sinφ = ${String.format(Locale.US, "%.2f", res.sinPhi)})"
                    ),
                    noteEn = "Formula: P = S × cosφ | Q = S × sinφ | S = √(P² + Q²)"
                )
            }
        }

        "current_to_power" -> {
            val vLine = input2.toDoubleOrNull() ?: 400.0
            val is3P = variantId != "1P"
            val cosPhi = 0.8
            if (d1 == null || d1 <= 0 || vLine <= 0) {
                ConversionOutput.Invalid("Enter valid Current (A) and Voltage (V)")
            } else {
                val res = ElectricalConverters.currentToPower(d1, vLine, cosPhi, is3P)
                ConversionOutput.MultiValueResult(
                    values = listOf(
                        "Active Power P (@ cosφ=0.8)" to "${String.format(Locale.US, "%.3f", res.pKw)} kW",
                        "Apparent Power S" to "${String.format(Locale.US, "%.3f", res.sKva)} kVA",
                        "Current I" to "${String.format(Locale.US, "%.2f", res.amps)} A"
                    ),
                    noteEn = if (is3P) "3-Phase Formula: P = √3 × V × I × cosφ / 1000" else "1-Phase Formula: P = V × I × cosφ / 1000"
                )
            }
        }

        "hp_to_kw" -> {
            if (d1 == null || d1 <= 0) {
                ConversionOutput.Invalid()
            } else {
                val isMetric = variantId == "metric"
                val kw = ElectricalConverters.hpToKw(d1, isMetric)
                ConversionOutput.SingleValueResult(
                    value = kw,
                    unitEn = "kW",
                    noteEn = if (isMetric) "Formula: P(kW) = PS × 0.7355" else "Formula: P(kW) = HP × 0.7457"
                )
            }
        }

        "kw_to_hp" -> {
            if (d1 == null || d1 <= 0) {
                ConversionOutput.Invalid()
            } else {
                val isMetric = variantId == "metric"
                val hp = ElectricalConverters.kwToHp(d1, isMetric)
                val unitLabel = if (isMetric) "PS" else "HP"
                ConversionOutput.SingleValueResult(
                    value = hp,
                    unitEn = unitLabel,
                    noteEn = if (isMetric) "Formula: PS = P(kW) / 0.7355" else "Formula: HP = P(kW) / 0.7457"
                )
            }
        }

        "freq_to_rpm" -> {
            if (d1 == null || d1 <= 0) {
                ConversionOutput.Invalid()
            } else {
                val poles = variantId.toIntOrNull() ?: 4
                val rpm = ElectricalConverters.freqToRpm(d1, poles)
                ConversionOutput.SingleValueResult(
                    value = rpm,
                    unitEn = "RPM",
                    noteEn = "Formula: Synchronous Speed N = 120 × f / poles"
                )
            }
        }

        "torque_power_rpm" -> {
            val d2 = input2.toDoubleOrNull()
            if (d1 == null || d1 <= 0 || d2 == null || d2 <= 0) {
                ConversionOutput.Invalid("Enter valid Power (kW) and Speed (RPM)")
            } else {
                val torque = ElectricalConverters.torqueFromKwRpm(d1, d2)
                ConversionOutput.SingleValueResult(
                    value = torque,
                    unitEn = "N·m",
                    noteEn = "Formula: Torque T = 9549.3 × P(kW) / N(RPM)"
                )
            }
        }

        "tx_quick_sc" -> {
            val zPct = input2.toDoubleOrNull() ?: 5.0
            if (d1 == null || d1 <= 0 || zPct <= 0) {
                ConversionOutput.Invalid("Enter valid Transformer Rating (kVA) and Impedance (%Z)")
            } else {
                val res = ElectricalConverters.txQuickShortCircuit(d1, zPct, 400.0)
                ConversionOutput.MultiValueResult(
                    values = listOf(
                        "Full Load Current (IFLC)" to "${String.format(Locale.US, "%.2f", res.iFlcAmps)} A",
                        "3-Phase Short Circuit Current (Isc)" to "${String.format(Locale.US, "%.2f", res.iScKa)} kA"
                    ),
                    noteEn = "Quick estimate (without IEC 60909 c-factor). Formula: IFLC = kVA × 1000 / (√3 × 400V) | Isc = IFLC / (%Z / 100)"
                )
            }
        }

        "uf_to_kvar" -> {
            val vLine = input2.toDoubleOrNull()
            val freq = input3.toDoubleOrNull() ?: 50.0
            if (d1 == null || d1 <= 0 || vLine == null || vLine <= 0) {
                ConversionOutput.Invalid("Enter valid Capacitance (µF) and Voltage (V)")
            } else {
                val conn = if (variantId == "delta") CapacitorConnectionType.DELTA else CapacitorConnectionType.SINGLE_OR_STAR
                val kvar = ElectricalConverters.microFaradToKvar(d1, vLine, freq, conn)
                ConversionOutput.SingleValueResult(
                    value = kvar,
                    unitEn = "kVAR",
                    noteEn = if (conn == CapacitorConnectionType.DELTA) "Formula: Q = 3 × 2π × f × C × V² × 10⁻⁹ (Delta 3-Phase Bank)" else "Formula: Q = 2π × f × C × V² × 10⁻⁹ (Star 3-Phase Bank / 1-Phase)"
                )
            }
        }

        "kvar_to_uf" -> {
            val vLine = input2.toDoubleOrNull()
            val freq = input3.toDoubleOrNull() ?: 50.0
            if (d1 == null || d1 <= 0 || vLine == null || vLine <= 0) {
                ConversionOutput.Invalid("Enter valid kVAR and Voltage (V)")
            } else {
                val conn = if (variantId == "delta") CapacitorConnectionType.DELTA else CapacitorConnectionType.SINGLE_OR_STAR
                val uf = ElectricalConverters.kvarToMicroFarad(d1, vLine, freq, conn)
                ConversionOutput.SingleValueResult(
                    value = uf,
                    unitEn = "µF",
                    noteEn = if (conn == CapacitorConnectionType.DELTA) "Formula: C = (Q × 10⁹) / (3 × 2π × f × V²) (Per Branch)" else "Formula: C = (Q × 10⁹) / (2π × f × V²) (Per Phase)"
                )
            }
        }

        "pfc_needed" -> {
            val cos1 = input2.toDoubleOrNull() ?: 0.8
            val cos2 = input3.toDoubleOrNull() ?: 0.95
            if (d1 == null || d1 <= 0 || cos1 < 0.1 || cos1 > 0.999 || cos2 < cos1 || cos2 > 1.0) {
                ConversionOutput.Invalid("Enter valid Power (kW) and Power Factors (0.1 ≤ cosφ1 < cosφ2 ≤ 1.0)")
            } else {
                val res = ElectricalConverters.powerFactorCorrection(d1, cos1, cos2, 400.0)
                ConversionOutput.MultiValueResult(
                    values = listOf(
                        "Required Capacitor Rating Qc" to "${String.format(Locale.US, "%.3f", res.qcKvar)} kVAR",
                        "Line Current Reduction ΔI" to "${String.format(Locale.US, "%.2f", res.deltaAmps)} A"
                    ),
                    noteEn = "Formula: Qc = P × (tanφ1 - tanφ2) | ΔI = (P × 1000 / (√3 × V)) × (1/cosφ1 - 1/cosφ2)"
                )
            }
        }

        "battery_capacity" -> {
            val vDc = input2.toDoubleOrNull() ?: 48.0
            if (d1 == null || d1 <= 0 || vDc <= 0) {
                ConversionOutput.Invalid("Enter valid Capacity (Ah) and DC Voltage (Vdc)")
            } else {
                val res = ElectricalConverters.batteryAhToKwh(d1, vDc, dod = 0.8, efficiency = 0.9)
                ConversionOutput.MultiValueResult(
                    values = listOf(
                        "Available Energy E" to "${String.format(Locale.US, "%.3f", res.eKwh)} kWh",
                        "Battery Capacity Ah" to "${String.format(Locale.US, "%.1f", res.ahRequired)} Ah"
                    ),
                    noteEn = "Formula: E = (Ah × Vdc × DOD × η) / 1000 | DOD = 0.8 (80%), Efficiency = 0.9 (90%)"
                )
            }
        }

        "voltage_units" -> {
            if (d1 == null || d1 < 0) {
                ConversionOutput.Invalid()
            } else {
                val baseVolts = when (variantId) {
                    "kV" -> d1 * 1000.0
                    "mV" -> d1 / 1000.0
                    else -> d1
                }
                val kv = baseVolts / 1000.0
                val mv = baseVolts * 1000.0
                ConversionOutput.SingleValueResult(
                    value = baseVolts,
                    unitEn = "V",
                    noteEn = "Conversion: ${formatNominal(kv)} kV | ${formatNominal(mv)} mV"
                )
            }
        }

        "current_units" -> {
            if (d1 == null || d1 < 0) {
                ConversionOutput.Invalid()
            } else {
                val baseAmps = when (variantId) {
                    "kA" -> d1 * 1000.0
                    "mA" -> d1 / 1000.0
                    else -> d1
                }
                val ka = baseAmps / 1000.0
                val ma = baseAmps * 1000.0
                ConversionOutput.SingleValueResult(
                    value = baseAmps,
                    unitEn = "A",
                    noteEn = "Conversion: ${formatNominal(ka)} kA | ${formatNominal(ma)} mA"
                )
            }
        }

        "temp_conversion" -> {
            if (d1 == null) {
                ConversionOutput.Invalid()
            } else {
                val celsius = when (variantId) {
                    "F" -> (d1 - 32.0) * 5.0 / 9.0
                    "K" -> d1 - 273.15
                    else -> d1
                }
                val fahr = celsius * 9.0 / 5.0 + 32.0
                val kelvin = celsius + 273.15
                ConversionOutput.SingleValueResult(
                    value = celsius,
                    unitEn = "°C",
                    noteEn = "Formulas: °F = (°C × 9/5) + 32 | K = °C + 273.15 -> ${String.format(Locale.US, "%.2f", fahr)} °F | ${String.format(Locale.US, "%.2f", kelvin)} K"
                )
            }
        }

        else -> ConversionOutput.Invalid()
    }
}
