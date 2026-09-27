package com.example.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.context.EngineeringContext

data class AssistantCapability(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val prompt: String
)

/**
 * Production MD3 Assistant Idle Landing Surface.
 *
 * Requirements (Gate 12 State A):
 * - Communicates useful engineering capabilities without pretending calculations were done.
 * - Zero fabricated metrics.
 * - Suggests engineering prompts for the active context.
 * - Test tag: `assistant_idle_landing`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantIdleLanding(
    context: EngineeringContext,
    onPromptClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val capabilities = listOf(
        AssistantCapability(
            title = "Cable Sizing & Verification",
            description = "Calculate conductor ampacity, voltage drop, and protection coordination under IEC 60364-5-52.",
            icon = Icons.Default.Bolt,
            prompt = "Size and verify the cable for this circuit."
        ),
        AssistantCapability(
            title = "Constraint Diagnosis",
            description = "Analyze governing constraints and actual vs limit values for non-compliant feeders.",
            icon = Icons.Default.Warning,
            prompt = "Diagnose the governing constraints and check failures."
        ),
        AssistantCapability(
            title = "What-If Scenarios",
            description = "Simulate conductor or load overrides in-memory without modifying the database.",
            icon = Icons.Default.Science,
            prompt = "What if we use a 240 mm² cable?"
        ),
        AssistantCapability(
            title = "Candidate Comparison",
            description = "Compare multiple standard cable cross-sections side-by-side deterministically.",
            icon = Icons.AutoMirrored.Filled.CompareArrows,
            prompt = "Compare candidate cable sizes for this load."
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("assistant_idle_landing"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Welcome Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Engineering Assistant",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = "VoltAtlas Engineering Assistant",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onSurface
            )

            val contextText = when {
                context.activeEntityName != null -> "Context: ${context.activeEntityType ?: "Entity"} ${context.activeEntityName}"
                context.projectName.isNotBlank() -> "Project: ${context.projectName}"
                else -> "Grounded Electrical Engineering Agent"
            }
            Text(
                text = contextText,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Capabilities Grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "CORE CAPABILITIES",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            capabilities.forEach { cap ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPromptClick(cap.prompt) }
                        .semantics { contentDescription = "Capability: ${cap.title}" }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = cap.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cap.title,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = cap.description,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

    }
}
