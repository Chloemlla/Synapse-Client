package com.synapse.mobile.core.auth

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.toStandardLoginResult(): StandardLoginResult {
    val data = optJSONObject("data")
    val userJson = optJSONObject("user") ?: data?.optJSONObject("user")
    val token = firstString("token", "accessToken", "jwt") ?: data?.firstString("token", "accessToken", "jwt")
    val requiresTwoFactor = optBoolean("requires2FA", false) ||
        optBoolean("requiresTwoFactor", false) ||
        data?.optBoolean("requires2FA", false) == true ||
        data?.optBoolean("requiresTwoFactor", false) == true
    return StandardLoginResult(
        success = optBoolean("success", optString("status") == "success"),
        token = token,
        requiresTwoFactor = requiresTwoFactor,
        twoFactorToken = firstString("twoFactorToken", "tempToken", "challengeToken")
            ?: data?.firstString("twoFactorToken", "tempToken", "challengeToken")
            ?: token.takeIf { requiresTwoFactor },
        twoFactorTypes = firstStringList("twoFactorType", "twoFactorTypes")
            .ifEmpty { data?.firstStringList("twoFactorType", "twoFactorTypes").orEmpty() },
        message = firstString("message") ?: optJSONObject("error")?.firstString("message"),
        user = userJson?.toSynapseUser(),
    )
}

internal fun JSONObject.toTurnstilePublicConfig(): TurnstilePublicConfig {
    val configJson = optJSONObject("config") ?: optJSONObject("data")
    val siteKey = (firstString("siteKey") ?: configJson?.firstString("siteKey"))
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    val enabled = optBoolean("enabled", configJson?.optBoolean("enabled", false) == true)
    return TurnstilePublicConfig(
        enabled = enabled && !siteKey.isNullOrBlank(),
        siteKey = siteKey,
    )
}

internal fun JSONObject.toMobileLoginChallenge(): MobileLoginChallenge =
    MobileLoginChallenge(
        success = optBoolean("success"),
        sessionId = optString("sessionId"),
        pollToken = optString("pollToken"),
        qrPayload = optString("qrPayload"),
        expiresAt = optString("expiresAt"),
        pollIntervalMs = optLong("pollIntervalMs", 2_000L),
    )

internal fun JSONObject.toMobileLoginStatus(): MobileLoginStatus =
    MobileLoginStatus(
        success = optBoolean("success"),
        status = optString("status"),
        expiresAt = optString("expiresAt"),
        token = firstString("token"),
        user = optJSONObject("user")?.toSynapseUser(),
    )

internal fun JSONObject.toClientTokenIssueResult(): ClientTokenIssueResult =
    ClientTokenIssueResult(
        success = optBoolean("success"),
        clientLoginToken = optString("clientLoginToken"),
        expiresAt = optString("expiresAt"),
    )

internal fun JSONObject.toJwtExchangeResult(): JwtExchangeResult =
    JwtExchangeResult(
        success = optBoolean("success"),
        token = optString("token"),
        user = getJSONObject("user").toSynapseUser(),
    )

internal fun JSONObject.toPasskeyAuthenticationStartResult(
    discoverable: Boolean = false,
): PasskeyAuthenticationStartResult {
    val optionsJson = optJSONObject("options")
        ?: optJSONObject("data")?.optJSONObject("options")
        ?: optJSONObject("publicKey")
        ?: this.takeIf { has("challenge") || has("rpId") || has("rpID") }
        ?: JSONObject()
    val challenge = firstString("challenge")
        ?: optionsJson.firstString("challenge")
    val summarized = try {
        SynapsePasskeyJson.summarizeOptions(optionsJson.toString())
    } catch (_: Exception) {
        PasskeyAuthenticationOptions(
            rawJson = optionsJson.toString(),
            hasChallenge = optionsJson.firstString("challenge") != null,
            rpId = optionsJson.firstString("rpId", "rpID"),
            allowCredentialCount = optionsJson.optJSONArray("allowCredentials")?.length() ?: 0,
            userVerification = optionsJson.firstString("userVerification"),
            challenge = challenge,
            discoverable = discoverable,
        )
    }
    val options = summarized.copy(
        challenge = challenge ?: summarized.challenge,
        discoverable = discoverable,
        hasChallenge = (challenge ?: summarized.challenge) != null || summarized.hasChallenge,
    )
    return PasskeyAuthenticationStartResult(
        options = options,
        challenge = challenge ?: options.challenge,
        discoverable = discoverable,
    )
}

internal fun JSONObject.toPasskeyAuthenticationFinishResult(): PasskeyAuthenticationFinishResult {
    val data = optJSONObject("data")
    return PasskeyAuthenticationFinishResult(
        success = optBoolean("success", data?.optBoolean("success", false) == true),
        token = firstString("token") ?: data?.firstString("token").orEmpty(),
        user = (optJSONObject("user") ?: data?.optJSONObject("user") ?: JSONObject()).toSynapseUser(),
    )
}

internal fun JSONObject.toTotpVerificationResult(): TotpVerificationResult {
    val data = optJSONObject("data")
    return TotpVerificationResult(
        verified = optBoolean("verified", data?.optBoolean("verified", false) == true),
        token = firstString("token") ?: data?.firstString("token").orEmpty(),
        message = firstString("message") ?: data?.firstString("message"),
    )
}

internal fun JSONObject.toSynapseUser(): SynapseUser =
    SynapseUser(
        id = optString("id"),
        username = optString("username"),
        email = optString("email"),
        role = optString("role"),
    )

internal fun JSONObject.firstString(vararg names: String): String? =
    names.firstNotNullOfOrNull { name ->
        if (!has(name) || isNull(name)) return@firstNotNullOfOrNull null
        optString(name).takeIf { it.isNotBlank() }
    }

internal fun JSONObject.firstStringList(vararg names: String): List<String> =
    names.firstNotNullOfOrNull { name ->
        if (!has(name) || isNull(name)) return@firstNotNullOfOrNull null
        when (val value = opt(name)) {
            is JSONArray -> value.toStringList()
            is String -> value.split(',', '|').map { it.trim() }.filter { it.isNotBlank() }
            else -> null
        }?.takeIf { it.isNotEmpty() }
    }.orEmpty()

private fun JSONArray.toStringList(): List<String> =
    (0 until length()).mapNotNull { index ->
        optString(index).takeIf { it.isNotBlank() }
    }
