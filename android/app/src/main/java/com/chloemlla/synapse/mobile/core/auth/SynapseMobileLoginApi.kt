package com.chloemlla.synapse.mobile.core.auth

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SynapseMobileLoginApi(
    private val baseUrl: String,
    private val httpClient: OkHttpClient,
    private val clientMetadata: () -> Map<String, String> = { emptyMap() },
) {
    suspend fun standardLogin(identifier: String, password: String, cfToken: String? = null): StandardLoginResult =
        post(
            path = "/api/auth/login",
            body = JSONObject()
                .put("identifier", identifier)
                .put("password", password)
                .apply {
                    cfToken
                        ?.takeIf { it.isNotBlank() }
                        ?.let { put("cfToken", it) }
                },
        ) { it.toStandardLoginResult() }



    suspend fun getLinuxDoAuthConfig(): LinuxDoAuthConfig =
        get(
            path = "/api/auth/linuxdo/config",
        ) { it.toLinuxDoAuthConfig() }

    suspend fun getOAuthAuthorizePreview(
        request: SynapseOAuthAuthorizationRequest,
        jwt: String,
    ): SynapseOAuthAuthorizePreview =
        get(
            path = "/api/oauth/authorize/preview?${request.queryParameters().toEncodedQuery()}",
            bearerToken = jwt,
        ) { it.toSynapseOAuthAuthorizePreview() }

    suspend fun submitOAuthAuthorization(
        request: SynapseOAuthAuthorizationRequest,
        approve: Boolean,
        jwt: String,
    ): SynapseOAuthAuthorizationResult =
        post(
            path = "/api/oauth/authorize",
            bearerToken = jwt,
            body = request.queryParameters()
                .toMutableMap()
                .apply { put("approve", approve.toString()) }
                .toJsonObject(),
        ) { it.toSynapseOAuthAuthorizationResult() }

    suspend fun listDeviceSessions(jwt: String): SynapseDeviceSessions =
        get(
            path = "/api/auth/sessions",
            bearerToken = jwt,
        ) { it.toSynapseDeviceSessions() }

    suspend fun revokeDeviceSession(
        jwt: String,
        deviceKey: String,
    ): Boolean = revokeSessionTarget(
        jwt = jwt,
        id = deviceKey,
    )

    suspend fun revokeClientSessions(
        jwt: String,
        deviceKey: String,
    ): Boolean = revokeSessionTarget(
        jwt = jwt,
        id = deviceKey,
    )

    suspend fun revokeDeviceSessions(
        jwt: String,
        deviceKey: String,
    ): Boolean = revokeSessionTarget(
        jwt = jwt,
        id = deviceKey,
    )

    suspend fun exchangeLinuxDoTicket(ticket: String): LinuxDoLoginResult =
        post(
            path = "/api/auth/linuxdo/exchange",
            body = JSONObject().put("ticket", ticket),
        ) { it.toLinuxDoLoginResult() }

    /**
     * Standalone IP geolocation endpoint. Used as a fallback when the device
     * sessions endpoint does not return a valid IP location for the current
     * device. Does not require authentication.
     */
    suspend fun getIpLocation(): IpLocationResult =
        get(
            path = "/api/ip",
        ) { it.toIpLocationResult() }

    /**
     * Queries location for a specific IP address. Used as a fallback when the
     * device sessions endpoint does not return a valid IP location for a
     * non-current device. Does not require authentication.
     */
    suspend fun getIpLocationForIp(ip: String): IpLocationQueryResult =
        get(
            path = "/api/ip-location?ip=${Uri.encode(ip)}",
        ) { it.toIpLocationQueryResult() }

    suspend fun getGoogleAuthConfig(): GoogleAuthConfig =
        get(
            path = "/api/auth/google/config?client=synapse-android",
        ) { it.toGoogleAuthConfig() }

    /**
     * Happy-TTS primary web path: bind-session may return JWT immediately
     * (identity already linked) or requireBinding with a provider bind session.
     */
    suspend fun googleBindSession(idToken: String): GoogleSignInBackendResult =
        post(
            path = "/api/auth/google/bind-session",
            body = JSONObject().put("idToken", idToken),
        ) { it.toGoogleSignInBackendResult() }

    /**
     * Direct Google login that always upserts/creates account and returns JWT.
     * Used as a mobile fallback when bind-session requires web-only binding UI.
     */
    suspend fun googleAuth(idToken: String): GoogleAuthLoginResult {
        val result = post(
            path = "/api/auth/google",
            body = JSONObject().put("idToken", idToken),
        ) { it.toGoogleSignInBackendResult() }
        return when (result) {
            is GoogleSignInBackendResult.Authenticated -> result.login
            is GoogleSignInBackendResult.RequiresBinding ->
                throw IllegalStateException("Google 登录返回了绑定会话，请使用 bind-session 流程。")
        }
    }

    suspend fun getTurnstilePublicConfig(): TurnstilePublicConfig =
        get(
            path = "/api/turnstile/public-config",
        ) { it.toTurnstilePublicConfig() }

    suspend fun getAuthProvidersPublicConfig(): AuthProvidersPublicConfig =
        get(
            path = "/api/auth/providers/public-config",
        ) { it.toAuthProvidersPublicConfig() }

    suspend fun createChallenge(): MobileLoginChallenge =
        post(
            path = "/api/auth/mobile-login/challenge",
            body = JSONObject(),
        ) { it.toMobileLoginChallenge() }

    suspend fun markScanned(payload: SynapseQrPayload): MobileLoginStatus =
        post(
            path = "/api/auth/mobile-login/challenge/scan",
            body = JSONObject()
                .put("sessionId", payload.sessionId)
                .put("scanToken", payload.scanToken),
        ) { it.toMobileLoginStatus() }

    suspend fun confirmWithJwt(payload: SynapseQrPayload, jwt: String): MobileLoginStatus =
        post(
            path = "/api/auth/mobile-login/challenge/confirm",
            bearerToken = jwt,
            body = JSONObject()
                .put("sessionId", payload.sessionId)
                .put("scanToken", payload.scanToken),
        ) { it.toMobileLoginStatus() }

    suspend fun confirmWithClientToken(
        payload: SynapseQrPayload,
        clientLoginToken: String,
        deviceId: String,
    ): MobileLoginStatus =
        post(
            path = "/api/auth/mobile-login/challenge/confirm",
            body = JSONObject()
                .put("sessionId", payload.sessionId)
                .put("scanToken", payload.scanToken)
                .put("clientLoginToken", clientLoginToken)
                .put("deviceId", deviceId),
        ) { it.toMobileLoginStatus() }

    suspend fun pollChallenge(sessionId: String, pollToken: String): MobileLoginStatus =
        post(
            path = "/api/auth/mobile-login/challenge/poll",
            body = JSONObject()
                .put("sessionId", sessionId)
                .put("pollToken", pollToken),
        ) { it.toMobileLoginStatus() }

    suspend fun issueClientToken(
        jwt: String,
        deviceId: String,
        deviceName: String,
    ): ClientTokenIssueResult =
        post(
            path = "/api/auth/mobile-login/client-token/issue",
            bearerToken = jwt,
            body = JSONObject()
                .put("deviceId", deviceId)
                .put("deviceName", deviceName),
        ) { it.toClientTokenIssueResult() }

    suspend fun exchangeClientToken(clientLoginToken: String, deviceId: String): JwtExchangeResult =
        post(
            path = "/api/auth/mobile-login/client-token/exchange",
            body = JSONObject()
                .put("clientLoginToken", clientLoginToken)
                .put("deviceId", deviceId),
        ) { it.toJwtExchangeResult() }

    suspend fun currentUser(jwt: String): SynapseUser =
        get(
            path = "/api/auth/me",
            bearerToken = jwt,
        ) { it.toSynapseUser() }

    suspend fun startPasskeyAuthentication(username: String, clientOrigin: String): PasskeyAuthenticationStartResult =
        post(
            path = "/api/passkey/authenticate/start",
            body = JSONObject()
                .put("username", username)
                .put("clientOrigin", clientOrigin),
        ) { it.toPasskeyAuthenticationStartResult(discoverable = false) }

    suspend fun startDiscoverablePasskeyAuthentication(clientOrigin: String): PasskeyAuthenticationStartResult =
        post(
            path = "/api/passkey/authenticate/start/discoverable",
            body = JSONObject().put("clientOrigin", clientOrigin),
        ) { it.toPasskeyAuthenticationStartResult(discoverable = true) }

    suspend fun finishPasskeyAuthentication(
        username: String,
        response: JSONObject,
        clientOrigin: String,
    ): PasskeyAuthenticationFinishResult =
        post(
            path = "/api/passkey/authenticate/finish",
            body = JSONObject()
                .put("username", username)
                .put("response", response)
                .put("clientOrigin", clientOrigin),
        ) { it.toPasskeyAuthenticationFinishResult() }

    suspend fun finishDiscoverablePasskeyAuthentication(
        response: JSONObject,
        challenge: String?,
        clientOrigin: String,
    ): PasskeyAuthenticationFinishResult =
        post(
            path = "/api/passkey/authenticate/finish/discoverable",
            body = JSONObject()
                .put("response", response)
                .put("clientOrigin", clientOrigin)
                .apply {
                    challenge
                        ?.takeIf { it.isNotBlank() }
                        ?.let { put("challenge", it) }
                },
        ) { it.toPasskeyAuthenticationFinishResult() }

    suspend fun verifyTotp(
        userId: String,
        pendingToken: String,
        token: String?,
        backupCode: String?,
    ): TotpVerificationResult =
        post(
            path = "/api/totp/verify-token",
            body = JSONObject()
                .put("userId", userId)
                .put("pendingToken", pendingToken)
                .apply {
                    token?.takeIf { it.isNotBlank() }?.let { put("token", it) }
                    backupCode?.takeIf { it.isNotBlank() }?.let { put("backupCode", it) }
                },
        ) { it.toTotpVerificationResult() }

    suspend fun revokeClientToken(jwt: String, clientLoginToken: String): Boolean =
        post(
            path = "/api/auth/mobile-login/client-token/revoke",
            bearerToken = jwt,
            body = JSONObject().put("clientLoginToken", clientLoginToken),
        ) { it.optBoolean("revoked") }

    private suspend fun revokeSessionTarget(
        jwt: String,
        id: String,
    ): Boolean =
        post(
            path = "/api/auth/sessions/${Uri.encode(id)}/revoke",
            bearerToken = jwt,
            body = JSONObject(),
        ) {
            it.optBoolean("success") ||
                it.optBoolean("revoked") ||
                (it.has("revoked") && it.optInt("revoked", -1) >= 0)
        }

    private suspend fun <T> post(
        path: String,
        body: JSONObject,
        bearerToken: String? = null,
        parse: (JSONObject) -> T,
    ): T = withContext(Dispatchers.IO) {
        val bodyText = body.toString()
        val requestFields = body.keys().asSequence().toList()
        val request = Request.Builder()
            .url(resolveUrl(path))
            .post(bodyText.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .apply {
                clientMetadata().forEach { (name, value) -> header(name, value) }
            }
            .apply {
                bearerToken
                    ?.takeIf { it.isNotBlank() }
                    ?.let { header("Authorization", "Bearer $it") }
            }
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseText = response.body.string()
            if (!response.isSuccessful) {
                throw SynapseApiException(
                    response.code,
                    SynapseApiErrorFormatter.failureMessage(
                        method = request.method,
                        url = request.url.toString(),
                        statusCode = response.code,
                        requestFields = requestFields,
                        responseText = responseText,
                    ),
                )
            }
            parse(responseText.toJsonObject())
        }
    }

    private suspend fun <T> get(
        path: String,
        bearerToken: String? = null,
        parse: (JSONObject) -> T,
    ): T = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(resolveUrl(path))
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .apply {
                clientMetadata().forEach { (name, value) -> header(name, value) }
            }
            .apply {
                bearerToken
                    ?.takeIf { it.isNotBlank() }
                    ?.let { header("Authorization", "Bearer $it") }
            }
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseText = response.body.string()
            if (!response.isSuccessful) {
                throw SynapseApiException(
                    response.code,
                    SynapseApiErrorFormatter.failureMessage(
                        method = request.method,
                        url = request.url.toString(),
                        statusCode = response.code,
                        requestFields = emptyList(),
                        responseText = responseText,
                    ),
                )
            }
            parse(responseText.toJsonObject())
        }
    }

    private fun resolveUrl(path: String): String =
        "${baseUrl.trim().trimEnd('/')}/${path.trimStart('/')}"

    private fun Map<String, String>.toEncodedQuery(): String =
        entries.joinToString("&") { (key, value) ->
            "${Uri.encode(key)}=${Uri.encode(value)}"
        }

    private fun Map<String, String>.toJsonObject(): JSONObject =
        JSONObject().apply { forEach { (key, value) -> put(key, value) } }

    private fun String.toJsonObject(): JSONObject {
        if (isBlank()) return JSONObject()
        return JSONObject(this)
    }

    private companion object {
        private const val USER_AGENT = "Synapse-Mobile-Android"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
