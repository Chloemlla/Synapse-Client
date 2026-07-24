package com.chloemlla.synapse.mobile.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.huawei.hms.hmsscankit.RemoteView
import com.huawei.hms.ml.scan.HmsScan
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * QR scan backends available to the Compose surface.
 *
 * HMS Scan Kit ([Hms]) is preferred (same free `scanplus` path as PiliPlus —
 * no `agconnect-services.json` / AGConnect plugin). ML Kit + CameraX is the
 * automatic fallback when HMS linkage or runtime setup fails.
 */
enum class QrScanBackend {
    Hms,
    MlKit,
}

/**
 * Public scanner entry used by [SynapseMobileApp]. Keeps the ViewModel callback
 * contract (`onQrCode: (String) -> Unit`) stable across backend swaps.
 */
@Composable
fun PermissionAwareQrScanner(
    modifier: Modifier = Modifier,
    preferredBackend: QrScanBackend = QrScanBackend.Hms,
    onQrCode: (String) -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    if (hasPermission) {
        DualBackendQrScanner(
            modifier = modifier,
            preferredBackend = preferredBackend,
            onQrCode = onQrCode,
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "需要相机权限才能扫描网页登录二维码。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("授权相机并扫描")
                }
            }
        }
    }
}

@Composable
private fun DualBackendQrScanner(
    modifier: Modifier = Modifier,
    preferredBackend: QrScanBackend,
    onQrCode: (String) -> Unit,
) {
    var forceMlKit by remember(preferredBackend) { mutableStateOf(false) }
    val activeBackend = when {
        forceMlKit -> QrScanBackend.MlKit
        preferredBackend == QrScanBackend.Hms && HmsScanAvailability.isUsable() -> QrScanBackend.Hms
        else -> QrScanBackend.MlKit
    }

    when (activeBackend) {
        QrScanBackend.Hms -> {
            HmsRemoteQrScanner(
                modifier = modifier,
                onQrCode = onQrCode,
                onBackendUnavailable = {
                    // Runtime / linkage failure → fall back to CameraX + ML Kit.
                    forceMlKit = true
                },
            )
        }
        QrScanBackend.MlKit -> {
            MlKitCameraQrScanner(
                modifier = modifier,
                onQrCode = onQrCode,
            )
        }
    }
}

/**
 * Detects whether the free HMS Scan Kit (`scanplus`) classes are loadable.
 * No AppGallery / AGConnect secrets are required for this check.
 */
internal object HmsScanAvailability {
    fun isUsable(): Boolean = runCatching {
        Class.forName("com.huawei.hms.hmsscankit.RemoteView")
        Class.forName("com.huawei.hms.ml.scan.HmsScan")
        true
    }.getOrDefault(false)
}

/**
 * Huawei Scan Kit [RemoteView] embedded in Compose via [AndroidView].
 *
 * Free/public `com.huawei.hms:scanplus` path — no `agconnect-services.json`.
 * Lifecycle callbacks are forwarded from the host Activity when available.
 */
@Composable
private fun HmsRemoteQrScanner(
    modifier: Modifier = Modifier,
    onQrCode: (String) -> Unit,
    onBackendUnavailable: () -> Unit,
) {
    val context = LocalContext.current
    // RemoteView.Builder.setContext requires an Activity (not a bare Context).
    val activity = remember(context) { context.findActivity() }
    val consumed = remember { AtomicBoolean(false) }
    val remoteViewHolder = remember { arrayOfNulls<RemoteView>(1) }
    val unavailableReported = remember { AtomicBoolean(false) }

    fun reportUnavailable(host: FrameLayout? = null) {
        if (!unavailableReported.compareAndSet(false, true)) return
        // Defer Compose state writes off the AndroidView factory path.
        val postTarget = host ?: activity?.window?.decorView
        if (postTarget != null) {
            postTarget.post { onBackendUnavailable() }
        } else {
            onBackendUnavailable()
        }
    }

    // Without a host Activity HMS cannot build RemoteView — fall back immediately.
    if (activity == null) {
        DisposableEffect(Unit) {
            onBackendUnavailable()
            onDispose { }
        }
        return
    }

    DisposableEffect(activity) {
        val view = remoteViewHolder[0]
        try {
            view?.onStart()
            view?.onResume()
        } catch (_: Throwable) {
            reportUnavailable()
        }
        onDispose {
            try {
                remoteViewHolder[0]?.onPause()
            } catch (_: Throwable) {
            }
            try {
                remoteViewHolder[0]?.onStop()
            } catch (_: Throwable) {
            }
            try {
                remoteViewHolder[0]?.onDestroy()
            } catch (_: Throwable) {
            }
            remoteViewHolder[0] = null
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        factory = { viewContext ->
            val host = FrameLayout(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            try {
                val metrics = viewContext.resources.displayMetrics
                val scanFrameSize = (240 * metrics.density).toInt()
                val width = metrics.widthPixels.coerceAtLeast(1)
                // RemoteView uses screen metrics for the bounding box; for the
                // embedded 280dp preview use a centered square within the view.
                val height = (280 * metrics.density).toInt().coerceAtLeast(1)
                val rect = Rect(
                    width / 2 - scanFrameSize / 2,
                    height / 2 - scanFrameSize / 2,
                    width / 2 + scanFrameSize / 2,
                    height / 2 + scanFrameSize / 2,
                )
                val remoteView = RemoteView.Builder()
                    .setContext(activity)
                    .setBoundingBox(rect)
                    .setFormat(HmsScan.QRCODE_SCAN_TYPE)
                    .build()
                remoteView.setOnResultCallback { results ->
                    val raw = results
                        ?.firstOrNull()
                        ?.getOriginalValue()
                        ?.takeIf { it.isNotBlank() }
                    if (raw != null && consumed.compareAndSet(false, true)) {
                        onQrCode(raw)
                    }
                }
                remoteView.onCreate(Bundle())
                remoteViewHolder[0] = remoteView
                host.addView(
                    remoteView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                try {
                    remoteView.onStart()
                    remoteView.onResume()
                } catch (_: Throwable) {
                    reportUnavailable(host)
                }
            } catch (_: Throwable) {
                reportUnavailable(host)
            }
            host
        },
    )
}

/**
 * Legacy CameraX + ML Kit barcode path retained as fallback when HMS is unavailable.
 */
@Composable
@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
private fun MlKitCameraQrScanner(
    modifier: Modifier = Modifier,
    onQrCode: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val consumed = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            analyzerExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        factory = { viewContext ->
            val previewView = PreviewView(viewContext)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(viewContext)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val scanner = BarcodeScanning.getClient()
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage == null || consumed.get()) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees,
                        )
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                val raw = barcodes.firstNotNullOfOrNull { barcode ->
                                    barcode.rawValue?.takeIf { it.isNotBlank() }
                                }
                                if (raw != null && consumed.compareAndSet(false, true)) {
                                    onQrCode(raw)
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    }

                    runCatching {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
            previewView
        },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
