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
                        ?: "400 V"
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
