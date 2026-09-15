package com.example.data.network

import com.example.data.dto.ChatRequestDto
import com.example.data.dto.ChatResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST

/**
 * Interface Retrofit para os endpoints da API de Chat.
 */
interface ChatApiService {

    @POST("chat")
    suspend fun sendChatMessage(
        @Body request: ChatRequestDto
    ): Response<ChatResponseDto>

    @DELETE("chat")
    suspend fun clearChat(): Response<Unit>
}
