package com.example.ui.designsystem.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Power
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.components.DsBottomNavBar
import com.example.ui.designsystem.components.DsBottomNavItem
import com.example.ui.designsystem.components.DsCard
import com.example.ui.designsystem.components.DsFab
import com.example.ui.designsystem.components.DsListRowItem
import com.example.ui.designsystem.components.DsMetricTile
import com.example.ui.designsystem.components.DsSectionHeader
import com.example.ui.designsystem.components.DsSelectableChip
import com.example.ui.designsystem.components.DsStatusBadge
import com.example.ui.designsystem.components.DsStatusVariant
import com.example.ui.designsystem.components.DsSummaryStatCard
import com.example.ui.designsystem.components.DsTopAppBar
import com.example.ui.designsystem.theme.DsColor
import com.example.ui.designsystem.theme.DsSpacing
import com.example.ui.designsystem.theme.DsTheme
import com.example.ui.designsystem.theme.DsType

/**
 * DEV / QA artifact ONLY — renders every design token + component in the Dark
 * theme so the foundation can be visually verified. INTENTIONALLY NOT wired
 * into the production navigation graph.
 */
@Composable
fun ComponentGalleryScreen() {
    DsTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize(), color = DsColor.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.lg)
            ) {
                Text(
                    text = "VoltAtlas — Design System",
                    style = DsType.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Phase UI-0 · dev-only gallery · dark theme",
                    style = DsType.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ── Tokens ──────────────────────────────────────────────
                DsSectionHeader(title = "Color tokens")
                ColorTokenRow()
                DsSectionHeader(title = "Primary & semantic")
                SemanticTokenRow()

                // ── Cards ───────────────────────────────────────────────
                DsSectionHeader(title = "DsCard")
                DsCard {
                    Text(
                        text = "Canonical container — surfaceContainer + tonal elevation.",
                        style = DsType.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(DsSpacing.sm))
                    DsStatusBadge(text = "no borders", variant = DsStatusVariant.Info)
                }

                // ── Status badges ───────────────────────────────────────
                DsSectionHeader(title = "DsStatusBadge")
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    DsStatusBadge(text = "PASS", variant = DsStatusVariant.Pass)
                    DsStatusBadge(text = "WARNING", variant = DsStatusVariant.Warning)
                    DsStatusBadge(text = "FAIL", variant = DsStatusVariant.Fail)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    DsStatusBadge(text = "INFO", variant = DsStatusVariant.Info)
                    DsStatusBadge(text = "STANDBY", variant = DsStatusVariant.Neutral)
                }

                // ── Metric tiles ────────────────────────────────────────
                DsSectionHeader(title = "DsMetricTile")
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                    DsMetricTile(
                        label = "Short-circuit",
                        value = "12.4",
                        unit = "kA",
                        status = DsStatusVariant.Pass,
                        modifier = Modifier.weight(1f)
                    )
                    DsMetricTile(
                        label = "Coordination",
                        value = "3",
                        unit = "of 7",
                        status = DsStatusVariant.Warning,
                        modifier = Modifier.weight(1f)
                    )
                }
// ── Summary stat card ───────────────────────────────────
                DsSectionHeader(title = "DsSummaryStatCard")
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                    DsSummaryStatCard(
                        icon = Icons.Filled.AccountTree,
                        value = "7",
                        caption = "Total circuits analyzed",
                        modifier = Modifier.weight(1f)
                    )
                    DsSummaryStatCard(
                        icon = Icons.Filled.Check,
                        value = "6",
                        caption = "Coordinated",
                        modifier = Modifier.weight(1f)
                    )
                }

                // ── Chips ───────────────────────────────────────────────
                DsSectionHeader(title = "DsSelectableChip")
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    DsSelectableChip(text = "All", selected = true, onClick = {})
                    DsSelectableChip(text = "Tier 1", selected = false, onClick = {})
                    DsSelectableChip(text = "Critical", selected = false, onClick = {})
                }

                // ── Section header w/ action ────────────────────────────
                DsSectionHeader(
                    title = "DsSectionHeader (with action)",
                    trailingAction = {
                        Text(
                            text = "View all",
                            style = DsType.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                )

                // ── List rows ───────────────────────────────────────────
                DsSectionHeader(title = "DsListRowItem")
                DsListRowItem(
                    title = "MCC-01 Feeder",
                    subtitle = "Ats · 800 A",
                    icon = Icons.Filled.Home,
                    trailingValue = "0.4 kA",
                    status = DsStatusVariant.Pass,
                    onClick = {}
                )
                DsListRowItem(
                    title = "TR-02 Transformer",
                    subtitle = "1500 kVA · 11/0.4 kV",
                    icon = Icons.Filled.Power,
                    trailingValue = "95 %",
                    status = DsStatusVariant.Warning,
                    onClick = {}
                )

                // ── FAB ─────────────────────────────────────────────────
                DsSectionHeader(title = "DsFab")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DsFab(onClick = {})
                    DsFab(onClick = {}, text = "New Circuit")
                }

                // ── Top bar ─────────────────────────────────────────────
                DsSectionHeader(title = "DsTopAppBar")
                DsTopAppBar(
                    projectName = "AL-Ain Plant",
                    screenTitle = "Dashboard",
                    onBackClick = {},
                    onPdfClick = {},
                    onAssistantClick = {},
                    onMenuClick = {}
                )

                // ── Bottom nav ──────────────────────────────────────────
                DsSectionHeader(title = "DsBottomNavBar")
                DsBottomNavBar(selected = DsBottomNavItem.Home, onSelect = {})
            }
        }
    }
}
@Composable
private fun ColorTokenRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        TokenSwatch("bg", DsColor.background)
        TokenSwatch("surface", DsColor.surface)
        TokenSwatch("container", DsColor.surfaceContainer)
        TokenSwatch("high", DsColor.surfaceContainerHigh)
        TokenSwatch("outline", DsColor.outline)
        TokenSwatch("outlineVar", DsColor.outlineVariant)
        TokenSwatch("onSurface", DsColor.onSurface)
        TokenSwatch("onSurfVar", DsColor.onSurfaceVariant)
    }
}

@Composable
private fun SemanticTokenRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        TokenSwatch("primary", DsColor.primary)
        TokenSwatch("info", DsColor.info)
        TokenSwatch("pass", DsColor.success)
        TokenSwatch("warn", DsColor.warning)
        TokenSwatch("fail", DsColor.danger)
        TokenSwatch("neutral", DsColor.neutral)
    }
}

@Composable
private fun TokenSwatch(name: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.height(DsSpacing.xs))
        Text(
            text = name,
            style = DsType.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}