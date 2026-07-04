package com.synapse.mobile.core.auth

import org.json.JSONObject

internal fun JSONObject.toStandardLoginResult(): StandardLoginResult {
    val data = optJSONObject("data")
    val userJson = optJSONObject("user") ?: data?.optJSONObject("user")
    return StandardLoginResult(
        success = optBoolean("success", optString("status") == "success"),
        token = firstString("token", "accessToken", "jwt") ?: data?.firstString("token", "accessToken", "jwt"),
        requiresTwoFactor = optBoolean("requires2FA", false) ||
            optBoolean("requiresTwoFactor", false) ||
            data?.optBoolean("requires2FA", false) == true ||
            data?.optBoolean("requiresTwoFactor", false) == true,
        twoFactorToken = firstString("twoFactorToken", "tempToken", "challengeToken")
            ?: data?.firstString("twoFactorToken", "tempToken", "challengeToken"),
        message = firstString("message") ?: optJSONObject("error")?.firstString("message"),
        user = userJson?.toSynapseUser(),
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
