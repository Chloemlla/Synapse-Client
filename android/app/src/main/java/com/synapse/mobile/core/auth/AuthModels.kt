package com.synapse.mobile.core.auth

import java.io.IOException
import java.time.Instant

data class SynapseUser(
    val id: String,
    val username: String,
    val email: String,
    val role: String,
)

data class StandardLoginResult(
    val success: Boolean,
    val token: String?,
    val requiresTwoFactor: Boolean,
    val twoFactorToken: String?,
    val message: String?,
    val user: SynapseUser?,
)

data class MobileLoginChallenge(
    val success: Boolean,
    val sessionId: String,
    val pollToken: String,
    val qrPayload: String,
    val expiresAt: String,
    val pollIntervalMs: Long,
)

data class MobileLoginStatus(
    val success: Boolean,
    val status: String,
    val expiresAt: String,
    val token: String? = null,
    val user: SynapseUser? = null,
)

data class ClientTokenIssueResult(
    val success: Boolean,
    val clientLoginToken: String,
    val expiresAt: String,
)

data class JwtExchangeResult(
    val success: Boolean,
    val token: String,
    val user: SynapseUser,
)

data class StoredSynapseCredentials(
    val jwt: String?,
    val clientLoginToken: String?,
    val userId: String?,
    val username: String?,
    val email: String?,
) {
    val hasJwt: Boolean = !jwt.isNullOrBlank()
    val hasClientLoginToken: Boolean = !clientLoginToken.isNullOrBlank()
    val clientLoginTokenPreview: String? = clientLoginToken.toSensitiveTokenPreview()
}

fun String?.toSensitiveTokenPreview(): String? {
    val token = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (token.length <= TOKEN_PREVIEW_VISIBLE_CHARS) return "已保存"
    return "${token.take(TOKEN_PREVIEW_PREFIX_CHARS)}...${token.takeLast(TOKEN_PREVIEW_SUFFIX_CHARS)}"
}

private const val TOKEN_PREVIEW_PREFIX_CHARS = 4
private const val TOKEN_PREVIEW_SUFFIX_CHARS = 6
private const val TOKEN_PREVIEW_VISIBLE_CHARS = TOKEN_PREVIEW_PREFIX_CHARS + TOKEN_PREVIEW_SUFFIX_CHARS

data class SynapseQrPayload(
    val sessionId: String,
    val scanToken: String,
    val apiBaseUrl: String,
    val expiresAt: Instant,
) {
    val isExpired: Boolean
        get() = Instant.now().isAfter(expiresAt)

    companion object {
        fun parse(raw: String): SynapseQrPayload = SynapseQrPayloadParser.parse(raw)
    }
}

class SynapseApiException(
    val statusCode: Int,
    message: String,
) : IOException(message)

sealed interface LoginOutcome {
    data class Authenticated(
        val user: SynapseUser?,
        val clientTokenExpiresAt: String,
    ) : LoginOutcome

    data class TwoFactorRequired(
        val message: String?,
        val twoFactorToken: String?,
    ) : LoginOutcome
}
