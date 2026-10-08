package com.dailyrupi.app.data

/** The last item tree and payment methods loaded, so Add expense works offline. */
interface ReferenceCache {
    suspend fun get(name: String): String?
    suspend fun put(name: String, json: String)
}
