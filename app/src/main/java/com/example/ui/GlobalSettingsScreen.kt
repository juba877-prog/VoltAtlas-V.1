package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.boot.VoltAtlasLogo
import com.example.data.Catalog
import com.example.data.GlobalPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsScreen(
    viewModel: EngineeringViewModel,
    preferences: GlobalPreferences,
    onBack: () -> Unit
) {
    val confirmDestructive by preferences.confirmDestructiveActions.collectAsState()
    val startupBehavior by preferences.startupBehavior.collectAsState()
    val themeMode by preferences.themeMode.collectAsState()
    val defaultFreq by preferences.defaultFrequency.collectAsState()
    val defaultVoltage by preferences.defaultVoltage3Phase.collectAsState()
    val maxVdLighting by preferences.maxVdLimitLighting.collectAsState()
    val maxVdPower by preferences.maxVdLimitPower.collectAsState()
    val defaultConductor by preferences.defaultConductorMaterial.collectAsState()
    val defaultInsulation by preferences.defaultInsulation.collectAsState()
    val defaultCoreConfig by preferences.defaultCoreConfig.collectAsState()
    val drumDefaultCapacity by preferences.drumDefaultCapacity.collectAsState()
    val cableSafetyMargin by preferences.cableSafetyMargin.collectAsState()
    val cableCutOverhead by preferences.cableCutOverhead.collectAsState()

    var drumCapacityText by remember(drumDefaultCapacity) { mutableStateOf(drumDefaultCapacity.toString()) }
    var safetyMarginText by remember(cableSafetyMargin) { mutableStateOf((cableSafetyMargin * 100.0).toString()) }
    var cutOverheadText by remember(cableCutOverhead) { mutableStateOf(cableCutOverhead.toString()) }

    val projects by viewModel.projects.collectAsState()
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var showResetDefaultsConfirm by remember { mutableStateOf(false) }
    var showImportProjectDialog by remember { mutableStateOf(false) }
    var importJsonInput by remember { mutableStateOf("") }
    var importErrorText by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val jsonStr = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: ""
                    if (jsonStr.isBlank()) {
                        importErrorText = "Selected file is empty."
                    } else {
                        viewModel.importProjectJson(jsonStr) { success, msg ->
                            if (success) {
                                showImportProjectDialog = false
                                viewModel.closeGlobalSettings()
                                Toast.makeText(context, "Project imported successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                importErrorText = msg
                            }
                        }
                    }
                } catch (e: Exception) {
                    importErrorText = "Failed to read file: ${e.message}"
                }
            }
        }
    }


    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .background(DarkBackground)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = SleekPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Global Settings",
                        color = ThemeTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                HorizontalDivider(color = BorderColor)
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
        ) {
            // A) GENERAL & APP BEHAVIOR
            item {
                Text(
                    text = "GENERAL & APP BEHAVIOR",
                    color = SleekPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Confirm Destructive Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Confirm Destructive Actions",
                                    color = ThemeTextColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Prompt confirmation before deleting projects or clearing project data.",
                                    color = SlateTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = confirmDestructive,
                                onCheckedChange = { preferences.setConfirmDestructiveActions(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = SleekPrimary,
                                    uncheckedThumbColor = SlateTextSecondary,
                                    uncheckedTrackColor = BorderColor
                                ),
                                modifier = Modifier.testTag("confirm_destructive_switch")
                            )
                        }

                        HorizontalDivider(color = BorderColor)

                        // Startup Screen Behavior
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Startup Screen Mode",
                                color = ThemeTextColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Choose initial screen when launching VoltAtlas.",
                                color = SlateTextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = startupBehavior == "ALWAYS_SHOW_PICKER",
                                    onClick = { preferences.setStartupBehavior("ALWAYS_SHOW_PICKER") },
                                    label = { Text("Always Show Picker", fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SleekPrimary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        containerColor = DarkBackground,
                                        labelColor = SlateTextSecondary
                                    )
                                )
                                FilterChip(
                                    selected = startupBehavior == "AUTO_OPEN_LAST",
                                    onClick = { preferences.setStartupBehavior("AUTO_OPEN_LAST") },
                                    label = { Text("Auto-open Last", fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SleekPrimary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        containerColor = DarkBackground,
                                        labelColor = SlateTextSecondary
                                    )
                                )
                            }
                        }

                        HorizontalDivider(color = BorderColor)

                        // Appearance Theme
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Appearance Theme",
                                color = ThemeTextColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("DARK" to "Dark", "LIGHT" to "Light", "SYSTEM" to "System").forEach { (key, label) ->
                                    FilterChip(
                                        selected = themeMode == key,
                                        onClick = { preferences.setThemeMode(key) },
                                        label = { Text(label, fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SleekPrimary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = DarkBackground,
                                            labelColor = SlateTextSecondary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // B) ENGINEERING STANDARDS & CALCULATION DEFAULTS
            item {
                Text(
                    text = "ENGINEERING STANDARDS & DEFAULTS",
                    color = SleekPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // System Frequency
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("System Frequency", color = ThemeTextColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Standard power grid frequency for AC calculations.", color = SlateTextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf(50 to "50 Hz (IEC / Gulf)", 60 to "60 Hz (ANSI / US)").forEach { (freq, label) ->
                                    FilterChip(
                                        selected = defaultFreq == freq,
                                        onClick = { preferences.setDefaultFrequency(freq) },
                                        label = { Text(label, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SleekPrimary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = DarkBackground,
                                            labelColor = SlateTextSecondary
                                        )
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = BorderColor)

                        // Default 3-Phase System Voltage
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Default Nominal 3-Phase Voltage", color = ThemeTextColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Default voltage level applied to new circuits.", color = SlateTextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(400 to "400 V", 380 to "380 V", 415 to "415 V", 480 to "480 V").forEach { (volt, label) ->
                                    FilterChip(
                                        selected = defaultVoltage == volt,
                                        onClick = { preferences.setDefaultVoltage3Phase(volt) },
                                        label = { Text(label, fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SleekPrimary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = DarkBackground,
                                            labelColor = SlateTextSecondary
                                        )
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = BorderColor)

                        // Voltage Drop Limits
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Voltage Drop Limit Thresholds", color = ThemeTextColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Maximum allowable voltage drop percentage limits per IEC 60364-5-52.", color = SlateTextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Lighting Circuits Limit", color = ThemeTextColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text("Standard limit is 3.0%", color = SlateTextSecondary, fontSize = 11.sp)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(3.0, 4.0, 5.0).forEach { limit ->
                                        FilterChip(
                                            selected = maxVdLighting == limit,
                                            onClick = { preferences.setMaxVdLimitLighting(limit) },
                                            label = { Text("$limit%", fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = SleekPrimary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                                containerColor = DarkBackground,
                                                labelColor = SlateTextSecondary
                                            )
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Power & Feeder Limit", color = ThemeTextColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text("Standard limit is 5.0%", color = SlateTextSecondary, fontSize = 11.sp)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(5.0, 6.0, 7.0).forEach { limit ->
                                        FilterChip(
                                            selected = maxVdPower == limit,
                                            onClick = { preferences.setMaxVdLimitPower(limit) },
                                            label = { Text("$limit%", fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = SleekPrimary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                                containerColor = DarkBackground,
                                                labelColor = SlateTextSecondary
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = BorderColor)

                        // Active Project Phase Imbalance Threshold Default
                        val activeProj by viewModel.activeProject.collectAsState()
                        if (activeProj != null) {
                            val currentProjLimit = activeProj!!.defaultPhaseImbalanceLimitPct
                            var showCustomProjLimitDialog by remember { mutableStateOf(false) }
                            var customProjLimitText by remember { mutableStateOf("") }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("Phase Imbalance Limit Threshold (Active Project)", color = ThemeTextColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("Project default maximum allowable phase unbalance percentage for panel schedules (Default 20.0%).", color = SlateTextSecondary, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Project Default Limit", color = ThemeTextColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        listOf(10.0, 15.0, 20.0).forEach { limit ->
                                            FilterChip(
                                                selected = currentProjLimit == limit,
                                                onClick = { viewModel.updateActiveProjectImbalanceLimit(limit) },
                                                label = { Text("${limit.toInt()}%", fontSize = 12.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = SleekPrimary,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                                    containerColor = DarkBackground,
                                                    labelColor = SlateTextSecondary
                                                )
                                            )
                                        }

                                        if (currentProjLimit !in listOf(10.0, 15.0, 20.0)) {
                                            FilterChip(
                                                selected = true,
                                                onClick = {},
                                                label = { Text("${currentProjLimit}%", fontSize = 12.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = SleekPrimary,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                                )
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                customProjLimitText = currentProjLimit.toString()
                                                showCustomProjLimitDialog = true
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Custom project threshold", tint = SleekPrimary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }

                            if (showCustomProjLimitDialog) {
                                AlertDialog(
                                    onDismissRequest = { showCustomProjLimitDialog = false },
                                    title = { Text("Project Default Imbalance Limit", color = ThemeTextColor, fontSize = 16.sp) },
                                    text = {
                                        Column {
                                            Text("Enter default maximum allowed phase unbalance percentage for project '${activeProj!!.projectName}':", color = SlateTextSecondary, fontSize = 12.sp)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            OutlinedTextField(
                                                value = customProjLimitText,
                                                onValueChange = { customProjLimitText = it },
                                                label = { Text("Limit (%)") },
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = SleekPrimary,
                                                    unfocusedBorderColor = BorderColor,
                                                    focusedLabelColor = SleekPrimary,
                                                    unfocusedLabelColor = SlateTextSecondary,
                                                    focusedTextColor = ThemeTextColor,
                                                    unfocusedTextColor = ThemeTextColor
                                                )
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                val valDouble = customProjLimitText.toDoubleOrNull()
                                                if (valDouble != null && valDouble >= 0.0) {
                                                    viewModel.updateActiveProjectImbalanceLimit(valDouble)
                                                }
                                                showCustomProjLimitDialog = false
                                            }
                                        ) {
                                            Text("Save", color = SleekPrimary)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showCustomProjLimitDialog = false }) {
                                            Text("Cancel", color = SlateTextSecondary)
                                        }
                                    },
                                    containerColor = DarkCardBg
                                )
                            }
                        }

                        HorizontalDivider(color = BorderColor)

                        // Default Conductor & Insulation
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Default Cable Specification Defaults", color = ThemeTextColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            // Conductor
                            Text("Conductor", color = SlateTextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Cu" to "Copper (Cu)", "Al" to "Aluminum (Al)").forEach { (mat, label) ->
                                    FilterChip(
                                        selected = defaultConductor == mat,
                                        onClick = { preferences.setDefaultConductorMaterial(mat) },
                                        label = { Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SleekPrimary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = DarkBackground,
                                            labelColor = SlateTextSecondary
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Insulation
                            Text("Insulation", color = SlateTextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("XLPE" to "XLPE (90°C)", "PVC" to "PVC (70°C)").forEach { (ins, label) ->
                                    FilterChip(
                                        selected = defaultInsulation == ins,
                                        onClick = { preferences.setDefaultInsulation(ins) },
                                        label = { Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SleekPrimary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = DarkBackground,
                                            labelColor = SlateTextSecondary
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Core Configuration
                            Text("Default Core Configuration", color = SlateTextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("1C", "2C", "3C", "3.5C", "4C").forEach { core ->
                                    FilterChip(
                                        selected = defaultCoreConfig == core,
                                        onClick = { preferences.setDefaultCoreConfig(core) },
                                        label = { Text(core, fontSize = 12.sp, maxLines = 1, softWrap = false) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SleekPrimary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = DarkBackground,
                                            labelColor = SlateTextSecondary
                                        )
                                    )
                                }
                            }
                        }

                    }
                }
            }

            // C) CABLE DRUM & CUTTING PARAMETERS
            item {
                Text(
                    text = "CABLE DRUM & CUTTING PARAMETERS",
                    color = SleekPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Standard Drum Supply Capacity
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Standard Supply Drum Length (m)", color = ThemeTextColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Default capacity per cable drum used for optimization packing.", color = SlateTextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = drumCapacityText,
                                onValueChange = {
                                    drumCapacityText = it
                                    it.toDoubleOrNull()?.let { d ->
                                        if (d > 0) preferences.setDrumDefaultCapacity(d)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = ThemeTextColor,
                                    unfocusedTextColor = ThemeTextColor,
                                    focusedBorderColor = SleekPrimary,
                                    unfocusedBorderColor = BorderColor
                                ),
                                singleLine = true
                            )
                        }

                        HorizontalDivider(color = BorderColor)

                        // Safety Margin
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Cable Cut Safety Margin (%)", color = ThemeTextColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Percent length added to each circuit run to cover installation curves.", color = SlateTextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = safetyMarginText,
                                onValueChange = {
                                    safetyMarginText = it
                                    it.toDoubleOrNull()?.let { d ->
                                        if (d >= 0) preferences.setCableSafetyMargin(d / 100.0)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = ThemeTextColor,
                                    unfocusedTextColor = ThemeTextColor,
                                    focusedBorderColor = SleekPrimary,
                                    unfocusedBorderColor = BorderColor
                                ),
                                singleLine = true
                            )
                        }

                        HorizontalDivider(color = BorderColor)

                        // Cut Termination Overhead
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Cut Termination Overhead (m)", color = ThemeTextColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Fixed length added to each circuit run to cover terminations at both ends.", color = SlateTextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = cutOverheadText,
                                onValueChange = {
                                    cutOverheadText = it
                                    it.toDoubleOrNull()?.let { d ->
                                        if (d >= 0) preferences.setCableCutOverhead(d)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = ThemeTextColor,
                                    unfocusedTextColor = ThemeTextColor,
                                    focusedBorderColor = SleekPrimary,
                                    unfocusedBorderColor = BorderColor
                                ),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            // D) DATA & CATALOG MANAGEMENT
            item {
                Text(
                    text = "DATA & CATALOG MANAGEMENT",
                    color = SleekPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Project data and catalogs are stored locally and securely on this device.",
                            color = ThemeTextColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // Counts rows
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Saved Projects", color = SlateTextSecondary, fontSize = 13.sp)
                            Text("${projects.size} projects", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Accessory Catalog", color = SlateTextSecondary, fontSize = 13.sp)
                            Text("${Catalog.accessories.size} accessories", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cable Catalog", color = SlateTextSecondary, fontSize = 13.sp)
                            val cableCount = Catalog.cableCatalog.size
                            Text(
                                text = if (cableCount > 0) "Loaded ($cableCount records)" else "Not Loaded",
                                color = if (cableCount > 0) SleekPass else SleekFail,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Breaker Catalog", color = SlateTextSecondary, fontSize = 13.sp)
                            val breakerCount = Catalog.breakers.size
                            Text(
                                text = if (breakerCount > 0) "Loaded ($breakerCount records)" else "Not Loaded",
                                color = if (breakerCount > 0) SleekPass else SleekFail,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        FilledTonalButton(
                            onClick = { showDiagnosticsDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("catalog_diagnostics_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Dns, contentDescription = "Catalog Diagnostics", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Catalog Diagnostics", color = ThemeTextColor, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { 
                                importJsonInput = ""
                                importErrorText = null
                                showImportProjectDialog = true 
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SleekPrimary)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = "Import Existing Project", tint = SleekPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import Existing Project (JSON)", color = SleekPrimary, fontWeight = FontWeight.Bold)
                        }


                        OutlinedButton(
                            onClick = { showResetDefaultsConfirm = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SleekWarning)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset Settings", tint = SleekWarning, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reset Settings to Default", color = SleekWarning, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // D) ABOUT VOLTATLAS
            item {
                Text(
                    text = "ABOUT VOLTATLAS",
                    color = SleekPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Centered small VoltAtlas Logo (cyan V + green bar)
                        VoltAtlasLogo(
                            modifier = Modifier.size(56.dp)
                        )

                        Text(
                            text = "VoltAtlas",
                            color = ThemeTextColor,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )

                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            color = SleekPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Professional LV Electrical Design Platform",
                            color = ThemeTextColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Cable sizing · Transformer/Generator/ATS/UPS · Short-circuit · Protection · PF correction · Earthing · Conduit · SLD · BOQ · PDF reports. Built to IEC standards.",
                            color = SlateTextSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )

                        HorizontalDivider(color = BorderColor)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Designed & Developed by Mahmoud Khalil",
                                color = ThemeTextColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "© 2026 Mahmoud Khalil. All rights reserved.",
                                color = SlateTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDiagnosticsDialog) {
        val diagnostics = Catalog.getDiagnostics()
        AlertDialog(
            onDismissRequest = { showDiagnosticsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Database Icon",
                        tint = SleekPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Catalog Diagnostics", color = ThemeTextColor)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Verifying JSON assets and specifications parsed in memory:",
                        color = SlateTextSecondary,
                        fontSize = 13.sp
                    )
                    
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                    
                    DiagnosticRow(label = "cable_types.json", loaded = Catalog.cableTypes.isNotEmpty(), count = Catalog.cableTypes.size)
                    DiagnosticRow(label = "data_sheet.json", loaded = Catalog.dataSheet.isNotEmpty(), count = Catalog.dataSheet.size)
                    DiagnosticRow(label = "cable_catalog.json", loaded = Catalog.cableCatalog.isNotEmpty(), count = Catalog.cableCatalog.size)
                    DiagnosticRow(label = "breaker_catalog.json", loaded = Catalog.breakers.isNotEmpty(), count = Catalog.breakers.size)
                    DiagnosticRow(label = "accessory_catalog.json", loaded = Catalog.accessories.isNotEmpty(), count = Catalog.accessories.size)
                    DiagnosticRow(label = "derating_data.json", loaded = Catalog.deratingData != null)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                    Text("Catalog Integrity Metrics (Status: ${diagnostics.status})", color = SleekPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Defined Families: ${diagnostics.definedFamilies}", color = ThemeTextColor, fontSize = 12.sp)
                    Text("Supported Families: ${diagnostics.supportedFamilies}", color = ThemeTextColor, fontSize = 12.sp)
                    Text("Unsupported Families: ${diagnostics.unsupportedFamilies}", color = ThemeTextColor, fontSize = 12.sp)
                    Text("Cable Records: ${diagnostics.cableRecords}", color = ThemeTextColor, fontSize = 12.sp)
                    Text("Missing R/X: ${diagnostics.missingRx}", color = ThemeTextColor, fontSize = 12.sp)
                    Text("Duplicate Keys: ${diagnostics.duplicateKeys}", color = ThemeTextColor, fontSize = 12.sp)
                    Text("Orphan Type Codes: ${diagnostics.orphanTypeCodes}", color = ThemeTextColor, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showDiagnosticsDialog = false },
                    modifier = Modifier.testTag("diagnostics_ok_btn")
                ) {
                    Text("OK", color = SleekPrimary)
                }
            },
            containerColor = DarkCardBg
        )
    }

    if (showResetDefaultsConfirm) {
        AlertDialog(
            onDismissRequest = { showResetDefaultsConfirm = false },
            title = { Text("Reset All Settings?", color = ThemeTextColor) },
            text = { Text("This will reset all global calculation parameters and preferences back to system factory defaults.", color = SlateTextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        preferences.resetToDefaults()
                        showResetDefaultsConfirm = false
                    }
                ) { Text("Reset", color = SleekWarning) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDefaultsConfirm = false }) { Text("Cancel", color = SlateTextSecondary) }
            },
            containerColor = DarkCardBg
        )
    }

    if (showImportProjectDialog) {
        var showManualPaste by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showImportProjectDialog = false },
            containerColor = DarkCardBg,
            title = { Text("Import Existing Project (JSON)", color = ThemeTextColor) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select a VoltAtlas project JSON file from your device:", color = SlateTextSecondary, fontSize = 12.sp)

                    Button(
                        onClick = {
                            importErrorText = null
                            importLauncher.launch(arrayOf("application/json", "text/plain"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_btn_select_import_file"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Select Folder", tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select JSON File", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }

                    if (importErrorText != null) {
                        Text(importErrorText ?: "Import failed", color = SleekFail, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))

                    TextButton(
                        onClick = { showManualPaste = !showManualPaste },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (showManualPaste) "Hide manual paste" else "Or paste JSON text manually",
                            color = SlateTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    if (showManualPaste) {
                        OutlinedTextField(
                            value = importJsonInput,
                            onValueChange = { importJsonInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            placeholder = { Text("{\n  \"project\": { \"projectName\": \"My Electrical Project\" },\n  \"circuits\": [...]\n}", color = SlateTextSecondary, fontSize = 11.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = ThemeTextColor,
                                unfocusedTextColor = ThemeTextColor,
                                focusedBorderColor = SleekPrimary,
                                unfocusedBorderColor = BorderColor
                            )
                        )

                        FilledTonalButton(
                            onClick = {
                                if (importJsonInput.isNotBlank()) {
                                    viewModel.importProjectJson(importJsonInput.trim()) { success, msg ->
                                        if (success) {
                                            showImportProjectDialog = false
                                            viewModel.closeGlobalSettings()
                                        } else {
                                            importErrorText = msg
                                        }
                                    }
                                } else {
                                    importErrorText = "Please paste valid project JSON data."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Import Pasted JSON") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImportProjectDialog = false }) { Text("Cancel", color = SlateTextSecondary) }
            }
        )
    }
}


@Composable
private fun DiagnosticRow(label: String, loaded: Boolean, count: Int? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (loaded) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = if (loaded) "Loaded" else "Failed",
                tint = if (loaded) SleekPass else SleekFail,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = ThemeTextColor, fontSize = 13.sp)
        }
        if (loaded && count != null) {
            Text("$count records", color = SlateTextSecondary, fontSize = 12.sp)
        } else {
            Text(
                text = if (loaded) "Loaded" else "Failed",
                color = if (loaded) SleekPass else SleekFail,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

