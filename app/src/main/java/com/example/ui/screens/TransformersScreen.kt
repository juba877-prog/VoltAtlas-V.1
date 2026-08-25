package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.TransformerEngine
import com.example.ui.EngineeringViewModel
import com.example.ui.UiState
import com.example.ui.designsystem.components.DsCard
import com.example.ui.designsystem.components.DsMetricTile
import com.example.ui.designsystem.components.DsSectionHeader
import com.example.ui.designsystem.components.DsSelectableChip
import com.example.ui.designsystem.components.DsStatusVariant
import com.example.ui.designsystem.icons.ElectricalIcons
import com.example.ui.designsystem.theme.DsColor

/**
 * TRANSFORMERS re-skin #01 (Phase 2): visual-only restyle onto the approved Ds
 * design system. Every value keeps its pre-existing binding — this screen only
 * renders what [EngineeringViewModel] / [UiState.Success] already expose today:
 *  - Vector Group          -> read-only display of the existing `vectorGroup`.
 *  - Secondary Voltage(LV) -> read-only display of the existing `result.voltage`
 *    (the per-transformer snapshot seeded at creation; see AUDIT-03).
 *  - Loading band colors   -> derived ONLY from the existing `result.status`
 *    strings emitted by TransformerEngine (VERIFIED / REVIEW / OVERLOADED).
 * No new data, no fabricated values, no engine/ViewModel changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransformersScreen(state: UiState.Success, viewModel: EngineeringViewModel) {
    val txId by viewModel.selectedTransformerId.collectAsState()
    val activeTx = state.transformers.firstOrNull { it.transformerId == txId } ?: state.transformers.firstOrNull()
    val result = activeTx?.let { state.transformerResults[it.transformerId] }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        if (activeTx == null) {
            item {
                DsCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = ElectricalIcons.Transformer,
                            contentDescription = "No Transformers",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Transformer Yet",
                            color = DsColor.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No transformer yet. Add one from a circuit's Fed From, or here. Its size is calculated from the loads you assign.",
                            color = DsColor.onSurfaceVariant,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.addNewTransformerInline() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text("Add Transformer", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
            return@LazyColumn
        }

        item {
            // Transformer selector row with Add (+) support — same actions as before.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select Transformer:", color = DsColor.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.transformers.forEach { tx ->
                            val isSelected = tx.transformerId == txId
                            DsSelectableChip(
                                text = tx.transformerId,
                                selected = isSelected,
                                onClick = { viewModel.selectedTransformerId.value = tx.transformerId },
                                modifier = Modifier.testTag("tx_tab_${tx.transformerId}")
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.addNewTransformerInline() },
                        modifier = Modifier.size(32.dp).testTag("add_additional_tx_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Transformer",
                            tint = DsColor.primary
                        )
                    }
                }
            }
        }

        if (result != null) {

            item {
                // Header card: identity, decorative illustration, delete, loading band.
                DsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                activeTx.transformerName,
                                color = DsColor.primary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("Assigned circuits: ${result.circuitsAssigned}", color = DsColor.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Image(
                            painter = painterResource(id = R.drawable.ic_transformer_illustration),
                            contentDescription = null, // purely decorative
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        if (state.transformers.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.deleteTransformer(activeTx) },
                                modifier = Modifier.testTag("delete_tx_btn_${activeTx.transformerId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Transformer",
                                    tint = DsColor.danger
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = DsColor.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Loading % — colored by the existing three-band result.status.
                    val band = loadingBandColor(result.status)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Loading", color = DsColor.onSurfaceVariant, fontSize = 11.sp)
                        Text(
                            text = "${String.format("%.1f", result.loadingPct)}%",
                            color = band,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (result.loadingPct / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)),
                        color = band,
                        trackColor = DsColor.surfaceContainerHigh,
                        strokeCap = StrokeCap.Round
                    )
                }
            }

            item {
                // Electrical Ratings grid — same values/formats as before, tile layout.
                DsSectionHeader(title = "Electrical Ratings")
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DsMetricTile("Connected Load", String.format("%.2f", result.connectedLoad), Modifier.weight(1f), unit = "kVA")
                        DsMetricTile("Max Active Demand", String.format("%.2f", result.demandLoad), Modifier.weight(1f), unit = "kW")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DsMetricTile("FLC (Incomer Rating)", String.format("%.2f", result.flc), Modifier.weight(1f), unit = "A")
                        DsMetricTile("Recommended Size", String.format("%.0f", result.recommendedSize), Modifier.weight(1f), unit = "kVA")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DsMetricTile("Operating Size", String.format("%.0f", result.selectedSize), Modifier.weight(1f), unit = "kVA")
                        DsMetricTile("Impedance Zt (%Z / mΩ)", "${String.format("%.2f", activeTx.percentZ)}% (${String.format("%.2f", result.zt)} mΩ)", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DsMetricTile("Isc 3-Phase", String.format("%.2f", result.isc3Phase), Modifier.weight(1f), unit = "kA", status = DsStatusVariant.Warning)
                        DsMetricTile("Isc 1-Phase", String.format("%.2f", result.isc1Phase), Modifier.weight(1f), unit = "kA", status = DsStatusVariant.Warning)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Decision A: read-only display of the pre-existing vectorGroup.
                        DsMetricTile("Vector Group", result.vectorGroup, Modifier.weight(1f))
                        // Decision B: read-only display of the pre-existing per-TX
                        // secondary-voltage snapshot (`result.voltage`). No primary
                        // voltage exists; no live GlobalPreferences re-sync (AUDIT-03).
                        DsMetricTile("Secondary Voltage (LV)", String.format("%.0f", result.voltage), Modifier.weight(1f), unit = "V")
                    }
                }
            }

            item {
                // Override settings card — same bindings, restyled.
                Column {
                    DsSectionHeader(title = "Transformer Specifications & Overrides")
                    Spacer(modifier = Modifier.height(12.dp))
                    DsCard {
                        // 1. Insulation type selection
                        Text("Transformer Insulation Type:", color = DsColor.onSurfaceVariant, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val types = listOf("Dry Type (Cast Resin)", "Oil-Immersed")
                            types.forEach { type ->
                                val isSelected = activeTx.txType.contains(if (type.contains("Oil")) "Oil" else "Dry", ignoreCase = true) ||
                                                 activeTx.txType.trim().equals(type.trim(), ignoreCase = true)
                                DsSelectableChip(
                                    text = type,
                                    selected = isSelected,
                                    onClick = {
                                        val defaultZ = TransformerEngine.getDefaultPercentZ(type, activeTx.kVA)
                                        viewModel.updateTransformer(
                                            activeTx.copy(txType = type, percentZ = defaultZ)
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = DsColor.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))

                        // 2. Capacity sizing mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Auto-recommend capacity", color = DsColor.onSurface, fontSize = 13.sp)
                            Switch(
                                checked = activeTx.sizeMode == "Auto",
                                onCheckedChange = { isChecked ->
                                    viewModel.updateTransformer(
                                        activeTx.copy(sizeMode = if (isChecked) "Auto" else "Manual")
                                    )
                                },
                                modifier = Modifier.testTag("tx_mode_switch")
                            )
                        }

                        if (activeTx.sizeMode == "Manual") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Manual Capacity Rating (kVA):", color = DsColor.onSurfaceVariant, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))

                            var capacityExpanded by remember { mutableStateOf(false) }
                            val iecRatings = listOf(50.0, 100.0, 160.0, 200.0, 250.0, 315.0, 400.0, 500.0, 630.0, 800.0, 1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0)

                            ExposedDropdownMenuBox(
                                expanded = capacityExpanded,
                                onExpandedChange = { capacityExpanded = !capacityExpanded }
                            ) {
                                OutlinedTextField(
                                    value = "${activeTx.manualKVA.toInt()} kVA",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = capacityExpanded) },
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = DsColor.primary,
                                        unfocusedBorderColor = DsColor.outlineVariant,
                                        focusedTextColor = DsColor.onSurface,
                                        unfocusedTextColor = DsColor.onSurface
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = capacityExpanded,
                                    onDismissRequest = { capacityExpanded = false }
                                ) {
                                    iecRatings.forEach { rating ->
                                        DropdownMenuItem(
                                            text = { Text("${rating.toInt()} kVA") },
                                            onClick = {
                                                capacityExpanded = false
                                                val defaultZ = TransformerEngine.getDefaultPercentZ(activeTx.txType, rating)
                                                viewModel.updateTransformer(
                                                    activeTx.copy(manualKVA = rating, kVA = rating, percentZ = defaultZ)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = DsColor.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))

                        // 3. Editable %Z Impedance Field
                        Text("Impedance %Z (Standard or Nameplate):", color = DsColor.onSurfaceVariant, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        var zText by remember(activeTx.percentZ) { mutableStateOf(activeTx.percentZ.toString()) }
                        OutlinedTextField(
                            value = zText,
                            onValueChange = { input ->
                                zText = input
                                val parsed = input.toDoubleOrNull()
                                if (parsed != null && parsed > 0.0) {
                                    viewModel.updateTransformer(activeTx.copy(percentZ = parsed))
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            trailingIcon = { Text("%", color = DsColor.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DsColor.primary,
                                unfocusedBorderColor = DsColor.outlineVariant,
                                focusedTextColor = DsColor.onSurface,
                                unfocusedTextColor = DsColor.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Auto-set per IEC 60076 / EN 50588-1 tables. Editable for real nameplate %Z.",
                            color = DsColor.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Approved three-band mapping over the EXISTING `TransformerResult.status`
 * strings emitted by TransformerEngine (no new thresholds, no recomputation):
 *  - "VERIFIED"                               -> success (green)
 *  - "REVIEW — CONNECTED LOAD EXCEEDS RATING" -> warning (amber)
 *  - "OVERLOADED — FAIL"                      -> danger (red)
 * The neutral fallback is defensive only; the engine never emits other values.
 */
private fun loadingBandColor(status: String): Color = when (status) {
    "VERIFIED" -> DsColor.success
    "REVIEW — CONNECTED LOAD EXCEEDS RATING" -> DsColor.warning
    "OVERLOADED — FAIL" -> DsColor.danger
    else -> DsColor.neutral
}
