package com.chloemlla.synapse.mobile.core.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Credential Manager 的三条路径都没拿到凭据，但还有救：需要界面层用 ActivityResult 拉起
 * 系统 Google 登录窗口（交互式），拿到 idToken 后回传 [SynapseGoogleCredentialClient.idTokenFromInteractiveResult]。
 *
 * 抛这个异常不代表登录失败，也不代表账号有问题。
 */
class GoogleInteractiveSignInRequired(
    val serverClientId: String,
    detail: String,
    cause: Throwable?,
) : IllegalStateException(detail, cause)

/**
 * Android Credential Manager adapter for Sign in with Google (SIWG).
 *
 * 顺序按官方指引（developer.android.com/identity/sign-in/credential-manager-siwg-implementation）：
 * 1) `GetGoogleIdOption(filterByAuthorizedAccounts = true)` —— 已授权账号，可自动登录
 * 2) `GetGoogleIdOption(filterByAuthorizedAccounts = false)` —— 设备上全部 Google 账号
 * 3) `GetSignInWithGoogleOption` —— 按钮流，覆盖"弹窗被跳过 / 需要重新验证 / 登录提示被关闭"
 * 4) 仍然不行 → [GoogleInteractiveSignInRequired]，由界面层走系统登录窗口
 */
class SynapseGoogleCredentialClient(
    context: Context,
    private val credentialManager: CredentialManager = CredentialManager.create(context.applicationContext),
) {
    private enum class CmStrategy {
        AuthorizedAccounts,
        AllDeviceAccounts,
        SignInWithGoogleButton,
    }

    /**
     * 依次尝试 Credential Manager 的三条路径；任何一条抛异常都不再提前放弃后面的策略。
     *
     * 用户主动取消不自动重试（官方明确要求），直接以取消文案结束。
     */
    suspend fun getGoogleIdToken(
        activity: Activity,
        serverClientId: String,
    ): String = withContext(Dispatchers.Main.immediate) {
        require(!activity.isFinishing && !activity.isDestroyed) {
            "当前界面已关闭，请重新发起 Google 登录。"
        }
        val cleanClientId = serverClientId.trim()
        require(cleanClientId.isNotBlank()) { "Google 登录暂不可用，请稍后重试。" }

        val strategies = CmStrategy.entries
        var lastError: GetCredentialException? = null
        var lastSummary: String? = null

        for (strategy in strategies) {
            try {
                return@withContext requestIdToken(activity, cleanClientId, strategy)
            } catch (cancel: GetCredentialCancellationException) {
                lastError = cancel
                val systemMessage = cancel.errorMessage?.toString()
                lastSummary = SynapseCredentialErrorMapper.cancellationSummary(
                    systemMessage = systemMessage,
                    actionLabel = "Google 登录",
                )
                // 技术性取消（账号需要重新验证）继续往下走：后面还有按钮流和系统窗口；
                // 用户主动取消不自动重试（官方明确要求）。
                val retry = SynapseCredentialErrorMapper.shouldRetryAfterCancellation(
                    systemMessage = systemMessage,
                    hasRemainingFallback = true,
                )
                if (!retry) {
                    throw IllegalStateException(mapCancellationError(cancel, actionLabel = "Google 登录"), cancel)
                }
            } catch (noCredential: NoCredentialException) {
                lastError = noCredential
                lastSummary = SynapseCredentialErrorMapper.noCredentialSummary()
            } catch (failure: GetCredentialException) {
                lastError = failure
                lastSummary = mapGetCredentialError(failure)
            }
        }

        throw GoogleInteractiveSignInRequired(
            serverClientId = cleanClientId,
            detail = lastSummary ?: "Google 登录未完成。",
            cause = lastError,
        )
    }

    /**
     * 交互式 Google 登录窗口的入口 Intent。交给 `ActivityResultContracts.StartActivityForResult`
     * 启动，结果用 [idTokenFromInteractiveResult] 解析。
     *
     * GoogleSignIn 在最新版本里已被 Credential Manager 取代，但它是官方给出的、
     * Credential Manager 无法恢复时的交互式回退路径（`SIGN_IN_REQUIRED` 的正确处置方式）。
     */
    @Suppress("DEPRECATION")
    fun interactiveSignInIntent(context: Context, serverClientId: String): Intent =
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(serverClientId.trim())
                .requestEmail()
                .build(),
        ).signInIntent

    /** 解析 [interactiveSignInIntent] 的返回结果；失败时给出与状态码对应的可执行结论。 */
    @Suppress("DEPRECATION")
    fun idTokenFromInteractiveResult(data: Intent?): String {
        if (data == null) {
            throw IllegalStateException("已取消 Google 登录。")
        }
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        val error = task.exception
        if (!task.isSuccessful || error != null) {
            val statusCode = (error as? ApiException)?.statusCode
            throw IllegalStateException(
                SynapseCredentialErrorMapper.googleWindowFailure(
                    statusCode = statusCode,
                    systemMessage = error?.message?.toString()?.takeIf { it.isNotBlank() },
                ),
                error,
            )
        }
        val idToken = task.result?.idToken?.trim().orEmpty()
        if (idToken.isBlank()) {
            throw IllegalStateException("Google 登录未完成，请重试或改用账号密码登录。")
        }
        return idToken
    }

    private suspend fun requestIdToken(
        activity: Activity,
        serverClientId: String,
        strategy: CmStrategy,
    ): String {
        val request = when (strategy) {
            CmStrategy.AuthorizedAccounts -> GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(true)
                        .setServerClientId(serverClientId)
                        .setAutoSelectEnabled(true)
                        .build(),
                )
                .build()

            CmStrategy.AllDeviceAccounts -> GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(serverClientId)
                        .setAutoSelectEnabled(false)
                        .build(),
                )
                .build()

            CmStrategy.SignInWithGoogleButton -> GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetSignInWithGoogleOption.Builder(serverClientId = serverClientId).build(),
                )
                .build()
        }

        return extractIdToken(
            credentialManager.getCredential(
                request = request,
                // 官方：用前台 Activity 包一层 MutableContextWrapper，避免配置变更时窗口行为不确定。
                context = MutableContextWrapper(activity),
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
                    throw IllegalStateException("无法解析 Google ID Token。请更新 Google Play 服务后重试。", error)
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
            error is NoCredentialException -> SynapseCredentialErrorMapper.noCredentialSummary()
            type.contains("CANCELED", ignoreCase = true) ||
                error is GetCredentialCancellationException ->
                SynapseCredentialErrorMapper.cancellationSummary(
                    systemMessage = message,
                    actionLabel = "Google 登录",
                )
            type.contains("NO_CREDENTIAL", ignoreCase = true) ->
                SynapseCredentialErrorMapper.noCredentialSummary()
            type.contains("INTERRUPTED", ignoreCase = true) -> "Google 登录被中断，请重试。"
            type.contains("PROVIDER_CONFIGURATION", ignoreCase = true) ->
                "Google 登录提供方未就绪。请安装/更新 Google Play 服务，并确认设备支持 Credential Manager。"
            type.contains("UNSUPPORTED", ignoreCase = true) ->
                "当前设备或系统版本不支持 Credential Manager 的 Google 登录，请使用其它登录方式。"
            else -> SynapseCredentialErrorMapper.googleFailureSummary(message)
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
