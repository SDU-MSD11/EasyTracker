package com.sdumsd11.easytracker.data.remote

import com.sdumsd11.easytracker.data.prefs.UserPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/** Adds `Authorization: Bearer <token>` from DataStore; skips auth endpoints (no token yet). */
class AuthInterceptor @Inject constructor(
    private val prefs: UserPreferences,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.contains("/auth/")) return chain.proceed(request)
        val token: String? = runBlocking { runCatching { prefs.authToken.first() }.getOrNull() }
        if (token.isNullOrBlank()) return chain.proceed(request)
        return chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $token").build(),
        )
    }
}
