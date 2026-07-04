package com.synapse.mobile.core.auth

import okhttp3.CertificatePinner
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object SynapseSecureOkHttpFactory {
    fun create(
        baseUrl: String,
        certificatePins: String = "",
        requireCertificatePins: Boolean = false,
    ): OkHttpClient {
        val url = baseUrl.trim().trimEnd('/').toHttpUrl()
        require(url.scheme == "https") { "Synapse API endpoints must use HTTPS." }

        val pins = CertificatePinPolicy.parse(certificatePins)
        require(!requireCertificatePins || pins.isNotEmpty()) {
            "Certificate pins are required for ${url.host}."
        }

        return OkHttpClient.Builder().apply {
            if (pins.isNotEmpty()) {
                certificatePinner(
                    CertificatePinner.Builder().apply {
                        pins.forEach { pin -> add(url.host, pin) }
                    }.build(),
                )
            }
        }
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}
