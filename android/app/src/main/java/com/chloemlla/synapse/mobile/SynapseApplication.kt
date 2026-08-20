package com.chloemlla.synapse.mobile

import android.app.Application
import android.content.Context
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import com.chloemlla.lumen.crash.CrashReport
import com.chloemlla.lumen.crash.LumenCrash
import com.chloemlla.lumen.crash.LumenCrashConfig
import com.chloemlla.lumen.crash.LumenCrashDefaults
import com.chloemlla.synapse.mobile.core.auth.SynapseDeviceId
import com.chloemlla.synapse.mobile.core.migration.LegacyPackageConfigMigrator
import com.chloemlla.synapse.mobile.core.notify.SynapseLiveUpdateNotifier
import com.tencent.mmkv.MMKV

class SynapseApplication : Application() {
    val liveUpdateNotifier: SynapseLiveUpdateNotifier by lazy { SynapseLiveUpdateNotifier(this) }
    @Volatile
    var lumenCrashAvailable: Boolean = false
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Crash SDK must never prevent process start.
        runCatching {
            installLumenCrashSdk()
            recordBreadcrumb("Application.attachBaseContext")
        }
    }

    override fun onCreate() {
        super.onCreate()
        runCatching {
            installLumenCrashSdk()
            recordBreadcrumb("Application.onCreate")
        }
        initializeMmkvOrRecordCrash()
        migrateLegacyPackageConfigOrRecordCrash()
        runCatching { liveUpdateNotifier.ensureChannels() }
    }

    private fun installLumenCrashSdk() {
        if (LumenCrash.isInstalled()) {
            lumenCrashAvailable = true
            return
        }
        val appName = runCatching { getString(R.string.app_name) }.getOrDefault("Synapse Mobile")
        // The SDK rejects non-HTTPS upload targets and this app declares usesCleartextTraffic="false",
        // so a custom API host is only honoured when it is already HTTPS.
        val crashBackendBaseUrl = BuildConfig.SYNAPSE_API_BASE_URL
            .trim()
            .takeIf { it.startsWith("https://", ignoreCase = true) }
            ?: LumenCrashDefaults.DEFAULT_CRASH_BACKEND_BASE_URL
        LumenCrash.install(
            this,
            LumenCrashConfig(
                appDisplayName = appName,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                commitHash = BuildConfig.SHORT_HASH,
                // The AAR merges this provider itself with the share + external-cache paths the
                // SDK needs; the host's own provider cannot serve the external-cache fallback.
                fileProviderAuthority = "${packageName}.lumen.crash.fileprovider",
                shareSubject = runCatching { getString(R.string.crash_report_share_subject) }.getOrNull(),
                reportTitle = runCatching { getString(R.string.crash_report_title) }.getOrNull(),
                reportMessage = runCatching { getString(R.string.crash_report_message) }.getOrNull(),
                anrWatchdogEnabled = true,
                anrWatchdogTimeoutMillis = 5_000L,
                anrWatchdogCheckIntervalMillis = 1_000L,
                startupHangWatchdogEnabled = true,
                startupHangTimeoutMillis = 15_000L,
                crashReportBackendBaseUrl = crashBackendBaseUrl,
                deviceInstallationIdProvider = {
                    // peek() only: this runs on the SDK upload executor, where minting and
                    // persisting a device id as a side effect of a crash would be wrong.
                    runCatching { SynapseDeviceId(this@SynapseApplication).peek() }.getOrNull()
                },
            ),
        )
        lumenCrashAvailable = LumenCrash.isInstalled()
    }

    private fun initializeMmkvOrRecordCrash() {
        runCatching { MMKV.initialize(this) }
            .onSuccess { recordBreadcrumb("MMKV initialized") }
            .onFailure(::recordCrash)
    }

    private fun migrateLegacyPackageConfigOrRecordCrash() {
        runCatching { LegacyPackageConfigMigrator(this).migrateIfNeeded() }
            .onSuccess { result ->
                recordBreadcrumb("Legacy package migration: ${result::class.java.simpleName}")
            }
            .onFailure { error ->
                recordBreadcrumb("Legacy package migration crashed: ${error::class.java.simpleName}")
                // Do not block app startup for migration failures.
            }
    }

    fun recordStartupCrash(throwable: Throwable): CrashReport {
        return recordCrash(throwable)
            ?: CrashReport(
                reportId = "startup-fallback",
                crashedAtMillis = System.currentTimeMillis(),
                crashedAtText = System.currentTimeMillis().toString(),
                exceptionType = throwable::class.java.name,
                rootCause = throwable.message ?: throwable::class.java.name,
                threadName = Thread.currentThread().name,
                processName = packageName,
                systemInfo = "LumenCrash unavailable during startup recording",
                stackTrace = throwable.stackTraceToString(),
            )
    }

    fun recordCrash(throwable: Throwable): CrashReport? {
        if (!lumenCrashAvailable && !runCatching { LumenCrash.isInstalled() }.getOrDefault(false)) {
            return null
        }
        return runCatching { LumenCrash.record(throwable) }.getOrNull()
    }

    fun loadPendingCrashReport(): CrashReport? {
        if (!lumenCrashAvailable && !runCatching { LumenCrash.isInstalled() }.getOrDefault(false)) {
            return null
        }
        return runCatching { LumenCrash.loadPendingReport() }.getOrNull()
    }

    fun clearPendingCrashReport() {
        runCatching { LumenCrash.clearPendingReport() }
    }

    private fun recordBreadcrumb(event: String) {
        runCatching { CrashBreadcrumbs.record(event) }
    }
}
