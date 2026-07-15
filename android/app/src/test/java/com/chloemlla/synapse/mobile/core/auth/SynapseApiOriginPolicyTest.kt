package com.chloemlla.synapse.mobile.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SynapseApiOriginPolicyTest {
    @Test
    fun normalizeHttpsOriginKeepsOnlyTrustedOriginParts() {
        assertEquals(
            "https://tts.chloemlla.com:8443",
            SynapseApiOriginPolicy.normalizeHttpsOrigin("HTTPS://TTS.CHLOEMLLA.COM:8443/"),
        )
    }

    @Test
    fun normalizeHttpsOriginRejectsUnsafeOrigins() {
        assertThrows(IllegalArgumentException::class.java) {
            SynapseApiOriginPolicy.normalizeHttpsOrigin("http://tts.chloemlla.com")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SynapseApiOriginPolicy.normalizeHttpsOrigin("https://user:pass@tts.chloemlla.com")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SynapseApiOriginPolicy.normalizeHttpsOrigin("https://tts.chloemlla.com/api")
        }
    }

    @Test
    fun requireTrustedOriginRejectsCrossOriginQrPayloads() {
        assertThrows(IllegalArgumentException::class.java) {
            SynapseApiOriginPolicy.requireTrustedOrigin(
                candidateOrigin = "https://evil.example",
                trustedOrigin = "https://tts.chloemlla.com",
            )
        }
    }
}
