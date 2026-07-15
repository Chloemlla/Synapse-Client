package com.chloemlla.synapse.mobile.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SynapseLinuxDoCallbackParserTest {
    @Test
    fun parsesAppDeepLinkTicket() {
        val payload = SynapseLinuxDoCallbackParser.parse(
            "synapse://linuxdo-callback?ticket=abc123&intent=login",
        )
        assertEquals("abc123", payload.ticket)
        assertEquals("login", payload.intent)
        assertFalse(payload.isBindFlow)
    }

    @Test
    fun parsesFrontendCallbackTicket() {
        val payload = SynapseLinuxDoCallbackParser.parse(
            "https://tts.chloemlla.com/auth/linuxdo/callback?ticket=ticket-value&intent=login",
        )
        assertEquals("ticket-value", payload.ticket)
        assertTrue(
            SynapseLinuxDoCallbackParser.isLinuxDoRelated(
                "https://tts.chloemlla.com/auth/linuxdo/callback?ticket=ticket-value",
            ),
        )
    }

    @Test
    fun parsesProviderBindRedirect() {
        val payload = SynapseLinuxDoCallbackParser.parse(
            "https://tts.chloemlla.com/auth/provider/bind?sessionToken=bind-session",
        )
        assertEquals("bind-session", payload.sessionToken)
        assertTrue(payload.isBindFlow)
        assertEquals("bind", payload.intent)
    }

    @Test
    fun parsesError() {
        val payload = SynapseLinuxDoCallbackParser.parse(
            "https://tts.chloemlla.com/auth/linuxdo/callback?error=access_denied",
        )
        assertEquals("access_denied", payload.error)
    }

    @Test
    fun isLinuxDoRelatedRejectsUnrelatedHttps() {
        assertFalse(
            SynapseLinuxDoCallbackParser.isLinuxDoRelated(
                "https://tts.chloemlla.com/login",
            ),
        )
        assertTrue(
            SynapseLinuxDoCallbackParser.isLinuxDoRelated(
                "https://tts.chloemlla.com/auth/linuxdo/callback?ticket=x",
            ),
        )
        assertFalse(
            SynapseLinuxDoCallbackParser.isLinuxDoRelated(
                "https://tts.chloemlla.com/api/auth/linuxdo/callback?code=x&state=y",
            ),
        )
    }
}
