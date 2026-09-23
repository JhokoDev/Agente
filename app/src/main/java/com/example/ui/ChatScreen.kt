package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.AgentAvatar
import com.example.ui.components.AgentPickerBottomSheet
import com.example.ui.components.ChatBubble
import com.example.ui.components.ChatDrawerContent
import com.example.ui.components.ChatInputBar
import com.example.ui.components.EmptyChatState
import com.example.ui.components.LoadingBubble
import com.example.ui.components.TokenConfigDialog
import com.example.ui.components.TokenWarningBanner
import com.example.viewmodel.AgentPickerTarget
import com.example.viewmodel.ChatUiEvent
import com.example.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    var showTokenDialog by remember { mutableStateOf(false) }
    var showClearConfirmationDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    LaunchedEffect(context) {
        viewModel.setContentResolver(context.contentResolver)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addSelectedFiles(uris)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.setSelectedImage(uri)
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.setSelectedDocument(uri)
        }
    }

    // Observa eventos de disparo único do ViewModel (ex: falha ao limpar com retry ou carregar sessão)
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is ChatUiEvent.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = event.actionLabel,
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        event.onAction?.invoke()
                    }
                }
            }
        }
    }

    // Rola automaticamente para a mensagem mais recente quando novas mensagens chegam ou sessão é carregada
    LaunchedEffect(uiState.currentChatId, uiState.messages.size, uiState.isLoading) {
        val count = uiState.messages.size + if (uiState.isLoading) 1 else 0
        if (count > 0) {
            listState.animateScrollToItem(count - 1)
        }
    }

    if (showTokenDialog) {
        TokenConfigDialog(
            currentToken = uiState.currentToken,
            currentBaseUrl = uiState.currentBaseUrl,
            onSave = { newToken, newBaseUrl -> viewModel.updateConfig(newToken, newBaseUrl) },
            onDismiss = { showTokenDialog = false }
        )
    }

    // ModalBottomSheet contextual única para Skill e Tool
    val pickerTarget = uiState.pickerTarget
    if (pickerTarget != null) {
        AgentPickerBottomSheet(
            target = pickerTarget,
            agentes = uiState.agentes,
            selectedSkillId = uiState.selectedSkillId,
            selectedToolId = uiState.selectedToolId,
            isLoading = uiState.isLoadingAgentes,
            errorMessage = uiState.agentesError,
            onSelectSkill = viewModel::selectSkill,
            onSelectTool = viewModel::selectTool,
            onRetryLoad = viewModel::retryLoadAgentes,
            onDismiss = viewModel::closePicker
        )
    }

    if (showClearConfirmationDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isClearing) {
                    showClearConfirmationDialog = false
                }
            },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = stringResource(R.string.clear_chat_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.clear_chat_confirmation),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmationDialog = false
                        viewModel.clearChat()
                    },
                    enabled = !uiState.isClearing,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_clear_button")
                ) {
                    Text(text = stringResource(R.string.clear_chat_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmationDialog = false },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("cancel_clear_button")
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                ChatDrawerContent(
                    chats = uiState.chats,
                    currentChatId = uiState.currentChatId,
                    isLoadingChats = uiState.isLoadingChats,
                    chatsError = uiState.chatsError,
                    onNewChatClick = {
                        viewModel.startNewChat()
                        coroutineScope.launch { drawerState.close() }
                    },
                    onSelectChat = { chatId ->
                        viewModel.openChat(chatId)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onRefreshChats = {
                        viewModel.retryLoadChats()
                    }
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            },
                            modifier = Modifier.testTag("open_drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = stringResource(R.string.open_chat_history),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AgentAvatar(size = 34.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                val displayTitle = uiState.currentChatTitle ?: stringResource(R.string.chat_title)
                                Text(
                                    text = displayTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.2).sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 1.dp)
                                ) {
                                    val dotColor = when {
                                        uiState.isClearing || uiState.isLoading || uiState.isLoadingSession -> MaterialTheme.colorScheme.primary
                                        uiState.isTokenConfigured -> Color(0xFF10B981) // Verde esmeralda conectado
                                        else -> MaterialTheme.colorScheme.error
                                    }
                                    val statusText = when {
                                        uiState.isClearing -> stringResource(R.string.status_clearing)
                                        uiState.isLoadingSession -> stringResource(R.string.loading_session)
                                        uiState.isLoading -> stringResource(R.string.status_thinking)
                                        uiState.isTokenConfigured -> stringResource(R.string.status_connected)
                                        else -> stringResource(R.string.status_no_token)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showTokenDialog = true },
                            enabled = !uiState.isClearing
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = stringResource(R.string.token_config_title),
                                tint = if (uiState.isTokenConfigured) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                            )
                        }

                        if (uiState.isClearing) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { showClearConfirmationDialog = true },
                                enabled = !uiState.isClearing,
                                modifier = Modifier.testTag("clear_chat_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.clear_chat),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    thickness = 0.8.dp
                )
            },
            bottomBar = {
                ChatInputBar(
                    text = uiState.inputText,
                    onTextChanged = viewModel::onInputTextChanged,
                    onSend = viewModel::sendMessage,
                    isEnabled = !uiState.isLoading && !uiState.isClearing && !uiState.isLoadingSession,
                    isTokenConfigured = uiState.isTokenConfigured,
                    isLoading = uiState.isLoading,
                    selectedSkillId = uiState.selectedSkillId,
                    selectedSkillName = uiState.selectedSkill.nome,
                    selectedSkillIcon = uiState.selectedSkill.icone,
                    selectedToolId = uiState.selectedToolId,
                    selectedToolName = uiState.selectedTool?.nome,
                    selectedToolIcon = uiState.selectedTool?.icone,
                    selectedFiles = uiState.selectedFiles,
                    selectedImageUri = uiState.selectedImageUri,
                    selectedDocumentUri = uiState.selectedDocumentUri,
                    selectedDocumentName = uiState.selectedDocumentName,
                    selectedDocumentSizeFormatted = uiState.selectedDocumentSizeFormatted,
                    uploadStatusMessage = uiState.uploadingStatusMessage,
                    onOpenSkillPicker = { viewModel.openPicker(AgentPickerTarget.SKILL) },
                    onOpenToolPicker = { viewModel.openPicker(AgentPickerTarget.TOOL) },
                    onResetSkill = { viewModel.selectSkill("default") },
                    onClearTool = { viewModel.selectTool(null) },
                    onAttachClick = { filePickerLauncher.launch("*/*") },
                    onRemoveSelectedFile = viewModel::removeSelectedFile,
                    onSelectImageClick = {
                        photoPickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    onRemoveSelectedImage = viewModel::clearSelectedImage,
                    onSelectDocumentClick = {
                        documentPickerLauncher.launch(
                            arrayOf(
                                "application/pdf",
                                "application/msword",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-powerpoint",
                                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                "text/plain",
                                "text/csv",
                                "application/json",
                                "*/*"
                            )
                        )
                    },
                    onRemoveSelectedDocument = viewModel::clearSelectedDocument,
                    modifier = Modifier.imePadding()
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (uiState.isLoadingSession) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (!uiState.isTokenConfigured) {
                    TokenWarningBanner(
                        onConfigureClick = { showTokenDialog = true }
                    )
                }

                AnimatedContent(
                    targetState = uiState.messages.isEmpty() && !uiState.isLoading,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                    },
                    label = "chat_content_transition",
                    modifier = Modifier.weight(1f)
                ) { isEmpty ->
                    if (isEmpty) {
                        EmptyChatState(
                            onPromptSelected = { prompt ->
                                viewModel.onInputTextChanged(prompt)
                                if (uiState.isTokenConfigured) {
                                    viewModel.sendMessage()
                                }
                            }
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("messages_list")
                        ) {
                            items(
                                items = uiState.messages,
                                key = { it.id }
                            ) { message ->
                                ChatBubble(
                                    message = message,
                                    onRetry = viewModel::retryLastMessage
                                )
                            }

                            if (uiState.isLoading) {
                                item(key = "loading_indicator") {
                                    LoadingBubble()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
