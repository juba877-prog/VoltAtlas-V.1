package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.VoltAtlasCard
import com.example.ui.components.VoltAtlasSectionHeader
import com.example.ui.conversions.ConversionDetailScreen
import com.example.ui.conversions.ConversionTool
import com.example.ui.conversions.ConversionsScreen
import com.example.ui.theme.*

@Composable
fun MoreModulesGrid(onNavigate: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        VoltAtlasSectionHeader(title = "Secondary Engineering Modules")

        VoltAtlasFullWidthModuleRow(
            title = "Panel Schedule & Load Aggregation",
            description = "Bottom-up recursive feeder load aggregation, phase balancing & feeder schedules",
            icon = Icons.Default.TableChart,
            iconTint = Color(0xFF10B981),
            onClick = { onNavigate(12) }
        )

        VoltAtlasFullWidthModuleRow(
            title = "Single-Line Diagram (SLD)",
            description = "Interactive single-line network diagram & validation view",
            icon = ElectricalIcons.Sld,
            onClick = { onNavigate(5) }
        )

        VoltAtlasFullWidthModuleRow(
            title = "Protection & Coordination",
            description = "Breaker coordination, tripping curves & fault currents",
            icon = ElectricalIcons.Protection,
            onClick = { onNavigate(4) }
        )

        VoltAtlasFullWidthModuleRow(
            title = "Conduits & Cable Fill",
            description = "Conduit sizing, NEC fill ratios & cable bundling",
            icon = ElectricalIcons.Conduit,
            onClick = { onNavigate(6) }
        )

        VoltAtlasFullWidthModuleRow(
            title = "BOQ & Procurement",
            description = "Bill of quantities, cable drum allocation & procurement schedules",
            icon = ElectricalIcons.Boq,
            onClick = { onNavigate(7) }
        )

        VoltAtlasFullWidthModuleRow(
            title = "Electrical & Cable Conversions",
            description = "AWG/SWG/kcmil to mm² (True, Nearest, Safe Up-Size), µF ↔ kVAR, HP ↔ kW, Freq ↔ RPM",
            icon = Icons.Default.SwapHoriz,
            iconTint = Color(0xFF38BDF8),
            onClick = { onNavigate(10) }
        )

        VoltAtlasFullWidthModuleRow(
            title = "Engineering Methodology & Formulas",
            description = "Complete reference guide for IEC & BS formulas, variables, and worked examples",
            icon = Icons.Default.Functions,
            iconTint = Color(0xFFA855F7),
            onClick = { onNavigate(11) }
        )

        VoltAtlasFullWidthModuleRow(
            title = "Global Settings",
            description = "Design preferences, system frequency, nominal voltage & calculation defaults",
            icon = ElectricalIcons.Settings,
            onClick = { onNavigate(8) }
        )
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
                        else SleekPrimary.copy(alpha = 0.12f),
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
                    color = ThemeTextColor,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = Caption.copy(fontSize = 12.sp),
                    color = SlateTextSecondary,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open",
                tint = SlateTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
