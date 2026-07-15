package com.chloemlla.synapse.mobile.core.auth

import java.net.URI
import java.net.URLDecoder
import java.time.Instant

object SynapseQrPayloadParser {
    fun parse(raw: String): SynapseQrPayload {
        val uri = runCatching { URI(raw.trim()) }
            .getOrElse { throw IllegalArgumentException("Invalid Synapse QR payload.") }

        require(uri.scheme == "synapse" && uri.host == "mobile-login") {
            "QR payload must use synapse://mobile-login."
        }

        val query = parseQuery(uri.rawQuery.orEmpty())
        val sessionId = query["sessionId"].orEmpty()
        val scanToken = query["scanToken"].orEmpty()
        val rawApiBaseUrl = query["apiBaseUrl"].orEmpty()
        val expiresAt = query["expiresAt"].orEmpty()

        require(sessionId.isNotBlank()) { "QR payload is missing sessionId." }
        require(scanToken.isNotBlank()) { "QR payload is missing scanToken." }
        require(rawApiBaseUrl.isNotBlank()) { "QR payload is missing apiBaseUrl." }
        val apiBaseUrl = SynapseApiOriginPolicy.normalizeHttpsOrigin(rawApiBaseUrl)
        require(expiresAt.isNotBlank()) { "QR payload is missing expiresAt." }

        return SynapseQrPayload(
            sessionId = sessionId,
            scanToken = scanToken,
            apiBaseUrl = apiBaseUrl,
            expiresAt = Instant.parse(expiresAt),
        )
    }

    private fun parseQuery(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        return rawQuery.split('&')
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = part.substring(0, separator).urlDecode()
                val value = part.substring(separator + 1).urlDecode()
                key to value
            }
            .toMap()
    }

    private fun String.urlDecode(): String =
        URLDecoder.decode(this, Charsets.UTF_8.name())
}
