package com.chloemlla.synapse.mobile.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthModelsTest {
    @Test
    fun tokenPreviewHidesMiddleOfLongTokens() {
        assertEquals("sml_...987654", "sml_abcdefghijklmnopqrstuvwxyz987654".toSensitiveTokenPreview())
    }

    @Test
    fun tokenPreviewShowsSavedForShortTokens() {
        assertEquals("已保存", "sml_short".toSensitiveTokenPreview())
    }

    @Test
    fun tokenPreviewReturnsNullForMissingTokens() {
        val token: String? = null
        assertNull(token.toSensitiveTokenPreview())
        assertNull("   ".toSensitiveTokenPreview())
    }
}
