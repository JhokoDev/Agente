package com.example.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AgenteResponse(
    @Json(name = "id")
    val id: String,
    @Json(name = "nome")
    val nome: String,
    @Json(name = "icone")
    val icone: String,
    @Json(name = "tipo")
    val tipo: String
)
