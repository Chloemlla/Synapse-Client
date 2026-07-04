package com.synapse.mobile.core.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class CertificatePinPolicyTest {
    @Test
    fun parseKeepsOnlySha256Pins() {
        val pins = CertificatePinPolicy.parse(
            "sha256/abc, invalid sha1/def\nsha256/abc sha256/xyz",
        )

        assertEquals(listOf("sha256/abc", "sha256/xyz"), pins)
    }
}
