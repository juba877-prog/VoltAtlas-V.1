package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeGuideSheet(
    onDismiss: () -> Unit,
    onNavigateToMethodology: (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ThemeSurface,
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.testTag("welcome_guide_modal_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Title Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = SleekPrimary.copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ElectricBolt,
                            contentDescription = null,
                            tint = SleekPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Welcome to VoltAtlas",
                        style = SectionTitle,
                        color = ThemeTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        text = "Electrical Engineering & System Sizing Suite",
                        style = Caption,
                        color = ThemeTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = ThemeBorderSubtle.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // Workflow Step Items
            WorkflowStepItem(
                stepNumber = "1",
                icon = Icons.Default.FolderSpecial,
                title = "Create a Project",
                description = "Start from the Project Picker or import an existing JSON project configuration."
            )

            Spacer(modifier = Modifier.height(12.dp))

            WorkflowStepItem(
                stepNumber = "2",
                icon = Icons.Default.AccountTree,
                title = "Build Your Panel Schedule",
                description = "Add sub-panels and direct loads; VoltAtlas recursively aggregates loads and balances phases automatically."
            )

            Spacer(modifier = Modifier.height(12.dp))

            WorkflowStepItem(
                stepNumber = "3",
                icon = Icons.Default.Calculate,
                title = "Size Cables & Protection",
                description = "Circuits, Transformers, and Protection screens evaluate voltage drop, thermal withstand, fault currents, and ADS coordination."
            )

            Spacer(modifier = Modifier.height(12.dp))

            WorkflowStepItem(
                stepNumber = "4",
                icon = Icons.Default.GridView,
                title = "Explore Specialized Tools",
                description = "Interactive Single-Line Diagrams (SLD), Conduit Fill, BOQ Procurement, and Earthing Auto-Design are available under the 'More' tab."
            )

            Spacer(modifier = Modifier.height(12.dp))

            WorkflowStepItem(
                stepNumber = "5",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = "Engineering Formulas & Standards",
                description = "Check 'Methodology & Formulas' under More anytime for the full IEC & BS engineering equations and worked examples.",
                actionLabel = if (onNavigateToMethodology != null) "Open Methodology" else null,
                onActionClick = onNavigateToMethodology
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("welcome_guide_got_it_btn")
            ) {
                Text(
                    text = "Got it, let's start",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Show me later",
                    color = ThemeTextSecondary,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WorkflowStepItem(
    stepNumber: String,
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            color = SleekPrimary.copy(alpha = 0.12f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stepNumber,
                    color = SleekPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ThemeTextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = ThemeTextPrimary,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = Caption,
                color = ThemeTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            if (actionLabel != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onActionClick,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = "→ $actionLabel",
                        color = SleekPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
