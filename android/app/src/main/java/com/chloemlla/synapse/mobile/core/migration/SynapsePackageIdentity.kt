package com.chloemlla.synapse.mobile.core.migration

object SynapsePackageIdentity {
    const val LEGACY_APPLICATION_ID = "com.synapse.mobile"
    const val PRODUCTION_APPLICATION_ID = "com.chloemlla.synapse.mobile"
    const val MIGRATION_AUTHORITY = "$LEGACY_APPLICATION_ID.migration"
    const val MIGRATION_PERMISSION = "$LEGACY_APPLICATION_ID.permission.READ_MIGRATION_CONFIG"
    const val MIGRATION_PATH = "config"
    const val MIGRATION_CONTENT_URI = "content://$MIGRATION_AUTHORITY/$MIGRATION_PATH"
}
