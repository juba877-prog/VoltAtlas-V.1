package com.example.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * Top-level workspace routes. Each owns an independent back stack (Navigation 3
 * multiple-back-stacks recipe), so switching bottom-navigation destinations keeps
 * every destination's stack and scroll state.
 */
val WorkspaceTopLevelRoutes: List<NavKey> = listOf(
    WorkspaceHomeKey,
    CircuitsKey,
    TransformersKey,
    PowerSystemsKey,
    MoreModulesKey
)

/** Workspace destinations rendered as Navigation 3 dialog scenes (slice 4). */
val NavKey.isWorkspaceDialog: Boolean
    get() = this is NewProjectConfirmDialogKey || this is DiagnosticsDialogKey

/**
 * Navigation state for the project workspace: one back stack per top-level route
 * plus the selected top-level route. The Home stack is always part of the visible
 * entries (the "exit through home" pattern), mirroring the legacy hierarchy where
 * system back at Home closed the project and returned to the Project Picker.
 */
class WorkspaceNavigationState(
    val startRoute: NavKey,
    private val topLevelRouteState: MutableState<NavKey>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>
) {
    var topLevelRoute: NavKey
        get() = topLevelRouteState.value
        internal set(value) {
            topLevelRouteState.value = value
        }

    private val topStack: NavBackStack<NavKey> get() = backStacks.getValue(topLevelRoute)

    /** Visible workspace destination, ignoring any dialog entry stacked on top. */
    val currentKey: NavKey
        get() = topStack.lastOrNull { !it.isWorkspaceDialog } ?: topStack.last()

    /**
     * True when the current top-level stack is at its base entry. NavDisplay has nothing
     * to pop in that state, so system Back must run the explicit workspace hierarchy
     * (non-Home destination -> Home, Home -> exit workspace / close project).
     */
    val isAtTopLevelBase: Boolean
        get() = backStacks.getValue(topLevelRoute).size <= 1
}

// The selected top-level route is one of the five workspace tab keys, so its legacy
// tab index is a compact, allocation-free saveable representation (no serialization).
private val TopLevelRouteSaver: Saver<NavKey, Int> = Saver(
    save = { it.workspaceTabIndex() },
    restore = { workspaceKeyForTabIndex(it) }
)

@Composable
fun rememberWorkspaceNavigationState(): WorkspaceNavigationState {
    val startRoute = WorkspaceHomeKey
    val topLevelRoute = rememberSaveable(stateSaver = TopLevelRouteSaver) {
        mutableStateOf(startRoute)
    }
    val backStacks = WorkspaceTopLevelRoutes.associateWith { rememberNavBackStack(it) }
    return remember(startRoute, backStacks) {
        WorkspaceNavigationState(startRoute, topLevelRoute, backStacks)
    }
}

/**
 * Translates navigation events into workspace state updates.
 *
 * Back hierarchy (preserved from the removed PredictiveBackHandler):
 *  1. any dialog / child entry on top of the current stack pops first,
 *  2. a non-Home top-level base returns to Home,
 *  3. Home's base invokes [onExitWorkspace] (closes the project -> Project Picker).
 */
class WorkspaceNavigator(
    private val state: WorkspaceNavigationState,
    private val onExitWorkspace: () -> Unit
) {
    val currentKey: NavKey get() = state.currentKey

    fun navigate(key: NavKey) {
        when {
            // Top-level destination: switch the selected tab, keeping every stack.
            key in state.backStacks -> state.topLevelRoute = key
            // Origin-based destination or dialog: stays on the stack it was opened
            // from (Panel Schedule returns to Circuits or More, replacing the removed
            // panelScheduleReturnTab memory) and dialogs overlay the current tab.
            key is PanelScheduleKey || key.isWorkspaceDialog ->
                state.backStacks.getValue(state.topLevelRoute).add(key)
            // More-modules children are parented under the More stack, preserving the
            // legacy "child back returns to the More grid" hierarchy.
            else -> {
                state.topLevelRoute = MoreModulesKey
                state.backStacks.getValue(MoreModulesKey).add(key)
            }
        }
    }

    fun navigateToTabIndex(index: Int) = navigate(workspaceKeyForTabIndex(index))

    fun goBack() {
        val stack = state.backStacks.getValue(state.topLevelRoute)
        when {
            stack.size > 1 -> stack.removeLastOrNull()
            state.topLevelRoute == state.startRoute -> onExitWorkspace()
            else -> state.topLevelRoute = state.startRoute
        }
    }
}

/**
 * Converts the workspace state into decorated NavEntries: the Home stack plus the
 * currently selected top-level stack (Navigation 3 multiple-back-stacks recipe).
 */
@Composable
fun WorkspaceNavigationState.toDecoratedEntries(
    entryProvider: (NavKey) -> NavEntry<NavKey>
): List<NavEntry<NavKey>> {
    val homeEntries = rememberDecoratedNavEntries(
        backStack = backStacks.getValue(startRoute),
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>()),
        entryProvider = entryProvider
    )
    if (topLevelRoute == startRoute) return homeEntries
    val topEntries = rememberDecoratedNavEntries(
        backStack = backStacks.getValue(topLevelRoute),
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>()),
        entryProvider = entryProvider
    )
    return homeEntries + topEntries
}
