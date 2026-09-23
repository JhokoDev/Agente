package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.dto.AgenteResponse
import com.example.data.dto.ChatRequest
import com.example.data.dto.ChatResponseDto
import com.example.data.dto.ChatSession
import com.example.data.dto.ChatListItem
import com.example.data.network.ChatApiService
import com.example.data.repository.ChatRepository
import com.example.viewmodel.AgentPickerTarget
import com.example.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class ChatInputRefactorArchitectureTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `verify menu string resources exist and match requirements`() {
        val specialistStr = context.getString(R.string.menu_item_specialist)
        val toolStr = context.getString(R.string.menu_item_tool)
        val attachImageStr = context.getString(R.string.menu_item_attach_image)
        val addActionDesc = context.getString(R.string.add_action_menu_description)

        assertEquals("Especialista", specialistStr)
        assertEquals("Ferramenta", toolStr)
        assertEquals("Anexar Imagem", attachImageStr)
        assertTrue(addActionDesc.isNotBlank())
    }

    @Test
    fun `selecting specialist via picker updates state and closes picker without losing other states`() = runTest {
        val mockService = object : ChatApiService {
            override suspend fun getAgentes(): Response<List<AgenteResponse>> = Response.success(emptyList())
            override suspend fun sendChatMessage(request: ChatRequest): Response<ChatResponseDto> =
                Response.success(ChatResponseDto(respostaIa = "Ok"))
            override suspend fun clearChat(): Response<Unit> = Response.success(Unit)
            override suspend fun getChats(): Response<List<ChatListItem>> = Response.success(emptyList())
            override suspend fun getChatSession(chatId: String): Response<ChatSession> =
                Response.success(ChatSession(id = chatId, titulo = "Chat"))
            override suspend fun uploadArquivo(file: okhttp3.MultipartBody.Part): Response<com.example.data.dto.UploadResponse> =
                Response.success(com.example.data.dto.UploadResponse(nome_arquivo = "mock.pdf"))
        }

        val repository = ChatRepository(mockService)
        val viewModel = ChatViewModel(initialToken = "test-token", customRepository = repository)

        // 1. Simula toque no menu + -> Especialista
        viewModel.openPicker(AgentPickerTarget.SKILL)
        assertEquals(AgentPickerTarget.SKILL, viewModel.uiState.value.pickerTarget)

        // 2. Seleciona a Skill no seletor
        viewModel.selectSkill("skill-coder")
        assertEquals("skill-coder", viewModel.uiState.value.selectedSkillId)
        assertNull(viewModel.uiState.value.pickerTarget) // Fechado automaticamente

        // 3. Reseta a Skill para o padrão
        viewModel.selectSkill("default")
        assertEquals("default", viewModel.uiState.value.selectedSkillId)
    }

    @Test
    fun `selecting tool via picker updates tool state and clearing tool works independently`() = runTest {
        val mockService = object : ChatApiService {
            override suspend fun getAgentes(): Response<List<AgenteResponse>> = Response.success(emptyList())
            override suspend fun sendChatMessage(request: ChatRequest): Response<ChatResponseDto> =
                Response.success(ChatResponseDto(respostaIa = "Ok"))
            override suspend fun clearChat(): Response<Unit> = Response.success(Unit)
            override suspend fun getChats(): Response<List<ChatListItem>> = Response.success(emptyList())
            override suspend fun getChatSession(chatId: String): Response<ChatSession> =
                Response.success(ChatSession(id = chatId, titulo = "Chat"))
            override suspend fun uploadArquivo(file: okhttp3.MultipartBody.Part): Response<com.example.data.dto.UploadResponse> =
                Response.success(com.example.data.dto.UploadResponse(nome_arquivo = "mock.pdf"))
        }

        val repository = ChatRepository(mockService)
        val viewModel = ChatViewModel(initialToken = "test-token", customRepository = repository)

        // 1. Simula toque no menu + -> Ferramenta
        viewModel.openPicker(AgentPickerTarget.TOOL)
        assertEquals(AgentPickerTarget.TOOL, viewModel.uiState.value.pickerTarget)

        // 2. Seleciona a Tool
        viewModel.selectTool("tool-terminal")
        assertEquals("tool-terminal", viewModel.uiState.value.selectedToolId)
        assertNull(viewModel.uiState.value.pickerTarget)

        // 3. Remove a Tool selecionada através do chip X
        viewModel.selectTool(null)
        assertNull(viewModel.uiState.value.selectedToolId)
    }

    @Test
    fun `skill and tool and image can coexist simultaneously and independently in state`() = runTest {
        val mockService = object : ChatApiService {
            override suspend fun getAgentes(): Response<List<AgenteResponse>> = Response.success(emptyList())
            override suspend fun sendChatMessage(request: ChatRequest): Response<ChatResponseDto> =
                Response.success(ChatResponseDto(respostaIa = "Ok"))
            override suspend fun clearChat(): Response<Unit> = Response.success(Unit)
            override suspend fun getChats(): Response<List<ChatListItem>> = Response.success(emptyList())
            override suspend fun getChatSession(chatId: String): Response<ChatSession> =
                Response.success(ChatSession(id = chatId, titulo = "Chat"))
            override suspend fun uploadArquivo(file: okhttp3.MultipartBody.Part): Response<com.example.data.dto.UploadResponse> =
                Response.success(com.example.data.dto.UploadResponse(nome_arquivo = "mock.pdf"))
        }

        val repository = ChatRepository(mockService)
        val viewModel = ChatViewModel(initialToken = "test-token", customRepository = repository)

        // Seleciona Skill
        viewModel.selectSkill("expert")
        // Seleciona Tool
        viewModel.selectTool("search")

        val combinedState = viewModel.uiState.first()
        assertEquals("expert", combinedState.selectedSkillId)
        assertEquals("search", combinedState.selectedToolId)
        assertNull(combinedState.selectedImageUri)

        // Remove imagem quando não há imagem não afeta Skill nem Tool
        viewModel.clearSelectedImage()
        val stateStillIntact = viewModel.uiState.first()
        assertEquals("expert", stateStillIntact.selectedSkillId)
        assertEquals("search", stateStillIntact.selectedToolId)
    }
}
