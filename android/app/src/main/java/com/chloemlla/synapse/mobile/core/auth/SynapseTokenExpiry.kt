package com.chloemlla.synapse.mobile.core.auth

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object SynapseTokenExpiry {
    private val displayFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())

    fun isExpiredAt(rawExpiresAt: String?, now: Instant = Instant.now()): Boolean =
        rawExpiresAt?.let { raw ->
            runCatching { now.isAfter(Instant.parse(raw)) }
                .getOrDefault(true)
        } ?: false

    fun parseInstant(rawExpiresAt: String?): Instant? =
        rawExpiresAt
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { Instant.parse(raw) }.getOrNull() }

    fun formatDisplay(rawExpiresAt: String?, now: Instant = Instant.now()): String? {
        val instant = parseInstant(rawExpiresAt) ?: return rawExpiresAt?.trim()?.takeIf { it.isNotBlank() }
        val base = displayFormatter.format(instant)
        val remaining = remainingLabel(rawExpiresAt, now)
        return if (remaining.isNullOrBlank()) base else "$base（$remaining）"
    }

    fun remainingLabel(rawExpiresAt: String?, now: Instant = Instant.now()): String? {
        val expiresAt = parseInstant(rawExpiresAt) ?: return null
        val seconds = Duration.between(now, expiresAt).seconds
        return when {
            seconds <= 0L -> "已过期"
            seconds < 60L -> "不到 1 分钟"
            seconds < 3_600L -> "剩余 ${seconds / 60L} 分钟"
            seconds < 86_400L -> "剩余 ${seconds / 3_600L} 小时"
            else -> "剩余 ${seconds / 86_400L} 天"
        }
    }

    fun formatInstantDisplay(expiresAt: Instant, now: Instant = Instant.now()): String {
        val base = displayFormatter.format(expiresAt)
        val seconds = Duration.between(now, expiresAt).seconds
        val remaining = when {
            seconds <= 0L -> "已过期"
            seconds < 60L -> "不到 1 分钟"
            seconds < 3_600L -> "剩余 ${seconds / 60L} 分钟"
            seconds < 86_400L -> "剩余 ${seconds / 3_600L} 小时"
            else -> "剩余 ${seconds / 86_400L} 天"
        }
        return "$base（$remaining）"
    }
}

