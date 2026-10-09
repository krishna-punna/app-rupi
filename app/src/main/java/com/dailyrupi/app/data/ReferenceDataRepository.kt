package com.dailyrupi.app.data

import com.dailyrupi.core.masterdata.ItemChoice
import com.dailyrupi.core.masterdata.activeItemChoices
import com.dailyrupi.core.model.CategoryNode
import com.dailyrupi.core.model.PaymentMethod
import com.dailyrupi.core.net.ApiClient
import com.dailyrupi.core.net.DailyRupiApi
import com.dailyrupi.core.net.apiCall
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

/**
 * Items and payment methods for Add expense: loaded from the server once per app run and
 * kept on the phone, so the last copy is used when the server cannot be reached.
 */
@Singleton
class ReferenceDataRepository @Inject constructor(
    private val api: DailyRupiApi,
    private val cache: ReferenceCache,
) {

    @Volatile
    private var items: List<ItemChoice>? = null

    @Volatile
    private var methods: List<PaymentMethod>? = null

    suspend fun itemChoices(refresh: Boolean = false): List<ItemChoice> =
        items.takeUnless { refresh }
            ?: activeItemChoices(load(MASTER_DATA, ListSerializer(CategoryNode.serializer())) { api.masterData() })
                .also { items = it }

    suspend fun paymentMethods(refresh: Boolean = false): List<PaymentMethod> =
        methods.takeUnless { refresh }
            ?: load(PAYMENT_METHODS, ListSerializer(PaymentMethod.serializer())) { api.paymentMethods() }
                .also { methods = it }

    fun clear() {
        items = null
        methods = null
    }

    private suspend fun <T> load(name: String, serializer: KSerializer<T>, fetch: suspend () -> T): T = try {
        apiCall { fetch() }.also { cache.put(name, ApiClient.json.encodeToString(serializer, it)) }
    } catch (e: IOException) {
        val saved = cache.get(name) ?: throw e
        ApiClient.json.decodeFromString(serializer, saved)
    }

    private companion object {
        const val MASTER_DATA = "master-data"
        const val PAYMENT_METHODS = "payment-methods"
    }
}
