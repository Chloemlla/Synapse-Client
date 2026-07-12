package com.synapse.mobile.ui

import com.synapse.mobile.core.auth.PasskeyAuthenticationOptions
import com.synapse.mobile.core.auth.PendingTwoFactorChallenge
import com.synapse.mobile.core.auth.StoredSynapseCredentials
import com.synapse.mobile.core.auth.SynapseQrPayload
import com.synapse.mobile.core.auth.GoogleAuthConfig
import com.synapse.mobile.core.auth.LinuxDoAuthConfig
import com.synapse.mobile.core.auth.TurnstilePublicConfig

enum class SynapseTab(val label: String) {
    Login("本客户端登录"),
    Qr("网页登录"),
    Session("本地会话"),
}

data class SynapseUiState(
    val selectedTab: SynapseTab = SynapseTab.Login,
    val username: String = "",
    val password: String = "",
    val totpCode: String = "",
    val backupCode: String = "",
    val manualJwt: String = "",
    val deviceName: String = "",
    val deviceId: String = "",
    val manualQrPayload: String = "",
    val parsedQrPayload: SynapseQrPayload? = null,
    val qrPayloadError: String? = null,
    val pendingTwoFactorChallenge: PendingTwoFactorChallenge? = null,
    val passkeyOptions: PasskeyAuthenticationOptions? = null,
    val passkeyChallenge: String? = null,
    val passkeyAssertionJson: String = "",
    val googleAuthConfig: GoogleAuthConfig = GoogleAuthConfig(enabled = false, clientIdConfigured = false, clientId = null),
    val googleAuthConfigLoading: Boolean = true,
    val googleAuthConfigError: String? = null,
    val linuxDoAuthConfig: LinuxDoAuthConfig = LinuxDoAuthConfig(enabled = false, clientIdConfigured = false, callbackUrl = null, frontendCallbackUrl = null, discoveryUrl = null, scopes = null),
    val linuxDoAuthConfigLoading: Boolean = true,
    val linuxDoAuthConfigError: String? = null,
    val linuxDoBrowserOpened: Boolean = false,
    val turnstileConfig: TurnstilePublicConfig = TurnstilePublicConfig(enabled = false, siteKey = null),
    val turnstileConfigLoading: Boolean = true,
    val turnstileConfigError: String? = null,
    val turnstileToken: String = "",
    val turnstileVerified: Boolean = false,
    val turnstileError: Boolean = false,
    val turnstileWidgetKey: Int = 0,
    val turnstilePageBaseUrl: String = "",
    val credentials: StoredSynapseCredentials = StoredSynapseCredentials(
        jwt = null,
        clientLoginToken = null,
        clientLoginTokenExpiresAt = null,
        userId = null,
        username = null,
        email = null,
        activeAccountId = null,
        accounts = emptyList(),
    ),
    val loading: Boolean = false,
    val showScanner: Boolean = false,
    val showWebLoginAccountPicker: Boolean = false,
    val status: String = "",
    val error: String? = null,
) {
    val requiresHumanVerification: Boolean = turnstileConfig.requiresVerification
    val hasUsableQrPayload: Boolean = parsedQrPayload != null && !parsedQrPayload.isExpired && qrPayloadError == null
    val hasAnyWebLoginCredential: Boolean =
        credentials.accounts.any { it.hasJwt || it.hasClientLoginToken } ||
            credentials.hasJwt ||
            credentials.hasClientLoginToken
    val hasCurrentClientLoginToken: Boolean =
        credentials.activeAccount?.hasClientLoginToken ?: credentials.hasClientLoginToken
    val hasStoredAccount: Boolean =
        credentials.accounts.isNotEmpty() || credentials.hasJwt || credentials.hasClientLoginToken
}
