package com.example.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Representa os eventos de dados (SSE) enviados pela rota POST /chat do FastAPI:
 * - Chunk de texto: data: {"chunk": "palavra..."}
 * - Finalização: data: {"done": true, "chat_id": "...", "titulo": "..."}
 * - Erro eventual: data: {"error": "mensagem..."}
 */
@JsonClass(generateAdapter = true)
data class SseChatEventDto(
    @Json(name = "chunk")
    val chunk: String? = null,
    @Json(name = "done")
    val done: Boolean? = null,
    @Json(name = "chat_id")
    val chatId: String? = null,
    @Json(name = "titulo")
    val titulo: String? = null,
    @Json(name = "error")
    val error: String? = null
)
