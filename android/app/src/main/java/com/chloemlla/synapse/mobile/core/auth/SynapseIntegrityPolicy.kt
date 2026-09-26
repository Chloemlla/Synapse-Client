package com.chloemlla.synapse.mobile.core.auth

/**
 * 设备证明（服务端 P2，Play Integrity）的本地编排：什么时候去要证明、要到了怎么填进请求。
 *
 * 纯逻辑，不碰 Android API，所以能直接跑 JVM 单测。
 *
 * 一条原则贯穿全流程：**拿不到证明从不否决本次轮换**。用户设备没有 Play 服务、离线、
 * 申请失败……一律照常提交，由服务端按自己的策略决定是否降级。客户端不做本地裁决，
 * 否则一次 Google 抖动就会变成用户登不上去。
 *
 * 策略正文见服务端仓库 docs/mobile-token-risk-control.md 第 4 节。
 */
object SynapseIntegrityPolicy {
    /**
     * 服务端要求证明时才去申请。`required=false`、nonce 缺失、云端项目号缺失都返回 null，
     * 调用方据此走"不带证明"的路径。
     */
    fun proofRequest(challenge: SynapseIntegrityChallenge?): SynapseIntegrityProofRequest? {
        if (challenge == null || !challenge.required) return null
        val nonce = challenge.nonce?.takeIf { it.isNotBlank() } ?: return null
        val cloudProjectNumber = challenge.cloudProjectNumber?.takeIf { it.isNotBlank() } ?: return null
        return SynapseIntegrityProofRequest(
            nonce = nonce,
            cloudProjectNumber = cloudProjectNumber,
        )
    }

    /** 把 Google 返回的证明令牌与它对应的 nonce 配成一对；令牌为空视为没拿到。 */
    fun proof(
        request: SynapseIntegrityProofRequest,
        integrityToken: String?,
    ): SynapseIntegrityProof? {
        val token = integrityToken?.takeIf { it.isNotBlank() } ?: return null
        return SynapseIntegrityProof(
            nonce = request.nonce,
            integrityToken = token,
        )
    }
}

data class SynapseIntegrityProofRequest(
    val nonce: String,
    val cloudProjectNumber: String,
)

/** 一次可以随轮换/签发请求提交的设备证明。 */
data class SynapseIntegrityProof(
    val nonce: String,
    val integrityToken: String,
)
