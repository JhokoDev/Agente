package com.example.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Representa um item da listagem retornada por GET /chats.
 * Suporta ids numéricos ou em string e títulos nulos com fallback seguro.
 */
@JsonClass(generateAdapter = true)
data class ChatListItem(
    @Json(name = "id")
    val id: String = "",
    @Json(name = "titulo")
    val titulo: String = "Conversa",
    @Json(name = "atualizado_em")
    val atualizado_em: Double? = null
)

/**
 * Representa uma sessão completa retornada por GET /chats/{chat_id}.
 * Tolera campos nulos ou ausentes para evitar quebras de deserialização no Moshi.
 */
@JsonClass(generateAdapter = true)
data class ChatSession(
    @Json(name = "id")
    val id: String = "",
    @Json(name = "titulo")
    val titulo: String = "Conversa",
    @Json(name = "mensagens")
    val mensagens: List<SessionMessage> = emptyList()
)

/**
 * Representa uma mensagem dentro do histórico da sessão.
 * Suporta variações comuns de backends FastAPI ('content' e 'texto').
 */
@JsonClass(generateAdapter = true)
data class SessionMessage(
    @Json(name = "role")
    val role: String = "assistant",
    @Json(name = "content")
    val content: String = ""
)
