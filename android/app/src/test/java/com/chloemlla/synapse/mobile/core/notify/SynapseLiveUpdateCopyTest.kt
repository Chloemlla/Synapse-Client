package com.chloemlla.synapse.mobile.core.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SynapseLiveUpdateCopyTest {
    @Test
    fun webQrWaitingIsOngoingAndPromoted() {
        val snapshot = SynapseLiveUpdateCopy.webQrWaiting(
            site = "https://tts.chloemlla.com",
            expiresAtEpochMillis = 1_700_000_000_000L,
        )
        assertEquals(SynapseLiveUpdateKind.WebQrLogin, snapshot.kind)
        assertEquals(SynapseLiveUpdatePhase.WaitingConfirm, snapshot.phase)
        assertTrue(snapshot.ongoing)
        assertTrue(snapshot.requestPromoted)
        assertTrue(snapshot.text.contains("tts.chloemlla.com"))
        assertEquals("qr", snapshot.openTab)
        assertTrue(snapshot.alert)
    }

    @Test
    fun terminalSnapshotsAreNotOngoingPromoted() {
        val success = SynapseLiveUpdateCopy.webQrSucceeded("https://example.com")
        val failed = SynapseLiveUpdateCopy.linuxDoFailed("ticket missing")
        assertFalse(success.ongoing)
        assertFalse(success.requestPromoted)
        assertFalse(failed.ongoing)
        assertFalse(failed.requestPromoted)
        assertTrue(failed.text.contains("ticket missing"))
        assertEquals(4_000L, success.autoDismissAfterMs)
        assertEquals(8_000L, failed.autoDismissAfterMs)
        assertEquals("session", success.openTab)
    }

    @Test
    fun linuxDoWaitingUsesBrowserReturnPhase() {
        val snapshot = SynapseLiveUpdateCopy.linuxDoWaiting()
        assertEquals(SynapseLiveUpdateKind.LinuxDoAuth, snapshot.kind)
        assertEquals(SynapseLiveUpdatePhase.WaitingBrowserReturn, snapshot.phase)
        assertTrue(snapshot.ongoing)
        assertTrue(snapshot.requestPromoted)
        assertEquals("login", snapshot.openTab)
    }

    @Test
    fun displaySitePrefersHost() {
        assertEquals("tts.chloemlla.com", SynapseLiveUpdateCopy.displaySite("https://tts.chloemlla.com/path"))
        assertEquals("example.com", SynapseLiveUpdateCopy.displaySite("example.com"))
        assertEquals("网页端", SynapseLiveUpdateCopy.displaySite("  "))
    }

    @Test
    fun sanitizeMessageKeepsFirstLineAndRedactsTokens() {
        val message = SynapseLiveUpdateCopy.sanitizeUserFacingMessage(
            message = "jwt=abc.def.ghi\nsecond line",
            fallback = "fallback",
        )
        assertTrue(message.contains("[redacted]") || message.contains("[token]"))
        assertFalse(message.contains("second line"))
    }

    @Test
    fun chipTextForExpiryFormatsCompactCountdown() {
        val now = 1_000_000L
        assertEquals("已过期", SynapseLiveUpdateCopy.chipTextForExpiry(now - 1, now))
        assertEquals("45秒", SynapseLiveUpdateCopy.chipTextForExpiry(now + 45_000L, now))
        assertEquals("3分", SynapseLiveUpdateCopy.chipTextForExpiry(now + 180_000L, now))
        assertNull(SynapseLiveUpdateCopy.chipTextForExpiry(null, now))
    }
}
