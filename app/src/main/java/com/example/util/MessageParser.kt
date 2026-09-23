package com.example.util

/**
 * Representa o conteúdo de uma mensagem da IA decomposto em pensamento/comando
 * secundário e resposta final após o término do loop agêntico/action no backend.
 */
data class ParsedAiMessage(
    val thought: String?,
    val finalResponse: String,
    val hasSeparator: Boolean
)

/**
 * Utilitário responsável por interpretar e decompor mensagens da IA que contenham
 * o separador oficial de execução de actions emitido pelo backend FastAPI.
 */
object MessageParser {

    /**
     * Separador oficial entre o pensamento/comando da IA e a resposta final.
     */
    const val SEPARATOR: String = "\n\n---\n"

    /**
     * Faz o parsing do texto da mensagem da IA.
     *
     * Regras:
     * - Utiliza a primeira ocorrência de [SEPARATOR] como divisor.
     * - Se o separador existir e houver texto antes dele, esse texto é tratado como [ParsedAiMessage.thought].
     * - Se o texto antes do separador for vazio ou consistir apenas em espaços, [thought] será null
     *   para evitar a criação de áreas vazias ou divisores desnecessários na interface.
     * - O texto após o separador é retornado como [ParsedAiMessage.finalResponse].
     * - Se o separador não existir, [thought] será null e [finalResponse] conterá o conteúdo original na íntegra.
     */
    fun parseAiContent(rawContent: String): ParsedAiMessage {
        val separatorIndex = rawContent.indexOf(SEPARATOR)
        if (separatorIndex == -1) {
            return ParsedAiMessage(
                thought = null,
                finalResponse = rawContent,
                hasSeparator = false
            )
        }

        val rawThought = rawContent.substring(0, separatorIndex).trim()
        val rawResponse = rawContent.substring(separatorIndex + SEPARATOR.length).trim()

        val thought = rawThought.ifEmpty { null }
        val finalResponse = if (rawResponse.isEmpty() && thought == null) {
            rawContent
        } else {
            rawResponse
        }

        return ParsedAiMessage(
            thought = thought,
            finalResponse = finalResponse,
            hasSeparator = true
        )
    }
}
