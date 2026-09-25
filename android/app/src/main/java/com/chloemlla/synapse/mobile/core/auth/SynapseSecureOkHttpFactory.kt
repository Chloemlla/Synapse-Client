package com.chloemlla.synapse.mobile.core.auth

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object SynapseSecureOkHttpFactory {
    /**
     * @param ipVerificationFingerprint 传入设备指纹后会给同源请求挂上首次访问闸门所需的
     *        X-Fingerprint / X-IP-Verification-Token，见 [SynapseIpVerificationInterceptor]。
     *        为 null 时不加该拦截器（引导请求必须走这条，否则会自我递归）。
     */
    fun create(
        baseUrl: String,
        ipVerificationFingerprint: (() -> String)? = null,
    ): OkHttpClient {
        val url = baseUrl.trim().trimEnd('/').toHttpUrl()
        require(url.scheme == "https") { "Synapse API endpoints must use HTTPS." }

        val builder = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)

        if (ipVerificationFingerprint != null) {
            builder.addInterceptor(
                SynapseIpVerificationInterceptor(
                    baseUrl = "$url".trimEnd('/'),
                    fingerprintProvider = ipVerificationFingerprint,
                ),
            )
        }

        return builder.build()
    }
}
