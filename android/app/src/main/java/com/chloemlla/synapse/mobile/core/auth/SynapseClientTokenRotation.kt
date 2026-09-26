package com.chloemlla.synapse.mobile.core.auth

import java.time.Duration
import java.time.Instant

/** 轮换结果档位；界面按档位给一句话，不复述协议细节。 */
enum class ClientTokenRotationOutcome {
    /** 已拿到新一代令牌并覆盖本地凭据。 */
    Rotated,

    /** 还没到服务端给的时间点，什么都没做。 */
    NotScheduled,

    /** 被服务端节流或网络不通：本地已排好退避时间，稍后自动重试。 */
    Deferred,

    /** 本机登录态已失效（令牌被撤销或整条链被吊销），需要重新登录。 */
    RequiresSignIn,

    /** 本机没有可轮换的登录令牌。 */
    NoAccount,
}

/**
 * 客户端登录令牌轮换的本地判定。
 *
 * 节奏一律听服务端：轮换成功返回的 `nextRotationAt` 是唯一时间源；本对象只负责
 * “到点没到点”和失败后的退避，不自己推算“应该每天一次”。
 * 策略正文见服务端仓库 docs/mobile-token-risk-control.md。
 */
object SynapseClientTokenRotation {
    /** 服务端说太频繁后的本地退避；比服务端的最小间隔长，避免每次启动都去撞墙。 */
    private val THROTTLED_RETRY: Duration = Duration.ofMinutes(30)

    /** 网络不通之类的失败退避。 */
    private val TRANSIENT_RETRY: Duration = Duration.ofHours(6)

    /**
     * 是否该轮换。老版本存下的凭据没有 `nextRotationAt`，视为“该补一次”，
     * 轮换成功后就能拿到服务端给的时间点。
     */
    fun isDue(nextRotationAt: String?, now: Instant = Instant.now()): Boolean {
        val scheduled = SynapseTokenExpiry.parseInstant(nextRotationAt) ?: return true
        return !now.isBefore(scheduled)
    }

    /**
     * 失败后什么时候再试。返回 null 表示重试没有意义（本机登录态已经不行了）。
     *
     * @param statusCode HTTP 状态码；null 表示压根没拿到响应（网络层失败）。
     */
    fun retryAt(statusCode: Int?, now: Instant = Instant.now()): Instant? =
        when (statusCode) {
            null -> now.plus(TRANSIENT_RETRY)
            401, 403 -> null
            429 -> now.plus(THROTTLED_RETRY)
            else -> now.plus(TRANSIENT_RETRY)
        }
}
