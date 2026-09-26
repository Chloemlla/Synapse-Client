package com.chloemlla.synapse.mobile.core.auth

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 向 Google Play 换一份设备证明。这是设备证明（服务端 P2）里唯一依赖 Android / Play 服务的一环，
 * 编排与判定都在 [SynapseIntegrityPolicy] 与 [SynapseAuthRepository] 里。
 *
 * 任何失败（没有 Play 服务、设备不支持、离线、超时）都只返回 null：轮换照常进行，
 * 由服务端决定是否降级 —— 这里不抛异常，也不在界面上报错。
 */
class SynapseIntegrityProvider(context: Context) {
    private val appContext = context.applicationContext

    /**
     * @param nonce 服务端签发的一次性挑战值，会作为 requestHash 回显在证明里。
     * @param cloudProjectNumber 服务端下发的 Google Cloud 项目号；Play 侧必填，缺失直接放弃。
     */
    suspend fun requestIntegrityToken(
        nonce: String,
        cloudProjectNumber: String,
    ): String? = withContext(Dispatchers.IO) {
        val projectNumber = cloudProjectNumber.trim().toLongOrNull() ?: return@withContext null
        runCatching {
            val manager = IntegrityManagerFactory.create(appContext)
            val request = IntegrityTokenRequest.builder()
                .setNonce(nonce)
                .setCloudProjectNumber(projectNumber)
                .build()
            Tasks.await(manager.requestIntegrityToken(request), REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).token()
        }.getOrNull()
    }

    private companion object {
        /** Google 侧正常几百毫秒；给足余量，但别让轮换流程被无限期挂住。 */
        const val REQUEST_TIMEOUT_SECONDS = 15L
    }
}
