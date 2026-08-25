package com.example.ui.designsystem.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Power
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.components.DsBottomNavBar
import com.example.ui.designsystem.components.DsBottomNavItem
import com.example.ui.designsystem.components.DsCard
import com.example.ui.designsystem.components.DsFab
import com.example.ui.designsystem.components.DsInfoChip
import com.example.ui.designsystem.components.DsListRowItem
import com.example.ui.designsystem.components.DsMetricTile
import com.example.ui.designsystem.components.DsSectionHeader
import com.example.ui.designsystem.components.DsSelectableChip
import com.example.ui.designsystem.components.DsStatusBadge
import com.example.ui.designsystem.components.DsStatusVariant
import com.example.ui.designsystem.components.DsSummaryStatCard
import com.example.ui.designsystem.components.DsTopAppBar
import com.example.ui.designsystem.theme.DsSpacing
import com.example.ui.designsystem.theme.DsTheme
import com.example.ui.designsystem.theme.DsType

// Dev-only @Preview gallery entries — dark theme. Not used by production code.

@Preview(name = "DsCard", widthDp = 420)
@Composable
fun PreviewDsCard() = DsTheme(darkTheme = true) {
    DsCard(modifier = Modifier.padding(16.dp)) {
        Text("VoltAtlas DsCard — surfaceContainer + tonal elevation", style = DsType.bodyMedium)
    }
}

@Preview(name = "DsStatusBadge", widthDp = 420)
@Composable
fun PreviewDsStatusBadge() = DsTheme(darkTheme = true) {
    Row(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
    ) {
        DsStatusBadge("PASS", DsStatusVariant.Pass)
        DsStatusBadge("WARNING", DsStatusVariant.Warning)
        DsStatusBadge("FAIL", DsStatusVariant.Fail)
        DsStatusBadge("INFO", DsStatusVariant.Info)
        DsStatusBadge("STANDBY", DsStatusVariant.Neutral)
    }
}

@Preview(name = "DsMetricTile", widthDp = 420)
@Composable
fun PreviewDsMetricTile() = DsTheme(darkTheme = true) {
    Row(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
    ) {
        DsMetricTile("Short-circuit", "12.4", unit = "kA", status = DsStatusVariant.Pass, modifier = Modifier.weight(1f))
        DsMetricTile("Coordination", "3", unit = "of 7", status = DsStatusVariant.Warning, modifier = Modifier.weight(1f))
    }
}

@Preview(name = "DsSummaryStatCard", widthDp = 420)
@Composable
fun PreviewDsSummaryStatCard() = DsTheme(darkTheme = true) {
    Row(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
    ) {
        DsSummaryStatCard(Icons.Filled.AccountTree, "7", "Total circuits analyzed", modifier = Modifier.weight(1f))
        DsSummaryStatCard(Icons.Filled.Info, "6", "Coordinated", modifier = Modifier.weight(1f))
    }
}

@Preview(name = "DsSelectableChip", widthDp = 420)
@Composable
fun PreviewDsSelectableChip() = DsTheme(darkTheme = true) {
    Row(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
    ) {
        DsSelectableChip("All", selected = true, onClick = {})
        DsSelectableChip("Tier 1", selected = false, onClick = {})
        DsSelectableChip("Critical", selected = false, onClick = {})
    }
}

@Preview(name = "DsSectionHeader", widthDp = 420)
@Composable
fun PreviewDsSectionHeader() = DsTheme(darkTheme = true) {
    Column(modifier = Modifier.padding(16.dp)) {
        DsSectionHeader("Coordination Summary")
        Spacer(Modifier.height(DsSpacing.md))
        DsSectionHeader("With an action", trailingAction = { Text("View all", style = DsType.labelLarge) })
    }
}

@Preview(name = "DsListRowItem", widthDp = 420)
@Composable
fun PreviewDsListRowItem() = DsTheme(darkTheme = true) {
    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        DsListRowItem("MCC-01 Feeder", icon = Icons.Filled.Home, subtitle = "800 A",
            trailingValue = "0.4 kA", status = DsStatusVariant.Pass, onClick = {})
        DsListRowItem("TR-02 Transformer", icon = Icons.Filled.Power, subtitle = "1500 kVA",
            trailingValue = "95 %", status = DsStatusVariant.Warning, onClick = {})
    }
}

@Preview(name = "DsFab", widthDp = 420)
@Composable
fun PreviewDsFab() = DsTheme(darkTheme = true) {
    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        DsFab(onClick = {})
        Spacer(Modifier.width(DsSpacing.md))
        DsFab(onClick = {}, text = "New Circuit")
    }
}

@Preview(name = "DsTopAppBar", widthDp = 420)
@Composable
fun PreviewDsTopAppBar() = DsTheme(darkTheme = true) {
    DsTopAppBar(
        projectName = "AL-Ain Plant",
        screenTitle = "Dashboard",
        onBackClick = {},
        onPdfClick = {},
        onAssistantClick = {},
        onMenuClick = {}
    )
}

@Preview(name = "DsInfoChip", widthDp = 420)
@Composable
fun PreviewDsInfoChip() = DsTheme(darkTheme = true) {
    Row(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
    ) {
        DsInfoChip("DP-G3", trailingValue = "87.9 kVA")
        DsInfoChip("MCC-01", trailingValue = "120.0 kVA")
    }
}

@Preview(name = "DsBottomNavBar", widthDp = 420)
@Composable
fun PreviewDsBottomNavBar() = DsTheme(darkTheme = true) {
    DsBottomNavBar(selected = DsBottomNavItem.Home, onSelect = {})
}