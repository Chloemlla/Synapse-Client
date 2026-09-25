package com.chloemlla.synapse.mobile.core.auth

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.time.Instant

/**
 * 首次访问闸门（/api/ip-verification）会拦下所有未豁免的请求，要求同时带上
 * X-Fingerprint 与 X-IP-Verification-Token。浏览器可以用 canvas 指纹加 Turnstile，
 * 安卓客户端两样都没有；这里改用稳定的设备 id 当指纹去换令牌，服务端对一个干净 IP
 * 是直接签发 issuedBy=auto 的令牌的，不需要人机验证。
 *
 * 令牌 40 分钟过期，并且绑定 (指纹, IP)：所以只在本地缓存到过期前 60 秒，
 * IP 变了则由 403 分支再换一次。
 */
internal class SynapseIpVerificationInterceptor(
    baseUrl: String,
    private val fingerprintProvider: () -> String,
) : Interceptor {

    private val origin: HttpUrl = baseUrl.trim().trimEnd('/').toHttpUrl()
    private val sessionUrl: HttpUrl =
        "${origin.scheme}://${origin.host}:${origin.port}/api/ip-verification/session".toHttpUrl()
    /** 不带本拦截器的客户端，用来发起换取令牌的引导请求，避免自我递归。 */
    private val bootstrapClient: OkHttpClient = SynapseSecureOkHttpFactory.create(baseUrl)
    private val refreshLock = Any()

    @Volatile
    private var cached: CachedToken? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isSameOrigin(request.url)) {
            return chain.proceed(request)
        }

        val fingerprint = runCatching { fingerprintProvider().trim() }.getOrDefault("")
        if (fingerprint.isEmpty()) {
            return chain.proceed(request)
        }

        val first = chain.proceed(
            withVerificationHeaders(request, fingerprint, tokenFor(fingerprint, forceRefresh = false)),
        )
        if (first.code != HTTP_FORBIDDEN || !isVerificationRequired(first)) {
            return first
        }

        // 403 上的 IP_VERIFICATION_REQUIRED 意味着请求根本没进业务逻辑，重放一次是安全的。
        first.close()
        return chain.proceed(
            withVerificationHeaders(request, fingerprint, tokenFor(fingerprint, forceRefresh = true)),
        )
    }

    private fun isSameOrigin(url: HttpUrl): Boolean =
        url.scheme == origin.scheme && url.host == origin.host && url.port == origin.port

    private fun isVerificationRequired(response: Response): Boolean =
        runCatching { response.peekBody(PEEK_LIMIT).string() }.getOrNull()?.contains(ERROR_CODE) == true

    private fun withVerificationHeaders(request: Request, fingerprint: String, token: String?): Request =
        request.newBuilder()
            .header(FINGERPRINT_HEADER, fingerprint)
            .apply {
                if (token != null) {
                    header(TOKEN_HEADER, token)
                } else {
                    removeHeader(TOKEN_HEADER)
                }
            }
            .build()

    private fun tokenFor(fingerprint: String, forceRefresh: Boolean): String? {
        if (!forceRefresh) {
            cached?.takeIf { it.isUsableFor(fingerprint) }?.let { return it.token }
        }
        synchronized(refreshLock) {
            if (!forceRefresh) {
                cached?.takeIf { it.isUsableFor(fingerprint) }?.let { return it.token }
            }
            val issued = requestToken(fingerprint)
            cached = issued
            return issued?.token
        }
    }

    private fun requestToken(fingerprint: String): CachedToken? {
        val body = JSONObject().put("fingerprint", fingerprint).toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(sessionUrl)
            .post(body)
            .header("Accept", "application/json")
            .header("User-Agent", SynapseMobileLoginApi.USER_AGENT)
            .build()
        return try {
            bootstrapClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string().orEmpty())
                val token = json.optString("token").takeIf { it.isNotBlank() } ?: return null
                CachedToken(token, fingerprint, resolveExpiry(json.optString("expiresAt")))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveExpiry(rawExpiresAt: String): Long =
        runCatching { Instant.parse(rawExpiresAt).toEpochMilli() }
            .getOrElse { System.currentTimeMillis() + FALLBACK_TTL_MILLIS }

    private class CachedToken(
        val token: String,
        private val fingerprint: String,
        private val expiresAtMillis: Long,
    ) {
        fun isUsableFor(candidate: String): Boolean =
            candidate == fingerprint && System.currentTimeMillis() < expiresAtMillis - REFRESH_SKEW_MILLIS
    }

    private companion object {
        const val FINGERPRINT_HEADER = "X-Fingerprint"
        const val TOKEN_HEADER = "X-IP-Verification-Token"
        const val ERROR_CODE = "IP_VERIFICATION_REQUIRED"
        const val HTTP_FORBIDDEN = 403
        const val REFRESH_SKEW_MILLIS = 60_000L
        const val FALLBACK_TTL_MILLIS = 30 * 60 * 1000L
        const val PEEK_LIMIT = 4L * 1024
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
