package com.chloemlla.synapse.mobile.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SynapseSecureOkHttpFactoryTest {
    @Test
    fun createRejectsHttpBaseUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            SynapseSecureOkHttpFactory.create("http://tts.chloemlla.com")
        }
    }

    @Test
    fun createUsesHttpsOnlyClient() {
        val client = SynapseSecureOkHttpFactory.create("https://tts.chloemlla.com/")

        assertNotNull(client)
        assertEquals(8_000, client.connectTimeoutMillis)
        assertEquals(8_000, client.readTimeoutMillis)
        assertEquals(8_000, client.writeTimeoutMillis)
    }
}
