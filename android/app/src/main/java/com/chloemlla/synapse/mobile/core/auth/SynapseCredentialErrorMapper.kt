package com.chloemlla.synapse.mobile.core.auth

/**
 * Maps Credential Manager / Play Services 的失败信息翻译成用户能执行的结论。
 *
 * 约束：不臆测"账号坏了"。`SIGN_IN_REQUIRED(4)`、`NoCredentialException` 在官方文档里
 * 都只表示"需要交互登录 / 当前入口不可用"，账号本身可能完全正常。
 *
 * Pure JVM so unit tests do not need Android instrumentation.
 */
internal object SynapseCredentialErrorMapper {
    /** `CommonStatusCodes.SIGN_IN_REQUIRED` / `SERVICE_MISSING` 一类：需要先把账号登录进来。 */
    private const val STATUS_SIGN_IN_REQUIRED = 4
    private const val STATUS_CANCELED = 16
    private const val STATUS_DEVELOPER_ERROR = 10
    private const val STATUS_NETWORK_ERROR = 7
    private const val STATUS_GOOGLE_SIGN_IN_CANCELLED = 12501

    /**
     * Account reauth failures are recoverable by widening the account filter, switching to the
     * Sign in with Google button flow, or falling back to the interactive Google window.
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
                "$actionLabel 需要重新验证 Google 账号。请在弹出的系统登录窗口里完成验证；" +
                    "若窗口没有出现，请检查设备上的 Google 账号登录设置，或改用账号密码登录。"
            isUserCancellation(message) ->
                "已取消 $actionLabel。"
            message.isNotBlank() ->
                "$actionLabel 未完成：$message"
            else ->
                "已取消 $actionLabel。"
        }
    }

    /** 官方列出的 `NoCredentialException` 成因：账号需要重新登录 / 登录提示被关闭 / 没有账号。 */
    fun noCredentialSummary(): String =
        "这台设备上没有可用的 Google 账号登录入口，请检查设备上的 Google 账号登录设置，或改用账号密码登录。"

    /** 系统登录窗口回来的结果；按 Play Services 状态码给结论。 */
    fun googleWindowFailure(
        statusCode: Int?,
        systemMessage: String?,
    ): String = when (statusCode) {
        STATUS_SIGN_IN_REQUIRED ->
            "这台设备上的 Google 账号还未登录，请先在系统里登录该 Google 账号，或改用账号密码登录。"
        STATUS_CANCELED, STATUS_GOOGLE_SIGN_IN_CANCELLED -> "已取消 Google 登录。"
        STATUS_DEVELOPER_ERROR -> "Google 登录未正确配置，请联系站点管理员检查 Web Client ID。"
        STATUS_NETWORK_ERROR -> "网络不可用，Google 登录失败，请稍后重试。"
        else -> googleFailureSummary(systemMessage)
    }

    fun googleFailureSummary(systemMessage: String?): String {
        val message = systemMessage?.trim().orEmpty()
        return if (message.isBlank()) {
            "Google 登录未完成，请重试或改用账号密码登录。"
        } else {
            "Google 登录未完成：$message"
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
