package com.chloemlla.synapse.mobile.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SynapseQrPayloadParserTest {
    @Test
    fun parseAcceptsValidPayload() {
        val payload = SynapseQrPayload.parse(
            "synapse://mobile-login?sessionId=session-1&scanToken=scan-1&apiBaseUrl=https%3A%2F%2Ftts.chloemlla.com&expiresAt=2026-07-04T12%3A00%3A00.000Z",
        )

        assertEquals("session-1", payload.sessionId)
        assertEquals("scan-1", payload.scanToken)
        assertEquals("https://tts.chloemlla.com", payload.apiBaseUrl)
        assertEquals("2026-07-04T12:00:00Z", payload.expiresAt.toString())
    }

    @Test
    fun parseRejectsWrongScheme() {
        assertThrows(IllegalArgumentException::class.java) {
            SynapseQrPayload.parse(
                "https://tts.chloemlla.com/mobile-login?sessionId=session-1&scanToken=scan-1&apiBaseUrl=https%3A%2F%2Ftts.chloemlla.com&expiresAt=2026-07-04T12%3A00%3A00.000Z",
            )
        }
    }

    @Test
    fun parseRejectsHttpApiBaseUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            SynapseQrPayload.parse(
                "synapse://mobile-login?sessionId=session-1&scanToken=scan-1&apiBaseUrl=http%3A%2F%2Ftts.chloemlla.com&expiresAt=2026-07-04T12%3A00%3A00.000Z",
            )
        }
    }
}
