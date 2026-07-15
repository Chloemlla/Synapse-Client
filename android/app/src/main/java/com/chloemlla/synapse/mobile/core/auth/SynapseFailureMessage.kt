package com.chloemlla.synapse.mobile.core.auth

/**
 * Builds user-visible failure text with diagnostic details.
 *
 * Contract:
 * - Always keep the primary error message first.
 * - Append exception type / HTTP status / cause chain when available.
 * - Never invent secrets; only surface messages already present on the throwable.
 */
internal object SynapseFailureMessage {
    private const val MAX_CAUSE_DEPTH = 5
    private const val MAX_TOTAL_CHARS = 2_500

    fun from(
        error: Throwable,
        fallback: String = "操作失败",
        context: String? = null,
    ): String {
        val primary = error.message?.trim()?.takeIf { it.isNotBlank() } ?: fallback.trim()
        val details = buildList {
            context?.trim()?.takeIf { it.isNotBlank() }?.let { add("上下文：$it") }
            add("异常类型：${error::class.java.name}")
            if (error is SynapseApiException) {
                // Prefer explicit status even when the message body already mentions it.
                add("HTTP 状态：${error.statusCode}")
            }
            var cause = error.cause
            var depth = 1
            while (cause != null && depth <= MAX_CAUSE_DEPTH) {
                val causeMessage = cause.message?.trim()?.takeIf { it.isNotBlank() }
                // Skip pure wrappers when the primary body already embeds the same diagnostic text.
                val alreadyCovered = causeMessage != null && primary.contains(causeMessage)
                if (!alreadyCovered) {
                    add(
                        if (causeMessage != null) {
                            "原因 #$depth：${cause::class.java.name} - $causeMessage"
                        } else {
                            "原因 #$depth：${cause::class.java.name}"
                        },
                    )
                }
                cause = cause.cause
                depth += 1
            }
        }

        val detailBlock = details
            .filterNot { detail ->
                // Avoid repeating a detail line that is already present in the primary body.
                primary.lineSequence().any { line -> line.trim() == detail }
            }
            .joinToString(separator = "\n")

        return listOf(primary, detailBlock)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
            .trim()
            .let(::truncate)
    }

    fun withDetails(
        summary: String,
        details: Map<String, String?> = emptyMap(),
    ): String {
        val body = buildString {
            append(summary.trim())
            details.forEach { (label, value) ->
                val clean = value?.trim()?.takeIf { it.isNotBlank() } ?: return@forEach
                if (isNotEmpty()) append('\n')
                append("$label：$clean")
            }
        }
        return truncate(body.trim())
    }

    private fun truncate(value: String): String {
        if (value.length <= MAX_TOTAL_CHARS) return value
        return value.take(MAX_TOTAL_CHARS - 1).trimEnd() + "…"
    }
}

