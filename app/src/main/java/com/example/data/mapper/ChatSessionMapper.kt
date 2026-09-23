package com.example.data.mapper

import com.example.data.dto.SessionMessage
import com.example.viewmodel.ChatMessage
import com.example.viewmodel.MessageAuthor
import java.util.UUID

/**
 * Mapeia mensagens do backend para o modelo visual [ChatMessage] da interface.
 * Trata 'user' como [MessageAuthor.USER] e qualquer outro (incluindo 'assistant') como [MessageAuthor.ASSISTANT].
 */
fun SessionMessage.toChatMessage(): ChatMessage {
    val author = if (role.equals("user", ignoreCase = true)) {
        MessageAuthor.USER
    } else {
        MessageAuthor.ASSISTANT
    }
    return ChatMessage(
        id = UUID.randomUUID().toString(),
        content = content,
        author = author,
        timestamp = ""
    )
}

fun List<SessionMessage>.toChatMessages(): List<ChatMessage> {
    return map { it.toChatMessage() }
}
