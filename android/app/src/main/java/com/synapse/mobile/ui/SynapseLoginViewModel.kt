package com.synapse.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.synapse.mobile.core.auth.LoginOutcome
import com.synapse.mobile.core.auth.SynapseAuthRepository
import com.synapse.mobile.core.auth.SynapseQrPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

class SynapseLoginViewModel(
    private val repository: SynapseAuthRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        SynapseUiState(
            deviceName = repository.defaultDeviceName(),
            deviceId = repository.deviceId(),
            credentials = repository.credentials(),
            turnstilePageBaseUrl = repository.apiOrigin(),
        ),
    )
    val state: StateFlow<SynapseUiState> = mutableState.asStateFlow()

    init {
        if (repository.revokeExpiredClientTokens()) {
            mutableState.update {
                it.copy(
                    credentials = repository.credentials(),
                    status = "检测到 SML 登录令牌已过期，已自动吊销本地令牌，请重新完成授权登录。",
                )
            }
        }
        loadTurnstileConfig()
    }

    fun selectTab(tab: SynapseTab) {
        mutableState.update { it.copy(selectedTab = tab, error = null) }
    }

    fun selectAccount(accountId: String) {
        runCatching { repository.selectAccount(accountId) }
            .onSuccess { credentials ->
                val revokedExpired = repository.revokeExpiredClientTokens()
                val refreshedCredentials = repository.credentials()
                mutableState.update {
                    it.copy(
                        credentials = refreshedCredentials,
                        status = if (revokedExpired) {
                            "已切换账号，并检测到 SML 登录令牌已过期，已自动吊销本地令牌，请重新完成授权登录。"
                        } else {
                            "已切换当前账号：${credentials.displayName ?: accountId}"
                        },
                        error = null,
                    )
                }
            }
            .onFailure { error ->
                mutableState.update {
                    it.copy(error = error.message ?: error::class.java.simpleName)
                }
            }
    }

    fun updateUsername(value: String) {
        mutableState.update {
            it.copy(
                username = value,
                pendingTwoFactorChallenge = null,
                passkeyOptions = null,
                passkeyAssertionJson = "",
                totpCode = "",
                backupCode = "",
                error = null,
            )
        }
    }

    fun updatePassword(value: String) {
        mutableState.update {
            it.copy(
                password = value,
                pendingTwoFactorChallenge = null,
                passkeyOptions = null,
                passkeyAssertionJson = "",
                totpCode = "",
                backupCode = "",
                error = null,
            )
        }
    }

    fun updateTotpCode(value: String) {
        mutableState.update { it.copy(totpCode = value, error = null) }
    }

    fun updateBackupCode(value: String) {
        mutableState.update { it.copy(backupCode = value, error = null) }
    }

    fun updatePasskeyAssertionJson(value: String) {
        mutableState.update { it.copy(passkeyAssertionJson = value, error = null) }
    }

    fun loadTurnstileConfig() {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    turnstileConfigLoading = true,
                    turnstileConfigError = null,
                    turnstileToken = "",
                    turnstileVerified = false,
                    turnstileError = false,
                )
            }
            runCatching { repository.getTurnstilePublicConfig() }
                .onSuccess { config ->
                    mutableState.update {
                        it.copy(
                            turnstileConfig = config,
                            turnstileConfigLoading = false,
                            turnstileConfigError = null,
                            turnstileToken = "",
                            turnstileVerified = false,
                            turnstileError = false,
                            turnstileWidgetKey = if (config.requiresVerification) {
                                it.turnstileWidgetKey + 1
                            } else {
                                it.turnstileWidgetKey
                            },
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            turnstileConfigLoading = false,
                            turnstileConfigError = error.message ?: "获取人机验证配置失败",
                            turnstileToken = "",
                            turnstileVerified = false,
                            turnstileError = false,
                        )
                    }
                }
        }
    }

    fun handleTurnstileVerify(token: String) {
        mutableState.update {
            it.copy(
                turnstileToken = token,
                turnstileVerified = token.isNotBlank(),
                turnstileError = false,
                error = null,
            )
        }
    }

    fun handleTurnstileExpire() {
        mutableState.update {
            it.copy(
                turnstileToken = "",
                turnstileVerified = false,
                turnstileError = false,
            )
        }
    }

    fun handleTurnstileError() {
        mutableState.update {
            it.copy(
                turnstileToken = "",
                turnstileVerified = false,
                turnstileError = true,
            )
        }
    }

    fun retryTurnstile() {
        mutableState.update { it.resetTurnstileChallenge() }
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
        mutableState.update { it.copy(selectedTab = SynapseTab.Qr, showScanner = false) }
        markScanned()
    }

    fun login() {
        val current = state.value
        if (current.username.isBlank() || current.password.isBlank()) {
            mutableState.update { it.copy(error = "请输入用户名和密码。") }
            return
        }
        if (current.turnstileConfigLoading) {
            mutableState.update { it.copy(error = "正在加载人机验证配置，请稍候。") }
            return
        }
        if (current.turnstileConfigError != null) {
            mutableState.update { it.copy(error = "请先重新加载人机验证配置。") }
            return
        }
        if (current.requiresHumanVerification && (!current.turnstileVerified || current.turnstileToken.isBlank())) {
            mutableState.update { it.copy(error = "请先完成人机验证。") }
            return
        }

        launchAction(resetTurnstileOnFailure = current.requiresHumanVerification) {
            when (
                val result = repository.standardLoginAndIssueClientToken(
                    username = current.username,
                    password = current.password,
                    deviceName = current.deviceName,
                    cfToken = current.turnstileToken.takeIf { current.requiresHumanVerification },
                )
            ) {
                is LoginOutcome.Authenticated -> {
                    mutableState.update {
                        it.resetTurnstileChallenge().copy(
                            pendingTwoFactorChallenge = null,
                            passkeyOptions = null,
                            passkeyAssertionJson = "",
                        )
                    }
                    val name = result.user?.username?.takeIf { it.isNotBlank() } ?: current.username
                    "本客户端登录成功，已签发客户端登录令牌。当前账号：$name"
                }

                is LoginOutcome.TwoFactorRequired -> {
                    mutableState.update {
                        it.resetTurnstileChallenge().copy(
                            pendingTwoFactorChallenge = result.challenge,
                            passkeyOptions = null,
                            passkeyAssertionJson = "",
                        )
                    }
                    val user = result.challenge.user?.username?.takeIf { it.isNotBlank() } ?: current.username
                    result.message ?: "登录本客户端的账号 $user 需要二次验证：${result.challenge.methodLabel}"
                }
            }
        }
    }

    fun startPasskeyAuthentication() {
        val current = state.value
        val challenge = current.pendingTwoFactorChallenge
        if (challenge == null || !challenge.methods.any { it.equals("Passkey", ignoreCase = true) }) {
            mutableState.update { it.copy(error = "当前本客户端登录没有可用的 Passkey 验证方式。") }
            return
        }
        val username = challenge.user?.username?.takeIf { it.isNotBlank() } ?: current.username
        if (username.isBlank()) {
            mutableState.update { it.copy(error = "缺少 Passkey 认证所需用户名。") }
            return
        }

        launchAction {
            val result = repository.startPasskeyAuthentication(username)
            mutableState.update { it.copy(passkeyOptions = result.options) }
            "已获取本客户端 Passkey 认证选项。"
        }
    }

    fun finishPasskeyAuthentication() {
        val current = state.value
        val challenge = current.pendingTwoFactorChallenge
        if (challenge == null || !challenge.methods.any { it.equals("Passkey", ignoreCase = true) }) {
            mutableState.update { it.copy(error = "当前本客户端登录没有可用的 Passkey 验证方式。") }
            return
        }
        val username = challenge.user?.username?.takeIf { it.isNotBlank() } ?: current.username
        if (username.isBlank()) {
            mutableState.update { it.copy(error = "缺少 Passkey 认证所需用户名。") }
            return
        }
        val assertion = runCatching { JSONObject(current.passkeyAssertionJson) }.getOrElse {
            mutableState.update { state -> state.copy(error = "Passkey assertion response JSON 格式无效。") }
            return
        }

        launchAction {
            val result = repository.finishPasskeyAuthentication(
                username = username,
                assertionResponse = assertion,
                deviceName = current.deviceName,
            )
            mutableState.update {
                it.copy(
                    pendingTwoFactorChallenge = null,
                    passkeyOptions = null,
                    passkeyAssertionJson = "",
                )
            }
            val name = result.user?.username?.takeIf { it.isNotBlank() } ?: current.username
            "Passkey 验证成功，已登录本客户端并签发客户端登录令牌。当前账号：$name"
        }
    }

    fun verifyTotp() {
        val current = state.value
        val challenge = current.pendingTwoFactorChallenge
        if (challenge == null || !challenge.methods.any { it.equals("TOTP", ignoreCase = true) }) {
            mutableState.update { it.copy(error = "当前本客户端登录没有可用的 TOTP 验证方式。") }
            return
        }
        if (current.totpCode.isBlank() && current.backupCode.isBlank()) {
            mutableState.update { it.copy(error = "请输入 TOTP 验证码或备用恢复码。") }
            return
        }

        launchAction {
            val result = repository.verifyTotpAndIssueClientToken(
                challenge = challenge,
                token = current.totpCode,
                backupCode = current.backupCode,
                deviceName = current.deviceName,
            )
            mutableState.update {
                it.copy(
                    pendingTwoFactorChallenge = null,
                    passkeyOptions = null,
                    passkeyAssertionJson = "",
                    totpCode = "",
                    backupCode = "",
                )
            }
            val name = result.user?.username?.takeIf { it.isNotBlank() } ?: current.username
            "TOTP 验证成功，已登录本客户端并签发客户端登录令牌。当前账号：$name"
        }
    }

    fun silentLogin() {
        launchAction {
            val result = repository.silentLogin()
            "本客户端自动登录成功，当前账号：${result.user.username.ifBlank { result.user.email }}"
        }
    }

    fun issueClientTokenFromJwt() {
        val current = state.value
        if (current.manualJwt.isBlank()) {
            mutableState.update { it.copy(error = "请输入用于登录本客户端的网页端或二次验证后 JWT。") }
            return
        }

        launchAction {
            repository.issueClientTokenForJwt(
                jwt = current.manualJwt,
                deviceName = current.deviceName,
            )
            "已使用 JWT 登录本客户端并签发客户端登录令牌。"
        }
    }

    fun markScanned() {
        val rawPayload = state.value.manualQrPayload
        if (rawPayload.isBlank()) {
            mutableState.update { it.copy(error = "请先扫描或粘贴网页登录二维码 payload。") }
            return
        }

        launchAction {
            val result = repository.parseAndMarkScanned(rawPayload)
            val parsed = SynapseQrPayload.parse(rawPayload)
            mutableState.update { it.copy(parsedQrPayload = parsed) }
            "已标记网页登录扫码状态：${result.status}"
        }
    }

    fun confirmQrLogin() {
        val rawPayload = state.value.manualQrPayload
        if (rawPayload.isBlank()) {
            mutableState.update { it.copy(error = "请先扫描或粘贴网页登录二维码 payload。") }
            return
        }
        val accounts = state.value.credentials.accounts
        if (accounts.size > 1) {
            mutableState.update {
                it.copy(
                    showWebLoginAccountPicker = true,
                    error = null,
                    status = "",
                )
            }
            return
        }

        confirmQrLoginWithSelectedAccount()
    }

    fun dismissWebLoginAccountPicker() {
        mutableState.update { it.copy(showWebLoginAccountPicker = false) }
    }

    fun confirmQrLoginWithAccount(accountId: String) {
        val rawPayload = state.value.manualQrPayload
        if (rawPayload.isBlank()) {
            mutableState.update {
                it.copy(
                    showWebLoginAccountPicker = false,
                    error = "请先扫描或粘贴网页登录二维码 payload。",
                )
            }
            return
        }

        runCatching { repository.selectAccount(accountId) }
            .onSuccess { credentials ->
                mutableState.update {
                    it.copy(
                        showWebLoginAccountPicker = false,
                        credentials = credentials,
                        error = null,
                        status = "",
                    )
                }
                confirmQrLoginWithSelectedAccount()
            }
            .onFailure { error ->
                mutableState.update {
                    it.copy(
                        showWebLoginAccountPicker = false,
                        credentials = repository.credentials(),
                        error = error.message ?: error::class.java.simpleName,
                    )
                }
            }
    }

    private fun confirmQrLoginWithSelectedAccount() {
        val rawPayload = state.value.manualQrPayload

        launchAction {
            val result = repository.confirmQrLogin(rawPayload)
            "网页登录确认结果：${result.status}"
        }
    }

    fun revokeClientToken() {
        launchAction {
            val revoked = repository.revokeClientLoginToken()
            if (revoked) "本客户端登录令牌已撤销。" else "服务端未返回 revoked=true。"
        }
    }

    fun clearCredentials() {
        repository.clearCredentials()
        mutableState.update {
            it.copy(
                credentials = repository.credentials(),
                pendingTwoFactorChallenge = null,
                passkeyOptions = null,
                passkeyAssertionJson = "",
                totpCode = "",
                backupCode = "",
                status = "当前账号本地凭据已清理。",
                error = null,
            )
        }
    }

    private fun launchAction(resetTurnstileOnFailure: Boolean = false, action: suspend () -> String) {
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
                        val next = if (resetTurnstileOnFailure) it.resetTurnstileChallenge() else it
                        next.copy(
                            loading = false,
                            credentials = repository.credentials(),
                            error = error.message ?: error::class.java.simpleName,
                        )
                    }
            }
        }
    }

    private fun SynapseUiState.resetTurnstileChallenge(): SynapseUiState =
        if (requiresHumanVerification) {
            copy(
                turnstileToken = "",
                turnstileVerified = false,
                turnstileError = false,
                turnstileWidgetKey = turnstileWidgetKey + 1,
            )
        } else {
            copy(
                turnstileToken = "",
                turnstileVerified = false,
                turnstileError = false,
            )
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
