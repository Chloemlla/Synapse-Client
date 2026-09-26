package com.chloemlla.synapse.mobile.core.auth

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * 复制敏感凭据（登录令牌）之前的设备锁屏校验。
 *
 * 官方依据：
 * - `KeyguardManager.isDeviceSecure()` —— 用户是否设了 PIN / 图案 / 密码（只用滑动解锁不算）。
 * - `KeyguardManager.createConfirmDeviceCredentialIntent(title, description)` —— 拉起系统凭据确认
 *   界面，调用方按 `Activity.RESULT_OK` 判定是否通过。
 * - 未设置锁屏时跳 `Settings.ACTION_BIOMETRIC_ENROLL`（带上允许的认证器），官方说明该页面
 *   "enroll biometrics, and setup PIN/Pattern/Pass if necessary"，即顺带引导创建锁屏。
 *
 * 为什么不用 `androidx.biometric` 的 `DEVICE_CREDENTIAL`：那个组合要求 API 30+，而本模块
 * minSdk 26，反而要多写一条 KeyguardManager 分支；`createConfirmDeviceCredentialIntent`
 * 一条路径覆盖 21+，并且在已录入指纹 / 人脸的系统上会一并接受生物识别。
 */
internal object SynapseDeviceLock {
    /** `BiometricManager.Authenticators.BIOMETRIC_STRONG or DEVICE_CREDENTIAL`。 */
    private const val ALLOWED_AUTHENTICATORS = 0b010 or 0b100

    /** 设备是否具备可用的锁屏凭据。 */
    fun isSecure(context: Context): Boolean =
        keyguardManager(context)?.isDeviceSecure == true

    /**
     * 系统锁屏 / 生物确认界面；返回 null 表示这台设备给不出确认入口，
     * 调用方必须按"未通过"处理，不能退化成直接放行。
     */
    @Suppress("DEPRECATION")
    fun confirmIntent(context: Context): Intent? =
        keyguardManager(context)
            ?.createConfirmDeviceCredentialIntent("验证锁屏以继续", "通过后才能复制登录令牌")

    /**
     * 打开“设置锁屏 / 注册指纹”的系统页面。
     *
     * Android 11+ 有包可见性限制，`resolveActivity()` 对系统设置页经常返回 null，
     * 所以直接试 `startActivity()` 并在失败时逐级回退。
     *
     * @return 是否真的拉起了页面；全失败时返回 false，由界面层给出手动提示。
     */
    fun openLockSetup(context: Context): Boolean {
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(
                    Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                        putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, ALLOWED_AUTHENTICATORS)
                    },
                )
            }
            add(Intent(Settings.ACTION_SECURITY_SETTINGS))
            add(Intent(Settings.ACTION_SETTINGS))
        }
        for (intent in intents) {
            if (runCatching {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
            ) {
                return true
            }
        }
        return false
    }

    private fun keyguardManager(context: Context): KeyguardManager? =
        runCatching {
            context.applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        }.getOrNull()
}
