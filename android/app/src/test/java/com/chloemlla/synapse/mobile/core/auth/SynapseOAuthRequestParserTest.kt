package com.chloemlla.synapse.mobile.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SynapseOAuthRequestParserTest {
    private val challenge = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"
    private val validUri = """
        synapse://oauth/authorize?provider_origin=https%3A%2F%2Ftts.chloemlla.com&response_type=code&client_id=syn_client_demo&redirect_uri=https%3A%2F%2Fclient.example%2Fcallback&scope=openid%20profile&state=state-123&code_challenge=$challenge&code_challenge_method=S256
    """.trimIndent()

    @Test
    fun parsesAndNormalizesTrustedAuthorizationRequest() {
        val request = SynapseOAuthRequestParser.parse(validUri, "https://TTS.CHLOEMLLA.COM/")

        assertEquals("https://tts.chloemlla.com", request.providerOrigin)
        assertEquals("syn_client_demo", request.clientId)
        assertEquals(listOf("openid", "profile"), request.scopes)
        assertEquals("state-123", request.state)
        assertEquals("S256", request.codeChallengeMethod)
    }

    @Test
    fun acceptsPiliplusNativeRedirectForPiliplusClient() {
        val piliplusUri = validUri
            .replace("client_id=syn_client_demo", "client_id=piliplus")
            .replace(
                "redirect_uri=https%3A%2F%2Fclient.example%2Fcallback",
                "redirect_uri=piliplus%3A%2F%2Fsynapse-auth",
            )

        val request = SynapseOAuthRequestParser.parse(piliplusUri, "https://tts.chloemlla.com")

        assertEquals("piliplus", request.clientId)
        assertEquals("piliplus://synapse-auth", request.redirectUri)
    }

    @Test
    fun rejectsNonHttpsProviderAndNonCodeResponseTypes() {
        assertThrows(SynapseOAuthRequestException::class.java) {
            SynapseOAuthRequestParser.parse(
                validUri.replace("https%3A%2F%2Ftts.chloemlla.com", "http%3A%2F%2Ftts.chloemlla.com"),
                "https://tts.chloemlla.com",
            )
        }
        assertThrows(SynapseOAuthRequestException::class.java) {
            SynapseOAuthRequestParser.parse(validUri.replace("response_type=code", "response_type=token"), "https://tts.chloemlla.com")
        }
    }

    @Test
    fun rejectsTokenLikeIntentParameters() {
        val tokenUri = "$validUri&access_token=eyJhbGciOiJIUzI1NiJ9.payload.signature"

        assertThrows(SynapseOAuthRequestException::class.java) {
            SynapseOAuthRequestParser.parse(tokenUri, "https://tts.chloemlla.com")
        }
    }

    @Test
    fun rejectsTokenParametersNestedInRedirectUri() {
        val redirectWithToken = validUri.replace(
            "https%3A%2F%2Fclient.example%2Fcallback",
            "https%3A%2F%2Fclient.example%2Fcallback%3Faccess_token%3Dsecret",
        )

        assertThrows(SynapseOAuthRequestException::class.java) {
            SynapseOAuthRequestParser.parse(redirectWithToken, "https://tts.chloemlla.com")
        }
    }

    @Test
    fun requiresS256PkceAndState() {
        assertThrows(SynapseOAuthRequestException::class.java) {
            SynapseOAuthRequestParser.parse(validUri.replace("code_challenge_method=S256", "code_challenge_method=plain"), "https://tts.chloemlla.com")
        }
        assertThrows(SynapseOAuthRequestException::class.java) {
            SynapseOAuthRequestParser.parse(validUri.replace("state=state-123&", ""), "https://tts.chloemlla.com")
        }
    }

    @Test
    fun errorCallbackCarriesStateWithoutSensitiveValues() {
        val callback = SynapseOAuthRequestParser.safeErrorRedirectUri(
            raw = validUri,
            error = "invalid_request",
            description = "请求无效",
        )

        requireNotNull(callback)
        assertTrue(callback.contains("error=invalid_request"))
        assertTrue(callback.contains("state=state-123"))
        assertFalse(callback.contains("access_token"))
        assertFalse(callback.contains("jwt"))
    }

    @Test
    fun masksIpAddressesForSessionDisplay() {
        assertEquals("203.0.*.*", "203.0.113.42".toSensitiveIpPreview())
        assertEquals("2001:db8:…", "2001:db8:0:0:0:0:0:1".toSensitiveIpPreview())
        assertEquals("已记录", "unknown".toSensitiveIpPreview())
    }
}
