package com.example

import android.net.Uri
import com.example.data.dto.AgenteResponse
import com.example.data.dto.ChatListItem
import com.example.data.dto.ChatRequest
import com.example.data.dto.ChatResponseDto
import com.example.data.dto.ChatSession
import com.example.data.dto.UploadResponse
import com.example.data.network.ChatApiService
import com.example.data.repository.ChatRepository
import com.example.data.repository.ChatResult
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
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
class ChatUploadArchitectureTest {

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

    // 1. ChatRequest serializa o campo anexos corretamente
    @Test
    fun `chat request serializes anexos list in json`() {
        val adapter = moshi.adapter(ChatRequest::class.java)

        val reqWithAttachments = ChatRequest(
            texto = "Analise estes dados",
            skill_id = "analista",
            tool_id = null,
            chat_id = "chat-123",
            anexos = listOf("relatorio.pdf", "dados.csv")
        )
        val json = adapter.toJson(reqWithAttachments)

        assertTrue(json.contains("\"texto\":\"Analise estes dados\""))
        assertTrue(json.contains("\"skill_id\":\"analista\""))
        assertTrue(json.contains("\"anexos\":[\"relatorio.pdf\",\"dados.csv\"]"))
    }

    // 2. ChatRequest serializa lista de anexos vazia
    @Test
    fun `chat request serializes empty anexos when not provided`() {
        val adapter = moshi.adapter(ChatRequest::class.java)

        val reqWithoutAttachments = ChatRequest(
            texto = "Olá sem anexos"
        )
        val json = adapter.toJson(reqWithoutAttachments)

        assertTrue(json.contains("\"anexos\":[]"))
    }

    // 3. UploadResponse deserializa nome_arquivo corretamente
    @Test
    fun `upload response deserializes correctly`() {
        val adapter = moshi.adapter(UploadResponse::class.java)
        val json = """{"nome_arquivo":"uuid-123_planilha.csv","mensagem":"Arquivo salvo"}"""

        val response = adapter.fromJson(json)
        assertNotNull(response)
        assertEquals("uuid-123_planilha.csv", response?.nome_arquivo)
        assertEquals("Arquivo salvo", response?.mensagem)
    }

    // 4. ChatRepository uploadArquivo retorna sucesso com nome_arquivo
    @Test
    fun `chat repository uploadArquivo succeeds and returns UploadResponse`() = runTest {
        val mockService = object : ChatApiService {
            override suspend fun getAgentes(): Response<List<AgenteResponse>> = Response.success(emptyList())
            override suspend fun sendChatMessage(request: ChatRequest): Response<ChatResponseDto> =
                Response.success(ChatResponseDto(respostaIa = "Ok"))
            override suspend fun getChats(): Response<List<ChatListItem>> = Response.success(emptyList())
            override suspend fun getChatSession(chatId: String): Response<ChatSession> =
                Response.success(ChatSession(id = chatId, titulo = "Sessão"))
            override suspend fun clearChat(): Response<Unit> = Response.success(Unit)

            override suspend fun uploadArquivo(file: MultipartBody.Part): Response<UploadResponse> {
                return Response.success(
                    UploadResponse(
                        nome_arquivo = "servidor_documento.pdf",
                        mensagem = "Upload realizado"
                    )
                )
            }
        }

        val repository = ChatRepository(mockService)
        val requestBody = "conteudo".toRequestBody("text/plain".toMediaType())
        val part = MultipartBody.Part.createFormData("file", "documento.pdf", requestBody)

        val result = repository.uploadArquivo(part)
        assertTrue(result.isSuccess)
        val uploadResp = result.getOrNull()
        assertNotNull(uploadResp)
        assertEquals("servidor_documento.pdf", uploadResp?.nome_arquivo)
    }

