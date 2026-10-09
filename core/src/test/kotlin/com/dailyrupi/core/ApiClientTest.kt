package com.dailyrupi.core

import com.dailyrupi.core.model.ExpenseRequest
import com.dailyrupi.core.net.ApiClient
import com.dailyrupi.core.net.ApiException
import com.dailyrupi.core.net.DailyRupiApi
import com.dailyrupi.core.net.InMemoryCookieStorage
import com.dailyrupi.core.net.PersistentCookieJar
import com.dailyrupi.core.net.ServerAddress
import com.dailyrupi.core.net.apiCall
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Plays the part of the Spring backend to check the client's cookie, CSRF and JSON handling. */
class ApiClientTest {

    private val server = MockWebServer()
    private val storage = InMemoryCookieStorage()
    private lateinit var cookies: PersistentCookieJar
    private lateinit var api: DailyRupiApi
    private var expiredCalls = 0

    @Before
    fun setUp() {
        server.start()
        cookies = PersistentCookieJar(storage)
        val address = ServerAddress(ServerAddress.parse(server.url("/").toString()))
        api = ApiClient.api(ApiClient.okHttp(address, cookies) { expiredCalls++ })
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun loginFetchesCsrfTokenFirstAndEchoesIt() = runTest {
        server.enqueue(MockResponse().setResponseCode(204).addHeader("Set-Cookie", "XSRF-TOKEN=tok1; Path=/"))
        server.enqueue(
            json("""{"username":"admin","roles":["USER"],"passwordChangeRequired":true}""")
                .addHeader("Set-Cookie", "JSESSIONID=s1; Path=/; HttpOnly"),
        )

        val user = api.login("admin", "secret pass")

        assertEquals("admin", user.username)
        assertTrue(user.passwordChangeRequired)
        val csrf = server.takeRequest()
        assertEquals("GET", csrf.method)
        assertEquals("/api/auth/csrf", csrf.path)
        val login = server.takeRequest()
        assertEquals("/api/auth/login", login.path)
        assertEquals("tok1", login.getHeader("X-XSRF-TOKEN"))
        assertEquals("username=admin&password=secret%20pass", login.body.readUtf8())
        assertTrue(cookies.hasSession())
    }

    @Test
    fun cookiesSurviveARestart() = runTest {
        server.enqueue(json("{}").addHeader("Set-Cookie", "JSESSIONID=s1; Path=/; HttpOnly"))
        server.enqueue(json("""{"username":"krishna"}"""))
        runCatching { api.me() }

        val restarted = PersistentCookieJar(storage)
        assertTrue(restarted.hasSession())
        restarted.clear()
        assertFalse(PersistentCookieJar(storage).hasSession())
    }

    @Test
    fun writesRetryOnceWithAFreshTokenWhenTheOldOneIsRefused() = runTest {
        cookies.saveFromResponse(server.url("/"), listOf(cookie("XSRF-TOKEN=old; Path=/")))
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"code":"CSRF_TOKEN_INVALID","message":"x"}"""))
        server.enqueue(MockResponse().setResponseCode(204).addHeader("Set-Cookie", "XSRF-TOKEN=new; Path=/"))
        server.enqueue(MockResponse().setResponseCode(204))

        api.deleteExpense(5)

        assertEquals("old", server.takeRequest().getHeader("X-XSRF-TOKEN"))
        assertEquals("/api/auth/csrf", server.takeRequest().path)
        val retry = server.takeRequest()
        assertEquals("DELETE", retry.method)
        assertEquals("/api/expenses/5", retry.path)
        assertEquals("new", retry.getHeader("X-XSRF-TOKEN"))
    }

    @Test
    fun expiredSessionIsReportedButFailedLoginIsNot() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"UNAUTHENTICATED","message":"Please log in"}"""))
        try {
            apiCall { api.summary() }
            fail()
        } catch (e: ApiException) {
            assertEquals(401, e.status)
            assertEquals("UNAUTHENTICATED", e.code)
        }
        assertEquals(1, expiredCalls)

        cookies.saveFromResponse(server.url("/"), listOf(cookie("XSRF-TOKEN=t; Path=/")))
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"code":"LOGIN_FAILED","message":"Invalid username or password, or the account is temporarily locked"}"""),
        )
        try {
            apiCall { api.login("admin", "wrong") }
            fail()
        } catch (e: ApiException) {
            assertEquals("Invalid username or password, or the account is temporarily locked", e.message)
        }
        assertEquals(1, expiredCalls)
    }

    @Test
    fun amountsAndDatesKeepTheirExactValues() = runTest {
        cookies.saveFromResponse(server.url("/"), listOf(cookie("XSRF-TOKEN=t; Path=/")))
        server.enqueue(
            json(
                """{"id":9,"amount":0.10,"spentAt":"2026-10-05T18:30:00","note":null,"categoryId":1,
                "categoryName":"Food","subCategoryId":2,"subCategoryName":"Groceries","itemId":3,"itemName":"Milk",
                "paymentMethodId":1,"paymentMethodName":"UPI"}""",
            ),
        )

        val saved = api.createExpense(
            ExpenseRequest(3, 1, BigDecimal("1234.50"), LocalDateTime.of(2026, 10, 5, 18, 30, 15, 999), null),
        )

        assertEquals(BigDecimal("0.10"), saved.amount)
        assertNull(saved.note)
        assertEquals(LocalDateTime.of(2026, 10, 5, 18, 30), saved.spentAt)
        val body = server.takeRequest().body.readUtf8()
        assertEquals("""{"itemId":3,"paymentMethodId":1,"amount":1234.50,"spentAt":"2026-10-05T18:30:15"}""", body)
    }

    @Test
    fun budgetWithoutBudgetValuesParses() = runTest {
        server.enqueue(
            json(
                """{"month":"2026-10","totalBudget":5000,"totalSpent":5200.5,"unbudgetedSpent":200.5,"lines":[
                {"categoryId":1,"categoryName":"Food","categoryActive":true,"budget":5000,"spent":5000,"remaining":0},
                {"categoryId":2,"categoryName":"Fun","categoryActive":true,"budget":null,"spent":200.5,"remaining":null}]}""",
            ),
        )
        val month = api.budget("2026-10")
        assertEquals(2, month.lines.size)
        assertNull(month.lines[1].budget)
        assertEquals(BigDecimal("5200.5"), month.totalSpent)
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun cookie(header: String) = okhttp3.Cookie.parse(server.url("/"), header)!!
}
