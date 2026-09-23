package com.example

import com.example.data.dto.AgenteResponse
import com.example.data.dto.ChatListItem
import com.example.data.dto.ChatRequest
import com.example.data.dto.ChatResponseDto
import com.example.data.dto.ChatSession
import com.example.data.dto.SessionMessage
import com.example.data.dto.UploadResponse
import com.example.data.mapper.toChatMessage
import com.example.data.mapper.toChatMessages
import com.example.data.network.ChatApiService
import com.example.data.repository.ChatRepository
import com.example.data.repository.ChatResult
import com.example.viewmodel.ChatUiEvent
import com.example.viewmodel.ChatViewModel
import com.example.viewmodel.MessageAuthor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionArchitectureTest {

    private val testDispatcher = StandardTestDispatcher()
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // 1. Serialização de ChatRequest com chat_id nulo e preenchido
    @Test
    fun `chat request serializes chat_id correctly`() {
        val adapter = moshi.adapter(ChatRequest::class.java)

        val req1 = ChatRequest(texto = "Primeira mensagem", chat_id = null)
        val json1 = adapter.toJson(req1)
        assertTrue(json1.contains("\"texto\":\"Primeira mensagem\""))
        assertTrue(json1.contains("\"skill_id\":\"default\""))

        val req2 = ChatRequest(texto = "Segunda mensagem", chat_id = "chat-999")
        val json2 = adapter.toJson(req2)
        assertTrue(json2.contains("\"chat_id\":\"chat-999\""))
    }

    // 2. Mapeamento de SessionMessage para ChatMessage (user -> USER, assistant -> ASSISTANT)
    @Test
    fun `session message correctly maps roles to MessageAuthor`() {
        val userMsg = SessionMessage(role = "user", content = "Olá assistente")
        val assistantMsg = SessionMessage(role = "assistant", content = "Olá! Como posso ajudar?")

        val mappedUser = userMsg.toChatMessage()
        val mappedAssistant = assistantMsg.toChatMessage()

        assertEquals(MessageAuthor.USER, mappedUser.author)
        assertEquals("Olá assistente", mappedUser.content)

        assertEquals(MessageAuthor.ASSISTANT, mappedAssistant.author)
        assertEquals("Olá! Como posso ajudar?", mappedAssistant.content)

        val list = listOf(userMsg, assistantMsg).toChatMessages()
        assertEquals(2, list.size)
        assertEquals(MessageAuthor.USER, list[0].author)
        assertEquals(MessageAuthor.ASSISTANT, list[1].author)
    }

    // 3. Envio da primeira mensagem com chat_id = null e recebimento/armazenamento do chat_id
    @Test
    fun `first message is sent with chat_id null and returns new chat_id and title`() = runTest {
        var sentChatId: String? = "SHOULD_BE_OVERWRITTEN"

        val mockService = object : ChatApiService {
            override suspend fun getAgentes(): Response<List<AgenteResponse>> = Response.success(emptyList())

            override suspend fun sendChatMessage(request: ChatRequest): Response<ChatResponseDto> {
                sentChatId = request.chat_id
                return Response.success(
                    ChatResponseDto(
                        respostaIa = "Resposta do servidor",
                        chatId = "session-12345",
                        titulo = "Meu Projeto"
                    )
                )
            }

            override suspend fun getChats(): Response<List<ChatListItem>> = Response.success(emptyList())
            override suspend fun getChatSession(chatId: String): Response<ChatSession> =
                Response.success(ChatSession(id = chatId, titulo = "Sessão"))
            override suspend fun clearChat(): Response<Unit> = Response.success(Unit)
            override suspend fun uploadArquivo(file: MultipartBody.Part): Response<UploadResponse> =
                Response.success(UploadResponse(nome_arquivo = "mock.pdf"))
        }

        val repository = ChatRepository(mockService)
        val result = repository.sendMessage(messageText = "Olá!", chatId = null)

        assertTrue(result is ChatResult.Success)
        val success = result as ChatResult.Success
        assertNull(sentChatId)
        assertEquals("session-12345", success.chatId)
        assertEquals("Meu Projeto", success.titulo)
        assertEquals("Resposta do servidor", success.reply)
    }

    // 4. Envio da segunda mensagem mantendo o mesmo chat_id
    @Test
    fun `second message sends the previous chat_id in ChatRequest`() = runTest {
        var sentChatId: String? = null

        val mockService = object : ChatApiService {
            override suspend fun getAgentes(): Response<List<AgenteResponse>> = Response.success(emptyList())

            override suspend fun sendChatMessage(request: ChatRequest): Response<ChatResponseDto> {
                sentChatId = request.chat_id
                return Response.success(
                    ChatResponseDto(
                        respostaIa = "Resposta 2",
                        chatId = request.chat_id,
                        titulo = "Meu Projeto"
                    )
                )
            }

            override suspend fun getChats(): Response<List<ChatListItem>> = Response.success(emptyList())
            override suspend fun getChatSession(chatId: String): Response<ChatSession> =
                Response.success(ChatSession(id = chatId, titulo = "Sessão"))
            override suspend fun clearChat(): Response<Unit> = Response.success(Unit)
            override suspend fun uploadArquivo(file: MultipartBody.Part): Response<UploadResponse> =
                Response.success(UploadResponse(nome_arquivo = "mock.pdf"))
        }

        val repository = ChatRepository(mockService)
        val result = repository.sendMessage(messageText = "Pergunta seguinte", chatId = "session-12345")

        assertTrue(result is ChatResult.Success)
        assertEquals("session-12345", sentChatId)
    }

    // 5. Criação de novo chat (startNewChat) limpa mensagens locais e reseta chat_id preservando Skill e Tool
    @Test
    fun `startNewChat resets chat_id and messages while preserving skill and tool`() = runTest {
        val mockAgentes = listOf(
            AgenteResponse(id = "especialista_python", nome = "Python", icone = "🐍", tipo = "skill"),
            AgenteResponse(id = "calculadora", nome = "Calc", icone = "🔢", tipo = "tool")
        )
        val mockService = object : ChatApiService {
            override suspend fun getAgentes(): Response<List<AgenteResponse>> = Response.success(mockAgentes)
            override suspend fun sendChatMessage(request: ChatRequest): Response<ChatResponseDto> =
                Response.success(ChatResponseDto(respostaIa = "Ok"))
            override suspend fun getChats(): Response<List<ChatListItem>> = Response.success(emptyList())
            override suspend fun getChatSession(chatId: String): Response<ChatSession> =
                Response.success(ChatSession(id = chatId, titulo = "Sessão"))
            override suspend fun clearChat(): Response<Unit> = Response.success(Unit)
            override suspend fun uploadArquivo(file: MultipartBody.Part): Response<UploadResponse> =
                Response.success(UploadResponse(nome_arquivo = "mock.pdf"))
        }
        val repository = ChatRepository(mockService)
        val viewModel = ChatViewModel(initialToken = "token-test", customRepository = repository)
        advanceUntilIdle()

        viewModel.selectSkill("especialista_python")
        viewModel.selectTool("calculadora")

        // Simula conversa ativa
        assertEquals("especialista_python", viewModel.uiState.value.selectedSkillId)
        assertEquals("calculadora", viewModel.uiState.value.selectedToolId)

        // Usuário clica em + Novo chat
        viewModel.startNewChat()

        assertNull(viewModel.uiState.value.currentChatId)
        assertNull(viewModel.uiState.value.currentChatTitle)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
        // Skill e Tool permanecem inalteradas
        assertEquals("especialista_python", viewModel.uiState.value.selectedSkillId)
        assertEquals("calculadora", viewModel.uiState.value.selectedToolId)
    }

    // 6. Carregamento do histórico (getChats)
    @Test
    fun `loadChats populates chat list in state`() = runTest {
        val mockChats = listOf(
            ChatListItem(id = "c1", titulo = "Planejamento"),
            ChatListItem(id = "c2", titulo = "Resumo de código")
        )

        val mockService = object : ChatApiService {
            override suspend fun getAgentes(): Response<List<AgenteResponse>> = Response.success(emptyList())
            override suspend fun sendChatMessage(request: ChatRequest): Response<ChatResponseDto> =
                Response.success(ChatResponseDto(respostaIa = "Ok"))
            override suspend fun getChats(): Response<List<ChatListItem>> = Response.success(mockChats)
            override suspend fun getChatSession(chatId: String): Response<ChatSession> =
                Response.success(ChatSession(id = chatId, titulo = "Sessão"))
            override suspend fun clearChat(): Response<Unit> = Response.success(Unit)
            override suspend fun uploadArquivo(file: MultipartBody.Part): Response<UploadResponse> =
                Response.success(UploadResponse(nome_arquivo = "mock.pdf"))
        }

        val repository = ChatRepository(mockService)
        val result = repository.getChats()

        assertTrue(result.isSuccess)
        val list = result.getOrNull()
        assertNotNull(list)
        assertEquals(2, list?.size)
        assertEquals("Planejamento", list?.get(0)?.titulo)
        assertEquals("Resumo de código", list?.get(1)?.titulo)
    }

    // 7. Abertura de uma sessão antiga (openChat)
    @Test
    fun `openChat loads session messages and updates currentChatId and currentChatTitle`() = runTest {
        val mockSession = ChatSession(
            id = "chat-456",
            titulo = "Discussão de arquitetura",
            mensagens = listOf(
                SessionMessage(role = "user", content = "Qual arquitetura adotar?"),
                SessionMessage(role = "assistant", content = "Recomendo MVVM com Clean Architecture.")
            )
        )

        val mockService = object : ChatApiService {
            override suspend fun getAgentes(): Response<List<AgenteResponse>> = Response.success(emptyList())
            override suspend fun sendChatMessage(request: ChatRequest): Response<ChatResponseDto> =
                Response.success(ChatResponseDto(respostaIa = "Ok"))
            override suspend fun getChats(): Response<List<ChatListItem>> = Response.success(emptyList())
            override suspend fun getChatSession(chatId: String): Response<ChatSession> =
                Response.success(mockSession)
            override suspend fun clearChat(): Response<Unit> = Response.success(Unit)
            override suspend fun uploadArquivo(file: MultipartBody.Part): Response<UploadResponse> =
                Response.success(UploadResponse(nome_arquivo = "mock.pdf"))
        }

        val repository = ChatRepository(mockService)
        val result = repository.getChatSession("chat-456")

        assertTrue(result.isSuccess)
        val session = result.getOrNull()
        assertNotNull(session)
        assertEquals("chat-456", session?.id)
        assertEquals("Discussão de arquitetura", session?.titulo)

        val messages = session?.mensagens?.toChatMessages()
        assertEquals(2, messages?.size)
        assertEquals(MessageAuthor.USER, messages?.get(0)?.author)
        assertEquals("Qual arquitetura adotar?", messages?.get(0)?.content)
        assertEquals(MessageAuthor.ASSISTANT, messages?.get(1)?.author)
        assertEquals("Recomendo MVVM com Clean Architecture.", messages?.get(1)?.content)
    }

    // 8. Falha ao listar conversas (trata erro e não quebra o app)
    @Test
    fun `getChats failure returns error result gracefully`() = runTest {
        val mockService = object : ChatApiService {
            override suspend fun getAgentes(): Response<List<AgenteResponse>> = Response.success(emptyList())
            override suspend fun sendChatMessage(request: ChatRequest): Response<ChatResponseDto> =
                Response.success(ChatResponseDto(respostaIa = "Ok"))
            override suspend fun getChats(): Response<List<ChatListItem>> {
                val errorBody = "Internal error".toResponseBody("text/plain".toMediaType())
                return Response.error(500, errorBody)
            }
            override suspend fun getChatSession(chatId: String): Response<ChatSession> =
                Response.success(ChatSession(id = chatId, titulo = "Sessão"))
            override suspend fun clearChat(): Response<Unit> = Response.success(Unit)
            override suspend fun uploadArquivo(file: MultipartBody.Part): Response<UploadResponse> =
                Response.success(UploadResponse(nome_arquivo = "mock.pdf"))
        }

        val repository = ChatRepository(mockService)
        val result = repository.getChats()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("500") == true)
    }

    // 9. Falha ao abrir uma sessão preserva a conversa já aberta
    @Test
    fun `getChatSession failure preserves current state and reports error`() = runTest {
        val mockService = object : ChatApiService {
            override suspend fun getAgentes(): Response<List<AgenteResponse>> = Response.success(emptyList())
            override suspend fun sendChatMessage(request: ChatRequest): Response<ChatResponseDto> =
                Response.success(ChatResponseDto(respostaIa = "Ok"))
            override suspend fun getChats(): Response<List<ChatListItem>> = Response.success(emptyList())
            override suspend fun getChatSession(chatId: String): Response<ChatSession> {
                val errorBody = "Not found".toResponseBody("text/plain".toMediaType())
                return Response.error(404, errorBody)
            }
            override suspend fun clearChat(): Response<Unit> = Response.success(Unit)
            override suspend fun uploadArquivo(file: MultipartBody.Part): Response<UploadResponse> =
                Response.success(UploadResponse(nome_arquivo = "mock.pdf"))
        }

        val repository = ChatRepository(mockService)
        val result = repository.getChatSession("chat-inexistente")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("404") == true)
    }

    // 10. Atualização do ViewModel ao enviar mensagem em nova conversa
    @Test
    fun `viewModel updates currentChatId and currentChatTitle after successful first message`() = runTest {
        val mockService = object : ChatApiService {
            override suspend fun getAgentes(): Response<List<AgenteResponse>> = Response.success(emptyList())
            override suspend fun sendChatMessage(request: ChatRequest): Response<ChatResponseDto> =
                Response.success(ChatResponseDto(respostaIa = "Paris", chatId = "c-1", titulo = "França"))
            override suspend fun getChats(): Response<List<ChatListItem>> = Response.success(emptyList())
            override suspend fun getChatSession(chatId: String): Response<ChatSession> =
                Response.success(ChatSession(id = chatId, titulo = "Sessão"))
            override suspend fun clearChat(): Response<Unit> = Response.success(Unit)
            override suspend fun uploadArquivo(file: MultipartBody.Part): Response<UploadResponse> =
                Response.success(UploadResponse(nome_arquivo = "mock.pdf"))
        }
        val repository = ChatRepository(mockService)
        val viewModel = ChatViewModel(initialToken = "token-test", customRepository = repository)
        advanceUntilIdle()

        // Estado inicial
        assertNull(viewModel.uiState.value.currentChatId)
        assertNull(viewModel.uiState.value.currentChatTitle)

        // Digita e envia mensagem
        viewModel.onInputTextChanged("Qual a capital da França?")
        viewModel.sendMessage()

        var attempts = 0
        while (viewModel.uiState.value.isLoading && attempts < 30) {
            Thread.sleep(50)
            advanceUntilIdle()
            attempts++
        }

        assertEquals("", viewModel.uiState.value.inputText)
        assertEquals(2, viewModel.uiState.value.messages.size)
        assertEquals("Qual a capital da França?", viewModel.uiState.value.messages[0].content)
        assertEquals(MessageAuthor.USER, viewModel.uiState.value.messages[0].author)
        assertEquals("Paris", viewModel.uiState.value.messages[1].content)
        assertEquals(MessageAuthor.ASSISTANT, viewModel.uiState.value.messages[1].author)
        assertEquals("c-1", viewModel.uiState.value.currentChatId)
        assertEquals("França", viewModel.uiState.value.currentChatTitle)
    }
}
