package com.chloemlla.synapse.mobile.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SynapseIntegrityPolicyTest {
    private fun challenge(
        required: Boolean = true,
        nonce: String? = "nonce-value",
        cloudProjectNumber: String? = "1234567890",
        success: Boolean = true,
    ) = SynapseIntegrityChallenge(
        success = success,
        required = required,
        nonce = nonce,
        expiresAt = null,
        cloudProjectNumber = cloudProjectNumber,
    )

    @Test
    fun proofRequestAsksForProofOnlyWhenServerRequiresIt() {
        assertNull(SynapseIntegrityPolicy.proofRequest(null))
        // 服务端没启用这一层：不申请，也不该让调用方以为出了错。
        assertNull(SynapseIntegrityPolicy.proofRequest(challenge(required = false)))
    }

    @Test
    fun proofRequestSkipsIncompleteChallenges() {
        assertNull(SynapseIntegrityPolicy.proofRequest(challenge(nonce = null)))
        assertNull(SynapseIntegrityPolicy.proofRequest(challenge(nonce = "   ")))
        assertNull(SynapseIntegrityPolicy.proofRequest(challenge(cloudProjectNumber = null)))
        assertNull(SynapseIntegrityPolicy.proofRequest(challenge(cloudProjectNumber = "")))
    }

    @Test
    fun proofRequestKeepsServerNonceAndProjectNumber() {
        val request = SynapseIntegrityPolicy.proofRequest(challenge())

        assertEquals("nonce-value", request?.nonce)
        assertEquals("1234567890", request?.cloudProjectNumber)
    }

    @Test
    fun proofPairsTheNonceWithTheGoogleToken() {
        val request = SynapseIntegrityPolicy.proofRequest(challenge())!!

        val proof = SynapseIntegrityPolicy.proof(request, "integrity-token")

        assertEquals("nonce-value", proof?.nonce)
        assertEquals("integrity-token", proof?.integrityToken)
    }

    @Test
    fun proofIsDroppedWhenGoogleReturnsNothing() {
        val request = SynapseIntegrityPolicy.proofRequest(challenge())!!

        // 拿不到证明就地放弃，绝不拿半份证明去换降级判定。
        assertNull(SynapseIntegrityPolicy.proof(request, null))
        assertNull(SynapseIntegrityPolicy.proof(request, "  "))
    }
}
