package com.example.data.repository

import com.example.data.dto.ChatRequestDto
import com.example.data.network.ChatApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

sealed interface ChatResult {
    data class Success(val reply: String) : ChatResult
    data class Error(val errorMessage: String, val isAuthError: Boolean = false) : ChatResult
}

sealed interface ClearChatResult {
    data object Success : ClearChatResult
    data class Error(val errorMessage: String, val isAuthError: Boolean = false) : ClearChatResult
}

class ChatRepository(
    private val apiService: ChatApiService
) {
    suspend fun sendMessage(messageText: String): ChatResult = withContext(Dispatchers.IO) {
        try {
            val response = apiService.sendChatMessage(ChatRequestDto(texto = messageText))

            if (response.isSuccessful) {
                val body = response.body()
                val reply = body?.respostaIa?.trim()
                if (!reply.isNullOrBlank()) {
                    ChatResult.Success(reply)
                } else {
                    ChatResult.Error("A API retornou uma resposta vazia.")
                }
            } else {
                val code = response.code()
                when (code) {
                    401, 403 -> {
                        ChatResult.Error(
                            errorMessage = "Erro de autenticação ($code): Token ausente ou inválido. Verifique sua chave CHAT_API_TOKEN no local.properties.",
                            isAuthError = true
                        )
                    }
                    404 -> ChatResult.Error("Endpoint /chat não encontrado no servidor ($code).")
                    422 -> ChatResult.Error("Erro de validação na requisição enviada à API ($code).")
                    in 500..599 -> ChatResult.Error("O servidor da API encontrou um erro interno (HTTP $code). Tente novamente mais tarde.")
                    else -> ChatResult.Error("Falha na requisição à API (HTTP $code: ${response.message()}).")
                }
            }
        } catch (e: SocketTimeoutException) {
            ChatResult.Error("Tempo limite esgotado ao aguardar resposta da API. Verifique sua conexão.")
        } catch (e: IOException) {
            ChatResult.Error("Falha de conexão com a API (${e.localizedMessage ?: "Erro de rede"}). Verifique se o servidor está ativo.")
        } catch (e: HttpException) {
            val code = e.code()
            if (code == 401 || code == 403) {
                ChatResult.Error("Erro de autenticação ($code): Token inválido ou expirado.", isAuthError = true)
            } else {
                ChatResult.Error("Erro HTTP $code: ${e.message()}")
            }
        } catch (e: Exception) {
            ChatResult.Error("Erro inesperado: ${e.localizedMessage ?: "Erro desconhecido"}")
        }
    }

    suspend fun clearChat(): ClearChatResult = withContext(Dispatchers.IO) {
        try {
            val response = apiService.clearChat()
            if (response.isSuccessful) {
                ClearChatResult.Success
            } else {
                val code = response.code()
                when (code) {
                    401, 403 -> {
                        ClearChatResult.Error(
                            errorMessage = "Erro de autenticação ($code): Token ausente ou inválido.",
                            isAuthError = true
                        )
                    }
                    404 -> ClearChatResult.Error("Endpoint de limpeza não encontrado no servidor ($code).")
                    in 500..599 -> ClearChatResult.Error("Erro no servidor ao limpar histórico (HTTP $code).")
                    else -> ClearChatResult.Error("Falha ao limpar histórico no servidor (HTTP $code).")
                }
            }
        } catch (e: SocketTimeoutException) {
            ClearChatResult.Error("Tempo limite esgotado ao limpar histórico no servidor.")
        } catch (e: IOException) {
            ClearChatResult.Error("Falha de conexão com a API ao limpar histórico.")
        } catch (e: HttpException) {
            val code = e.code()
            ClearChatResult.Error("Erro HTTP $code ao limpar histórico no servidor.", isAuthError = code == 401 || code == 403)
        } catch (e: Exception) {
            ClearChatResult.Error(e.localizedMessage ?: "Erro inesperado ao limpar histórico no servidor.")
        }
    }
}
