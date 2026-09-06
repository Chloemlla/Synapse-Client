package com.chloemlla.synapse.mobile.ui

import com.chloemlla.synapse.mobile.BuildConfig

/** 读取 CI 注入的构建元数据，供更新检测与界面使用。 */
object AppBuildInfo {
    val shortHash: String = BuildConfig.SHORT_HASH.trim().ifBlank { "unknown" }
    val buildTimeUtcMillis: Long = BuildConfig.BUILD_TIME_UTC_MILLIS
    val versionName: String = BuildConfig.VERSION_NAME
    val versionCode: Int = BuildConfig.VERSION_CODE
    val updateCheckEnabled: Boolean = BuildConfig.UPDATE_CHECK_ENABLED
    val versionLabel: String = "${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
    val isDevBuild: Boolean = shortHash == "unknown" || buildTimeUtcMillis <= 0
}
