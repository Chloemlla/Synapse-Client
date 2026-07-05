package com.synapse.mobile.core.auth

import java.net.URI

internal object SynapseApiOriginPolicy {
    fun normalizeHttpsOrigin(rawOrigin: String): String {
        val uri = runCatching { URI(rawOrigin.trim()) }
            .getOrElse { throw IllegalArgumentException("Synapse API origin is invalid.") }

        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Synapse API origin must use HTTPS."
        }
        require(!uri.host.isNullOrBlank()) {
            "Synapse API origin must include a host."
        }
        require(uri.userInfo == null) {
            "Synapse API origin must not include user info."
        }
        require(uri.rawQuery == null && uri.rawFragment == null) {
            "Synapse API origin must not include query or fragment."
        }
        require(uri.rawPath.isNullOrBlank() || uri.rawPath == "/") {
            "Synapse API origin must not include a path."
        }

        return buildString {
            append("https://")
            append(uri.host.lowercase())
            if (uri.port != -1) {
                append(":")
                append(uri.port)
            }
        }
    }

    fun requireTrustedOrigin(candidateOrigin: String, trustedOrigin: String): String {
        val normalizedCandidate = normalizeHttpsOrigin(candidateOrigin)
        val normalizedTrusted = normalizeHttpsOrigin(trustedOrigin)
        require(normalizedCandidate == normalizedTrusted) {
            "QR payload apiBaseUrl is not trusted for this app."
        }
        return normalizedCandidate
    }
}
