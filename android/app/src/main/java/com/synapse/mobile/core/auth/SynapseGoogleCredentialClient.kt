package com.synapse.mobile.core.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android Credential Manager adapter for Sign in with Google (SIWG).
 *
 * Flow (Google SIWG docs + Happy-TTS `/api/auth/google*`):
 * 1. Load server Web Client ID from `GET /api/auth/google/config`
 * 2. Call Credential Manager with Google ID option / SIWG button option
 * 3. Extract Google ID token and POST to Happy-TTS for JWT
 */
class SynapseGoogleCredentialClient(
    context: Context,
    private val credentialManager: CredentialManager = CredentialManager.create(context.applicationContext),
) {
    /**
     * Tries authorized Google accounts first (bottom sheet), then falls back to
     * the full Sign in with Google button flow when no saved credential is ready.
     */
    suspend fun getGoogleIdToken(
        activity: Activity,
        serverClientId: String,
        filterByAuthorizedAccounts: Boolean = true,
    ): String = withContext(Dispatchers.Main.immediate) {
        require(!activity.isFinishing && !activity.isDestroyed) {
            "Activity 不可用，无法唤起 Google 登录。"
        }
        val cleanClientId = serverClientId.trim()
        require(cleanClientId.isNotBlank()) { "缺少 Google serverClientId。" }

        try {
            try {
                requestGoogleIdToken(
                    activity = activity,
                    serverClientId = cleanClientId,
                    filterByAuthorizedAccounts = filterByAuthorizedAccounts,
                    autoSelectEnabled = false,
                )
            } catch (error: NoCredentialException) {
                if (filterByAuthorizedAccounts) {
                    try {
                        requestGoogleIdToken(
                            activity = activity,
                            serverClientId = cleanClientId,
                            filterByAuthorizedAccounts = false,
                            autoSelectEnabled = false,
                        )
                    } catch (inner: NoCredentialException) {
                        requestSignInWithGoogleButton(
                            activity = activity,
                            serverClientId = cleanClientId,
                        )
                    }
                } else {
                    requestSignInWithGoogleButton(
                        activity = activity,
                        serverClientId = cleanClientId,
                    )
                }
            }
        } catch (error: GetCredentialCancellationException) {
            throw IllegalStateException("已取消 Google 登录。", error)
        } catch (error: NoCredentialException) {
            throw IllegalStateException(
                "未找到可用的 Google 账号。请确认设备已登录 Google 账号，并安装/更新 Google Play 服务。",
                error,
            )
        } catch (error: GetCredentialException) {
            throw IllegalStateException(mapGetCredentialError(error), error)
        }
    }

    private suspend fun requestGoogleIdToken(
        activity: Activity,
        serverClientId: String,
        filterByAuthorizedAccounts: Boolean,
        autoSelectEnabled: Boolean,
    ): String {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(autoSelectEnabled)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        return extractIdToken(
            credentialManager.getCredential(
                context = activity,
                request = request,
            ).credential,
        )
    }

    private suspend fun requestSignInWithGoogleButton(
        activity: Activity,
        serverClientId: String,
    ): String {
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()
        return extractIdToken(
            credentialManager.getCredential(
                context = activity,
                request = request,
            ).credential,
        )
    }

    private fun extractIdToken(credential: Credential): String {
        return when {
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                try {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken.trim()
                    require(idToken.isNotBlank()) { "Google 登录未返回有效 idToken。" }
                    idToken
                } catch (error: GoogleIdTokenParsingException) {
                    throw IllegalStateException("无法解析 Google ID Token。", error)
                }
            }
            else -> throw IllegalStateException(
                "Credential Manager 返回了非 Google ID Token 类型：${credential.type}",
            )
        }
    }

    private fun mapGetCredentialError(error: GetCredentialException): String {
        val type = error.type.orEmpty()
        val message = error.errorMessage?.toString()?.takeIf { it.isNotBlank() }
        return when {
            type.contains("CANCELED", ignoreCase = true) -> "已取消 Google 登录。"
            type.contains("NO_CREDENTIAL", ignoreCase = true) ->
                "未找到可用的 Google 账号。请确认设备已登录 Google 账号。"
            type.contains("INTERRUPTED", ignoreCase = true) -> "Google 登录被中断，请重试。"
            type.contains("PROVIDER_CONFIGURATION", ignoreCase = true) ->
                "Google 登录提供方未就绪。请安装/更新 Google Play 服务，并确认设备支持 Credential Manager。"
            type.contains("UNSUPPORTED", ignoreCase = true) ->
                "当前设备或系统不支持 Google Credential Manager 登录。"
            !message.isNullOrBlank() -> "Google 登录失败：$message"
            else -> "Google 登录失败：${error::class.java.simpleName}"
        }
    }
}