package com.synapse.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.synapse.mobile.core.auth.LoginOutcome
import com.synapse.mobile.core.auth.SynapseAuthRepository
import com.synapse.mobile.core.auth.SynapseQrPayload
import com.synapse.mobile.core.auth.toSensitiveTokenPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SynapseLoginViewModel(
    private val repository: SynapseAuthRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        SynapseUiState(
            deviceName = repository.defaultDeviceName(),
            deviceId = repository.deviceId(),
            credentials = repository.credentials(),
        ),
    )
    val state: StateFlow<SynapseUiState> = mutableState.asStateFlow()

    fun updateUsername(value: String) {
        mutableState.update { it.copy(username = value, error = null) }
    }

    fun updatePassword(value: String) {
        mutableState.update { it.copy(password = value, error = null) }
    }

    fun updateManualJwt(value: String) {
        mutableState.update { it.copy(manualJwt = value, error = null) }
    }

    fun updateDeviceName(value: String) {
        mutableState.update { it.copy(deviceName = value, error = null) }
    }

    fun updateManualQrPayload(value: String) {
        val parsedPayload = runCatching { SynapseQrPayload.parse(value) }.getOrNull()
        mutableState.update {
            it.copy(
                manualQrPayload = value,
                parsedQrPayload = parsedPayload,
                error = null,
            )
        }
    }

    fun setScannerVisible(visible: Boolean) {
        mutableState.update { it.copy(showScanner = visible, error = null) }
    }

    fun acceptScannedPayload(rawPayload: String) {
        updateManualQrPayload(rawPayload)
        mutableState.update { it.copy(showScanner = false) }
        markScanned()
    }

    fun login() {
        val current = state.value
        if (current.username.isBlank() || current.password.isBlank()) {
            mutableState.update { it.copy(error = "请输入用户名和密码。") }
            return
        }

        launchAction {
            when (
                val result = repository.standardLoginAndIssueClientToken(
                    username = current.username,
                    password = current.password,
                    deviceName = current.deviceName,
                )
            ) {
                is LoginOutcome.Authenticated -> {
                    val name = result.user?.username?.takeIf { it.isNotBlank() } ?: current.username
                    val tokenPreview = repository.credentials().clientLoginToken.toSensitiveTokenPreview()
                    "登录成功，已签发客户端登录令牌${tokenPreview?.let { "：$it" }.orEmpty()}。当前账号：$name"
                }

                is LoginOutcome.TwoFactorRequired -> {
                    result.message ?: "账号需要继续完成 TOTP 或 Passkey 二次验证。"
                }
            }
        }
    }

    fun silentLogin() {
        launchAction {
            val result = repository.silentLogin()
            "自动登录成功，当前账号：${result.user.username.ifBlank { result.user.email }}"
        }
    }

    fun issueClientTokenFromJwt() {
        val current = state.value
        if (current.manualJwt.isBlank()) {
            mutableState.update { it.copy(error = "请输入已完成二次验证后的 JWT。") }
            return
        }

        launchAction {
            val issued = repository.issueClientTokenForJwt(
                jwt = current.manualJwt,
                deviceName = current.deviceName,
            )
            val tokenPreview = issued.clientLoginToken.toSensitiveTokenPreview()
            "客户端登录令牌已签发${tokenPreview?.let { "：$it" }.orEmpty()}，过期时间：${issued.expiresAt}"
        }
    }

    fun markScanned() {
        val rawPayload = state.value.manualQrPayload
        if (rawPayload.isBlank()) {
            mutableState.update { it.copy(error = "请先扫描或粘贴二维码 payload。") }
            return
        }

        launchAction {
            val result = repository.parseAndMarkScanned(rawPayload)
            val parsed = SynapseQrPayload.parse(rawPayload)
            mutableState.update { it.copy(parsedQrPayload = parsed) }
            "已标记扫码状态：${result.status}"
        }
    }

    fun confirmQrLogin() {
        val rawPayload = state.value.manualQrPayload
        if (rawPayload.isBlank()) {
            mutableState.update { it.copy(error = "请先扫描或粘贴二维码 payload。") }
            return
        }

        launchAction {
            val result = repository.confirmQrLogin(rawPayload)
            "网页登录确认结果：${result.status}"
        }
    }

    fun revokeClientToken() {
        launchAction {
            val revoked = repository.revokeClientLoginToken()
            if (revoked) "客户端登录令牌已撤销。" else "服务端未返回 revoked=true。"
        }
    }

    fun clearCredentials() {
        repository.clearCredentials()
        mutableState.update {
            it.copy(
                credentials = repository.credentials(),
                status = "本地凭据已清理。",
                error = null,
            )
        }
    }

    private fun launchAction(action: suspend () -> String) {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null, status = "") }
            runCatching { action() }
                .onSuccess { message ->
                    mutableState.update {
                        it.copy(
                            loading = false,
                            credentials = repository.credentials(),
                            status = message,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            loading = false,
                            credentials = repository.credentials(),
                            error = error.message ?: error::class.java.simpleName,
                        )
                    }
                }
        }
    }

    class Factory(
        private val repository: SynapseAuthRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SynapseLoginViewModel::class.java)) {
                return SynapseLoginViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
