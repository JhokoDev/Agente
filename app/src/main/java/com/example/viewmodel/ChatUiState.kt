package com.example.viewmodel

import android.net.Uri
import com.example.data.ApiConfig
import com.example.data.dto.AgenteResponse
import com.example.data.dto.ChatListItem
import java.util.UUID

val DEFAULT_AGENTE = AgenteResponse(
    id = "default",
    nome = "Padrão",
    icone = "🤖",
    tipo = "padrao"
)

enum class AgentPickerTarget {
    SKILL,
    TOOL
}

enum class MessageAuthor {
    USER,
    ASSISTANT
}

data class SelectedFileItem(
    val uri: Uri,
    val name: String,
    val sizeFormatted: String = ""
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val author: MessageAuthor,
    val timestamp: String = "",
    val isError: Boolean = false,
    val canRetry: Boolean = false,
    val attachments: List<String> = emptyList(),
    val imageUri: Uri? = null,
    val documentUri: Uri? = null,
    val documentName: String? = null,
    val isStreaming: Boolean = false
)

sealed interface ChatUiEvent {
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null
    ) : ChatUiEvent
}

data class ChatUiState(
    val currentChatId: String? = null,
    val currentChatTitle: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val chats: List<ChatListItem> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isLoadingChats: Boolean = false,
    val isLoadingSession: Boolean = false,
    val isClearing: Boolean = false,
    val currentError: String? = null,
    val chatsError: String? = null,
    val sessionError: String? = null,
    val lastSentUserMessage: String? = null,
    val lastSentSkillId: String? = null,
    val lastSentToolId: String? = null,
    val lastSentChatId: String? = null,
    val currentToken: String = "",
    val currentBaseUrl: String = ApiConfig.DEFAULT_BASE_URL,
    val isTokenConfigured: Boolean = false,
    val agentes: List<AgenteResponse> = listOf(DEFAULT_AGENTE),
    val selectedSkillId: String = "default",
    val selectedToolId: String? = null,
    val pickerTarget: AgentPickerTarget? = null,
    val isLoadingAgentes: Boolean = false,
    val agentesError: String? = null,
    val selectedFiles: List<SelectedFileItem> = emptyList(),
    val uploadingStatusMessage: String? = null,
    val selectedImageUri: Uri? = null,
    val lastSentImageUri: Uri? = null,
    val selectedDocumentUri: Uri? = null,
    val selectedDocumentName: String? = null,
    val selectedDocumentSizeFormatted: String? = null,
    val lastSentDocumentUri: Uri? = null,
    val lastSentDocumentName: String? = null
) {
    val selectedSkill: AgenteResponse
        get() = agentes.firstOrNull { it.id == selectedSkillId } ?: DEFAULT_AGENTE

    val selectedTool: AgenteResponse?
        get() = selectedToolId?.let { id ->
            agentes.firstOrNull { it.id == id && (it.tipo.equals("tool", ignoreCase = true) || it.tipo.equals("action", ignoreCase = true)) }
        }

    val isBottomSheetVisible: Boolean
        get() = pickerTarget != null
}
