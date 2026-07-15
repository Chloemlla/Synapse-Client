package com.chloemlla.synapse.mobile.core.notify

/**
 * Host-owned Live Update session kinds.
 *
 * Official criteria (Android Live Updates): ongoing, user-initiated, time-sensitive.
 * Synapse uses this for web QR confirmation and Linux.do OAuth return waits only.
 */
enum class SynapseLiveUpdateKind {
    WebQrLogin,
    LinuxDoAuth,
}

enum class SynapseLiveUpdatePhase {
    WaitingConfirm,
    WaitingBrowserReturn,
    Completing,
    Succeeded,
    Failed,
}

data class SynapseLiveUpdateSnapshot(
    val kind: SynapseLiveUpdateKind,
    val phase: SynapseLiveUpdatePhase,
    val title: String,
    val text: String,
    val shortCriticalText: String?,
    val whenEpochMillis: Long?,
    val ongoing: Boolean,
    val requestPromoted: Boolean,
    val showIndeterminateProgress: Boolean = false,
    val alert: Boolean = false,
    val autoDismissAfterMs: Long? = null,
    val openTab: String? = null,
)

object SynapseLiveUpdateCopy {
    private const val TERMINAL_SUCCESS_DISMISS_MS = 4_000L
    private const val TERMINAL_FAILURE_DISMISS_MS = 8_000L

    fun webQrWaiting(site: String?, expiresAtEpochMillis: Long?): SynapseLiveUpdateSnapshot {
        val siteLabel = displaySite(site)
        return SynapseLiveUpdateSnapshot(
            kind = SynapseLiveUpdateKind.WebQrLogin,
            phase = SynapseLiveUpdatePhase.WaitingConfirm,
            title = "待确认网页登录",
            text = "已识别 $siteLabel 登录请求。返回应用点确认后，网页端才会完成登录。",
            shortCriticalText = chipTextForExpiry(expiresAtEpochMillis) ?: "待确认",
            whenEpochMillis = expiresAtEpochMillis,
            ongoing = true,
            requestPromoted = true,
            alert = true,
            openTab = "qr",
        )
    }

    fun webQrCompleting(site: String?): SynapseLiveUpdateSnapshot {
        val siteLabel = displaySite(site)
        return SynapseLiveUpdateSnapshot(
            kind = SynapseLiveUpdateKind.WebQrLogin,
            phase = SynapseLiveUpdatePhase.Completing,
            title = "正在确认网页登录",
            text = "正在确认 $siteLabel 登录…",
            shortCriticalText = "确认中",
            whenEpochMillis = null,
            ongoing = true,
            requestPromoted = true,
            showIndeterminateProgress = true,
            openTab = "qr",
        )
    }

    fun webQrSucceeded(site: String?): SynapseLiveUpdateSnapshot {
        val siteLabel = displaySite(site)
        return SynapseLiveUpdateSnapshot(
            kind = SynapseLiveUpdateKind.WebQrLogin,
            phase = SynapseLiveUpdatePhase.Succeeded,
            title = "网页登录已完成",
            text = "$siteLabel 已确认登录。",
            shortCriticalText = "完成",
            whenEpochMillis = null,
            ongoing = false,
            requestPromoted = false,
            alert = true,
            autoDismissAfterMs = TERMINAL_SUCCESS_DISMISS_MS,
            openTab = "session",
        )
    }

    fun webQrFailed(message: String?): SynapseLiveUpdateSnapshot {
        return SynapseLiveUpdateSnapshot(
            kind = SynapseLiveUpdateKind.WebQrLogin,
            phase = SynapseLiveUpdatePhase.Failed,
            title = "网页登录未完成",
            text = sanitizeUserFacingMessage(message, fallback = "确认失败，点开通知返回应用重试。"),
            shortCriticalText = "失败",
            whenEpochMillis = null,
            ongoing = false,
            requestPromoted = false,
            alert = true,
            autoDismissAfterMs = TERMINAL_FAILURE_DISMISS_MS,
            openTab = "qr",
        )
    }

