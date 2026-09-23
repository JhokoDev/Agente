package com.example.data.repository

import com.example.data.ApiConfig
import com.example.data.dto.AgenteResponse
import com.example.data.dto.ChatListItem
import com.example.data.dto.ChatRequest
import com.example.data.dto.ChatSession
import com.example.data.dto.SseChatEventDto
import com.example.data.dto.UploadResponse
import com.example.data.network.ChatApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

sealed interface ChatResult {
    data class Success(
        val reply: String,
        val chatId: String? = null,
        val titulo: String? = null
    ) : ChatResult

    data class Error(
        val errorMessage: String,
        val isAuthError: Boolean = false
    ) : ChatResult
}

sealed interface ChatStreamEvent {
    data class Chunk(val text: String) : ChatStreamEvent
    data class Done(val chatId: String? = null, val titulo: String? = null) : ChatStreamEvent
    data class Error(val errorMessage: String, val isAuthError: Boolean = false) : ChatStreamEvent
}

sealed interface ClearChatResult {
    data object Success : ClearChatResult
    data class Error(val errorMessage: String, val isAuthError: Boolean = false) : ClearChatResult
}

class ChatRepository(
    private val apiService: ChatApiService,
    private val okHttpClient: OkHttpClient? = null,
    private val baseUrl: String = ApiConfig.DEFAULT_BASE_URL,
    private val tokenProvider: (() -> String?)? = null,
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
) {
    private val sseAdapter by lazy { moshi.adapter(SseChatEventDto::class.java) }
    private val chatRequestAdapter by lazy { moshi.adapter(ChatRequest::class.java) }

    /**
     * Consome a rota SSE POST /chat usando EventSource (okhttp3-sse),
     * emitindo chunks de texto em tempo real em um Kotlin Flow.
     */
    fun sendMessageStream(
        messageText: String,
        skillId: String = "default",
        toolId: String? = null,
        chatId: String? = null,
        anexos: List<String>? = emptyList(),
        imagemBase64: String? = null
    ): Flow<ChatStreamEvent> {
        val client = okHttpClient
        if (client == null) {
            // Fallback para execução síncrona/Retrofit (útil para testes unitários com mocks)
            return flow {
                val result = sendMessage(
                    messageText = messageText,
                    skillId = skillId,
                    toolId = toolId,
                    chatId = chatId,
                    anexos = anexos,
                    imagemBase64 = imagemBase64
                )
                when (result) {
                    is ChatResult.Success -> {
                        emit(ChatStreamEvent.Chunk(result.reply))
                        emit(ChatStreamEvent.Done(chatId = result.chatId, titulo = result.titulo))
                    }
                    is ChatResult.Error -> {
                        emit(ChatStreamEvent.Error(result.errorMessage, result.isAuthError))
                    }
                }
            }.flowOn(Dispatchers.IO)
        }

        return callbackFlow {
            val requestDto = ChatRequest(
                texto = messageText,
                skill_id = skillId.ifBlank { "default" },
                tool_id = toolId,
                chat_id = chatId,
                anexos = anexos ?: emptyList(),
                imagem_base64 = imagemBase64
            )

            val jsonBody = chatRequestAdapter.toJson(requestDto)
            val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
            val url = "${ApiConfig.normalizeUrl(baseUrl)}chat"

            val reqBuilder = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")

            val token = tokenProvider?.invoke()?.trim()
            if (!token.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }

            val request = reqBuilder.build()
            val eventSourceFactory = EventSources.createFactory(client)

            val eventSource = eventSourceFactory.newEventSource(request, object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    // Conexão SSE aberta
                }

                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    try {
                        val sseEvent = sseAdapter.fromJson(data)
                        if (sseEvent != null) {
                            if (!sseEvent.chunk.isNullOrEmpty()) {
                                trySend(ChatStreamEvent.Chunk(sseEvent.chunk))
                            }
                            if (sseEvent.done == true) {
                                trySend(ChatStreamEvent.Done(chatId = sseEvent.chatId, titulo = sseEvent.titulo))
                                close()
                            }
                            if (!sseEvent.error.isNullOrBlank()) {
                                trySend(ChatStreamEvent.Error(sseEvent.error))
                                close()
                            }
                        }
                    } catch (_: Exception) {
                        if (data.isNotBlank()) {
                            trySend(ChatStreamEvent.Chunk(data))
                        }
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    close()
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    if (response != null) {
                        val code = response.code
                        val isAuth = code == 401 || code == 403
                        val msg = when (code) {
                            401, 403 -> "Erro de autenticação ($code): Token ausente ou inválido."
                            404 -> "Endpoint /chat não encontrado no servidor ($code)."
                            422 -> "Erro de validação na requisição enviada à API ($code)."
                            in 500..599 -> "O servidor da API encontrou um erro interno (HTTP $code)."
                            else -> "Falha na requisição SSE (HTTP $code: ${response.message})."
                        }
                        trySend(ChatStreamEvent.Error(msg, isAuthError = isAuth))
                    } else if (t != null) {
                        val isTimeout = t is SocketTimeoutException
                        val msg = if (isTimeout) {
                            "Tempo limite esgotado ao aguardar resposta da API."
                        } else {
                            "Falha de conexão com a API (${t.localizedMessage ?: "Erro de rede"})."
                        }
                        trySend(ChatStreamEvent.Error(msg))
                    } else {
                        trySend(ChatStreamEvent.Error("Falha desconhecida na conexão com o servidor."))
                    }
                    close()
                }
            })

            awaitClose {
                eventSource.cancel()
            }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Abordagem alternativa com ResponseBody.byteStream() / BufferedSource:
     * Lê linha a linha o stream SSE (data: {...}) e emite os eventos no Kotlin Flow.
     */
    fun parseSseStream(source: okio.BufferedSource): Flow<ChatStreamEvent> = flow {
        while (currentCoroutineContext().isActive) {
            val line = source.readUtf8Line() ?: break
            if (line.startsWith("data:")) {
                val jsonPayload = line.removePrefix("data:").trim()
                if (jsonPayload.isNotEmpty()) {
                    try {
                        val sseEvent = sseAdapter.fromJson(jsonPayload)
                        if (sseEvent != null) {
                            if (!sseEvent.chunk.isNullOrEmpty()) {
                                emit(ChatStreamEvent.Chunk(sseEvent.chunk))
                            }
                            if (sseEvent.done == true) {
                                emit(ChatStreamEvent.Done(chatId = sseEvent.chatId, titulo = sseEvent.titulo))
                                break
                            }
                            if (!sseEvent.error.isNullOrBlank()) {
                                emit(ChatStreamEvent.Error(sseEvent.error))
                                break
                            }
                        }
                    } catch (_: Exception) {
                        emit(ChatStreamEvent.Chunk(jsonPayload))
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
    suspend fun getAgentes(): Result<List<AgenteResponse>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getAgentes()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(IOException("Resposta vazia da API de agentes."))
                }
            } else {
                val code = response.code()
                val msg = when (code) {
                    401, 403 -> "Erro de autenticação ($code): Token ausente ou inválido."
                    404 -> "Endpoint /agentes não encontrado no servidor ($code)."
                    in 500..599 -> "Erro interno no servidor da API (HTTP $code)."
                    else -> "Falha na requisição de agentes (HTTP $code: ${response.message()})."
                }
                Result.failure(IOException(msg))
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(IOException("Tempo limite esgotado ao buscar agentes. Verifique sua conexão.", e))
        } catch (e: IOException) {
            Result.failure(IOException("Falha de conexão com a API (${e.localizedMessage ?: "Erro de rede"}). Verifique se o servidor está ativo.", e))
        } catch (e: HttpException) {
            val code = e.code()
            if (code == 401 || code == 403) {
                Result.failure(IOException("Erro de autenticação ($code): Token inválido ou expirado.", e))
            } else {
                Result.failure(IOException("Erro HTTP $code ao buscar agentes: ${e.message()}", e))
            }
        } catch (e: Exception) {
            Result.failure(IOException("Erro inesperado ao buscar agentes: ${e.localizedMessage ?: "Erro desconhecido"}", e))
        }
    }

    suspend fun sendMessage(
        messageText: String,
        skillId: String = "default",
        toolId: String? = null,
        chatId: String? = null,
        anexos: List<String>? = emptyList(),
        imagemBase64: String? = null
    ): ChatResult = withContext(Dispatchers.IO) {
        try {
            val request = ChatRequest(
                texto = messageText,
                skill_id = skillId.ifBlank { "default" },
                tool_id = toolId,
                chat_id = chatId,
                anexos = anexos ?: emptyList(),
                imagem_base64 = imagemBase64
            )
            val response = apiService.sendChatMessage(request)

            if (response.isSuccessful) {
                val body = response.body()
                val reply = body?.respostaIa?.trim()
                if (!reply.isNullOrBlank()) {
                    ChatResult.Success(
                        reply = reply,
                        chatId = body.chatId,
                        titulo = body.titulo
                    )
                } else {
                    ChatResult.Error("A API retornou uma resposta vazia.")
                }
            } else {
                val code = response.code()
                when (code) {
                    401, 403 -> {
                        ChatResult.Error(
                            errorMessage = "Erro de autenticação ($code): Token ausente ou inválido.",
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

    suspend fun getChats(): Result<List<ChatListItem>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getChats()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(IOException("Resposta vazia da lista de conversas."))
                }
            } else {
                val code = response.code()
                val msg = when (code) {
                    401, 403 -> "Erro de autenticação ($code): Token ausente ou inválido."
                    404 -> "Endpoint /chats não encontrado no servidor ($code)."
                    in 500..599 -> "Erro interno no servidor ao buscar conversas (HTTP $code)."
                    else -> "Falha ao buscar histórico de conversas (HTTP $code: ${response.message()})."
                }
                Result.failure(IOException(msg))
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(IOException("Tempo limite esgotado ao buscar histórico de conversas.", e))
        } catch (e: IOException) {
            Result.failure(IOException("Falha de conexão ao buscar histórico de conversas (${e.localizedMessage ?: "Erro de rede"}).", e))
        } catch (e: HttpException) {
            val code = e.code()
            if (code == 401 || code == 403) {
                Result.failure(IOException("Erro de autenticação ($code): Token inválido ou expirado.", e))
            } else {
                Result.failure(IOException("Erro HTTP $code ao carregar histórico: ${e.message()}", e))
            }
        } catch (e: Exception) {
            Result.failure(IOException("Erro inesperado ao buscar histórico: ${e.localizedMessage ?: "Erro desconhecido"}", e))
        }
    }

    suspend fun getChatSession(chatId: String): Result<ChatSession> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getChatSession(chatId)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(IOException("Resposta vazia ao carregar a conversa."))
                }
            } else {
                val code = response.code()
                val msg = when (code) {
                    401, 403 -> "Erro de autenticação ($code): Token ausente ou inválido."
                    404 -> "Conversa não encontrada no servidor ($code)."
                    in 500..599 -> "Erro interno no servidor ao carregar a conversa (HTTP $code)."
                    else -> "Falha ao carregar a conversa (HTTP $code: ${response.message()})."
                }
                Result.failure(IOException(msg))
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(IOException("Tempo limite esgotado ao carregar a conversa.", e))
        } catch (e: IOException) {
            Result.failure(IOException("Falha de conexão ao carregar conversa (${e.localizedMessage ?: "Erro de rede"}).", e))
        } catch (e: HttpException) {
            val code = e.code()
            if (code == 401 || code == 403) {
                Result.failure(IOException("Erro de autenticação ($code): Token inválido ou expirado.", e))
            } else {
                Result.failure(IOException("Erro HTTP $code ao carregar conversa: ${e.message()}", e))
            }
        } catch (e: Exception) {
            Result.failure(IOException("Erro inesperado ao carregar conversa: ${e.localizedMessage ?: "Erro desconhecido"}", e))
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

    suspend fun uploadArquivo(filePart: MultipartBody.Part): Result<UploadResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.uploadArquivo(filePart)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.nome_arquivo.isNotBlank()) {
                    Result.success(body)
                } else {
                    Result.failure(IOException("Resposta inválida ou nome de arquivo vazio retornado pelo servidor."))
                }
            } else {
                val code = response.code()
                val msg = when (code) {
                    401, 403 -> "Erro de autenticação ($code) no envio do arquivo."
                    413 -> "Arquivo muito grande para upload ($code)."
                    422 -> "Erro de validação no anexo ($code)."
                    in 500..599 -> "Erro interno no servidor durante o upload (HTTP $code)."
                    else -> "Falha no upload do arquivo (HTTP $code: ${response.message()})."
                }
                Result.failure(IOException(msg))
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(IOException("Tempo limite esgotado no upload do arquivo.", e))
        } catch (e: IOException) {
            Result.failure(IOException("Falha de conexão no upload do arquivo: ${e.localizedMessage ?: "Erro de rede"}", e))
        } catch (e: Exception) {
            Result.failure(IOException("Erro inesperado no upload do arquivo: ${e.localizedMessage ?: "Erro desconhecido"}", e))
        }
    }
}
