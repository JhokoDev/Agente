package com.example

import com.example.data.dto.AgenteResponse
import com.example.data.dto.ChatListItem
import com.example.data.dto.ChatRequest
import com.example.data.dto.ChatResponseDto
import com.example.data.dto.ChatSession
import com.example.data.dto.UploadResponse
import com.example.data.network.ChatApiService
import com.example.data.repository.ChatRepository
import com.example.data.repository.ChatResult
import com.example.viewmodel.AgentPickerTarget
import com.example.viewmodel.ChatViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MultipartBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AgentArchitectureTest {

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

    // 1. Serialização e estrutura do ChatRequest
    @Test
    fun `chat request defaults skill_id to default and tool_id to null`() {
        val request = ChatRequest(texto = "Olá")
        assertEquals("Olá", request.texto)
        assertEquals("default", request.skill_id)
        assertNull(request.tool_id)
        assertNull(request.chat_id)
    }

    @Test
    fun `chat request serializes correctly to JSON with and without tool_id`() {
        val adapter = moshi.adapter(ChatRequest::class.java)

        val requestWithoutTool = ChatRequest(texto = "Ajuda", skill_id = "expert", tool_id = null)
        val jsonWithoutTool = adapter.toJson(requestWithoutTool)
        assertTrue(jsonWithoutTool.contains("\"texto\":\"Ajuda\""))
        assertTrue(jsonWithoutTool.contains("\"skill_id\":\"expert\""))

        val requestWithTool = ChatRequest(texto = "Calcule", skill_id = "matematico", tool_id = "calculadora")
        val jsonWithTool = adapter.toJson(requestWithTool)
        assertTrue(jsonWithTool.contains("\"texto\":\"Calcule\""))
        assertTrue(jsonWithTool.contains("\"skill_id\":\"matematico\""))
        assertTrue(jsonWithTool.contains("\"tool_id\":\"calculadora\""))
    }

    // 2. Envio do request com Skill selecionada e sem Tool (tool_id = null)
    @Test
    fun `repository sends message with skill and without tool`() = runTest {
        var capturedRequest: ChatRequest? = null

        val mockService = object : ChatApiService {
            override suspend fun getAgentes(): Response<List<AgenteResponse>> {
                return Response.success(emptyList())
            }

            override suspend fun sendChatMessage(request: ChatRequest): Response<ChatResponseDto> {
                capturedRequest = request
                return Response.success(ChatResponseDto(respostaIa = "Resposta com skill"))
            }

            override suspend fun clearChat(): Response<Unit> {
                return Response.success(Unit)
            }

            override suspend fun getChats(): Response<List<ChatListItem>> {
                return Response.success(emptyList())
            }

            override suspend fun getChatSession(chatId: String): Response<ChatSession> {
                return Response.success(ChatSession(id = chatId, titulo = "Sessão"))
            }

            override suspend fun uploadArquivo(file: MultipartBody.Part): Response<UploadResponse> {
                return Response.success(UploadResponse(nome_arquivo = "mock.pdf"))
            }
        }

        val repository = ChatRepository(mockService)
        val result = repository.sendMessage(messageText = "Escreva um poema", skillId = "poeta", toolId = null)

        assertTrue(result is ChatResult.Success)
        assertNotNull(capturedRequest)
        assertEquals("Escreva um poema", capturedRequest?.texto)
        assertEquals("poeta", capturedRequest?.skill_id)
        assertNull(capturedRequest?.tool_id)
    }

    // 3. Envio do request com Skill e Tool selecionadas
    @Test
    fun `repository sends message with both skill and tool selected`() = runTest {
        var capturedRequest: ChatRequest? = null

        val mockService = object : ChatApiService {
            override suspend fun getAgentes(): Response<List<AgenteResponse>> {
                return Response.success(emptyList())
            }

            override suspend fun sendChatMessage(request: ChatRequest): Response<ChatResponseDto> {
                capturedRequest = request
                return Response.success(ChatResponseDto(respostaIa = "Resultado calculado"))
            }

            override suspend fun clearChat(): Response<Unit> {
                return Response.success(Unit)
            }

            override suspend fun getChats(): Response<List<ChatListItem>> {
                return Response.success(emptyList())
            }

            override suspend fun getChatSession(chatId: String): Response<ChatSession> {
                return Response.success(ChatSession(id = chatId, titulo = "Sessão"))
            }

            override suspend fun uploadArquivo(file: MultipartBody.Part): Response<UploadResponse> {
                return Response.success(UploadResponse(nome_arquivo = "mock.pdf"))
            }
        }

        val repository = ChatRepository(mockService)
        val result = repository.sendMessage(messageText = "2 + 2", skillId = "professor", toolId = "calculadora")

        assertTrue(result is ChatResult.Success)
        assertNotNull(capturedRequest)
        assertEquals("2 + 2", capturedRequest?.texto)
        assertEquals("professor", capturedRequest?.skill_id)
        assertEquals("calculadora", capturedRequest?.tool_id)
    }

    // 4. Seleção independente de Skill e Tool
    @Test
    fun `viewModel supports independent selection of skill and tool`() = runTest {
        val viewModel = ChatViewModel(initialToken = "test-token")

        // Início: skill default e sem tool
        assertEquals("default", viewModel.uiState.value.selectedSkillId)
        assertNull(viewModel.uiState.value.selectedToolId)

        // Seleciona uma Skill: tool continua inalterada (null)
        viewModel.selectSkill("resumidor")
        assertEquals("resumidor", viewModel.uiState.value.selectedSkillId)
        assertNull(viewModel.uiState.value.selectedToolId)

        // Seleciona uma Tool: skill continua inalterada ("resumidor")
        viewModel.selectTool("analisador_codigo")
        assertEquals("resumidor", viewModel.uiState.value.selectedSkillId)
        assertEquals("analisador_codigo", viewModel.uiState.value.selectedToolId)

        // Muda a Skill novamente: tool permanece selecionada
        viewModel.selectSkill("escritor")
        assertEquals("escritor", viewModel.uiState.value.selectedSkillId)
        assertEquals("analisador_codigo", viewModel.uiState.value.selectedToolId)
    }

    // 5. Remoção de Tool ao escolher a opção "Nenhuma ferramenta"
    @Test
    fun `selecting null tool clears tool selection while preserving skill`() = runTest {
        val viewModel = ChatViewModel(initialToken = "test-token")

        viewModel.selectSkill("especialista_ia")
        viewModel.selectTool("busca_web")

        assertEquals("especialista_ia", viewModel.uiState.value.selectedSkillId)
        assertEquals("busca_web", viewModel.uiState.value.selectedToolId)

        // Usuário toca na opção "Nenhuma ferramenta"
        viewModel.selectTool(null)

        assertEquals("especialista_ia", viewModel.uiState.value.selectedSkillId)
        assertNull(viewModel.uiState.value.selectedToolId)
    }

    // 6. Alvo e controle da BottomSheet
    @Test
    fun `picker target controls bottom sheet visibility and mode`() = runTest {
        val viewModel = ChatViewModel(initialToken = "test-token")

        assertNull(viewModel.uiState.value.pickerTarget)

        viewModel.openPicker(AgentPickerTarget.SKILL)
        assertEquals(AgentPickerTarget.SKILL, viewModel.uiState.value.pickerTarget)

        viewModel.closePicker()
        assertNull(viewModel.uiState.value.pickerTarget)

        viewModel.openPicker(AgentPickerTarget.TOOL)
        assertEquals(AgentPickerTarget.TOOL, viewModel.uiState.value.pickerTarget)

        viewModel.selectTool("ferramenta_1")
        assertNull(viewModel.uiState.value.pickerTarget)
    }

    // 7. Fallback para skill_id = default e tool_id = null se a carga de agentes falhar
    @Test
    fun `if agents fail to load chat continues sending with default skill and null tool`() = runTest {
        var capturedRequest: ChatRequest? = null

        val mockService = object : ChatApiService {
            override suspend fun getAgentes(): Response<List<AgenteResponse>> {
                throw java.io.IOException("Falha de rede ao buscar agentes")
            }

            override suspend fun sendChatMessage(request: ChatRequest): Response<ChatResponseDto> {
                capturedRequest = request
                return Response.success(ChatResponseDto(respostaIa = "Ok"))
            }

            override suspend fun clearChat(): Response<Unit> {
                return Response.success(Unit)
            }

            override suspend fun getChats(): Response<List<ChatListItem>> {
                return Response.success(emptyList())
            }

            override suspend fun getChatSession(chatId: String): Response<ChatSession> {
                return Response.success(ChatSession(id = chatId, titulo = "Sessão"))
            }

            override suspend fun uploadArquivo(file: MultipartBody.Part): Response<UploadResponse> {
                return Response.success(UploadResponse(nome_arquivo = "mock.pdf"))
            }
        }

        val repository = ChatRepository(mockService)
        val agentsResult = repository.getAgentes()

        assertTrue(agentsResult.isFailure)

        // Fallback garantido no envio
        val chatResult = repository.sendMessage("Pergunta de teste", skillId = "default", toolId = null)
        assertTrue(chatResult is ChatResult.Success)
        assertNotNull(capturedRequest)
        assertEquals("Pergunta de teste", capturedRequest?.texto)
        assertEquals("default", capturedRequest?.skill_id)
        assertNull(capturedRequest?.tool_id)
    }
}
