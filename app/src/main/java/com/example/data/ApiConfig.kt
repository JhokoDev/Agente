package com.example.data

/**
 * Configurações centralizadas da API FastAPI.
 */
object ApiConfig {
    /**
     * URL base oficial padrão da API FastAPI especificada no projeto.
     */
    const val DEFAULT_BASE_URL: String = "https://redesigned-space-guide-wv74gq6jqq5c5g69-8000.app.github.dev/"
    const val BASE_URL: String = DEFAULT_BASE_URL

    /**
     * Valor de exemplo para verificar se o token foi configurado pelo desenvolvedor.
     */
    const val TOKEN_PLACEHOLDER: String = "COLE_SEU_TOKEN_AQUI"

    /**
     * Timeout em segundos para requisições de rede (suporte a loop agêntico e execution tools).
     */
    const val TIMEOUT_SECONDS: Long = 150L

    /**
     * Valida e normaliza a URL da API, garantindo prefixo http/https e barra ao final para o Retrofit.
     */
    fun normalizeUrl(rawUrl: String?): String {
        if (rawUrl.isNullOrBlank()) return DEFAULT_BASE_URL
        val trimmed = rawUrl.trim()
        val withScheme = if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            "https://$trimmed"
        } else {
            trimmed
        }
        return if (!withScheme.endsWith("/")) "$withScheme/" else withScheme
    }

    /**
     * Valida se o token fornecido é válido e não é o placeholder de exemplo.
     */
    fun isTokenConfigured(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val trimmed = token.trim()
        return trimmed != TOKEN_PLACEHOLDER && !trimmed.startsWith("COLE_SEU_TOKEN")
    }
}
