package com.example.data.network

import com.example.data.ApiConfig
import com.example.data.repository.ChatRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Provedor do cliente Retrofit, OkHttp e ChatRepository com suporte a SSE streaming.
 */
object NetworkClient {

    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    fun createOkHttpClient(
        tokenProvider: () -> String?,
        isStreaming: Boolean = false
    ): OkHttpClient {
        // Logging interceptor configurado para nível BASIC ou sem cabeçalhos para não expor o Authorization Bearer
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("Authorization")
            redactHeader("Cookie")
        }

        val authInterceptor = AuthInterceptor(tokenProvider)

        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(ApiConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(ApiConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (isStreaming) {
            // Em conexões SSE, o readTimeout deve ser 0 (infinito) para permitir tokens esparsos
            builder.readTimeout(0, TimeUnit.MILLISECONDS)
        } else {
            builder.readTimeout(ApiConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }

        return builder.build()
    }

    fun createChatApiService(
        tokenProvider: () -> String?
    ): ChatApiService = createChatApiService(ApiConfig.DEFAULT_BASE_URL, tokenProvider)

    fun createChatApiService(
        baseUrl: String = ApiConfig.DEFAULT_BASE_URL,
        tokenProvider: () -> String?
    ): ChatApiService {
        val client = createOkHttpClient(tokenProvider, isStreaming = false)
        return createChatApiService(baseUrl, tokenProvider, client)
    }

    fun createChatApiService(
        baseUrl: String,
        tokenProvider: () -> String?,
        client: OkHttpClient
    ): ChatApiService {
        val normalizedUrl = ApiConfig.normalizeUrl(baseUrl)

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(ChatApiService::class.java)
    }

    fun createChatRepository(
        baseUrl: String = ApiConfig.DEFAULT_BASE_URL,
        tokenProvider: () -> String?
    ): ChatRepository {
        val sseClient = createOkHttpClient(tokenProvider, isStreaming = true)
        val standardClient = createOkHttpClient(tokenProvider, isStreaming = false)
        val apiService = createChatApiService(baseUrl, tokenProvider, standardClient)

        return ChatRepository(
            apiService = apiService,
            okHttpClient = sseClient,
            baseUrl = ApiConfig.normalizeUrl(baseUrl),
            tokenProvider = tokenProvider,
            moshi = moshi
        )
    }
}
