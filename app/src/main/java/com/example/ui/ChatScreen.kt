package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.ChatBubble
import com.example.ui.components.ChatInputBar
import com.example.ui.components.EmptyChatState
import com.example.ui.components.LoadingBubble
import com.example.ui.components.TokenConfigDialog
import com.example.ui.components.TokenWarningBanner
import com.example.viewmodel.ChatUiEvent
import com.example.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showTokenDialog by remember { mutableStateOf(false) }
    var showClearConfirmationDialog by remember { mutableStateOf(false) }

    // Observa eventos de disparo único do ViewModel (ex: falha ao limpar com retry)
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

    // Rola automaticamente para a mensagem mais recente quando novas mensagens chegam ou ao carregar
    val itemCount = uiState.messages.size + if (uiState.isLoading) 1 else 0
    LaunchedEffect(itemCount) {
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }

    if (showTokenDialog) {
        TokenConfigDialog(
            currentToken = uiState.currentToken,
            onSave = { newToken -> viewModel.updateToken(newToken) },
            onDismiss = { showTokenDialog = false }
        )
    }

    if (showClearConfirmationDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isClearing) {
                    showClearConfirmationDialog = false
                }
            },
            title = {
                Text(text = stringResource(R.string.clear_chat_title))
            },
            text = {
                Text(text = stringResource(R.string.clear_chat_confirmation))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmationDialog = false
                        viewModel.clearChat()
                    },
                    enabled = !uiState.isClearing,
                    modifier = Modifier.testTag("confirm_clear_button")
                ) {
                    Text(text = stringResource(R.string.clear_chat_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmationDialog = false },
                    modifier = Modifier.testTag("cancel_clear_button")
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.chat_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            val dotColor = when {
                                uiState.isClearing || uiState.isLoading -> MaterialTheme.colorScheme.primary
                                uiState.isTokenConfigured -> Color(0xFF10B981) // Verde esmeralda conectado
                                else -> MaterialTheme.colorScheme.error
                            }
                            val statusText = when {
                                uiState.isClearing -> stringResource(R.string.status_clearing)
                                uiState.isLoading -> stringResource(R.string.status_thinking)
                                uiState.isTokenConfigured -> stringResource(R.string.status_connected)
                                else -> stringResource(R.string.status_no_token)
                            }

                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showTokenDialog = true },
                        enabled = !uiState.isClearing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = stringResource(R.string.token_config_title),
                            tint = if (uiState.isTokenConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }

                    if (uiState.isClearing) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
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
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            ChatInputBar(
                text = uiState.inputText,
                onTextChanged = viewModel::onInputTextChanged,
                onSend = viewModel::sendMessage,
                isEnabled = !uiState.isLoading && !uiState.isClearing,
                isTokenConfigured = uiState.isTokenConfigured,
                modifier = Modifier.imePadding()
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!uiState.isTokenConfigured) {
                TokenWarningBanner(
                    onConfigureClick = { showTokenDialog = true }
                )
            }

            if (uiState.messages.isEmpty() && !uiState.isLoading) {
                EmptyChatState(
                    onPromptSelected = { prompt ->
                        viewModel.onInputTextChanged(prompt)
                        if (uiState.isTokenConfigured) {
                            viewModel.sendMessage()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
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
