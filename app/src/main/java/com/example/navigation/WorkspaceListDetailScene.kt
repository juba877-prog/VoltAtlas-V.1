package com.example.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.example.ui.adaptive.VoltAtlasNavRailMinWidthDp

/**
 * Phase 4 — adaptive Navigation 3 scene for the workspace's More-modules flow.
 *
 * [WorkspaceListDetailScene] is produced when the window is at least as wide as the shell's
 * navigation-rail breakpoint (600dp), the hub entry is present in the back stack and either a
 * module is selected (detail pane) or [detailPlaceholder] fills the empty detail pane. Narrow
 * windows return `null` so NavDisplay keeps its single-pane scene — phones and split-screen
 * retain the existing full-screen behavior, and no state migration is implied by switching
 * layouts.
 *
 * The scene key is the list entry's content key, so switching modules swaps the detail pane
 * in place instead of animating the whole scene out.
 */
class WorkspaceListDetailScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    private val listEntry: NavEntry<T>,
    private val detailEntry: NavEntry<T>?,
    private val detailPlaceholder: @Composable () -> Unit
) : Scene<T> {

    override val entries: List<NavEntry<T>> = listOfNotNull(listEntry, detailEntry)

    override val content: @Composable (() -> Unit) = {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(0.4f)) {
                listEntry.Content()
            }
            Column(modifier = Modifier.weight(0.6f)) {
                if (detailEntry != null) detailEntry.Content() else detailPlaceholder()
            }
        }
    }

    companion object {
        /** Metadata marking the More-modules hub as the list pane. */
        fun listPane(): Map<String, Any> = mapOf(LIST_PANE_METADATA_KEY to true)

        /** Metadata marking a More module destination as the detail pane. */
        fun detailPane(): Map<String, Any> = mapOf(DETAIL_PANE_METADATA_KEY to true)

        const val LIST_PANE_METADATA_KEY: String = "com.example.navigation.workspaceListPane"
        const val DETAIL_PANE_METADATA_KEY: String = "com.example.navigation.workspaceDetailPane"
    }
}

/** [SceneStrategy] returning a [WorkspaceListDetailScene] on wide windows. */
class WorkspaceListDetailSceneStrategy<T : Any>(
    val windowWidthDp: Int,
    private val detailPlaceholder: @Composable () -> Unit
) : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (windowWidthDp < VoltAtlasNavRailMinWidthDp) return null
        val listEntry = entries
            .findLast { it.metadata.containsKey(WorkspaceListDetailScene.LIST_PANE_METADATA_KEY) }
            ?: return null
        val detailEntry = entries.lastOrNull()?.takeIf {
            it !== listEntry && it.metadata.containsKey(WorkspaceListDetailScene.DETAIL_PANE_METADATA_KEY)
        }
        return WorkspaceListDetailScene(
            key = listEntry.contentKey,
            previousEntries = entries.dropLast(1),
            listEntry = listEntry,
            detailEntry = detailEntry,
            detailPlaceholder = detailPlaceholder
        )
    }
}

@Composable
fun <T : Any> rememberWorkspaceListDetailSceneStrategy(
    detailPlaceholder: @Composable () -> Unit
): WorkspaceListDetailSceneStrategy<T> {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return remember(widthDp, detailPlaceholder) {
        WorkspaceListDetailSceneStrategy(widthDp, detailPlaceholder)
    }
}
