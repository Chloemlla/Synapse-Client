package com.chloemlla.synapse.mobile.core.migration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageMigrationAccessPolicyTest {
    @Test
    fun `allows only production package name`() {
        assertTrue(
            PackageMigrationAccessPolicy.isAllowedCallerPackage(
                SynapsePackageIdentity.PRODUCTION_APPLICATION_ID,
            ),
        )
        assertFalse(
            PackageMigrationAccessPolicy.isAllowedCallerPackage(
                SynapsePackageIdentity.LEGACY_APPLICATION_ID,
            ),
        )
        assertFalse(PackageMigrationAccessPolicy.isAllowedCallerPackage(null))
        assertFalse(PackageMigrationAccessPolicy.isAllowedCallerPackage("  "))
    }

    @Test
    fun `requires overlapping signatures`() {
        assertTrue(
            PackageMigrationAccessPolicy.signaturesMatch(
                callerSignatures = setOf("AA:BB", "CC:DD"),
                hostSignatures = setOf("CC:DD", "EE:FF"),
            ),
        )
        assertFalse(
            PackageMigrationAccessPolicy.signaturesMatch(
                callerSignatures = setOf("AA:BB"),
                hostSignatures = setOf("CC:DD"),
            ),
        )
        assertFalse(
            PackageMigrationAccessPolicy.signaturesMatch(
                callerSignatures = emptySet(),
                hostSignatures = setOf("CC:DD"),
            ),
        )
    }
}
