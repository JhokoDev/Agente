package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.util.FileUploadUtils
import com.example.viewmodel.SelectedFileItem

@Composable
fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    isEnabled: Boolean,
    isTokenConfigured: Boolean,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    selectedSkillId: String = "default",
    selectedSkillName: String = "Geral",
    selectedSkillIcon: String = "🧠",
    selectedToolId: String? = null,
    selectedToolName: String? = null,
    selectedToolIcon: String? = null,
    selectedFiles: List<SelectedFileItem> = emptyList(),
    selectedImageUri: Uri? = null,
    selectedDocumentUri: Uri? = null,
    selectedDocumentName: String? = null,
    selectedDocumentSizeFormatted: String? = null,
    uploadStatusMessage: String? = null,
    onOpenSkillPicker: () -> Unit = {},
    onOpenToolPicker: () -> Unit = {},
    onResetSkill: () -> Unit = {},
    onClearTool: () -> Unit = {},
    onAttachClick: () -> Unit = {},
    onRemoveSelectedFile: (Uri) -> Unit = {},
    onSelectImageClick: () -> Unit = {},
    onRemoveSelectedImage: () -> Unit = {},
    onSelectDocumentClick: () -> Unit = {},
    onRemoveSelectedDocument: () -> Unit = {}
) {
    val canSend = isEnabled && isTokenConfigured && !isLoading && (text.isNotBlank() || selectedImageUri != null || selectedDocumentUri != null || selectedFiles.isNotEmpty())

    var isMenuExpanded by remember { mutableStateOf(false) }

    val isCustomSkillActive = selectedSkillId.isNotBlank() && selectedSkillId != "default"
    val isToolActive = !selectedToolId.isNullOrBlank() && !selectedToolName.isNullOrBlank()

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val sendButtonContainerColor by animateColorAsState(
        targetValue = if (canSend) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        },
        animationSpec = tween(200),
        label = "send_button_color"
    )

    val sendButtonContentColor by animateColorAsState(
        targetValue = if (canSend) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        },
        animationSpec = tween(200),
        label = "send_button_content_color"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // Indicador de progresso e mensagem de status do upload (se ativo)
            AnimatedVisibility(
                visible = !uploadStatusMessage.isNullOrBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uploadStatusMessage ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Indicadores de Skill e Tool ativas (aparecem como chips discretos e informativos acima do campo)
            AnimatedVisibility(
                visible = isCustomSkillActive || isToolActive,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isCustomSkillActive) {
                        ActiveConfigChip(
                            icon = selectedSkillIcon.ifBlank { "🧠" },
                            label = stringResource(R.string.active_skill_label, selectedSkillName),
                            contentDescription = "Especialista ativo: $selectedSkillName",
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            onChipClick = onOpenSkillPicker,
                            onRemoveClick = onResetSkill,
                            removeDescription = stringResource(R.string.remove_active_skill),
                            testTag = "active_skill_chip"
                        )
                    }

                    if (isToolActive && !selectedToolName.isNullOrBlank()) {
                        ActiveConfigChip(
                            icon = selectedToolIcon ?: "🛠️",
                            label = stringResource(R.string.active_tool_label, selectedToolName),
                            contentDescription = "Ferramenta ativa: $selectedToolName",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
                            onChipClick = onOpenToolPicker,
                            onRemoveClick = onClearTool,
                            removeDescription = stringResource(R.string.remove_active_tool),
                            testTag = "active_tool_chip"
                        )
                    }
                }
            }

            // Área de arquivos selecionados imediatamente acima da caixa de digitação
            AnimatedVisibility(
                visible = selectedFiles.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("selected_files_row")
                ) {
                    items(selectedFiles, key = { it.uri.toString() }) { fileItem ->
                        AttachmentChip(
                            file = fileItem,
                            enabled = !isLoading,
                            onRemove = { onRemoveSelectedFile(fileItem.uri) }
                        )
                    }
                }
            }

            // Área de pré-visualização (thumbnail) da imagem selecionada imediatamente acima da caixa de digitação
            AnimatedVisibility(
                visible = selectedImageUri != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (selectedImageUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .testTag("selected_image_preview_box")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "Pré-visualização da imagem selecionada",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("selected_image_thumbnail")
                            )
                        }

                        // Botão de remoção (X) sobre o canto da miniatura
                        Surface(
                            onClick = onRemoveSelectedImage,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = 2.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.TopStart)
                                .offset(x = 50.dp, y = (-6).dp)
                                .testTag("remove_selected_image_button")
                                .semantics { contentDescription = "Remover imagem selecionada" }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remover imagem",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Área de pré-visualização do documento selecionado imediatamente acima da caixa de digitação
            AnimatedVisibility(
                visible = selectedDocumentUri != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (selectedDocumentUri != null) {
                    val docName = selectedDocumentName?.ifBlank { "Documento selecionado" } ?: "Documento selecionado"
                    val docIcon = FileUploadUtils.getFileIcon(docName)
                    DocumentPreviewCard(
                        fileName = docName,
                        fileIcon = docIcon,
                        fileSizeFormatted = selectedDocumentSizeFormatted,
                        enabled = !isLoading,
                        onRemove = onRemoveSelectedDocument,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }
            }

            // Barra horizontal de digitação e ações: [ + ] [ Campo de mensagem ] [ Enviar ]
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)
            ) {
                // 1. Único botão de adição [+] com menu flutuante de ações
                val addActionDesc = stringResource(R.string.add_action_menu_description)

                Box(
                    modifier = Modifier
                        .padding(bottom = 2.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    Surface(
                        onClick = { isMenuExpanded = !isMenuExpanded },
                        enabled = isEnabled && !isLoading,
                        shape = CircleShape,
                        color = if (isMenuExpanded) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isMenuExpanded) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            }
                        ),
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("add_action_button")
                            .semantics { contentDescription = addActionDesc }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = addActionDesc,
                                tint = if (isMenuExpanded) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Menu flutuante de ações ancorado no botão +
                    MaterialTheme(
                        shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))
                    ) {
                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false },
                            modifier = Modifier
                                .widthIn(min = 210.dp)
                                .testTag("chat_actions_menu")
                        ) {
                            // Ação 1: Especialista
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.menu_item_specialist),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    onOpenSkillPicker()
                                },
                                modifier = Modifier
                                    .testTag("action_menu_specialist")
                                    .defaultMinSize(minHeight = 48.dp)
                            )

                            // Ação 2: Ferramenta
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.menu_item_tool),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    onOpenToolPicker()
                                },
                                modifier = Modifier
                                    .testTag("action_menu_tool")
                                    .defaultMinSize(minHeight = 48.dp)
                            )

                            // Ação 3: Anexar Imagem
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.menu_item_attach_image),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    onSelectImageClick()
                                },
                                modifier = Modifier
                                    .testTag("action_menu_attach_image")
                                    .defaultMinSize(minHeight = 48.dp)
                            )

                            // Ação 4: Documento
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.menu_item_document),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    onSelectDocumentClick()
                                },
                                modifier = Modifier
                                    .testTag("action_menu_document")
                                    .defaultMinSize(minHeight = 48.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Campo de texto de digitação
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(26.dp))
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            RoundedCornerShape(26.dp)
                        )
                ) {
                    TextField(
                        value = text,
                        onValueChange = onTextChanged,
                        placeholder = {
                            Text(
                                text = if (isTokenConfigured) {
                                    stringResource(R.string.input_placeholder)
                                } else {
                                    "Configure o token para enviar mensagens"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        },
                        enabled = isEnabled && !isLoading,
                        minLines = 1,
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = if (canSend) ImeAction.Send else ImeAction.Default
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (canSend) {
                                    onSend()
                                }
                            }
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chat_input_field")
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Botão de envio
                FilledIconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onSend()
                    },
                    enabled = canSend,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = sendButtonContainerColor,
                        contentColor = sendButtonContentColor,
                        disabledContainerColor = sendButtonContainerColor,
                        disabledContentColor = sendButtonContentColor
                    ),
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("send_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.send_description),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Chip para exibição compacta de Skill ou Tool ativa acima do campo de digitação.
 */
@Composable
private fun ActiveConfigChip(
    icon: String,
    label: String,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    onChipClick: () -> Unit,
    onRemoveClick: () -> Unit,
    removeDescription: String,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onChipClick,
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = icon,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(4.dp))
            Surface(
                onClick = onRemoveClick,
                shape = CircleShape,
                color = contentColor.copy(alpha = 0.12f),
                modifier = Modifier
                    .size(20.dp)
                    .semantics { this.contentDescription = removeDescription }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = removeDescription,
                        tint = contentColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Chip para exibição dos arquivos selecionados acima da barra de digitação.
 */
@Composable
private fun AttachmentChip(
    file: SelectedFileItem,
    enabled: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fileIcon = FileUploadUtils.getFileIcon(file.name)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .testTag("attachment_chip_${file.name}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = fileIcon,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = file.name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier
                    .size(24.dp)
                    .testTag("remove_attachment_${file.name}")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remover anexo ${file.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Componente visual para pré-visualização de documento selecionado com ícone de documento,
 * nome do arquivo, tamanho formatado e botão X para remoção individual.
 */
@Composable
private fun DocumentPreviewCard(
    fileName: String,
    fileIcon: String,
    fileSizeFormatted: String?,
    enabled: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier.testTag("selected_document_preview_card")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Text(
                text = fileIcon.ifBlank { "📄" },
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("selected_document_name")
                )
                if (!fileSizeFormatted.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = fileSizeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.testTag("selected_document_size")
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                onClick = onRemove,
                enabled = enabled,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("remove_selected_document_button")
                    .semantics { contentDescription = "Remover documento selecionado" }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remover documento selecionado",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
