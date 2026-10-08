package com.dailyrupi.core

import com.dailyrupi.core.auth.OfflineLogin
import com.dailyrupi.core.net.InMemoryCookieStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineLoginTest {

    private val storage = InMemoryCookieStorage()
    private val login = OfflineLogin(storage, iterations = 1_000)

    @Test
    fun checksThePasswordWithoutStoringIt() {
        login.remember("krishna", "a long password")

        assertFalse(storage.read()!!.contains("a long password"))
        assertEquals(OfflineLogin.Result.Success("krishna"), login.check("Krishna ", "a long password"))
        assertEquals(OfflineLogin.Result.WrongPassword(4), login.check("krishna", "a long passworD"))
        assertEquals(OfflineLogin.Result.Unavailable, login.check("someone", "a long password"))
    }

    @Test
    fun aRightPasswordResetsTheCountAndFiveWrongOnesForgetTheHash() {
        login.remember("krishna", "pw")
        repeat(4) { login.check("krishna", "wrong") }
        assertEquals(OfflineLogin.Result.Success("krishna"), login.check("krishna", "pw"))

        repeat(4) { login.check("krishna", "wrong") }
        assertEquals(OfflineLogin.Result.WrongPassword(0), login.check("krishna", "wrong"))
        assertEquals(OfflineLogin.Result.Unavailable, login.check("krishna", "pw"))
        assertFalse(login.isAvailable("krishna"))
    }

    @Test
    fun eachRememberUsesANewSalt() {
        login.remember("krishna", "pw")
        val first = storage.read()
        login.remember("krishna", "pw")
        assertTrue(first != storage.read())
        login.forget()
        assertEquals(null, storage.read())
    }
}
