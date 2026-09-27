package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.boot.VoltAtlasLogo
import com.example.ui.components.EntryCircuitBackground
import com.example.ui.theme.BrandMarkBlue
import com.example.ui.theme.BrandMarkGreen
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.LightBackgroundDeep
import com.example.ui.theme.LightPrimaryBlue
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary
import com.example.ui.theme.LightTextTertiary
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.LocalThemeIsDark
import com.example.ui.theme.SlateTextSecondary


/**
 * VoltAtlas entry / mode-selection surface — the app's top-level destination.
 *
 * Responsibility: "Where does the user want to go?" It offers exactly two destinations:
 *  - Projects          -> existing Project Picker / workspace (blue)
 *  - Quick Engineering -> existing Quick Engineering hub (green)
 *
 * It contains no project actions, no search, no recent projects and no calculation
 * categories: those stay in the existing screens this surface only routes to.
 *
 * Presentation only: no engineering calculation, no persistence and no navigation-graph
 * ownership live here (MainLayout performs the actual routing).
 */
@Composable
fun VoltAtlasEntryScreen(
    onOpenProjects: () -> Unit,
    onOpenQuickEngineering: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val reduceMotion = LocalReduceMotion.current
    val isDark = LocalThemeIsDark.current

    // Lifecycle awareness: the electrical animation only runs while this screen is the resumed
    // destination. Navigating away disposes this composition entirely.
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val isVisible = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    var showExitDialog by rememberSaveable { mutableStateOf(false) }

    // Root-destination back behaviour: confirm before leaving VoltAtlas.
    BackHandler(enabled = true) {
        if (showExitDialog) showExitDialog = false else showExitDialog = true
    }

    Box(modifier = modifier.fillMaxSize()) {
        EntryCircuitBackground(
            modifier = Modifier.fillMaxSize(),
            isPlaying = isVisible,
            reduceMotion = reduceMotion
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp)
        ) {
            // --- SETTINGS (existing action, existing visual language) -------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("entry_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = if (isDark) BrandMarkBlue else LightPrimaryBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = ENTRY_CONTENT_MAX_WIDTH)
                    .align(Alignment.CenterHorizontally)
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // --- BRAND (single source: com.example.boot.VoltAtlasLogo) --------
                VoltAtlasLogo(
                    modifier = Modifier
                        .size(width = 96.dp, height = 84.dp)
                        .testTag("entry_logo"),
                    tightFit = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = if (isDark) Color.White else LightTextPrimary)) { append("Volt") }
                        withStyle(SpanStyle(color = if (isDark) BrandMarkBlue else LightPrimaryBlue)) { append("Atlas") }
                    },
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp,
                        letterSpacing = 0.5.sp
                    ),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("entry_title")
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Electrical Engineering Platform",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = if (isDark) SlateTextSecondary else LightTextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.testTag("entry_subtitle")
                )

                Spacer(modifier = Modifier.height(36.dp))

                // --- THE TWO MODES (no other primary choices, ever) ---------------
                EntryModeCard(
                    title = "Projects",
                    description = "Create, open & manage engineering projects",
                    icon = Icons.Default.Folder,
                    accent = if (isDark) BrandMarkBlue else LightPrimaryBlue,
                    cardBg = if (isDark) Color(0xFF071424) else Color(0xFFFFFFFF),
                    badgeBg = if (isDark) Color(0xFF0C243E) else Color(0xFFE0F2FE),
                    titleColor = if (isDark) Color.White else LightTextPrimary,
                    descriptionColor = if (isDark) Color(0xFF8E9AA8) else LightTextSecondary,
                    borderAlpha = if (isDark) 0.50f else 0.35f,
                    testTag = "entry_projects_card",
                    onClick = onOpenProjects
                )

                Spacer(modifier = Modifier.height(18.dp))

                EntryModeCard(
                    title = "Quick Engineering",
                    description = "Run standalone calculations without creating a project",
                    icon = Icons.Default.Bolt,
                    accent = if (isDark) BrandMarkGreen else Color(0xFF059669),
                    cardBg = if (isDark) Color(0xFF051612) else Color(0xFFFFFFFF),
                    badgeBg = if (isDark) Color(0xFF072C1E) else Color(0xFFDCFCE7),
                    titleColor = if (isDark) Color.White else LightTextPrimary,
                    descriptionColor = if (isDark) Color(0xFF8E9AA8) else LightTextSecondary,
                    borderAlpha = if (isDark) 0.50f else 0.35f,
                    testTag = "entry_quick_card",
                    onClick = onOpenQuickEngineering
                )
            }

            // --- LOCAL PROCESSING NOTE (supporting information only) --------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp, top = 8.dp)
                    .testTag("entry_security_note"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = if (isDark) Color(0xFF6B7A8D) else LightTextTertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "All calculations are performed locally and securely",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = if (isDark) Color(0xFF6B7A8D) else LightTextTertiary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit VoltAtlas?") },
            text = { Text("Are you sure you want to exit the app?") },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Cancel") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Exit") }
            }
        )
    }
}

/**
 * One of the two primary mode choices.
 *
 * Phone-first geometry: one clear full-width card with a large thumb-reachable tap area, the
 * mode's accent as a restrained border + internal glow, and an explicit pressed state without
 * any scaling or bounce. Blue and green are branding; the title/description text carries the
 * semantic distinction, so the cards are never color-only.
 */
@Composable
private fun EntryModeCard(
    title: String,
    description: String,
    icon: ImageVector,
    accent: Color,
    cardBg: Color,
    badgeBg: Color,
    titleColor: Color = Color.White,
    descriptionColor: Color = Color(0xFF8E9AA8),
    borderAlpha: Float = 0.50f,
    testTag: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 106.dp)
            .clip(shape)
            .background(
                if (isPressed) MaterialTheme.colorScheme.surfaceContainerHigh else cardBg
            )
            .border(
                width = 1.5.dp,
                color = accent.copy(alpha = if (isPressed) 0.90f else borderAlpha),
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = "Open $title",
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(30.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    color = titleColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    ),
                    color = descriptionColor
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Phone-first content cap: on wider windows the entry composition stays a centred phone-like
 * column instead of stretching into a tablet layout (per the responsive contract).
 */
private val ENTRY_CONTENT_MAX_WIDTH = 480.dp

