package com.tvstreamnode.tv.data.api

import com.tvstreamnode.tv.util.Preferences
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private var api: TvheadendApi? = null
    private var currentBaseUrl: String = ""
    private var client: OkHttpClient? = null

    fun getApi(preferences: Preferences): TvheadendApi {
        val baseUrl = normalizeUrl(preferences.serverUrl)
        if (api == null || baseUrl != currentBaseUrl) {
            currentBaseUrl = baseUrl

            client = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(preferences))
                .apply {
                    if (com.tvstreamnode.tv.BuildConfig.DEBUG) {
                        addInterceptor(HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        })
                    }
                }
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client!!)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            api = retrofit.create(TvheadendApi::class.java)
        }
        return api!!
    }

    fun reset() {
        api = null
        client = null
        currentBaseUrl = ""
    }

    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://$normalized"
        }
        if (!normalized.endsWith("/")) {
            normalized = "$normalized/"
        }
        return normalized
    }
}
