package com.dailyrupi.core.net

import java.io.IOException
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/** Placeholder base for Retrofit; every request is moved onto the real [ServerAddress]. */
const val PLACEHOLDER_BASE_URL = "http://daily-rupi.invalid/"

class NoServerAddressException : IOException("No server address set")

class BaseUrlInterceptor(private val server: ServerAddress) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val base = server.url ?: throw NoServerAddressException()
        return chain.proceed(request.newBuilder().url(rebase(base, request.url)).build())
    }

    companion object {
        fun rebase(base: HttpUrl, url: HttpUrl): HttpUrl = base.newBuilder()
            .encodedPath(base.encodedPath.trimEnd('/') + url.encodedPath)
            .encodedQuery(url.encodedQuery)
            .build()
    }
}

/**
 * Spring Security's cookie CSRF protection, as the Angular app does it: every write
 * echoes the XSRF-TOKEN cookie in an X-XSRF-TOKEN header. When there is no token yet
 * one is fetched first, and a write refused for a stale token is retried once.
 */
class CsrfInterceptor(private val cookies: CookieJar) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method in SAFE_METHODS) return chain.proceed(request)

        val token = tokenFor(request.url) ?: run {
            fetchToken(chain, request)
            tokenFor(request.url)
        }
        val response = chain.proceed(withToken(request, token))
        if (response.code != 403 || !response.peekBody(PEEK_BYTES).string().contains(CSRF_ERROR_CODE)) {
            return response
        }

        response.close()
        fetchToken(chain, request)
        return chain.proceed(withToken(request, tokenFor(request.url)))
    }

    private fun tokenFor(url: HttpUrl): String? =
        cookies.loadForRequest(url).firstOrNull { it.name == COOKIE_NAME && it.value.isNotEmpty() }?.value

    private fun fetchToken(chain: Interceptor.Chain, request: Request) {
        val csrfUrl = request.url.newBuilder()
            .encodedPath(request.url.encodedPath.substringBefore("/api/") + "/api/auth/csrf")
            .query(null)
            .build()
        chain.proceed(Request.Builder().url(csrfUrl).get().build()).close()
    }

    private fun withToken(request: Request, token: String?): Request =
        if (token == null) request else request.newBuilder().header(HEADER_NAME, token).build()

    companion object {
        const val COOKIE_NAME = "XSRF-TOKEN"
        const val HEADER_NAME = "X-XSRF-TOKEN"
        private const val CSRF_ERROR_CODE = "CSRF_TOKEN_INVALID"
        private const val PEEK_BYTES = 4096L
        private val SAFE_METHODS = setOf("GET", "HEAD", "OPTIONS", "TRACE")
    }
}

/**
 * Tells the app when the server no longer accepts the session (it expired after
 * 30 minutes idle, or was ended elsewhere), so it can show Log in again.
 * A failed login is also a 401 but is reported on the login screen instead.
 */
class SessionExpiryInterceptor(private val onSessionExpired: () -> Unit) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 401 && !chain.request().url.encodedPath.endsWith("/api/auth/login")) {
            onSessionExpired()
        }
        return response
    }
}
