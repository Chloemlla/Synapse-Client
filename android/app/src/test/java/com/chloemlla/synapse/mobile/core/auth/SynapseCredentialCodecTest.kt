package com.chloemlla.synapse.mobile.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SynapseCredentialCodecTest {
    @Test
    fun encodeDecodeAccountsRoundTripsCredentialMetadata() {
        val account = StoredSynapseAccount(
            accountId = "user-1",
            jwt = "jwt-value",
            clientLoginToken = "sml_token_value",
            clientLoginTokenExpiresAt = "2026-10-02T00:00:00Z",
            userId = "user-1",
            username = "alice",
            email = "alice@example.com",
        )

        val decoded = SynapseCredentialCodec.decodeAccounts(
            SynapseCredentialCodec.encodeAccounts(listOf(account)),
        )

        assertEquals(listOf(account), decoded)
    }

    @Test
    fun decodeAccountsRejectsCorruptJson() {
        assertThrows(SynapseCredentialCorruptionException::class.java) {
            SynapseCredentialCodec.decodeAccounts("""[{ "username": "alice" }]""")
        }
    }
}
