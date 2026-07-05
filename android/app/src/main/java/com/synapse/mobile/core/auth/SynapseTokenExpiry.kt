package com.synapse.mobile.core.auth

import java.time.Instant

internal object SynapseTokenExpiry {
    fun isExpiredAt(rawExpiresAt: String?, now: Instant = Instant.now()): Boolean =
        rawExpiresAt?.let { raw ->
            runCatching { now.isAfter(Instant.parse(raw)) }
                .getOrDefault(true)
        } ?: false
}
