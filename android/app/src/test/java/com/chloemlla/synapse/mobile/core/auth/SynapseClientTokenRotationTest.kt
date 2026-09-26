package com.chloemlla.synapse.mobile.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class SynapseClientTokenRotationTest {
    private val now: Instant = Instant.parse("2026-09-26T00:00:00Z")

    @Test
    fun missingScheduleIsTreatedAsDue() {
        // 老版本存下的凭据没有 nextRotationAt，先补一次轮换换回服务端的节奏。
        assertTrue(SynapseClientTokenRotation.isDue(null, now))
        assertTrue(SynapseClientTokenRotation.isDue("  ", now))
        assertTrue(SynapseClientTokenRotation.isDue("not-a-timestamp", now))
    }

    @Test
    fun futureScheduleIsNotDue() {
        assertFalse(SynapseClientTokenRotation.isDue(now.plusSeconds(1).toString(), now))
        // 到点即视为到期，不留余量。
        assertTrue(SynapseClientTokenRotation.isDue(now.toString(), now))
        assertTrue(SynapseClientTokenRotation.isDue(now.minusSeconds(1).toString(), now))
    }

    @Test
    fun revokedOrMismatchedTokenIsNotRetried() {
        assertNull(SynapseClientTokenRotation.retryAt(401, now))
        assertNull(SynapseClientTokenRotation.retryAt(403, now))
    }

    @Test
    fun throttledFailureBacksOffShorterThanTransientFailure() {
        val throttled = SynapseClientTokenRotation.retryAt(429, now)
        val transient = SynapseClientTokenRotation.retryAt(500, now)
        val offline = SynapseClientTokenRotation.retryAt(null, now)

        assertNotNull(throttled)
        assertEquals(now.plus(30, ChronoUnit.MINUTES), throttled)
        assertEquals(now.plus(6, ChronoUnit.HOURS), transient)
        assertEquals(transient, offline)
    }
}
