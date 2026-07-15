package com.chloemlla.synapse.mobile.core.migration

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import com.chloemlla.synapse.mobile.core.auth.SynapseCredentialStore
import com.chloemlla.synapse.mobile.core.auth.SynapseDeviceId
import com.chloemlla.synapse.mobile.core.crash.CrashBreadcrumbs
import java.security.MessageDigest

/**
 * Legacy-package export surface. Registered only on the legacy product flavor.
 * Callers must hold the signature-protected migration permission and pass
 * package-name + signing-cert checks.
 */
class MigrationConfigProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (!isConfigUri(uri)) return null
        val appContext = context?.applicationContext ?: return null
        if (!authorizeCaller()) {
            CrashBreadcrumbs.record("Migration export denied for caller")
            return null
        }

        return runCatching {
            val credentials = SynapseCredentialStore(appContext).load()
            val deviceId = SynapseDeviceId(appContext).peek()
            val payload = PackageMigrationPayload(
                version = PackageMigrationPayloadCodec.CURRENT_VERSION,
                deviceId = deviceId,
                activeAccountId = credentials.activeAccountId,
                accounts = credentials.accounts,
            )
            val encoded = PackageMigrationPayloadCodec.encode(payload)
            MatrixCursor(arrayOf(COLUMN_PAYLOAD_JSON)).apply {
                addRow(arrayOf(encoded))
            }
        }.onFailure {
            CrashBreadcrumbs.record("Migration export failed: ${it::class.java.simpleName}")
        }.getOrNull()
    }

    override fun getType(uri: Uri): String? =
        if (isConfigUri(uri)) {
            "vnd.android.cursor.item/vnd.${SynapsePackageIdentity.MIGRATION_AUTHORITY}.config"
        } else {
            null
        }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun isConfigUri(uri: Uri): Boolean {
        if (uri.authority != SynapsePackageIdentity.MIGRATION_AUTHORITY) return false
        val path = uri.path?.trim('/') ?: return false
        return path == SynapsePackageIdentity.MIGRATION_PATH
    }

    private fun authorizeCaller(): Boolean {
        val appContext = context?.applicationContext ?: return false
        val callerPackage = callingPackage ?: return false
        if (!PackageMigrationAccessPolicy.isAllowedCallerPackage(callerPackage)) return false

        val packageManager = appContext.packageManager
        val hostSignatures = packageSignatures(packageManager, appContext.packageName)
        val callerSignatures = packageSignatures(packageManager, callerPackage)
        return PackageMigrationAccessPolicy.signaturesMatch(callerSignatures, hostSignatures)
    }

    private fun packageSignatures(packageManager: PackageManager, packageName: String): Set<String> {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
                val signingInfo = info.signingInfo ?: return emptySet()
                val signatures = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
                signatures.mapNotNull { signatureDigest(it.toByteArray()) }.toSet()
            } else {
                @Suppress("DEPRECATION")
                val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures?.mapNotNull { signatureDigest(it.toByteArray()) }?.toSet().orEmpty()
            }
        }.getOrDefault(emptySet())
    }

    private fun signatureDigest(bytes: ByteArray): String? {
        return runCatching {
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString(":") { "%02X".format(it) }
        }.getOrNull()
    }

    companion object {
        const val COLUMN_PAYLOAD_JSON = "payload_json"
    }
}
