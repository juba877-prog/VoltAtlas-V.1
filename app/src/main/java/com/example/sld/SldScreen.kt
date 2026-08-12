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
    modifier: Modifier = Modifier
) {
    com.example.ui.SldScreen(
        circuits = state.circuits,
        activeProjectId = activeProjectId,
        sizingResults = state.sizingResults,
        shortCircuitResults = state.shortCircuitResults,
        atsEntity = state.atsEntity,
        transformers = state.transformers,
        transformerResults = state.transformerResults,
        generatorResult = state.generatorResult,
        atsResult = state.atsResult,
        modifier = modifier
    )
}
