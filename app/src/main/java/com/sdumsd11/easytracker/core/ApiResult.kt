package com.sdumsd11.easytracker.core

import java.io.IOException
import org.json.JSONObject
import retrofit2.Response

/**
 * Network outcome mapping. UIs never see raw responses; repos map them to [RequestStatus].
 * Backend errors are `{error: string}` with a 4xx status.
 */

/** Backend `{error}` message, or null when the body is empty/unparseable. */
fun String?.toBackendError(): String? = runCatching {
    if (this == null) return null
    JSONObject(this).optString("error").ifEmpty { null }
}.getOrNull()

/**
 * Runs [block], mapping Retrofit/transport outcomes to [RequestStatus].
 * Calls [onUnauthorized] (repos clear the stored token) on HTTP 401.
 */
suspend fun <T> safeApiCall(
    onUnauthorized: suspend () -> Unit = {},
    block: suspend () -> Response<T>,
): RequestStatus<T> = try {
    val res = block()
    if (res.isSuccessful) {
        @Suppress("UNCHECKED_CAST")
        RequestStatus.Success(res.body() ?: Unit as T) // 204 No Content -> Unit
    } else {
        if (res.code() == 401) onUnauthorized()
        RequestStatus.Failed(
            res.errorBody()?.string().toBackendError() ?: "Request failed (${res.code()})",
            retryable = res.code() !in setOf(401, 403, 404),
        )
    }
} catch (e: IOException) {
    RequestStatus.Failed(e.message ?: "No connection", retryable = true)
} catch (e: Exception) {
    RequestStatus.Failed(e.message ?: "Unexpected error", retryable = false)
}
