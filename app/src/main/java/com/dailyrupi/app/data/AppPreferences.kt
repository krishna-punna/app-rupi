package com.dailyrupi.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** Small settings kept on the phone. Everything except the server address is cleared on Log out. */
interface AppPreferences {
    suspend fun serverUrl(): String?
    suspend fun setServerUrl(url: String)
    suspend fun username(): String?
    suspend fun setUsername(username: String)
    suspend fun recentItemIds(): List<Long>
    suspend fun setRecentItemIds(ids: List<Long>)
    suspend fun lastPaymentMethodId(): Long?
    suspend fun setLastPaymentMethodId(id: Long)
    suspend fun clearUserData()
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class DataStoreAppPreferences(private val context: Context) : AppPreferences {

    private suspend fun prefs(): Preferences = context.dataStore.data.first()

    override suspend fun serverUrl() = prefs()[SERVER_URL]

    override suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[SERVER_URL] = url }
    }

    override suspend fun username() = prefs()[USERNAME]

    override suspend fun setUsername(username: String) {
        context.dataStore.edit { it[USERNAME] = username }
    }

    override suspend fun recentItemIds(): List<Long> =
        prefs()[RECENT_ITEMS].orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    override suspend fun setRecentItemIds(ids: List<Long>) {
        context.dataStore.edit { it[RECENT_ITEMS] = ids.joinToString(",") }
    }

    override suspend fun lastPaymentMethodId() = prefs()[LAST_PAYMENT_METHOD]

    override suspend fun setLastPaymentMethodId(id: Long) {
        context.dataStore.edit { it[LAST_PAYMENT_METHOD] = id }
    }

    override suspend fun clearUserData() {
        context.dataStore.edit {
            it.remove(USERNAME)
            it.remove(RECENT_ITEMS)
            it.remove(LAST_PAYMENT_METHOD)
        }
    }

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val RECENT_ITEMS = stringPreferencesKey("recent_items")
        val LAST_PAYMENT_METHOD = longPreferencesKey("last_payment_method")
    }
}
