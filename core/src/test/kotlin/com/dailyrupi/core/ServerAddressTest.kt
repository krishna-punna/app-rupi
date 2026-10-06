package com.dailyrupi.core

import com.dailyrupi.core.net.BaseUrlInterceptor
import com.dailyrupi.core.net.ServerAddress
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerAddressTest {

    @Test
    fun parsesWhatUsersType() {
        assertEquals("https://rupi.example.com/", ServerAddress.parse("rupi.example.com").toString())
        assertEquals("http://10.0.2.2:8080/", ServerAddress.parse(" http://10.0.2.2:8080 ").toString())
        assertEquals("https://host/rupi/", ServerAddress.parse("https://host/rupi").toString())
        assertNull(ServerAddress.parse(""))
        assertNull(ServerAddress.parse("not a url"))
        assertNull(ServerAddress.parse("ftp://host"))
    }

    @Test
    fun rebasesRequestsOntoServer() {
        val request = "http://daily-rupi.invalid/api/expenses?page=1&size=20".toHttpUrl()
        assertEquals(
            "https://host/rupi/api/expenses?page=1&size=20",
            BaseUrlInterceptor.rebase("https://host/rupi/".toHttpUrl(), request).toString(),
        )
        assertEquals(
            "http://10.0.2.2:8080/api/expenses?page=1&size=20",
            BaseUrlInterceptor.rebase("http://10.0.2.2:8080/".toHttpUrl(), request).toString(),
        )
    }
}

class ServerCheckTest {

    @org.junit.Test
    fun recognisesTheBackendByItsCsrfCookie() = kotlinx.coroutines.test.runTest {
        val server = okhttp3.mockwebserver.MockWebServer()
        server.start()
        val check = com.dailyrupi.core.net.ServerCheck()
        val base = server.url("/")

        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(204).addHeader("Set-Cookie", "XSRF-TOKEN=a; Path=/"))
        assertNull(check.problem(base))
        assertEquals("/api/auth/csrf", server.takeRequest().path)

        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody("<html>router</html>"))
        assertEquals("Something answered at that address, but it is not a Daily Rupi server", check.problem(base))

        server.shutdown()
        assertEquals("Could not reach that address", check.problem(base))
    }
}
