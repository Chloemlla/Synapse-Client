package com.chloemlla.synapse.mobile

import android.app.Application
import android.content.Context
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import com.chloemlla.lumen.crash.CrashReport
import com.chloemlla.lumen.crash.LumenCrash
import com.chloemlla.lumen.crash.LumenCrashConfig
import com.chloemlla.synapse.mobile.core.migration.LegacyPackageConfigMigrator
import com.tencent.mmkv.MMKV

class SynapseApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        installLumenCrashSdk()
        CrashBreadcrumbs.record("Application.attachBaseContext")
    }

    override fun onCreate() {
        super.onCreate()
        installLumenCrashSdk()
        CrashBreadcrumbs.record("Application.onCreate")
        initializeMmkvOrRecordCrash()
        migrateLegacyPackageConfigOrRecordCrash()
    }

    private fun installLumenCrashSdk() {
        if (LumenCrash.isInstalled()) return
        val appName = runCatching { getString(R.string.app_name) }.getOrDefault("Synapse Mobile")
        LumenCrash.install(
            this,
            LumenCrashConfig(
                appDisplayName = appName,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                commitHash = BuildConfig.SHORT_HASH,
                fileProviderAuthority = "${packageName}.fileprovider",
                shareSubject = runCatching { getString(R.string.crash_report_share_subject) }.getOrNull(),
                reportTitle = runCatching { getString(R.string.crash_report_title) }.getOrNull(),
                reportMessage = runCatching { getString(R.string.crash_report_message) }.getOrNull(),
            ),
        )
    }

    private fun initializeMmkvOrRecordCrash() {
        runCatching { MMKV.initialize(this) }
            .onSuccess { CrashBreadcrumbs.record("MMKV initialized") }
            .onFailure(::recordCrash)
    }

    private fun migrateLegacyPackageConfigOrRecordCrash() {
        runCatching { LegacyPackageConfigMigrator(this).migrateIfNeeded() }
            .onSuccess { result ->
                CrashBreadcrumbs.record("Legacy package migration: ${result::class.java.simpleName}")
            }
            .onFailure { error ->
                CrashBreadcrumbs.record("Legacy package migration crashed: ${error::class.java.simpleName}")
                // Do not block app startup for migration failures.
            }
    }

    fun recordStartupCrash(throwable: Throwable): CrashReport {
        return recordCrash(throwable)
    }

    fun recordCrash(throwable: Throwable): CrashReport {
        return LumenCrash.record(throwable)
    }
}
