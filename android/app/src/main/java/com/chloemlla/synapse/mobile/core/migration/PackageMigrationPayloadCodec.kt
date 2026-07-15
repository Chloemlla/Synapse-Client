package com.chloemlla.synapse.mobile.core.migration

import com.chloemlla.synapse.mobile.core.auth.StoredSynapseAccount
import com.chloemlla.synapse.mobile.core.auth.SynapseCredentialCodec
import org.json.JSONException
import org.json.JSONObject

data class PackageMigrationPayload(
    val version: Int,
    val deviceId: String?,
    val activeAccountId: String?,
    val accounts: List<StoredSynapseAccount>,
) {
    val hasUsableConfig: Boolean =
        !deviceId.isNullOrBlank() || accounts.isNotEmpty()
}

internal object PackageMigrationPayloadCodec {
    const val CURRENT_VERSION = 1

    fun encode(payload: PackageMigrationPayload): String {
        return JSONObject()
            .put(KEY_VERSION, payload.version)
            .put(KEY_DEVICE_ID, payload.deviceId)
            .put(KEY_ACTIVE_ACCOUNT_ID, payload.activeAccountId)
            .put(KEY_ACCOUNTS_JSON, SynapseCredentialCodec.encodeAccounts(payload.accounts))
            .toString()
    }

    fun decode(rawJson: String): PackageMigrationPayload {
        try {
            val json = JSONObject(rawJson)
            val version = json.optInt(KEY_VERSION, CURRENT_VERSION)
            if (version != CURRENT_VERSION) {
                throw JSONException("Unsupported package migration payload version: $version")
            }
            val accountsRaw = json.optString(KEY_ACCOUNTS_JSON).takeIf { it.isNotBlank() } ?: "[]"
            return PackageMigrationPayload(
                version = version,
                deviceId = json.stringValue(KEY_DEVICE_ID),
                activeAccountId = json.stringValue(KEY_ACTIVE_ACCOUNT_ID),
                accounts = SynapseCredentialCodec.decodeAccounts(accountsRaw),
            )
        } catch (error: JSONException) {
            throw PackageMigrationPayloadException(error)
        } catch (error: IllegalArgumentException) {
            throw PackageMigrationPayloadException(error)
        }
    }

    private fun JSONObject.stringValue(name: String): String? =
        if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private const val KEY_VERSION = "version"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"
    private const val KEY_ACCOUNTS_JSON = "accounts_json"
}

class PackageMigrationPayloadException(cause: Throwable) :
    IllegalStateException("Package migration payload is invalid.", cause)
