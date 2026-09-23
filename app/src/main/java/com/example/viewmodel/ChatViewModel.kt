package com.example.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.ApiConfig
import com.example.data.dto.ChatListItem
import com.example.data.mapper.toChatMessages
import com.example.data.network.NetworkClient
import com.example.data.repository.ChatRepository
import com.example.data.repository.ChatResult
import com.example.data.repository.ChatStreamEvent
import com.example.data.repository.ClearChatResult
import com.example.util.FileUploadUtils
import com.example.util.ImageProcessingUtils
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
    initialToken: String = BuildConfig.CHAT_API_TOKEN,
    initialBaseUrl: String = ApiConfig.DEFAULT_BASE_URL,
    customRepository: ChatRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            currentToken = initialToken,
            currentBaseUrl = ApiConfig.normalizeUrl(initialBaseUrl),
            isTokenConfigured = ApiConfig.isTokenConfigured(initialToken)
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<ChatUiEvent>()
    val uiEvent: SharedFlow<ChatUiEvent> = _uiEvent.asSharedFlow()

    private val _selectedFilesUris = MutableStateFlow<List<Uri>>(emptyList())
    val selectedFilesUris: StateFlow<List<Uri>> = _selectedFilesUris.asStateFlow()

    private var contentResolver: ContentResolver? = null

    // Cache de nomes de arquivos físicos já enviados com sucesso ao servidor (chave: Uri, valor: nome_arquivo)
    private val uploadedFileNamesCache = mutableMapOf<Uri, String>()

    @Volatile
    private var repository: ChatRepository = customRepository ?: createRepository(_uiState.value.currentBaseUrl)

    private fun createRepository(baseUrl: String): ChatRepository {
        return NetworkClient.createChatRepository(
            baseUrl = baseUrl,
            tokenProvider = { _uiState.value.currentToken }
        )
    }

    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    init {
        loadAgentes()
        if (_uiState.value.isTokenConfigured) {
            loadChats()
        }
    }

    fun setContentResolver(resolver: ContentResolver) {
        this.contentResolver = resolver
    }

    fun addSelectedFiles(uris: List<Uri>, explicitNames: List<String>? = null) {
        if (uris.isEmpty()) return
        val currentUris = _selectedFilesUris.value
        val newUris = uris.filter { it !in currentUris }
        if (newUris.isEmpty()) return

        val updatedUris = currentUris + newUris
        _selectedFilesUris.value = updatedUris

        val resolver = contentResolver
        val newItems = newUris.mapIndexed { index, uri ->
            val resolvedName = explicitNames?.getOrNull(index)?.takeIf { it.isNotBlank() }
                ?: resolver?.let { FileUploadUtils.getFileName(it, uri) }
                ?: (uri.lastPathSegment?.substringAfterLast('/') ?: "arquivo")
            SelectedFileItem(uri = uri, name = resolvedName)
        }

        _uiState.update { state ->
            state.copy(
                selectedFiles = state.selectedFiles + newItems,
                currentError = null
            )
        }
    }

    fun removeSelectedFile(uri: Uri) {
        _selectedFilesUris.update { list -> list.filter { it != uri } }
        uploadedFileNamesCache.remove(uri)
        _uiState.update { state ->
            state.copy(selectedFiles = state.selectedFiles.filter { it.uri != uri })
        }
    }

    fun clearSelectedFiles() {
        _selectedFilesUris.value = emptyList()
        uploadedFileNamesCache.clear()
        _uiState.update { state ->
            state.copy(
                selectedFiles = emptyList(),
                uploadingStatusMessage = null
            )
        }
    }

    fun setSelectedImage(uri: Uri?) {
        _uiState.update { it.copy(selectedImageUri = uri) }
    }

    fun clearSelectedImage() {
        _uiState.update { it.copy(selectedImageUri = null) }
    }

    fun setSelectedDocument(uri: Uri?) {
        if (uri == null) {
            clearSelectedDocument()
            return
        }
        val resolver = contentResolver
        val name = resolver?.let { FileUploadUtils.getFileName(it, uri, fallback = "Documento selecionado") }
            ?: (uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "Documento selecionado" } ?: "Documento selecionado")
        val sizeBytes = resolver?.let { FileUploadUtils.getFileSize(it, uri) } ?: -1L
        val formattedSize = if (sizeBytes > 0) FileUploadUtils.formatFileSize(sizeBytes) else null

        _uiState.update {
            it.copy(
                selectedDocumentUri = uri,
                selectedDocumentName = name.ifBlank { "Documento selecionado" },
                selectedDocumentSizeFormatted = formattedSize,
                currentError = null
            )
        }
    }

    fun clearSelectedDocument() {
        _uiState.update {
            it.copy(
                selectedDocumentUri = null,
                selectedDocumentName = null,
                selectedDocumentSizeFormatted = null
            )
        }
    }

    fun loadAgentes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAgentes = true, agentesError = null) }
            val result = repository.getAgentes()
            result.onSuccess { apiAgentes ->
                val hasDefaultSkill = apiAgentes.any { it.id == "default" }
                val consolidatedList = if (hasDefaultSkill) {
                    apiAgentes
                } else {
                    listOf(DEFAULT_AGENTE) + apiAgentes
                }

                _uiState.update { current ->
                    val updatedSkillId = if (current.selectedSkillId == "default") {
                        consolidatedList.firstOrNull { it.id == "default" }?.id ?: "default"
                    } else if (consolidatedList.any { it.id == current.selectedSkillId }) {
                        current.selectedSkillId
                    } else {
                        "default"
                    }

                    val updatedToolId = current.selectedToolId?.let { toolId ->
                        if (consolidatedList.any { it.id == toolId && (it.tipo.equals("tool", ignoreCase = true) || it.tipo.equals("action", ignoreCase = true)) }) {
                            toolId
                        } else {
                            null
                        }
                    }

                    current.copy(
                        agentes = consolidatedList,
                        selectedSkillId = updatedSkillId,
                        selectedToolId = updatedToolId,
                        isLoadingAgentes = false,
                        agentesError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        isLoadingAgentes = false,
                        agentesError = error.localizedMessage ?: "Não foi possível carregar a lista de agentes."
                    )
                }
            }
        }
    }

    fun retryLoadAgentes() {
        loadAgentes()
    }

    fun retryLoadChats() {
        loadChats()
    }

    fun loadChats(silent: Boolean = false) {
        if (!_uiState.value.isTokenConfigured) return

        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isLoadingChats = true, chatsError = null) }
            }
            val result = repository.getChats()
            result.onSuccess { chatList ->
                _uiState.update { current ->
                    current.copy(
                        chats = chatList,
                        isLoadingChats = false,
                        chatsError = null
                    )
                }
            }.onFailure { error ->
                if (!silent) {
                    _uiState.update { current ->
                        current.copy(
                            isLoadingChats = false,
                            chatsError = error.localizedMessage ?: "Não foi possível carregar o histórico de conversas."
                        )
                    }
                }
            }
        }
    }

    fun startNewChat() {
        _uiState.update { current ->
            current.copy(
                currentChatId = null,
                currentChatTitle = null,
                messages = emptyList(),
                currentError = null,
                sessionError = null,
                uploadingStatusMessage = null,
                selectedDocumentUri = null,
                selectedDocumentName = null,
                selectedDocumentSizeFormatted = null
            )
        }
    }

    fun openChat(chatId: String) {
        if (chatId.isBlank()) return

        val currentId = _uiState.value.currentChatId
        if (currentId == chatId && _uiState.value.messages.isNotEmpty()) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSession = true, sessionError = null) }
            val result = repository.getChatSession(chatId)
            result.onSuccess { session ->
                val mappedMessages = session.mensagens.toChatMessages()
                _uiState.update { current ->
                    current.copy(
                        currentChatId = session.id.ifBlank { chatId },
                        currentChatTitle = session.titulo,
                        messages = mappedMessages,
                        isLoadingSession = false,
                        sessionError = null
                    )
                }
            }.onFailure { error ->
                val errorMessage = error.localizedMessage ?: "Não foi possível carregar a conversa."
                _uiState.update { current ->
                    current.copy(
                        isLoadingSession = false,
                        sessionError = errorMessage
                    )
                }
                _uiEvent.emit(
                    ChatUiEvent.ShowSnackbar(
                        message = errorMessage,
                        actionLabel = "Tentar novamente",
                        onAction = { openChat(chatId) }
                    )
                )
            }
        }
    }

    fun onInputTextChanged(newText: String) {
        _uiState.update { it.copy(inputText = newText) }
    }

    fun clearCurrentError() {
        _uiState.update { it.copy(currentError = null) }
    }

    fun openPicker(target: AgentPickerTarget) {
        _uiState.update { it.copy(pickerTarget = target) }
    }

    fun closePicker() {
        _uiState.update { it.copy(pickerTarget = null) }
    }

    fun selectSkill(skillId: String) {
        _uiState.update { it.copy(selectedSkillId = skillId, pickerTarget = null) }
    }

    fun selectTool(toolId: String?) {
        _uiState.update { it.copy(selectedToolId = toolId, pickerTarget = null) }
    }

    fun updateBaseUrl(newBaseUrl: String) {
        val normalizedUrl = ApiConfig.normalizeUrl(newBaseUrl)
        if (normalizedUrl != _uiState.value.currentBaseUrl) {
            repository = createRepository(normalizedUrl)
            _uiState.update { it.copy(currentBaseUrl = normalizedUrl) }
            loadAgentes()
            if (_uiState.value.isTokenConfigured) {
                loadChats()
            }
        }
    }

    fun updateConfig(newToken: String, newBaseUrl: String) {
        val trimmedToken = newToken.trim()
        val normalizedUrl = ApiConfig.normalizeUrl(newBaseUrl)
        val isConfigured = ApiConfig.isTokenConfigured(trimmedToken)
        val urlChanged = normalizedUrl != _uiState.value.currentBaseUrl

        if (urlChanged) {
            repository = createRepository(normalizedUrl)
        }

        _uiState.update {
            it.copy(
                currentToken = trimmedToken,
                currentBaseUrl = normalizedUrl,
                isTokenConfigured = isConfigured,
                currentError = if (isConfigured) null else it.currentError
            )
        }

        loadAgentes()
        if (isConfigured) {
            loadChats()
        }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        val imageUriToProcess = _uiState.value.selectedImageUri
        val documentUriToProcess = _uiState.value.selectedDocumentUri
        val documentNameToProcess = _uiState.value.selectedDocumentName
        val urisToUpload = _selectedFilesUris.value

        // Permite envio de: texto sem mídia, imagem, documento, ou múltiplos anexos
        if ((text.isEmpty() && imageUriToProcess == null && documentUriToProcess == null && urisToUpload.isEmpty()) ||
            _uiState.value.isLoading ||
            !_uiState.value.isTokenConfigured
        ) {
            return
        }

        val skillId = _uiState.value.selectedSkillId.ifBlank { "default" }
        val toolId = _uiState.value.selectedToolId
        val sessionSnapshotId = _uiState.value.currentChatId
        val currentTime = getCurrentFormattedTime()
        val currentAttachments = (_uiState.value.selectedFiles.map { it.name } + listOfNotNull(documentNameToProcess)).distinct()

        val userMessage = ChatMessage(
            content = text,
            author = MessageAuthor.USER,
            timestamp = currentTime,
            attachments = currentAttachments,
            imageUri = imageUriToProcess,
            documentUri = documentUriToProcess,
            documentName = documentNameToProcess
        )

        val assistantMessageId = java.util.UUID.randomUUID().toString()
        val assistantMessage = ChatMessage(
            id = assistantMessageId,
            content = "",
            author = MessageAuthor.ASSISTANT,
            timestamp = currentTime,
            isStreaming = true
        )

        // Limpeza imediata do estado visual da imagem, documento e texto da interface
        // Adiciona imediatamente a mensagem do usuário e o balão vazio do assistente
        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMessage + assistantMessage,
                inputText = "",
                selectedImageUri = null,
                selectedDocumentUri = null,
                selectedDocumentName = null,
                selectedDocumentSizeFormatted = null,
                isLoading = true,
                uploadingStatusMessage = when {
                    documentUriToProcess != null -> "Preparando documento..."
                    imageUriToProcess != null -> "Processando imagem..."
                    urisToUpload.isNotEmpty() -> "Preparando envio de anexos..."
                    else -> null
                },
                currentError = null,
                lastSentUserMessage = text,
                lastSentImageUri = imageUriToProcess,
                lastSentDocumentUri = documentUriToProcess,
                lastSentDocumentName = documentNameToProcess,
                lastSentSkillId = skillId,
                lastSentToolId = toolId,
                lastSentChatId = sessionSnapshotId
            )
        }

        executeApiCallWithAttachments(
            assistantMessageId = assistantMessageId,
            messageText = text,
            skillId = skillId,
            toolId = toolId,
            sessionSnapshotId = sessionSnapshotId,
            uris = urisToUpload,
            imageUri = imageUriToProcess,
            documentUri = documentUriToProcess,
            documentName = documentNameToProcess
        )
    }

    fun retryLastMessage() {
        val lastMessage = _uiState.value.lastSentUserMessage
        val lastImageUri = _uiState.value.lastSentImageUri
        val lastDocumentUri = _uiState.value.lastSentDocumentUri
        val lastDocumentName = _uiState.value.lastSentDocumentName
        val urisToUpload = _selectedFilesUris.value

        if ((lastMessage.isNullOrBlank() && lastImageUri == null && lastDocumentUri == null && urisToUpload.isEmpty()) ||
            _uiState.value.isLoading ||
            !_uiState.value.isTokenConfigured
        ) {
            return
        }

        val skillId = _uiState.value.lastSentSkillId ?: _uiState.value.selectedSkillId.ifBlank { "default" }
        val toolId = _uiState.value.lastSentToolId ?: _uiState.value.selectedToolId
        val sessionSnapshotId = _uiState.value.lastSentChatId ?: _uiState.value.currentChatId
        val assistantMessageId = java.util.UUID.randomUUID().toString()
        val assistantMessage = ChatMessage(
            id = assistantMessageId,
            content = "",
            author = MessageAuthor.ASSISTANT,
            timestamp = getCurrentFormattedTime(),
            isStreaming = true
        )

        _uiState.update { state ->
            val cleanMessages = if (state.messages.isNotEmpty() && state.messages.last().isError) {
                state.messages.dropLast(1)
            } else {
                state.messages
            }
            state.copy(
                messages = cleanMessages + assistantMessage,
                isLoading = true,
                uploadingStatusMessage = when {
                    lastDocumentUri != null -> "Preparando documento..."
                    lastImageUri != null -> "Processando imagem..."
                    urisToUpload.isNotEmpty() -> "Reenviando anexos..."
                    else -> null
                },
                currentError = null
            )
        }

        executeApiCallWithAttachments(
            assistantMessageId = assistantMessageId,
            messageText = lastMessage ?: "",
            skillId = skillId,
            toolId = toolId,
            sessionSnapshotId = sessionSnapshotId,
            uris = urisToUpload,
            imageUri = lastImageUri,
            documentUri = lastDocumentUri,
            documentName = lastDocumentName
        )
    }

    private fun executeApiCallWithAttachments(
        assistantMessageId: String,
        messageText: String,
        skillId: String,
        toolId: String?,
        sessionSnapshotId: String?,
        uris: List<Uri>,
        imageUri: Uri? = null,
        documentUri: Uri? = null,
        documentName: String? = null
    ) {
        viewModelScope.launch {
            // Etapa 0 — Processamento seguro da imagem fora da Main Thread (se houver)
            var imagemBase64: String? = null
            if (imageUri != null) {
                val resolver = contentResolver
                if (resolver == null) {
                    val errorMsg = "ContentResolver não disponível para processar a imagem."
                    _uiState.update { state ->
                        val updated = state.messages.map { msg ->
                            if (msg.id == assistantMessageId) msg.copy(content = errorMsg, isError = true, canRetry = true, isStreaming = false) else msg
                        }
                        state.copy(messages = updated, isLoading = false, uploadingStatusMessage = null, currentError = errorMsg)
                    }
                    _uiEvent.emit(ChatUiEvent.ShowSnackbar(errorMsg))
                    return@launch
                }

                _uiState.update { it.copy(uploadingStatusMessage = "Otimizando imagem...") }

                imagemBase64 = try {
                    ImageProcessingUtils.uriToCompressedBase64(resolver, imageUri)
                } catch (e: Exception) {
                    null
                }

                if (imagemBase64 == null) {
                    val errorMsg = "Falha ao processar a imagem selecionada. Verifique o arquivo e tente novamente."
                    _uiState.update { state ->
                        val updated = state.messages.map { msg ->
                            if (msg.id == assistantMessageId) msg.copy(content = errorMsg, isError = true, canRetry = true, isStreaming = false) else msg
                        }
                        state.copy(messages = updated, isLoading = false, uploadingStatusMessage = null, currentError = errorMsg)
                    }
                    _uiEvent.emit(ChatUiEvent.ShowSnackbar(errorMsg))
                    return@launch
                }
            }

            val serverFileNames = mutableListOf<String>()

            // Etapa 0.1 — Validação e upload de documento fora da Main Thread (se houver)
            if (documentUri != null) {
                val resolver = contentResolver
                if (resolver == null) {
                    val errorMsg = "ContentResolver não disponível para leitura do documento."
                    _uiState.update { state ->
                        val updated = state.messages.map { msg ->
                            if (msg.id == assistantMessageId) msg.copy(content = errorMsg, isError = true, canRetry = true, isStreaming = false) else msg
                        }
                        state.copy(messages = updated, isLoading = false, uploadingStatusMessage = null, currentError = errorMsg)
                    }
                    _uiEvent.emit(ChatUiEvent.ShowSnackbar(errorMsg))
                    return@launch
                }

                // Validação de limite seguro de tamanho (20 MB)
                val docSize = FileUploadUtils.getFileSize(resolver, documentUri)
                if (docSize > 20 * 1024 * 1024L) {
                    val errorMsg = "O documento selecionado excede o limite máximo permitido de 20 MB."
                    _uiState.update { state ->
                        val updated = state.messages.map { msg ->
                            if (msg.id == assistantMessageId) msg.copy(content = errorMsg, isError = true, canRetry = true, isStreaming = false) else msg
                        }
                        state.copy(messages = updated, isLoading = false, uploadingStatusMessage = null, currentError = errorMsg)
                    }
                    _uiEvent.emit(ChatUiEvent.ShowSnackbar(errorMsg))
                    return@launch
                }

                val cachedDocName = uploadedFileNamesCache[documentUri]
                if (!cachedDocName.isNullOrBlank()) {
                    serverFileNames.add(cachedDocName)
                } else {
                    val resolvedDocName = documentName?.ifBlank { null }
                        ?: FileUploadUtils.getFileName(resolver, documentUri, fallback = "documento")
                    _uiState.update {
                        it.copy(uploadingStatusMessage = "Enviando documento $resolvedDocName...")
                    }

                    val filePart = try {
                        FileUploadUtils.uriToMultipartPart(resolver, documentUri)
                    } catch (e: Exception) {
                        val errorMsg = "Falha ao ler o documento $resolvedDocName: ${e.localizedMessage ?: "Erro desconhecido"}"
                        _uiState.update { state ->
                            val updated = state.messages.map { msg ->
                                if (msg.id == assistantMessageId) msg.copy(content = errorMsg, isError = true, canRetry = true, isStreaming = false) else msg
                            }
                            state.copy(messages = updated, isLoading = false, uploadingStatusMessage = null, currentError = errorMsg)
                        }
                        _uiEvent.emit(ChatUiEvent.ShowSnackbar(errorMsg))
                        return@launch
                    }

                    val uploadResult = repository.uploadArquivo(filePart)
                    if (uploadResult.isFailure) {
                        val errorMsg = uploadResult.exceptionOrNull()?.message ?: "Falha ao enviar o documento $resolvedDocName para o servidor."
                        _uiState.update { state ->
                            val updated = state.messages.map { msg ->
                                if (msg.id == assistantMessageId) msg.copy(content = errorMsg, isError = true, canRetry = true, isStreaming = false) else msg
                            }
                            state.copy(messages = updated, isLoading = false, uploadingStatusMessage = null, currentError = errorMsg)
                        }
                        _uiEvent.emit(ChatUiEvent.ShowSnackbar(errorMsg))
                        return@launch
                    }

                    val uploadedName = uploadResult.getOrNull()?.nome_arquivo
                    if (uploadedName.isNullOrBlank()) {
                        val errorMsg = "Servidor não retornou o nome do arquivo processado para $resolvedDocName."
                        _uiState.update { state ->
                            val updated = state.messages.map { msg ->
                                if (msg.id == assistantMessageId) msg.copy(content = errorMsg, isError = true, canRetry = true, isStreaming = false) else msg
                            }
                            state.copy(messages = updated, isLoading = false, uploadingStatusMessage = null, currentError = errorMsg)
                        }
                        _uiEvent.emit(ChatUiEvent.ShowSnackbar(errorMsg))
                        return@launch
                    }

                    uploadedFileNamesCache[documentUri] = uploadedName
                    serverFileNames.add(uploadedName)
                }
            }

            // Etapa A — Upload dos arquivos físicos para /upload
            if (uris.isNotEmpty()) {
                val resolver = contentResolver
                if (resolver == null) {
                    val errorMsg = "ContentResolver não disponível para leitura dos anexos."
                    _uiState.update { state ->
                        val updated = state.messages.map { msg ->
                            if (msg.id == assistantMessageId) msg.copy(content = errorMsg, isError = true, canRetry = true, isStreaming = false) else msg
                        }
                        state.copy(messages = updated, isLoading = false, uploadingStatusMessage = null, currentError = errorMsg)
                    }
                    _uiEvent.emit(ChatUiEvent.ShowSnackbar(errorMsg))
                    return@launch
                }

                for ((index, uri) in uris.withIndex()) {
                    val cachedName = uploadedFileNamesCache[uri]
                    if (!cachedName.isNullOrBlank()) {
                        serverFileNames.add(cachedName)
                        continue
                    }

                    val fileName = FileUploadUtils.getFileName(resolver, uri)
                    _uiState.update {
                        it.copy(uploadingStatusMessage = "Enviando $fileName (${index + 1}/${uris.size})...")
                    }

                    val filePart = try {
                        FileUploadUtils.uriToMultipartPart(resolver, uri)
                    } catch (e: Exception) {
                        val errorMsg = "Falha ao ler o arquivo $fileName: ${e.localizedMessage ?: "Erro desconhecido"}"
                        _uiState.update { state ->
                            val updated = state.messages.map { msg ->
                                if (msg.id == assistantMessageId) msg.copy(content = errorMsg, isError = true, canRetry = true, isStreaming = false) else msg
                            }
                            state.copy(messages = updated, isLoading = false, uploadingStatusMessage = null, currentError = errorMsg)
                        }
                        _uiEvent.emit(ChatUiEvent.ShowSnackbar(errorMsg))
                        return@launch
                    }

                    val uploadResult = repository.uploadArquivo(filePart)
                    if (uploadResult.isFailure) {
                        val errorMsg = uploadResult.exceptionOrNull()?.message ?: "Falha ao enviar o arquivo $fileName para o servidor."
                        _uiState.update { state ->
                            val updated = state.messages.map { msg ->
                                if (msg.id == assistantMessageId) msg.copy(content = errorMsg, isError = true, canRetry = true, isStreaming = false) else msg
                            }
                            state.copy(messages = updated, isLoading = false, uploadingStatusMessage = null, currentError = errorMsg)
                        }
                        _uiEvent.emit(ChatUiEvent.ShowSnackbar(errorMsg))
                        return@launch
                    }

                    val uploadedName = uploadResult.getOrNull()?.nome_arquivo
                    if (uploadedName.isNullOrBlank()) {
                        val errorMsg = "Servidor não retornou o nome do arquivo processado para $fileName."
                        _uiState.update { state ->
                            val updated = state.messages.map { msg ->
                                if (msg.id == assistantMessageId) msg.copy(content = errorMsg, isError = true, canRetry = true, isStreaming = false) else msg
                            }
                            state.copy(messages = updated, isLoading = false, uploadingStatusMessage = null, currentError = errorMsg)
                        }
                        _uiEvent.emit(ChatUiEvent.ShowSnackbar(errorMsg))
                        return@launch
                    }

                    uploadedFileNamesCache[uri] = uploadedName
                    serverFileNames.add(uploadedName)
                }
            }

            // Etapa B — Consumo do stream SSE da rota POST /chat
            _uiState.update { it.copy(uploadingStatusMessage = null) }

            repository.sendMessageStream(
                messageText = messageText,
                skillId = skillId,
                toolId = toolId,
                chatId = sessionSnapshotId,
                anexos = serverFileNames,
                imagemBase64 = imagemBase64
            ).collect { event ->
                when (event) {
                    is ChatStreamEvent.Chunk -> {
                        val currentSessionId = _uiState.value.currentChatId
                        val isSameSession = if (sessionSnapshotId == null) {
                            currentSessionId == null
                        } else {
                            currentSessionId == sessionSnapshotId
                        }

                        if (isSameSession) {
                            _uiState.update { state ->
                                val updatedMessages = state.messages.map { msg ->
                                    if (msg.id == assistantMessageId) {
                                        msg.copy(
                                            content = msg.content + event.text,
                                            isStreaming = true
                                        )
                                    } else {
                                        msg
                                    }
                                }
                                state.copy(messages = updatedMessages)
                            }
                        }
                    }
                    is ChatStreamEvent.Done -> {
                        val currentSessionId = _uiState.value.currentChatId
                        val isSameSession = if (sessionSnapshotId == null) {
                            currentSessionId == null
                        } else {
                            currentSessionId == sessionSnapshotId
                        }

                        val newChatId = event.chatId ?: sessionSnapshotId
                        val newTitle = event.titulo

                        if (isSameSession) {
                            _uiState.update { state ->
                                val updatedMessages = state.messages.map { msg ->
                                    if (msg.id == assistantMessageId) {
                                        msg.copy(isStreaming = false)
                                    } else {
                                        msg
                                    }
                                }
                                val updatedChats = if (!newChatId.isNullOrBlank()) {
                                    val existingIndex = state.chats.indexOfFirst { it.id == newChatId }
                                    val titleToUse = newTitle ?: state.currentChatTitle ?: "Conversa"
                                    if (existingIndex >= 0) {
                                        state.chats.mapIndexed { idx, item ->
                                            if (idx == existingIndex) item.copy(titulo = titleToUse) else item
                                        }
                                    } else {
                                        listOf(ChatListItem(id = newChatId, titulo = titleToUse)) + state.chats
                                    }
                                } else {
                                    state.chats
                                }

                                state.copy(
                                    messages = updatedMessages,
                                    currentChatId = newChatId ?: state.currentChatId,
                                    currentChatTitle = newTitle ?: state.currentChatTitle,
                                    chats = updatedChats,
                                    isLoading = false,
                                    uploadingStatusMessage = null,
                                    currentError = null
                                )
                            }
                            clearSelectedFiles()
                        } else {
                            _uiState.update { it.copy(isLoading = false, uploadingStatusMessage = null) }
                            clearSelectedFiles()
                        }

                        loadChats(silent = true)
                    }
                    is ChatStreamEvent.Error -> {
                        val currentSessionId = _uiState.value.currentChatId
                        val isSameSession = if (sessionSnapshotId == null) {
                            currentSessionId == null
                        } else {
                            currentSessionId == sessionSnapshotId
                        }

                        if (isSameSession) {
                            _uiState.update { state ->
                                val updatedMessages = state.messages.map { msg ->
                                    if (msg.id == assistantMessageId) {
                                        val finalContent = if (msg.content.isBlank()) {
                                            event.errorMessage
                                        } else {
                                            msg.content + "\n\n[Erro: ${event.errorMessage}]"
                                        }
                                        msg.copy(
                                            content = finalContent,
                                            isError = true,
                                            canRetry = true,
                                            isStreaming = false
                                        )
                                    } else {
                                        msg
                                    }
                                }
                                state.copy(
                                    messages = updatedMessages,
                                    isLoading = false,
                                    uploadingStatusMessage = null,
                                    currentError = event.errorMessage
                                )
                            }
                            _uiEvent.emit(ChatUiEvent.ShowSnackbar(event.errorMessage))
                        } else {
                            _uiState.update { it.copy(isLoading = false, uploadingStatusMessage = null) }
                        }
                    }
                }
            }
        }
    }

    fun clearChat() {
        clearChatHistory()
    }

    fun clearChatHistory() {
        if (_uiState.value.isClearing || _uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isClearing = true) }
            val result = repository.clearChat()
            when (result) {
                is ClearChatResult.Success -> {
                    _uiState.update {
                        it.copy(
                            messages = emptyList(),
                            currentChatId = null,
                            currentChatTitle = null,
                            isClearing = false,
                            currentError = null
                        )
                    }
                    clearSelectedFiles()
                    clearSelectedImage()
                    clearSelectedDocument()
                    loadChats(silent = true)
                    _uiEvent.emit(ChatUiEvent.ShowSnackbar("Histórico limpo com sucesso."))
                }
                is ClearChatResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isClearing = false,
                            currentError = result.errorMessage
                        )
                    }
                    _uiEvent.emit(ChatUiEvent.ShowSnackbar(result.errorMessage))
                }
            }
        }
    }

    private fun getCurrentFormattedTime(): String {
        return timeFormatter.format(Date())
    }
}
