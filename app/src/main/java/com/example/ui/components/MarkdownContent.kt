package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CodeBlockBackgroundDark
import kotlinx.coroutines.delay

sealed interface MarkdownBlock {
    data class Code(val language: String, val code: String) : MarkdownBlock
    data class Header(val level: Int, val text: String) : MarkdownBlock
    data class Bullet(val bullet: String, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
}

/**
 * Componente que renderiza texto Markdown com suporte a blocos de código com botão de cópia,
 * cabeçalhos, listas, formatação inline e cursor de streaming pulsante suave.
 */
@Composable
fun MarkdownContent(
    content: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    val blocks = remember(content) { parseMarkdownBlocks(content) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEachIndexed { index, block ->
            val isLastBlock = index == blocks.lastIndex
            when (block) {
                is MarkdownBlock.Code -> {
                    CodeBlockView(
                        language = block.language,
                        code = block.code
                    )
                }
                is MarkdownBlock.Header -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = block.text,
                            style = style,
                            color = textColor
                        )
                        if (isStreaming && isLastBlock) {
                            StreamingCursor(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                is MarkdownBlock.Bullet -> {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = block.bullet,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = parseInlineMarkdown(block.text, textColor),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = textColor,
                                    lineHeight = 22.sp
                                )
                            )
                            if (isStreaming && isLastBlock) {
                                StreamingCursor(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                is MarkdownBlock.Quote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = parseInlineMarkdown(block.text, textColor),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = FontStyle.Italic,
                                color = textColor.copy(alpha = 0.85f),
                                lineHeight = 22.sp
                            )
                        )
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = parseInlineMarkdown(block.text, textColor),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = textColor,
                                lineHeight = 22.sp
                            )
                        )
                        if (isStreaming && isLastBlock) {
                            StreamingCursor(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Área visual própria para blocos de código com destaque, botão de cópia com feedback visual
 * e tipografia monoespaçada.
 */
@Composable
fun CodeBlockView(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2000)
            isCopied = false
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CodeBlockBackgroundDark,
        modifier = modifier
            .fillMaxWidth()
            .testTag("code_block")
    ) {
        Column {
            // Barra superior do cabeçalho do código
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Text(
                    text = language.ifBlank { "código" }.lowercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCopied) {
                        Text(
                            text = "Copiado!",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF4ADE80),
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(code))
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isCopied = true
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("copy_code_button")
                    ) {
                        Icon(
                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = if (isCopied) "Código copiado" else "Copiar código",
                            tint = if (isCopied) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Conteúdo do código com rolagem horizontal
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                )
            }
        }
    }
}

/**
 * Cursor animado suave para indicar streaming em andamento.
 */
@Composable
fun StreamingCursor(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "streaming_cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    Box(
        modifier = modifier
            .padding(start = 4.dp, bottom = 3.dp)
            .size(7.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

/**
 * Analisa o texto em blocos estruturados de Markdown.
 */
fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    if (text.isBlank()) return emptyList()

    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.lines()
    var inCodeBlock = false
    var currentCodeLang = ""
    val currentCodeLines = mutableListOf<String>()
    val currentParagraphLines = mutableListOf<String>()

    fun flushParagraph() {
        if (currentParagraphLines.isNotEmpty()) {
            val paragraphText = currentParagraphLines.joinToString("\n").trim()
            if (paragraphText.isNotEmpty()) {
                blocks.add(MarkdownBlock.Paragraph(paragraphText))
            }
            currentParagraphLines.clear()
        }
    }

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                // Fechando bloco de código
                blocks.add(
                    MarkdownBlock.Code(
                        language = currentCodeLang,
                        code = currentCodeLines.joinToString("\n")
                    )
                )
                currentCodeLines.clear()
                currentCodeLang = ""
                inCodeBlock = false
            } else {
                // Abrindo bloco de código
                flushParagraph()
                currentCodeLang = trimmed.removePrefix("```").trim()
                inCodeBlock = true
            }
            continue
        }

        if (inCodeBlock) {
            currentCodeLines.add(line)
            continue
        }

        // Fora de bloco de código: verificar títulos, bullets, citações
        when {
            trimmed.startsWith("### ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Header(3, trimmed.removePrefix("### ")))
            }
            trimmed.startsWith("## ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Header(2, trimmed.removePrefix("## ")))
            }
            trimmed.startsWith("# ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Header(1, trimmed.removePrefix("# ")))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                val bullet = if (trimmed.startsWith("- ")) "•" else "•"
                blocks.add(MarkdownBlock.Bullet(bullet, trimmed.substring(2)))
            }
            trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                flushParagraph()
                val dotIndex = trimmed.indexOf('.')
                val number = trimmed.substring(0, dotIndex + 1)
                val rest = trimmed.substring(dotIndex + 1).trim()
                blocks.add(MarkdownBlock.Bullet(number, rest))
            }
            trimmed.startsWith("> ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Quote(trimmed.removePrefix("> ")))
            }
            trimmed.isEmpty() -> {
                flushParagraph()
            }
            else -> {
                currentParagraphLines.add(line)
            }
        }
    }

    if (inCodeBlock) {
        // Bloco de código incompleto durante streaming
        blocks.add(
            MarkdownBlock.Code(
                language = currentCodeLang,
                code = currentCodeLines.joinToString("\n")
            )
        )
    } else {
        flushParagraph()
    }

    return blocks
}

/**
 * Formata inline markdown (negrito, itálico, inline code) para AnnotatedString.
 */
@Composable
fun parseInlineMarkdown(text: String, baseColor: Color): AnnotatedString {
    val inlineCodeBg = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
    val inlineCodeColor = MaterialTheme.colorScheme.primary

    return remember(text, baseColor, inlineCodeBg, inlineCodeColor) {
        buildAnnotatedString {
            var i = 0
            while (i < text.length) {
                // Inline Code: `code`
                if (text[i] == '`') {
                    val end = text.indexOf('`', i + 1)
                    if (end != -1) {
                        val code = text.substring(i + 1, end)
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = inlineCodeBg,
                                color = inlineCodeColor,
                                fontSize = 13.sp
                            )
                        )
                        append(" $code ")
                        pop()
                        i = end + 1
                        continue
                    }
                }

                // Bold: **text**
                if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        val boldText = text.substring(i + 2, end)
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(boldText)
                        pop()
                        i = end + 2
                        continue
                    }
                }

                // Italic: *text* or _text_
                if (text[i] == '*' || text[i] == '_') {
                    val char = text[i]
                    val end = text.indexOf(char, i + 1)
                    if (end != -1 && end > i + 1) {
                        val italicText = text.substring(i + 1, end)
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(italicText)
                        pop()
                        i = end + 1
                        continue
                    }
                }

                append(text[i])
                i++
            }
        }
    }
}
