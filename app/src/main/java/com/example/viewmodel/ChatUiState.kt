package com.example.viewmodel

import java.util.UUID

enum class MessageAuthor {
    USER,
    ASSISTANT
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val author: MessageAuthor,
    val timestamp: String,
    val isError: Boolean = false,
    val canRetry: Boolean = false
)

sealed interface ChatUiEvent {
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null
    ) : ChatUiEvent
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isClearing: Boolean = false,
    val currentError: String? = null,
    val lastSentUserMessage: String? = null,
    val currentToken: String = "",
    val isTokenConfigured: Boolean = false
)
