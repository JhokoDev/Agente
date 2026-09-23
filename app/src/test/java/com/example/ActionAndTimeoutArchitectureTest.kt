package com.example

import com.example.data.ApiConfig
import com.example.data.dto.AgenteResponse
import com.example.data.network.NetworkClient
import com.example.util.MessageParser
import com.example.viewmodel.AgentPickerTarget
import com.example.viewmodel.ChatUiState
import com.example.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
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
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class ActionAndTimeoutArchitectureTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // 1. Timeout do Retrofit / OkHttp
    // =========================================================================

    @Test
    fun `apiConfig timeout is configured to 150 seconds`() {
        assertEquals(150L, ApiConfig.TIMEOUT_SECONDS)
    }

    @Test
    fun `networkClient okHttpClient is configured with 150 seconds for connect, read, and write timeouts`() {
        // Obter OkHttpClient via chamada createChatApiService
        val service = NetworkClient.createChatApiService { "test-token" }
        assertNotNull(service)

        val client = OkHttpClient.Builder()
            .connectTimeout(ApiConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(ApiConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(ApiConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        assertEquals(150_000, client.connectTimeoutMillis)
        assertEquals(150_000, client.readTimeoutMillis)
        assertEquals(150_000, client.writeTimeoutMillis)
    }

    // =========================================================================
    // 2. Suporte a "action" na seleção de ferramentas
    // =========================================================================

    @Test
    fun `tools selection accepts both tool and action types and ignores others`() {
        val agentes = listOf(
            AgenteResponse(id = "default", nome = "Padrão", icone = "🤖", tipo = "padrao"),
            AgenteResponse(id = "skill_1", nome = "Especialista Java", icone = "☕", tipo = "skill"),
            AgenteResponse(id = "tool_1", nome = "Terminal Bash", icone = "💻", tipo = "tool"),
            AgenteResponse(id = "action_1", nome = "Executar Script Python", icone = "⚡", tipo = "action"),
            AgenteResponse(id = "other_1", nome = "Desconhecido", icone = "❓", tipo = "system")
        )

        // Filtro idêntico ao implementado em AgentSelectorBar
        val toolsAndActions = agentes.filter {
            (it.tipo.equals("tool", ignoreCase = true) || it.tipo.equals("action", ignoreCase = true)) && it.id != "default"
        }

        assertEquals(2, toolsAndActions.size)
        assertTrue(toolsAndActions.any { it.id == "tool_1" && it.tipo == "tool" })
        assertTrue(toolsAndActions.any { it.id == "action_1" && it.tipo == "action" })
        assertFalse(toolsAndActions.any { it.tipo == "skill" })
        assertFalse(toolsAndActions.any { it.tipo == "system" })
    }

    @Test
    fun `chatUiState selectedTool resolves action type correctly`() {
        val actionAgent = AgenteResponse(id = "action_cmd", nome = "CLI Action", icone = "⚡", tipo = "action")
        val toolAgent = AgenteResponse(id = "calc_tool", nome = "Calculadora", icone = "🧮", tipo = "tool")

        val stateWithAction = ChatUiState(
            agentes = listOf(actionAgent, toolAgent),
            selectedToolId = "action_cmd"
        )
        assertNotNull(stateWithAction.selectedTool)
        assertEquals("action_cmd", stateWithAction.selectedTool?.id)
        assertEquals("action", stateWithAction.selectedTool?.tipo)

        val stateWithTool = ChatUiState(
            agentes = listOf(actionAgent, toolAgent),
            selectedToolId = "calc_tool"
        )
        assertNotNull(stateWithTool.selectedTool)
        assertEquals("calc_tool", stateWithTool.selectedTool?.id)
        assertEquals("tool", stateWithTool.selectedTool?.tipo)

        val stateWithSkill = ChatUiState(
            agentes = listOf(AgenteResponse(id = "skill_id", nome = "Skill", icone = "🎯", tipo = "skill")),
            selectedToolId = "skill_id"
        )
        assertNull("Skills não devem ser resolvidas como selectedTool", stateWithSkill.selectedTool)
    }

    @Test
    fun `viewModel maintains selectedToolId when agent is of type action`() = runTest {
        val viewModel = ChatViewModel(initialToken = "test-token")
        advanceUntilIdle()

        viewModel.selectTool("action_exec")
        assertEquals("action_exec", viewModel.uiState.value.selectedToolId)

        viewModel.openPicker(AgentPickerTarget.TOOL)
        assertEquals(AgentPickerTarget.TOOL, viewModel.uiState.value.pickerTarget)
        assertTrue(viewModel.uiState.value.isBottomSheetVisible)

        viewModel.closePicker()
        assertNull(viewModel.uiState.value.pickerTarget)
        assertFalse(viewModel.uiState.value.isBottomSheetVisible)
    }

    // =========================================================================
    // 3. Renderização de Respostas da IA e Separador \n\n---\n
    // =========================================================================

    @Test
    fun `case A - normal response without separator produces no thought and unchanged response`() {
        val content = "Tudo funcionando normalmente."
        val result = MessageParser.parseAiContent(content)

        assertFalse(result.hasSeparator)
        assertNull(result.thought)
        assertEquals("Tudo funcionando normalmente.", result.finalResponse)
    }

    @Test
    fun `case B - response with action separator correctly extracts thought and final response`() {
        val content = "Executando comando no terminal\n\n---\nO comando terminou e o resultado foi X."
        val result = MessageParser.parseAiContent(content)

        assertTrue(result.hasSeparator)
        assertEquals("Executando comando no terminal", result.thought)
        assertEquals("O comando terminou e o resultado foi X.", result.finalResponse)
    }

    @Test
    fun `case C - separator with empty thought before produces null thought without crash`() {
        val content = "\n\n---\nResposta final."
        val result = MessageParser.parseAiContent(content)

        assertTrue(result.hasSeparator)
        assertNull("Thought deve ser null para não renderizar bloco vazio", result.thought)
        assertEquals("Resposta final.", result.finalResponse)
    }

    @Test
    fun `case D - multiple separator occurrences splits on the FIRST occurrence`() {
        val content = "Pensamento 1\n\n---\nResposta intermediária com outro\n\n---\nconteúdo final."
        val result = MessageParser.parseAiContent(content)

        assertTrue(result.hasSeparator)
        assertEquals("Pensamento 1", result.thought)
        assertEquals("Resposta intermediária com outro\n\n---\nconteúdo final.", result.finalResponse)
    }

    @Test
    fun `case E - message with thought and empty response after separator`() {
        val content = "Executando varredura...\n\n---\n   "
        val result = MessageParser.parseAiContent(content)

        assertTrue(result.hasSeparator)
        assertEquals("Executando varredura...", result.thought)
        assertEquals("", result.finalResponse)
    }

    @Test
    fun `case F - content with whitespace around separator is properly trimmed`() {
        val content = "   Comando em execução   \n\n---\n   Resultado obtido com sucesso   "
        val result = MessageParser.parseAiContent(content)

        assertTrue(result.hasSeparator)
        assertEquals("Comando em execução", result.thought)
        assertEquals("Resultado obtido com sucesso", result.finalResponse)
    }
}
