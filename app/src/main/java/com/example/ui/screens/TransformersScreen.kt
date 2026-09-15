package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import com.example.ui.designsystem.components.DsInfoChip
import com.example.ui.designsystem.components.DsMetricTile
import com.example.ui.designsystem.components.DsSectionHeader
import com.example.ui.designsystem.components.DsSelectableChip
import com.example.ui.designsystem.components.DsStatusBadge
import com.example.ui.designsystem.components.DsStatusVariant
import com.example.ui.designsystem.icons.ElectricalIcons
import com.example.ui.designsystem.theme.DsShape
import com.example.ui.designsystem.theme.DsSpacing
import com.example.ui.designsystem.theme.DsType
import com.example.ui.designsystem.theme.dsExtendedColors

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface

/**
 * TRANSFORMERS re-skin #01 (Phase 2 + V3 polish): visual-only restyle onto the
 * approved Ds design system with high-density engineering tool layout.
 * Every value keeps its pre-existing binding:
 *  - Vector Group / Secondary Voltage -> read-only displays of existing data.
 *  - Loading band colors              -> derived ONLY from the existing
 *    `result.status` strings emitted by TransformerEngine (VERIFIED / REVIEW /
 *    OVERLOADED). Sourced from theme-aware [MaterialTheme.dsExtendedColors].
 *    Loading bar appearance is UNCHANGED (user instruction).
 *  - Assigned Circuits list           -> display-only compact chips derived
 *    from the EXISTING circuit->transformer assignment (`CircuitEntity.transformerId`),
 *    mirroring TransformerEngine's belongsToTx filter. No recompute, no new field.
 * No new data, no fabricated values, no engine/ViewModel changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransformersScreen(state: UiState.Success, viewModel: EngineeringViewModel) {
    val txId by viewModel.selectedTransformerId.collectAsState()
    val activeTx = state.transformers.firstOrNull { it.transformerId == txId } ?: state.transformers.firstOrNull()
    val result = activeTx?.let { state.transformerResults[it.transformerId] }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

        if (activeTx == null) {
            item {
                DsCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
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
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            style = DsType.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No transformer yet. Add one from a circuit's Fed From, or here. Its size is calculated from the loads you assign.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = DsType.bodySmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.addNewTransformerInline() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text("Add Transformer", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, style = DsType.labelMedium)
                        }
                    }
                }
            }
            return@LazyColumn
        }

        item {
            // Transformer selector row with Add (+) support — dynamic full-width scrollable row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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

                FilledTonalIconButton(
                    onClick = { viewModel.addNewTransformerInline() },
                    modifier = Modifier.size(36.dp).testTag("add_additional_tx_btn"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Transformer",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (result != null) {

            item {
                // Hero card: identity, illustration, status, delete, loading band + compact summary KPIs.
                DsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                activeTx.transformerName,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeTx.transformerId,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = DsType.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DsStatusBadge(
                                    text = activeTx.status,
                                    variant = if (activeTx.status.equals("Active", ignoreCase = true)) DsStatusVariant.Pass else DsStatusVariant.Neutral
                                )
                                Text(
                                    "Insulation: ${activeTx.txType}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = DsType.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Image(
                            painter = painterResource(id = R.drawable.ic_transformer_illustration),
                            contentDescription = null, // purely decorative
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        if (state.transformers.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.deleteTransformer(activeTx) },
                                modifier = Modifier.size(36.dp).testTag("delete_tx_btn_${activeTx.transformerId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Transformer",
                                    tint = MaterialTheme.dsExtendedColors.danger,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Loading % — colored by the existing three-band result.status.
                    val band = loadingBandColor(result.status)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Loading", color = MaterialTheme.colorScheme.onSurfaceVariant, style = DsType.labelSmall)
                        Text(
                            text = "${String.format("%.1f", result.loadingPct)}%",
                            color = band,
                            fontWeight = FontWeight.Bold,
                            style = DsType.titleMedium,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (result.loadingPct / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                        color = band,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        strokeCap = StrokeCap.Round
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Compact Summary KPI Strip (2x2 grid inside hero card)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompactKpiTile("Connected Load", String.format("%.2f", result.connectedLoad), unit = "kVA", modifier = Modifier.weight(1f))
                            CompactKpiTile("Max Active Demand", String.format("%.2f", result.demandLoad), unit = "kW", modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompactKpiTile("Recommended Size", String.format("%.0f", result.recommendedSize), unit = "kVA", modifier = Modifier.weight(1f))
                            CompactKpiTile("Operating Size", String.format("%.0f", result.selectedSize), unit = "kVA", modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            item {
                // Electrical Ratings card — compact 3x2 grid
                Column {
                    DsSectionHeader(title = "Electrical Ratings")
                    Spacer(modifier = Modifier.height(8.dp))
                    DsCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DsMetricTile("FLC (Incomer Rating)", String.format("%.2f", result.flc), Modifier.weight(1f), unit = "A", dense = true)
                                DsMetricTile("Impedance Zt", "${String.format("%.2f", activeTx.percentZ)}% (${String.format("%.2f", result.zt)} mΩ)", Modifier.weight(1f), dense = true)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DsMetricTile("Isc 3-Phase", String.format("%.2f", result.isc3Phase), Modifier.weight(1f), unit = "kA", status = DsStatusVariant.Warning, dense = true)
                                DsMetricTile("Isc 1-Phase", String.format("%.2f", result.isc1Phase), Modifier.weight(1f), unit = "kA", status = DsStatusVariant.Warning, dense = true)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Decision A: read-only display of the pre-existing vectorGroup.
                                DsMetricTile("Vector Group", result.vectorGroup, Modifier.weight(1f), dense = true)
                                // Decision B: read-only display of the pre-existing per-TX
                                // secondary-voltage snapshot (`result.voltage`). No primary
                                // voltage exists; no live GlobalPreferences re-sync (AUDIT-03).
                                DsMetricTile("Secondary Voltage (LV)", String.format("%.0f", result.voltage), Modifier.weight(1f), unit = "V", dense = true)
                            }
                        }
                    }
                }
            }

            item {
                // Override settings card — same bindings, restyled and compact.
                Column {
                    DsSectionHeader(title = "Transformer Specifications & Overrides")
                    Spacer(modifier = Modifier.height(8.dp))
                    DsCard {
                        // 1. Insulation type selection — chips stacked full-width so
                        // "Dry Type (Cast Resin)" never truncates.
                        Text("Transformer Insulation Type:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = DsType.labelSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(10.dp))

                        // 2. Capacity sizing mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Auto-recommend capacity",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = DsType.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
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
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Manual Capacity Rating (kVA):", color = MaterialTheme.colorScheme.onSurfaceVariant, style = DsType.labelSmall)
                            Spacer(modifier = Modifier.height(4.dp))

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
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
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

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(10.dp))

                        // 3. Editable %Z Impedance Field
                        Text("Impedance %Z (Standard or Nameplate):", color = MaterialTheme.colorScheme.onSurfaceVariant, style = DsType.labelSmall)
                        Spacer(modifier = Modifier.height(4.dp))
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
                            trailingIcon = { Text("%", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Auto-set per IEC 60076 / EN 50588-1 tables. Editable for real nameplate %Z.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = DsType.bodySmall
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(10.dp))

                        // 4. Primary (HV/MV) supply voltage — G5. Real column-backed;
                        // drives the genuine SLD "Utility Grid (X kV)" label.
                        Text("Primary Supply Voltage (V):", color = MaterialTheme.colorScheme.onSurfaceVariant, style = DsType.labelSmall)
                        Text(
                            "MV/HV side of the transformer, e.g. 11000 for 11 kV, 22000 for 22 kV.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = DsType.bodySmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        var hvVText by remember(activeTx.primaryVoltageV) { mutableStateOf(activeTx.primaryVoltageV?.toString() ?: "") }
                        OutlinedTextField(
                            value = hvVText,
                            onValueChange = { input ->
                                hvVText = input
                                val parsed = input.toIntOrNull()
                                viewModel.updateTransformer(
                                    activeTx.copy(primaryVoltageV = parsed?.takeIf { it > 0 })
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            trailingIcon = { Text("V", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(10.dp))

                        // 5. Utility fault level S_kQ (MVA) — G4. When set, the grid's own
                        // impedance becomes part of the source-to-feeder fault path (IEC 60909).
                        Text("Utility Fault Level S_kQ (MVA):", color = MaterialTheme.colorScheme.onSurfaceVariant, style = DsType.labelSmall)
                        Text(
                            "Available short-circuit power at the point of supply. Leave blank for the infinite-grid convention.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = DsType.bodySmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        var skqText by remember(activeTx.gridFaultMVA) { mutableStateOf(activeTx.gridFaultMVA?.toString() ?: "") }
                        OutlinedTextField(
                            value = skqText,
                            onValueChange = { input ->
                                skqText = input
                                val parsed = input.toDoubleOrNull()
                                viewModel.updateTransformer(
                                    activeTx.copy(gridFaultMVA = parsed?.takeIf { it > 0.0 })
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            trailingIcon = { Text("MVA", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            item {
                // Assigned Circuits — display-only compact chips
                Column {
                    DsSectionHeader(title = "Assigned Circuits (${result.circuitsAssigned})")
                    Spacer(modifier = Modifier.height(8.dp))
                    DsCard {
                        val txIdClean = activeTx.transformerId.trim().lowercase()
                        val assigned = state.circuits.filter { c ->
                            val cTxId = c.transformerId.trim().lowercase()
                            if (cTxId.isNotEmpty()) {
                                cTxId == txIdClean
                            } else {
                                val fedFromClean = c.fedFrom.trim().lowercase()
                                fedFromClean == txIdClean || fedFromClean in listOf("transformer", "ats", "generator", "grid")
                            }
                        }
                        if (assigned.isEmpty()) {
                            Text(
                                "No circuits assigned to this transformer.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = DsType.bodySmall
                            )
                        } else {
                            val shown = assigned.take(4)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                shown.chunked(2).forEach { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        pair.forEach { c ->
                                            DsInfoChip(
                                                label = c.circuitName,
                                                trailingValue = "%.1f kVA".format(c.loadKVA),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (pair.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                                val more = (result.circuitsAssigned - shown.size).coerceAtLeast(0)
                                if (more > 0) {
                                    Text(
                                        text = "+$more More",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = DsType.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}


@Composable
private fun CompactKpiTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null
) {
    Box(
        modifier = modifier
            .clip(DsShape.card)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            Text(
                text = label,
                style = DsType.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = DsType.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (unit != null) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = unit,
                        style = DsType.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
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
 * Sourced from theme-aware [MaterialTheme.dsExtendedColors].
 */
@Composable
private fun loadingBandColor(status: String): Color {
    val ext = MaterialTheme.dsExtendedColors
    return when (status) {
        "VERIFIED" -> ext.success
        "REVIEW — CONNECTED LOAD EXCEEDS RATING" -> ext.warning
        "OVERLOADED — FAIL" -> ext.danger
        else -> ext.neutral
    }
}


