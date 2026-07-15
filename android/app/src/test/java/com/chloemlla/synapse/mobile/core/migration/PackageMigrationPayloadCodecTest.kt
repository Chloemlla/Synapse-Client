package com.chloemlla.synapse.mobile.core.migration

import com.chloemlla.synapse.mobile.core.auth.StoredSynapseAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageMigrationPayloadCodecTest {
    @Test
    fun `round trips accounts and device id`() {
        val accounts = listOf(
            StoredSynapseAccount(
                accountId = "user-1",
                jwt = "jwt-token",
                clientLoginToken = "sml_token",
                clientLoginTokenExpiresAt = "2099-01-01T00:00:00Z",
                userId = "user-1",
                username = "alice",
                email = "alice@example.com",
            ),
        )
        val payload = PackageMigrationPayload(
            version = PackageMigrationPayloadCodec.CURRENT_VERSION,
            deviceId = "android-device-1",
            activeAccountId = "user-1",
            accounts = accounts,
        )

        val decoded = PackageMigrationPayloadCodec.decode(PackageMigrationPayloadCodec.encode(payload))

        assertEquals(payload.version, decoded.version)
        assertEquals(payload.deviceId, decoded.deviceId)
        assertEquals(payload.activeAccountId, decoded.activeAccountId)
        assertEquals(1, decoded.accounts.size)
        assertEquals("user-1", decoded.accounts[0].accountId)
        assertEquals("jwt-token", decoded.accounts[0].jwt)
        assertEquals("sml_token", decoded.accounts[0].clientLoginToken)
        assertTrue(decoded.hasUsableConfig)
    }

    @Test
    fun `empty payload is not usable`() {
        val payload = PackageMigrationPayload(
            version = PackageMigrationPayloadCodec.CURRENT_VERSION,
            deviceId = null,
            activeAccountId = null,
            accounts = emptyList(),
        )
        assertFalse(payload.hasUsableConfig)
    }

    @Test(expected = PackageMigrationPayloadException::class)
    fun `rejects unsupported version`() {
        PackageMigrationPayloadCodec.decode("""{"version":99,"accounts_json":"[]"}""")
    }
}
