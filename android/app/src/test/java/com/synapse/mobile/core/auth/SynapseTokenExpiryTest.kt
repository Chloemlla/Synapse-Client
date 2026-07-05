package com.synapse.mobile.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SynapseTokenExpiryTest {
    @Test
    fun isExpiredAtComparesIsoInstant() {
        val now = Instant.parse("2026-07-05T00:00:00Z")

        assertTrue(SynapseTokenExpiry.isExpiredAt("2026-07-04T23:59:59Z", now))
        assertFalse(SynapseTokenExpiry.isExpiredAt("2026-07-05T00:00:01Z", now))
    }

    @Test
    fun isExpiredAtTreatsMissingAsNotExpiredAndInvalidAsExpired() {
        val now = Instant.parse("2026-07-05T00:00:00Z")

        assertFalse(SynapseTokenExpiry.isExpiredAt(null, now))
        assertTrue(SynapseTokenExpiry.isExpiredAt("not-an-instant", now))
    }
}
