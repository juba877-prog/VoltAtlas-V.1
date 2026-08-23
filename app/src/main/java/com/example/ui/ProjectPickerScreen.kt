package com.example.ui

import com.example.ui.theme.*

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import com.example.data.ProjectEntity
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

val BrandCyan: Color
    @Composable get() = SleekPrimary
val BrandGreen: Color
    @Composable get() = StatusNeutral

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectPickerScreen(
    projects: List<ProjectEntity>,
    onCreateProject: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onRenameProject: (String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onExportProjectJson: (suspend (String) -> String)? = null,
    onImportProjectJson: (String, (Boolean, String) -> Unit) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
    onOpenQuickCalc: () -> Unit = {},
    confirmDestructiveActions: Boolean = true
) {
    val brandCyan = BrandCyan
    val brandGreen = BrandGreen
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var newProjectName by rememberSaveable { mutableStateOf("") }

    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var importJsonText by rememberSaveable { mutableStateOf("") }
    var importErrorMsg by rememberSaveable { mutableStateOf<String?>(null) }

    var pendingExportProject by remember { mutableStateOf<ProjectEntity?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val proj = pendingExportProject
        if (uri != null && proj != null && onExportProjectJson != null) {
            scope.launch {
                try {
                    val jsonStr = onExportProjectJson(proj.projectId)
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonStr.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "Exported '${proj.projectName}' successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    pendingExportProject = null
                }
            }
        } else {
            pendingExportProject = null
        }
    }

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
                        importErrorMsg = "Selected file is empty."
                    } else {
                        onImportProjectJson(jsonStr) { success, msg ->
                            if (success) {
                                showImportDialog = false
                                Toast.makeText(context, "Project imported successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                importErrorMsg = msg
                            }
                        }
                    }
                } catch (e: Exception) {
                    importErrorMsg = "Failed to read file: ${e.message}"
                }
            }
        }
    }

    fun launchExport(project: ProjectEntity) {
        pendingExportProject = project
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val safeName = project.projectName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val defaultFileName = "${safeName}_${dateStr}.json"
        exportLauncher.launch(defaultFileName)
    }

    
    var showRenameDialog by rememberSaveable { mutableStateOf<ProjectEntity?>(null) }
    var renameValue by rememberSaveable { mutableStateOf("") }
    
    var showDeleteConfirm by rememberSaveable { mutableStateOf<ProjectEntity?>(null) }
    
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredProjects = remember(projects, searchQuery) {
        if (searchQuery.isBlank()) {
            projects
        } else {
            projects.filter {
                it.projectName.contains(searchQuery, ignoreCase = true) ||
                (it.clientName ?: "").contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        // Faint decorative electrical schematic grid lines
        
        Canvas(modifier = Modifier.fillMaxSize().alpha(0.05f)) {
            val width = size.width
            val height = size.height
            val stepX = width / 12f
            val stepY = height / 20f
            
            // Grid lines
            for (i in 1..11) {
                drawLine(
                    color = brandCyan,
                    start = Offset(i * stepX, 0f),
                    end = Offset(i * stepX, height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            for (i in 1..19) {
                drawLine(
                    color = brandCyan,
                    start = Offset(0f, i * stepY),
                    end = Offset(width, i * stepY),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .testTag("settings_btn")
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = BrandCyan
            )
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp)
            ) {
                // VA Monogram Canvas
                Box(
                    modifier = Modifier.size(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    
        Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        
                        val vLeftX = w * 0.31f
                        val vLeftY = h * 0.35f
                        val vBottomX = w * 0.50f
                        val vBottomY = h * 0.72f
                        val vRightX = w * 0.69f
                        val vRightY = h * 0.35f
                        
                        val strokePx = 6.dp.toPx()
                        
                        // Draw V (Cyan)
                        drawLine(
                            color = brandCyan,
                            start = Offset(vLeftX, vLeftY),
                            end = Offset(vBottomX, vBottomY),
                            strokeWidth = strokePx,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = brandCyan,
                            start = Offset(vBottomX, vBottomY),
                            end = Offset(vRightX, vRightY),
                            strokeWidth = strokePx,
                            cap = StrokeCap.Round
                        )
                        
                        // Draw Crossbar representing A (Green)
                        val barY = h * 0.53f
                        val barStartX = w * 0.41f
                        val barEndX = w * 0.59f
                        
                        drawLine(
                            color = brandGreen,
                            start = Offset(barStartX, barY),
                            end = Offset(barEndX, barY),
                            strokeWidth = strokePx,
                            cap = StrokeCap.Round
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Volt",
                        color = ThemeTextColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Atlas",
                        color = brandCyan,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Electrical Engineering Platform",
                    color = SlateTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }

            // Body
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { 
                                newProjectName = ""
                                showCreateDialog = true 
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.horizontalGradient(colors = listOf(BrandCyan, SleekPrimary))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+ New Project", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                importJsonText = ""
                                importErrorMsg = null
                                showImportDialog = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BrandCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandCyan),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("📥 Import Project", color = brandCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Engineering Standalone Scratchpad Action Button
                    OutlinedButton(
                        onClick = onOpenQuickCalc,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("quick_calc_btn"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BrandGreen),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandGreen),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Quick Engineering",
                                tint = BrandGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Quick Engineering",
                                color = BrandGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))



                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search projects...", color = SlateTextSecondary) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = BrandCyan)
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
                            cursorColor = BrandCyan,
                            focusedBorderColor = BorderColor,
                            unfocusedBorderColor = BorderColor
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Section Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recent Projects", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${filteredProjects.size} projects", color = SlateTextSecondary, fontSize = 14.sp)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (projects.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "No Projects",
                                    tint = BrandCyan,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No Saved Projects Yet",
                                    color = ThemeTextColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                            text = "Start by creating a new electrical engineering project.",
                                    color = SlateTextSecondary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        newProjectName = ""
                                        showCreateDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandCyan),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Create New Project", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                } else if (filteredProjects.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No projects match your search.", color = SlateTextSecondary, fontSize = 16.sp)
                        }
                    }
                } else {
                    items(filteredProjects, key = { it.projectId }) { project ->
                        ProjectCard(
                            project = project,
                            onClick = { onOpenProject(project.projectId) },
                            onExport = { launchExport(project) },
                            onRename = { 
                                renameValue = project.projectName
                                showRenameDialog = project
                            },
                            onDelete = {
                                if (confirmDestructiveActions) {
                                    showDeleteConfirm = project
                                } else {
                                    onDeleteProject(project.projectId)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Security, contentDescription = "Security Status", tint = SlateTextSecondary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("All projects are stored locally and securely", color = SlateTextSecondary, fontSize = 12.sp)
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = DarkCardBg,
            title = { Text("Create New Project", color = ThemeTextColor) },
            text = {
                OutlinedTextField(
                    value = newProjectName,
                    onValueChange = { newProjectName = it },
                    label = { Text("Project Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ThemeTextColor,
                        unfocusedTextColor = ThemeTextColor,
                        focusedBorderColor = SleekPrimary,
                        unfocusedBorderColor = BorderColor
                    ),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newProjectName.isNotBlank()) {
                            onCreateProject(newProjectName.trim())
                            showCreateDialog = false
                        }
                    }
                ) { Text("Create", color = SleekPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel", color = SlateTextSecondary) }
            }
        )
    }

    if (showRenameDialog != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            containerColor = DarkCardBg,
            title = { Text("Rename Project", color = ThemeTextColor) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("New Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ThemeTextColor,
                        unfocusedTextColor = ThemeTextColor,
                        focusedBorderColor = SleekPrimary,
                        unfocusedBorderColor = BorderColor
                    ),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameValue.isNotBlank()) {
                            showRenameDialog?.let { dialog -> onRenameProject(dialog.projectId, renameValue.trim()); showRenameDialog = null }
                        }
                    }
                ) { Text("Save", color = SleekPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) { Text("Cancel", color = SlateTextSecondary) }
            }
        )
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = DarkCardBg,
            title = { Text("Delete Project?", color = SleekFail) },
            text = { Text("Are you sure you want to delete '${showDeleteConfirm?.projectName}'? This action cannot be undone.", color = SlateTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm?.let { dialog -> onDeleteProject(dialog.projectId); showDeleteConfirm = null }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SleekFail)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel", color = SlateTextSecondary) }
            }
        )
    }

    if (showImportDialog) {
        var showManualPaste by rememberSaveable { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = DarkCardBg,
            title = { Text("Import Existing Project (JSON)", color = ThemeTextColor) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select a VoltAtlas project JSON file from your device:", color = SlateTextSecondary, fontSize = 12.sp)

                    Button(
                        onClick = {
                            importErrorMsg = null
                            importLauncher.launch(arrayOf("application/json", "text/plain"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_select_import_file"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Select Folder", tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select JSON File", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }

                    if (importErrorMsg != null) {
                        Text(importErrorMsg ?: "Import failed", color = SleekFail, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                            value = importJsonText,
                            onValueChange = { importJsonText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            placeholder = { Text("{\n  \"project\": { \"projectName\": \"My Project\" },\n  \"circuits\": [...]\n}", color = SlateTextSecondary, fontSize = 11.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = ThemeTextColor,
                                unfocusedTextColor = ThemeTextColor,
                                focusedBorderColor = SleekPrimary,
                                unfocusedBorderColor = BorderColor
                            )
                        )

                        FilledTonalButton(
                            onClick = {
                                if (importJsonText.isNotBlank()) {
                                    onImportProjectJson(importJsonText.trim()) { success, msg ->
                                        if (success) {
                                            showImportDialog = false
                                        } else {
                                            importErrorMsg = msg
                                        }
                                    }
                                } else {
                                    importErrorMsg = "Please paste valid project JSON data."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Import Pasted JSON")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel", color = SlateTextSecondary) }
            }
        )
    }
}



@Composable
private fun ProjectCard(
    project: ProjectEntity,
    onClick: () -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val brandCyan = BrandCyan
    val brandGreen = BrandGreen

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Tile
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(if (LocalThemeIsDark.current) SlateBorderDark else SlateBorderLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = project.projectName.take(1).uppercase(),
                    color = brandCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(project.projectName, color = ThemeTextColor, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(modifier = Modifier.height(4.dp))
                
                val subtitle = buildString {
                    if (!project.clientName.isNullOrBlank()) {
                        append(project.clientName)
                        append(" • ")
                    }
                    append(dateFormat.format(Date(project.updatedAt)))
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status dot
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(StatusNeutral, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Active", color = StatusNeutral, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(subtitle, color = SlateTextSecondary, fontSize = 11.sp, maxLines = 1, softWrap = false)
                }
            }

            
            IconButton(onClick = onExport, modifier = Modifier.testTag("export_project_${project.projectId}")) {
                Icon(Icons.Default.Share, contentDescription = "Export Project", tint = BrandCyan)
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = BrandCyan)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = SleekFail)
            }
        }
    }
}
