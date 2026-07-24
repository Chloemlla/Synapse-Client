package com.chloemlla.synapse.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dual-backend QR scanner scaffolding contracts.
 *
 * HMS class loadability is runtime-dependent (scanplus AAR + device/OEM),
 * so unit tests only lock the public enum surface and availability helper shape.
 */
class QrScanBackendTest {
    @Test
    fun preferredBackendValues_areStable() {
        assertEquals(
            listOf(QrScanBackend.Hms, QrScanBackend.MlKit),
            QrScanBackend.entries,
        )
    }

    @Test
    fun hmsScanAvailability_returnsBooleanWithoutThrowing() {
        // On pure JVM unit test classpath the HMS AAR may or may not resolve;
        // either outcome is acceptable — the helper must never throw.
        val usable = HmsScanAvailability.isUsable()
        assertTrue(usable || !usable)
    }
}
