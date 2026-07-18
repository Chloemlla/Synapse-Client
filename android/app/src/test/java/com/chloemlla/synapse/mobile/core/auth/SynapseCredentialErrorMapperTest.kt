package com.chloemlla.synapse.mobile.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SynapseCredentialErrorMapperTest {
    @Test
    fun accountReauthFailedIsNotTreatedAsUserCancel() {
        val summary = SynapseCredentialErrorMapper.cancellationSummary(
            systemMessage = "[16] Account reauth failed.",
            actionLabel = "Google 登录",
        )

        assertTrue(SynapseCredentialErrorMapper.isAccountReauthFailure("[16] Account reauth failed."))
        assertFalse(SynapseCredentialErrorMapper.isUserCancellation("[16] Account reauth failed."))
        assertTrue(summary.contains("重新验证"))
        assertFalse(summary.contains("已取消"))
    }

    @Test
    fun blankOrCancelMessageIsUserCancel() {
        assertEquals(
            "已取消 Google 登录。",
            SynapseCredentialErrorMapper.cancellationSummary(
                systemMessage = null,
                actionLabel = "Google 登录",
            ),
        )
        assertEquals(
            "已取消 Passkey 验证。",
            SynapseCredentialErrorMapper.cancellationSummary(
                systemMessage = "activity is cancelled by the user.",
                actionLabel = "Passkey 验证",
            ),
        )
    }

    @Test
    fun otherCancellationMessagesKeepSystemDetail() {
        val summary = SynapseCredentialErrorMapper.cancellationSummary(
            systemMessage = "provider interrupted",
            actionLabel = "Google 登录",
        )
        assertTrue(summary.contains("未完成：provider interrupted"))
    }

    @Test
    fun accountReauthCancellationRetriesWhileFallbackRemains() {
        assertTrue(
            SynapseCredentialErrorMapper.shouldRetryAfterCancellation(
                systemMessage = "[16] Account reauth failed.",
                hasRemainingFallback = true,
            ),
        )
        assertFalse(
            SynapseCredentialErrorMapper.shouldRetryAfterCancellation(
                systemMessage = "[16] Account reauth failed.",
                hasRemainingFallback = false,
            ),
        )
        assertFalse(
            SynapseCredentialErrorMapper.shouldRetryAfterCancellation(
                systemMessage = "activity is cancelled by the user.",
                hasRemainingFallback = true,
            ),
        )
        assertFalse(
            SynapseCredentialErrorMapper.shouldRetryAfterCancellation(
                systemMessage = null,
                hasRemainingFallback = true,
            ),
        )
    }
}
