package com.example.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.example.ui.adaptive.VoltAtlasNavRailMinWidthDp
import com.example.ui.theme.SlateTextSecondary
import com.example.ui.theme.SleekPrimary
import com.example.ui.theme.ThemeBackground
import com.example.ui.theme.ThemeBorderSubtle
import com.example.ui.theme.ThemeSurface

/**
 * Navigation 3 List-Detail Scene for Quick Engineering (Step 3).
 *
 * Displays a two-pane layout on wide screens (>= 600dp):
 *  - Left Pane (38% width): Quick Engineering Toolbox Hub (search & category cards).
 *  - Right Pane (62% width): The active Quick Tool scratchpad (or placeholder when none selected).
 *
 * On narrow screens (< 600dp, e.g. phones portrait), [QuickEngineeringListDetailSceneStrategy]
 * returns `null` so NavDisplay falls back to single-pane navigation.
 */
class QuickEngineeringListDetailScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    private val listEntry: NavEntry<T>,
    private val detailEntry: NavEntry<T>?,
    private val detailPlaceholder: @Composable () -> Unit
) : Scene<T> {

    override val entries: List<NavEntry<T>> = listOfNotNull(listEntry, detailEntry)

    override val content: @Composable (() -> Unit) = {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(ThemeBackground)
                .testTag("quick_engineering_two_pane_row")
        ) {
            // Master / List Pane (38% width)
            Box(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight()
                    .testTag("quick_hub_list_pane")
            ) {
                listEntry.Content()
            }

            // Subtle vertical divider between panes
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(ThemeBorderSubtle)
            )

            // Detail Pane (62% width)
            Box(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight()
                    .testTag("quick_tool_detail_pane")
            ) {
                if (detailEntry != null) {
                    detailEntry.Content()
                } else {
                    detailPlaceholder()
                }
            }
        }
    }

    companion object {
        fun listPane(): Map<String, Any> = mapOf(LIST_PANE_METADATA_KEY to true)
        fun detailPane(): Map<String, Any> = mapOf(DETAIL_PANE_METADATA_KEY to true)

        const val LIST_PANE_METADATA_KEY: String = "com.example.navigation.quickListPane"
        const val DETAIL_PANE_METADATA_KEY: String = "com.example.navigation.quickDetailPane"
    }
}

/**
 * [SceneStrategy] producing [QuickEngineeringListDetailScene] on wide displays (>= 600dp).
 */
class QuickEngineeringListDetailSceneStrategy<T : Any>(
    val windowWidthDp: Int,
    private val detailPlaceholder: @Composable () -> Unit
) : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (windowWidthDp < VoltAtlasNavRailMinWidthDp) return null
        val listEntry = entries
            .findLast { it.metadata.containsKey(QuickEngineeringListDetailScene.LIST_PANE_METADATA_KEY) }
            ?: return null
        val detailEntry = entries.lastOrNull()?.takeIf {
            it !== listEntry && it.metadata.containsKey(QuickEngineeringListDetailScene.DETAIL_PANE_METADATA_KEY)
        }
        return QuickEngineeringListDetailScene(
            key = listEntry.contentKey,
            previousEntries = entries.takeWhile { it !== listEntry },
            listEntry = listEntry,
            detailEntry = detailEntry,
            detailPlaceholder = detailPlaceholder
        )
    }
}

@Composable
fun <T : Any> rememberQuickEngineeringListDetailSceneStrategy(
    detailPlaceholder: @Composable () -> Unit = { QuickToolPlaceholder() }
): QuickEngineeringListDetailSceneStrategy<T> {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return remember(widthDp, detailPlaceholder) {
        QuickEngineeringListDetailSceneStrategy(widthDp, detailPlaceholder)
    }
}

/**
 * Placeholder displayed in the detail pane when no tool has been selected yet.
 */
@Composable
fun QuickToolPlaceholder(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ThemeBackground)
            .padding(32.dp)
            .testTag("quick_tool_placeholder"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(ThemeSurface, shape = MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = null,
                    tint = SleekPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Quick Engineering Toolbox",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Select a calculation on the left to start.",
                style = MaterialTheme.typography.bodyMedium,
                color = SlateTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
