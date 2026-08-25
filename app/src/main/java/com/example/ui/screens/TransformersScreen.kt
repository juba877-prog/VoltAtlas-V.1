package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.TransformerEngine
import com.example.ui.EngineeringViewModel
import com.example.ui.UiState
import com.example.ui.theme.*
import com.example.ui.designsystem.icons.ElectricalIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransformersScreen(state: UiState.Success, viewModel: EngineeringViewModel) {
    val txId by viewModel.selectedTransformerId.collectAsState()
    val activeTx = state.transformers.firstOrNull { it.transformerId == txId } ?: state.transformers.firstOrNull()
    val result = activeTx?.let { state.transformerResults[it.transformerId] }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (activeTx == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, ThemeBorderSubtle)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
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
                            color = ThemeTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No transformer yet. Add one from a circuit's Fed From, or here. Its size is calculated from the loads you assign.",
                            color = ThemeTextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.addNewTransformerInline()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ThemePrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Add Transformer", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
            return@LazyColumn
        }

        item {
            // Dropdown selection row with Add (+) support
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select Transformer:", color = ThemeTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
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
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectedTransformerId.value = tx.transformerId },
                                label = { Text(tx.transformerId, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else ThemeTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ThemePrimary,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                modifier = Modifier.testTag("tx_tab_${tx.transformerId}")
                            )
                        }
                    }
                    
                    // Button to add an additional transformer
                    IconButton(
                        onClick = {
                            viewModel.addNewTransformerInline()
                        },
                        modifier = Modifier.size(32.dp).testTag("add_additional_tx_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Transformer",
                            tint = ThemePrimary
                        )
                    }
                }
            }
        }

        if (result != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, ThemeBorderSubtle),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(activeTx.transformerName, color = ThemePrimary, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text("Assigned circuits: ${result.circuitsAssigned}", color = ThemeTextSecondary, fontSize = 12.sp, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (state.transformers.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.deleteTransformer(activeTx) },
                                    modifier = Modifier.testTag("delete_tx_btn_${activeTx.transformerId}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Transformer",
                                        tint = ThemeStatusFail
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = "${String.format("%.1f", result.loadingPct)}%",
                                color = if (result.loadingPct <= 100) ThemeStatusPass else ThemeStatusFail,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                maxLines = 1
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = ThemeBorderSubtle)
                        Spacer(modifier = Modifier.height(16.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Insulation Type", color = ThemeTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text(activeTx.txType, color = ThemePrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Connected Total Load", color = ThemeTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("${String.format("%.2f", result.connectedLoad)} kVA", color = ThemeTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Max Active Demand", color = ThemeTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("${String.format("%.2f", result.demandLoad)} kW", color = ThemeTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("FLC (Incomer Rating)", color = ThemeTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("${String.format("%.2f", result.flc)} A", color = ThemeTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Recommended Size", color = ThemeTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("${String.format("%.0f", result.recommendedSize)} kVA", color = ThemePrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Operating Size", color = ThemeTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("${String.format("%.0f", result.selectedSize)} kVA", color = ThemeTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Impedance Zt (%Z / mΩ)", color = ThemeTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("${String.format("%.2f", activeTx.percentZ)}% (${String.format("%.2f", result.zt)} mΩ)", color = ThemeTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Isc 3-Phase", color = ThemeTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("${String.format("%.2f", result.isc3Phase)} kA", color = ThemeStatusWarning, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Isc 1-Phase", color = ThemeTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("${String.format("%.2f", result.isc1Phase)} kA", color = ThemeStatusWarning, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Interactive Override Settings Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, ThemeBorderSubtle),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("TRANSFORMER SPECIFICATIONS & OVERRIDES", color = ThemeTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // 1. Transformer Type Selection (Oil-Immersed vs Dry Type Cast Resin)
                        Text("Transformer Insulation Type:", color = ThemeTextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val types = listOf("Dry Type (Cast Resin)", "Oil-Immersed")
                            types.forEach { type ->
                                val isSelected = activeTx.txType.contains(if (type.contains("Oil")) "Oil" else "Dry", ignoreCase = true) ||
                                                 activeTx.txType.trim().equals(type.trim(), ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        val defaultZ = TransformerEngine.getDefaultPercentZ(type, activeTx.kVA)
                                        viewModel.updateTransformer(
                                            activeTx.copy(txType = type, percentZ = defaultZ)
                                        )
                                    },
                                    label = { Text(type, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else ThemeTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ThemePrimary,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = ThemeBorderSubtle)
                        Spacer(modifier = Modifier.height(12.dp))

                        // 2. Capacity Sizing Mode (Auto vs Manual)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Auto-recommend capacity", color = ThemeTextPrimary, fontSize = 13.sp)
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
                            Text("Manual Capacity Rating (kVA):", color = ThemeTextSecondary, fontSize = 12.sp)
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
                                        focusedBorderColor = ThemePrimary,
                                        unfocusedBorderColor = ThemeBorderSubtle,
                                        focusedTextColor = ThemeTextPrimary,
                                        unfocusedTextColor = ThemeTextPrimary
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
                        HorizontalDivider(color = ThemeBorderSubtle)
                        Spacer(modifier = Modifier.height(12.dp))

                        // 3. Editable %Z Impedance Field
                        Text("Impedance %Z (Standard or Nameplate):", color = ThemeTextSecondary, fontSize = 12.sp)
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
                            trailingIcon = { Text("%", color = ThemeTextSecondary, modifier = Modifier.padding(end = 12.dp)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ThemePrimary,
                                unfocusedBorderColor = ThemeBorderSubtle,
                                focusedTextColor = ThemeTextPrimary,
                                unfocusedTextColor = ThemeTextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Auto-set per IEC 60076 / EN 50588-1 tables. Editable for real nameplate %Z.",
                            color = ThemeTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
