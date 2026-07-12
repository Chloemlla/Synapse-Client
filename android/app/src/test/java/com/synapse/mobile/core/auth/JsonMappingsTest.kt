package com.synapse.mobile.core.auth

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun turnstilePublicConfigRequiresEnabledSiteKey() {
        val result = JSONObject(
            """
            {
              "enabled": true,
              "siteKey": "0x4AAAA-public-site-key",
              "hcaptchaEnabled": false,
              "hcaptchaSiteKey": null
            }
            """.trimIndent(),
        ).toTurnstilePublicConfig()

        assertTrue(result.requiresVerification)
        assertEquals("0x4AAAA-public-site-key", result.siteKey)
    }

    @Test
    fun turnstilePublicConfigDisablesWhenSiteKeyIsMissing() {
        val result = JSONObject(
            """
            {
              "enabled": true,
              "siteKey": ""
            }
            """.trimIndent(),
        ).toTurnstilePublicConfig()

        assertFalse(result.requiresVerification)
        assertEquals(null, result.siteKey)
    }

    @Test
    fun turnstilePublicConfigMapsNestedConfig() {
        val result = JSONObject(
            """
            {
              "config": {
                "enabled": true,
                "siteKey": "0x4AAAA-nested-site-key"
              }
            }
            """.trimIndent(),
        ).toTurnstilePublicConfig()

        assertTrue(result.requiresVerification)
        assertEquals("0x4AAAA-nested-site-key", result.siteKey)
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

    @Test
    fun totpVerificationMapsJwtToken() {
        val result = JSONObject(
            """
            {
              "message": "验证成功",
              "verified": true,
              "token": "jwt-value"
            }
            """.trimIndent(),
        ).toTotpVerificationResult()

        assertTrue(result.verified)
        assertEquals("jwt-value", result.token)
        assertEquals("验证成功", result.message)
    }

    @Test
    fun discoverablePasskeyStartMapsChallengeAndMode() {
        val result = JSONObject(
            """
            {
              "options": {
                "challenge": "discoverable-challenge",
                "rpId": "tts.chloemlla.com",
                "userVerification": "required"
              },
              "challenge": "discoverable-challenge"
            }
            """.trimIndent(),
        ).toPasskeyAuthenticationStartResult(discoverable = true)

        assertTrue(result.discoverable)
        assertTrue(result.options.discoverable)
        assertEquals("discoverable-challenge", result.challenge)
        assertTrue(result.options.hasChallenge)
        assertEquals(0, result.options.allowCredentialCount)
    }
@Test
    fun googleAuthConfigMapsSummary() {
        val result = JSONObject(
            """
            {
              "enabled": true,
              "clientIdConfigured": true,
              "clientId": "17055657909-example.apps.googleusercontent.com"
            }
            """.trimIndent(),
        ).toGoogleAuthConfig()

        assertTrue(result.enabled)
        assertTrue(result.clientIdConfigured)
        assertTrue(result.canSignIn)
        assertEquals("17055657909-example.apps.googleusercontent.com", result.clientId)
    }

    @Test
    fun googleAuthConfigDisablesWithoutClientId() {
        val result = JSONObject(
            """
            {
              "enabled": true,
              "clientIdConfigured": false,
              "clientId": ""
            }
            """.trimIndent(),
        ).toGoogleAuthConfig()

        assertFalse(result.canSignIn)
    }

    @Test
    fun googleBindSessionMapsAuthenticatedPayload() {
        val result = JSONObject(
            """
            {
              "requiresBinding": false,
              "token": "jwt-google",
              "user": {
                "id": "user-g1",
                "username": "google_user",
                "email": "user@gmail.com",
                "role": "user"
              },
              "isNewUser": false,
              "provider": "google"
            }
            """.trimIndent(),
        ).toGoogleSignInBackendResult()

        val login = result as GoogleSignInBackendResult.Authenticated
        assertEquals("jwt-google", login.login.token)
        assertEquals("google_user", login.login.user.username)
        assertEquals("google", login.login.provider)
        assertFalse(login.login.isNewUser)
    }

    @Test
    fun googleBindSessionMapsRequiresBinding() {
        val result = JSONObject(
            """
            {
              "requiresBinding": true,
              "provider": "google",
              "session": {
                "sessionToken": "bind-session-token"
              }
            }
            """.trimIndent(),
        ).toGoogleSignInBackendResult()

        val binding = result as GoogleSignInBackendResult.RequiresBinding
        assertEquals("bind-session-token", binding.session.sessionToken)
        assertEquals("google", binding.session.provider)
    }
@Test
    fun linuxDoAuthConfigMapsSummary() {
        val result = JSONObject(
            """
            {
              "enabled": true,
              "clientIdConfigured": true,
              "callbackUrl": "https://tts.chloemlla.com/api/auth/linuxdo/callback",
              "frontendCallbackUrl": "https://tts.chloemlla.com/auth/linuxdo/callback",
              "discoveryUrl": "https://connect.linux.do/.well-known/openid-configuration",
              "scopes": "openid profile email"
            }
            """.trimIndent(),
        ).toLinuxDoAuthConfig()

        assertTrue(result.enabled)
        assertTrue(result.canSignIn)
        assertEquals("https://tts.chloemlla.com/api/auth/linuxdo/callback", result.callbackUrl)
        assertEquals("openid profile email", result.scopes)
    }

    @Test
    fun linuxDoExchangeMapsTokenAndUser() {
        val result = JSONObject(
            """
            {
              "token": "jwt-linuxdo",
              "user": {
                "id": "u1",
                "username": "ld_user",
                "email": "ld@example.com",
                "role": "user"
              },
              "isNewUser": false,
              "provider": "linuxdo"
            }
            """.trimIndent(),
        ).toLinuxDoLoginResult()

        assertEquals("jwt-linuxdo", result.token)
        assertEquals("ld_user", result.user.username)
        assertEquals("linuxdo", result.provider)
        assertFalse(result.isNewUser)
    }
}
