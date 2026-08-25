package com.example.ui.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.icons.ElectricalIcons
import com.example.ui.designsystem.theme.DsColor
import com.example.ui.designsystem.theme.DsType

enum class DsBottomNavItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector? = null
) {
    Home("Home", ElectricalIcons.Home),
    Circuits("Circuits", ElectricalIcons.Circuits),
    Tx("Tx", ElectricalIcons.Transformer),
    Power("Power", ElectricalIcons.Power),
    More("More", Icons.Filled.MoreHoriz);
}

/**
 * VoltAtlas bottom navigation (Home / Circuits / Tx / Power / More) with a
 * pill active indicator per MD3. Fully stateless.
 *
 * Mirrors the production phone bottom bar exactly: Home / Circuits / Tx /
 * Power use the intrinsic duotone ElectricalIcons rendered with
 * [Color.Unspecified] (their Cyan #08B7E8 + Green #19C979 paths stay
 * untouched), while More remains a monochrome glyph.
 *
 * KNOWN LIMITATION (future shell-migration concern): tints are fixed
 * dark-theme tokens mirroring production one-to-one — unselected
 * [DsColor.textSecondary] #AEB6C2, selected [DsColor.primary] #3B9EFF.
 * They intentionally do NOT adapt to a light color scheme. Accepted while
 * this component remains design-system-only (gallery + previews), unwired
 * to production screens.
 */
@Composable
fun DsBottomNavBar(
    selected: DsBottomNavItem,
    onSelect: (DsBottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified
) {
    val resolvedContainerColor = if (containerColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        containerColor
    }

    Surface(modifier = modifier.fillMaxWidth(), color = resolvedContainerColor) {
        NavigationBar(
            modifier = Modifier.navigationBarsPadding(),
            containerColor = Color.Transparent
        ) {
            DsBottomNavItem.entries.forEach { item ->
                // Production parity: duotone vectors keep their intrinsic
                // two-tone colors; only monochrome glyphs receive the nav tint.
                val isDuotone = item.icon == ElectricalIcons.Home ||
                        item.icon == ElectricalIcons.Circuits ||
                        item.icon == ElectricalIcons.Transformer ||
                        item.icon == ElectricalIcons.Power
                NavigationBarItem(
                    selected = selected == item,
                    onClick = { onSelect(item) },
                    icon = {
                        Icon(
                            imageVector = if (selected == item) item.selectedIcon ?: item.icon else item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(24.dp),
                            tint = if (isDuotone) {
                                Color.Unspecified
                            } else {
                                if (selected == item) DsColor.primary else DsColor.textSecondary
                            }
                        )
                    },
                    label = { Text(text = item.label, style = DsType.labelMedium) },
                    alwaysShowLabel = false,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DsColor.primary,
                        selectedTextColor = DsColor.primary,
                        indicatorColor = DsColor.primary.copy(alpha = 0.12f),
                        unselectedIconColor = DsColor.textSecondary,
                        unselectedTextColor = DsColor.textSecondary
                    )
                )
            }
        }
    }
}