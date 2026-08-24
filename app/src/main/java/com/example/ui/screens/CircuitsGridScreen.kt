package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.ui.AssistantEntryPoint
import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.engine.CableSizingEngine
import com.example.ui.EngineeringViewModel
import com.example.ui.UiState
import com.example.ui.components.themeStatusColor
import com.example.ui.components.statusSymbol
import com.example.ui.dialogs.CircuitEditDialog
import com.example.ui.catalogCableSizeOptions
import com.example.ui.theme.*

private val PANEL_TYPES = setOf("MDB", "SMDB", "DB", "MCC", "PANEL", "OTHER")

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CircuitsGridScreen(
    state: UiState.Success,
    viewModel: EngineeringViewModel,
    onNavigateToPanelSchedule: ((Int) -> Unit)? = null,
    onOpenAssistant: ((AssistantEntryPoint, CircuitEntity) -> Unit)? = null
) {
    val query by viewModel.circuitSearchQuery.collectAsState()
    val statusFilter by viewModel.circuitStatusFilter.collectAsState()
    val parentFilter by viewModel.circuitParentFilter.collectAsState()
    val selectedId by viewModel.selectedCircuitId.collectAsState()

    var showDialog by rememberSaveable { mutableStateOf(false) }
    var editingCircuit by rememberSaveable { mutableStateOf<CircuitEntity?>(null) }
    var circuitToDelete by rememberSaveable { mutableStateOf<CircuitEntity?>(null) }

    // Perform queries and filters (memoized to prevent un-remembered re-filtering on every animation frame)
    val childrenLookup = remember(state.circuits) {
        CircuitChildLookup.build(state.circuits)
    }

    val filteredCircuits = remember(state.circuits, state.sizingResults, query, statusFilter, parentFilter) {
        state.circuits.filter { circ ->
            val matchesQuery = query.isBlank() ||
                circ.circuitName.contains(query, ignoreCase = true) ||
                circ.fedFrom.contains(query, ignoreCase = true) ||
                circ.cableType.contains(query, ignoreCase = true) ||
                circ.loadType.contains(query, ignoreCase = true) ||
                (circ.overrideCableSize ?: "").contains(query, ignoreCase = true)

            val matchesStatus = when (statusFilter) {
                "PASS" -> state.sizingResults[circ.id]?.status?.contains("PASS") == true
                "FAIL" -> state.sizingResults[circ.id]?.status?.contains("FAIL") == true
                "WARNING" -> state.sizingResults[circ.id]?.status?.contains("WARN") == true
                else -> true
            }

            val matchesParent = when (parentFilter) {
                "Transformer" -> circ.fedFrom.equals("Transformer", ignoreCase = true)
                "Sub-feeders" -> !circ.fedFrom.equals("Transformer", ignoreCase = true)
                else -> true
            }

            matchesQuery && matchesStatus && matchesParent
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Bar
            TextField(
                value = query,
                onValueChange = { viewModel.circuitSearchQuery.value = it },
                placeholder = { Text("Search circuits by name, parent, cable, or load...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_circuits_input"),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedTextColor = ThemeTextPrimary,
                    unfocusedTextColor = ThemeTextPrimary
                )
            )

            // Dynamic Filter Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tier filter chip (All -> Transformer -> Sub-feeders)
                val isParentFiltered = parentFilter != "All"
                FilterChip(
                    selected = isParentFiltered,
                    onClick = {
                        viewModel.circuitParentFilter.value = when (parentFilter) {
                            "All" -> "Transformer"
                            "Transformer" -> "Sub-feeders"
                            else -> "All"
                        }
                    },
                    label = {
                        Text(
                            text = "Tier: $parentFilter",
                            color = ThemeTextPrimary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ThemePrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier.weight(1f)
                )

                // Status filter chip (All -> PASS -> WARNING -> FAIL)
                val isStatusFiltered = statusFilter != "All"
                val statusChipBg = when (statusFilter) {
                    "PASS" -> ThemeStatusPass
                    "FAIL" -> ThemeStatusFail
                    "WARNING" -> ThemeStatusWarning
                    else -> MaterialTheme.colorScheme.surfaceContainer
                }
                FilterChip(
                    selected = isStatusFiltered,
                    onClick = {
                        viewModel.circuitStatusFilter.value = when (statusFilter) {
                            "All" -> "PASS"
                            "PASS" -> "WARNING"
                            "WARNING" -> "FAIL"
                            else -> "All"
                        }
                    },
                    label = {
                        Text(
                            text = "Status: $statusFilter",
                            color = if (isStatusFiltered) MaterialTheme.colorScheme.onPrimary else ThemeTextPrimary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = statusChipBg,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier.weight(1f)
                )
            }


            // Horizontal Grid/List of circuits
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                if (filteredCircuits.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No circuits found. Click '+' to add your first circuit.",
                                color = ThemeTextSecondary,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                items(filteredCircuits, key = { it.id }) { circ ->
                    val result = state.sizingResults[circ.id]
                    val isSelected = selectedId == circ.id
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectedCircuitId.value = if (isSelected) null else circ.id }
                            .testTag("circuit_card_${circ.circuitName}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) if (LocalThemeIsDark.current) SelectedTabContainerDark else SelectedTabContainerLight else MaterialTheme.colorScheme.surfaceContainer
                        ),
                        border = BorderStroke(1.dp, if (isSelected) ThemePrimary else ThemeBorderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val childCircuits = childrenLookup.getChildren(circ.circuitName)
                                    val hasChildren = childrenLookup.hasChildren(circ.circuitName)
                                    val pType = circ.panelType?.trim()?.uppercase() ?: ""
                                    val isPanelRow = pType in PANEL_TYPES || hasChildren

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            circ.circuitName,
                                            color = ThemeTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )

                                        if (isPanelRow) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                onClick = {
                                                    viewModel.selectedCircuitId.value = circ.id
                                                    onNavigateToPanelSchedule?.invoke(circ.id)
                                                },
                                                color = ThemePrimary.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, ThemePrimary.copy(alpha = 0.4f)),
                                                modifier = Modifier.testTag("open_panel_schedule_chip_${circ.circuitName}")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Open Panel Schedule", tint = ThemePrimary, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("Schedule →", color = ThemePrimary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Fed from: ${circ.fedFrom}  •  ${circ.loadKVA} kVA | ${circ.phases}", color = ThemeTextSecondary, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    
                                    val childrenTotalLoad = childrenLookup.getChildrenTotalLoad(circ.circuitName)
                                    val childrenDiffers = kotlin.math.abs(circ.loadKVA - childrenTotalLoad) > 0.001
                                    val isUnconfirmedConflict = hasChildren && circ.directLoadConfirmed == null && childrenDiffers
                                
                                if (isUnconfirmedConflict) {
                                    Surface(
                                        onClick = {
                                            viewModel.selectedCircuitId.value = circ.id
                                            onNavigateToPanelSchedule?.invoke(circ.id)
                                        },
                                        color = ThemeStatusWarning.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, ThemeStatusWarning.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .testTag("unconfirmed_conflict_badge_${circ.circuitName}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Load conflict warning",
                                                tint = ThemeStatusWarning,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Superseded by ${Math.round(childrenTotalLoad * 10.0) / 10.0} kVA from children — tap to review in Panel Schedule",
                                                color = ThemeStatusWarning,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                
                                val isUnsupported = Catalog.isCableTypeUnsupported(circ.cableType)
                                if (isUnsupported) {
                                    Text(
                                        text = "Catalog data is not available for this cable family.",
                                        color = ThemeStatusWarning,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp).testTag("unsupported_warning_${circ.circuitName}")
                                    )
                                }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                if (result != null) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = result.cableSize_mm2,
                                            color = ThemePrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = statusSymbol(result.status),
                                            color = themeStatusColor(result.status),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                            // Expanded detailing with interactive overrides and CRUD edit/delete buttons
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = fadeIn(tween(200)) + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                                exit = fadeOut(tween(150)) + shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessLow))
                            ) {
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    HorizontalDivider(color = ThemeBorderSubtle)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // CRUD Quick Control Panel
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val pTypeExp = circ.panelType?.trim()?.uppercase() ?: ""
                                        val isPanelRowExp = pTypeExp in setOf("MDB", "SMDB", "DB", "MCC", "PANEL", "OTHER") || childrenLookup.hasChildren(circ.circuitName)

                                        Text("Circuit Operations:", color = ThemeTextSecondary, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        if (isPanelRowExp) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.selectedCircuitId.value = circ.id
                                                    onNavigateToPanelSchedule?.invoke(circ.id)
                                                },
                                                modifier = Modifier.testTag("open_panel_schedule_btn_${circ.circuitName}")
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Open Panel Schedule", tint = ThemePrimary)
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                editingCircuit = circ
                                                showDialog = true
                                            },
                                            modifier = Modifier.testTag("edit_circuit_btn_${circ.circuitName}")
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Circuit", tint = ThemePrimary)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = {
                                                circuitToDelete = circ
                                            },
                                            modifier = Modifier.testTag("delete_circuit_btn_${circ.circuitName}")
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Circuit", tint = ThemeStatusFail)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    if (result != null) {
                                        // Sizing Details
                                        Text("Sizing Specifications", color = ThemePrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Design Current (Ib)", color = ThemeTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                            Text("${String.format("%.2f", result.Ib_A)} A", color = ThemeTextPrimary, fontSize = 11.sp, maxLines = 1)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Derating Factor", color = ThemeTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                            Text("${String.format("%.3f", result.cTotalDerating)}", color = ThemeTextPrimary, fontSize = 11.sp, maxLines = 1)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Device & Type", color = ThemeTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                            Text("${result.deviceSize_A.toInt()} A (${result.deviceType})", color = ThemeTextPrimary, fontSize = 11.sp, maxLines = 1)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Voltage Drop (V / %)", color = ThemeTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                            Text("${String.format("%.2f", result.voltDrop_V)} V (${String.format("%.2f", result.vdPct)}%)", color = ThemeTextPrimary, fontSize = 11.sp, maxLines = 1)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Cumulative VD %", color = ThemeTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                            Text(
                                                text = "${String.format("%.2f", result.cumulativeVDpct)}% / ${circ.maxVDpct * 100}%",
                                                color = themeStatusColor(result.status),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                maxLines = 1
                                            )
                                        }

                                        if (result.failReason.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(8.dp))

                                             Text(
                                                text = "Failure reason:",
                                                color = ThemeStatusFail,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Text(
                                                text = result.failReason,
                                                color = ThemeTextPrimary,
                                                fontSize = 11.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        HorizontalDivider(color = ThemeBorderSubtle)
                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Interactive Manual Overrides Panel
                                        Text("MANUAL OVERRIDES PANEL", color = ThemeStatusWarning, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Mode Indicator & Reset to Auto Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val isOverrideActive = circ.overrideDeviceSize != null || circ.overrideCableSize != null || circ.overrideDeviceType != null
                                            Text(
                                                text = if (isOverrideActive) "MODE: OVERRIDE ACTIVE" else "MODE: AUTOMATIC SIZING",
                                                color = if (isOverrideActive) ThemeStatusWarning else ThemeStatusPass,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.updateCircuit(
                                                        circ.copy(
                                                            overrideDeviceSize = null,
                                                            overrideCableSize = null,
                                                            overrideDeviceType = null
                                                        )
                                                    )
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                border = BorderStroke(1.dp, ThemeBorderSubtle),
                                                modifier = Modifier.testTag("reset_to_auto_btn_${circ.circuitName}")
                                            ) {
                                                Text("Reset to Auto", color = ThemeTextPrimary, fontSize = 11.sp)
                                            }
                                        }

                                        if (onOpenAssistant != null) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            OutlinedButton(
                                                onClick = { onOpenAssistant(AssistantEntryPoint.CONTEXTUAL_CIRCUIT, circ) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                border = BorderStroke(1.dp, ThemePrimary.copy(alpha = 0.4f)),
                                                shape = RoundedCornerShape(VoltAtlasShapes.chip),
                                                modifier = Modifier.fillMaxWidth().testTag("ask_assistant_circuit_btn_${circ.circuitName}")
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AutoAwesome,
                                                        contentDescription = "Ask Assistant",
                                                        tint = ThemePrimary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Ask Assistant about this circuit", color = ThemePrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // 1. Device Type Dropdown via ExposedDropdownMenuBox
                                        var deviceTypeDropdownExpanded by remember { mutableStateOf(false) }
                                        val selectedDeviceTypeStr = circ.overrideDeviceType ?: "Auto (${result.deviceType})"

                                        ExposedDropdownMenuBox(
                                            expanded = deviceTypeDropdownExpanded,
                                            onExpandedChange = { deviceTypeDropdownExpanded = !deviceTypeDropdownExpanded },
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = selectedDeviceTypeStr,
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("Protection Device Type Override", color = ThemeTextSecondary, fontSize = 11.sp) },
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceTypeDropdownExpanded) },
                                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                                    focusedContainerColor = DarkBackground,
                                                    unfocusedContainerColor = DarkBackground,
                                                    focusedBorderColor = ThemePrimary,
                                                    unfocusedBorderColor = ThemeBorderSubtle,
                                                    focusedLabelColor = ThemePrimary,
                                                    unfocusedLabelColor = ThemeTextSecondary,
                                                    focusedTextColor = ThemeTextPrimary,
                                                    unfocusedTextColor = ThemeTextPrimary
                                                ),
                                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).testTag("override_device_type_btn")
                                            )
                                            ExposedDropdownMenu(
                                                expanded = deviceTypeDropdownExpanded,
                                                onDismissRequest = { deviceTypeDropdownExpanded = false },
                                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer).heightIn(max = 280.dp)
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Auto (Recommended ${result.deviceType})", color = ThemePrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                                    onClick = {
                                                        viewModel.updateCircuit(circ.copy(overrideDeviceType = null))
                                                        deviceTypeDropdownExpanded = false
                                                    }
                                                )
                                                listOf("MCB", "MCCB", "ACB", "Switch-Disconnector").forEach { typeOption ->
                                                    DropdownMenuItem(
                                                        text = { Text(typeOption, color = ThemeTextPrimary, fontSize = 12.sp) },
                                                        onClick = {
                                                            viewModel.updateCircuit(circ.copy(overrideDeviceType = typeOption))
                                                            deviceTypeDropdownExpanded = false
                                                        },
                                                        modifier = Modifier.testTag("device_type_option_$typeOption")
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // 2. Device Rating Dropdown via ExposedDropdownMenuBox
                                        var deviceDropdownExpanded by remember { mutableStateOf(false) }
                                        val selectedDeviceRatingStr = if (circ.overrideDeviceSize != null) "${circ.overrideDeviceSize.toInt()} A" else "Auto (${result.deviceSize_A.toInt()} A)"

                                        ExposedDropdownMenuBox(
                                            expanded = deviceDropdownExpanded,
                                            onExpandedChange = { deviceDropdownExpanded = !deviceDropdownExpanded },
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = selectedDeviceRatingStr,
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("Protection Device Rating Override", color = ThemeTextSecondary, fontSize = 11.sp) },
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceDropdownExpanded) },
                                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                                    focusedContainerColor = DarkBackground,
                                                    unfocusedContainerColor = DarkBackground,
                                                    focusedBorderColor = ThemePrimary,
                                                    unfocusedBorderColor = ThemeBorderSubtle,
                                                    focusedLabelColor = ThemePrimary,
                                                    unfocusedLabelColor = ThemeTextSecondary,
                                                    focusedTextColor = ThemeTextPrimary,
                                                    unfocusedTextColor = ThemeTextPrimary
                                                ),
                                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).testTag("override_breaker_btn")
                                            )
                                            ExposedDropdownMenu(
                                                expanded = deviceDropdownExpanded,
                                                onDismissRequest = { deviceDropdownExpanded = false },
                                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer).heightIn(max = 280.dp)
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Auto (Recommended ${result.deviceSize_A.toInt()} A)", color = ThemePrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                                    onClick = {
                                                        viewModel.updateCircuit(circ.copy(overrideDeviceSize = null))
                                                        deviceDropdownExpanded = false
                                                    }
                                                )
                                                CableSizingEngine.STANDARD_RATINGS.forEach { rating ->
                                                    DropdownMenuItem(
                                                        text = { Text("${rating.toInt()} A", color = ThemeTextPrimary, fontSize = 12.sp) },
                                                        onClick = {
                                                            viewModel.updateCircuit(circ.copy(overrideDeviceSize = rating))
                                                            deviceDropdownExpanded = false
                                                        },
                                                        modifier = Modifier.testTag("breaker_option_${rating.toInt()}A")
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // 2. Cable Size Dropdown via ExposedDropdownMenuBox
                                        var cableDropdownExpanded by remember { mutableStateOf(false) }
                                        val catalogSizes = catalogCableSizeOptions(circ.manufacturer, circ.cableType)
                                        val selectedCableSizeStr = circ.overrideCableSize ?: "Auto (${result.cableSize_mm2})"

                                        ExposedDropdownMenuBox(
                                            expanded = cableDropdownExpanded,
                                            onExpandedChange = { if (catalogSizes.isNotEmpty()) cableDropdownExpanded = !cableDropdownExpanded },
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = selectedCableSizeStr,
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("Cable Cross-Section Override", color = ThemeTextSecondary, fontSize = 11.sp) },
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cableDropdownExpanded) },
                                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                                    focusedContainerColor = DarkBackground,
                                                    unfocusedContainerColor = DarkBackground,
                                                    focusedBorderColor = ThemePrimary,
                                                    unfocusedBorderColor = ThemeBorderSubtle,
                                                    focusedLabelColor = ThemePrimary,
                                                    unfocusedLabelColor = ThemeTextSecondary,
                                                    focusedTextColor = ThemeTextPrimary,
                                                    unfocusedTextColor = ThemeTextPrimary
                                                ),
                                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).testTag("override_cable_btn")
                                            )
                                            ExposedDropdownMenu(
                                                expanded = cableDropdownExpanded,
                                                onDismissRequest = { cableDropdownExpanded = false },
                                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer).heightIn(max = 280.dp)
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Auto (Recommended ${result.cableSize_mm2})", color = ThemePrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                                    onClick = {
                                                        viewModel.updateCircuit(circ.copy(overrideCableSize = null))
                                                        cableDropdownExpanded = false
                                                    }
                                                )
                                                catalogSizes.forEach { sizeStr ->
                                                    DropdownMenuItem(
                                                        text = { Text(sizeStr, color = ThemeTextPrimary, fontSize = 12.sp) },
                                                        onClick = {
                                                            viewModel.updateCircuit(circ.copy(overrideCableSize = sizeStr))
                                                            cableDropdownExpanded = false
                                                        },
                                                        modifier = Modifier.testTag("cable_option_$sizeStr")
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Apply Override Button Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            FilledTonalButton(
                                                onClick = { viewModel.recalculate() },
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                modifier = Modifier.testTag("apply_override_btn_${circ.circuitName}")
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = "Apply Override", modifier = Modifier.size(14.dp), tint = ThemeTextPrimary)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Apply Override", color = ThemeTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                if (filteredCircuits.size > 50) {
                    item {
                        Text(
                            "Showing all ${filteredCircuits.size} matching circuits.",
                            color = ThemeTextSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Add Floating Action Button for adding new circuits
        FloatingActionButton(
            onClick = {
                editingCircuit = null
                showDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_circuit_fab"),
            containerColor = ThemePrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Circuit", tint = MaterialTheme.colorScheme.onPrimary)
        }
    }

    if (showDialog) {
        CircuitEditDialog(
            circuit = editingCircuit,
            existingCircuits = state.circuits,
            onDismiss = { showDialog = false },
            onSave = { saved ->
                val original = editingCircuit
                val normalized = if (
                    original != null &&
                    (!original.manufacturer.equals(saved.manufacturer, ignoreCase = true) ||
                        !original.cableType.equals(saved.cableType, ignoreCase = true))
                ) {
                    saved.copy(overrideCableSize = null)
                } else {
                    saved
                }
                if (normalized.id == 0) {
                    viewModel.addCircuit(normalized)
                } else {
                    viewModel.updateCircuit(normalized)
                }
                showDialog = false
            },
            viewModel = viewModel
        )
    }

    val deletingCircuit = circuitToDelete
    if (deletingCircuit != null) {
        val dependents = remember(deletingCircuit, state.circuits) {
            state.circuits.filter {
                (it.parentId != null && it.parentId == deletingCircuit.id) ||
                (it.id != deletingCircuit.id && it.fedFrom.trim().equals(deletingCircuit.circuitName.trim(), ignoreCase = true))
            }
        }
        AlertDialog(
            onDismissRequest = { circuitToDelete = null },
            title = {
                Text(
                    text = if (dependents.isNotEmpty()) "Delete Parent Board '${deletingCircuit.circuitName}'?" else "Delete Circuit '${deletingCircuit.circuitName}'?",
                    color = ThemeTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                if (dependents.isNotEmpty()) {
                    Text(
                        text = "Warning: '${deletingCircuit.circuitName}' feeds ${dependents.size} downstream circuit(s) (${dependents.joinToString { it.circuitName }}). Deleting it will disconnect these circuits and reset their source to Transformer. Do you want to proceed?",
                        color = ThemeTextPrimary,
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        text = "Are you sure you want to delete circuit '${deletingCircuit.circuitName}'?",
                        color = ThemeTextPrimary,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCircuit(deletingCircuit)
                        circuitToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeStatusFail),
                    modifier = Modifier.testTag("confirm_delete_circuit_btn_${deletingCircuit.circuitName}")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { circuitToDelete = null }
                ) {
                    Text("Cancel", color = ThemeTextPrimary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    }
}
