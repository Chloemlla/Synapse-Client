package com.chloemlla.synapse.mobile.core.auth

import android.content.Context
import java.util.UUID

class SynapseDeviceId(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)

    fun peek(): String? = prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }

    fun getOrCreate(): String {
        val existing = peek()
        if (existing != null) return existing

        val generated = "android-${UUID.randomUUID()}"
        check(prefs.edit().putString(KEY_DEVICE_ID, generated).commit()) {
            "Unable to persist Synapse device id."
        }
        return generated
    }

    fun replace(deviceId: String) {
        val normalized = deviceId.trim()
        require(normalized.isNotBlank()) { "Device id cannot be blank." }
        check(prefs.edit().putString(KEY_DEVICE_ID, normalized).commit()) {
            "Unable to persist Synapse device id."
        }
    }

    private companion object {
        private const val STORE_NAME = "synapse_device"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
