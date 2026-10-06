package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.example.ui.theme.VoltAtlasMotion
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.ui.designsystem.theme.DsShape
import com.example.ui.designsystem.theme.DsSpacing
import com.example.ui.designsystem.theme.DsType
import com.example.ui.designsystem.theme.dsExtendedColors
import com.example.ui.screens.circuits.CircuitCardModelCache
import com.example.ui.screens.circuits.CircuitCardUiModel
import com.example.ui.screens.circuits.CircuitLevelFilter
import com.example.ui.screens.circuits.CircuitSortOrder
import com.example.ui.screens.circuits.CircuitStatusFilter
import com.example.ui.screens.circuits.CircuitUiModelMapper
import com.example.ui.screens.circuits.CircuitUiStatus
import com.example.ui.screens.circuits.CircuitsSummaryUiModel
import com.example.ui.screens.circuits.CircuitsGridSaveCoordinator
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
    val selectedId by viewModel.selectedCircuitId.collectAsState()

    // Typed presentation filter & sort states (screen-local authority)
    var selectedLevel by rememberSaveable { mutableStateOf(CircuitLevelFilter.ALL) }
    var selectedStatus by rememberSaveable { mutableStateOf(CircuitStatusFilter.ALL) }
    var selectedSort by rememberSaveable { mutableStateOf(CircuitSortOrder.SOURCE_ORDER) }

    var showDialog by rememberSaveable { mutableStateOf(false) }
    var editingCircuit by rememberSaveable { mutableStateOf<CircuitEntity?>(null) }
    var circuitToDelete by rememberSaveable { mutableStateOf<CircuitEntity?>(null) }

    val modelCache = remember { CircuitCardModelCache() }

    // Hierarchy lookup for schedule eligibility & conflict detection
    val childrenLookup = remember(state.circuits) {
        CircuitChildLookup.build(state.circuits)
    }

    // O(N) parent name lookup by ID for breadcrumbs without full-list scans
    val parentNamesById = remember(state.circuits) {
        state.circuits.associate { it.id to it.circuitName }
    }

    // 1. Whole-project health summary (strictly computed from whole state.circuits, invariant to filters)
    // ECT-EE-06 (D5): both the summary and the cards consume the authoritative
    // ProtectionResult, so they must be memoized on it as well — otherwise a
    // protection re-run would leave PASS/FAIL counts and cards stale.
    val summary = remember(state.circuits, state.sizingResults, state.protectionResults) {
        CircuitUiModelMapper.mapToSummaryUiModel(state.circuits, state.sizingResults, state.protectionResults)
    }

    // 2. Authoritative card models map (memoized per-circuit via modelCache)
    val cardModelsById = remember(state.circuits, state.sizingResults, state.shortCircuitResults, state.protectionResults, childrenLookup, parentNamesById) {
        modelCache.sync(state.circuits)
        state.circuits.associate { circ ->
            val parentName = circ.parentId?.let { pId -> if (pId != circ.id) parentNamesById[pId] else null }
            val model = modelCache.getOrCompute(
                circuit = circ,
                sizingResult = state.sizingResults[circ.id],
                shortCircuitResult = state.shortCircuitResults[circ.id],
                parentName = parentName,
                hasChildren = childrenLookup.hasChildren(circ.circuitName),
                protectionResult = state.protectionResults[circ.id]
            )
            circ.id to model
        }
    }

    val cardModels = remember(state.circuits, cardModelsById) {
        state.circuits.mapNotNull { cardModelsById[it.id] }
    }

    val filteredCards = remember(cardModels, query, selectedLevel, selectedStatus) {
        CircuitUiModelMapper.filterCircuits(
            cards = cardModels,
            query = query,
            levelFilter = selectedLevel,
            statusFilter = selectedStatus,
            childrenLookup = { childrenLookup.hasChildren(it) }
        )
    }

    val sortedCards = remember(filteredCards, selectedSort) {
        CircuitUiModelMapper.sortCircuits(filteredCards, selectedSort)
    }

    val entityById = remember(state.circuits) { state.circuits.associateBy { it.id } }
    val visibleCircuits = remember(sortedCards, entityById) {
        sortedCards.mapNotNull { entityById[it.id] }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = DsSpacing.md)
                    .testTag("circuits_lazy_grid"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = DsSpacing.xs, bottom = 88.dp)
            ) {
                // 1. Search Bar
                item(key = "circuit_search_bar", span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { viewModel.circuitSearchQuery.value = it },
                        placeholder = {
                            Text(
                                "Search circuits by name, parent, cable, load...",
                                style = DsType.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search icon",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.circuitSearchQuery.value = "" },
                                    modifier = Modifier.testTag("clear_circuit_search_btn")
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_circuits_input"),
                        shape = DsShape.card,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                // 2. Compact Selectors (Level & Status Dropdown Selectors)
                item(key = "circuit_filter_selectors", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Compact Level Selector
                        CompactSelector(
                            labelPrefix = "Level",
                            currentLabel = selectedLevel.displayLabel,
                            options = CircuitLevelFilter.values().map { level ->
                                level.displayLabel to {
                                    selectedLevel = level
                                    viewModel.circuitParentFilter.value = when (level) {
                                        CircuitLevelFilter.ALL -> "All"
                                        CircuitLevelFilter.MAIN_FEEDERS -> "Transformer"
                                        CircuitLevelFilter.DOWNSTREAM -> "Downstream"
                                        CircuitLevelFilter.PANELS -> "Panels"
                                    }
                                }
                            },
                            testTag = "filter_level_selector",
                            modifier = Modifier.weight(1f)
                        )

                        // Compact Status Selector (Strictly All, Pass, Fail, Standby)
                        CompactSelector(
                            labelPrefix = "Status",
                            currentLabel = selectedStatus.displayLabel,
                            options = CircuitStatusFilter.values().map { status ->
                                status.displayLabel to {
                                    selectedStatus = status
                                    viewModel.circuitStatusFilter.value = when (status) {
                                        CircuitStatusFilter.ALL -> "All"
                                        CircuitStatusFilter.PASS -> "PASS"
                                        CircuitStatusFilter.FAIL -> "FAIL"
                                        CircuitStatusFilter.STANDBY -> "Standby"
                                    }
                                }
                            },
                            testTag = "filter_status_selector",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 3. Whole-Project Health Summary Strip
                item(key = "circuit_summary_strip", span = { GridItemSpan(maxLineSpan) }) {
                    CircuitsSummaryStrip(
                        summary = summary,
                        modifier = Modifier.testTag("circuits_summary_strip")
                    )
                }

                // 4. Visible Count Header and Compact Sort Selector Row
                item(key = "circuit_count_sort_header", span = { GridItemSpan(maxLineSpan) }) {
                    val totalCount = state.circuits.size
                    val visibleCount = visibleCircuits.size
                    val countHeaderText = if (visibleCount == totalCount) {
                        "CIRCUITS ($totalCount)"
                    } else {
                        "CIRCUITS ($visibleCount of $totalCount)"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DsSpacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = countHeaderText,
                            style = DsType.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .testTag("circuits_visible_count_header")
                        )

                        Spacer(modifier = Modifier.width(DsSpacing.xs))

                        // Compact Sort Selector
                        CompactSortSelector(
                            currentSort = selectedSort,
                            onSortSelected = { selectedSort = it },
                            testTag = "circuits_sort_selector"
                        )
                    }
                }

                // 5. Grid/List of circuits
                if (state.circuits.isEmpty()) {
                    item(key = "empty_circuits_state", span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No circuits found. Click '+' to add your first circuit.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = DsType.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (visibleCircuits.isEmpty()) {
                    item(key = "no_matching_circuits_state", span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "No circuits match the current search or filters.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = DsType.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(DsSpacing.md))
                            OutlinedButton(
                                onClick = {
                                    viewModel.circuitSearchQuery.value = ""
                                    viewModel.circuitParentFilter.value = "All"
                                    viewModel.circuitStatusFilter.value = "All"
                                    selectedLevel = CircuitLevelFilter.ALL
                                    selectedStatus = CircuitStatusFilter.ALL
                                },
                                modifier = Modifier.testTag("reset_filters_btn")
                            ) {
                                Text("Reset Filters", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                items(visibleCircuits, key = { it.id }) { circ ->
                    val result = state.sizingResults[circ.id]
                    val isSelected = selectedId == circ.id
                    val cardModel = cardModelsById[circ.id] ?: return@items

                    CircuitCardV3(
                        circ = circ,
                        result = result,
                        model = cardModel,
                        isSelected = isSelected,
                        childrenLookup = childrenLookup,
                        viewModel = viewModel,
                        onNavigateToPanelSchedule = onNavigateToPanelSchedule,
                        onOpenAssistant = onOpenAssistant,
                        onEdit = { editingCircuit = it; showDialog = true },
                        onDelete = { circuitToDelete = it }
                    )
                }
                if (visibleCircuits.size > 50) {
                    item(key = "circuits_footer_count", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Showing all ${visibleCircuits.size} matching circuits.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = DsType.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
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
    }

    if (showDialog) {
        CircuitEditDialog(
            circuit = editingCircuit,
            existingCircuits = state.circuits,
            onDismiss = { showDialog = false },
            onSave = { saved ->
                CircuitsGridSaveCoordinator.handleSave(
                    original = editingCircuit,
                    saved = saved,
                    onAdd = { viewModel.addCircuit(it) },
                    onUpdate = { viewModel.updateCircuit(it) },
                    onDismiss = { showDialog = false }
                )
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

/**
 * Compact, whole-project health summary strip.
 * Exactly 5 project health metrics: Total, Pass, Fail, Standby, Max Vd.
 * Stateless, theme-aware, with adaptive wrapping for narrow/dense form factors and merged TalkBack semantics.
 */
@Composable
private fun CircuitsSummaryStrip(
    summary: CircuitsSummaryUiModel,
    modifier: Modifier = Modifier
) {
    val extColors = MaterialTheme.dsExtendedColors
    val isMaxVdExceeded = (summary.rawMaxVdPct ?: 0.0) > 4.0

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = DsShape.card,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "PROJECT HEALTH SUMMARY",
                style = DsType.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isNarrow = maxWidth < 350.dp || LocalDensity.current.fontScale >= 1.25f
                if (isNarrow) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SummaryMetricCell(
                                label = "Total",
                                value = summary.totalCount.toString(),
                                valueColor = MaterialTheme.colorScheme.onSurface,
                                contentDescription = "Total circuits: ${summary.totalCount}",
                                modifier = Modifier.weight(1f)
                            )
                            SummaryMetricCell(
                                label = "Pass",
                                value = summary.passCount.toString(),
                                valueColor = extColors.success,
                                contentDescription = "Passing circuits: ${summary.passCount}",
                                modifier = Modifier.weight(1f)
                            )
                            SummaryMetricCell(
                                label = "Fail",
                                value = summary.failCount.toString(),
                                valueColor = extColors.danger,
                                contentDescription = "Failing circuits: ${summary.failCount}",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SummaryMetricCell(
                                label = "Standby",
                                value = summary.standbyCount.toString(),
                                valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                contentDescription = "Standby circuits: ${summary.standbyCount}",
                                modifier = Modifier.weight(1f)
                            )
                            SummaryMetricCell(
                                label = "Max Vd",
                                value = summary.maxVdDisplay,
                                valueColor = if (isMaxVdExceeded) extColors.danger else MaterialTheme.colorScheme.onSurface,
                                contentDescription = "Maximum voltage drop: ${summary.maxVdDisplay}",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SummaryMetricCell(
                            label = "Total",
                            value = summary.totalCount.toString(),
                            valueColor = MaterialTheme.colorScheme.onSurface,
                            contentDescription = "Total circuits: ${summary.totalCount}",
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricCell(
                            label = "Pass",
                            value = summary.passCount.toString(),
                            valueColor = extColors.success,
                            contentDescription = "Passing circuits: ${summary.passCount}",
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricCell(
                            label = "Fail",
                            value = summary.failCount.toString(),
                            valueColor = extColors.danger,
                            contentDescription = "Failing circuits: ${summary.failCount}",
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricCell(
                            label = "Standby",
                            value = summary.standbyCount.toString(),
                            valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            contentDescription = "Standby circuits: ${summary.standbyCount}",
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricCell(
                            label = "Max Vd",
                            value = summary.maxVdDisplay,
                            valueColor = if (isMaxVdExceeded) extColors.danger else MaterialTheme.colorScheme.onSurface,
                            contentDescription = "Maximum voltage drop: ${summary.maxVdDisplay}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricCell(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            this.contentDescription = contentDescription
        }
    ) {
        Text(
            text = label,
            style = DsType.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = value,
            style = DsType.titleMedium.copy(fontSize = 15.sp),
            color = valueColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CompactSelector(
    labelPrefix: String,
    currentLabel: String,
    options: List<Pair<String, () -> Unit>>,
    testTag: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$labelPrefix: $currentLabel",
                    style = DsType.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(DsSpacing.xs))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Expand $labelPrefix options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            options.forEach { (optionLabel, onSelect) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = optionLabel,
                            style = DsType.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onSelect()
                        expanded = false
                    },
                    modifier = Modifier.testTag("${testTag}_option_${optionLabel.replace(" ", "_")}")
                )
            }
        }
    }
}

@Composable
private fun CompactSortSelector(
    currentSort: CircuitSortOrder,
    onSortSelected: (CircuitSortOrder) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.testTag(testTag)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sort: ${currentSort.displayLabel}",
                    style = DsType.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Expand sort options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            CircuitSortOrder.values().forEach { sortOption ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = sortOption.displayLabel,
                            style = DsType.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onSortSelected(sortOption)
                        expanded = false
                    },
                    modifier = Modifier.testTag("sort_option_${sortOption.name}")
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CircuitCardV3(
    circ: CircuitEntity,
    result: com.example.engine.SizingResult?,
    model: CircuitCardUiModel,
    isSelected: Boolean,
    childrenLookup: CircuitChildLookup,
    viewModel: EngineeringViewModel,
    onNavigateToPanelSchedule: ((Int) -> Unit)?,
    onOpenAssistant: ((AssistantEntryPoint, CircuitEntity) -> Unit)?,
    onEdit: (CircuitEntity) -> Unit,
    onDelete: (CircuitEntity) -> Unit
) {
    val childCircuits = childrenLookup.getChildren(circ.circuitName)
    val hasChildren = childrenLookup.hasChildren(circ.circuitName)
    val pType = circ.panelType?.trim()?.uppercase() ?: ""
    val isPanelRow = pType in PANEL_TYPES || hasChildren

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.selectedCircuitId.value = if (isSelected) null else circ.id }
            .testTag("circuit_card_${circ.circuitName}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                if (LocalThemeIsDark.current) SelectedTabContainerDark else SelectedTabContainerLight
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: Circuit Name + Tier Badge + Expand Affordance + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = model.circuitName,
                        style = DsType.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val tierLabel = if (circ.parentId == null) "Tier 1 • Main" else if (hasChildren) "Tier 2 • Sub-Main" else "Tier 3 • Branch"
                    MiniTierChip(tier = tierLabel)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.selectedCircuitId.value = if (isSelected) null else circ.id },
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("circuit_expand_btn_${circ.circuitName}")
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isSelected) "Collapse circuit" else "Expand circuit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    MiniStatusBadge(
                        text = model.status.displayLabel,
                        isPass = model.status == CircuitUiStatus.PASS
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Row 2: Electrical specs summary (Load, Phase, Ib, In) + Cable summary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = "⚡ ${model.loadDisplay}",
                        style = DsType.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text("•", style = DsType.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = model.phasesDisplay,
                        style = DsType.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text("•", style = DsType.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "Ib: ${model.ibDisplay}",
                        style = DsType.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text("•", style = DsType.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "In: ${model.inDisplay}",
                        style = DsType.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = model.cableSizeDisplay,
                    style = DsType.labelSmall.copy(fontSize = 10.5.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Row 2.5: Breadcrumb & Length footer line
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fed from: ${model.breadcrumbDisplay}",
                    style = DsType.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Length: ${circ.length_m.toInt()}m",
                    style = DsType.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // Warnings / Conflict alerts
            val childrenTotalLoad = childrenLookup.getChildrenTotalLoad(circ.circuitName)
            val childrenDiffers = kotlin.math.abs(circ.loadKVA - childrenTotalLoad) > 0.001
            val isUnconfirmedConflict = hasChildren && circ.directLoadConfirmed == null && childrenDiffers

            if (isUnconfirmedConflict) {
                Surface(
                    onClick = {
                        viewModel.selectedCircuitId.value = circ.id
                        onNavigateToPanelSchedule?.invoke(circ.id)
                    },
                    color = MaterialTheme.dsExtendedColors.warning.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.dsExtendedColors.warning.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .testTag("unconfirmed_conflict_badge_${circ.circuitName}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Load conflict warning",
                            tint = MaterialTheme.dsExtendedColors.warning,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Superseded by ${Math.round(childrenTotalLoad * 10.0) / 10.0} kVA from children — tap to review in Panel Schedule",
                            color = MaterialTheme.dsExtendedColors.warning,
                            style = DsType.bodySmall.copy(fontSize = 10.5.sp),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val isUnsupported = Catalog.isCableTypeUnsupported(circ.cableType)
            if (isUnsupported) {
                Text(
                    text = "Catalog data is not available for this cable family.",
                    color = MaterialTheme.dsExtendedColors.warning,
                    style = DsType.bodySmall.copy(fontSize = 10.5.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp).testTag("unsupported_warning_${circ.circuitName}")
                )
            }

            // Row 3: 3 Horizontal Engineering Metric Tiles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val vdVoltSubtitle = if (result != null) "${String.format("%.2f", result.voltDrop_V)} V" else model.vdLimitDisplay
                EngineeringMetricTile(
                    title = "Voltage Drop",
                    icon = Icons.AutoMirrored.Filled.TrendingDown,
                    value = model.vdDisplay,
                    subtitle = vdVoltSubtitle,
                    badgeText = if (model.isVdOk) "PASS" else "FAIL",
                    isPass = model.isVdOk,
                    modifier = Modifier.weight(1f)
                )
                EngineeringMetricTile(
                    title = "Loading (Iz)",
                    icon = Icons.Default.ElectricBolt,
                    value = model.loadingDisplay,
                    subtitle = model.izDisplay,
                    badgeText = if (model.isLoadingOk) "PASS" else "FAIL",
                    isPass = model.isLoadingOk,
                    modifier = Modifier.weight(1f)
                )
                EngineeringMetricTile(
                    title = "Isc (3Φ)",
                    icon = Icons.Default.Speed,
                    value = model.isc3phDisplay,
                    subtitle = model.icuDisplay,
                    badgeText = if (model.isIscOk) "PASS" else "FAIL",
                    isPass = model.isIscOk,
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 4: Expanded Drawer with full sizing specs, CRUD and overrides
            AnimatedVisibility(
                visible = isSelected,
                enter = VoltAtlasMotion.expandableEnter(),
                exit = VoltAtlasMotion.expandableExit()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Circuit Operations
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Circuit Operations:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = DsType.labelSmall)
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isPanelRow) {
                            FilledTonalButton(
                                onClick = {
                                    viewModel.selectedCircuitId.value = circ.id
                                    onNavigateToPanelSchedule?.invoke(circ.id)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp).testTag("open_panel_schedule_btn_${circ.circuitName}")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                    contentDescription = "Open Panel Schedule",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Schedule", style = DsType.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        IconButton(
                            onClick = { onEdit(circ) },
                            modifier = Modifier.size(28.dp).testTag("edit_circuit_btn_${circ.circuitName}")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Circuit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onDelete(circ) },
                            modifier = Modifier.size(28.dp).testTag("delete_circuit_btn_${circ.circuitName}")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Circuit", tint = MaterialTheme.dsExtendedColors.danger, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (result != null) {
                        Text("Sizing Specifications", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = DsType.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Design Current (Ib)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = DsType.bodySmall, modifier = Modifier.weight(1f))
                            Text("${String.format("%.2f", result.Ib_A)} A", color = MaterialTheme.colorScheme.onSurface, style = DsType.bodySmall, maxLines = 1)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Derating Factor", color = MaterialTheme.colorScheme.onSurfaceVariant, style = DsType.bodySmall, modifier = Modifier.weight(1f))
                            Text("${String.format("%.3f", result.cTotalDerating)}", color = MaterialTheme.colorScheme.onSurface, style = DsType.bodySmall, maxLines = 1)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Device & Type", color = MaterialTheme.colorScheme.onSurfaceVariant, style = DsType.bodySmall, modifier = Modifier.weight(1f))
                            Text("${result.deviceSize_A.toInt()} A (${result.deviceType})", color = MaterialTheme.colorScheme.onSurface, style = DsType.bodySmall, maxLines = 1)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Voltage Drop (V / %)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = DsType.bodySmall, modifier = Modifier.weight(1f))
                            Text("${String.format("%.2f", result.voltDrop_V)} V (${String.format("%.2f", result.vdPct)}%)", color = MaterialTheme.colorScheme.onSurface, style = DsType.bodySmall, maxLines = 1)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cumulative VD %", color = MaterialTheme.colorScheme.onSurfaceVariant, style = DsType.bodySmall, modifier = Modifier.weight(1f))
                            Text(
                                text = "${String.format("%.2f", result.cumulativeVDpct)}% / ${circ.maxVDpct * 100}%",
                                color = themeStatusColor(result.status),
                                fontWeight = FontWeight.Bold,
                                style = DsType.bodySmall,
                                maxLines = 1
                            )
                        }

                        if (result.failReason.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Failure reason:", color = MaterialTheme.dsExtendedColors.danger, style = DsType.bodySmall, fontWeight = FontWeight.Bold)
                            Text(text = result.failReason, color = MaterialTheme.colorScheme.onSurface, style = DsType.bodySmall)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Manual Overrides Panel
                    Text("MANUAL OVERRIDES PANEL", color = MaterialTheme.dsExtendedColors.warning, fontWeight = FontWeight.Bold, style = DsType.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isOverrideActive = circ.overrideDeviceSize != null || circ.overrideCableSize != null || circ.overrideDeviceType != null || circ.overrideClearingTime_s != null
                        Text(
                            text = if (isOverrideActive) "MODE: OVERRIDE ACTIVE" else "MODE: AUTOMATIC SIZING",
                            color = if (isOverrideActive) MaterialTheme.dsExtendedColors.warning else MaterialTheme.dsExtendedColors.success,
                            style = DsType.labelSmall,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedButton(
                            onClick = {
                                viewModel.updateCircuit(
                                    circ.copy(
                                        overrideDeviceSize = null,
                                        overrideCableSize = null,
                                        overrideDeviceType = null,
                                        overrideClearingTime_s = null
                                    )
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.testTag("reset_to_auto_btn_${circ.circuitName}")
                        ) {
                            Text("Reset to Auto", color = MaterialTheme.colorScheme.onSurface, style = DsType.bodySmall)
                        }
                    }

                    if (onOpenAssistant != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { onOpenAssistant(AssistantEntryPoint.CONTEXTUAL_CIRCUIT, circ) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
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
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ask Assistant about this circuit", color = MaterialTheme.colorScheme.primary, style = DsType.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 1. Device Type Override
                    var deviceTypeDropdownExpanded by remember { mutableStateOf(false) }
                    val selectedDeviceTypeStr = circ.overrideDeviceType ?: if (result != null) "Auto (${result.deviceType})" else "Auto (no sizing run yet)"

                    ExposedDropdownMenuBox(
                        expanded = deviceTypeDropdownExpanded,
                        onExpandedChange = { deviceTypeDropdownExpanded = !deviceTypeDropdownExpanded },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedDeviceTypeStr,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Protection Device Type Override", color = MaterialTheme.colorScheme.onSurfaceVariant, style = DsType.bodySmall) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceTypeDropdownExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).testTag("override_device_type_btn")
                        )
                        ExposedDropdownMenu(
                            expanded = deviceTypeDropdownExpanded,
                            onDismissRequest = { deviceTypeDropdownExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer).heightIn(max = 280.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (result != null) "Auto (Recommended ${result.deviceType})" else "Auto (no sizing run yet)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = DsType.bodySmall) },
                                onClick = {
                                    viewModel.updateCircuit(circ.copy(overrideDeviceType = null))
                                    deviceTypeDropdownExpanded = false
                                }
                            )
                            listOf("MCB", "MCCB", "ACB", "Switch-Disconnector").forEach { typeOption ->
                                DropdownMenuItem(
                                    text = { Text(typeOption, color = MaterialTheme.colorScheme.onSurface, style = DsType.bodySmall) },
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

                    // 2. Device Rating Override
                    var deviceDropdownExpanded by remember { mutableStateOf(false) }
                    val selectedDeviceRatingStr = if (circ.overrideDeviceSize != null) {
                        "${circ.overrideDeviceSize.toInt()} A"
                    } else if (result != null) {
                        "Auto (${result.deviceSize_A.toInt()} A)"
                    } else {
                        "Auto (no sizing run yet)"
                    }

                    ExposedDropdownMenuBox(
                        expanded = deviceDropdownExpanded,
                        onExpandedChange = { deviceDropdownExpanded = !deviceDropdownExpanded },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedDeviceRatingStr,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Protection Device Rating Override", color = MaterialTheme.colorScheme.onSurfaceVariant, style = DsType.bodySmall) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceDropdownExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).testTag("override_breaker_btn")
                        )
                        ExposedDropdownMenu(
                            expanded = deviceDropdownExpanded,
                            onDismissRequest = { deviceDropdownExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer).heightIn(max = 280.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (result != null) "Auto (Recommended ${result.deviceSize_A.toInt()} A)" else "Auto (no sizing run yet)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = DsType.bodySmall) },
                                onClick = {
                                    viewModel.updateCircuit(circ.copy(overrideDeviceSize = null))
                                    deviceDropdownExpanded = false
                                }
                            )
                            CableSizingEngine.STANDARD_RATINGS.forEach { rating ->
                                DropdownMenuItem(
                                    text = { Text("${rating.toInt()} A", color = MaterialTheme.colorScheme.onSurface, style = DsType.bodySmall) },
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

                    // 3. Cable Size Override
                    var cableDropdownExpanded by remember { mutableStateOf(false) }
                    val catalogSizes = catalogCableSizeOptions(circ.manufacturer, circ.cableType)
                    val selectedCableSizeStr = circ.overrideCableSize ?: if (result != null) "Auto (${result.cableSize_mm2})" else "Auto (no sizing run yet)"

                    ExposedDropdownMenuBox(
                        expanded = cableDropdownExpanded,
                        onExpandedChange = { if (catalogSizes.isNotEmpty()) cableDropdownExpanded = !cableDropdownExpanded },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedCableSizeStr,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cable Cross-Section Override", color = MaterialTheme.colorScheme.onSurfaceVariant, style = DsType.bodySmall) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cableDropdownExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).testTag("override_cable_btn")
                        )
                        ExposedDropdownMenu(
                            expanded = cableDropdownExpanded,
                            onDismissRequest = { cableDropdownExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer).heightIn(max = 280.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (result != null) "Auto (Recommended ${result.cableSize_mm2})" else "Auto (no sizing run yet)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = DsType.bodySmall) },
                                onClick = {
                                    viewModel.updateCircuit(circ.copy(overrideCableSize = null))
                                    cableDropdownExpanded = false
                                }
                            )
                            catalogSizes.forEach { sizeStr ->
                                DropdownMenuItem(
                                    text = { Text(sizeStr, color = MaterialTheme.colorScheme.onSurface, style = DsType.bodySmall) },
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

                    // Thermal Clearing Time Override (s) (EE-03)
                    OutlinedTextField(
                        value = circ.overrideClearingTime_s?.toString() ?: "",
                        onValueChange = { newVal ->
                            val parsed = newVal.trim().toDoubleOrNull()
                            viewModel.updateCircuit(circ.copy(overrideClearingTime_s = parsed))
                        },
                        label = { Text(if (result != null) "Clearing Time (s) [Auto: %.2fs]".format(result.clearingTime_s) else "Clearing Time (s) [Auto: 0.10s]", style = DsType.labelSmall) },
                        placeholder = { Text("Auto", style = DsType.bodySmall) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clearing_time_input_${circ.circuitName}"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Apply Override
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.recalculate() },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("apply_override_btn_${circ.circuitName}")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Apply Override", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Apply Override", color = MaterialTheme.colorScheme.onSurface, style = DsType.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun MiniTierChip(
    tier: String
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = tier,
            style = DsType.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun MiniStatusBadge(
    text: String,
    isPass: Boolean
) {
    val color = if (isPass) MaterialTheme.dsExtendedColors.success else MaterialTheme.dsExtendedColors.danger
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            lineHeight = 9.sp
        )
    }
}

@Composable
private fun EngineeringMetricTile(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    subtitle: String,
    badgeText: String,
    isPass: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = title,
                    style = DsType.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = DsType.titleSmall.copy(fontSize = 12.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subtitle,
                    style = DsType.bodySmall.copy(fontSize = 8.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(2.dp))
                MiniStatusBadge(
                    text = badgeText,
                    isPass = isPass
                )
            }
        }
    }
}


