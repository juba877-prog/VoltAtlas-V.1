package com.example.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.components.DsCard
import com.example.ui.designsystem.components.DsMetricTile
import com.example.ui.designsystem.components.DsSectionHeader
import com.example.ui.designsystem.components.DsStatusBadge
import com.example.ui.designsystem.components.DsStatusVariant
import com.example.ui.designsystem.theme.DsSpacing
import com.example.ui.designsystem.theme.DsTheme
import com.example.ui.designsystem.theme.DsType

private data class PreviewCircuitItem(
    val name: String,
    val tier: String,
    val cable: String,
    val ibA: String,
    val vdPct: String,
    val status: DsStatusVariant
)

private val samplePreviewCircuits = listOf(
    PreviewCircuitItem("MDB-01", "Tier 1 • Main", "4x185 mm² Cu/XLPE", "345.2 A", "0.85%", DsStatusVariant.Pass),
    PreviewCircuitItem("SMDB-Lighting", "Tier 2 • Sub", "4x95 mm² Cu/XLPE", "162.0 A", "1.42%", DsStatusVariant.Pass),
    PreviewCircuitItem("DB-HVAC-01", "Tier 3 • Branch", "4x35 mm² Cu/XLPE", "84.5 A", "3.80%", DsStatusVariant.Warning),
    PreviewCircuitItem("DB-Emergency", "Tier 3 • Branch", "4x25 mm² Cu/XLPE", "48.0 A", "1.10%", DsStatusVariant.Pass),
    PreviewCircuitItem("MCC-Pump-Station", "Tier 2 • Sub", "4x120 mm² Cu/XLPE", "210.0 A", "4.65%", DsStatusVariant.Fail),
    PreviewCircuitItem("DB-UPS-Critical", "Tier 3 • Branch", "4x50 mm² Cu/XLPE", "92.0 A", "0.95%", DsStatusVariant.Pass)
)

/**
 * Previews the adaptive multi-column circuit grid across all standard form factors:
 * Phone (1 column), Foldable (2 columns), Tablet (2-3 columns), and Desktop (3-4 columns).
 */
@FormFactorPreviews
@Composable
fun PreviewAdaptiveCircuitGrid() {
    DsTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 340.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = DsSpacing.md),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = DsSpacing.xs, bottom = 24.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        DsSectionHeader("Circuits (Adaptive Form Factors)")
                        Text(
                            text = "Grid automatically reflows: 1 col on Phone, 2 on Foldable/Tablet, 3+ on Desktop.",
                            style = DsType.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(samplePreviewCircuits) { circ ->
                    DsCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(circ.name, style = DsType.titleMedium)
                                    Text(circ.tier, style = DsType.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DsStatusBadge(circ.status.name, circ.status)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(circ.cable, style = DsType.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Ib: ${circ.ibA}", style = DsType.bodySmall)
                                Text("VD: ${circ.vdPct}", style = DsType.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Previews the adaptive dashboard metrics reflow across all standard form factors.
 */
@FormFactorPreviews
@Composable
fun PreviewAdaptiveDashboardMetrics() {
    DsTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                DsSectionHeader("Key Metrics (Adaptive Reflow)")
                Spacer(Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { DsMetricTile("Active Circuits", "14", unit = "total", status = DsStatusVariant.Pass) }
                    item { DsMetricTile("Max Tx Load", "82.4", unit = "%", status = DsStatusVariant.Pass) }
                    item { DsMetricTile("Max Cum. VD", "3.42", unit = "%", status = DsStatusVariant.Warning) }
                    item { DsMetricTile("Max Isc (3φ)", "24.6", unit = "kA", status = DsStatusVariant.Pass) }
                }
            }
        }
    }
}

/**
 * Previews the Quick Engineering Two-Pane layout across all form factors.
 */
@FormFactorPreviews
@Composable
fun PreviewAdaptiveQuickEngineeringTwoPane() {
    DsTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(0.38f).fillMaxHeight()) {
                    com.example.ui.screens.QuickEngineeringHubScreen(
                        onSelectTool = {},
                        onBack = {}
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )
                Box(modifier = Modifier.weight(0.62f).fillMaxHeight()) {
                    com.example.navigation.QuickToolPlaceholder()
                }
            }
        }
    }
}

/**
 * Previews the adaptive multi-column Project Picker across all standard form factors:
 * Phone (1 column), Foldable (2 columns), Tablet (2-3 columns), and Desktop (3-4 columns).
 */
@FormFactorPreviews
@Composable
fun PreviewAdaptiveProjectPicker() {
    val sampleProjects = listOf(
        com.example.data.ProjectEntity(
            projectId = "P1",
            projectName = "Hospital Main Substation",
            clientName = "National Health Authority",
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L
        ),
        com.example.data.ProjectEntity(
            projectId = "P2",
            projectName = "Commercial Tower Phase 2",
            clientName = "Emaar Properties",
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L
        ),
        com.example.data.ProjectEntity(
            projectId = "P3",
            projectName = "Industrial Plant Feeder",
            clientName = "Petrofac Engineering",
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L
        ),
        com.example.data.ProjectEntity(
            projectId = "P4",
            projectName = "Data Center Distribution",
            clientName = "Cloud Infrastructure Ltd",
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L
        )
    )

    com.example.ui.theme.VoltAtlasTheme(darkTheme = true) {
        com.example.ui.ProjectPickerScreen(
            projects = sampleProjects,
            onCreateProject = {},
            onOpenProject = {},
            onRenameProject = { _, _ -> },
            onDeleteProject = {}
        )
    }
}


