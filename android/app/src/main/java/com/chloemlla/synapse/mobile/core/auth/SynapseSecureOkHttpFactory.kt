package com.chloemlla.synapse.mobile.core.auth

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object SynapseSecureOkHttpFactory {
    fun create(
        baseUrl: String,
    ): OkHttpClient {
        val url = baseUrl.trim().trimEnd('/').toHttpUrl()
        require(url.scheme == "https") { "Synapse API endpoints must use HTTPS." }

        return OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}
