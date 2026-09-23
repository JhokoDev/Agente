package com.example.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.roundToInt

/**
 * Utilitário responsável pelo processamento seguro, redimensionamento com preservação
 * de proporção, amostragem e compressão de imagens para codificação Base64
 * sem sobrecarregar a memória do dispositivo (anti-OOM).
 */
object ImageProcessingUtils {

    const val MAX_IMAGE_DIMENSION = 800
    const val DEFAULT_JPEG_QUALITY = 80

    /**
     * Converte uma imagem referenciada por [Uri] em uma String Base64 comprimida em JPEG
     * respeitando o limite máximo de 800x800 pixels e mantendo a proporção original.
     *
     * Executa com segurança em [Dispatchers.IO], liberando bitmaps da memória imediatamente.
     *
     * @return String codificada em Base64 com flag [Base64.NO_WRAP], ou `null` se falhar.
     */
    suspend fun uriToCompressedBase64(
        contentResolver: ContentResolver,
        uri: Uri,
        maxDimension: Int = MAX_IMAGE_DIMENSION,
        quality: Int = DEFAULT_JPEG_QUALITY,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): String? = withContext(dispatcher) {
        try {
            // Etapa A1: Leitura de dimensões sem carregar os pixels (inJustDecodeBounds)
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            } ?: return@withContext null

            val originalWidth = boundsOptions.outWidth
            val originalHeight = boundsOptions.outHeight

            if (originalWidth <= 0 || originalHeight <= 0) {
                return@withContext null
            }

            // Etapa A2: Cálculo de amostragem (inSampleSize)
            val sampleOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(originalWidth, originalHeight, maxDimension, maxDimension)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val sampledBitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, sampleOptions)
            } ?: return@withContext null

            // Etapa B: Redimensionamento preservando proporção (limite máximo 800x800)
            val finalBitmap = scaleBitmapToMaxDimensions(sampledBitmap, maxDimension)

            // Etapa C: Compressão para JPEG 80%
            val outputStream = ByteArrayOutputStream()
            val compressionSuccess = finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

            // Etapa E: Liberação imediata de memória dos bitmaps
            finalBitmap.recycle()
            if (sampledBitmap != finalBitmap && !sampledBitmap.isRecycled) {
                sampledBitmap.recycle()
            }

            if (!compressionSuccess) {
                return@withContext null
            }

            val byteArray = outputStream.toByteArray()
            outputStream.close()

            // Etapa D: Codificação em Base64 (NO_WRAP)
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: OutOfMemoryError) {
            System.gc()
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Sobrecarga de conveniência aceitando [Context].
     */
    suspend fun uriToCompressedBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = MAX_IMAGE_DIMENSION,
        quality: Int = DEFAULT_JPEG_QUALITY,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): String? = uriToCompressedBase64(context.contentResolver, uri, maxDimension, quality, dispatcher)

    /**
     * Calcula o fator de amostragem em potência de 2 para otimizar leitura inicial.
     */
    fun calculateInSampleSize(
        actualWidth: Int,
        actualHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (actualHeight > reqHeight || actualWidth > reqWidth) {
            val halfHeight = actualHeight / 2
            val halfWidth = actualWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Redimensiona um [Bitmap] caso alguma dimensão ultrapasse [maxDimension],
     * mantendo rigorosamente o aspect ratio original.
     * Se a imagem já for menor ou igual ao limite, retorna o bitmap original sem ampliá-lo.
     */
    fun scaleBitmapToMaxDimensions(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height

        if (width <= maxDimension && height <= maxDimension) {
            return source
        }

        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int

        if (width >= height) {
            targetWidth = maxDimension
            targetHeight = (maxDimension / ratio).roundToInt().coerceAtLeast(1)
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * ratio).roundToInt().coerceAtLeast(1)
        }

        val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        if (scaled != source && !source.isRecycled) {
            source.recycle()
        }
        return scaled
    }

    /**
     * Converte diretamente um [Bitmap] em Base64 comprimido (útil para testes unitários).
     */
    fun bitmapToCompressedBase64(
        bitmap: Bitmap,
        maxDimension: Int = MAX_IMAGE_DIMENSION,
        quality: Int = DEFAULT_JPEG_QUALITY
    ): String? {
        return try {
            val scaled = scaleBitmapToMaxDimensions(bitmap, maxDimension)
            val outputStream = ByteArrayOutputStream()
            val success = scaled.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            if (scaled != bitmap && !scaled.isRecycled) {
                scaled.recycle()
            }
            if (!success) return null
            val bytes = outputStream.toByteArray()
            outputStream.close()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
}
