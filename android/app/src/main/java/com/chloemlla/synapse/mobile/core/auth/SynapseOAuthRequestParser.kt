package com.chloemlla.synapse.mobile.core.auth

import java.net.URI
import java.net.URLDecoder

internal object SynapseOAuthRequestParser {
    private val allowedParameters = setOf(
        "provider_origin",
        "response_type",
        "client_id",
        "redirect_uri",
        "scope",
        "state",
        "code_challenge",
        "code_challenge_method",
        "client_name",
        "client_version",
        "client_build",
        "device_id",
        "device_name",
        "platform",
    )
    private val requiredParameters = setOf(
        "provider_origin",
        "response_type",
        "client_id",
        "redirect_uri",
        "scope",
        "state",
        "code_challenge",
        "code_challenge_method",
    )
    private val pkcePattern = Regex("^[A-Za-z0-9._~-]{43,128}$")

    fun parse(raw: String, trustedProviderOrigin: String): SynapseOAuthAuthorizationRequest {
        val uri = try {
            parseUri(raw.trim())
        } catch (error: Exception) {
            throw SynapseOAuthRequestException(
                message = error.message ?: "OAuth 请求无效。",
            )
        }
        val safeRedirect = runCatching { safeRedirectUri(uri) }.getOrNull()
        val safeState = uri.getQueryParameter("state")?.trim()?.takeIf { it.isNotBlank() }

        try {
            require(uri.scheme.equals("synapse", ignoreCase = true)) { "OAuth 请求必须使用 synapse scheme。" }
            require(uri.host.equals("oauth", ignoreCase = true)) { "OAuth 请求 host 必须为 oauth。" }
            require(uri.path == "/authorize") { "OAuth 请求 path 必须为 /authorize。" }
            require(uri.fragment.isNullOrBlank()) { "OAuth 请求不得包含 fragment。" }

            val names = uri.queryParameterNames
            require(names.containsAll(requiredParameters) && names.all { it in allowedParameters }) {
                "OAuth 请求包含未知或缺失参数。"
            }

            val providerOrigin = required(uri, "provider_origin")
            val normalizedOrigin = SynapseApiOriginPolicy.requireTrustedOrigin(
                candidateOrigin = providerOrigin,
                trustedOrigin = trustedProviderOrigin,
            )
            val responseType = required(uri, "response_type")
            require(responseType == "code") { "OAuth response_type 只支持 code。" }

            val clientId = required(uri, "client_id")
            val redirectUri = required(uri, "redirect_uri")
            require(isSafeRedirectBase(redirectUri, clientId)) { "OAuth redirect_uri 不是安全的已登记地址。" }

            val scopes = required(uri, "scope")
                .split(Regex("\\s+"))
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
            require(scopes.isNotEmpty()) { "OAuth scope 不能为空。" }

            val state = required(uri, "state")
            require(state.length <= 500) { "OAuth state 超出长度限制。" }

            val codeChallenge = required(uri, "code_challenge")
            require(pkcePattern.matches(codeChallenge)) { "OAuth code_challenge 必须是有效的 S256 值。" }
            val codeChallengeMethod = required(uri, "code_challenge_method")
            require(codeChallengeMethod == "S256") { "OAuth 只支持 S256 PKCE。" }

            return SynapseOAuthAuthorizationRequest(
                providerOrigin = normalizedOrigin,
                responseType = responseType,
                clientId = clientId,
                redirectUri = redirectUri,
                scopes = scopes,
                state = state,
                codeChallenge = codeChallenge,
                codeChallengeMethod = codeChallengeMethod,
                clientName = optional(uri, "client_name"),
                clientVersion = optional(uri, "client_version"),
                clientBuild = optional(uri, "client_build"),
                deviceId = optional(uri, "device_id"),
                deviceName = optional(uri, "device_name"),
                platform = optional(uri, "platform"),
            )
        } catch (error: Exception) {
            if (error is SynapseOAuthRequestException) throw error
            throw SynapseOAuthRequestException(
                message = error.message ?: "OAuth 请求无效。",
                safeRedirectUri = safeRedirect,
                safeState = safeState,
            )
        }
    }

    fun isOAuthRelated(raw: String): Boolean {
        return runCatching {
            val uri = parseUri(raw.trim())
            uri.scheme.equals("synapse", ignoreCase = true) &&
                uri.host.equals("oauth", ignoreCase = true)
        }.getOrDefault(false)
    }

    fun safeErrorRedirectUri(raw: String, error: String, description: String): String? {
        val uri = runCatching { parseUri(raw.trim()) }.getOrNull() ?: return null
        val redirectUri = runCatching { safeRedirectUri(uri) }.getOrNull() ?: return null
        val state = uri.getQueryParameter("state")?.trim().orEmpty()
        if (state.isBlank() || state.length > 500) return null
        return appendCallbackParameters(
            redirectUri = redirectUri,
            parameters = linkedMapOf(
                "error" to error,
                "error_description" to description,
                "state" to state,
            ),
        )
    }

