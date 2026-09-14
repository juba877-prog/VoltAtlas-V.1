package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.adaptive.VoltAtlasWindowWidth
import com.example.ui.adaptive.rememberVoltAtlasWindowWidth
import com.example.ui.components.VoltAtlasCard
import com.example.ui.components.VoltAtlasSectionHeader
import com.example.ui.conversions.ConversionDetailScreen
import com.example.ui.conversions.ConversionTool
import com.example.ui.conversions.ConversionsScreen
import com.example.ui.theme.*
import com.example.ui.designsystem.icons.ElectricalIcons

/**
 * Component-local content minimum for one module card:
 * 48dp icon tile + 16dp gap + ~200dp minimum readable title/description
 * column + 12dp gap + 20dp trailing affordance + card padding.
 *
 * This is a *content breakpoint*, not a device breakpoint: below this width
 * the row no longer reads comfortably, so the grid must not narrow it
 * further (Compact windows therefore keep the plain single-column flow).
 */
private val ModuleCardMinWidth: Dp = 320.dp

private data class ModuleEntry(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconTint: Color = Color.Unspecified,
    val targetTab: Int
)

@Composable
fun MoreModulesGrid(onNavigate: (Int) -> Unit) {
    val modules = listOf(
        ModuleEntry(
            title = "Panel Schedule & Load Aggregation",
            description = "Bottom-up recursive feeder load aggregation, phase balancing & feeder schedules",
            icon = Icons.Default.TableChart,
            iconTint = Color(0xFF10B981),
            targetTab = 12
        ),
        ModuleEntry(
            title = "Single-Line Diagram (SLD)",
            description = "Interactive single-line network diagram & validation view",
            icon = ElectricalIcons.Sld,
            targetTab = 5
        ),
        ModuleEntry(
            title = "Protection & Coordination",
            description = "Breaker coordination, tripping curves & fault currents",
            icon = ElectricalIcons.Protection,
            targetTab = 4
        ),
        ModuleEntry(
            title = "Conduits & Cable Fill",
            description = "Conduit sizing, NEC fill ratios & cable bundling",
            icon = ElectricalIcons.Conduit,
            targetTab = 6
        ),
        ModuleEntry(
            title = "BOQ & Procurement",
            description = "Bill of quantities, cable drum allocation & procurement schedules",
            icon = ElectricalIcons.Boq,
            targetTab = 7
        ),
        ModuleEntry(
            title = "Electrical & Cable Conversions",
            description = "AWG/SWG/kcmil to mm² (True, Nearest, Safe Up-Size), µF ↔ kVAR, HP ↔ kW, Freq ↔ RPM",
            icon = Icons.Default.SwapHoriz,
            iconTint = Color(0xFF38BDF8),
            targetTab = 10
        ),
        ModuleEntry(
            title = "Engineering Methodology & Formulas",
            description = "Complete reference guide for IEC & BS formulas, variables, and worked examples",
            icon = Icons.Default.Functions,
            iconTint = Color(0xFFA855F7),
            targetTab = 11
        ),
        ModuleEntry(
            title = "Global Settings",
            description = "Design preferences, system frequency, nominal voltage & calculation defaults",
            icon = ElectricalIcons.Settings,
            targetTab = 8
        )
    )

    val windowWidth = rememberVoltAtlasWindowWidth()

    if (windowWidth == VoltAtlasWindowWidth.Compact) {
        // Compact: keep the approved single-column scroll flow (identical
        // order and content as before).
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VoltAtlasSectionHeader(title = "Secondary Engineering Modules")

            modules.forEach { module ->
                VoltAtlasFullWidthModuleRow(
                    title = module.title,
                    description = module.description,
                    icon = module.icon,
                    iconTint = module.iconTint,
                    onClick = { onNavigate(module.targetTab) }
                )
            }
        }
    } else {
        // Medium/Expanded: use the extra width with an adaptive card grid so
        // cards never shrink below their measured readable minimum.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = ModuleCardMinWidth),
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                VoltAtlasSectionHeader(title = "Secondary Engineering Modules")
            }

            items(modules) { module ->
                VoltAtlasFullWidthModuleRow(
                    title = module.title,
                    description = module.description,
                    icon = module.icon,
                    iconTint = module.iconTint,
                    onClick = { onNavigate(module.targetTab) }
                )
            }
        }
    }
}

@Composable
fun ConversionsContainerScreen(onBack: () -> Unit = {}) {
    var selectedTool by remember { mutableStateOf<ConversionTool?>(null) }

    val activeTool = selectedTool
    if (activeTool != null) {
        ConversionDetailScreen(
            tool = activeTool,
            onBack = { selectedTool = null }
        )
    } else {
        ConversionsScreen(
            onSelectTool = { selectedTool = it },
            onBack = onBack
        )
    }
}

@Composable
fun VoltAtlasFullWidthModuleRow(
    title: String,
    description: String,
    icon: ImageVector,
    iconTint: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    VoltAtlasCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 72.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (iconTint != Color.Unspecified) iconTint.copy(alpha = 0.12f)
                        else ThemePrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Menu icon",
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = CardTitle.copy(fontSize = 15.sp),
                    color = ThemeTextPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = Caption.copy(fontSize = 12.sp),
                    color = ThemeTextSecondary,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open",
                tint = ThemeTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
