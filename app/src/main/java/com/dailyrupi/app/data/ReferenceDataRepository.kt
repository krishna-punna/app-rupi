package com.dailyrupi.app.data

import com.dailyrupi.core.masterdata.ItemChoice
import com.dailyrupi.core.masterdata.activeItemChoices
import com.dailyrupi.core.model.PaymentMethod
import com.dailyrupi.core.net.DailyRupiApi
import com.dailyrupi.core.net.apiCall
import javax.inject.Inject
import javax.inject.Singleton

/** Items and payment methods for Add expense, loaded once per app run. */
@Singleton
class ReferenceDataRepository @Inject constructor(private val api: DailyRupiApi) {

    @Volatile
    private var items: List<ItemChoice>? = null

    @Volatile
    private var methods: List<PaymentMethod>? = null

    suspend fun itemChoices(refresh: Boolean = false): List<ItemChoice> =
        items.takeUnless { refresh } ?: activeItemChoices(apiCall { api.masterData() }).also { items = it }

    suspend fun paymentMethods(refresh: Boolean = false): List<PaymentMethod> =
        methods.takeUnless { refresh } ?: apiCall { api.paymentMethods() }.also { methods = it }

    fun clear() {
        items = null
        methods = null
    }
}
