package com.dailyrupi.core.net

import com.dailyrupi.core.model.CategoryNode
import com.dailyrupi.core.model.ChangePasswordRequest
import com.dailyrupi.core.model.CurrentUser
import com.dailyrupi.core.model.Expense
import com.dailyrupi.core.model.ExpensePage
import com.dailyrupi.core.model.ExpenseRequest
import com.dailyrupi.core.model.ExpenseSummary
import com.dailyrupi.core.model.MonthBudget
import com.dailyrupi.core.model.PaymentMethod
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The Daily Rupi REST API, used unchanged from the web app. Paths are relative:
 * [BaseUrlInterceptor] points them at the server address chosen in the app.
 */
interface DailyRupiApi {

    @GET("api/auth/csrf")
    suspend fun csrf()

    /** Spring Security form login: 200 with the user, or 401 LOGIN_FAILED. */
    @FormUrlEncoded
    @POST("api/auth/login")
    suspend fun login(@Field("username") username: String, @Field("password") password: String): CurrentUser

    @POST("api/auth/logout")
    suspend fun logout()

    @GET("api/auth/me")
    suspend fun me(): CurrentUser

    /** Ends the session on success, so the user logs in again with the new password. */
    @PUT("api/auth/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest)

    @GET("api/payment-methods")
    suspend fun paymentMethods(): List<PaymentMethod>

    @GET("api/expenses")
    suspend fun expenses(@Query("page") page: Int, @Query("size") size: Int): ExpensePage

    @GET("api/expenses/summary")
    suspend fun summary(): ExpenseSummary

    @POST("api/expenses")
    suspend fun createExpense(@Body body: ExpenseRequest): Expense

    @PUT("api/expenses/{id}")
    suspend fun updateExpense(@Path("id") id: Long, @Body body: ExpenseRequest): Expense

    @DELETE("api/expenses/{id}")
    suspend fun deleteExpense(@Path("id") id: Long)

    /** [month] is yyyy-MM. */
    @GET("api/budgets/{month}")
    suspend fun budget(@Path("month") month: String): MonthBudget

    @GET("api/master-data")
    suspend fun masterData(@Query("includeInactive") includeInactive: Boolean = false): List<CategoryNode>
}
