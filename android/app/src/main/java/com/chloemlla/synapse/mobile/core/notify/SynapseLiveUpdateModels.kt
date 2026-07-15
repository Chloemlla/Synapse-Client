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
)

object SynapseLiveUpdateCopy {
    fun webQrWaiting(site: String?, expiresAtEpochMillis: Long?): SynapseLiveUpdateSnapshot {
        val siteLabel = site?.takeIf { it.isNotBlank() } ?: "网页端"
        return SynapseLiveUpdateSnapshot(
            kind = SynapseLiveUpdateKind.WebQrLogin,
            phase = SynapseLiveUpdatePhase.WaitingConfirm,
            title = "网页登录待确认",
            text = "已扫描 $siteLabel 登录二维码。请在应用内确认后完成网页登录。",
            shortCriticalText = "待确认",
            whenEpochMillis = expiresAtEpochMillis,
            ongoing = true,
            requestPromoted = true,
        )
    }

    fun webQrCompleting(site: String?): SynapseLiveUpdateSnapshot {
        val siteLabel = site?.takeIf { it.isNotBlank() } ?: "网页端"
        return SynapseLiveUpdateSnapshot(
            kind = SynapseLiveUpdateKind.WebQrLogin,
            phase = SynapseLiveUpdatePhase.Completing,
            title = "正在确认网页登录",
            text = "正在向 $siteLabel 提交确认…",
            shortCriticalText = "确认中",
            whenEpochMillis = null,
            ongoing = true,
            requestPromoted = true,
        )
    }

    fun webQrSucceeded(site: String?): SynapseLiveUpdateSnapshot {
        val siteLabel = site?.takeIf { it.isNotBlank() } ?: "网页端"
        return SynapseLiveUpdateSnapshot(
            kind = SynapseLiveUpdateKind.WebQrLogin,
            phase = SynapseLiveUpdatePhase.Succeeded,
            title = "网页登录已完成",
            text = "已确认 $siteLabel 网页登录。",
            shortCriticalText = "完成",
            whenEpochMillis = null,
            ongoing = false,
            requestPromoted = false,
        )
    }

    fun webQrFailed(message: String?): SynapseLiveUpdateSnapshot {
        return SynapseLiveUpdateSnapshot(
            kind = SynapseLiveUpdateKind.WebQrLogin,
            phase = SynapseLiveUpdatePhase.Failed,
            title = "网页登录确认失败",
            text = message?.takeIf { it.isNotBlank() } ?: "网页登录确认失败，请返回应用重试。",
            shortCriticalText = "失败",
            whenEpochMillis = null,
            ongoing = false,
            requestPromoted = false,
        )
    }

    fun linuxDoWaiting(): SynapseLiveUpdateSnapshot {
        return SynapseLiveUpdateSnapshot(
            kind = SynapseLiveUpdateKind.LinuxDoAuth,
            phase = SynapseLiveUpdatePhase.WaitingBrowserReturn,
            title = "等待 Linux.do 授权返回",
            text = "已打开浏览器授权页。完成后应通过 App Links 自动返回本应用。",
            shortCriticalText = "授权中",
            whenEpochMillis = null,
            ongoing = true,
            requestPromoted = true,
        )
    }

    fun linuxDoCompleting(): SynapseLiveUpdateSnapshot {
        return SynapseLiveUpdateSnapshot(
            kind = SynapseLiveUpdateKind.LinuxDoAuth,
            phase = SynapseLiveUpdatePhase.Completing,
            title = "正在完成 Linux.do 登录",
            text = "已收到授权回调，正在兑换登录票据…",
            shortCriticalText = "兑换中",
            whenEpochMillis = null,
            ongoing = true,
            requestPromoted = true,
        )
    }

    fun linuxDoSucceeded(): SynapseLiveUpdateSnapshot {
        return SynapseLiveUpdateSnapshot(
            kind = SynapseLiveUpdateKind.LinuxDoAuth,
            phase = SynapseLiveUpdatePhase.Succeeded,
            title = "Linux.do 登录完成",
            text = "授权已完成，本客户端登录令牌已更新。",
            shortCriticalText = "完成",
            whenEpochMillis = null,
            ongoing = false,
            requestPromoted = false,
        )
    }

    fun linuxDoFailed(message: String?): SynapseLiveUpdateSnapshot {
        return SynapseLiveUpdateSnapshot(
            kind = SynapseLiveUpdateKind.LinuxDoAuth,
            phase = SynapseLiveUpdatePhase.Failed,
            title = "Linux.do 登录失败",
            text = message?.takeIf { it.isNotBlank() } ?: "Linux.do 授权失败，请返回应用重试。",
            shortCriticalText = "失败",
            whenEpochMillis = null,
            ongoing = false,
            requestPromoted = false,
        )
    }
}
