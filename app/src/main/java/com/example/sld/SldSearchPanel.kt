package com.example.sld

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SldSearchPanel(
    allNodes: List<PositionedSldNode>,
    onSelectNode: (PositionedSldNode) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredNodes = remember(searchQuery, allNodes) {
        if (searchQuery.isBlank()) {
            allNodes
        } else {
            val q = searchQuery.trim().lowercase()
            allNodes.filter { node ->
                node.label.lowercase().contains(q) ||
                        node.id.lowercase().contains(q) ||
                        node.panelType.lowercase().contains(q) ||
                        node.dataBlock?.cableDescription?.lowercase()?.contains(q) == true ||
                        node.dataBlock?.deviceTypeAndRating?.lowercase()?.contains(q) == true
            }
        }
    }

    val isDark = com.example.ui.theme.LocalThemeIsDark.current
    val palette = SldTheme.palette(isDark)
    val cardBg = palette.surface
    val chipBg = if (isDark) Color(0xFF0D1117) else Color(0xFFF1F5F9)
    val borderColor = if (isDark) Color(0xFF30363D) else Color(0xFFCBD5E1)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Search Network Equipment",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = palette.textPrimary
                )
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Search",
                        tint = palette.textSecondary
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Equipment name, ID, cable...", fontSize = 12.sp, color = palette.textSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SleekPrimary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SleekPrimary,
                    unfocusedBorderColor = borderColor,
                    focusedContainerColor = chipBg,
                    unfocusedContainerColor = chipBg,
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary
                )
            )

            Text(
                text = "Results (${filteredNodes.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SlateTextSecondary
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredNodes, key = { it.id }) { node ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(chipBg, RoundedCornerShape(8.dp))
                            .clickable { onSelectNode(node) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = node.label,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "${node.panelType} | ID: ${node.id}",
                                fontSize = 10.sp,
                                color = palette.textSecondary
                            )
                        }

                        val statusColor = try {
                            Color(android.graphics.Color.parseColor(node.statusColorHex))
                        } catch (e: Exception) {
                            SleekPass
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = statusColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "SELECT",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
