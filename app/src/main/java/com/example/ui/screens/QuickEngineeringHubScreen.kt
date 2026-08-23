package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.VoltAtlasCard
import com.example.ui.theme.*

data class QuickToolItem(
    val id: String,
    val category: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconTint: Color = SleekPrimary
)

/**
 * Quick Engineering Hub Screen (Standalone Toolbox).
 *
 * Provides standalone fast access to exactly 8 Batch 1 calculation tools
 * grouped under CABLE, POWER, TRANSFORMER, and LIGHTING categories.
 *
 * Completely isolated from Project state, Project revision, Room persistence, and AI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEngineeringHubScreen(
    onSelectTool: (String) -> Unit,
    onBack: () -> Unit
) {
    // Intercept system/hardware/gesture back to return cleanly to Home (ProjectPickerScreen)
    BackHandler(onBack = onBack)

    var searchQuery by rememberSaveable { mutableStateOf("") }

    val allTools = remember {
        listOf(
            // CABLE
            QuickToolItem(
                id = "cable_sizing",
                category = "CABLE",
                title = "Cable Sizing",
                description = "Select cable size & verify compliance (IEC 60364 / BS 7671)",
                icon = ElectricalIcons.Circuits,
                iconTint = SleekPrimary
            ),
            QuickToolItem(
                id = "voltage_drop",
                category = "CABLE",
                title = "Voltage Drop",
                description = "Calculate mV/A/m, total ΔV and percentage drop (1-Phase / 3-Phase)",
                icon = Icons.Default.ElectricBolt,
                iconTint = SleekPrimary
            ),

            // POWER
            QuickToolItem(
                id = "load_current",
                category = "POWER",
                title = "Load Current",
                description = "Convert between Amps, kW, and kVA for 1-Phase & 3-Phase systems",
                icon = ElectricalIcons.Power,
                iconTint = Color(0xFF10B981)
            ),
            QuickToolItem(
                id = "power_triangle",
                category = "POWER",
                title = "Power Triangle",
                description = "Calculate active P (kW), reactive Q (kVAR) & apparent S (kVA)",
                icon = Icons.Default.ChangeHistory,
                iconTint = Color(0xFF10B981)
            ),
            QuickToolItem(
                id = "pfc",
                category = "POWER",
                title = "Power Factor Correction",
                description = "Calculate capacitor rating (kVAR), current reduction ΔI & capacitance",
                icon = Icons.Default.FlashOn,
                iconTint = Color(0xFF10B981)
            ),

            // TRANSFORMER
            QuickToolItem(
                id = "transformer_quick",
                category = "TRANSFORMER",
                title = "Transformer Quick Sizing",
                description = "Full load current (FLC), loading %, and standard size selection",
                icon = ElectricalIcons.Transformer,
                iconTint = Color(0xFFF59E0B)
            ),
            QuickToolItem(
                id = "transformer_sc",
                category = "TRANSFORMER",
                title = "Transformer Short-Circuit",
                description = "Symmetrical fault current & impedance (Nominal & IEC 60909)",
                icon = Icons.Default.Bolt,
                iconTint = Color(0xFFF59E0B)
            ),

            // LIGHTING
            QuickToolItem(
                id = "lighting",
                category = "LIGHTING",
                title = "Lighting / Lux",
                description = "Lumen method room lux, fixture count & power density (W/m²)",
                icon = Icons.Default.Lightbulb,
                iconTint = Color(0xFFA855F7)
            )
        )
    }

    val filteredTools = remember(searchQuery, allTools) {
        if (searchQuery.isBlank()) {
            allTools
        } else {
            val q = searchQuery.trim().lowercase()
            allTools.filter {
                it.title.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
            }
        }
    }

    val categories = remember(filteredTools) {
        filteredTools.map { it.category }.distinct()
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        // Faint decorative electrical schematic grid lines
        Canvas(modifier = Modifier.fillMaxSize().alpha(0.04f)) {
            val width = size.width
            val height = size.height
            val stepX = width / 12f
            val stepY = height / 20f

            for (i in 1..11) {
                drawLine(
                    color = SleekPrimary,
                    start = Offset(i * stepX, 0f),
                    end = Offset(i * stepX, height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            for (i in 1..19) {
                drawLine(
                    color = SleekPrimary,
                    start = Offset(0f, i * stepY),
                    end = Offset(width, i * stepY),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Quick Engineering",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = ThemeTextColor
                            )
                            Text(
                                text = "Fast standalone calculations (In-Memory Scratchpad)",
                                style = MaterialTheme.typography.labelSmall,
                                color = SleekPrimary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("quick_hub_back_btn")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Home",
                                tint = ThemeTextColor
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkCardBg)
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Search Bar item (Full width span)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_hub_search_field"),
                        placeholder = { Text("Search calculations...", color = SlateTextSecondary) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = SleekPrimary)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SlateTextSecondary)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkCardBg,
                            unfocusedContainerColor = DarkCardBg,
                            focusedTextColor = ThemeTextColor,
                            unfocusedTextColor = ThemeTextColor,
                            cursorColor = SleekPrimary,
                            focusedBorderColor = SleekPrimary,
                            unfocusedBorderColor = BorderColor
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                if (filteredTools.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No calculations match your search.",
                                color = SlateTextSecondary,
                                fontSize = 15.sp
                            )
                        }
                    }
                } else {
                    categories.forEach { categoryName ->
                        val toolsInCategory = filteredTools.filter { it.category == categoryName }

                        // Category Section Header (Full width span)
                        item(key = "header_$categoryName", span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(18.dp)
                                        .background(
                                            when (categoryName) {
                                                "CABLE" -> SleekPrimary
                                                "POWER" -> Color(0xFF10B981)
                                                "TRANSFORMER" -> Color(0xFFF59E0B)
                                                "LIGHTING" -> Color(0xFFA855F7)
                                                else -> SleekPrimary
                                            },
                                            RoundedCornerShape(2.dp)
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = categoryName,
                                    style = SectionTitle.copy(fontSize = 14.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = ThemeTextColor
                                )
                            }
                        }

                        // Tool Cards in Category
                        items(toolsInCategory, key = { it.id }) { tool ->
                            QuickToolCard(
                                tool = tool,
                                onClick = { onSelectTool(tool.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickToolCard(
    tool: QuickToolItem,
    onClick: () -> Unit
) {
    VoltAtlasCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 76.dp)
            .testTag("quick_tool_card_${tool.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        tool.iconTint.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = tool.title,
                    tint = tool.iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tool.title,
                    style = CardTitle.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.Bold,
                    color = ThemeTextColor,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tool.description,
                    style = Caption.copy(fontSize = 12.sp),
                    color = SlateTextSecondary,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open ${tool.title}",
                tint = SlateTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
