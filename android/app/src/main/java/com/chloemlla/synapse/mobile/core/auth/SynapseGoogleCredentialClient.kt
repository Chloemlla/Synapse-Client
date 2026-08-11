package com.chloemlla.synapse.mobile.core.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

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
     * Tries authorized Google accounts first (bottom sheet), then widens to all
     * device Google accounts, then falls back to the full Sign in with Google
     * button flow, and finally attempts a browser-based GoogleSignInClient
     * fallback. Account reauth failures (code 16) are treated as recoverable
     * for earlier steps so a stale authorized account does not hard-fail SIWG.
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

        val steps = buildList<suspend () -> String> {
            if (filterByAuthorizedAccounts) {
                add {
                    requestGoogleIdToken(
                        activity = activity,
                        serverClientId = cleanClientId,
                        filterByAuthorizedAccounts = true,
                        autoSelectEnabled = false,
                    )
                }
            }
            add {
                requestGoogleIdToken(
                    activity = activity,
                    serverClientId = cleanClientId,
                    filterByAuthorizedAccounts = false,
                    autoSelectEnabled = false,
                )
            }
            add {
                requestSignInWithGoogleButton(
                    activity = activity,
                    serverClientId = cleanClientId,
                )
            }
            // Browser-based fallback: GoogleSignInClient bypasses Credential
            // Manager entirely, so it is not affected by account reauth failures
            // (code 16) that block all Credential Manager paths.
            add {
                requestGoogleSignInClientFallback(
                    activity = activity,
                    serverClientId = cleanClientId,
                )
            }
        }

        var lastRecoverableError: GetCredentialException? = null
        for ((index, requestStep) in steps.withIndex()) {
            val hasRemainingFallback = index < steps.lastIndex
            try {
                return@withContext requestStep()
            } catch (error: NoCredentialException) {
                lastRecoverableError = error
                if (!hasRemainingFallback) {
                    throw IllegalStateException(
                        "未找到可用的 Google 账号。请确认设备已登录 Google 账号，并安装/更新 Google Play 服务。",
                        error,
                    )
                }
            } catch (error: GetCredentialCancellationException) {
                val systemMessage = error.errorMessage?.toString()
                if (
                    SynapseCredentialErrorMapper.shouldRetryAfterCancellation(
                        systemMessage = systemMessage,
                        hasRemainingFallback = hasRemainingFallback,
                    )
                ) {
                    lastRecoverableError = error
                    continue
                }
                throw IllegalStateException(mapCancellationError(error, actionLabel = "Google 登录"), error)
            } catch (error: GetCredentialException) {
                throw IllegalStateException(mapGetCredentialError(error), error)
            }
        }

        // Defensive: loop always returns or throws when steps is non-empty.
        val fallbackError = lastRecoverableError
        if (fallbackError is GetCredentialCancellationException) {
            throw IllegalStateException(
                mapCancellationError(fallbackError, actionLabel = "Google 登录"),
                fallbackError,
            )
        }
        if (fallbackError is NoCredentialException) {
            throw IllegalStateException(
                "未找到可用的 Google 账号。请确认设备已登录 Google 账号，并安装/更新 Google Play 服务。",
                fallbackError,
            )
        }
        throw IllegalStateException("Google 登录失败：未返回凭据。", fallbackError)
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

    /**
     * Browser-based fallback using GoogleSignInClient. Uses silentSignIn() to
     * attempt a non-interactive token refresh. GoogleSignInClient bypasses the
     * Credential Manager entirely, so it is not affected by account reauth
     * failures (code 16) that block all Credential Manager paths.
     *
     * If silentSignIn() fails (e.g. the account needs interactive re-auth),
     * the error message guides the user to re-add their Google account in
     * system settings, which is the only reliable client-side fix.
     *
     * GoogleSignIn and GoogleSignInOptions are deprecated in the latest Play
     * Services but remain the only reliable fallback when Credential Manager
     * cannot recover from account reauth failures (code 16).
     */
    @Suppress("DEPRECATION")
    private suspend fun requestGoogleSignInClientFallback(
        activity: Activity,
        serverClientId: String,
    ): String = withContext(Dispatchers.IO) {
        val gso = GoogleSignInOptions.Builder()
            .requestIdToken(serverClientId)
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(activity, gso)

        val account = try {
            Tasks.await(googleSignInClient.silentSignIn(), 30, TimeUnit.SECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            throw IllegalStateException(
                "Google 登录超时。请检查网络连接后重试。",
                e,
            )
        } catch (e: Exception) {
            // Interactive sign-in via getSignInIntent() would require the
            // ActivityResultLauncher pattern, which is not available in this
            // context. Instead, guide the user to the system-level fix.
            throw IllegalStateException(
                "Google 登录需要重新验证账号。请前往系统设置 → Google → 管理账号，\n" +
                    "移除并重新添加此 Google 账号，然后重新尝试登录。\n" +
                    "（异常：${e.message?.take(120) ?: e::class.java.simpleName}）",
                e,
            )
        }
        val idToken = account.idToken?.trim()
        require(!idToken.isNullOrBlank()) { "Google Sign-In 未返回有效 idToken。" }
        idToken
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

    private fun mapCancellationError(
        error: GetCredentialCancellationException,
        actionLabel: String,
    ): String {
        val type = error.type.orEmpty()
        val message = error.errorMessage?.toString()?.takeIf { it.isNotBlank() }
        return SynapseFailureMessage.withDetails(
            summary = SynapseCredentialErrorMapper.cancellationSummary(
                systemMessage = message,
                actionLabel = actionLabel,
            ),
            details = mapOf(
                "异常类型" to error::class.java.name,
                "Credential 错误类型" to type.takeIf { it.isNotBlank() },
                "系统消息" to message,
            ),
        )
    }

    private fun mapGetCredentialError(error: GetCredentialException): String {
        val type = error.type.orEmpty()
        val message = error.errorMessage?.toString()?.takeIf { it.isNotBlank() }
        val summary = when {
            type.contains("CANCELED", ignoreCase = true) ||
                error is GetCredentialCancellationException ->
                SynapseCredentialErrorMapper.cancellationSummary(
                    systemMessage = message,
                    actionLabel = "Google 登录",
                )
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
        return SynapseFailureMessage.withDetails(
            summary = summary,
            details = mapOf(
                "异常类型" to error::class.java.name,
                "Credential 错误类型" to type.takeIf { it.isNotBlank() },
                "系统消息" to message,
            ),
        )
    }
}
