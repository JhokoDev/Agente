package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.ApiConfig
import com.example.data.network.NetworkClient
import com.example.data.repository.ChatRepository
import com.example.data.repository.ChatResult
import com.example.data.repository.ClearChatResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatViewModel(
    initialToken: String = BuildConfig.CHAT_API_TOKEN
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            currentToken = initialToken,
            isTokenConfigured = ApiConfig.isTokenConfigured(initialToken)
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<ChatUiEvent>()
    val uiEvent: SharedFlow<ChatUiEvent> = _uiEvent.asSharedFlow()

    private val repository: ChatRepository by lazy {
        val apiService = NetworkClient.createChatApiService(
            tokenProvider = { _uiState.value.currentToken }
        )
        ChatRepository(apiService)
    }

    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun onInputTextChanged(newText: String) {
        _uiState.update { it.copy(inputText = newText) }
    }

    fun updateToken(newToken: String) {
        val trimmed = newToken.trim()
        val isConfigured = ApiConfig.isTokenConfigured(trimmed)
        _uiState.update {
            it.copy(
                currentToken = trimmed,
                isTokenConfigured = isConfigured,
                currentError = if (isConfigured) null else it.currentError
            )
        }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isLoading || !_uiState.value.isTokenConfigured) {
            return
        }

        val currentTime = getCurrentFormattedTime()
        val userMessage = ChatMessage(
            content = text,
            author = MessageAuthor.USER,
            timestamp = currentTime
        )

        // Adiciona imediatamente a mensagem do usuário, limpa o campo e inicia o carregamento
        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMessage,
                inputText = "",
                isLoading = true,
                currentError = null,
                lastSentUserMessage = text
            )
        }

        executeApiCall(text)
    }

    fun retryLastMessage() {
        val lastMessage = _uiState.value.lastSentUserMessage
        if (lastMessage.isNullOrBlank() || _uiState.value.isLoading || !_uiState.value.isTokenConfigured) {
            return
        }

        _uiState.update { state ->
            state.copy(
                isLoading = true,
                currentError = null
            )
        }

        executeApiCall(lastMessage)
    }

    private fun executeApiCall(messageText: String) {
        viewModelScope.launch {
            when (val result = repository.sendMessage(messageText)) {
                is ChatResult.Success -> {
                    val replyMessage = ChatMessage(
                        content = result.reply,
                        author = MessageAuthor.ASSISTANT,
                        timestamp = getCurrentFormattedTime()
                    )
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages + replyMessage,
                            isLoading = false,
                            currentError = null
                        )
                    }
                }
                is ChatResult.Error -> {
                    val errorMessage = ChatMessage(
                        content = result.errorMessage,
                        author = MessageAuthor.ASSISTANT,
                        timestamp = getCurrentFormattedTime(),
                        isError = true,
                        canRetry = true
                    )
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages + errorMessage,
                            isLoading = false,
                            currentError = result.errorMessage
                        )
                    }
                }
            }
        }
    }

    fun clearChat() {
        if (_uiState.value.isClearing) return

        // Limpa imediatamente o histórico local na interface
        _uiState.update {
            it.copy(
                messages = emptyList(),
                isClearing = true,
                currentError = null,
                lastSentUserMessage = null
            )
        }

        performRemoteClear()
    }

    fun retryClearRemote() {
        if (_uiState.value.isClearing) return

        _uiState.update { it.copy(isClearing = true) }
        performRemoteClear()
    }

    private fun performRemoteClear() {
        viewModelScope.launch {
            when (repository.clearChat()) {
                is ClearChatResult.Success -> {
                    _uiState.update { it.copy(isClearing = false) }
                }
                is ClearChatResult.Error -> {
                    _uiState.update { it.copy(isClearing = false) }
                    _uiEvent.emit(
                        ChatUiEvent.ShowSnackbar(
                            message = "Não foi possível limpar o histórico no servidor.",
                            actionLabel = "Tentar novamente",
                            onAction = { retryClearRemote() }
                        )
                    )
                }
            }
        }
    }

    private fun getCurrentFormattedTime(): String {
        return timeFormatter.format(Date())
    }
}
