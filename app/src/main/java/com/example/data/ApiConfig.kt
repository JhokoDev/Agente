package com.example.data

/**
 * Configurações centralizadas da API FastAPI.
 */
object ApiConfig {
    /**
     * URL base oficial da API FastAPI especificada no projeto.
     */
    const val BASE_URL: String = "https://redesigned-space-guide-wv74gq6jqq5c5g69-8000.app.github.dev/"

    /**
     * Valor de exemplo para verificar se o token foi configurado pelo desenvolvedor.
     */
    const val TOKEN_PLACEHOLDER: String = "COLE_SEU_TOKEN_AQUI"

    /**
     * Timeout em segundos para requisições de rede.
     */
    const val TIMEOUT_SECONDS: Long = 45L

    /**
     * Valida se o token fornecido é válido e não é o placeholder de exemplo.
     */
    fun isTokenConfigured(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val trimmed = token.trim()
        return trimmed != TOKEN_PLACEHOLDER && !trimmed.startsWith("COLE_SEU_TOKEN")
    }
}
