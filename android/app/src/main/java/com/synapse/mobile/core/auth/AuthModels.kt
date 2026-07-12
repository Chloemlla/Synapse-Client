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
    val twoFactorTypes: List<String>,
    val message: String?,
    val user: SynapseUser?,
)

data class TurnstilePublicConfig(
    val enabled: Boolean,
    val siteKey: String?,
) {
    val requiresVerification: Boolean = enabled && !siteKey.isNullOrBlank()
}

data class PendingTwoFactorChallenge(
    val user: SynapseUser?,
    val token: String?,
    val methods: List<String>,
) {
    val tokenPreview: String? = token.toSensitiveTokenPreview()
    val methodLabel: String = methods.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "未知"
}

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

data class PasskeyAuthenticationOptions(
    val rawJson: String,
    val hasChallenge: Boolean,
    val rpId: String?,
    val allowCredentialCount: Int,
    val userVerification: String?,
    val challenge: String? = null,
    val discoverable: Boolean = false,
) {
    val summaryLines: List<String> = listOfNotNull(
        "Challenge：${if (hasChallenge) "已返回" else "未返回"}",
        rpId?.takeIf { it.isNotBlank() }?.let { "RP ID：$it" },
        if (discoverable) "模式：Discoverable（无需用户名）" else "模式：指定用户",
        "Credential 数量：$allowCredentialCount",
        userVerification?.takeIf { it.isNotBlank() }?.let { "User Verification：$it" },
    )
}

data class PasskeyAuthenticationStartResult(
    val options: PasskeyAuthenticationOptions,
    val challenge: String? = null,
    val discoverable: Boolean = false,
)

data class PasskeyAuthenticationFinishResult(
    val success: Boolean,
    val token: String,
    val user: SynapseUser,
)

data class TotpVerificationResult(
    val verified: Boolean,
    val token: String,
    val message: String?,
)

data class StoredSynapseAccount(
    val accountId: String,
    val jwt: String?,
    val clientLoginToken: String?,
    val clientLoginTokenExpiresAt: String?,
    val userId: String?,
    val username: String?,
    val email: String?,
) {
    val displayName: String = username ?: email ?: userId ?: accountId
    val hasJwt: Boolean = !jwt.isNullOrBlank()
    val hasClientLoginToken: Boolean = !clientLoginToken.isNullOrBlank()
    val clientLoginTokenPreview: String? = clientLoginToken.toSensitiveTokenPreview()
    val isClientLoginTokenExpired: Boolean =
        SynapseTokenExpiry.isExpiredAt(clientLoginTokenExpiresAt)
}

data class StoredSynapseCredentials(
    val jwt: String?,
    val clientLoginToken: String?,
    val clientLoginTokenExpiresAt: String?,
    val userId: String?,
    val username: String?,
    val email: String?,
    val activeAccountId: String?,
    val accounts: List<StoredSynapseAccount>,
) {
    val hasJwt: Boolean = !jwt.isNullOrBlank()
    val hasClientLoginToken: Boolean = !clientLoginToken.isNullOrBlank()
    val clientLoginTokenPreview: String? = clientLoginToken.toSensitiveTokenPreview()
    val activeAccount: StoredSynapseAccount? = accounts.firstOrNull { it.accountId == activeAccountId }
    val displayName: String? = username ?: email ?: userId
    val isClientLoginTokenExpired: Boolean = activeAccount?.isClientLoginTokenExpired == true
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


data class GoogleAuthConfig(
    val enabled: Boolean,
    val clientIdConfigured: Boolean,
    val clientId: String?,
) {
    val canSignIn: Boolean = enabled && clientIdConfigured && !clientId.isNullOrBlank()
}

data class GoogleAuthLoginResult(
    val token: String,
    val user: SynapseUser,
    val isNewUser: Boolean,
    val provider: String,
)

data class GoogleBindSessionRequired(
    val sessionToken: String,
    val provider: String,
)

sealed interface GoogleSignInBackendResult {
    data class Authenticated(val login: GoogleAuthLoginResult) : GoogleSignInBackendResult
    data class RequiresBinding(val session: GoogleBindSessionRequired) : GoogleSignInBackendResult
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
        val challenge: PendingTwoFactorChallenge,
    ) : LoginOutcome
}
