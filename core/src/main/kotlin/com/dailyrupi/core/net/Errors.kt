package com.dailyrupi.core.net

import com.dailyrupi.core.model.ApiErrorBody
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/** An error response from the backend, with its stable [code] and readable [message]. */
class ApiException(val status: Int, val code: String, override val message: String) : Exception(message)

/** Runs an API call, turning an HTTP error into an [ApiException] carrying the server's code and message. */
suspend fun <T> apiCall(block: suspend () -> T): T = try {
    block()
} catch (e: HttpException) {
    throw e.toApiException()
}

fun HttpException.toApiException(): ApiException {
    val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
    val parsed = body?.let { runCatching { ApiClient.json.decodeFromString(ApiErrorBody.serializer(), it) }.getOrNull() }
    return ApiException(code(), parsed?.code.orEmpty(), parsed?.message?.takeIf { it.isNotBlank() } ?: defaultMessage(code()))
}

private fun defaultMessage(status: Int) = when (status) {
    401 -> "Please log in"
    403 -> "You do not have permission to do this"
    404 -> "Not found on the server. Check the server address."
    in 500..599 -> "The server had a problem. Try again later."
    else -> "Request failed ($status)"
}

/** What to show the user for any failure. */
fun Throwable.userMessage(): String = when (this) {
    is CancellationException -> throw this
    is ApiException -> message
    is NoServerAddressException -> "Set the server address first"
    is IOException -> "Could not reach the server. Check your connection and the server address."
    else -> "Something went wrong"
}
