package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Catalog
import com.example.ui.theme.BorderColor
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.SleekFail
import com.example.ui.theme.SleekPass
import com.example.ui.theme.SleekPrimary
import com.example.ui.theme.SleekWarning
import com.example.ui.theme.SlateTextSecondary
import com.example.ui.theme.ThemeTextColor
import kotlin.math.abs

/**
 * Navigation 3 dialog-scene contents for the workspace (Phase 3.2, slice 4).
 *
 * These replace the former MainLayout-owned AlertDialogs: the dialog window is now
 * provided by [androidx.navigation3.scene.DialogSceneStrategy] from the back stack,
 * so the content is a plain Material surface and the stack owns dismissal.
 */
@Composable
fun WorkspaceNewProjectDialogContent(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = DarkCardBg,
        modifier = Modifier.widthIn(max = 560.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text(
                "Start New Empty Project?",
                color = ThemeTextColor,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "This will delete the current circuits and transformers from Room. This action cannot be undone.",
                color = SlateTextSecondary
            )
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = ThemeTextColor)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onConfirm) {
                    Text("Delete and Start", color = SleekWarning)
                }
            }
        }
    }
}

@Composable
fun WorkspaceDiagnosticsDialogContent(
    state: UiState.Success,
    onDismiss: () -> Unit
) {
    val diagnostics = Catalog.getDiagnostics()
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = DarkCardBg,
        modifier = Modifier.widthIn(max = 560.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = "Database Icon",
                    tint = SleekPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Catalog Diagnostics", color = ThemeTextColor, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Verifying JSON assets and specifications parsed in memory:",
                    color = SlateTextSecondary,
                    fontSize = 13.sp
                )
                HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                DiagnosticRow(label = "cable_types.json", loaded = Catalog.cableTypes.isNotEmpty(), count = Catalog.cableTypes.size)
                DiagnosticRow(label = "data_sheet.json", loaded = Catalog.dataSheet.isNotEmpty(), count = Catalog.dataSheet.size)
                DiagnosticRow(label = "cable_catalog.json", loaded = Catalog.cableCatalog.isNotEmpty(), count = Catalog.cableCatalog.size)
                DiagnosticRow(label = "breaker_catalog.json", loaded = Catalog.breakers.isNotEmpty(), count = Catalog.breakers.size)
                DiagnosticRow(label = "accessory_catalog.json", loaded = Catalog.accessories.isNotEmpty(), count = Catalog.accessories.size)
                DiagnosticRow(label = "derating_data.json", loaded = Catalog.deratingData != null)
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                Text("Catalog Integrity Metrics (Status: ${diagnostics.status})", color = SleekPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Defined Families: ${diagnostics.definedFamilies}", color = ThemeTextColor, fontSize = 12.sp)
                Text("Supported Families: ${diagnostics.supportedFamilies}", color = ThemeTextColor, fontSize = 12.sp)
                Text("Unsupported Families: ${diagnostics.unsupportedFamilies}", color = ThemeTextColor, fontSize = 12.sp)
                Text("Cable Records: ${diagnostics.cableRecords}", color = ThemeTextColor, fontSize = 12.sp)
                Text("Missing R/X: ${diagnostics.missingRx}", color = ThemeTextColor, fontSize = 12.sp)
                Text("Duplicate Keys: ${diagnostics.duplicateKeys}", color = ThemeTextColor, fontSize = 12.sp)
                Text("Orphan Type Codes: ${diagnostics.orphanTypeCodes}", color = ThemeTextColor, fontSize = 12.sp)
                val shop1Circuit = state.circuits.firstOrNull { it.circuitName.trim().equals("SHOP-1", ignoreCase = true) }
                if (shop1Circuit != null) {
                    val shop1Result = state.sizingResults[shop1Circuit.id]
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                    Text("SHOP-1 Diagnostic Trace:", color = SleekPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val selectedType = Catalog.cableTypes.firstOrNull {
                        it.cableType.trim().equals(shop1Circuit.cableType.trim(), ignoreCase = true)
                    }
                    val typeCode = selectedType?.code ?: -1
                    val candidates = Catalog.cableCatalog.filter {
                        it.mfg.trim().equals(shop1Circuit.manufacturer.trim(), ignoreCase = true) &&
                            it.typeCode == typeCode
                    }
                    val matchingRxCount = Catalog.dataSheet.count { sheet ->
                        candidates.any { c -> abs(sheet.size - c.size) <= 1e-9 }
                    }
                    Text("• circuit.id: ${shop1Circuit.id}", color = ThemeTextColor, fontSize = 11.sp)
                    Text("• circuitName: ${shop1Circuit.circuitName}", color = ThemeTextColor, fontSize = 11.sp)
                    Text("• loadKVA: ${shop1Circuit.loadKVA}", color = ThemeTextColor, fontSize = 11.sp)
                    Text("• voltageV: ${shop1Circuit.voltageV}", color = ThemeTextColor, fontSize = 11.sp)
                    Text("• pf: ${shop1Circuit.pf}", color = ThemeTextColor, fontSize = 11.sp)
                    Text("• safetyFactor: ${shop1Circuit.safetyFactor}", color = ThemeTextColor, fontSize = 11.sp)
                    Text("• manufacturer: '[${shop1Circuit.manufacturer}]'", color = ThemeTextColor, fontSize = 11.sp)
                    Text("• cableType: '[${shop1Circuit.cableType}]'", color = ThemeTextColor, fontSize = 11.sp)
                    Text("• installation: '${shop1Circuit.installation}'", color = ThemeTextColor, fontSize = 11.sp)
                    Text("• parallelRuns: ${shop1Circuit.parallelRuns}", color = ThemeTextColor, fontSize = 11.sp)
                    Text("• overrideCableSize: ${shop1Circuit.overrideCableSize}", color = ThemeTextColor, fontSize = 11.sp)
                    Text("• overrideDeviceSize: ${shop1Circuit.overrideDeviceSize}", color = ThemeTextColor, fontSize = 11.sp)
                    Text("• resolved CableTypeEntry code: $typeCode", color = ThemeTextColor, fontSize = 11.sp)
                    Text("• number of manufacturer/type candidates: ${candidates.size}", color = ThemeTextColor, fontSize = 11.sp)
                    Text("• matching R/X row count: $matchingRxCount", color = ThemeTextColor, fontSize = 11.sp)
                    Text(
                        "• final failReason: '${shop1Result?.failReason ?: ""}'",
                        color = if (shop1Result?.status?.contains("FAIL") == true) SleekFail else SleekPass,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (diagnostics.lastErrorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Last Error Message:", color = SleekWarning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SleekFail.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = diagnostics.lastErrorMessage ?: "",
                            color = SleekFail,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SleekPass.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓ ALL ASSETS LOADED SUCCESSFULLY",
                            color = SleekPass,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = SleekPrimary)
                }
            }
        }
    }
}

/** Catalog asset row used by the diagnostics dialog scene (moved from MainLayout). */
@Composable
private fun DiagnosticRow(label: String, loaded: Boolean, count: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (loaded) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = if (loaded) "Loaded" else "Not loaded",
            tint = if (loaded) SleekPass else SleekFail,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = ThemeTextColor,
            fontSize = 13.sp
        )
        if (count != null) {
            Text(
                text = if (count == 0) "0" else count.toString(),
                color = if (count == 0) SleekWarning else SlateTextSecondary,
                fontSize = 13.sp
            )
        } else {
            Text(
                text = if (loaded) "OK" else "ERROR",
                color = if (loaded) SleekPass else SleekFail,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
