package com.chloemlla.synapse.mobile.core.migration

object PackageMigrationAccessPolicy {
    fun isAllowedCallerPackage(
        callerPackage: String?,
        expectedPackage: String = SynapsePackageIdentity.PRODUCTION_APPLICATION_ID,
    ): Boolean {
        val packageName = callerPackage?.trim().orEmpty()
        return packageName.isNotEmpty() && packageName == expectedPackage
    }

    fun signaturesMatch(
        callerSignatures: Set<String>,
        hostSignatures: Set<String>,
    ): Boolean {
        if (callerSignatures.isEmpty() || hostSignatures.isEmpty()) return false
        return callerSignatures.any { it in hostSignatures }
    }
}
