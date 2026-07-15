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

    private const val KEY_ACCOUNT_ID = "account_id"
    private const val KEY_JWT = "jwt"
    private const val KEY_CLIENT_LOGIN_TOKEN = "client_login_token"
    private const val KEY_CLIENT_LOGIN_TOKEN_EXPIRES_AT = "client_login_token_expires_at"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_EMAIL = "email"
}

class SynapseCredentialCorruptionException(cause: Throwable) :
    IllegalStateException("Stored Synapse credentials are corrupted. Clear local credentials and sign in again.", cause)