    // 5. ChatRepository uploadArquivo lida com falha de HTTP (ex: 413 arquivo muito grande)
    @Test
    fun `chat repository uploadArquivo handles HTTP error gracefully`() = runTest {
        val mockService = object : ChatApiService {
            override suspend fun getAgentes(): Response<List<AgenteResponse>> = Response.success(emptyList())
            override suspend fun sendChatMessage(request: ChatRequest): Response<ChatResponseDto> =
                Response.success(ChatResponseDto(respostaIa = "Ok"))
            override suspend fun getChats(): Response<List<ChatListItem>> = Response.success(emptyList())
            override suspend fun getChatSession(chatId: String): Response<ChatSession> =
                Response.success(ChatSession(id = chatId, titulo = "Sessão"))
            override suspend fun clearChat(): Response<Unit> = Response.success(Unit)

            override suspend fun uploadArquivo(file: MultipartBody.Part): Response<UploadResponse> {
                val errorBody = "File too large".toResponseBody("text/plain".toMediaType())
                return Response.error(413, errorBody)
            }
        }

        val repository = ChatRepository(mockService)
        val requestBody = "conteudo".toRequestBody("text/plain".toMediaType())
        val part = MultipartBody.Part.createFormData("file", "grande.bin", requestBody)

        val result = repository.uploadArquivo(part)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("413") == true)
    }

    // 6. ChatRepository sendMessage propaga anexos para o ChatRequest enviado
    @Test
    fun `chat repository sendMessage propagates anexos to the network request`() = runTest {
        var capturedRequest: ChatRequest? = null

        val mockService = object : ChatApiService {
            override suspend fun getAgentes(): Response<List<AgenteResponse>> = Response.success(emptyList())
            override suspend fun sendChatMessage(request: ChatRequest): Response<ChatResponseDto> {
                capturedRequest = request
                return Response.success(ChatResponseDto(respostaIa = "Analisei os anexos"))
            }
            override suspend fun getChats(): Response<List<ChatListItem>> = Response.success(emptyList())
            override suspend fun getChatSession(chatId: String): Response<ChatSession> =
                Response.success(ChatSession(id = chatId, titulo = "Sessão"))
            override suspend fun clearChat(): Response<Unit> = Response.success(Unit)
            override suspend fun uploadArquivo(file: MultipartBody.Part): Response<UploadResponse> =
                Response.success(UploadResponse(nome_arquivo = "mock.pdf"))
        }

        val repository = ChatRepository(mockService)
        val attachments = listOf("uploaded_1.pdf", "uploaded_2.csv")
        val result = repository.sendMessage(
            messageText = "Resuma estes dois arquivos",
            skillId = "resumidor",
            anexos = attachments
        )

        assertTrue(result is ChatResult.Success)
        assertNotNull(capturedRequest)
        assertEquals(attachments, capturedRequest?.anexos)
        assertEquals("Resuma estes dois arquivos", capturedRequest?.texto)
    }

    // 7. Gerenciamento de arquivos no ChatViewModel (adicionar, remover e limpar)
    @Test
    fun `chatViewModel correctly adds, removes, and clears selected files`() = runTest {
        val viewModel = ChatViewModel(initialToken = "token-test")
        advanceUntilIdle()

        val uri1 = Uri.parse("content://com.example.provider/docs/doc1.pdf")
        val uri2 = Uri.parse("content://com.example.provider/docs/dados.csv")

        // Adiciona arquivos com nomes explícitos
        viewModel.addSelectedFiles(
            uris = listOf(uri1, uri2),
            explicitNames = listOf("doc1.pdf", "dados.csv")
        )

        assertEquals(2, viewModel.uiState.value.selectedFiles.size)
        assertEquals(2, viewModel.selectedFilesUris.value.size)
        assertEquals("doc1.pdf", viewModel.uiState.value.selectedFiles[0].name)
        assertEquals("dados.csv", viewModel.uiState.value.selectedFiles[1].name)

        // Remove um arquivo específico
        viewModel.removeSelectedFile(uri1)
        assertEquals(1, viewModel.uiState.value.selectedFiles.size)
        assertEquals(1, viewModel.selectedFilesUris.value.size)
        assertEquals("dados.csv", viewModel.uiState.value.selectedFiles[0].name)

        // Limpa todos os arquivos
        viewModel.clearSelectedFiles()
        assertTrue(viewModel.uiState.value.selectedFiles.isEmpty())
        assertTrue(viewModel.selectedFilesUris.value.isEmpty())
    }
}
