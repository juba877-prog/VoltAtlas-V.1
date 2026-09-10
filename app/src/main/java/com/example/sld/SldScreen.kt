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
        transformers = state.transformers,
        transformerResults = state.transformerResults,
        generators = state.generators,
        generatorResults = state.generatorResults,
        atsList = state.atsList,
        atsResults = state.atsResults,
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
