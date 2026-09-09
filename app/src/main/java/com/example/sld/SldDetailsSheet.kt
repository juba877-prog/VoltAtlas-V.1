package com.example.sld

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.South
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SldDetailsSheet(
    node: SldNode,
    issues: List<SldValidationIssue>,
    onClose: () -> Unit,
    onFocusUpstreamPath: (String) -> Unit,
    onFocusDownstreamBranch: (String) -> Unit,
    onNavigateToTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val nodeIssues = issues.filter { it.affectedNodeId == node.id }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = DarkCardBg,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = node.displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemeTextColor
                    )
                    Text(
                        text = "Type: ${node.type.name} | ID: ${node.id}",
                        fontSize = 11.sp,
                        color = SlateTextSecondary
                    )
                }

                val statusColor = when (node.status) {
                    SldValidationStatus.VALID -> SleekPass
                    SldValidationStatus.WARNING -> SleekWarning
                    SldValidationStatus.INVALID -> SleekFail
                    SldValidationStatus.DISCONNECTED -> SlateTextSecondary
                    SldValidationStatus.DATA_REQUIRED -> SleekPrimary
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = node.status.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = BorderColor)

            // Engineering Parameters Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = DarkBackground),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Engineering Parameters & Results",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = SleekPrimary
                    )

                    val db = node.dataBlock
                    val displayVoltage = node.voltage?.takeIf { it.isNotBlank() }
                        ?: db.voltage?.takeIf { it.isNotBlank() }
                        ?: "Not set"
                    DetailRow("Voltage Rating", displayVoltage)
                    db.ratedKva?.let { DetailRow("Rating / Capacity", it) }
                    db.deviceTypeAndRating?.let { DetailRow("Protective Device", it) }
                    db.cableDescription?.let { DetailRow("Feeder Cable", "$it (${db.parallelRuns} run(s))") }
                    db.lengthM?.let { DetailRow("Route Length", it) }
                    db.ibAmp?.let { DetailRow("Design Current (Ib)", it) }
                    db.izAmp?.let { DetailRow("Effective Capacity (Iz)", it) }
                    db.voltageDropPct?.let { DetailRow("Circuit VD", it) }
                    db.cumulativeVdPct?.let { DetailRow("Cumulative VD", it) }
                    db.faultLevelKa?.let { DetailRow("3Ph Short Circuit", it) }
                    db.loadingPct?.let { DetailRow("Transformer Loading", it) }
                    // ---- DARK-DATA SLOTS (engine-backed, plumbed end-to-end) ----
                    db.interruptingRatingKa?.let { DetailRow("Interrupting Rating (Icu)", it) }
                    db.transformerZtMOhm?.let { DetailRow("Transformer Impedance (Zt)", it) }
                    db.transformerIsc1PhaseKa?.let { DetailRow("Transformer Isc (1Ph)", it) }
                    db.cableUtilizationPct?.let { DetailRow("Cable Utilization", it) }
                    db.cableNeutralMm2?.let { DetailRow("Neutral Conductor", it) }
                    db.cableCpcMm2?.let { DetailRow("CPC (Earth) Conductor", it) }
                    db.cableInstallation?.let { DetailRow("Installation Method", it) }
                    db.voltageDropLimitPct?.let { DetailRow("VD Design Limit", it) }
                    db.atsRequiredIcwKa?.let { DetailRow("ATS Required Icw", it) }
                    db.atsRequiredIcmKa?.let { DetailRow("ATS Required Icm", it) }
                    db.atsGoverningCurrentA?.let { DetailRow("ATS Governing Current", it) }
                    db.atsTransferTimeS?.let { DetailRow("ATS Transfer Time", it) }
                    db.upsBatteryAh?.let { DetailRow("UPS Battery Bank", it) }
                    db.upsBatteryStrings?.let { DetailRow("UPS Battery Strings", it) }
                    db.generatorMotorStartingKva?.let { DetailRow("Motor-Starting kVA", it) }
                    db.generatorStepLoadKva?.let { DetailRow("Step Load at Start", it) }
                    db.voltageFrequencyHz?.let { DetailRow("Output Frequency", it) }
                    // ---- BOARD HEADERS (stored on CircuitEntity) ----
                    db.busbarRatingA?.let { DetailRow("Busbar Rating", it) }
                    db.scRatingKA?.let { DetailRow("Board SC Rating", it) }
                    db.ipRating?.let { DetailRow("Ingress Protection", it) }
                    db.panelLocation?.let { DetailRow("Panel Location", it) }
                    db.numberOfWays?.let { DetailRow("Number of Ways", it) }
                    db.mainIncoming?.let { DetailRow("Main Incoming", it) }
                    // ---- BOARD AGGREGATION (PanelScheduleEngine roll-up — 2A) ----
                    db.boardTotalConnectedKva?.let { DetailRow("Total Connected Load (TCL)", it) }
                    db.boardDemandKva?.let { DetailRow("Total Demand Load (TDL)", it) }
                    db.boardDemandFactorPct?.let { DetailRow("Demand Factor", it) }
                    db.boardDesignCurrentA?.let { DetailRow("Board Design Current", it) }
                    db.boardUnbalancePct?.let { DetailRow("Phase Unbalance", it) }
                    // ---- PROTECTION / ADS VERDICTS (engine-backed) ----
                    db.protectionOverload?.let { DetailRow("Overload (Ib≤In≤Iz)", it) }
                    db.protectionShortCircuit?.let { DetailRow("Short Circuit (Icu≥Isc)", it) }
                    db.protectionCableWithstand?.let { DetailRow("Cable Withstand", it) }
                    db.protectionSelectivity?.let { DetailRow("Selectivity", it) }
                    val zsPair = listOfNotNull(db.adsZsOhm, db.adsMaxZsOhm)
                        .takeIf { it.size == 2 }?.joinToString(" ≤ ") ?: db.adsZsOhm
                    zsPair?.let { DetailRow("Earth Loop (Zs ≤ Zs,max)", it) }
                    db.adsDisconnectionTime?.let { DetailRow("Disconnection Time", it) }
                    db.adsStatus?.let { DetailRow("ADS Verdict", it) }
                    DetailRow("Result Provenance", node.provenance.name)
                }
            }

            // Validation Issues specific to this node
            if (nodeIssues.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkBackground),
                    border = BorderStroke(1.dp, SleekWarning.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Validation Flags",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = SleekWarning
                        )
                        nodeIssues.forEach { issue ->
                            Text(
                                text = "• [${issue.issueCode}] ${issue.failedCondition}",
                                fontSize = 11.sp,
                                color = ThemeTextColor
                            )
                        }
                    }
                }
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onFocusUpstreamPath(node.id) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SleekPrimary)
                ) {
                    Icon(Icons.Default.North, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Upstream", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { onFocusDownstreamBranch(node.id) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SleekPrimary)
                ) {
                    Icon(Icons.Default.South, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Downstream", fontSize = 11.sp)
                }

                Button(
                    onClick = {
                        onClose()
                        onNavigateToTab(node.targetTab)
                    },
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open Details", fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 11.sp, color = SlateTextSecondary)
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (value.contains("DATA REQUIRED")) SleekWarning else ThemeTextColor
        )
    }
}
