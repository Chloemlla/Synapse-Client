package com.synapse.mobile.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun remainingLabelUsesFriendlyChineseBuckets() {
        val now = Instant.parse("2026-07-05T00:00:00Z")

        assertEquals("已过期", SynapseTokenExpiry.remainingLabel("2026-07-04T23:59:59Z", now))
        assertEquals("不到 1 分钟", SynapseTokenExpiry.remainingLabel("2026-07-05T00:00:30Z", now))
        assertEquals("剩余 5 分钟", SynapseTokenExpiry.remainingLabel("2026-07-05T00:05:00Z", now))
        assertEquals("剩余 2 小时", SynapseTokenExpiry.remainingLabel("2026-07-05T02:00:00Z", now))
        assertEquals("剩余 3 天", SynapseTokenExpiry.remainingLabel("2026-07-08T00:00:00Z", now))
        assertNull(SynapseTokenExpiry.remainingLabel(null, now))
        assertNull(SynapseTokenExpiry.remainingLabel("not-an-instant", now))
    }

    @Test
    fun formatDisplayKeepsRawWhenUnparseableAndAddsRemainingWhenValid() {
        val now = Instant.parse("2026-07-05T00:00:00Z")

        assertEquals("not-an-instant", SynapseTokenExpiry.formatDisplay("not-an-instant", now))
        assertNull(SynapseTokenExpiry.formatDisplay(null, now))
        assertTrue(
            SynapseTokenExpiry.formatDisplay("2026-07-05T01:00:00Z", now)!!.contains("剩余 1 小时"),
        )
    }
}
