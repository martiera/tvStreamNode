package com.tvstreamnode.tv.data.api

import com.tvstreamnode.tv.util.Preferences
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val preferences: Preferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val credentials = Credentials.basic(preferences.username, preferences.password)
        val request = chain.request().newBuilder()
            .header("Authorization", credentials)
            .build()
        return chain.proceed(request)
    }
}
