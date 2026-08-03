package com.chloemlla.synapse.mobile.core.auth

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.toSynapseOAuthAuthorizePreview(): SynapseOAuthAuthorizePreview {
    val data = optJSONObject("data")
    val client = optJSONObject("client") ?: data?.optJSONObject("client")
        ?: throw IllegalStateException("OAuth 预览缺少客户端信息。")
    val user = optJSONObject("user") ?: data?.optJSONObject("user")
        ?: throw IllegalStateException("OAuth 预览缺少授权账号信息。")
    val scopes = firstScopeList("scopes", "scope")
        .ifEmpty { data?.firstScopeList("scopes", "scope").orEmpty() }
    return SynapseOAuthAuthorizePreview(
        client = SynapseOAuthClientSummary(
            clientId = client.firstString("clientId", "client_id").orEmpty(),
            name = client.firstString("name").orEmpty(),
            description = client.firstString("description"),
            homepageUrl = client.firstString("homepageUrl", "homepage_url"),
        ),
        scopes = scopes,
        scopeDetails = (optJSONArray("scopeDetails")
            ?: data?.optJSONArray("scopeDetails")
            ?: JSONArray()).toScopeSummaries(),
        redirectUri = firstString("redirectUri", "redirect_uri")
            ?: data?.firstString("redirectUri", "redirect_uri")
            ?: throw IllegalStateException("OAuth 预览缺少 redirectUri。"),
        responseType = firstString("responseType", "response_type")
            ?: data?.firstString("responseType", "response_type")
            ?: "code",
        state = firstString("state") ?: data?.firstString("state"),
        codeChallengeMethod = firstString("codeChallengeMethod", "code_challenge_method")
            ?: data?.firstString("codeChallengeMethod", "code_challenge_method"),
        account = user.toSynapseUser(),
    )
}

internal fun JSONObject.toSynapseOAuthAuthorizationResult(): SynapseOAuthAuthorizationResult =
    SynapseOAuthAuthorizationResult(
        success = optBoolean("success", optJSONObject("data")?.optBoolean("success", false) == true),
        redirectUri = firstString("redirectUri", "redirect_uri")
            ?: optJSONObject("data")?.firstString("redirectUri", "redirect_uri")
            ?: throw IllegalStateException("OAuth 响应缺少 redirectUri。"),
        scopes = firstScopeList("scopes", "scope")
            .ifEmpty { optJSONObject("data")?.firstScopeList("scopes", "scope").orEmpty() },
    )

internal fun JSONObject.toSynapseDeviceSessions(): SynapseDeviceSessions {
    val data = optJSONObject("data")
    val currentDeviceId = firstString("currentDeviceId", "current_device_id")
        ?: data?.firstString("currentDeviceId", "current_device_id")
        ?: ""
    val devices = optJSONArray("devices")
        ?: data?.optJSONArray("devices")
    val sessions = optJSONArray("sessions")
        ?: data?.optJSONArray("sessions")
        ?: JSONArray()
    val mapped = devices?.toDeviceSessions(currentDeviceId)
        ?: sessions.toDeviceSessions(currentDeviceId)
    return SynapseDeviceSessions(
        currentDeviceId = currentDeviceId,
        sessions = mapped,
        currentDeviceKey = mapped.firstOrNull { it.isCurrentDevice }?.deviceKey,
    )
}

private fun JSONObject.firstScopeList(vararg names: String): List<String> {
    names.forEach { name ->
        if (!has(name) || isNull(name)) return@forEach
        when (val value = opt(name)) {
            is JSONArray -> {
                val result = value.toStringList()
                if (result.isNotEmpty()) return result
            }
            is String -> {
                val result = value.split(Regex("[\\s,|]+"))
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct()
                if (result.isNotEmpty()) return result
            }
        }
    }
    return emptyList()
}

private fun JSONArray.toScopeSummaries(): List<SynapseOAuthScopeSummary> =
    (0 until length()).mapNotNull { index ->
        optJSONObject(index)?.let { scope ->
            SynapseOAuthScopeSummary(
                key = scope.firstString("key").orEmpty(),
                label = scope.firstString("label").orEmpty(),
                description = scope.firstString("description").orEmpty(),
                category = scope.firstString("category").orEmpty(),
                identityScope = scope.optBoolean("identityScope", scope.optBoolean("identity_scope")),
            )
        }
    }

private fun JSONArray.toDeviceSessions(currentDeviceId: String): List<SynapseDeviceSession> =
    (0 until length()).mapNotNull { index ->
        val group = optJSONObject(index) ?: return@mapNotNull null
        val nested = group.optJSONArray("sessions")?.optJSONObject(0)
        val session = nested ?: group
        val deviceKey = group.firstString("deviceKey", "device_key")
            ?: session.firstString("deviceKey", "device_key")
        val deviceId = group.firstString("deviceId", "device_id")
            ?: session.firstString("deviceId", "device_id")
        val isCurrent = group.optBoolean("current", group.optBoolean("isCurrent")) ||
            session.optBoolean("isCurrentDevice", session.optBoolean("is_current_device")) ||
            (!deviceId.isNullOrBlank() && deviceId == currentDeviceId)
        SynapseDeviceSession(
            sessionId = deviceKey ?: session.firstString("sessionId", "session_id", "id").orEmpty(),
            deviceId = deviceId,
            deviceName = group.firstString("deviceName", "device_name")
                ?: session.firstString("deviceName", "device_name").orEmpty(),
            clientId = group.firstString("clientId", "client_id")
                ?: session.firstString("clientId", "client_id"),
            clientName = group.firstString("clientName", "client_name", "appName", "app_name", "clientType", "client_type")
                ?: session.firstString("clientName", "client_name", "appName", "app_name").orEmpty(),
            clientType = group.firstString("clientType", "client_type", "type")
                ?: session.firstString("clientType", "client_type", "type").orEmpty(),
            ipAddress = group.firstString("ip", "ipAddress", "ip_address")
                ?: session.firstString("ipAddress", "ip_address", "ip"),
            ipLocation = group.locationLabel() ?: session.locationLabel(),
            userAgent = group.firstString("userAgent", "user_agent")
                ?: session.firstString("userAgent", "user_agent"),
            lastActiveAt = group.firstString("recentActivityAt", "recent_activity_at", "lastActiveAt", "last_activity_at", "lastSeen", "last_seen")
                ?: session.firstString("lastActiveAt", "last_active_at", "lastSeen", "last_seen"),
            createdAt = group.firstString("createdAt", "created_at")
                ?: session.firstString("createdAt", "created_at"),
            isCurrentDevice = isCurrent,
            deviceKey = deviceKey,
        )
    }.filter { it.sessionId.isNotBlank() || !it.deviceKey.isNullOrBlank() }

private fun JSONObject.locationLabel(): String? {
    val location = optJSONObject("ipLocation") ?: optJSONObject("ip_location") ?: optJSONObject("location")
    if (location != null) {
        return listOfNotNull(
            location.firstString("country", "countryName", "country_name"),
            location.firstString("region", "regionName", "region_name", "province"),
            location.firstString("city"),
        ).joinToString(" · ").takeIf { it.isNotBlank() }
    }
    return firstString("ipLocation", "ip_location", "location", "region", "country")
}

private fun JSONArray.toStringList(): List<String> =
    (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
