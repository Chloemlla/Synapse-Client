package com.synapse.mobile.core.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tencent.mmkv.MMKV
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

class SynapseCredentialStore(context: Context) {
    private val appContext = context.applicationContext
    private val masterKey by lazy {
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    private val secureMetadata by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            SECURE_METADATA_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
    private val encryptedStore by lazy {
        MMKV.mmkvWithID(STORE_NAME, MMKV.SINGLE_PROCESS_MODE, mmkvCryptKey())
            ?: error("Unable to open Synapse credential store.")
    }

    fun saveJwt(jwt: String, user: SynapseUser? = null) {
        val accounts = loadAccounts().toMutableList()
        val accountId = user?.accountId() ?: currentAccountId(accounts) ?: MANUAL_ACCOUNT_ID
        val existing = accounts.firstOrNull { it.accountId == accountId }
        val activeBeforeSave = activeAccount(accounts)
        upsertAccount(
            accounts,
            StoredSynapseAccount(
                accountId = accountId,
                jwt = jwt,
                clientLoginToken = existing?.clientLoginToken ?: activeBeforeSave
                    ?.takeIf { user != null && it.accountId != accountId }
                    ?.clientLoginToken,
                clientLoginTokenExpiresAt = existing?.clientLoginTokenExpiresAt ?: activeBeforeSave
                    ?.takeIf { user != null && it.accountId != accountId }
                    ?.clientLoginTokenExpiresAt,
                userId = user?.id ?: existing?.userId,
                username = user?.username ?: existing?.username,
                email = user?.email ?: existing?.email,
            ),
        )
        removePromotedManualAccount(accounts, activeBeforeSave, accountId)
        saveAccounts(accounts, accountId)
        writeLegacyActive(load().activeAccount)
    }

    fun saveClientLoginToken(clientLoginToken: String, expiresAt: String?) {
        val accounts = loadAccounts().toMutableList()
        val accountId = currentAccountId(accounts) ?: MANUAL_ACCOUNT_ID
        val existing = accounts.firstOrNull { it.accountId == accountId }
        upsertAccount(
            accounts,
            StoredSynapseAccount(
                accountId = accountId,
                jwt = existing?.jwt,
                clientLoginToken = clientLoginToken,
                clientLoginTokenExpiresAt = expiresAt ?: existing?.clientLoginTokenExpiresAt,
                userId = existing?.userId,
                username = existing?.username,
                email = existing?.email,
            ),
        )
        saveAccounts(accounts, accountId)
        writeLegacyActive(load().activeAccount)
    }

    fun saveUser(user: SynapseUser) {
        val accounts = loadAccounts().toMutableList()
        val accountId = user.accountId()
        val activeBeforeSave = activeAccount(accounts)
        val existing = accounts.firstOrNull { it.accountId == accountId } ?: activeBeforeSave
        upsertAccount(
            accounts,
            StoredSynapseAccount(
                accountId = accountId,
                jwt = existing?.jwt,
                clientLoginToken = existing?.clientLoginToken,
                clientLoginTokenExpiresAt = existing?.clientLoginTokenExpiresAt,
                userId = user.id,
                username = user.username,
                email = user.email,
            ),
        )
        removePromotedManualAccount(accounts, activeBeforeSave, accountId)
        saveAccounts(accounts, accountId)
        writeLegacyActive(load().activeAccount)
    }

    fun load(): StoredSynapseCredentials {
        val accounts = loadAccounts()
        val activeAccountId = currentAccountId(accounts)
        val active = accounts.firstOrNull { it.accountId == activeAccountId }
        return StoredSynapseCredentials(
            jwt = active?.jwt,
            clientLoginToken = active?.clientLoginToken,
            clientLoginTokenExpiresAt = active?.clientLoginTokenExpiresAt,
            userId = active?.userId,
            username = active?.username,
            email = active?.email,
            activeAccountId = active?.accountId,
            accounts = accounts,
        )
    }

    fun selectAccount(accountId: String) {
        val accounts = loadAccounts()
        require(accounts.any { it.accountId == accountId }) { "Account is not stored on this device." }
        encryptedStore.encode(KEY_ACTIVE_ACCOUNT_ID, accountId)
        writeLegacyActive(accounts.firstOrNull { it.accountId == accountId })
    }

    fun clearAll() {
        encryptedStore.removeValuesForKeys(
            arrayOf(
                KEY_JWT,
                KEY_CLIENT_LOGIN_TOKEN,
                KEY_USER_ID,
                KEY_USERNAME,
                KEY_EMAIL,
                KEY_ACCOUNTS_JSON,
                KEY_ACTIVE_ACCOUNT_ID,
            ),
        )
    }

    fun clearCurrentAccount() {
        val accounts = loadAccounts().toMutableList()
        val activeId = currentAccountId(accounts) ?: return
        val nextAccounts = accounts.filterNot { it.accountId == activeId }
        val nextActiveId = nextAccounts.firstOrNull()?.accountId
        saveAccounts(nextAccounts, nextActiveId)
        writeLegacyActive(nextAccounts.firstOrNull { it.accountId == nextActiveId })
    }

    fun revokeExpiredClientTokens(now: Instant = Instant.now()): Boolean {
        val accounts = loadAccounts().toMutableList()
        var changed = false
        val nextAccounts = accounts.map { account ->
            if (account.clientLoginToken != null && account.isClientLoginTokenExpiredAt(now)) {
                changed = true
                account.copy(jwt = null, clientLoginToken = null)
            } else {
                account
            }
        }
        if (!changed) return false
        saveAccounts(nextAccounts, currentAccountId(nextAccounts))
        writeLegacyActive(activeAccount(nextAccounts))
        return true
    }

    fun clearJwtOnly() {
        val accounts = loadAccounts().toMutableList()
        val active = activeAccount(accounts) ?: return
        upsertAccount(accounts, active.copy(jwt = null))
        saveAccounts(accounts, active.accountId)
        writeLegacyActive(active.copy(jwt = null))
    }

    private fun mmkvCryptKey(): String {
        val existing = secureMetadata.getString(KEY_MMKV_CRYPT_KEY, null)
        if (!existing.isNullOrBlank()) return existing

        val generated = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        check(secureMetadata.edit().putString(KEY_MMKV_CRYPT_KEY, generated).commit()) {
            "Unable to persist Synapse MMKV encryption key."
        }
        return generated
    }

    private fun loadAccounts(): List<StoredSynapseAccount> {
        val stored = encryptedStore.decodeString(KEY_ACCOUNTS_JSON)
            ?.takeIf { it.isNotBlank() }
            ?.let(::parseAccounts)
            .orEmpty()
        if (stored.isNotEmpty()) return stored

        val legacy = legacyAccount() ?: return emptyList()
        saveAccounts(listOf(legacy), legacy.accountId)
        return listOf(legacy)
    }

    private fun parseAccounts(rawJson: String): List<StoredSynapseAccount> =
        runCatching {
            val array = JSONArray(rawJson)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    val accountId = json.stringValue(KEY_ACCOUNT_ID) ?: continue
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
        }.getOrDefault(emptyList())

    private fun saveAccounts(accounts: List<StoredSynapseAccount>, activeAccountId: String?) {
        val encoded = JSONArray().apply {
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
        encryptedStore.encode(KEY_ACCOUNTS_JSON, encoded)
        if (activeAccountId.isNullOrBlank()) {
            encryptedStore.removeValuesForKeys(arrayOf(KEY_ACTIVE_ACCOUNT_ID))
        } else {
            encryptedStore.encode(KEY_ACTIVE_ACCOUNT_ID, activeAccountId)
        }
    }

    private fun legacyAccount(): StoredSynapseAccount? {
        val jwt = encryptedStore.decodeString(KEY_JWT)?.takeIf { it.isNotBlank() }
        val clientLoginToken = encryptedStore.decodeString(KEY_CLIENT_LOGIN_TOKEN)?.takeIf { it.isNotBlank() }
        val userId = encryptedStore.decodeString(KEY_USER_ID)?.takeIf { it.isNotBlank() }
        val username = encryptedStore.decodeString(KEY_USERNAME)?.takeIf { it.isNotBlank() }
        val email = encryptedStore.decodeString(KEY_EMAIL)?.takeIf { it.isNotBlank() }
        if (jwt == null && clientLoginToken == null && userId == null && username == null && email == null) return null
        return StoredSynapseAccount(
            accountId = userId ?: username ?: email ?: MANUAL_ACCOUNT_ID,
            jwt = jwt,
            clientLoginToken = clientLoginToken,
            clientLoginTokenExpiresAt = null,
            userId = userId,
            username = username,
            email = email,
        )
    }

    private fun writeLegacyActive(active: StoredSynapseAccount?) {
        if (active == null) {
            encryptedStore.removeValuesForKeys(
                arrayOf(KEY_JWT, KEY_CLIENT_LOGIN_TOKEN, KEY_USER_ID, KEY_USERNAME, KEY_EMAIL),
            )
            return
        }
        active.jwt?.let { encryptedStore.encode(KEY_JWT, it) } ?: encryptedStore.removeValuesForKeys(arrayOf(KEY_JWT))
        active.clientLoginToken?.let { encryptedStore.encode(KEY_CLIENT_LOGIN_TOKEN, it) }
            ?: encryptedStore.removeValuesForKeys(arrayOf(KEY_CLIENT_LOGIN_TOKEN))
        active.userId?.let { encryptedStore.encode(KEY_USER_ID, it) }
            ?: encryptedStore.removeValuesForKeys(arrayOf(KEY_USER_ID))
        active.username?.let { encryptedStore.encode(KEY_USERNAME, it) }
            ?: encryptedStore.removeValuesForKeys(arrayOf(KEY_USERNAME))
        active.email?.let { encryptedStore.encode(KEY_EMAIL, it) }
            ?: encryptedStore.removeValuesForKeys(arrayOf(KEY_EMAIL))
    }

    private fun activeAccount(accounts: List<StoredSynapseAccount>): StoredSynapseAccount? =
        currentAccountId(accounts)?.let { accountId ->
            accounts.firstOrNull { it.accountId == accountId }
        }

    private fun currentAccountId(accounts: List<StoredSynapseAccount>): String? {
        val stored = encryptedStore.decodeString(KEY_ACTIVE_ACCOUNT_ID)?.takeIf { it.isNotBlank() }
        if (stored != null && accounts.any { it.accountId == stored }) return stored
        return accounts.firstOrNull()?.accountId
    }

    private fun upsertAccount(accounts: MutableList<StoredSynapseAccount>, account: StoredSynapseAccount) {
        val index = accounts.indexOfFirst { it.accountId == account.accountId }
        if (index >= 0) {
            accounts[index] = account
        } else {
            accounts.add(account)
        }
    }

    private fun removePromotedManualAccount(
        accounts: MutableList<StoredSynapseAccount>,
        activeBeforeSave: StoredSynapseAccount?,
        newAccountId: String,
    ) {
        if (activeBeforeSave?.accountId == MANUAL_ACCOUNT_ID && newAccountId != MANUAL_ACCOUNT_ID) {
            accounts.removeAll { it.accountId == MANUAL_ACCOUNT_ID }
        }
    }

    private fun SynapseUser.accountId(): String =
        id.takeIf { it.isNotBlank() } ?: username.takeIf { it.isNotBlank() } ?: email.takeIf { it.isNotBlank() }
        ?: MANUAL_ACCOUNT_ID

    private fun StoredSynapseAccount.isClientLoginTokenExpiredAt(now: Instant): Boolean =
        clientLoginTokenExpiresAt?.let { raw ->
            runCatching { now.isAfter(Instant.parse(raw)) }.getOrDefault(false)
        } ?: false

    private fun JSONObject.stringValue(name: String): String? =
        if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private companion object {
        private const val SECURE_METADATA_NAME = "synapse_secure_metadata"
        private const val STORE_NAME = "synapse_encrypted_credentials"
        private const val KEY_ACCOUNTS_JSON = "accounts_json"
        private const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_JWT = "jwt"
        private const val KEY_CLIENT_LOGIN_TOKEN = "client_login_token"
        private const val KEY_CLIENT_LOGIN_TOKEN_EXPIRES_AT = "client_login_token_expires_at"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_EMAIL = "email"
        private const val KEY_MMKV_CRYPT_KEY = "mmkv_crypt_key"
        private const val MANUAL_ACCOUNT_ID = "manual_jwt"
    }
}
