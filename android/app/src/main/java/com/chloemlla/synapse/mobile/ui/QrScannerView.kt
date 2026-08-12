package com.chloemlla.synapse.mobile.ui

import android.Manifest
import android.content.pm.PackageManager
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
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Public scanner entry used by [SynapseMobileApp]. Handles the camera
 * permission gate and delegates straight to the CameraX + ML Kit scanner.
 */
@Composable
fun PermissionAwareQrScanner(
    modifier: Modifier = Modifier,
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
        MlKitCameraQrScanner(
            modifier = modifier,
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

/**
 * Primary CameraX + ML Kit barcode scanning path.
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
    val disposed = remember { AtomicBoolean(false) }
    val scannerRef = remember { AtomicReference<BarcodeScanner?>(null) }
    val analysisRef = remember { AtomicReference<ImageAnalysis?>(null) }
    val cameraProviderRef = remember { AtomicReference<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            analysisRef.getAndSet(null)?.clearAnalyzer()
            cameraProviderRef.getAndSet(null)?.unbindAll()
            scannerRef.getAndSet(null)?.close()
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
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            val scanner = BarcodeScanning.getClient(options)
            scannerRef.set(scanner)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(viewContext)
            cameraProviderFuture.addListener(
                {
                    if (disposed.get()) return@addListener
                    val cameraProvider = try {
                        cameraProviderFuture.get()
                    } catch (error: Exception) {
                        return@addListener
                    }
                    if (disposed.get()) return@addListener
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysisRef.set(it) }
                    analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                        if (disposed.get()) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        val mediaImage = imageProxy.image
                        if (mediaImage == null || consumed.get()) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        val inputImage = try {
                            InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees,
                            )
                        } catch (error: Exception) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        val task = try {
                            scanner.process(inputImage)
                        } catch (error: Exception) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        task.addOnSuccessListener { barcodes ->
                            val raw = barcodes.firstNotNullOfOrNull { barcode ->
                                barcode.rawValue?.takeIf { it.isNotBlank() }
                            }
                            if (raw != null && consumed.compareAndSet(false, true)) {
                                onQrCode(raw)
                            }
                        }.addOnCompleteListener {
                            imageProxy.close()
                        }
                    }
                    cameraProviderRef.set(cameraProvider)
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
