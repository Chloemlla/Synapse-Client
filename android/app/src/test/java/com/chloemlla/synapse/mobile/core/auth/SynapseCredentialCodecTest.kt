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

    @Test
    fun encodeDecodeAccountsRoundTripsRotationSchedule() {
        val account = StoredSynapseAccount(
            accountId = "user-1",
            jwt = "jwt-value",
            clientLoginToken = "sml_token_value",
            clientLoginTokenExpiresAt = "2026-12-25T00:00:00Z",
            userId = "user-1",
            username = "alice",
            email = "alice@example.com",
            clientLoginTokenNextRotationAt = "2026-09-27T00:00:00Z",
            clientLoginTokenRotatedAt = "2026-09-26T00:00:00Z",
            clientLoginTokenRotationIndex = 7,
        )

        val decoded = SynapseCredentialCodec.decodeAccounts(
            SynapseCredentialCodec.encodeAccounts(listOf(account)),
        )

        assertEquals(listOf(account), decoded)
    }

    @Test
    fun decodeAccountsAcceptsLegacyJsonWithoutRotationFields() {
        // 轮换上线前存下的凭据没有那几个字段，不能当成 corrupted。
        val legacy = """[{"account_id":"user-1","client_login_token":"sml_x"}]"""

        val decoded = SynapseCredentialCodec.decodeAccounts(legacy)

        assertEquals(1, decoded.size)
        assertEquals(null, decoded.first().clientLoginTokenNextRotationAt)
        assertEquals(null, decoded.first().clientLoginTokenRotatedAt)
        assertEquals(0, decoded.first().clientLoginTokenRotationIndex)
        assertEquals(false, decoded.first().clientLoginTokenNeedsReverification)
    }

    @Test
    fun encodeDecodeAccountsRoundTripsReverificationFlag() {
        // 设备证明降级标记要能跨重启留存，否则界面提示一次就没了。
        val account = StoredSynapseAccount(
            accountId = "user-1",
            jwt = "jwt-value",
            clientLoginToken = "sml_token_value",
            clientLoginTokenExpiresAt = "2026-09-27T00:00:00Z",
            userId = "user-1",
            username = "alice",
            email = "alice@example.com",
            clientLoginTokenRotationIndex = 3,
            clientLoginTokenNeedsReverification = true,
        )

        val decoded = SynapseCredentialCodec.decodeAccounts(
            SynapseCredentialCodec.encodeAccounts(listOf(account)),
        )

        assertEquals(listOf(account), decoded)
    }
}
