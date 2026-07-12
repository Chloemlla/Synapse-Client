package com.synapse.mobile.core.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Android Credential Manager adapter for Happy-TTS WebAuthn authentication.
 *
 * Flow (matches Google passkey sign-in docs + Happy-TTS routes):
 * 1. Backend returns PublicKeyCredentialRequestOptions JSON
 * 2. App calls [CredentialManager.getCredential] with [GetPublicKeyCredentialOption]
 * 3. App posts [PublicKeyCredential.authenticationResponseJson] to finish endpoints
 */
class SynapsePasskeyCredentialClient(
    context: Context,
    private val credentialManager: CredentialManager = CredentialManager.create(context.applicationContext),
) {
    suspend fun getAuthenticationAssertion(
        activity: Activity,
        optionsJson: String,
    ): JSONObject = withContext(Dispatchers.Main.immediate) {
        require(!activity.isFinishing && !activity.isDestroyed) {
            "Activity 不可用，无法唤起 Credential Manager。"
        }
        val requestJson = SynapsePasskeyJson.toGetCredentialRequestJson(optionsJson)
        // preferImmediatelyAvailableCredentials=false so the system UI can still open
        // when only hybrid / cloud passkeys are available.
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetPublicKeyCredentialOption(requestJson = requestJson))
            .setPreferImmediatelyAvailableCredentials(false)
            .build()
        try {
            val result = credentialManager.getCredential(
                context = activity,
                request = request,
            )
            val credential = result.credential
            val responseJson = when (credential) {
                is PublicKeyCredential -> credential.authenticationResponseJson
                else -> throw IllegalStateException(
                    "Credential Manager returned unexpected type: ${credential.type}",
                )
            }
            SynapsePasskeyJson.parseAuthenticationResponse(responseJson)
        } catch (error: GetCredentialCancellationException) {
            throw IllegalStateException("已取消 Passkey 验证。", error)
        } catch (error: NoCredentialException) {
            throw IllegalStateException(
                "未找到可用的 Passkey。请确认：1) 本机或同一 Google 账号已保存该站通行密钥；2) 应用已与 RP 域名完成 Digital Asset Links 关联；3) 设备支持通行密钥。",
                error,
            )
        } catch (error: GetCredentialException) {
            throw IllegalStateException(mapGetCredentialError(error), error)
        }
    }

    private fun mapGetCredentialError(error: GetCredentialException): String {
        val type = error.type.orEmpty()
        val message = error.errorMessage?.toString()?.takeIf { it.isNotBlank() }
        return when {
            type.contains("CANCELED", ignoreCase = true) -> "已取消 Passkey 验证。"
            type.contains("NO_CREDENTIAL", ignoreCase = true) ->
                "未找到可用的 Passkey。请确认本机已保存该账号的通行密钥，且已完成 Digital Asset Links 关联。"
            type.contains("INTERRUPTED", ignoreCase = true) -> "Passkey 验证被中断，请重试。"
            type.contains("PROVIDER_CONFIGURATION", ignoreCase = true) ->
                "通行密钥提供方未就绪。请安装/更新 Google Play 服务，并确认设备支持 Credential Manager。"
            type.contains("UNSUPPORTED", ignoreCase = true) ->
                "当前设备或系统不支持通行密钥 Credential Manager。"
            !message.isNullOrBlank() -> "Passkey 验证失败：$message"
            else -> "Passkey 验证失败：${error::class.java.simpleName}"
        }
    }
}