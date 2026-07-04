package com.synapse.mobile.core.auth

import android.content.Context
import android.os.Build

class SynapseAuthRepository(
    context: Context,
    private val defaultBaseUrl: String,
    private val certificatePins: String,
    private val requireCertificatePins: Boolean,
) {
    private val credentialStore = SynapseCredentialStore(context)
    private val deviceId = SynapseDeviceId(context)

    fun credentials(): StoredSynapseCredentials = credentialStore.load()

    fun defaultDeviceName(): String =
        listOf(Build.MANUFACTURER, Build.MODEL)
            .joinToString(" ")
            .trim()
            .ifBlank { "Android device" }

    fun deviceId(): String = deviceId.getOrCreate()

    suspend fun standardLoginAndIssueClientToken(
        username: String,
        password: String,
        deviceName: String,
    ): LoginOutcome {
        val api = apiFor(defaultBaseUrl)
        val login = api.standardLogin(username.trim(), password)
        if (login.requiresTwoFactor) {
            return LoginOutcome.TwoFactorRequired(
                message = login.message,
                twoFactorToken = login.twoFactorToken,
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
        credentialStore.saveClientLoginToken(issued.clientLoginToken)

        return LoginOutcome.Authenticated(
            user = login.user,
            clientTokenExpiresAt = issued.expiresAt,
        )
    }

    suspend fun issueClientTokenForJwt(jwt: String, deviceName: String): ClientTokenIssueResult {
        val normalizedJwt = jwt.trim()
        require(normalizedJwt.isNotBlank()) { "JWT is required." }
        credentialStore.saveJwt(normalizedJwt)
        return apiFor(defaultBaseUrl)
            .issueClientToken(
                jwt = normalizedJwt,
                deviceId = deviceId.getOrCreate(),
                deviceName = deviceName.ifBlank { defaultDeviceName() },
            )
            .also { issued ->
                credentialStore.saveClientLoginToken(issued.clientLoginToken)
            }
    }

    suspend fun silentLogin(): JwtExchangeResult {
        val stored = credentialStore.load()
        val clientToken = stored.clientLoginToken
            ?: throw IllegalStateException("No client login token is stored on this device.")

        return try {
            apiFor(defaultBaseUrl)
                .exchangeClientToken(clientToken, deviceId.getOrCreate())
                .also { result ->
                    credentialStore.saveJwt(result.token, result.user)
                }
        } catch (error: SynapseApiException) {
            if (error.statusCode == 401) credentialStore.clearAll()
            throw error
        }
    }

    suspend fun parseAndMarkScanned(rawPayload: String): MobileLoginStatus {
        val payload = SynapseQrPayload.parse(rawPayload)
        require(!payload.isExpired) { "QR challenge has expired." }
        return apiFor(payload.apiBaseUrl).markScanned(payload)
    }

    suspend fun confirmQrLogin(rawPayload: String): MobileLoginStatus {
        val payload = SynapseQrPayload.parse(rawPayload)
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
        val stored = credentialStore.load()
        val jwt = stored.jwt ?: throw IllegalStateException("A JWT is required to revoke the client login token.")
        val clientToken = stored.clientLoginToken
            ?: throw IllegalStateException("No client login token is stored on this device.")

        return apiFor(defaultBaseUrl).revokeClientToken(jwt, clientToken).also { revoked ->
            if (revoked) credentialStore.clearAll()
        }
    }

    fun clearCredentials() {
        credentialStore.clearAll()
    }

    private fun apiFor(baseUrl: String): SynapseMobileLoginApi =
        SynapseMobileLoginApi(
            baseUrl = baseUrl.trim().trimEnd('/'),
            httpClient = SynapseSecureOkHttpFactory.create(
                baseUrl = baseUrl.trim().trimEnd('/'),
                certificatePins = certificatePins,
                requireCertificatePins = requireCertificatePins,
            ),
        )
}
