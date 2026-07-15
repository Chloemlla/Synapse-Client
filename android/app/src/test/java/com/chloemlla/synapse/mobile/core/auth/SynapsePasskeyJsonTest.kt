package com.chloemlla.synapse.mobile.core.auth

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SynapsePasskeyJsonTest {
    @Test
    fun toGetCredentialRequestJsonNormalizesHappyTtsOptions() {
        val requestJson = SynapsePasskeyJson.toGetCredentialRequestJson(
            """
            {
              "challenge": "challenge-value",
              "rpId": "tts.chloemlla.com",
              "allowCredentials": [
                { "id": "credential-1", "type": "public-key", "transports": ["internal"] }
              ],
              "userVerification": "required",
              "timeout": 60000
            }
            """.trimIndent(),
        )
        val request = JSONObject(requestJson)

        assertEquals("challenge-value", request.getString("challenge"))
        assertEquals("tts.chloemlla.com", request.getString("rpId"))
        assertEquals("required", request.getString("userVerification"))
        assertEquals(1, request.getJSONArray("allowCredentials").length())
        assertEquals("credential-1", request.getJSONArray("allowCredentials").getJSONObject(0).getString("id"))
    }

    @Test
    fun toGetCredentialRequestJsonSupportsDiscoverableEmptyAllowList() {
        val requestJson = SynapsePasskeyJson.toGetCredentialRequestJson(
            """
            {
              "challenge": "discoverable-challenge",
              "rpId": "tts.chloemlla.com",
              "userVerification": "required"
            }
            """.trimIndent(),
        )
        val request = JSONObject(requestJson)

        assertEquals("discoverable-challenge", request.getString("challenge"))
        // Empty allow list must be omitted so Credential Manager can discover credentials.
        assertFalse(request.has("allowCredentials"))
    }

    @Test
    fun toGetCredentialRequestJsonAcceptsNestedPublicKeyEnvelope() {
        val requestJson = SynapsePasskeyJson.toGetCredentialRequestJson(
            """
            {
              "publicKey": {
                "challenge": "nested-challenge",
                "rpID": "tts.chloemlla.com",
                "userVerification": "preferred"
              }
            }
            """.trimIndent(),
        )
        val request = JSONObject(requestJson)

        assertEquals("nested-challenge", request.getString("challenge"))
        assertEquals("tts.chloemlla.com", request.getString("rpId"))
        assertEquals("preferred", request.getString("userVerification"))
    }

    @Test
    fun parseAuthenticationResponseRequiresAssertionFields() {
        val response = SynapsePasskeyJson.parseAuthenticationResponse(
            """
            {
              "id": "credential-1",
              "response": {
                "clientDataJSON": "client-data",
                "authenticatorData": "auth-data",
                "signature": "signature-data"
              }
            }
            """.trimIndent(),
        )

        assertEquals("credential-1", response.getString("id"))
        assertEquals("credential-1", response.getString("rawId"))
        assertEquals("public-key", response.getString("type"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseAuthenticationResponseRejectsMissingSignature() {
        SynapsePasskeyJson.parseAuthenticationResponse(
            """
            {
              "id": "credential-1",
              "response": {
                "clientDataJSON": "client-data",
                "authenticatorData": "auth-data"
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun summarizeOptionsDoesNotExposeRawChallengeInSummary() {
        val options = SynapsePasskeyJson.summarizeOptions(
            """
            {
              "challenge": "super-secret-challenge",
              "rpId": "tts.chloemlla.com",
              "allowCredentials": [{ "id": "credential-1" }],
              "userVerification": "required"
            }
            """.trimIndent(),
        )

        assertTrue(options.hasChallenge)
        assertEquals(1, options.allowCredentialCount)
        assertFalse(options.summaryLines.any { it.contains("super-secret-challenge") })
        assertFalse(options.summaryLines.any { it.contains("credential-1") })
    }
}
