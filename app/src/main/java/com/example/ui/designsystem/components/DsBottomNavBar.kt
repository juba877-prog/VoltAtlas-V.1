package com.example.ui.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Power
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
import com.example.ui.designsystem.theme.DsColor
import com.example.ui.designsystem.theme.DsType

enum class DsBottomNavItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector? = null
) {
    Home("Home", Icons.Filled.Home),
    Circuits("Circuits", Icons.Filled.AccountTree),
    Tx("Tx", Icons.Filled.Bolt),
    Power("Power", Icons.Filled.Power),
    More("More", Icons.Filled.MoreHoriz);
}

/**
 * VoltAtlas bottom navigation (Home / Circuits / Tx / Power / More) with a
 * pill active indicator per MD3. Fully stateless.
 */
@Composable
fun DsBottomNavBar(
    selected: DsBottomNavItem,
    onSelect: (DsBottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = DsColor.surfaceContainer
) {
    Surface(modifier = modifier.fillMaxWidth(), color = containerColor) {
        NavigationBar(
            modifier = Modifier.navigationBarsPadding(),
            containerColor = Color.Transparent
        ) {
            DsBottomNavItem.entries.forEach { item ->
                NavigationBarItem(
                    selected = selected == item,
                    onClick = { onSelect(item) },
                    icon = {
                        Icon(
                            imageVector = if (selected == item) item.selectedIcon ?: item.icon else item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text(text = item.label, style = DsType.labelMedium) },
                    alwaysShowLabel = false,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}