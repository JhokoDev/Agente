package com.example.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ChatRequest(
    @Json(name = "texto")
    val texto: String,
    @Json(name = "skill_id")
    val skill_id: String = "default",
    @Json(name = "tool_id")
    val tool_id: String? = null,
    @Json(name = "chat_id")
    val chat_id: String? = null,
    @Json(name = "imagem_base64")
    val imagem_base64: String? = null,
    @Json(name = "anexos")
    val anexos: List<String>? = emptyList()
)

typealias ChatRequestDto = ChatRequest

@JsonClass(generateAdapter = true)
data class UploadResponse(
    @Json(name = "nome_arquivo")
    val nome_arquivo: String,
    @Json(name = "mensagem")
    val mensagem: String = ""
)