    fun linuxDoWaiting(): SynapseLiveUpdateSnapshot {
        return SynapseLiveUpdateSnapshot(
            kind = SynapseLiveUpdateKind.LinuxDoAuth,
            phase = SynapseLiveUpdatePhase.WaitingBrowserReturn,
            title = "等待 Linux.do 返回",
            text = "请在浏览器完成授权。完成后会自动回到应用；若没有，点通知手动返回。",
            shortCriticalText = "授权中",
            whenEpochMillis = null,
            ongoing = true,
            requestPromoted = true,
            alert = true,
            openTab = "login",
        )
    }

    fun linuxDoCompleting(): SynapseLiveUpdateSnapshot {
        return SynapseLiveUpdateSnapshot(
            kind = SynapseLiveUpdateKind.LinuxDoAuth,
            phase = SynapseLiveUpdatePhase.Completing,
            title = "正在完成登录",
            text = "已收到 Linux.do 授权结果，正在完成客户端登录…",
            shortCriticalText = "完成中",
            whenEpochMillis = null,
            ongoing = true,
            requestPromoted = true,
            showIndeterminateProgress = true,
            openTab = "login",
        )
    }

    fun linuxDoSucceeded(): SynapseLiveUpdateSnapshot {
        return SynapseLiveUpdateSnapshot(
            kind = SynapseLiveUpdateKind.LinuxDoAuth,
            phase = SynapseLiveUpdatePhase.Succeeded,
            title = "Linux.do 登录完成",
            text = "授权成功，本客户端会话已更新。",
            shortCriticalText = "完成",
            whenEpochMillis = null,
            ongoing = false,
            requestPromoted = false,
            alert = true,
            autoDismissAfterMs = TERMINAL_SUCCESS_DISMISS_MS,
            openTab = "session",
        )
    }

    fun linuxDoFailed(message: String?): SynapseLiveUpdateSnapshot {
        return SynapseLiveUpdateSnapshot(
            kind = SynapseLiveUpdateKind.LinuxDoAuth,
            phase = SynapseLiveUpdatePhase.Failed,
            title = "Linux.do 未完成",
            text = sanitizeUserFacingMessage(message, fallback = "授权失败，点开通知返回应用重试。"),
            shortCriticalText = "失败",
            whenEpochMillis = null,
            ongoing = false,
            requestPromoted = false,
            alert = true,
            autoDismissAfterMs = TERMINAL_FAILURE_DISMISS_MS,
            openTab = "login",
        )
    }

    fun displaySite(site: String?): String {
        val raw = site?.trim().orEmpty()
        if (raw.isBlank()) return "网页端"
        return runCatching {
            val withScheme = if (raw.contains("://")) raw else "https://$raw"
            val host = java.net.URI(withScheme).host?.trim().orEmpty()
            host.ifBlank { raw }
        }.getOrDefault(raw)
    }

    fun sanitizeUserFacingMessage(message: String?, fallback: String, maxLength: Int = 96): String {
        val firstLine = message
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() }
            .orEmpty()
        val cleaned = firstLine
            .replace(Regex("""\b(eyJ[a-zA-Z0-9_-]{10,}\.[a-zA-Z0-9._-]{10,})\b"""), "[token]")
            .replace(Regex("""(?i)(jwt|token|password|clientLoginToken)\s*[:=]\s*\S+"""), "$1=[redacted]")
            .ifBlank { fallback }
        return if (cleaned.length <= maxLength) cleaned else cleaned.take(maxLength - 1).trimEnd() + "…"
    }

    fun chipTextForExpiry(expiresAtEpochMillis: Long?, nowEpochMillis: Long = System.currentTimeMillis()): String? {
        if (expiresAtEpochMillis == null) return null
        val remainingMs = expiresAtEpochMillis - nowEpochMillis
        if (remainingMs <= 0L) return "已过期"
        val totalSeconds = (remainingMs + 999L) / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return when {
            minutes >= 60L -> "${minutes / 60L}小时"
            minutes >= 2L -> "${minutes}分"
            minutes == 1L && seconds == 0L -> "1分"
            minutes == 1L -> "1分${seconds}秒"
            else -> "${totalSeconds}秒"
        }
    }
}
