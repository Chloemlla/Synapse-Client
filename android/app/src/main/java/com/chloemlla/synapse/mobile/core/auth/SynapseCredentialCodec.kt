package com.chloemlla.synapse.mobile.core.auth

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal object SynapseCredentialCodec {
    fun encodeAccounts(accounts: List<StoredSynapseAccount>): String =
        JSONArray().apply {
            accounts.forEach { account ->
                put(
                    JSONObject()
                        .put(KEY_ACCOUNT_ID, account.accountId)
                        .put(KEY_JWT, account.jwt)
                        .put(KEY_CLIENT_LOGIN_TOKEN, account.clientLoginToken)
                        .put(KEY_CLIENT_LOGIN_TOKEN_EXPIRES_AT, account.clientLoginTokenExpiresAt)
                        .put(KEY_CLIENT_LOGIN_TOKEN_NEXT_ROTATION_AT, account.clientLoginTokenNextRotationAt)
                        .put(KEY_CLIENT_LOGIN_TOKEN_ROTATED_AT, account.clientLoginTokenRotatedAt)
                        .put(KEY_CLIENT_LOGIN_TOKEN_ROTATION_INDEX, account.clientLoginTokenRotationIndex)
                        .put(KEY_CLIENT_LOGIN_TOKEN_NEEDS_REVERIFICATION, account.clientLoginTokenNeedsReverification)
                        .put(KEY_USER_ID, account.userId)
                        .put(KEY_USERNAME, account.username)
                        .put(KEY_EMAIL, account.email),
                )
            }
        }.toString()

    fun decodeAccounts(rawJson: String): List<StoredSynapseAccount> {
        try {
            val array = JSONArray(rawJson)
            return buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index)
                        ?: throw JSONException("Account entry $index is not an object.")
                    val accountId = json.stringValue(KEY_ACCOUNT_ID)
                        ?: throw JSONException("Account entry $index is missing account_id.")
                    add(
                        StoredSynapseAccount(
                            accountId = accountId,
                            jwt = json.stringValue(KEY_JWT),
                            clientLoginToken = json.stringValue(KEY_CLIENT_LOGIN_TOKEN),
                            clientLoginTokenExpiresAt = json.stringValue(KEY_CLIENT_LOGIN_TOKEN_EXPIRES_AT),
                            clientLoginTokenNextRotationAt = json.stringValue(KEY_CLIENT_LOGIN_TOKEN_NEXT_ROTATION_AT),
                            clientLoginTokenRotatedAt = json.stringValue(KEY_CLIENT_LOGIN_TOKEN_ROTATED_AT),
                            clientLoginTokenRotationIndex = json.intValue(KEY_CLIENT_LOGIN_TOKEN_ROTATION_INDEX),
                            clientLoginTokenNeedsReverification =
                                json.booleanValue(KEY_CLIENT_LOGIN_TOKEN_NEEDS_REVERIFICATION),
                            userId = json.stringValue(KEY_USER_ID),
                            username = json.stringValue(KEY_USERNAME),
                            email = json.stringValue(KEY_EMAIL),
                        ),
                    )
                }
            }
        } catch (error: JSONException) {
            throw SynapseCredentialCorruptionException(error)
        } catch (error: IllegalArgumentException) {
            throw SynapseCredentialCorruptionException(error)
        }
    }

    private fun JSONObject.stringValue(name: String): String? =
        if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    /**
     * 轮换代次。旧版本写出的 JSON 里没有这个字段，缺省当第 0 代，
     * 不让一份存量凭据因为新字段报错而被当成 corrupted。
     */
    private fun JSONObject.intValue(name: String): Int =
        if (!has(name) || isNull(name)) 0 else optInt(name, 0).coerceAtLeast(0)

    /** 同上：旧版本没有这个字段，缺省 false，别让存量凭据被判成 corrupted。 */
    private fun JSONObject.booleanValue(name: String): Boolean =
        has(name) && !isNull(name) && optBoolean(name, false)

    private const val KEY_ACCOUNT_ID = "account_id"
    private const val KEY_JWT = "jwt"
    private const val KEY_CLIENT_LOGIN_TOKEN = "client_login_token"
    private const val KEY_CLIENT_LOGIN_TOKEN_EXPIRES_AT = "client_login_token_expires_at"
    private const val KEY_CLIENT_LOGIN_TOKEN_NEXT_ROTATION_AT = "client_login_token_next_rotation_at"
    private const val KEY_CLIENT_LOGIN_TOKEN_ROTATED_AT = "client_login_token_rotated_at"
    private const val KEY_CLIENT_LOGIN_TOKEN_ROTATION_INDEX = "client_login_token_rotation_index"
    private const val KEY_CLIENT_LOGIN_TOKEN_NEEDS_REVERIFICATION = "client_login_token_needs_reverification"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_EMAIL = "email"
}

class SynapseCredentialCorruptionException(cause: Throwable) :
    IllegalStateException("Stored Synapse credentials are corrupted. Clear local credentials and sign in again.", cause)
