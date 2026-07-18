package com.chloemlla.synapse.mobile.core.auth

import java.net.URI
import java.net.URLDecoder

/**
 * Parses Happy-TTS Linux.do OAuth completion redirects for the Android app.
 *
 * Successful login: frontend SPA callback contains `ticket` (+ optional `intent`).
 * Needs web bind: `/auth/provider/bind?sessionToken=...`
 * Failure: `error` query param.
 *
 * Also accepts:
 * - custom app deep link `synapse://linuxdo-callback?ticket=...`
 * - bare query fragments copied from the SPA page (`ticket=...&intent=login`)
 *
 * Backend OAuth redirect_uri `/api/auth/linuxdo/callback` is intentionally not
 * treated as an app-owned completion URL.
 */
object SynapseLinuxDoCallbackParser {
    const val APP_SCHEME = "synapse"
    const val APP_HOST = "linuxdo-callback"
    private const val FRONTEND_CALLBACK_PATH = "/auth/linuxdo/callback"
    private const val PROVIDER_BIND_PATH = "/auth/provider/bind"
    private val CALLBACK_QUERY_KEYS = setOf(
        "ticket",
        "error",
        "sessiontoken",
        "mergetoken",
        "status",
        "intent",
    )

    fun isLinuxDoRelated(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return false
        if (looksLikeCallbackQuery(trimmed)) {
            return true
        }
        return try {
            val uri = URI(trimmed)
            when {
                uri.scheme.equals(APP_SCHEME, ignoreCase = true) &&
                    uri.host.equals(APP_HOST, ignoreCase = true) -> true
                uri.scheme.equals("https", ignoreCase = true) &&
                    (
                        isFrontendCallbackPath(uri.path) ||
                            isProviderBindPath(uri.path)
                        ) -> true
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun parse(raw: String): LinuxDoCallbackPayload {
        val trimmed = raw.trim()
        require(trimmed.isNotBlank()) { "Linux.do 回调地址为空。" }

        // Accept bare query strings copied from the SPA completion page.
        if (looksLikeCallbackQuery(trimmed)) {
            return payloadFromQuery(
                query = parseQuery(trimmed.removePrefix("?")),
                path = FRONTEND_CALLBACK_PATH,
            )
        }

        val uri = try {
            URI(trimmed)
        } catch (error: Exception) {
            throw IllegalArgumentException("无法解析 Linux.do 回调地址。", error)
        }

        return payloadFromQuery(
            query = parseQuery(uri.rawQuery.orEmpty()),
            path = uri.path.orEmpty(),
        )
    }

    private fun payloadFromQuery(
        query: Map<String, String>,
        path: String,
    ): LinuxDoCallbackPayload {
        val error = query["error"]?.takeIf { it.isNotBlank() }
        val ticket = query["ticket"]?.takeIf { it.isNotBlank() }
        val intent = query["intent"]?.takeIf { it.isNotBlank() }
        val sessionToken = query["sessionToken"]?.takeIf { it.isNotBlank() }
        val bindStatus = query["status"]?.takeIf { it.isNotBlank() }
        val mergeToken = query["mergeToken"]?.takeIf { it.isNotBlank() }

        val resolvedIntent = when {
            !intent.isNullOrBlank() -> intent
            isProviderBindPath(path) || !sessionToken.isNullOrBlank() -> "bind"
            else -> null
        }

        return LinuxDoCallbackPayload(
            ticket = ticket,
            intent = resolvedIntent,
            error = error,
            sessionToken = sessionToken,
            bindStatus = bindStatus,
            mergeToken = mergeToken,
        )
    }

    private fun looksLikeCallbackQuery(raw: String): Boolean {
        if (raw.contains("://")) return false
        val candidate = raw.removePrefix("?")
        if (!candidate.contains('=')) return false
        val keys = parseQuery(candidate).keys.map { it.lowercase() }.toSet()
        return keys.any { it in CALLBACK_QUERY_KEYS }
    }

    private fun isFrontendCallbackPath(path: String?): Boolean {
        val normalized = normalizePath(path)
        return normalized == FRONTEND_CALLBACK_PATH ||
            normalized.startsWith("$FRONTEND_CALLBACK_PATH/")
    }

    private fun isProviderBindPath(path: String?): Boolean {
        val normalized = normalizePath(path)
        return normalized == PROVIDER_BIND_PATH ||
            normalized.startsWith("$PROVIDER_BIND_PATH/")
    }

    private fun normalizePath(path: String?): String {
        val raw = path.orEmpty().ifBlank { "/" }
        return if (raw.length > 1 && raw.endsWith('/')) raw.dropLast(1) else raw
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