package com.chloemlla.synapse.mobile.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SynapseFailureMessageTest {
    @Test
    fun fromAppendsExceptionTypeHttpStatusAndCauseChain() {
        val root = IllegalStateException("provider not ready")
        val mid = IOException("credential manager failed", root)
        val top = SynapseApiException(
            statusCode = 503,
            message = "Google 登录失败",
        )
        top.initCause(mid)

        val message = SynapseFailureMessage.from(
            error = top,
            fallback = "登录失败",
            context = "Google SIWG",
        )

        assertTrue(message.contains("Google 登录失败"))
        assertTrue(message.contains("上下文：Google SIWG"))
        assertTrue(message.contains("异常类型：${SynapseApiException::class.java.name}"))
        assertTrue(message.contains("HTTP 状态：503"))
        assertTrue(message.contains("原因 #1：${IOException::class.java.name} - credential manager failed"))
        assertTrue(message.contains("原因 #2：${IllegalStateException::class.java.name} - provider not ready"))
    }

    @Test
    fun fromUsesFallbackWhenMessageBlank() {
        val message = SynapseFailureMessage.from(
            error = RuntimeException("   "),
            fallback = "获取配置失败",
        )

        assertTrue(message.startsWith("获取配置失败"))
        assertTrue(message.contains("异常类型：${RuntimeException::class.java.name}"))
    }

    @Test
    fun withDetailsSkipsBlankValues() {
        val message = SynapseFailureMessage.withDetails(
            summary = "Linux.do 授权失败",
            details = mapOf(
                "错误码" to "access_denied",
                "ticket" to "  ",
                "intent" to null,
            ),
        )

        assertTrue(message.contains("Linux.do 授权失败"))
        assertTrue(message.contains("错误码：access_denied"))
        assertFalse(message.contains("ticket"))
        assertFalse(message.contains("intent"))
    }
}
