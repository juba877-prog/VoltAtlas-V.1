package com.example.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.GlobalPreferences
import com.example.pdf.ReportOptions
import com.example.ui.theme.*

@Composable
fun PdfExportDialog(
    projectId: String,
    currentProjectName: String,
    currentClientName: String,
    preferences: GlobalPreferences,
    onDismiss: () -> Unit,
    onExport: (ReportOptions) -> Unit
) {
    var projectName by rememberSaveable { mutableStateOf(currentProjectName) }
    var engineer by rememberSaveable { mutableStateOf(preferences.getReportEngineer(projectId)) }
    var contractorCompany by rememberSaveable { mutableStateOf(preferences.getReportContractor(projectId)) }
    var consultant by rememberSaveable { mutableStateOf(preferences.getReportConsultant(projectId)) }
    var owner by rememberSaveable {
        mutableStateOf(preferences.getReportOwner(projectId).ifBlank { currentClientName })
    }
    var date by rememberSaveable { mutableStateOf(preferences.getReportDate(projectId)) }
    var revision by rememberSaveable { mutableStateOf(preferences.getReportRevision(projectId)) }

    var isPortrait by rememberSaveable { mutableStateOf(true) }
    var includeCalculationDetails by rememberSaveable { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ThemeSurface),
            border = BorderStroke(1.dp, ThemeBorderSubtle)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                SleekPrimary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Export PDF",
                            tint = SleekPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Export PDF Report",
                            color = ThemeTextColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Report metadata & title block details",
                            color = SlateTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = ThemeBorderSubtle)
                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable fields
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text("Project Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("pdf_project_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SleekPrimary,
                            unfocusedBorderColor = ThemeBorderSubtle,
                            focusedLabelColor = SleekPrimary,
                            unfocusedLabelColor = SlateTextSecondary,
                            focusedTextColor = ThemeTextColor,
                            unfocusedTextColor = ThemeTextColor
                        )
                    )

                    OutlinedTextField(
                        value = engineer,
                        onValueChange = { engineer = it },
                        label = { Text("Engineer") },
                        placeholder = { Text("e.g. Eng. John Doe") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("pdf_engineer_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SleekPrimary,
                            unfocusedBorderColor = ThemeBorderSubtle,
                            focusedLabelColor = SleekPrimary,
                            unfocusedLabelColor = SlateTextSecondary,
                            focusedTextColor = ThemeTextColor,
                            unfocusedTextColor = ThemeTextColor
                        )
                    )

                    OutlinedTextField(
                        value = contractorCompany,
                        onValueChange = { contractorCompany = it },
                        label = { Text("Contractor Company") },
                        placeholder = { Text("e.g. Apex Electrical Contracting") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("pdf_contractor_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SleekPrimary,
                            unfocusedBorderColor = ThemeBorderSubtle,
                            focusedLabelColor = SleekPrimary,
                            unfocusedLabelColor = SlateTextSecondary,
                            focusedTextColor = ThemeTextColor,
                            unfocusedTextColor = ThemeTextColor
                        )
                    )

                    OutlinedTextField(
                        value = consultant,
                        onValueChange = { consultant = it },
                        label = { Text("Consultant") },
                        placeholder = { Text("e.g. Global Engineering Consultancy") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("pdf_consultant_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SleekPrimary,
                            unfocusedBorderColor = ThemeBorderSubtle,
                            focusedLabelColor = SleekPrimary,
                            unfocusedLabelColor = SlateTextSecondary,
                            focusedTextColor = ThemeTextColor,
                            unfocusedTextColor = ThemeTextColor
                        )
                    )

                    OutlinedTextField(
                        value = owner,
                        onValueChange = { owner = it },
                        label = { Text("Owner / Client") },
                        placeholder = { Text("e.g. City Development Authority") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("pdf_owner_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SleekPrimary,
                            unfocusedBorderColor = ThemeBorderSubtle,
                            focusedLabelColor = SleekPrimary,
                            unfocusedLabelColor = SlateTextSecondary,
                            focusedTextColor = ThemeTextColor,
                            unfocusedTextColor = ThemeTextColor
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = date,
                            onValueChange = { date = it },
                            label = { Text("Date") },
                            singleLine = true,
                            modifier = Modifier.weight(1.3f).testTag("pdf_date_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SleekPrimary,
                                unfocusedBorderColor = ThemeBorderSubtle,
                                focusedLabelColor = SleekPrimary,
                                unfocusedLabelColor = SlateTextSecondary,
                                focusedTextColor = ThemeTextColor,
                                unfocusedTextColor = ThemeTextColor
                            )
                        )

                        OutlinedTextField(
                            value = revision,
                            onValueChange = { revision = it },
                            label = { Text("Revision") },
                            singleLine = true,
                            modifier = Modifier.weight(0.7f).testTag("pdf_revision_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SleekPrimary,
                                unfocusedBorderColor = ThemeBorderSubtle,
                                focusedLabelColor = SleekPrimary,
                                unfocusedLabelColor = SlateTextSecondary,
                                focusedTextColor = ThemeTextColor,
                                unfocusedTextColor = ThemeTextColor
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    HorizontalDivider(color = ThemeBorderSubtle)
                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = isPortrait,
                            onCheckedChange = { isPortrait = it },
                            colors = CheckboxDefaults.colors(checkedColor = SleekPrimary)
                        )
                        Text("Portrait Layout", color = ThemeTextColor, fontSize = 13.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = includeCalculationDetails,
                            onCheckedChange = { includeCalculationDetails = it },
                            colors = CheckboxDefaults.colors(checkedColor = SleekPrimary)
                        )
                        Text("Include Calculation Details", color = ThemeTextColor, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ThemeBorderSubtle)
                    ) {
                        Text("Cancel", color = ThemeTextColor, fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            preferences.setReportEngineer(projectId, engineer)
                            preferences.setReportContractor(projectId, contractorCompany)
                            preferences.setReportConsultant(projectId, consultant)
                            preferences.setReportOwner(projectId, owner)
                            preferences.setReportDate(projectId, date)
                            preferences.setReportRevision(projectId, revision)

                            onExport(
                                ReportOptions(
                                    isCompleteProjectReport = true,
                                    selectedSections = listOf(
                                        "Executive Summary",
                                        "Circuits & Cable Sizing",
                                        "Transformers",
                                        "Power Systems",
                                        "Protection & Short Circuit",
                                        "Conduits",
                                        "BOQ & Procurement"
                                    ),
                                    isPortrait = isPortrait,
                                    includeCalculationDetails = includeCalculationDetails,
                                    projectName = projectName,
                                    engineer = engineer,
                                    contractorCompany = contractorCompany,
                                    consultant = consultant,
                                    owner = owner,
                                    date = date,
                                    revision = revision
                                )
                            )
                        },
                        modifier = Modifier.weight(1f).height(42.dp).testTag("pdf_export_confirm_btn"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)
                    ) {
                        Text("Generate PDF", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
