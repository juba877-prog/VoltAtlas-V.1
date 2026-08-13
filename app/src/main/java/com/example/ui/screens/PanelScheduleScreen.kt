package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.VoltAtlasMotion
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CircuitEntity
import com.example.engine.PanelScheduleEngine
import com.example.engine.PanelScheduleRecursiveResult
import com.example.engine.PanelScheduleRow
import com.example.ui.EngineeringViewModel
import com.example.ui.UiState
import com.example.ui.theme.*

data class PanelSelectionOption(
    val id: Int?,
    val panelName: String,
    val fedFrom: String?,
    val displayLabel: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelScheduleScreen(
    viewModel: EngineeringViewModel,
    uiState: UiState,
    onBack: () -> Unit = {}
) {
    val successState = uiState as? UiState.Success
    val circuits = successState?.circuits ?: emptyList()
    val sizingResults = successState?.sizingResults ?: emptyMap()
    val activeProject by viewModel.activeProject.collectAsState()

    // Identify all panel options in the project (circuits explicitly marked with a panelType: MDB, SMDB, DB, MCC, PANEL, OTHER)
    val panelOptions = remember(circuits) {
        val systemNodes = setOf("transformer", "ats", "generator", "grid")

        val candidateEntities = circuits.filter { c ->
            val pType = c.panelType?.trim()?.uppercase() ?: ""
            val isPanelType = pType in setOf("MDB", "SMDB", "DB", "MCC", "PANEL", "OTHER")
            val hasChildren = circuits.any { it.fedFrom.trim().equals(c.circuitName.trim(), ignoreCase = true) }
            isPanelType || hasChildren
        }

        val nameCounts = candidateEntities.groupingBy { it.circuitName.trim().lowercase() }.eachCount()

        val entityOptions = candidateEntities.map { c ->
            val nameTrim = c.circuitName.trim()
            val count = nameCounts[nameTrim.lowercase()] ?: 1
            val label = if (count > 1) {
                val fedContext = if (c.fedFrom.isNotBlank()) "fed from ${c.fedFrom}, " else ""
                "$nameTrim (${fedContext}#${c.id})"
            } else {
                nameTrim
            }
            PanelSelectionOption(
                id = c.id,
                panelName = nameTrim,
                fedFrom = c.fedFrom,
                displayLabel = label
            )
        }

        val knownNamesLower = circuits.map { it.circuitName.trim().lowercase() }.toSet()
        val orphanFedFromNames = circuits.map { it.fedFrom.trim() }
            .filter { it.isNotEmpty() && !systemNodes.contains(it.lowercase()) && !knownNamesLower.contains(it.lowercase()) }
            .distinct()

        val orphanOptions = orphanFedFromNames.map { name ->
            PanelSelectionOption(
                id = null,
                panelName = name,
                fedFrom = null,
                displayLabel = name
            )
        }

        (entityOptions + orphanOptions).sortedBy { it.displayLabel }
    }

    val panelNames = remember(panelOptions) {
        panelOptions.map { it.panelName }.distinct()
    }

    val selectedCircuitId by viewModel.selectedCircuitId.collectAsState()
    var selectedOptionKey by remember { mutableStateOf<String?>(null) }

    val selectedOption = remember(panelOptions, selectedOptionKey) {
        panelOptions.firstOrNull { opt ->
            val key = if (opt.id != null) "id_${opt.id}" else "name_${opt.panelName}"
            key == selectedOptionKey
        } ?: panelOptions.firstOrNull()
    }

    val selectedPanelName = selectedOption?.panelName ?: ""

    // Keep selected option synced when selectedCircuitId or panelOptions update
    LaunchedEffect(selectedCircuitId, panelOptions) {
        if (selectedCircuitId != null) {
            val targetKey = "id_$selectedCircuitId"
            if (panelOptions.any { (if (it.id != null) "id_${it.id}" else "name_${it.panelName}") == targetKey }) {
                selectedOptionKey = targetKey
            } else if (panelOptions.isNotEmpty() && selectedOption == null) {
                val first = panelOptions.first()
                selectedOptionKey = if (first.id != null) "id_${first.id}" else "name_${first.panelName}"
            }
        } else if (panelOptions.isNotEmpty()) {
            if (selectedOption == null) {
                val first = panelOptions.first()
                selectedOptionKey = if (first.id != null) "id_${first.id}" else "name_${first.panelName}"
            }
        } else {
            selectedOptionKey = null
        }
    }

    // Full CircuitEditDialog states
    var circuitToEditInDialog by remember { mutableStateOf<CircuitEntity?>(null) }
    var showCircuitEditDialog by remember { mutableStateOf(false) }
    var isNewCircuitInDialog by remember { mutableStateOf(false) }
    var showDeletePanelConfirmDialog by remember { mutableStateOf(false) }

    // Recursive calculation for the selected panel
    val scheduleResult = remember(selectedOption, circuits, sizingResults) {
        if (selectedOption == null) {
            PanelScheduleRecursiveResult(
                panelName = "",
                panelCircuit = null,
                childrenCircuits = emptyList(),
                rows = emptyList(),
                aggregatedChildrenLoadKVA = 0.0,
                panelOwnLoadKVA = 0.0,
                directLoadConfirmed = null
            )
        } else {
            PanelScheduleEngine.generateScheduleRecursive(
                panelName = selectedOption.panelName,
                circuits = circuits,
                sizingResults = sizingResults,
                targetCircuitId = selectedOption.id
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeBackground)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("panel_schedule_back_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = ThemeTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Panel Schedule & Load Aggregation",
                    style = SectionTitle,
                    color = ThemeTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Bottom-up recursive feeder aggregation & phase balancing",
                    style = Caption,
                    color = ThemeTextSecondary
                )
            }
            Button(
                onClick = {
                    val defaultParent = if (selectedPanelName.isNotEmpty() && panelNames.contains(selectedPanelName)) selectedPanelName else "Transformer"
                    circuitToEditInDialog = CircuitEntity(
                        circuitName = "",
                        fedFrom = defaultParent,
                        panelType = "DB",
                        loadKVA = 10.0,
                        voltageV = 400,
                        phases = "3P+N",
                        length_m = 30.0,
                        maxVDpct = 0.05
                    )
                    isNewCircuitInDialog = true
                    showCircuitEditDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("create_new_panel_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Panel", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Panel Selector Dropdown Row
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ThemeSurface),
            border = BorderStroke(1.dp, ThemeBorderSubtle),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Panel:",
                    fontWeight = FontWeight.SemiBold,
                    color = ThemeTextPrimary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(12.dp))

                var expandedDropdown by remember { mutableStateOf(false) }

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { expandedDropdown = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("panel_selector_dropdown"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ThemeBorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedOption?.displayLabel ?: "No panels created",
                                color = ThemeTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = SleekPrimary)
                        }
                    }

                    DropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false }
                    ) {
                        if (panelOptions.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No panels created yet") },
                                onClick = { expandedDropdown = false }
                            )
                        } else {
                            panelOptions.forEach { opt ->
                                val isSelected = opt == selectedOption
                                DropdownMenuItem(
                                    text = { Text(opt.displayLabel, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        selectedOptionKey = if (opt.id != null) "id_${opt.id}" else "name_${opt.panelName}"
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main Content Area
        if (panelNames.isEmpty() || selectedPanelName.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(containerColor = ThemeSurface),
                border = BorderStroke(1.dp, ThemeBorderSubtle),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = SleekPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Panels in Project",
                        style = SectionTitle,
                        color = ThemeTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "There are no panelboards or distribution boards created yet in this project. Tap '+ New Panel' above to create your first panel schedule.",
                        style = Caption,
                        color = ThemeTextSecondary,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            val defaultParent = if (selectedPanelName.isNotEmpty() && panelNames.contains(selectedPanelName)) selectedPanelName else "Transformer"
                            circuitToEditInDialog = CircuitEntity(
                                circuitName = "",
                                fedFrom = defaultParent,
                                panelType = "DB",
                                loadKVA = 10.0,
                                voltageV = 400,
                                phases = "3P+N",
                                length_m = 30.0,
                                maxVDpct = 0.05
                            )
                            isNewCircuitInDialog = true
                            showCircuitEditDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("empty_state_create_panel_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create First Panel", fontSize = 14.sp)
                    }
                }
            }
        } else if (scheduleResult.hasCircularReference) {
            // Circular reference error banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StatusFail.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, StatusFail),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = StatusFail, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = scheduleResult.errorMessage ?: "Circular reference detected in hierarchy.",
                        color = StatusFail,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            val openAddChildDialog = {
                circuitToEditInDialog = CircuitEntity(
                    circuitName = "",
                    fedFrom = selectedPanelName,
                    panelType = "LOAD",
                    loadKVA = 5.0,
                    voltageV = 400,
                    phases = "3P+N",
                    length_m = 30.0,
                    maxVDpct = 0.05
                )
                isNewCircuitInDialog = true
                showCircuitEditDialog = true
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Header overview card
                item {
                    PanelOverviewCard(
                        scheduleResult = scheduleResult,
                        onDeletePanel = { showDeletePanelConfirmDialog = true },
                        onAddChildCircuit = { openAddChildDialog() }
                    )
                }

                // Panel Header Equipment Specification Card
                if (scheduleResult.panelCircuit != null) {
                    val scResult = (uiState as? UiState.Success)?.shortCircuitResults?.get(scheduleResult.panelCircuit.id)
                    val calculatedIscKA = scResult?.iscKA
                    item {
                        PanelHeaderEquipmentCard(
                            panelCircuit = scheduleResult.panelCircuit,
                            totalPanelLoadKVA = scheduleResult.totalPanelLoadKVA,
                            calculatedIscKA = calculatedIscKA,
                            onUpdatePanelCircuit = { updated: CircuitEntity ->
                                viewModel.updateCircuit(updated)
                            }
                        )
                    }
                }

                // Part C Modal Dialog trigger notice if unconfirmed direct load exists
                if (scheduleResult.hasUnconfirmedDirectLoadDialog) {
                    item {
                        UnconfirmedDirectLoadCard(
                            scheduleResult = scheduleResult,
                            onConfirmDirectLoad = {
                                val pCircuit = scheduleResult.panelCircuit
                                if (pCircuit != null) {
                                    viewModel.updateCircuit(pCircuit.copy(directLoadConfirmed = true))
                                }
                            },
                            onConfirmEstimate = {
                                val pCircuit = scheduleResult.panelCircuit
                                if (pCircuit != null) {
                                    viewModel.updateCircuit(pCircuit.copy(directLoadConfirmed = false, loadKVA = 0.0))
                                }
                            }
                        )
                    }
                }

                // General Load Mismatch Card (if mismatch exists and direct load is not explicitly confirmed true)
                if (scheduleResult.hasLoadMismatch && !scheduleResult.hasUnconfirmedDirectLoadDialog) {
                    item {
                        LoadMismatchConflictCard(
                            scheduleResult = scheduleResult,
                            onSyncCircuitLoad = {
                                val pCircuit = scheduleResult.panelCircuit
                                if (pCircuit != null) {
                                    viewModel.updateCircuit(pCircuit.copy(loadKVA = scheduleResult.aggregatedChildrenLoadKVA))
                                } else {
                                    val newCircuit = viewModel.createDefaultCircuit(
                                        name = selectedPanelName,
                                        loadKVA = scheduleResult.aggregatedChildrenLoadKVA,
                                        fedFrom = "Transformer",
                                        phases = "3P+N"
                                    ).copy(panelType = "MDB")
                                    viewModel.addCircuit(newCircuit)
                                }
                            }
                        )
                    }
                }

                // Schedule Rows Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Feeder & Circuit Schedule (${scheduleResult.rows.size} items)",
                            style = SectionTitle,
                            color = ThemeTextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { openAddChildDialog() },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SleekPrimary),
                            modifier = Modifier.testTag("add_child_circuit_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = SleekPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Child Load", fontSize = 12.sp, color = SleekPrimary, maxLines = 1)
                        }
                    }
                }

                if (scheduleResult.rows.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = ThemeSurface),
                            border = BorderStroke(1.dp, ThemeBorderSubtle),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Inbox, contentDescription = null, tint = ThemeTextSecondary, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No circuits fed from '$selectedPanelName'", color = ThemeTextSecondary, fontSize = 14.sp)
                                Text("0.0 kVA total load — add child circuits or sub-panels below.", color = ThemeTextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    items(scheduleResult.rows, key = { row -> row.circuitId ?: "${row.itemNo}_${row.description}" }) { row ->
                        PanelScheduleRowCard(
                            row = row,
                            onSubPanelClick = { subName ->
                                val subMatch = panelOptions.firstOrNull { opt -> opt.panelName.trim().equals(subName.trim(), ignoreCase = true) }
                                if (subMatch != null) {
                                    selectedOptionKey = if (subMatch.id != null) "id_${subMatch.id}" else "name_${subMatch.panelName}"
                                }
                            },
                            onEditClick = { targetRow ->
                                val targetCircuit = circuits.firstOrNull { it.id == targetRow.circuitId }
                                    ?: circuits.firstOrNull { it.circuitName.trim().equals(targetRow.description.trim(), ignoreCase = true) }
                                if (targetCircuit != null) {
                                    circuitToEditInDialog = targetCircuit
                                    isNewCircuitInDialog = false
                                    showCircuitEditDialog = true
                                }
                            }
                        )
                    }
                }

                // Load Type Breakdown Card
                item {
                    LoadTypeBreakdownCard(breakdown = scheduleResult.loadTypeBreakdown)
                }

                // Phase Distribution & Imbalance Card
                item {
                    PhaseBalanceCard(
                        scheduleResult = scheduleResult,
                        activeProject = activeProject,
                        onUpdateOverrideLimit = { overrideVal ->
                            scheduleResult.panelCircuit?.id?.let { circuitId ->
                                viewModel.updateCircuitImbalanceOverride(circuitId, overrideVal)
                            }
                        }
                    )
                }
            }
        }
    }

    // Render Full CircuitEditDialog
    if (showCircuitEditDialog && circuitToEditInDialog != null) {
        key(circuitToEditInDialog) {
            com.example.ui.CircuitEditDialog(
                circuit = circuitToEditInDialog,
                existingCircuits = circuits,
                viewModel = viewModel,
                onDismiss = {
                    showCircuitEditDialog = false
                    circuitToEditInDialog = null
                },
                onSave = { savedCircuit ->
                    if (isNewCircuitInDialog) {
                        viewModel.addCircuit(savedCircuit)
                        val savedPType = savedCircuit.panelType?.trim()?.uppercase() ?: ""
                        if (savedPType in setOf("MDB", "SMDB", "DB", "MCC", "PANEL", "OTHER")) {
                            selectedOptionKey = if (savedCircuit.id != 0) "id_${savedCircuit.id}" else "name_${savedCircuit.circuitName}"
                        }
                    } else {
                        viewModel.updateCircuit(savedCircuit)
                    }
                    showCircuitEditDialog = false
                    circuitToEditInDialog = null
                }
            )
        }
    }

    // Modal Dialog 3: Delete Panel Confirm with Explicit Parent Selection for Orphans
    if (showDeletePanelConfirmDialog) {
        val childCircuits = circuits.filter { child ->
            (selectedOption?.id != null && child.parentId == selectedOption.id) ||
                    child.fedFrom.trim().equals(selectedPanelName, ignoreCase = true)
        }
        val pCircuit = scheduleResult.panelCircuit
        val targetParentOptions = remember(panelNames, selectedPanelName) {
            listOf("Transformer") + panelNames.filter { !it.equals(selectedPanelName, ignoreCase = true) }
        }

        val childAssignments = remember(childCircuits) {
            mutableStateMapOf<Int, String>().apply {
                childCircuits.forEach { child ->
                    put(child.id, targetParentOptions.firstOrNull() ?: "Transformer")
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showDeletePanelConfirmDialog = false },
            title = { Text("Delete Panel '$selectedPanelName'?", color = ThemeTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (childCircuits.isNotEmpty()) {
                        Text(
                            text = "⚠ ORPHAN WARNING: Panel '$selectedPanelName' feeds ${childCircuits.size} child load(s). Select a new parent ('fedFrom') for each child circuit before deleting:",
                            color = StatusWarning,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        childCircuits.forEach { child ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = ThemeSurface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Circuit: ${child.circuitName} (${child.loadKVA} kVA)",
                                        color = ThemeTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("New Parent (fedFrom):", color = ThemeTextPrimary.copy(alpha = 0.7f), fontSize = 11.sp)

                                    var dropdownExpanded by remember { mutableStateOf(false) }
                                    val currentAssigned = childAssignments[child.id] ?: targetParentOptions.firstOrNull() ?: "Transformer"

                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedButton(
                                            onClick = { dropdownExpanded = true },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(currentAssigned, color = ThemeTextPrimary)
                                        }
                                        DropdownMenu(
                                            expanded = dropdownExpanded,
                                            onDismissRequest = { dropdownExpanded = false }
                                        ) {
                                            targetParentOptions.forEach { opt ->
                                                DropdownMenuItem(
                                                    text = { Text(opt) },
                                                    onClick = {
                                                        childAssignments[child.id] = opt
                                                        dropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Are you sure you want to delete panel '$selectedPanelName'?",
                            color = ThemeTextPrimary,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Reassign orphans to explicitly chosen parents
                        childCircuits.forEach { child ->
                            val chosenParent = childAssignments[child.id] ?: "Transformer"
                            viewModel.updateCircuit(child.copy(fedFrom = chosenParent))
                        }
                        // Delete panel circuit if it exists
                        if (pCircuit != null) {
                            viewModel.deleteCircuit(pCircuit)
                        }
                        showDeletePanelConfirmDialog = false
                        val remainingOptions = panelOptions.filter { it != selectedOption }
                        val nextOpt = remainingOptions.firstOrNull()
                        selectedOptionKey = nextOpt?.let { if (it.id != null) "id_${it.id}" else "name_${it.panelName}" }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusFail),
                    modifier = Modifier.testTag("confirm_delete_panel_btn")
                ) {
                    Text(if (childCircuits.isNotEmpty()) "Reassign & Delete Panel" else "Delete Panel", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeletePanelConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PanelOverviewCard(
    scheduleResult: PanelScheduleRecursiveResult,
    onDeletePanel: () -> Unit,
    onAddChildCircuit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ThemeSurface),
        border = BorderStroke(1.dp, ThemeBorderSubtle),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = scheduleResult.panelName,
                        style = CardTitle,
                        color = ThemeTextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Fed From: ${scheduleResult.panelCircuit?.fedFrom ?: "Transformer / Parent"}",
                        style = BodySecondary,
                        color = ThemeTextSecondary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onDeletePanel,
                        modifier = Modifier.testTag("delete_panel_btn")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Panel", tint = StatusFail)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = ThemeBorderSubtle)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("TOTAL PANEL LOAD", style = Caption, color = ThemeTextSecondary)
                    Text(
                        text = "${scheduleResult.totalPanelLoadKVA} kVA",
                        style = ScreenTitle,
                        color = SleekPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("CHILD LOADS", style = Caption, color = ThemeTextSecondary)
                    Text(
                        text = "${scheduleResult.rows.size} Items (${scheduleResult.aggregatedChildrenLoadKVA} kVA)",
                        style = SectionTitle,
                        color = ThemeTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelHeaderEquipmentCard(
    panelCircuit: CircuitEntity,
    totalPanelLoadKVA: Double,
    calculatedIscKA: Double? = null,
    onUpdatePanelCircuit: (CircuitEntity) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Check busbar current warning
    val voltageV = if (panelCircuit.voltageV > 0) panelCircuit.voltageV.toDouble() else 400.0
    val pf = if (panelCircuit.pf > 0) panelCircuit.pf else 0.8
    val is3P = panelCircuit.phases.contains("3P")
    val currentA = if (is3P) {
        (totalPanelLoadKVA * 1000.0) / (kotlin.math.sqrt(3.0) * voltageV * pf)
    } else {
        (totalPanelLoadKVA * 1000.0) / (voltageV * pf)
    }
    val busbarA = panelCircuit.busbarRatingA
    val showBusbarWarning = busbarA != null && busbarA > 0.0 && currentA > busbarA

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("panel_header_equipment_card"),
        colors = CardDefaults.cardColors(containerColor = ThemeSurface),
        border = BorderStroke(1.dp, ThemeBorderSubtle),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = SleekPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Panel Header Equipment Specification",
                        style = CardTitle,
                        color = ThemeTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Panel Header",
                        tint = SleekPrimary
                    )
                }
            }

            if (showBusbarWarning) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StatusWarning.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, StatusWarning),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = StatusWarning, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "⚠ Aggregated load current (${String.format(java.util.Locale.US, "%.1f", currentA)} A) exceeds busbar rating (${busbarA?.toInt()} A)",
                            style = Caption,
                            color = StatusWarning,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = ThemeBorderSubtle)
                Spacer(modifier = Modifier.height(12.dp))

                // Row 1: Busbar Rating & SC/AIC Rating
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        com.example.ui.components.VoltAtlasTextField(
                            value = panelCircuit.busbarRatingA?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "",
                            onValueChange = { input ->
                                val doubleVal = input.toDoubleOrNull()
                                onUpdatePanelCircuit(panelCircuit.copy(busbarRatingA = doubleVal))
                            },
                            label = "Busbar Rating (A)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.testTag("input_panel_busbar_rating")
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        val scText = calculatedIscKA?.let { String.format(java.util.Locale.US, "%.2f kA", it) } ?: "N/A"
                        com.example.ui.components.VoltAtlasTextField(
                            value = scText,
                            onValueChange = {},
                            label = "Short Circuit (kA) [Calculated]",
                            readOnly = true,
                            modifier = Modifier.testTag("display_panel_sc_rating")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Row 2: Main Incoming Device Type & Count
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        com.example.ui.components.VoltAtlasDropdown(
                            value = panelCircuit.mainIncomingType ?: "",
                            options = listOf("", "MCCB", "ACB", "LBS", "Switch-Disconnector", "Fuse-Switch"),
                            onSelect = { selected ->
                                onUpdatePanelCircuit(panelCircuit.copy(mainIncomingType = selected.ifBlank { null }))
                            },
                            label = "Main Incoming Type",
                            modifier = Modifier.testTag("input_panel_main_incoming_type")
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        com.example.ui.components.VoltAtlasTextField(
                            value = panelCircuit.mainIncomingCount?.toString() ?: "",
                            onValueChange = { input ->
                                val intVal = input.toIntOrNull()
                                onUpdatePanelCircuit(panelCircuit.copy(mainIncomingCount = intVal))
                            },
                            label = "Incoming Count",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.testTag("input_panel_main_incoming_count")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Row 3: IP Rating & Cable Entry
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        com.example.ui.components.VoltAtlasDropdown(
                            value = panelCircuit.ipRating ?: "",
                            options = listOf("", "IP20", "IP41", "IP54", "IP55", "IP65"),
                            onSelect = { selected ->
                                onUpdatePanelCircuit(panelCircuit.copy(ipRating = selected.ifBlank { null }))
                            },
                            label = "IP Rating",
                            modifier = Modifier.testTag("input_panel_ip_rating")
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        com.example.ui.components.VoltAtlasDropdown(
                            value = panelCircuit.cableEntry ?: "",
                            options = listOf("", "Top", "Bottom", "Top/Bottom"),
                            onSelect = { selected ->
                                onUpdatePanelCircuit(panelCircuit.copy(cableEntry = selected.ifBlank { null }))
                            },
                            label = "Cable Entry",
                            modifier = Modifier.testTag("input_panel_cable_entry")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Row 4: Panel Location & Number of Ways
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        com.example.ui.components.VoltAtlasTextField(
                            value = panelCircuit.panelLocation ?: "",
                            onValueChange = { input ->
                                onUpdatePanelCircuit(panelCircuit.copy(panelLocation = input.ifBlank { null }))
                            },
                            label = "Location",
                            modifier = Modifier.testTag("input_panel_location")
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        com.example.ui.components.VoltAtlasTextField(
                            value = panelCircuit.numberOfWays?.toString() ?: "",
                            onValueChange = { input ->
                                val intVal = input.toIntOrNull()
                                onUpdatePanelCircuit(panelCircuit.copy(numberOfWays = intVal))
                            },
                            label = "Number of Ways",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.testTag("input_panel_number_of_ways")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Row 5: Feeder Description
                com.example.ui.components.VoltAtlasTextField(
                    value = panelCircuit.feederDescription ?: "",
                    onValueChange = { input ->
                        onUpdatePanelCircuit(panelCircuit.copy(feederDescription = input.ifBlank { null }))
                    },
                    label = "Feeder / Supply Cable Description",
                    modifier = Modifier.testTag("input_panel_feeder_description")
                )
            }
        }
    }
}

@Composable
fun UnconfirmedDirectLoadCard(
    scheduleResult: PanelScheduleRecursiveResult,
    onConfirmDirectLoad: () -> Unit,
    onConfirmEstimate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("unconfirmed_direct_load_card"),
        colors = CardDefaults.cardColors(containerColor = StatusWarning.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, StatusWarning),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Help, contentDescription = null, tint = StatusWarning, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Explicit Confirmation Required (Part C)",
                    color = StatusWarning,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Panel '${scheduleResult.panelName}' has an existing load of ${scheduleResult.panelOwnLoadKVA} kVA recorded, and also feeds ${scheduleResult.childrenCircuits.size} sub-circuit(s) totaling ${scheduleResult.aggregatedChildrenLoadKVA} kVA.",
                color = ThemeTextPrimary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Is ${scheduleResult.panelOwnLoadKVA} kVA a DIRECT LOAD (e.g. equipment wired directly to this panel), or was it a preliminary estimate before sub-circuits were added?",
                color = ThemeTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConfirmDirectLoad,
                    colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                    modifier = Modifier.weight(1f).testTag("confirm_direct_load_btn"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Direct Load — Keep Both (${scheduleResult.totalPanelLoadKVA + scheduleResult.panelOwnLoadKVA} kVA)", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onConfirmEstimate,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusWarning),
                    border = BorderStroke(1.dp, StatusWarning),
                    modifier = Modifier.weight(1f).testTag("confirm_estimate_clear_btn"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Estimate — Use Children (${scheduleResult.aggregatedChildrenLoadKVA} kVA)", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun LoadMismatchConflictCard(
    scheduleResult: PanelScheduleRecursiveResult,
    onSyncCircuitLoad: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("load_mismatch_card"),
        colors = CardDefaults.cardColors(containerColor = StatusWarning.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, StatusWarning),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = StatusWarning,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recorded Load Mismatch",
                    color = StatusWarning,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Recorded on Circuit: ${scheduleResult.panelOwnLoadKVA} kVA | Aggregated Child Total: ${scheduleResult.aggregatedChildrenLoadKVA} kVA",
                color = ThemeTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "The panel's recorded circuit load does not match the sum of its sub-circuits.",
                color = ThemeTextPrimary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onSyncCircuitLoad,
                colors = ButtonDefaults.buttonColors(containerColor = StatusWarning),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sync_circuit_load_btn")
            ) {
                Text(
                    text = "Update Load to ${scheduleResult.aggregatedChildrenLoadKVA} kVA",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun PanelScheduleRowCard(
    row: PanelScheduleRow,
    onSubPanelClick: (String) -> Unit,
    onEditClick: ((PanelScheduleRow) -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .testTag("row_card_${row.itemNo}"),
        colors = CardDefaults.cardColors(
            containerColor = if (row.isSubPanel) SleekPrimary.copy(alpha = 0.08f) else ThemeSurface
        ),
        border = BorderStroke(1.dp, if (row.isSubPanel) SleekPrimary.copy(alpha = 0.3f) else ThemeBorderSubtle),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(SleekPrimary.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "${row.itemNo}", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = row.description,
                            fontWeight = FontWeight.Bold,
                            color = ThemeTextPrimary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (row.isSubPanel) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = SleekPrimary,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "SUB-PANEL",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        } else if (row.isDirectLoadItem) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = StatusWarning,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "DIRECT LOAD",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Breaker: ${row.breakerRating} • Cable: ${row.cableSize}",
                        style = Caption,
                        color = ThemeTextSecondary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "${row.loadKVA} kVA",
                        fontWeight = FontWeight.Bold,
                        color = SleekPrimary,
                        fontSize = 13.sp
                    )

                    if (onEditClick != null && row.circuitId != null) {
                        IconButton(
                            onClick = { onEditClick(row) },
                            modifier = Modifier.size(26.dp).testTag("edit_row_${row.itemNo}")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit circuit details", tint = ThemeTextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }

                    if (row.isSubPanel && row.subPanelName != null) {
                        IconButton(
                            onClick = { onSubPanelClick(row.subPanelName) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Drill into sub-panel", tint = SleekPrimary, modifier = Modifier.size(16.dp))
                        }
                    }

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Toggle details",
                            tint = SleekPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            val reduceMotion = LocalReduceMotion.current
            AnimatedVisibility(
                visible = isExpanded,
                enter = if (reduceMotion) fadeIn(animationSpec = VoltAtlasMotion.instantFadeSpec) else fadeIn(animationSpec = VoltAtlasMotion.fadeEnterSpec) + expandVertically(animationSpec = spring(stiffness = VoltAtlasMotion.SpringStiffnessLow)),
                exit = if (reduceMotion) fadeOut(animationSpec = VoltAtlasMotion.instantFadeSpec) else fadeOut(animationSpec = VoltAtlasMotion.fadeExitSpec) + shrinkVertically(animationSpec = spring(stiffness = VoltAtlasMotion.SpringStiffnessLow))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    HorizontalDivider(color = ThemeBorderSubtle.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = row.description,
                        fontWeight = FontWeight.Bold,
                        color = ThemeTextPrimary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Phases: ${row.phases}", style = Caption, color = ThemeTextSecondary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Assigned Phase: ${row.assignedPhase}", style = Caption, color = ThemeTextSecondary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Breaker: ${row.breakerRating}", style = Caption, color = ThemeTextSecondary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Cable: ${row.cableSize}", style = Caption, color = ThemeTextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PhaseBalanceCard(
    scheduleResult: PanelScheduleRecursiveResult,
    activeProject: com.example.data.ProjectEntity? = null,
    onUpdateOverrideLimit: ((Double?) -> Unit)? = null
) {
    val effectiveLimit = scheduleResult.effectiveImbalanceLimit(activeProject)
    val isBalanced = scheduleResult.isBalanced(activeProject)
    val currentOverride = scheduleResult.panelCircuit?.phaseImbalanceLimitOverridePct

    var showCustomDialog by remember { mutableStateOf(false) }
    var customInputText by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ThemeSurface),
        border = BorderStroke(1.dp, ThemeBorderSubtle),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("PHASE BALANCE SUMMARY", style = SectionTitle, color = ThemeTextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (currentOverride != null) "Threshold: ${effectiveLimit}% (Panel Override)" else "Threshold: ${effectiveLimit}% (Project Default)",
                        color = ThemeTextSecondary,
                        fontSize = 11.sp
                    )
                }

                Surface(
                    color = if (isBalanced) StatusPass.copy(alpha = 0.2f) else StatusWarning.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isBalanced) "BALANCED (${scheduleResult.unbalancePct}%)" else "UNBALANCED (${scheduleResult.unbalancePct}%)",
                        color = if (isBalanced) StatusPass else StatusWarning,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PhaseColumn("L1 Load", "${scheduleResult.phaseL1KVA} kVA", Color(0xFFEF4444))
                PhaseColumn("L2 Load", "${scheduleResult.phaseL2KVA} kVA", Color(0xFFF59E0B))
                PhaseColumn("L3 Load", "${scheduleResult.phaseL3KVA} kVA", Color(0xFF3B82F6))
            }

            if (onUpdateOverrideLimit != null && scheduleResult.panelCircuit != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ThemeBorderSubtle)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Panel Threshold Override:", color = ThemeTextSecondary, fontSize = 12.sp)

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(10.0, 15.0, 20.0).forEach { limit ->
                            val isSelected = currentOverride == limit
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) onUpdateOverrideLimit(null)
                                    else onUpdateOverrideLimit(limit)
                                },
                                label = { Text("${limit.toInt()}%", fontSize = 11.sp) },
                                modifier = Modifier.height(28.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SleekPrimary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = ThemeBackground,
                                    labelColor = ThemeTextSecondary
                                )
                            )
                        }

                        if (currentOverride != null && currentOverride !in listOf(10.0, 15.0, 20.0)) {
                            FilterChip(
                                selected = true,
                                onClick = { onUpdateOverrideLimit(null) },
                                label = { Text("${currentOverride}%", fontSize = 11.sp) },
                                modifier = Modifier.height(28.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SleekPrimary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }

                        IconButton(
                            onClick = {
                                customInputText = currentOverride?.toString() ?: effectiveLimit.toString()
                                showCustomDialog = true
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Custom threshold", tint = SleekPrimary, modifier = Modifier.size(16.dp))
                        }

                        if (currentOverride != null) {
                            TextButton(
                                onClick = { onUpdateOverrideLimit(null) },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Reset", color = SleekWarning, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Panel Imbalance Threshold Override", color = ThemeTextPrimary, fontSize = 16.sp) },
            text = {
                Column {
                    Text("Enter custom maximum allowed unbalance percentage for panel ${scheduleResult.panelName}:", color = ThemeTextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customInputText,
                        onValueChange = { customInputText = it },
                        label = { Text("Limit (%)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SleekPrimary,
                            unfocusedBorderColor = ThemeBorderSubtle,
                            focusedLabelColor = SleekPrimary,
                            unfocusedLabelColor = ThemeTextSecondary,
                            focusedTextColor = ThemeTextPrimary,
                            unfocusedTextColor = ThemeTextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val valDouble = customInputText.toDoubleOrNull()
                        if (valDouble != null && valDouble >= 0.0) {
                            onUpdateOverrideLimit?.invoke(valDouble)
                        }
                        showCustomDialog = false
                    }
                ) {
                    Text("Apply", color = SleekPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text("Cancel", color = ThemeTextSecondary)
                }
            },
            containerColor = ThemeSurface
        )
    }
}

@Composable
fun PhaseColumn(label: String, value: String, accentColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(accentColor, shape = RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = Caption, color = ThemeTextSecondary)
        Text(value, fontWeight = FontWeight.Bold, color = ThemeTextPrimary, fontSize = 14.sp)
    }
}

@Composable
fun LoadTypeBreakdownCard(breakdown: com.example.data.LoadTypeBreakdown) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("load_type_breakdown_card"),
        colors = CardDefaults.cardColors(containerColor = ThemeSurface),
        border = BorderStroke(1.dp, ThemeBorderSubtle),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Load Type Breakdown & Demand Analysis",
                    style = SectionTitle,
                    color = ThemeTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = SleekPrimary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Overall DF: ${String.format(java.util.Locale.US, "%.1f", breakdown.overallDemandFactor * 100)}%",
                        color = SleekPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (breakdown.rows.isEmpty()) {
                Text(
                    text = "No active circuits classified for load type breakdown.",
                    color = ThemeTextSecondary,
                    fontSize = 12.sp
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ThemeBorderSubtle.copy(alpha = 0.2f), shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Category", color = ThemeTextSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(2.2f))
                    Text("Qty", color = ThemeTextSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                    Text("Conn kVA", color = ThemeTextSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.3f))
                    Text("Avg PF", color = ThemeTextSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.0f))
                    Text("DF", color = ThemeTextSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.0f))
                    Text("Demand kVA", color = ThemeTextSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.4f))
                }

                HorizontalDivider(color = ThemeBorderSubtle)

                breakdown.rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (index % 2 == 1) ThemeBorderSubtle.copy(alpha = 0.1f) else Color.Transparent)
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(row.categoryLabel, color = ThemeTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(2.2f))
                        Text("${row.circuitCount}", color = ThemeTextPrimary, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                        Text(String.format(java.util.Locale.US, "%.1f", row.connectedKva), color = ThemeTextPrimary, fontSize = 11.sp, modifier = Modifier.weight(1.3f))
                        Text(String.format(java.util.Locale.US, "%.2f", row.averagePf), color = ThemeTextPrimary, fontSize = 11.sp, modifier = Modifier.weight(1.0f))
                        Text(String.format(java.util.Locale.US, "%.2f", row.effectiveDemandFactor), color = ThemeTextPrimary, fontSize = 11.sp, modifier = Modifier.weight(1.0f))
                        Text(String.format(java.util.Locale.US, "%.1f", row.demandKva), color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.4f))
                    }
                }

                HorizontalDivider(color = ThemeBorderSubtle, thickness = 1.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SleekPrimary.copy(alpha = 0.08f), shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TOTAL / OVERALL", color = ThemeTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(2.2f))
                    Text("${breakdown.rows.sumOf { it.circuitCount }}", color = ThemeTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                    Text(String.format(java.util.Locale.US, "%.1f", breakdown.totalConnectedKva), color = ThemeTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.3f))
                    Text("—", color = ThemeTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1.0f))
                    Text(String.format(java.util.Locale.US, "%.2f", breakdown.overallDemandFactor), color = ThemeTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.0f))
                    Text(String.format(java.util.Locale.US, "%.1f", breakdown.totalDemandKva), color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.4f))
                }
            }
        }
    }
}

