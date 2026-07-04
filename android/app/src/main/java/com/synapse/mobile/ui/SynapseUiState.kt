package com.synapse.mobile.ui

import com.synapse.mobile.core.auth.StoredSynapseCredentials
import com.synapse.mobile.core.auth.SynapseQrPayload

enum class SynapseTab(val label: String) {
    Login("登录"),
    Qr("扫码"),
    Session("会话"),
}

data class SynapseUiState(
    val username: String = "",
    val password: String = "",
    val manualJwt: String = "",
    val deviceName: String = "",
    val deviceId: String = "",
    val manualQrPayload: String = "",
    val parsedQrPayload: SynapseQrPayload? = null,
    val credentials: StoredSynapseCredentials = StoredSynapseCredentials(
        jwt = null,
        clientLoginToken = null,
        userId = null,
        username = null,
        email = null,
    ),
    val loading: Boolean = false,
    val showScanner: Boolean = false,
    val status: String = "",
    val error: String? = null,
)
