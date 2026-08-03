package com.chloemlla.synapse.mobile.core.auth

import android.net.Uri

data class SynapseOAuthAuthorizationRequest(
    val providerOrigin: String,
    val responseType: String,
    val clientId: String,
    val redirectUri: String,
    val scopes: List<String>,
    val state: String,
    val codeChallenge: String,
    val codeChallengeMethod: String,
    val clientName: String? = null,
    val clientVersion: String? = null,
    val clientBuild: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val platform: String? = null,
) {
    fun queryParameters(): Map<String, String> = linkedMapOf(
        "response_type" to responseType,
        "client_id" to clientId,
        "redirect_uri" to redirectUri,
        "scope" to scopes.joinToString(" "),
        "state" to state,
        "code_challenge" to codeChallenge,
        "code_challenge_method" to codeChallengeMethod,
    )

    fun errorRedirectUri(error: String, description: String): String =
        appendCallbackParameters(
            redirectUri = redirectUri,
            parameters = linkedMapOf(
                "error" to error,
                "error_description" to description,
                "state" to state,
            ),
        )
}

data class SynapseOAuthClientSummary(
    val clientId: String,
    val name: String,
    val description: String?,
    val homepageUrl: String?,
)

data class SynapseOAuthScopeSummary(
    val key: String,
    val label: String,
    val description: String,
    val category: String,
    val identityScope: Boolean,
)

data class SynapseOAuthAuthorizePreview(
    val client: SynapseOAuthClientSummary,
    val scopes: List<String>,
    val scopeDetails: List<SynapseOAuthScopeSummary>,
    val redirectUri: String,
    val responseType: String,
    val state: String?,
    val codeChallengeMethod: String?,
    val account: SynapseUser,
)

data class SynapseOAuthAuthorizationResult(
    val success: Boolean,
    val redirectUri: String,
    val scopes: List<String>,
)

class SynapseOAuthRequestException(
    message: String,
    val safeRedirectUri: String? = null,
    val safeState: String? = null,
) : IllegalArgumentException(message)

internal fun appendCallbackParameters(
    redirectUri: String,
    parameters: Map<String, String>,
): String {
    val builder = Uri.parse(redirectUri).buildUpon()
    parameters.forEach { (name, value) ->
        builder.appendQueryParameter(name, value)
    }
    return builder.build().toString()
}

data class SynapseDeviceSession(
    val sessionId: String,
    val deviceId: String?,
    val deviceName: String,
    val clientId: String?,
    val clientName: String,
    val clientType: String,
    val ipAddress: String?,
    val ipLocation: String?,
    val userAgent: String?,
    val lastActiveAt: String?,
    val createdAt: String?,
    val isCurrentDevice: Boolean,
    val deviceKey: String? = null,
) {
    val maskedIpAddress: String
        get() = ipAddress.toSensitiveIpPreview() ?: "未返回"

    val displayClient: String
        get() = clientName.ifBlank { clientType.ifBlank { "其他客户端" } }
}

data class SynapseDeviceSessions(
    val currentDeviceId: String,
    val sessions: List<SynapseDeviceSession>,
    val currentDeviceKey: String? = null,
)

enum class SynapseSessionRevokeKind {
    SESSION,
    CLIENT,
    DEVICE,
}

data class SynapseSessionRevokeTarget(
    val kind: SynapseSessionRevokeKind,
    val id: String,
    val label: String,
) {
    val requestKey: String = "${kind.name}:$id"
}

fun String?.toSensitiveIpPreview(): String? {
    val value = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (value.contains(':')) {
        val segments = value.split(':').filter { it.isNotBlank() }
        return if (segments.size >= 2) "${segments[0]}:${segments[1]}:…" else "已记录"
    }
    val octets = value.split('.')
    return if (octets.size == 4 && octets.all { it.all(Char::isDigit) }) {
        "${octets[0]}.${octets[1]}.*.*"
    } else {
        "已记录"
    }
}
