package com.example.sld

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.example.ui.UiState

@Composable
fun SldScreen(
    state: UiState.Success,
    activeProjectId: String,
    projectName: String = "VoltAtlas",
    isFullscreen: Boolean = false,
    onToggleFullscreen: (Boolean) -> Unit = {},
    onBackToProjects: () -> Unit = {},
    onNavigateToTab: (Int) -> Unit = {},
    manualPositions: MutableMap<String, Offset> = mutableMapOf(),
    projectRevision: Long? = null,
    modifier: Modifier = Modifier
) {
    com.example.ui.SldScreen(
        circuits = state.circuits,
        activeProjectId = activeProjectId,
        projectName = projectName,
        sizingResults = state.sizingResults,
        shortCircuitResults = state.shortCircuitResults,
        atsEntity = state.atsEntity,
        transformers = state.transformers,
        transformerResults = state.transformerResults,
        generatorResult = state.generatorResult,
        generators = state.generators,
        generatorResults = state.generatorResults,
        atsResult = state.atsResult,
        atsList = state.atsList,
        atsResults = state.atsResults,
        upsEntity = state.upsList.firstOrNull(),
        upsResult = state.upsResult,
        upsList = state.upsList,
        upsResults = state.upsResults,
        protectionResults = state.protectionResults,
        adsResults = state.adsResults,
        projectRevision = projectRevision,
        manualPositions = manualPositions,
        onNavigateToTab = onNavigateToTab,
        modifier = modifier
    )
}
