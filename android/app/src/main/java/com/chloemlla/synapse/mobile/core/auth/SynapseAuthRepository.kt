package com.chloemlla.synapse.mobile.core.auth

import android.net.Uri
import android.content.Context
import android.os.Build
import org.json.JSONObject

class SynapseAuthRepository(
    context: Context,
    private val defaultBaseUrl: String,
) {
    private val credentialStore = SynapseCredentialStore(context)
    private val deviceId = SynapseDeviceId(context)
    private val trustedApiOrigin = SynapseApiOriginPolicy.normalizeHttpsOrigin(defaultBaseUrl)

    fun credentials(): StoredSynapseCredentials = credentialStore.load()

    fun revokeExpiredClientTokens(): Boolean =
        credentialStore.revokeExpiredClientTokens()

    fun selectAccount(accountId: String): StoredSynapseCredentials {
        credentialStore.selectAccount(accountId)
        return credentialStore.load()
    }

    fun defaultDeviceName(): String =
        listOf(Build.MANUFACTURER, Build.MODEL)
            .joinToString(" ")
            .trim()
            .ifBlank { "Android device" }

    fun deviceId(): String = deviceId.getOrCreate()

    fun apiOrigin(): String = trustedApiOrigin

    suspend fun getTurnstilePublicConfig(): TurnstilePublicConfig =
        apiFor(trustedApiOrigin).getTurnstilePublicConfig()

    suspend fun getGoogleAuthConfig(): GoogleAuthConfig =
        apiFor(trustedApiOrigin).getGoogleAuthConfig()

    suspend fun getLinuxDoAuthConfig(): LinuxDoAuthConfig =
        apiFor(trustedApiOrigin).getLinuxDoAuthConfig()

    /**
     * Happy-TTS Linux.do OAuth start URL (server issues PKCE + redirects to connect.linux.do).
     */
    fun linuxDoStartUrl(intent: String = "login"): String {
        val cleanIntent = intent.trim().ifBlank { "login" }
        return "${trustedApiOrigin.trimEnd('/')}/api/auth/linuxdo/start?intent=${Uri.encode(cleanIntent)}"
    }

    suspend fun signInWithLinuxDoTicket(
        ticket: String,
        deviceName: String,
    ): LoginOutcome.Authenticated {
        val cleanTicket = ticket.trim()
        require(cleanTicket.isNotBlank()) { "缺少 Linux.do 登录票据。" }

        val api = apiFor(trustedApiOrigin)
        val login = api.exchangeLinuxDoTicket(cleanTicket)
        require(login.token.isNotBlank()) { "Linux.do 登录响应未包含 JWT。" }
        credentialStore.saveJwt(login.token, login.user)

        val issued = api.issueClientToken(
            jwt = login.token,
            deviceId = deviceId.getOrCreate(),
            deviceName = deviceName.ifBlank { defaultDeviceName() },
        )
        credentialStore.saveClientLoginToken(issued.clientLoginToken, issued.expiresAt)

        return LoginOutcome.Authenticated(
            user = login.user,
            clientTokenExpiresAt = issued.expiresAt,
        )
    }


    /**
     * Completes Happy-TTS Google Sign-In with an ID token from Credential Manager.
     *
     * Prefer [SynapseMobileLoginApi.googleBindSession] (web parity). If the backend
     * requires account binding that needs a web UI, fall back to
     * [SynapseMobileLoginApi.googleAuth] which upserts/creates and returns JWT.
     */
    suspend fun signInWithGoogleIdToken(
        idToken: String,
        deviceName: String,
    ): LoginOutcome.Authenticated {
        val cleanIdToken = idToken.trim()
        require(cleanIdToken.isNotBlank()) { "缺少 Google idToken。" }

        val api = apiFor(trustedApiOrigin)
        // Web parity: bind-session first. Mobile has no provider-bind UI, so
        // requiresBinding falls back to POST /api/auth/google (auto upsert).
        val login = when (val backendResult = api.googleBindSession(cleanIdToken)) {
            is GoogleSignInBackendResult.Authenticated -> backendResult.login
            is GoogleSignInBackendResult.RequiresBinding -> api.googleAuth(cleanIdToken)
        }

        require(login.token.isNotBlank()) { "Google 登录响应未包含 JWT。" }
        credentialStore.saveJwt(login.token, login.user)

        val issued = api.issueClientToken(
            jwt = login.token,
            deviceId = deviceId.getOrCreate(),
            deviceName = deviceName.ifBlank { defaultDeviceName() },
        )
        credentialStore.saveClientLoginToken(issued.clientLoginToken, issued.expiresAt)

        return LoginOutcome.Authenticated(
            user = login.user,
            clientTokenExpiresAt = issued.expiresAt,
        )
    }


    suspend fun standardLoginAndIssueClientToken(
        username: String,
        password: String,
        deviceName: String,
        cfToken: String? = null,
    ): LoginOutcome {
        val api = apiFor(trustedApiOrigin)
        val login = api.standardLogin(username.trim(), password, cfToken)
        if (login.requiresTwoFactor) {
            return LoginOutcome.TwoFactorRequired(
                message = login.message,
                challenge = PendingTwoFactorChallenge(
                    user = login.user,
                    token = login.twoFactorToken,
                    methods = login.twoFactorTypes,
                ),
            )
        }

        val jwt = login.token?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(login.message ?: "Login response did not include a JWT.")
        credentialStore.saveJwt(jwt, login.user)

        val issued = api.issueClientToken(
            jwt = jwt,
            deviceId = deviceId.getOrCreate(),
            deviceName = deviceName.ifBlank { defaultDeviceName() },
        )
        credentialStore.saveClientLoginToken(issued.clientLoginToken, issued.expiresAt)

        return LoginOutcome.Authenticated(
            user = login.user,
            clientTokenExpiresAt = issued.expiresAt,
        )
    }

    suspend fun issueClientTokenForJwt(jwt: String, deviceName: String): ClientTokenIssueResult {
        val normalizedJwt = jwt.trim()
        require(normalizedJwt.isNotBlank()) { "JWT is required." }
        val api = apiFor(trustedApiOrigin)
        val user = api.currentUser(normalizedJwt)
        credentialStore.saveJwt(normalizedJwt, user)
        return api
            .issueClientToken(
                jwt = normalizedJwt,
                deviceId = deviceId.getOrCreate(),
                deviceName = deviceName.ifBlank { defaultDeviceName() },
            )
            .also { issued ->
                credentialStore.saveClientLoginToken(issued.clientLoginToken, issued.expiresAt)
            }
    }

    suspend fun startPasskeyAuthentication(username: String): PasskeyAuthenticationStartResult {
        val normalizedUsername = username.trim()
        require(normalizedUsername.isNotBlank()) { "Username is required." }
        return apiFor(trustedApiOrigin).startPasskeyAuthentication(
            username = normalizedUsername,
            clientOrigin = trustedApiOrigin,
        )
    }

    suspend fun startDiscoverablePasskeyAuthentication(): PasskeyAuthenticationStartResult =
        apiFor(trustedApiOrigin).startDiscoverablePasskeyAuthentication(
            clientOrigin = trustedApiOrigin,
        )

    suspend fun finishPasskeyAuthentication(
        username: String,
        assertionResponse: JSONObject,
        deviceName: String,
    ): LoginOutcome.Authenticated {
        val normalizedUsername = username.trim()
        require(normalizedUsername.isNotBlank()) { "Username is required." }
        val api = apiFor(trustedApiOrigin)
        val result = api.finishPasskeyAuthentication(
            username = normalizedUsername,
            response = assertionResponse,
            clientOrigin = trustedApiOrigin,
        )
        return persistPasskeyLogin(api, result, deviceName)
    }

    suspend fun finishDiscoverablePasskeyAuthentication(
        assertionResponse: JSONObject,
        challenge: String?,
        deviceName: String,
    ): LoginOutcome.Authenticated {
        val api = apiFor(trustedApiOrigin)
        val result = api.finishDiscoverablePasskeyAuthentication(
            response = assertionResponse,
            challenge = challenge,
            clientOrigin = trustedApiOrigin,
        )
        return persistPasskeyLogin(api, result, deviceName)
    }

    private suspend fun persistPasskeyLogin(
        api: SynapseMobileLoginApi,
        result: PasskeyAuthenticationFinishResult,
        deviceName: String,
    ): LoginOutcome.Authenticated {
        require(result.token.isNotBlank()) { "Passkey response did not include a JWT." }
        credentialStore.saveJwt(result.token, result.user)

        val issued = api.issueClientToken(
            jwt = result.token,
            deviceId = deviceId.getOrCreate(),
            deviceName = deviceName.ifBlank { defaultDeviceName() },
        )
        credentialStore.saveClientLoginToken(issued.clientLoginToken, issued.expiresAt)

        return LoginOutcome.Authenticated(
            user = result.user,
            clientTokenExpiresAt = issued.expiresAt,
        )
    }

    suspend fun verifyTotpAndIssueClientToken(
        challenge: PendingTwoFactorChallenge,
        token: String?,
        backupCode: String?,
        deviceName: String,
    ): LoginOutcome.Authenticated {
        val user = challenge.user ?: throw IllegalStateException("Two-factor response did not include a user.")
        val pendingToken = challenge.token?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Two-factor response did not include a pending token.")
        val cleanToken = token?.trim().orEmpty()
        val cleanBackupCode = backupCode?.trim().orEmpty()
        require(cleanToken.isNotBlank() || cleanBackupCode.isNotBlank()) {
            "TOTP code or backup code is required."
        }

        val api = apiFor(trustedApiOrigin)
        val result = api.verifyTotp(
            userId = user.id,
            pendingToken = pendingToken,
            token = cleanToken.takeIf { it.isNotBlank() },
            backupCode = cleanBackupCode.takeIf { it.isNotBlank() },
        )
        require(result.token.isNotBlank()) {
            result.message ?: "TOTP verification response did not include a JWT."
        }
        credentialStore.saveJwt(result.token, user)

        val issued = api.issueClientToken(
            jwt = result.token,
            deviceId = deviceId.getOrCreate(),
            deviceName = deviceName.ifBlank { defaultDeviceName() },
        )
        credentialStore.saveClientLoginToken(issued.clientLoginToken, issued.expiresAt)

        return LoginOutcome.Authenticated(
            user = user,
            clientTokenExpiresAt = issued.expiresAt,
        )
    }

    suspend fun silentLogin(): JwtExchangeResult {
        require(!credentialStore.revokeExpiredClientTokens()) {
            "SML 登录令牌已过期，请重新完成授权登录。"
        }
        val stored = credentialStore.load()
        val clientToken = stored.clientLoginToken
            ?: throw IllegalStateException("No client login token is stored on this device.")

        return try {
            apiFor(trustedApiOrigin)
                .exchangeClientToken(clientToken, deviceId.getOrCreate())
                .also { result ->
                    credentialStore.saveJwt(result.token, result.user)
                }
        } catch (error: SynapseApiException) {
            if (error.statusCode == 401) credentialStore.clearCurrentAccount()
            throw error
        }
    }

    suspend fun parseAndMarkScanned(rawPayload: String): MobileLoginStatus {
        val payload = parseTrustedQrPayload(rawPayload)
        require(!payload.isExpired) { "QR challenge has expired." }
        return apiFor(payload.apiBaseUrl).markScanned(payload)
    }

    suspend fun confirmQrLogin(rawPayload: String): MobileLoginStatus {
        require(!credentialStore.revokeExpiredClientTokens()) {
            "SML 登录令牌已过期，请重新完成授权登录。"
        }
        val payload = parseTrustedQrPayload(rawPayload)
        require(!payload.isExpired) { "QR challenge has expired." }
        val stored = credentialStore.load()
        val api = apiFor(payload.apiBaseUrl)

        val jwt = stored.jwt
        if (!jwt.isNullOrBlank()) {
            try {
                return api.confirmWithJwt(payload, jwt)
            } catch (error: SynapseApiException) {
                if (error.statusCode != 401 || stored.clientLoginToken.isNullOrBlank()) throw error
                credentialStore.clearJwtOnly()
            }
        }

        val clientToken = stored.clientLoginToken
            ?: throw IllegalStateException("No local credential is available to confirm web login.")
        return api.confirmWithClientToken(
            payload = payload,
            clientLoginToken = clientToken,
            deviceId = deviceId.getOrCreate(),
        )
    }

    suspend fun revokeClientLoginToken(): Boolean {
        require(!credentialStore.revokeExpiredClientTokens()) {
            "SML 登录令牌已过期，请重新完成授权登录。"
        }
        val stored = credentialStore.load()
        val jwt = stored.jwt ?: throw IllegalStateException("A JWT is required to revoke the client login token.")
        val clientToken = stored.clientLoginToken
            ?: throw IllegalStateException("No client login token is stored on this device.")

        return apiFor(trustedApiOrigin).revokeClientToken(jwt, clientToken).also { revoked ->
            if (revoked) credentialStore.clearCurrentAccount()
        }
    }

    fun clearCredentials() {
        credentialStore.clearCurrentAccount()
    }

    private fun apiFor(baseUrl: String): SynapseMobileLoginApi =
        SynapseMobileLoginApi(
            baseUrl = SynapseApiOriginPolicy.normalizeHttpsOrigin(baseUrl),
            httpClient = SynapseSecureOkHttpFactory.create(
                baseUrl = SynapseApiOriginPolicy.normalizeHttpsOrigin(baseUrl),
            ),
        )

    private fun parseTrustedQrPayload(rawPayload: String): SynapseQrPayload {
        val payload = SynapseQrPayload.parse(rawPayload)
        SynapseApiOriginPolicy.requireTrustedOrigin(payload.apiBaseUrl, trustedApiOrigin)
        return payload
    }
}
