package com.dailyrupi.core.net

import java.io.IOException
import java.net.UnknownServiceException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** "Test connection" on Server setup: is there a Daily Rupi backend at this address? */
class ServerCheck(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) {
    /** Null when the server answered like Daily Rupi does, otherwise what went wrong. */
    suspend fun problem(base: HttpUrl): String? = withContext(Dispatchers.IO) {
        val url = base.resolve("api/auth/csrf") ?: return@withContext "That is not a valid address"
        try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                val csrfCookie = response.headers("Set-Cookie").any { it.startsWith("${CsrfInterceptor.COOKIE_NAME}=") }
                when {
                    response.isSuccessful && csrfCookie -> null
                    response.isSuccessful || response.code == 404 ->
                        "Something answered at that address, but it is not a Daily Rupi server"
                    else -> "The server answered with an error (${response.code})"
                }
            }
        } catch (e: UnknownServiceException) {
            "This build only connects over https://"
        } catch (e: SSLException) {
            "Secure connection failed. Check the address and the server's certificate."
        } catch (e: IOException) {
            "Could not reach that address"
        }
    }
}
