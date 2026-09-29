package com.example.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 keys for the VoltAtlas app-level flow.
 *
 * Slice 1 scope (Phase 3.2): Entry -> { ProjectPicker | QuickHub -> QuickTool }.
 * The workspace / tab destinations remain on the existing state-based router until the
 * multiple-back-stacks slice, so this stack never holds a workspace destination.
 *
 * Keys must be serializable so `rememberNavBackStack` can persist the stack across
 * configuration changes and process death.
 */
@Serializable
data object EntryKey : NavKey

@Serializable
data object ProjectPickerKey : NavKey

@Serializable
data object QuickHubKey : NavKey

@Serializable
data class QuickToolKey(val toolId: String) : NavKey

// ── Workspace destinations (Phase 3.2 slice 3: multiple back stacks) ──────────
// Top-level routes (own independent back stack each): WorkspaceHomeKey,
// CircuitsKey, TransformersKey, PowerSystemsKey, MoreModulesKey.
// Children of the More stack: Protection/Sld/Conduits/Boq/WorkspaceSettings/
// Conversions/Methodology. Origin-based: PanelScheduleKey.
// Dialog scenes (slice 4): NewProjectConfirmDialogKey, DiagnosticsDialogKey.
@Serializable
data object WorkspaceHomeKey : NavKey

@Serializable
data object CircuitsKey : NavKey

@Serializable
data object TransformersKey : NavKey

@Serializable
data object PowerSystemsKey : NavKey

@Serializable
data object MoreModulesKey : NavKey

@Serializable
data object ProtectionKey : NavKey

@Serializable
data object SldKey : NavKey

@Serializable
data object ConduitsKey : NavKey

@Serializable
data object BoqProcurementKey : NavKey

@Serializable
data object WorkspaceSettingsKey : NavKey

@Serializable
data object ConversionsKey : NavKey

@Serializable
data object MethodologyKey : NavKey

@Serializable
data object PanelScheduleKey : NavKey

@Serializable
data object NewProjectConfirmDialogKey : NavKey

@Serializable
data object DiagnosticsDialogKey : NavKey

/**
 * Legacy tab index of a workspace key, mirroring the pre-Navigation 3 `currentTab`
 * contract that the shell chrome (bottom bar / rail selection, assistant context,
 * module title) still speaks. Keeping the mapping in one place lets the UI keep
 * its existing semantics while navigation itself is stack-based.
 */
fun NavKey.workspaceTabIndex(): Int = when (this) {
    WorkspaceHomeKey -> 0
    CircuitsKey -> 1
    TransformersKey -> 2
    PowerSystemsKey -> 3
    ProtectionKey -> 4
    SldKey -> 5
    ConduitsKey -> 6
    BoqProcurementKey -> 7
    WorkspaceSettingsKey -> 8
    MoreModulesKey -> 9
    ConversionsKey -> 10
    MethodologyKey -> 11
    PanelScheduleKey -> 12
    else -> 0
}

/** Inverse of [NavKey.workspaceTabIndex] for legacy Int-based tab callbacks. */
fun workspaceKeyForTabIndex(index: Int): NavKey = when (index) {
    1 -> CircuitsKey
    2 -> TransformersKey
    3 -> PowerSystemsKey
    4 -> ProtectionKey
    5 -> SldKey
    6 -> ConduitsKey
    7 -> BoqProcurementKey
    8 -> WorkspaceSettingsKey
    9 -> MoreModulesKey
    10 -> ConversionsKey
    11 -> MethodologyKey
    12 -> PanelScheduleKey
    else -> WorkspaceHomeKey
}
