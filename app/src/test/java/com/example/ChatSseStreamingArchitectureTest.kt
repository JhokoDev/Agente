package com.example

import com.example.data.dto.AgenteResponse
import com.example.data.dto.ChatListItem
import com.example.data.dto.ChatRequest
import com.example.data.dto.ChatResponseDto
import com.example.data.dto.ChatSession
import com.example.data.dto.SseChatEventDto
import com.example.data.dto.UploadResponse
import com.example.data.network.ChatApiService
import com.example.data.repository.ChatRepository
import com.example.data.repository.ChatStreamEvent
import com.example.viewmodel.ChatViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MultipartBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class ChatSseStreamingArchitectureTest {

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

    // 1. SseChatEventDto deserializa chunks e evento de conclusão done corretamente
    @Test
    fun `sse chat event dto deserializes chunk and done events`() {
        val adapter = moshi.adapter(SseChatEventDto::class.java)

        val chunkJson = """{"chunk": "Olá, como posso ajudar?"}"""
        val chunkEvent = adapter.fromJson(chunkJson)
        assertNotNull(chunkEvent)
        assertEquals("Olá, como posso ajudar?", chunkEvent?.chunk)

        val doneJson = """{"done": true, "chat_id": "chat-sse-456", "titulo": "Nova Conversa"}"""
        val doneEvent = adapter.fromJson(doneJson)
        assertNotNull(doneEvent)
        assertTrue(doneEvent?.done == true)
        assertEquals("chat-sse-456", doneEvent?.chatId)
        assertEquals("Nova Conversa", doneEvent?.titulo)
    }

    // 2. ChatRepository parseSseStream lê linha a linha os eventos SSE do FastAPI
    @Test
    fun `chat repository parseSseStream emits chunk and done events sequentially`() = runTest {
        val mockService = object : ChatApiService {
            override suspend fun getAgentes(): Response<List<AgenteResponse>> = Response.success(emptyList())
            override suspend fun sendChatMessage(request: ChatRequest): Response<ChatResponseDto> =
                Response.success(ChatResponseDto(respostaIa = "Fallback"))
            override suspend fun getChats(): Response<List<ChatListItem>> = Response.success(emptyList())
            override suspend fun getChatSession(chatId: String): Response<ChatSession> =
                Response.success(ChatSession(id = chatId, titulo = "Sessão"))
            override suspend fun clearChat(): Response<Unit> = Response.success(Unit)
            override suspend fun uploadArquivo(file: MultipartBody.Part): Response<UploadResponse> =
                Response.success(UploadResponse(nome_arquivo = "arq.pdf", mensagem = "ok"))
        }

        val repository = ChatRepository(apiService = mockService, moshi = moshi)

        val sseData = """
            data: {"chunk": "Olá"}
            
            data: {"chunk": " mundo"}
            
            data: {"done": true, "chat_id": "chat-100", "titulo": "Título Stream"}
            
        """.trimIndent()

        val buffer = Buffer().writeUtf8(sseData)
        val events = repository.parseSseStream(buffer).toList()

        assertEquals(3, events.size)
        assertTrue(events[0] is ChatStreamEvent.Chunk)
        assertEquals("Olá", (events[0] as ChatStreamEvent.Chunk).text)

        assertTrue(events[1] is ChatStreamEvent.Chunk)
        assertEquals(" mundo", (events[1] as ChatStreamEvent.Chunk).text)

        assertTrue(events[2] is ChatStreamEvent.Done)
        assertEquals("chat-100", (events[2] as ChatStreamEvent.Done).chatId)
        assertEquals("Título Stream", (events[2] as ChatStreamEvent.Done).titulo)
    }

    // 3. ViewModel sendMessage insere imediatamente o balão vazio do assistente e atualiza com os chunks
    @Test
    fun `view model sendMessage adds assistant placeholder immediately and updates on chunks`() = runTest {
        val mockService = object : ChatApiService {
            override suspend fun getAgentes(): Response<List<AgenteResponse>> = Response.success(emptyList())
            override suspend fun sendChatMessage(request: ChatRequest): Response<ChatResponseDto> =
                Response.success(
                    ChatResponseDto(
                        respostaIa = "Resposta simulada completa",
                        chatId = "chat-999",
                        titulo = "Conversa SSE"
                    )
                )
            override suspend fun getChats(): Response<List<ChatListItem>> = Response.success(emptyList())
            override suspend fun getChatSession(chatId: String): Response<ChatSession> =
                Response.success(ChatSession(id = chatId, titulo = "Sessão"))
            override suspend fun clearChat(): Response<Unit> = Response.success(Unit)
            override suspend fun uploadArquivo(file: MultipartBody.Part): Response<UploadResponse> =
                Response.success(UploadResponse(nome_arquivo = "a.txt", mensagem = "ok"))
        }

        val repository = ChatRepository(apiService = mockService, moshi = moshi)
        val viewModel = ChatViewModel(
            initialToken = "test-token",
            initialBaseUrl = "http://10.0.2.2:8000/",
            customRepository = repository
        )
        advanceUntilIdle()

        viewModel.onInputTextChanged("Explique SSE")

        // Dispara o envio
        viewModel.sendMessage()

        // Verifica estado imediatamente após o disparo:
        // Mensagem do usuário inserida e balão vazio do assistente criado
        val stateAfterSend = viewModel.uiState.value
        assertEquals(2, stateAfterSend.messages.size)
        assertEquals("Explique SSE", stateAfterSend.messages[0].content)
        assertEquals("", stateAfterSend.messages[1].content)
        assertTrue(stateAfterSend.messages[1].isStreaming)
        assertTrue(stateAfterSend.isLoading)

        // Avança até a conclusão do stream (aguardando processamento em Dispatchers.IO)
        var attempts = 0
        while (viewModel.uiState.value.isLoading && attempts < 30) {
            Thread.sleep(50)
            advanceUntilIdle()
            attempts++
        }

        val stateAfterStream = viewModel.uiState.value
        assertFalse(stateAfterStream.isLoading)
        assertEquals(2, stateAfterStream.messages.size)
        assertEquals("Resposta simulada completa", stateAfterStream.messages[1].content)
        assertFalse(stateAfterStream.messages[1].isStreaming)
        assertEquals("chat-999", stateAfterStream.currentChatId)
    }
}
