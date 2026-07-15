package com.chloemlla.synapse.mobile.core.crash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportSanitizerTest {
    @Test
    fun `redacts token and api key patterns`() {
        val sanitized = CrashReportSanitizer.sanitize(
            "Bearer header.payload.signature " +
                "https://example.com/login?access_token=secret-token&password=hunter2 " +
                "sk-abcdefghijklmnopqrstuvwxyz AIzaabcdefghijklmnopqrstuvwxyz123456",
        )

        assertTrue(sanitized.contains("Bearer [redacted]"))
        assertTrue(sanitized.contains("access_token=[redacted]"))
        assertTrue(sanitized.contains("password=[redacted]"))
        assertTrue(sanitized.contains("sk-[redacted]"))
        assertTrue(sanitized.contains("AIza[redacted]"))
        assertFalse(sanitized.contains("secret-token"))
        assertFalse(sanitized.contains("hunter2"))
    }

    @Test
    fun `redacts local paths and content uris`() {
        val sanitized = CrashReportSanitizer.sanitize(
            "C:\\Users\\alice\\secret.txt /home/bob/private /Users/carol/private " +
                "content://com.chloemlla.synapse.mobile/private file:///data/user/0/private",
        )

        assertTrue(sanitized.contains("[user-home]"))
        assertTrue(sanitized.contains("[content-uri]"))
        assertTrue(sanitized.contains("[file-uri]"))
        assertFalse(sanitized.contains("alice"))
        assertFalse(sanitized.contains("bob"))
        assertFalse(sanitized.contains("carol"))
    }
}
