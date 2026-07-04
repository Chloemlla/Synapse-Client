package com.synapse.mobile.core.auth

import android.content.Context
import java.util.UUID

class SynapseDeviceId(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)

    fun getOrCreate(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val generated = "android-${UUID.randomUUID()}"
        check(prefs.edit().putString(KEY_DEVICE_ID, generated).commit()) {
            "Unable to persist Synapse device id."
        }
        return generated
    }

    private companion object {
        private const val STORE_NAME = "synapse_device"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
