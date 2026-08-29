package com.chloemlla.synapse.mobile.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Public scanner entry used by [SynapseMobileApp]. Handles the camera
 * permission gate, delegates straight to the CameraX + ML Kit scanner, and
 * offers decoding a QR code picked from the device gallery (no camera
 * permission required for that path).
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
    val scope = rememberCoroutineScope()
    var decodingImage by remember { mutableStateOf(false) }
    var pickError by remember { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            decodingImage = true
            pickError = null
            scope.launch {
                val raw = withContext(Dispatchers.IO) {
                    runCatching { decodeQrFromImageUri(context, uri) }.getOrNull()
                }
                decodingImage = false
                val payload = raw?.takeIf { it.isNotBlank() }
                if (payload != null) {
                    onQrCode(payload)
                } else {
                    pickError = "未在所选图片中识别到二维码，请选择清晰的二维码截图或照片。"
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (hasPermission) {
            MlKitCameraQrScanner(
                modifier = Modifier.fillMaxWidth(),
                onQrCode = onQrCode,
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
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
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !decodingImage,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            onClick = {
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        ) {
            if (decodingImage) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
            }
            Spacer(modifier = Modifier.size(8.dp))
            Text(if (decodingImage) "正在识别图片中的二维码…" else "从相册选择二维码")
        }
        pickError?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
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

private const val GALLERY_QR_MAX_DIMENSION = 2048

private fun decodeQrFromImageUri(context: Context, uri: Uri): String? {
    val resolver = context.contentResolver
    val rotation = resolver.openInputStream(uri)?.use { stream ->
        val orientation = runCatching {
            ExifInterface(stream)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }
    } ?: 0

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (
        bounds.outWidth / sampleSize > GALLERY_QR_MAX_DIMENSION ||
        bounds.outHeight / sampleSize > GALLERY_QR_MAX_DIMENSION
    ) {
        sampleSize *= 2
    }
    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, decodeOptions)
    } ?: return null
    val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )
    return try {
        Tasks.await(scanner.process(InputImage.fromBitmap(bitmap, rotation)))
            .firstNotNullOfOrNull { barcode -> barcode.rawValue?.takeIf { it.isNotBlank() } }
    } catch (error: Exception) {
        null
    } finally {
        scanner.close()
    }
}
