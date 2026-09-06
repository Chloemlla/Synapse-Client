package com.chloemlla.synapse.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chloemlla.synapse.mobile.core.update.ReleaseAsset
import com.chloemlla.synapse.mobile.core.update.UpdateCandidate
import com.chloemlla.synapse.mobile.core.update.UpdateChecker
import com.chloemlla.synapse.mobile.core.update.UpdateDialog
import com.chloemlla.synapse.mobile.core.update.UpdateDialogState
import com.chloemlla.synapse.mobile.core.update.UpdateInstaller
import com.chloemlla.synapse.mobile.core.update.UpdateStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 顶部栏更新入口可调用的动作。release 构建才启用（debug 与本地 dev 构建不显示图标）。
 */
@Stable
class UpdateCenterActions internal constructor(
    val updateCheckEnabled: Boolean,
    val manualCheck: () -> Unit,
)

/**
 * 应用内自更新状态机宿主：启动时按偏好自动检查、顶部栏手动检查、下载与安装。
 * 状态与下载协程都在这里持有，[content] 只负责渲染主界面并从动作入口触发检查。
 */
@Composable
fun UpdateCenter(content: @Composable (UpdateCenterActions) -> Unit) {
    val context = LocalContext.current.applicationContext
    val store = remember { UpdateStore(context) }
    val updateChecker = remember { UpdateChecker(context) }
    val updateInstaller = remember { UpdateInstaller(context) }
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var updateDialogState by remember { mutableStateOf<UpdateDialogState>(UpdateDialogState.Hidden) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgressBytes by remember { mutableLongStateOf(0L) }
    var downloadProgressTotalBytes by remember { mutableStateOf<Long?>(null) }
    var autoCheckStarted by remember { mutableStateOf(false) }
    var autoCheckUpdate by remember { mutableStateOf(store.autoCheckUpdate) }
    var updateCheckJob by remember { mutableStateOf<Job?>(null) }
    var updateDownloadJob by remember { mutableStateOf<Job?>(null) }

    fun startInstallIfAllowed(candidate: UpdateCandidate, file: File) {
        if (updateInstaller.canInstallPackages()) {
            runCatching { updateInstaller.installApk(file) }
                .onSuccess { updateDialogState = UpdateDialogState.Hidden }
                .onFailure {
                    updateDialogState = UpdateDialogState.Error(
                        detail = it.message.orEmpty(),
                        title = "无法启动安装程序",
                    )
                }
            return
        }
        updateDialogState = UpdateDialogState.InstallAuthorization(candidate, file)
    }

    fun triggerUpdateCheck(manual: Boolean) {
        if (!AppBuildInfo.updateCheckEnabled) return

        updateCheckJob?.cancel()
        updateCheckJob = coroutineScope.launch {
            if (manual) {
                updateDialogState = UpdateDialogState.Checking
            }
            try {
                val candidate = withContext(Dispatchers.IO) {
                    updateChecker.checkForUpdate()
                }
                updateDialogState = when {
                    candidate != null -> UpdateDialogState.UpdateAvailable(candidate)
                    manual -> UpdateDialogState.NoUpdate
                    else -> UpdateDialogState.Hidden
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                if (manual) {
                    updateDialogState = UpdateDialogState.Error(
                        detail = throwable.message.orEmpty(),
                        title = "检查更新失败",
                    )
                }
            } finally {
                autoCheckStarted = true
            }
        }
    }

    fun triggerUpdateDownload(candidate: UpdateCandidate, asset: ReleaseAsset) {
        updateDownloadJob?.cancel()
        updateDownloadJob = coroutineScope.launch {
            downloadingUpdate = true
            updateDialogState = UpdateDialogState.Downloading(candidate, asset)
            downloadProgressBytes = 0L
            downloadProgressTotalBytes = null
            try {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        updateInstaller.downloadApk(asset) { downloadedBytes, totalBytes ->
                            downloadProgressBytes = downloadedBytes
                            downloadProgressTotalBytes = totalBytes
                        }
                    }
                }
                result.onSuccess { file ->
                    startInstallIfAllowed(candidate, file)
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    updateDialogState = UpdateDialogState.Error(
                        detail = throwable.message.orEmpty(),
                        title = "下载更新失败",
                    )
                }
            } finally {
                downloadingUpdate = false
                downloadProgressBytes = 0L
                downloadProgressTotalBytes = null
            }
        }
    }

    // 取消下载：立即收起对话框；底层阻塞读取无法中断，因此保留 downloadingUpdate=true
    // 直到协程真正结束，避免并发写入同一个缓存 APK 文件。
    fun cancelUpdateDownload() {
        updateDownloadJob?.cancel()
        updateDialogState = UpdateDialogState.Hidden
    }

    DisposableEffect(lifecycleOwner, updateDialogState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val pendingInstall = updateDialogState as? UpdateDialogState.InstallAuthorization
                ?: return@LifecycleEventObserver
            if (updateInstaller.canInstallPackages()) {
                startInstallIfAllowed(pendingInstall.candidate, pendingInstall.file)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (AppBuildInfo.updateCheckEnabled &&
            !AppBuildInfo.isDevBuild &&
            !autoCheckStarted &&
            store.autoCheckUpdate
        ) {
            triggerUpdateCheck(manual = false)
        }
    }

    val actions = UpdateCenterActions(
        updateCheckEnabled = AppBuildInfo.updateCheckEnabled && !AppBuildInfo.isDevBuild,
        manualCheck = { triggerUpdateCheck(manual = true) },
    )

    Box(Modifier.fillMaxSize()) {
        content(actions)
        UpdateDialog(
            state = updateDialogState,
            downloadingUpdate = downloadingUpdate,
            downloadProgressBytes = downloadProgressBytes,
            downloadProgressTotalBytes = downloadProgressTotalBytes,
            autoCheckUpdate = autoCheckUpdate,
            onAutoCheckUpdateChange = { value ->
                autoCheckUpdate = value
                store.autoCheckUpdate = value
            },
            onDismiss = {
                updateCheckJob?.cancel()
                updateDialogState = UpdateDialogState.Hidden
            },
            onDownloadUpdate = { candidate, asset -> triggerUpdateDownload(candidate, asset) },
            onInstallDownloadedApk = { candidate, file -> startInstallIfAllowed(candidate, file) },
            onError = { message ->
                updateDialogState = UpdateDialogState.Error(
                    detail = message,
                    title = "无法打开授权页面",
                )
            },
            updateInstaller = updateInstaller,
            onCancelDownload = { cancelUpdateDownload() },
        )
    }
}
