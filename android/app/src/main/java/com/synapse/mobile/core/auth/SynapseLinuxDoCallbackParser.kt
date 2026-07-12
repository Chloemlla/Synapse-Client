package com.synapse.mobile.core.auth

import android.net.Uri

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

    fun isLinuxDoRelated(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return false
        return try {
            val uri = Uri.parse(trimmed)
            when {
                uri.scheme.equals(APP_SCHEME, ignoreCase = true) &&
                    uri.host.equals(APP_HOST, ignoreCase = true) -> true
                uri.scheme.equals("https", ignoreCase = true) &&
                    (
                        uri.path?.contains("/auth/linuxdo/callback") == true ||
                            uri.path?.contains("/auth/provider/bind") == true
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
            Uri.parse(trimmed)
        } catch (error: Exception) {
            throw IllegalArgumentException("无法解析 Linux.do 回调地址。", error)
        }

        val error = uri.getQueryParameter("error")?.takeIf { it.isNotBlank() }
        val ticket = uri.getQueryParameter("ticket")?.takeIf { it.isNotBlank() }
        val intent = uri.getQueryParameter("intent")?.takeIf { it.isNotBlank() }
        val sessionToken = uri.getQueryParameter("sessionToken")?.takeIf { it.isNotBlank() }
        val bindStatus = uri.getQueryParameter("status")?.takeIf { it.isNotBlank() }
        val mergeToken = uri.getQueryParameter("mergeToken")?.takeIf { it.isNotBlank() }

        val path = uri.path.orEmpty()
        val isProviderBindPath = path.contains("/auth/provider/bind")
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
}
