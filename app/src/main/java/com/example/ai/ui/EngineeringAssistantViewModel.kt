package com.example.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.context.EngineeringContext
import com.example.ai.service.AssistantMessage
import com.example.ai.service.AssistantResponse
import com.example.ai.service.EngineeringAiService
import com.example.ai.service.FirebaseAiServiceImpl
import com.example.ai.service.MessageRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Entry points for invoking the VoltAtlas Engineering AI Assistant.
 */
enum class AssistantEntryPoint(val title: String) {
    DASHBOARD_ACTION("Dashboard Overview"),
    TOP_BAR_ICON("Assistant"),
    CONTEXTUAL_CIRCUIT("Circuit Analysis"),
    DIAGNOSE_FAILURE("Diagnostics"),
    OVERFLOW_MENU("Menu Action")
}

/**
 * Comprehensive lifecycle state machine for the Assistant container.
 */
enum class AssistantState {
    CLOSED,
    OPENING,
    COMPACT,
    EXPANDED,
    COLLECTING_INPUTS,
    CALCULATING,
    SHOWING_RESULT,
    SHOWING_ALTERNATIVES,
    PROPOSAL_REVIEW,
    APPLYING,
    APPLIED,
    ERROR,
    CLOSING
}

/**
 * UI State exposed to the Compose container.
 */
data class AssistantUiState(
    val state: AssistantState = AssistantState.CLOSED,
    val entryPoint: AssistantEntryPoint = AssistantEntryPoint.TOP_BAR_ICON,
    val context: EngineeringContext = EngineeringContext(),
    val messages: List<AssistantMessage> = emptyList(),
    val typedCards: List<AssistantCardUiModel> = emptyList(),
    val inputText: String = "",
    val isThinking: Boolean = false,
    val errorMessage: String? = null
) {
    val isOpen: Boolean
        get() = state != AssistantState.CLOSED && state != AssistantState.CLOSING

    val isExpanded: Boolean
        get() = state == AssistantState.EXPANDED || 
                state == AssistantState.PROPOSAL_REVIEW || 
                state == AssistantState.SHOWING_ALTERNATIVES
}

/**
 * ViewModel managing Assistant container state, conversations, and back-gesture consumption.
 */
class EngineeringAssistantViewModel(
    private val aiService: EngineeringAiService = FirebaseAiServiceImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    fun open(entryPoint: AssistantEntryPoint, context: EngineeringContext) {
        val initialGreeting = when (entryPoint) {
            AssistantEntryPoint.CONTEXTUAL_CIRCUIT -> 
                "Inspecting circuit **${context.activeEntityName ?: context.activeEntityId ?: "selected"}**. How can I help optimize or verify this feeder?"
            AssistantEntryPoint.DIAGNOSE_FAILURE ->
                "Reviewing active project compliance issues. Would you like me to analyze potential causes or recommend remediation?"
            AssistantEntryPoint.DASHBOARD_ACTION ->
                "Hello! I am your VoltAtlas Engineering Assistant. Ask me about cable sizing, transformer capacity, or voltage drop across **${context.projectName}**."
            else ->
                "Hello! How can I assist with your electrical engineering calculations today?"
        }

        _uiState.update { current ->
            current.copy(
                state = AssistantState.COMPACT,
                entryPoint = entryPoint,
                context = context,
                messages = if (current.messages.isEmpty()) {
                    listOf(AssistantMessage(role = MessageRole.ASSISTANT, text = initialGreeting))
                } else current.messages,
                errorMessage = null
            )
        }
    }

    fun close() {
        _uiState.update { it.copy(state = AssistantState.CLOSED) }
    }

    fun toggleExpand() {
        _uiState.update { current ->
            if (current.isExpanded) {
                current.copy(state = AssistantState.COMPACT)
            } else {
                current.copy(state = AssistantState.EXPANDED)
            }
        }
    }

    fun setInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        val userMsg = AssistantMessage(role = MessageRole.USER, text = trimmed)
        _uiState.update { current ->
            current.copy(
                messages = current.messages + userMsg,
                inputText = "",
                isThinking = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val response = aiService.sendUserMessage(
                context = _uiState.value.context,
                conversationHistory = _uiState.value.messages,
                userText = trimmed
            )

            when (response) {
                is AssistantResponse.Text -> {
                    val reply = AssistantMessage(role = MessageRole.ASSISTANT, text = response.content)
                    _uiState.update { current ->
                        current.copy(
                            messages = current.messages + reply,
                            isThinking = false
                        )
                    }
                }
                is AssistantResponse.Error -> {
                    _uiState.update { current ->
                        current.copy(
                            isThinking = false,
                            errorMessage = response.message
                        )
                    }
                }
            }
        }
    }

    /**
     * Predictive Back Tier-0 consumer.
     * Returns true if back gesture was consumed by the assistant; false if assistant was closed.
     */
    fun onBackHandled(): Boolean {
        val current = _uiState.value
        if (!current.isOpen) return false

        if (current.isExpanded) {
            // Collapse to compact first
            _uiState.update { it.copy(state = AssistantState.COMPACT) }
            return true
        } else {
            // Dismiss assistant
            close()
            return true
        }
    }
}
