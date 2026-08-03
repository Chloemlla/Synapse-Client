package com.chloemlla.synapse.mobile.core.auth

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SynapseSecurityJsonMappingsTest {
    @Test
    fun mapsOAuthPreviewWithoutTokenFields() {
        val preview = JSONObject(
            """
            {
              "success": true,
              "client": {"clientId":"syn_client_demo","name":"PiliPlus","description":"Client","homepageUrl":"https://client.example"},
              "scopes": ["openid", "profile"],
              "scopeDetails": [{"key":"openid","label":"身份标识","description":"读取身份","category":"identity","identityScope":true}],
              "redirectUri":"https://client.example/callback",
              "responseType":"code",
              "state":"state-123",
              "codeChallengeMethod":"S256",
              "user":{"id":"u1","username":"alice","email":"a@example.com","role":"trusted"},
              "token":"must-not-be-read"
            }
            """.trimIndent(),
        ).toSynapseOAuthAuthorizePreview()

        assertEquals("PiliPlus", preview.client.name)
        assertEquals(listOf("openid", "profile"), preview.scopes)
        assertEquals("alice", preview.account.username)
        assertEquals("S256", preview.codeChallengeMethod)
    }

    @Test
    fun mapsCurrentDeviceAndIpLocation() {
        val sessions = JSONObject(
            """
            {
              "currentDeviceId":"device-current",
              "sessions":[
                {"sessionId":"s-current","deviceId":"device-current","deviceName":"Pixel","clientName":"Synapse Mobile","clientType":"PiliPlus","ip":"203.0.113.42","ipLocation":{"country":"中国","region":"上海"},"isCurrentDevice":true},
                {"sessionId":"s-web","deviceId":"device-web","deviceName":"Chrome","clientId":"web-1","clientName":"Web","clientType":"Web","ip":"198.51.100.10","ipLocation":"美国","isCurrentDevice":false}
              ]
            }
            """.trimIndent(),
        ).toSynapseDeviceSessions()

        assertEquals("device-current", sessions.currentDeviceId)
        assertTrue(sessions.sessions.first().isCurrentDevice)
        assertEquals("中国 · 上海", sessions.sessions.first().ipLocation)
        assertEquals("198.51.*.*", sessions.sessions[1].maskedIpAddress)
        assertEquals("web-1", sessions.sessions[1].clientId)
    }
}