    fun validateCallbackUri(
        callbackUri: String,
        request: SynapseOAuthAuthorizationRequest,
        approved: Boolean,
    ): String {
        val actual = parseUriOrThrow(callbackUri, "OAuth 回调地址无效。")
        val expected = parseUriOrThrow(request.redirectUri, "OAuth redirect_uri 无效。")
        require(actual.scheme.equals(expected.scheme, ignoreCase = true)) { "OAuth 回调 scheme 不匹配。" }
        require(actual.host.equals(expected.host, ignoreCase = true)) { "OAuth 回调 host 不匹配。" }
        require(actual.port == expected.port && normalizedPath(actual) == normalizedPath(expected)) { "OAuth 回调地址不匹配。" }
        expected.queryParameterNames.forEach { name ->
            require(actual.getQueryParameter(name) == expected.getQueryParameter(name)) {
                "OAuth 回调原始 query 不匹配。"
            }
        }
        require(isAllowedRedirectUri(callbackUri, request.clientId)) { "OAuth 回调地址不是安全的已登记地址。" }
        require(actual.queryParameterNames.none { it.lowercase() in SENSITIVE_PARAMETERS }) {
            "OAuth 回调包含禁止的 token 参数。"
        }
        require(actual.queryParameterNames.all { it in expected.queryParameterNames || it in CALLBACK_PARAMETERS }) {
            "OAuth 回调包含未知参数。"
        }
        require(actual.getQueryParameter("state") == request.state) { "OAuth 回调 state 不匹配。" }
        if (approved) {
            require(!actual.getQueryParameter("code").isNullOrBlank()) { "OAuth 批准回调缺少 code。" }
            require(actual.getQueryParameter("error").isNullOrBlank()) { "OAuth 批准回调包含 error。" }
        } else {
            require(actual.getQueryParameter("error") == "access_denied") { "OAuth 拒绝回调错误码不匹配。" }
        }
        return actual.raw
    }

    private fun required(uri: ParsedUri, name: String): String =
        uri.getQueryParameter(name)?.trim()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("OAuth 缺少 $name。")

    private fun optional(uri: ParsedUri, name: String): String? =
        uri.getQueryParameter(name)?.trim()?.takeIf { it.isNotBlank() }?.take(160)

    private fun safeRedirectUri(uri: ParsedUri): String {
        val raw = uri.getQueryParameter("redirect_uri")?.trim()
            ?: throw IllegalArgumentException("OAuth 缺少 redirect_uri。")
        require(isSafeRedirectBase(raw, uri.getQueryParameter("client_id"))) {
            "OAuth redirect_uri 必须使用安全的已登记地址。"
        }
        return raw
    }

    private fun isSafeRedirectBase(raw: String, clientId: String? = null): Boolean = runCatching {
        isAllowedRedirectUri(raw, clientId) && parseUri(raw).queryParameterNames.none {
            it.lowercase() in SENSITIVE_PARAMETERS || it in CALLBACK_PARAMETERS
        }
    }.getOrDefault(false)

    private fun isAllowedRedirectUri(raw: String, clientId: String? = null): Boolean =
        isHttpsRedirectUri(raw) ||
            (clientId == PILIPLUS_CLIENT_ID && isNativeRedirectUri(raw) && runCatching {
                val uri = URI(raw)
                uri.scheme.equals("piliplus", ignoreCase = true) &&
                    uri.host.equals("synapse-auth", ignoreCase = true) &&
                    (uri.path.isNullOrBlank() || uri.path == "/")
            }.getOrDefault(false))

    private fun isHttpsRedirectUri(raw: String): Boolean = runCatching {
        val uri = URI(raw)
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            uri.fragment == null
    }.getOrDefault(false)

    private fun isNativeRedirectUri(raw: String): Boolean = runCatching {
        val uri = URI(raw)
        !uri.scheme.isNullOrBlank() &&
            !uri.scheme.equals("http", ignoreCase = true) &&
            !uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            uri.port == -1 &&
            uri.fragment == null
    }.getOrDefault(false)

    private fun normalizedPath(uri: ParsedUri): String =
        uri.path.orEmpty().trimEnd('/').ifBlank { "/" }

    private fun parseUriOrThrow(raw: String, message: String): ParsedUri =
        runCatching { parseUri(raw.trim()) }.getOrElse { error ->
            throw IllegalArgumentException(message, error)
        }

    private fun parseUri(raw: String): ParsedUri {
        val parsed = URI(raw)
        val parameters = linkedMapOf<String, MutableList<String>>()
        parsed.rawQuery.orEmpty()
            .split('&')
            .filter(String::isNotEmpty)
            .forEach { component ->
                val separator = component.indexOf('=')
                val rawName = if (separator >= 0) component.substring(0, separator) else component
                val rawValue = if (separator >= 0) component.substring(separator + 1) else ""
                val name = decodeQueryComponent(rawName)
                val value = decodeQueryComponent(rawValue)
                parameters.getOrPut(name) { mutableListOf() }.add(value)
            }
        return ParsedUri(raw, parsed, parameters.mapValues { it.value.toList() })
    }

    private fun decodeQueryComponent(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private data class ParsedUri(
        val raw: String,
        val parsed: URI,
        val queryParameters: Map<String, List<String>>,
    ) {
        val scheme: String?
            get() = parsed.scheme
        val host: String?
            get() = parsed.host
        val path: String?
            get() = parsed.path
        val fragment: String?
            get() = parsed.fragment
        val port: Int
            get() = parsed.port
        val queryParameterNames: Set<String>
            get() = queryParameters.keys

        fun getQueryParameter(name: String): String? = queryParameters[name]?.firstOrNull()
    }

    private val SENSITIVE_PARAMETERS = setOf(
        "access_token",
        "client_secret",
        "id_token",
        "jwt",
        "refresh_token",
        "token",
    )
    private val CALLBACK_PARAMETERS = setOf("code", "error", "error_description", "scope", "state")
    private const val PILIPLUS_CLIENT_ID = "piliplus"
    private const val PILIPLUS_REDIRECT_URI = "piliplus://synapse-auth"
}
