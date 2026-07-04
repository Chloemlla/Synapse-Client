package com.synapse.mobile.core.auth

object CertificatePinPolicy {
    fun parse(rawPins: String): List<String> =
        rawPins
            .split(',', '\n', '\r', '\t', ' ')
            .map { it.trim() }
            .filter { it.startsWith("sha256/") }
            .distinct()
}
