package com.synapse.mobile.ui

import com.synapse.mobile.core.auth.PasskeyAuthenticationOptions
import com.synapse.mobile.core.auth.PendingTwoFactorChallenge
import com.synapse.mobile.core.auth.StoredSynapseCredentials
import com.synapse.mobile.core.auth.SynapseQrPayload
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
    val pendingTwoFactorChallenge: PendingTwoFactorChallenge? = null,
    val passkeyOptions: PasskeyAuthenticationOptions? = null,
    val passkeyAssertionJson: String = "",
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
}
