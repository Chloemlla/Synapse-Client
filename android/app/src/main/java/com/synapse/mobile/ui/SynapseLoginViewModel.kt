package com.synapse.mobile.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.synapse.mobile.core.auth.LoginOutcome
import com.synapse.mobile.core.auth.GoogleAuthConfig
import com.synapse.mobile.core.auth.PasskeyAuthenticationOptions
import com.synapse.mobile.core.auth.SynapseAuthRepository
import com.synapse.mobile.core.auth.SynapsePasskeyJson
import com.synapse.mobile.core.auth.SynapsePasskeyCredentialClient
import com.synapse.mobile.core.auth.SynapseGoogleCredentialClient
import com.synapse.mobile.core.auth.SynapseLinuxDoCallbackParser
import com.synapse.mobile.core.auth.LinuxDoAuthConfig
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
        loadGoogleAuthConfig()
        loadLinuxDoAuthConfig()
    }

    fun selectTab(tab: SynapseTab) {
        mutableState.update { it.copy(selectedTab = tab, error = null) }
    }

    fun clearFeedback() {
        mutableState.update { it.copy(status = "", error = null) }
    }

    fun clearQrPayload() {
        mutableState.update {
            it.copy(
                manualQrPayload = "",
                parsedQrPayload = null,
                qrPayloadError = null,
                showScanner = false,
                showWebLoginAccountPicker = false,
                error = null,
                status = "已清除网页登录二维码。",
            )
        }
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
                    passkeyChallenge = null,
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
                    passkeyChallenge = null,
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
        val parseResult = value
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { SynapseQrPayload.parse(it) } }
        val parsedPayload = parseResult?.getOrNull()
        val qrPayloadError = when {
            value.isBlank() -> null
            parsedPayload == null -> parseResult?.exceptionOrNull()?.message ?: "网页登录二维码 payload 格式无效。"
            parsedPayload.isExpired -> "网页登录二维码已过期，请在网页端重新生成二维码。"
            else -> null
        }
        mutableState.update {
            it.copy(
                manualQrPayload = value,
                parsedQrPayload = parsedPayload,
                qrPayloadError = qrPayloadError,
                error = null,
            )
        }
    }

    fun setScannerVisible(visible: Boolean) {
        mutableState.update { it.copy(showScanner = visible, error = null) }
    }

    fun acceptScannedPayload(rawPayload: String) {
        updateManualQrPayload(rawPayload)
        mutableState.update {
            it.copy(
                selectedTab = SynapseTab.Qr,
                showScanner = false,
                status = if (it.hasUsableQrPayload) {
                    "已识别网页登录二维码，请核对目标站点后确认。"
                } else {
                    it.status
                },
            )
        }
        if (state.value.hasUsableQrPayload) {
            markScanned()
        }
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
                            password = "",
                            pendingTwoFactorChallenge = null,
                            passkeyOptions = null,
                            passkeyAssertionJson = "",
                    passkeyChallenge = null,
                            totpCode = "",
                            backupCode = "",
                            selectedTab = SynapseTab.Session,
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
                    passkeyChallenge = null,
                        )
                    }
                    val user = result.challenge.user?.username?.takeIf { it.isNotBlank() } ?: current.username
                    result.message ?: "登录本客户端的账号 $user 需要二次验证：${result.challenge.methodLabel}"
                }
            }
        }
    }

    fun startPasskeyAuthentication(
        activity: Activity,
        passkeyClient: SynapsePasskeyCredentialClient,
    ) {
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
            val start = repository.startPasskeyAuthentication(username)
            val options = start.options
            val pendingChallenge = start.challenge ?: options.challenge
            mutableState.update {
                it.copy(
                    passkeyOptions = options,
                    passkeyChallenge = pendingChallenge,
                    passkeyAssertionJson = "",
                    status = "正在唤起系统通行密钥…",
                )
            }
            completePasskeyAuthentication(
                activity = activity,
                passkeyClient = passkeyClient,
                options = options,
                pendingChallenge = pendingChallenge,
                username = username,
                discoverable = false,
                deviceName = current.deviceName,
            )
        }
    }

    fun startDiscoverablePasskeyAuthentication(
        activity: Activity,
        passkeyClient: SynapsePasskeyCredentialClient,
    ) {
        val current = state.value
        launchAction {
            val start = repository.startDiscoverablePasskeyAuthentication()
            val options = start.options
            val pendingChallenge = start.challenge ?: options.challenge
            mutableState.update {
                it.copy(
                    passkeyOptions = options,
                    passkeyChallenge = pendingChallenge,
                    passkeyAssertionJson = "",
                    pendingTwoFactorChallenge = null,
                    status = "正在唤起系统通行密钥…",
                )
            }
            completePasskeyAuthentication(
                activity = activity,
                passkeyClient = passkeyClient,
                options = options,
                pendingChallenge = pendingChallenge,
                username = current.username,
                discoverable = true,
                deviceName = current.deviceName,
            )
        }
    }

    fun authenticateWithPasskey(
        activity: Activity,
        passkeyClient: SynapsePasskeyCredentialClient,
    ) {
        val current = state.value
        val options = current.passkeyOptions
        if (options == null) {
            mutableState.update { it.copy(error = "请先获取 Passkey 认证选项。") }
            return
        }
        val twoFactor = current.pendingTwoFactorChallenge
        val isDiscoverable = options.discoverable || twoFactor == null
        val username = twoFactor?.user?.username?.takeIf { it.isNotBlank() } ?: current.username
        if (!isDiscoverable && username.isBlank()) {
            mutableState.update { it.copy(error = "缺少 Passkey 认证所需用户名。") }
            return
        }
        launchAction {
            completePasskeyAuthentication(
                activity = activity,
                passkeyClient = passkeyClient,
                options = options,
                pendingChallenge = current.passkeyChallenge ?: options.challenge,
                username = username,
                discoverable = isDiscoverable,
                deviceName = current.deviceName,
            )
        }
    }

    private suspend fun completePasskeyAuthentication(
        activity: Activity,
        passkeyClient: SynapsePasskeyCredentialClient,
        options: PasskeyAuthenticationOptions,
        pendingChallenge: String?,
        username: String,
        discoverable: Boolean,
        deviceName: String,
    ): String {
        val assertion = passkeyClient.getAuthenticationAssertion(
            activity = activity,
            optionsJson = options.rawJson,
        )
        val result = if (discoverable) {
            repository.finishDiscoverablePasskeyAuthentication(
                assertionResponse = assertion,
                challenge = pendingChallenge,
                deviceName = deviceName,
            )
        } else {
            repository.finishPasskeyAuthentication(
                username = username,
                assertionResponse = assertion,
                deviceName = deviceName,
            )
        }
        mutableState.update {
            it.copy(
                password = "",
                pendingTwoFactorChallenge = null,
                passkeyOptions = null,
                passkeyChallenge = null,
                passkeyAssertionJson = "",
                totpCode = "",
                backupCode = "",
                selectedTab = SynapseTab.Session,
            )
        }
        val name = result.user?.username?.takeIf { it.isNotBlank() } ?: username
        return "Passkey 验证成功，已登录本客户端并签发客户端登录令牌。当前账号：$name"
    }

    fun finishPasskeyAuthentication() {
        // Compatibility entry for manual assertion JSON (debug/fallback only).
        val current = state.value
        val options = current.passkeyOptions
        val isDiscoverable = options?.discoverable == true || current.pendingTwoFactorChallenge == null
        val challenge = current.pendingTwoFactorChallenge
        if (!isDiscoverable && (challenge == null || !challenge.methods.any { it.equals("Passkey", ignoreCase = true) })) {
            mutableState.update { it.copy(error = "当前本客户端登录没有可用的 Passkey 验证方式。") }
            return
        }
        val username = challenge?.user?.username?.takeIf { it.isNotBlank() } ?: current.username
        if (!isDiscoverable && username.isBlank()) {
            mutableState.update { it.copy(error = "缺少 Passkey 认证所需用户名。") }
            return
        }
        val assertion = runCatching {
            SynapsePasskeyJson.parseAuthenticationResponse(current.passkeyAssertionJson)
        }.getOrElse {
            mutableState.update { state -> state.copy(error = it.message ?: "Passkey assertion response JSON 格式无效。") }
            return
        }
        launchAction {
            val result = if (isDiscoverable) {
                repository.finishDiscoverablePasskeyAuthentication(
                    assertionResponse = assertion,
                    challenge = current.passkeyChallenge ?: options?.challenge,
                    deviceName = current.deviceName,
                )
            } else {
                repository.finishPasskeyAuthentication(
                    username = username,
                    assertionResponse = assertion,
                    deviceName = current.deviceName,
                )
            }
            mutableState.update {
                it.copy(
                    password = "",
                    pendingTwoFactorChallenge = null,
                    passkeyOptions = null,
                    passkeyChallenge = null,
                    passkeyAssertionJson = "",
                    totpCode = "",
                    backupCode = "",
                    selectedTab = SynapseTab.Session,
                )
            }
            val name = result.user?.username?.takeIf { it.isNotBlank() } ?: username
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
                    password = "",
                    pendingTwoFactorChallenge = null,
                    passkeyOptions = null,
                    passkeyAssertionJson = "",
                    passkeyChallenge = null,
                    totpCode = "",
                    backupCode = "",
                    selectedTab = SynapseTab.Session,
                )
            }
            val name = result.user?.username?.takeIf { it.isNotBlank() } ?: current.username
            "TOTP 验证成功，已登录本客户端并签发客户端登录令牌。当前账号：$name"
        }
    }



    fun loadLinuxDoAuthConfig() {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    linuxDoAuthConfigLoading = true,
                    linuxDoAuthConfigError = null,
                )
            }
            runCatching { repository.getLinuxDoAuthConfig() }
                .onSuccess { config ->
                    mutableState.update {
                        it.copy(
                            linuxDoAuthConfig = config,
                            linuxDoAuthConfigLoading = false,
                            linuxDoAuthConfigError = null,
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            linuxDoAuthConfig = LinuxDoAuthConfig(
                                enabled = false,
                                clientIdConfigured = false,
                                callbackUrl = null,
                                frontendCallbackUrl = null,
                                discoveryUrl = null,
                                scopes = null,
                            ),
                            linuxDoAuthConfigLoading = false,
                            linuxDoAuthConfigError = error.message ?: error::class.java.simpleName,
                        )
                    }
                }
        }
    }

    fun linuxDoStartUrl(intent: String = "login"): String = repository.linuxDoStartUrl(intent)

    fun reportLinuxDoBrowserOpenFailed(message: String) {
        mutableState.update {
            it.copy(
                error = message.ifBlank { "无法打开浏览器进行 Linux.do 授权。" },
                status = "",
            )
        }
    }

    fun markLinuxDoBrowserOpened() {
        mutableState.update {
            it.copy(
                linuxDoBrowserOpened = true,
                selectedTab = SynapseTab.Login,
                status = "已打开 Linux.do 授权页。完成授权后请回到本应用；若系统未自动回调，可粘贴回调链接或登录票据。",
                error = null,
            )
        }
    }

    fun handleIncomingUri(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return
        if (SynapseLinuxDoCallbackParser.isLinuxDoRelated(trimmed)) {
            completeLinuxDoFromCallback(trimmed)
            return
        }
        acceptScannedPayload(trimmed)
    }

    fun completeLinuxDoFromCallback(raw: String) {
        val payload = try {
            SynapseLinuxDoCallbackParser.parse(raw)
        } catch (error: Exception) {
            mutableState.update {
                it.copy(
                    selectedTab = SynapseTab.Login,
                    error = error.message ?: "无法解析 Linux.do 回调。",
                )
            }
            return
        }

        if (!payload.error.isNullOrBlank()) {
            mutableState.update {
                it.copy(
                    selectedTab = SynapseTab.Login,
                    linuxDoBrowserOpened = false,
                    error = "Linux.do 授权失败：${payload.error}",
                    status = "",
                )
            }
            return
        }

        if (payload.isBindFlow) {
            mutableState.update {
                it.copy(
                    selectedTab = SynapseTab.Login,
                    linuxDoBrowserOpened = false,
                    error = "该 Linux.do 账号尚未绑定本地账号。请先在网页端完成绑定后再使用 App 登录。",
                    status = "",
                )
            }
            return
        }

        val ticket = payload.ticket
        if (ticket.isNullOrBlank()) {
            mutableState.update {
                it.copy(
                    selectedTab = SynapseTab.Login,
                    error = "Linux.do 回调缺少登录票据 ticket。",
                )
            }
            return
        }

        exchangeLinuxDoTicket(ticket)
    }

    fun exchangeLinuxDoTicket(ticket: String) {
        launchAction {
            val outcome = repository.signInWithLinuxDoTicket(
                ticket = ticket,
                deviceName = mutableState.value.deviceName,
            )
            mutableState.update {
                it.copy(
                    credentials = repository.credentials(),
                    pendingTwoFactorChallenge = null,
                    passkeyOptions = null,
                    passkeyChallenge = null,
                    passkeyAssertionJson = "",
                    password = "",
                    linuxDoBrowserOpened = false,
                )
            }
            val name = outcome.user?.username ?: outcome.user?.email ?: "当前账号"
            "Linux.do 登录成功，已登录本客户端并签发客户端登录令牌。当前账号：$name"
        }
    }

    fun loadGoogleAuthConfig() {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    googleAuthConfigLoading = true,
                    googleAuthConfigError = null,
                )
            }
            runCatching { repository.getGoogleAuthConfig() }
                .onSuccess { config ->
                    mutableState.update {
                        it.copy(
                            googleAuthConfig = config,
                            googleAuthConfigLoading = false,
                            googleAuthConfigError = null,
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            googleAuthConfig = GoogleAuthConfig(
                                enabled = false,
                                clientIdConfigured = false,
                                clientId = null,
                            ),
                            googleAuthConfigLoading = false,
                            googleAuthConfigError = error.message ?: error::class.java.simpleName,
                        )
                    }
                }
        }
    }

    fun signInWithGoogle(
        activity: Activity,
        googleClient: SynapseGoogleCredentialClient,
    ) {
        launchAction {
            val config = mutableState.value.googleAuthConfig
            val clientId = config.clientId?.takeIf { it.isNotBlank() }
            if (!config.canSignIn || clientId == null) {
                // Refresh once in case config was not loaded yet.
                val refreshed = repository.getGoogleAuthConfig()
                mutableState.update {
                    it.copy(
                        googleAuthConfig = refreshed,
                        googleAuthConfigLoading = false,
                        googleAuthConfigError = null,
                    )
                }
                if (!refreshed.canSignIn || refreshed.clientId.isNullOrBlank()) {
                    throw IllegalStateException(
                        refreshed.let { cfg ->
                            when {
                                !cfg.enabled -> "当前服务端未启用 Google 登录。"
                                !cfg.clientIdConfigured || cfg.clientId.isNullOrBlank() -> "服务端未配置 Google Client ID。"
                                else -> "Google 登录不可用。"
                            }
                        },
                    )
                }
            }
            val serverClientId = mutableState.value.googleAuthConfig.clientId
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("缺少 Google Client ID。")
            val idToken = googleClient.getGoogleIdToken(
                activity = activity,
                serverClientId = serverClientId,
            )
            val outcome = repository.signInWithGoogleIdToken(
                idToken = idToken,
                deviceName = mutableState.value.deviceName,
            )
            mutableState.update {
                it.copy(
                    credentials = repository.credentials(),
                    pendingTwoFactorChallenge = null,
                    passkeyOptions = null,
                    passkeyChallenge = null,
                    passkeyAssertionJson = "",
                    password = "",
                )
            }
            val name = outcome.user?.username ?: outcome.user?.email ?: "当前账号"
            "Google 登录成功，已登录本客户端并签发客户端登录令牌。当前账号：$name"
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
            mutableState.update {
                it.copy(
                    manualJwt = "",
                    selectedTab = SynapseTab.Session,
                )
            }
            "已使用 JWT 登录本客户端并签发客户端登录令牌。"
        }
    }

    fun markScanned() {
        val current = state.value
        if (!ensureQrPayloadReady(current)) return
        val rawPayload = current.manualQrPayload
        launchAction {
            val result = repository.parseAndMarkScanned(rawPayload)
            val parsed = SynapseQrPayload.parse(rawPayload)
            mutableState.update { it.copy(parsedQrPayload = parsed) }
            "已标记网页登录扫码状态：${result.status}"
        }
    }

    fun confirmQrLogin() {
        val current = state.value
        if (!ensureQrPayloadReady(current)) return
        if (!current.hasAnyWebLoginCredential) {
            mutableState.update {
                it.copy(error = "请先登录本客户端并签发令牌，再继续网页登录。")
            }
            return
        }
        val accounts = current.credentials.accounts
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
        val current = state.value
        if (!ensureQrPayloadReady(current)) {
            mutableState.update {
                it.copy(
                    showWebLoginAccountPicker = false,
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
            val site = state.value.parsedQrPayload?.apiBaseUrl
            if (site.isNullOrBlank()) {
                "网页登录已确认：${result.status}"
            } else {
                "已确认登录网页端：$site（${result.status}）"
            }
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
                    passkeyChallenge = null,
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

    private fun ensureQrPayloadReady(current: SynapseUiState): Boolean {
        if (current.manualQrPayload.isBlank()) {
            mutableState.update { it.copy(error = "请先扫描或粘贴网页登录二维码 payload。") }
            return false
        }
        val payload = current.parsedQrPayload
        if (payload == null || current.qrPayloadError != null) {
            mutableState.update {
                it.copy(error = current.qrPayloadError ?: "网页登录二维码 payload 格式无效。")
            }
            return false
        }
        if (payload.isExpired) {
            mutableState.update {
                it.copy(error = "网页登录二维码已过期，请在网页端重新生成二维码。")
            }
            return false
        }
        return true
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
