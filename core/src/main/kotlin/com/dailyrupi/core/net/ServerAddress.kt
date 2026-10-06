package com.dailyrupi.core.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The backend address in use, changed from Server setup. Null until one is chosen. */
class ServerAddress(initial: HttpUrl? = null) {

    @Volatile
    var url: HttpUrl? = initial

    companion object {
        /**
         * Turns what the user typed into a base URL, or null if it is not one.
         * "rupi.example.com" becomes "https://rupi.example.com/"; a path is kept
         * for servers hosted under a prefix.
         */
        fun parse(input: String): HttpUrl? {
            val trimmed = input.trim()
            if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            val url = withScheme.toHttpUrlOrNull() ?: return null
            if (url.host.isEmpty() || url.query != null || url.fragment != null) return null
            val path = url.encodedPath.let { if (it.endsWith("/")) it else "$it/" }
            return url.newBuilder().encodedPath(path).build()
        }
    }
}
