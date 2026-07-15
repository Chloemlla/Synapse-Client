package com.chloemlla.synapse.mobile.core.migration

import android.content.Context
import android.net.Uri
import com.chloemlla.synapse.mobile.BuildConfig
import com.chloemlla.synapse.mobile.core.auth.SynapseCredentialStore
import com.chloemlla.synapse.mobile.core.auth.SynapseDeviceId
import com.chloemlla.lumen.crash.CrashBreadcrumbs

class LegacyPackageConfigMigrator(
    context: Context,
    private val credentialStore: SynapseCredentialStore = SynapseCredentialStore(context),
    private val deviceIdStore: SynapseDeviceId = SynapseDeviceId(context),
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun migrateIfNeeded(): MigrationResult {
        if (BuildConfig.IS_LEGACY_PACKAGE) return MigrationResult.SkippedLegacyBuild
        if (prefs.getBoolean(KEY_MIGRATION_DONE, false)) return MigrationResult.AlreadyCompleted

        val hasLocalCredentials = credentialStore.load().accounts.isNotEmpty()
        val hasLocalDeviceId = !deviceIdStore.peek().isNullOrBlank()
        if (hasLocalCredentials || hasLocalDeviceId) {
            markDone()
            return MigrationResult.SkippedLocalConfigPresent
        }

        if (!isLegacyPackageInstalled()) {
            markDone()
            return MigrationResult.SkippedLegacyMissing
        }

        val payload = queryLegacyPayload()
        if (payload == null) {
            // Leave incomplete so a later launch can retry after the legacy exporter APK is installed.
            CrashBreadcrumbs.record("Legacy package migration export unavailable")
            return MigrationResult.FailedExportUnavailable
        }
        if (!payload.hasUsableConfig) {
            markDone()
            return MigrationResult.SkippedEmptyExport
        }

        credentialStore.importAccounts(payload.accounts, payload.activeAccountId)
        payload.deviceId?.takeIf { it.isNotBlank() }?.let(deviceIdStore::replace)
        markDone()
        CrashBreadcrumbs.record("Legacy package config migrated")
        return MigrationResult.Migrated(
            accountCount = payload.accounts.size,
            deviceIdMigrated = !payload.deviceId.isNullOrBlank(),
        )
    }

    private fun isLegacyPackageInstalled(): Boolean {
        return runCatching {
            appContext.packageManager.getPackageInfo(SynapsePackageIdentity.LEGACY_APPLICATION_ID, 0)
            true
        }.getOrDefault(false)
    }

    private fun queryLegacyPayload(): PackageMigrationPayload? {
        val uri = Uri.parse(SynapsePackageIdentity.MIGRATION_CONTENT_URI)
        return runCatching {
            appContext.contentResolver.query(uri, arrayOf(MigrationConfigProvider.COLUMN_PAYLOAD_JSON), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val index = cursor.getColumnIndex(MigrationConfigProvider.COLUMN_PAYLOAD_JSON)
                    if (index < 0) return@use null
                    val raw = cursor.getString(index)?.takeIf { it.isNotBlank() } ?: return@use null
                    PackageMigrationPayloadCodec.decode(raw)
                }
        }.onFailure {
            CrashBreadcrumbs.record("Legacy package migration query failed: ${it::class.java.simpleName}")
        }.getOrNull()
    }

    private fun markDone() {
        prefs.edit().putBoolean(KEY_MIGRATION_DONE, true).apply()
    }

    private companion object {
        private const val PREFS_NAME = "synapse_package_migration"
        private const val KEY_MIGRATION_DONE = "migration_done"
    }
}

sealed interface MigrationResult {
    data object AlreadyCompleted : MigrationResult
    data object SkippedLegacyBuild : MigrationResult
    data object SkippedLocalConfigPresent : MigrationResult
    data object SkippedLegacyMissing : MigrationResult
    data object SkippedEmptyExport : MigrationResult
    data object FailedExportUnavailable : MigrationResult
    data class Migrated(
        val accountCount: Int,
        val deviceIdMigrated: Boolean,
    ) : MigrationResult
}
