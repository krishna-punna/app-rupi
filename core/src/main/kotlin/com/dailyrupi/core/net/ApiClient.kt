package com.dailyrupi.core.net

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object ApiClient {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun okHttp(server: ServerAddress, cookies: CookieJar, onSessionExpired: () -> Unit): OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(cookies)
            .addInterceptor(BaseUrlInterceptor(server))
            .addInterceptor(SessionExpiryInterceptor(onSessionExpired))
            .addInterceptor(CsrfInterceptor(cookies))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

    fun api(client: OkHttpClient): DailyRupiApi = Retrofit.Builder()
        .baseUrl(PLACEHOLDER_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(DailyRupiApi::class.java)
}
