package com.synapse.mobile.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SynapseApiErrorFormatterTest {
    @Test
    fun failureMessageIncludesNestedValidationDetails() {
        val message = SynapseApiErrorFormatter.failureMessage(
            method = "POST",
            url = "https://tts.chloemlla.com/api/auth/login",
            statusCode = 400,
            requestFields = listOf("identifier", "password"),
            responseText = """
                {
                  "error": {
                    "message": "输入验证失败",
                    "details": [
                      { "path": ["identifier"], "message": "identifier 不能为空" },
                      { "field": "password", "message": "密码长度不能少于 8 位" }
                    ]
                  }
                }
            """.trimIndent(),
        )

        assertTrue(message.contains("输入验证失败"))
        assertTrue(message.contains("API 请求：POST https://tts.chloemlla.com/api/auth/login"))
        assertTrue(message.contains("HTTP 状态：400"))
        assertTrue(message.contains("请求字段：identifier, password"))
        assertTrue(message.contains("identifier：identifier 不能为空"))
        assertTrue(message.contains("password：密码长度不能少于 8 位"))
    }

    @Test
    fun failureMessageDoesNotEchoRequestValues() {
        val message = SynapseApiErrorFormatter.failureMessage(
            method = "POST",
            url = "https://tts.chloemlla.com/api/auth/mobile-login/client-token/exchange",
            statusCode = 401,
            requestFields = listOf("clientLoginToken", "deviceId"),
            responseText = """{ "message": "客户端登录令牌无效" }""",
        )

        assertTrue(message.contains("请求字段：clientLoginToken, deviceId"))
        assertFalse(message.contains("sml_"))
    }
}
