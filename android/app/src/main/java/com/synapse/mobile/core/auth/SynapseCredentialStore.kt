package com.synapse.mobile.core.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tencent.mmkv.MMKV
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
        encryptedStore.encode(KEY_JWT, jwt)
        user?.let { saveUser(it) }
    }

    fun saveClientLoginToken(clientLoginToken: String) {
        encryptedStore.encode(KEY_CLIENT_LOGIN_TOKEN, clientLoginToken)
    }

    fun saveUser(user: SynapseUser) {
        encryptedStore.encode(KEY_USER_ID, user.id)
        encryptedStore.encode(KEY_USERNAME, user.username)
        encryptedStore.encode(KEY_EMAIL, user.email)
    }

    fun load(): StoredSynapseCredentials =
        StoredSynapseCredentials(
            jwt = encryptedStore.decodeString(KEY_JWT)?.takeIf { it.isNotBlank() },
            clientLoginToken = encryptedStore.decodeString(KEY_CLIENT_LOGIN_TOKEN)?.takeIf { it.isNotBlank() },
            userId = encryptedStore.decodeString(KEY_USER_ID)?.takeIf { it.isNotBlank() },
            username = encryptedStore.decodeString(KEY_USERNAME)?.takeIf { it.isNotBlank() },
            email = encryptedStore.decodeString(KEY_EMAIL)?.takeIf { it.isNotBlank() },
        )

    fun clearAll() {
        encryptedStore.removeValuesForKeys(
            arrayOf(
                KEY_JWT,
                KEY_CLIENT_LOGIN_TOKEN,
                KEY_USER_ID,
                KEY_USERNAME,
                KEY_EMAIL,
            ),
        )
    }

    fun clearJwtOnly() {
        encryptedStore.removeValuesForKeys(arrayOf(KEY_JWT))
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

    private companion object {
        private const val SECURE_METADATA_NAME = "synapse_secure_metadata"
        private const val STORE_NAME = "synapse_encrypted_credentials"
        private const val KEY_JWT = "jwt"
        private const val KEY_CLIENT_LOGIN_TOKEN = "client_login_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_EMAIL = "email"
        private const val KEY_MMKV_CRYPT_KEY = "mmkv_crypt_key"
    }
}
