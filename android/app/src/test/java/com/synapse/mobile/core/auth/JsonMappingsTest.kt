package com.synapse.mobile.core.auth

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonMappingsTest {
    @Test
    fun standardLoginMapsRequiresTwoFactorResponse() {
        val result = JSONObject(
            """
            {
              "success": true,
              "requires2FA": true,
              "token": "short-two-factor-token",
              "twoFactorType": ["TOTP", "Passkey"],
              "user": {
                "id": "user-1",
                "username": "alice",
                "email": "alice@example.com",
                "role": "user"
              }
            }
            """.trimIndent(),
        ).toStandardLoginResult()

        assertTrue(result.requiresTwoFactor)
        assertEquals("short-two-factor-token", result.twoFactorToken)
        assertEquals(listOf("TOTP", "Passkey"), result.twoFactorTypes)
        assertEquals("alice", result.user?.username)
    }

    @Test
    fun passkeyStartMapsAuthenticationOptionsSummary() {
        val result = JSONObject(
            """
            {
              "options": {
                "challenge": "challenge-value",
                "rpId": "tts.chloemlla.com",
                "allowCredentials": [{ "id": "credential-1" }, { "id": "credential-2" }],
                "userVerification": "required"
              }
            }
            """.trimIndent(),
        ).toPasskeyAuthenticationStartResult()

        assertTrue(result.options.hasChallenge)
        assertEquals("tts.chloemlla.com", result.options.rpId)
        assertEquals(2, result.options.allowCredentialCount)
        assertEquals("required", result.options.userVerification)
    }

    @Test
    fun passkeyFinishMapsTokenAndUserWithoutRole() {
        val result = JSONObject(
            """
            {
              "success": true,
              "token": "jwt-value",
              "user": {
                "id": "user-1",
                "username": "alice",
                "email": "alice@example.com"
              }
            }
            """.trimIndent(),
        ).toPasskeyAuthenticationFinishResult()

        assertTrue(result.success)
        assertEquals("jwt-value", result.token)
        assertEquals("alice@example.com", result.user.email)
        assertEquals("", result.user.role)
    }
}
