package com.synapse.mobile.core.auth

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure JSON helpers for Happy-TTS / WebAuthn passkey payloads.
 * Keeps Credential Manager request shaping testable without Android framework APIs.
 */
object SynapsePasskeyJson {
    /**
     * Normalize server authentication options into the requestJson shape expected by
     * [androidx.credentials.GetPublicKeyCredentialOption].
     *
     * Happy-TTS returns simplewebauthn options under `{ "options": { ... } }` which the
     * API mapper already unwraps into [PasskeyAuthenticationOptions.rawJson].
     */
    fun toGetCredentialRequestJson(optionsJson: String): String {
        val root = JSONObject(optionsJson)
        val options = when {
            root.has("publicKey") && root.opt("publicKey") is JSONObject -> root.getJSONObject("publicKey")
            root.has("options") && root.opt("options") is JSONObject -> root.getJSONObject("options")
            else -> root
        }

        val challenge = options.firstString("challenge")
            ?: throw IllegalArgumentException("Passkey options missing challenge.")
        val rpId = options.firstString("rpId", "rpID")
            ?: throw IllegalArgumentException("Passkey options missing rpId.")

        val request = JSONObject()
            .put("challenge", challenge)
            .put("rpId", rpId)
            .put("timeout", options.optLong("timeout", 0L).takeIf { it > 0L } ?: 60_000L)
            .put(
                "userVerification",
                options.firstString("userVerification") ?: "required",
            )

        val allowCredentials = options.optJSONArray("allowCredentials")
        if (allowCredentials != null && allowCredentials.length() > 0) {
            request.put("allowCredentials", normalizeAllowCredentials(allowCredentials))
        } else {
            // Discoverable / conditional UI style: empty allow list lets Credential Manager discover.
            request.put("allowCredentials", JSONArray())
        }

        options.optJSONObject("extensions")?.let { request.put("extensions", it) }
        return request.toString()
    }

    fun parseAuthenticationResponse(responseJson: String): JSONObject {
        val response = JSONObject(responseJson)
        val id = response.firstString("id", "rawId")
            ?: throw IllegalArgumentException("Passkey response missing credential id.")
        if (!response.has("type") || response.isNull("type")) {
            response.put("type", "public-key")
        }
        if (!response.has("rawId") || response.isNull("rawId")) {
            response.put("rawId", id)
        }
        if (!response.has("id") || response.isNull("id")) {
            response.put("id", id)
        }
        val assertion = response.optJSONObject("response")
            ?: throw IllegalArgumentException("Passkey response missing response object.")
        if (!assertion.has("clientDataJSON") || assertion.isNull("clientDataJSON")) {
            throw IllegalArgumentException("Passkey response missing clientDataJSON.")
        }
        if (!assertion.has("authenticatorData") || assertion.isNull("authenticatorData")) {
            throw IllegalArgumentException("Passkey response missing authenticatorData.")
        }
        if (!assertion.has("signature") || assertion.isNull("signature")) {
            throw IllegalArgumentException("Passkey response missing signature.")
        }
        return response
    }

    fun summarizeOptions(optionsJson: String): PasskeyAuthenticationOptions {
        val requestJson = toGetCredentialRequestJson(optionsJson)
        val options = JSONObject(requestJson)
        return PasskeyAuthenticationOptions(
            rawJson = requestJson,
            hasChallenge = options.firstString("challenge") != null,
            rpId = options.firstString("rpId"),
            allowCredentialCount = options.optJSONArray("allowCredentials")?.length() ?: 0,
            userVerification = options.firstString("userVerification"),
        )
    }

    private fun normalizeAllowCredentials(source: JSONArray): JSONArray {
        val normalized = JSONArray()
        for (index in 0 until source.length()) {
            val item = source.optJSONObject(index) ?: continue
            val id = item.firstString("id") ?: continue
            val entry = JSONObject()
                .put("id", id)
                .put("type", item.firstString("type") ?: "public-key")
            val transports = item.optJSONArray("transports")
            if (transports != null && transports.length() > 0) {
                entry.put("transports", transports)
            }
            normalized.put(entry)
        }
        return normalized
    }
}
