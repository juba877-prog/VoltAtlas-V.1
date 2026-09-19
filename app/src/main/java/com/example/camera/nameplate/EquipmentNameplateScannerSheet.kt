package com.example.camera.nameplate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentNameplateScannerSheet(
    initialScanResult: NameplateScanResult? = null,
    onApplyToMotorStarter: (powerKw: Double, voltageV: Double, pf: Double, eff: Double) -> Unit = { _, _, _, _ -> },
    onApplyToCableSizing: (currentAmps: Double, voltageV: Double, kw: Double) -> Unit = { _, _, _ -> },
    onDismiss: () -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("nameplate_scanner_sheet")
    ) {
        EquipmentNameplateScannerContent(
            scanResult = initialScanResult,
            onApplyToMotorStarter = onApplyToMotorStarter,
            onApplyToCableSizing = onApplyToCableSizing,
            onClose = onDismiss
        )
    }
}

@Composable
fun EquipmentNameplateScannerContent(
    scanResult: NameplateScanResult? = null,
    onApplyToMotorStarter: (powerKw: Double, voltageV: Double, pf: Double, eff: Double) -> Unit = { _, _, _, _ -> },
    onApplyToCableSizing: (currentAmps: Double, voltageV: Double, kw: Double) -> Unit = { _, _, _ -> },
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var editablePowerKw by remember(scanResult) { mutableStateOf(scanResult?.powerKw?.toString() ?: "37.0") }
    var editableVoltageV by remember(scanResult) { mutableStateOf(scanResult?.voltageV?.toInt()?.toString() ?: "400") }
    var editableCurrentA by remember(scanResult) { mutableStateOf(scanResult?.currentAmps?.toString() ?: "68.5") }
    var editablePf by remember(scanResult) { mutableStateOf(scanResult?.powerFactor?.toString() ?: "0.86") }
    var editableEff by remember(scanResult) { mutableStateOf(scanResult?.efficiencyPct?.toString() ?: "92.4") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Equipment Nameplate Scanner",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.testTag("close_scanner_button")) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        // Viewfinder / Reticle Frame
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .testTag("viewfinder_box"),
            contentAlignment = Alignment.Center
        ) {
            // Reticle guide overlay
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ElectricBolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "Align Equipment Nameplate within reticle",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
                Text(
                    text = "Supports IEC 60034 Motors & IEC 60076 Transformers",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
            }
        }

        // Detected Specs Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("detected_specs_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Detected Specifications (Review & Confirm)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // Input Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editablePowerKw,
                        onValueChange = { editablePowerKw = it },
                        label = { Text("Power (kW)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("scanner_power_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editableVoltageV,
                        onValueChange = { editableVoltageV = it },
                        label = { Text("Voltage (V)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("scanner_voltage_input"),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editableCurrentA,
                        onValueChange = { editableCurrentA = it },
                        label = { Text("FLA (Amps)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("scanner_current_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editablePf,
                        onValueChange = { editablePf = it },
                        label = { Text("cos φ") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("scanner_pf_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editableEff,
                        onValueChange = { editableEff = it },
                        label = { Text("Eff η (%)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("scanner_eff_input"),
                        singleLine = true
                    )
                }
            }
        }

        // Action Routing Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val kw = editablePowerKw.toDoubleOrNull() ?: 37.0
                    val v = editableVoltageV.toDoubleOrNull() ?: 400.0
                    val pf = editablePf.toDoubleOrNull() ?: 0.86
                    val eff = editableEff.toDoubleOrNull() ?: 92.4
                    onApplyToMotorStarter(kw, v, pf, eff)
                    onClose()
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("apply_to_motor_button")
            ) {
                Text("Apply to Motor Starter")
            }

            OutlinedButton(
                onClick = {
                    val amps = editableCurrentA.toDoubleOrNull() ?: 68.5
                    val v = editableVoltageV.toDoubleOrNull() ?: 400.0
                    val kw = editablePowerKw.toDoubleOrNull() ?: 37.0
                    onApplyToCableSizing(amps, v, kw)
                    onClose()
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("apply_to_cable_button")
            ) {
                Text("Apply to Cable")
            }
        }
    }
}
