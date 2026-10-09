package com.dailyrupi.core.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Where the cookie jar keeps its contents between app runs. The app encrypts them. */
interface CookieStorage {
    fun read(): String?
    fun write(value: String?)
}

class InMemoryCookieStorage : CookieStorage {
    private var value: String? = null
    override fun read(): String? = value
    override fun write(value: String?) {
        this.value = value
    }
}

/**
 * Keeps the session and CSRF cookies across app restarts, including the session
 * cookie the server sends without an expiry, so the user stays logged in until
 * the server's idle timeout or Log out.
 */
class PersistentCookieJar(
    private val storage: CookieStorage,
    private val clock: () -> Long = System::currentTimeMillis,
) : CookieJar {

    private val cookies = mutableListOf<Cookie>()
    private var loaded = false

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        ensureLoaded()
        for (cookie in cookies) {
            this.cookies.removeAll { it.sameIdentity(cookie) }
            if (cookie.expiresAt > clock() && cookie.value.isNotEmpty()) {
                this.cookies += cookie
            }
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        ensureLoaded()
        val now = clock()
        if (cookies.removeAll { it.expiresAt <= now }) persist()
        return cookies.filter { it.matches(url) }
    }

    /** True when a session cookie is stored, which may still have expired on the server. */
    @Synchronized
    fun hasSession(): Boolean {
        ensureLoaded()
        return cookies.any { it.name == SESSION_COOKIE && it.expiresAt > clock() }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        loaded = true
        storage.write(null)
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val stored = storage.read() ?: return
        runCatching { json.decodeFromString(ListSerializer(StoredCookie.serializer()), stored) }
            .getOrDefault(emptyList())
            .mapTo(cookies) { it.toCookie() }
    }

    private fun persist() {
        storage.write(json.encodeToString(ListSerializer(StoredCookie.serializer()), cookies.map(StoredCookie::from)))
    }

    private fun Cookie.sameIdentity(other: Cookie) =
        name == other.name && domain == other.domain && path == other.path

    @Serializable
    private data class StoredCookie(
        val name: String,
        val value: String,
        val expiresAt: Long,
        val domain: String,
        val path: String,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean,
    ) {
        fun toCookie(): Cookie = Cookie.Builder()
            .name(name)
            .value(value)
            .expiresAt(expiresAt)
            .path(path)
            .apply { if (hostOnly) hostOnlyDomain(domain) else domain(domain) }
            .apply { if (secure) secure() }
            .apply { if (httpOnly) httpOnly() }
            .build()

        companion object {
            fun from(cookie: Cookie) = StoredCookie(
                cookie.name, cookie.value, cookie.expiresAt, cookie.domain, cookie.path,
                cookie.secure, cookie.httpOnly, cookie.hostOnly,
            )
        }
    }

    companion object {
        const val SESSION_COOKIE = "JSESSIONID"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
