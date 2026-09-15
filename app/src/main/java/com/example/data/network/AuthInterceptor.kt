package com.example.data.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor OkHttp responsável por adicionar o cabeçalho "Authorization: Bearer <TOKEN>"
 * de forma segura a todas as requisições de saída.
 */
class AuthInterceptor(
    private val tokenProvider: () -> String?
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = tokenProvider()?.trim()

        val requestBuilder = originalRequest.newBuilder()

        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(requestBuilder.build())
    }
}
