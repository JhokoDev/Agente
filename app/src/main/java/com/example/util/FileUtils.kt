package com.example.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException

/**
 * Utilitário para manipulação e conversão segura de arquivos e URIs
 * selecionados via Storage Access Framework sem exigir permissões legadas de armazenamento.
 */
object FileUploadUtils {

    /**
     * Recupera o nome real do arquivo a partir do ContentResolver com fallback seguro.
     */
    fun getFileName(contentResolver: ContentResolver, uri: Uri, fallback: String = "Documento selecionado"): String {
        var name: String? = null
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (_: Exception) {
                // Falha silenciosa em query de ContentProvider, recorre ao lastPathSegment
            }
        }
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment
        }
        return name?.substringAfterLast('/')?.ifBlank { fallback } ?: fallback
    }

    /**
     * Determina o tamanho do arquivo a partir do ContentResolver, ou -1 se não estiver disponível.
     */
    fun getFileSize(contentResolver: ContentResolver, uri: Uri): Long {
        var size: Long = -1L
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return size
    }

    /**
     * Formata o tamanho em bytes para representação legível ao usuário (B, KB, MB).
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return ""
        return when {
            bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    /**
     * Converte um Uri para MultipartBody.Part através de streaming eficiente
     * sem duplicar o arquivo em disco ou carregar tudo em memória RAM.
     */
    fun uriToMultipartPart(
        contentResolver: ContentResolver,
        uri: Uri,
        partName: String = "file"
    ): MultipartBody.Part {
        val fileName = getFileName(contentResolver, uri)
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val mediaType = mimeType.toMediaTypeOrNull()
        val declaredSize = getFileSize(contentResolver, uri)

        val requestBody = object : RequestBody() {
            override fun contentType(): MediaType? = mediaType

            override fun contentLength(): Long = declaredSize

            override fun writeTo(sink: BufferedSink) {
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw IOException("Não foi possível abrir o fluxo de leitura para o arquivo: $fileName")
                inputStream.use { stream ->
                    sink.writeAll(stream.source())
                }
            }
        }

        return MultipartBody.Part.createFormData(partName, fileName, requestBody)
    }

    /**
     * Retorna um ícone ou emoji apropriado para representar o tipo de arquivo.
     */
    fun getFileIcon(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> "📄"
            "csv", "tsv", "xlsx", "xls" -> "📊"
            "txt", "md", "rtf", "doc", "docx" -> "📝"
            "kt", "java", "py", "js", "ts", "html", "css", "json", "xml", "c", "cpp", "rs", "go", "sql", "sh" -> "💻"
            "zip", "tar", "gz", "rar", "7z" -> "📦"
            "png", "jpg", "jpeg", "webp", "gif", "svg" -> "🖼️"
            else -> "📎"
        }
    }
}
