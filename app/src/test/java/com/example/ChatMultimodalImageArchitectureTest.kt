package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.dto.ChatRequest
import com.example.util.ImageProcessingUtils
import com.example.viewmodel.ChatUiEvent
import com.example.viewmodel.ChatViewModel
import com.example.viewmodel.MessageAuthor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class ChatMultimodalImageArchitectureTest {

    private val testDispatcher = StandardTestDispatcher()
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // 1. ChatRequest serializa imagem_base64 quando presente
    @Test
    fun `chat request serializes imagem_base64 when provided`() {
        val adapter = moshi.adapter(ChatRequest::class.java)

        val reqWithImage = ChatRequest(
            texto = "O que há nesta imagem?",
            skill_id = "default",
            tool_id = null,
            chat_id = "chat-999",
            anexos = emptyList(),
            imagem_base64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        )
        val json = adapter.toJson(reqWithImage)

        assertTrue(json.contains("\"texto\":\"O que há nesta imagem?\""))
        assertTrue(json.contains("\"imagem_base64\":\"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=\""))
    }

    // 2. ChatRequest serializa sem imagem_base64 ou com null para manter compatibilidade
    @Test
    fun `chat request maintains backward compatibility when imagem_base64 is omitted`() {
        val adapter = moshi.adapter(ChatRequest::class.java)

        val reqTextOnly = ChatRequest(
            texto = "Apenas texto",
            skill_id = "default"
        )
        val json = adapter.toJson(reqTextOnly)

        assertTrue(json.contains("\"texto\":\"Apenas texto\""))
        assertFalse(json.contains("\"imagem_base64\":\"algo\""))
    }

    // 3. Utilitário ImageProcessingUtils redimensiona bitmap sem ultrapassar 800x800
    @Test
    fun `ImageProcessingUtils scales large image down to max 800x800 while preserving aspect ratio`() {
        // Cria bitmap simulado de 1600x1200
        val original = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val scaled = ImageProcessingUtils.scaleBitmapToMaxDimensions(original, 800)

        assertTrue(scaled.width <= 800)
        assertTrue(scaled.height <= 800)
        // Proporção de 1600x1200 é 4:3 -> 800x600
        assertEquals(800, scaled.width)
        assertEquals(600, scaled.height)
    }

    // 4. Utilitário ImageProcessingUtils não aumenta imagens já menores que 800x800
    @Test
    fun `ImageProcessingUtils does not upscale smaller images`() {
        val original = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val scaled = ImageProcessingUtils.scaleBitmapToMaxDimensions(original, 800)

        assertEquals(400, scaled.width)
        assertEquals(300, scaled.height)
    }

    // 5. Utilitário processa imagem para JPEG e gera Base64 sem quebra de linha (NO_WRAP)
    @Test
    fun `ImageProcessingUtils compresses and encodes file uri to Base64 without line wraps`() = runTest(testDispatcher) {
        // Cria um arquivo de imagem temporário real
        val file = File(context.cacheDir, "test_sample.png")
        FileOutputStream(file).use { out ->
            val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = Uri.fromFile(file)

        val base64 = ImageProcessingUtils.uriToCompressedBase64(
            contentResolver = context.contentResolver,
            uri = uri,
            dispatcher = testDispatcher
        )
        assertNotNull(base64)
        assertTrue(base64!!.isNotEmpty())
        assertFalse(base64.contains("\n"))
        assertFalse(base64.contains("\r"))
    }

    // 6. Utilitário lida com Uri inválida ou não existente graciosamente sem lançar exceção fatal
    @Test
    fun `ImageProcessingUtils handles invalid or non-existent uri gracefully`() = runTest(testDispatcher) {
        val nonExistentUri = Uri.fromFile(File(context.cacheDir, "definitely_not_existing.jpg"))
        val base64NonExistent = ImageProcessingUtils.uriToCompressedBase64(
            contentResolver = context.contentResolver,
            uri = nonExistentUri,
            dispatcher = testDispatcher
        )
        assertNull(base64NonExistent)
    }

    // 7. ViewModel gerencia seleção e remoção de imagem no UiState
    @Test
    fun `ChatViewModel sets and clears selected image correctly`() = runTest(testDispatcher) {
        val viewModel = ChatViewModel()
        val dummyUri = Uri.parse("content://media/external/images/media/42")

        assertNull(viewModel.uiState.value.selectedImageUri)

        viewModel.setSelectedImage(dummyUri)
        assertEquals(dummyUri, viewModel.uiState.value.selectedImageUri)

        viewModel.clearSelectedImage()
        assertNull(viewModel.uiState.value.selectedImageUri)
    }

    // 8. Envio de mensagem com imagem processa e limpa preview visual
    @Test
    fun `ChatViewModel sendMessage with image adds message and clears image preview`() = runTest(testDispatcher) {
        val viewModel = ChatViewModel()
        viewModel.setContentResolver(context.contentResolver)

        val file = File(context.cacheDir, "chat_send_test.jpg")
        FileOutputStream(file).use { out ->
            val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        val uri = Uri.fromFile(file)

        viewModel.setSelectedImage(uri)
        viewModel.onInputTextChanged("Descreva esta foto")

        viewModel.sendMessage()

        // O preview visual de imagem e texto deve ter sido limpo imediatamente
        assertEquals("", viewModel.uiState.value.inputText)
        assertNull(viewModel.uiState.value.selectedImageUri)

        // A mensagem do usuário inserida no histórico contém o imageUri anexado
        val userMsg = viewModel.uiState.value.messages.firstOrNull { it.author == MessageAuthor.USER }
        assertNotNull(userMsg)
        assertEquals("Descreva esta foto", userMsg?.content)
        assertEquals(uri, userMsg?.imageUri)
    }
}
