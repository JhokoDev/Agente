package com.example.data.network

import com.example.data.dto.AgenteResponse
import com.example.data.dto.ChatListItem
import com.example.data.dto.ChatRequest
import com.example.data.dto.ChatResponseDto
import com.example.data.dto.ChatSession
import com.example.data.dto.UploadResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Interface Retrofit para os endpoints da API de Chat, Agentes, Sessões e Upload de Arquivos.
 */
interface ChatApiService {

    @GET("agentes")
    suspend fun getAgentes(): Response<List<AgenteResponse>>

    @POST("chat")
    suspend fun sendChatMessage(
        @Body request: ChatRequest
    ): Response<ChatResponseDto>

    @GET("chats")
    suspend fun getChats(): Response<List<ChatListItem>>

    @GET("chats/{chat_id}")
    suspend fun getChatSession(
        @Path("chat_id") chatId: String
    ): Response<ChatSession>

    @DELETE("chat")
    suspend fun clearChat(): Response<Unit>

    @Multipart
    @POST("upload")
    suspend fun uploadArquivo(
        @Part file: MultipartBody.Part
    ): Response<UploadResponse>
}
