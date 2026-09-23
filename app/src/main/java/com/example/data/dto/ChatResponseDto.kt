package com.example.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ChatResponseDto(
    @Json(name = "resposta_ia")
    val respostaIa: String? = null,
    @Json(name = "chat_id")
    val chatId: String? = null,
    @Json(name = "titulo")
    val titulo: String? = null
)

typealias ChatResponse = ChatResponseDto
