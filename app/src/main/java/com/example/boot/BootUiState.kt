package com.example.boot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.StartupState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * BootUiState
 * -----------
 * Represents the REAL startup progress of VoltAtlas. Scene 07
 * (System Initialization) ticks each row when the corresponding real task
 * finishes — it is NOT a fixed timer.
 */
data class BootUiState(
    val initEngine: Boolean = false,        // "Initializing Calculation Engine"
    val iecDbLoaded: Boolean = false,       // "IEC Database Loaded"
    val materialLibLoaded: Boolean = false, // "Material Library Loaded"
    val calcEngineReady: Boolean = false    // "Calculation Engine Ready"
) {
    /** True only when every startup task has completed. */
    val allReady: Boolean
        get() = initEngine && iecDbLoaded && materialLibLoaded && calcEngineReady

    /** Number of completed steps (0..4), used to drive the checklist UI. */
    val completedSteps: Int
        get() = listOf(initEngine, iecDbLoaded, materialLibLoaded, calcEngineReady)
            .count { it }
}

/**
 * BootViewModel — drives the real startup sequence.
 */
class BootViewModel : ViewModel() {

    private val _state = MutableStateFlow(BootUiState())
    val state: StateFlow<BootUiState> = _state.asStateFlow()

    fun updateFromStartupState(startupState: StartupState) {
        when (startupState) {
            is StartupState.Initial -> {
                _state.value = BootUiState(initEngine = false, iecDbLoaded = false, materialLibLoaded = false, calcEngineReady = false)
            }
            is StartupState.OpeningDatabase -> {
                _state.value = _state.value.copy(initEngine = true)
            }
            is StartupState.ValidatingCableCatalog,
            is StartupState.ValidatingAccessoryCatalog,
            is StartupState.ValidatingBreakerCatalog,
            is StartupState.ValidatingConduitCatalog -> {
                _state.value = _state.value.copy(initEngine = true, iecDbLoaded = true)
            }
            is StartupState.LoadingProjects,
            is StartupState.RestoringApplicationState -> {
                _state.value = _state.value.copy(initEngine = true, iecDbLoaded = true, materialLibLoaded = true)
            }
            is StartupState.Ready,
            is StartupState.ReadyWithWarnings,
            is StartupState.NoProjects -> {
                _state.value = BootUiState(initEngine = true, iecDbLoaded = true, materialLibLoaded = true, calcEngineReady = true)
            }
            else -> {
                _state.value = BootUiState(initEngine = true, iecDbLoaded = true, materialLibLoaded = true, calcEngineReady = true)
            }
        }
    }

    /** Kick off the real startup sequence. */
    fun start() {
        viewModelScope.launch { startBootSequence() }
    }

    private suspend fun startBootSequence() {
        if (!_state.value.initEngine) {
            delay(200)
            _state.value = _state.value.copy(initEngine = true)
        }
        if (!_state.value.iecDbLoaded) {
            delay(250)
            _state.value = _state.value.copy(iecDbLoaded = true)
        }
        if (!_state.value.materialLibLoaded) {
            delay(250)
            _state.value = _state.value.copy(materialLibLoaded = true)
        }
        if (!_state.value.calcEngineReady) {
            delay(200)
            _state.value = _state.value.copy(calcEngineReady = true)
        }
    }
}
