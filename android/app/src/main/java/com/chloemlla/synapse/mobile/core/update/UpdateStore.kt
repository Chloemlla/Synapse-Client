package com.chloemlla.synapse.mobile.core.update

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class UpdateStore(context: Context) {
    private val prefs = sharedPrefs(context)

    /** 启动时是否自动检查软件更新。默认开启。 */
    var autoCheckUpdate: Boolean
        get() = prefs.getBoolean("auto_check_update", true)
        set(value) {
            prefs.edit { putBoolean("auto_check_update", value) }
        }

    companion object {
        private const val PREFS_NAME = "synapse_update"

        @Volatile
        private var cachedPrefs: SharedPreferences? = null

        private fun sharedPrefs(context: Context): SharedPreferences =
            cachedPrefs ?: synchronized(this) {
                cachedPrefs ?: context.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .also { cachedPrefs = it }
            }
    }
}
