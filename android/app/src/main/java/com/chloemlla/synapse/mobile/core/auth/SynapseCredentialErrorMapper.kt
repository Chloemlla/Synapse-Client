package com.chloemlla.synapse.mobile.core.auth

/**
 * Maps Credential Manager cancellation / provider messages into user-facing summaries.
 * Pure JVM so unit tests do not need Android instrumentation.
 */
internal object SynapseCredentialErrorMapper {
    /**
     * Account reauth failures are often recoverable by widening the Google account picker
     * (drop authorized-only filter) or switching to the full Sign in with Google button.
     * Real user cancellations must not auto-retry.
     */
    fun shouldRetryAfterCancellation(
        systemMessage: String?,
        hasRemainingFallback: Boolean,
    ): Boolean {
        if (!hasRemainingFallback) return false
        return isAccountReauthFailure(systemMessage.orEmpty())
    }

    fun cancellationSummary(
        systemMessage: String?,
        actionLabel: String,
    ): String {
        val message = systemMessage?.trim().orEmpty()
        return when {
            isAccountReauthFailure(message) ->
                "$actionLabel 需要重新验证 Google 账号登录状态。请打开系统设置重新登录该账号，或更换账号后重试。"
            isUserCancellation(message) ->
                "已取消 $actionLabel。"
            message.isNotBlank() ->
                "$actionLabel 未完成：$message"
            else ->
                "已取消 $actionLabel。"
        }
    }

    fun isAccountReauthFailure(message: String): Boolean {
        val normalized = message.trim().lowercase()
        if (normalized.isEmpty()) return false
        return normalized.contains("account reauth failed") ||
            normalized.contains("reauth failed") ||
            Regex("""\[\s*16\s*]""").containsMatchIn(normalized)
    }

    fun isUserCancellation(message: String): Boolean {
        val normalized = message.trim().lowercase()
        if (normalized.isEmpty()) return true
        if (isAccountReauthFailure(normalized)) return false
        return normalized.contains("cancel") ||
            normalized.contains("cancelled") ||
            normalized.contains("canceled") ||
            normalized.contains("user canceled") ||
            normalized.contains("user cancelled") ||
            normalized.contains("activity is cancelled") ||
            normalized.contains("activity is canceled")
    }
}
