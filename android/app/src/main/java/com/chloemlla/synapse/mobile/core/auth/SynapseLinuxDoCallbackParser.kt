package com.chloemlla.synapse.mobile.core.auth

import java.net.URI
import java.net.URLDecoder

/**
 * Parses Happy-TTS Linux.do OAuth completion redirects for the Android app.
 *
 * Successful login: frontend callback contains `ticket` (+ optional `intent`).
 * Needs web bind: `/auth/provider/bind?sessionToken=...`
 * Failure: `error` query param.
 *
 * Also accepts a custom app deep link:
 * `synapse://linuxdo-callback?ticket=...` or `?error=...`
 */
object SynapseLinuxDoCallbackParser {
    const val APP_SCHEME = "synapse"
    const val APP_HOST = "linuxdo-callback"
    private const val FRONTEND_CALLBACK_PATH = "/auth/linuxdo/callback"
    private const val PROVIDER_BIND_PATH = "/auth/provider/bind"

    fun isLinuxDoRelated(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return false
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
        val uri = try {
            URI(trimmed)
        } catch (error: Exception) {
            throw IllegalArgumentException("无法解析 Linux.do 回调地址。", error)
        }

        val query = parseQuery(uri.rawQuery.orEmpty())
        val error = query["error"]?.takeIf { it.isNotBlank() }
        val ticket = query["ticket"]?.takeIf { it.isNotBlank() }
        val intent = query["intent"]?.takeIf { it.isNotBlank() }
        val sessionToken = query["sessionToken"]?.takeIf { it.isNotBlank() }
        val bindStatus = query["status"]?.takeIf { it.isNotBlank() }
        val mergeToken = query["mergeToken"]?.takeIf { it.isNotBlank() }

        val path = uri.path.orEmpty()
        val isProviderBindPath = isProviderBindPath(path)
        val resolvedIntent = when {
            !intent.isNullOrBlank() -> intent
            isProviderBindPath || !sessionToken.isNullOrBlank() -> "bind"
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
