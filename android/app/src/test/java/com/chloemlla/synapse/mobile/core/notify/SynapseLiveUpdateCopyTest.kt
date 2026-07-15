package com.chloemlla.synapse.mobile.core.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals("待确认", snapshot.shortCriticalText)
        assertTrue(snapshot.text.contains("https://tts.chloemlla.com"))
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
    }

    @Test
    fun linuxDoWaitingUsesBrowserReturnPhase() {
        val snapshot = SynapseLiveUpdateCopy.linuxDoWaiting()
        assertEquals(SynapseLiveUpdateKind.LinuxDoAuth, snapshot.kind)
        assertEquals(SynapseLiveUpdatePhase.WaitingBrowserReturn, snapshot.phase)
        assertTrue(snapshot.ongoing)
        assertTrue(snapshot.requestPromoted)
    }
}
