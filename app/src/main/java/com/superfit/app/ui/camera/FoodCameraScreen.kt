package com.superfit.app.ui.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.provider.Settings
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.ui.dashboard.NeonGreen
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

/**
 * Full-screen camera for logging food from a meal photo, a packet's barcode or its nutrition
 * label. Every result is shown in "Check your meal" first; nothing is saved until Log meal.
 * [onLogged] gets the meal to save and whether it is a packaged food.
 */
@Composable
fun FoodCameraScreen(
    controller: FoodCameraController,
    apiKey: String,
    onLogged: (NutritionEntryEntity, Boolean) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    controller.apiKey = apiKey
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            // The photo is shrunk to 1024 px before it is sent, so there is no need to capture more.
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1600, 1200), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                    )
                    .build()
            )
            .build()
    }

    var hasPermission by remember { mutableStateOf(hasCameraPermission(context)) }
    var askedForPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        askedForPermission = true
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    // Coming back from the app's settings page with the camera allowed.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasPermission = hasCameraPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler {
        if (controller.state == CameraState.Viewfinder) onClose() else controller.retake()
    }

    val cameraUnavailable = {
        Toast.makeText(context, "Camera isn't available right now", Toast.LENGTH_SHORT).show()
        onClose()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraPreview(
                mode = controller.mode,
                imageCapture = imageCapture,
                onBarcode = { code ->
                    if (code == null) {
                        controller.onNoBarcode()
                    } else if (controller.onBarcode(code)) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
                onUnavailable = cameraUnavailable
            )
        } else if (askedForPermission) {
            CameraPermissionExplainer(
                onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                }
            )
        }

        controller.photo?.let { photo ->
            Image(
                bitmap = photo.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        val state = controller.state
        if (hasPermission && state == CameraState.Viewfinder) {
            when (controller.mode) {
                CameraMode.BARCODE -> FrameGuide(widthFraction = 0.78f, heightFraction = 0.22f, hint = "Point at the barcode")
                CameraMode.LABEL -> FrameGuide(widthFraction = 0.8f, heightFraction = 0.55f, hint = "Fit the nutrition table here")
                CameraMode.MEAL -> {}
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close camera", tint = Color.White)
            }
            controller.banner?.let { message ->
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        if (hasPermission && state == CameraState.Viewfinder) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (controller.mode == CameraMode.BARCODE) {
                    // Keeps the mode switcher in the same place in every mode.
                    Spacer(modifier = Modifier.size(76.dp))
                } else {
                    ShutterButton(onClick = {
                        takePhoto(context, imageCapture, onPhoto = controller::onPhoto, onError = {
                            Toast.makeText(context, "Couldn't take the photo. Please try again.", Toast.LENGTH_SHORT).show()
                        })
                    })
                }
                ModeSwitcher(selected = controller.mode, onSelect = controller::changeMode)
            }
        }

        when (state) {
            is CameraState.Working -> WorkingOverlay(state.message)
            is CameraState.Failed -> FailedOverlay(state.message, onRetake = controller::retake, onClose = onClose)
            is CameraState.MealResult -> CheckMealSheet(
                result = state.result,
                onLog = { onLogged(it, false) },
                onRetake = controller::retake
            )
            is CameraState.PackResult -> CheckPackSheet(
                food = state.food,
                onLog = { onLogged(it, true) },
                onRetake = controller::retake
            )
            CameraState.Viewfinder -> {}
        }
    }
}

@Composable
private fun CameraPreview(
    mode: CameraMode,
    imageCapture: ImageCapture,
    onBarcode: (String?) -> Unit,
    onUnavailable: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build()
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            scanner.close()
        }
    }

    // Barcode mode only needs the live analysis; Meal and Label only need photos.
    val scanning = mode == CameraMode.BARCODE
    DisposableEffect(scanning, lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            try {
                val cameraProvider = future.get().also { provider = it }
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                cameraProvider.unbindAll()
                if (scanning) {
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(analysisExecutor, BarcodeAnalyzer(scanner) { code -> currentOnBarcode(code) }) }
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } else {
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                }
            } catch (e: Exception) {
                android.util.Log.w("FoodCamera", "Camera could not start", e)
                currentOnUnavailable()
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            provider?.unbindAll()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/** Hands each frame's barcode number, or null for none, to [onCode]. ML Kit calls back on the main thread. */
private class BarcodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val onCode: (String?) -> Unit
) : ImageAnalysis.Analyzer {
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (media == null) {
            image.close()
            return
        }
        scanner.process(InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees))
            .addOnSuccessListener { codes ->
                onCode(codes.firstNotNullOfOrNull { code -> code.rawValue?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } })
            }
            .addOnCompleteListener { image.close() }
    }
}

/** Decodes and turns the photo upright off the main thread, then hands it over on the main thread. */
private fun takePhoto(context: Context, imageCapture: ImageCapture, onPhoto: (Bitmap) -> Unit, onError: () -> Unit) {
    val main = ContextCompat.getMainExecutor(context)
    imageCapture.takePicture(
        Dispatchers.Default.asExecutor(),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = try {
                    image.toBitmap().rotated(image.imageInfo.rotationDegrees)
                } catch (e: Exception) {
                    null
                } finally {
                    image.close()
                }
                main.execute { if (bitmap != null) onPhoto(bitmap) else onError() }
            }

            override fun onError(exception: ImageCaptureException) {
                android.util.Log.w("FoodCamera", "Photo capture failed", exception)
                main.execute { onError() }
            }
        }
    )
}

private fun Bitmap.rotated(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Composable
private fun FrameGuide(widthFraction: Float, heightFraction: Float, hint: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .fillMaxHeight(heightFraction)
                .border(3.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(24.dp))
        )
        Text(
            text = hint,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 14.dp, start = 24.dp, end = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ShutterButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(7.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.PhotoCamera, contentDescription = "Take photo", tint = Color.Black.copy(alpha = 0.7f))
    }
}

@Composable
private fun ModeSwitcher(selected: CameraMode, onSelect: (CameraMode) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CameraMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Text(
                text = mode.label,
                color = if (isSelected) Color.Black else Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) Color.White else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            )
        }
    }
}

@Composable
private fun WorkingOverlay(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = NeonGreen, strokeWidth = 3.dp)
            Text(text = message, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FailedOverlay(message: String, onRetake: () -> Unit, onClose: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = message, color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
            Button(
                onClick = onRetake,
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.White)
            ) {
                Text("Retake", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onClose) {
                Text("Close", color = Color.White)
            }
        }
    }
}

@Composable
private fun CameraPermissionExplainer(onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Superfit needs the camera to read your meal or a barcode.",
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onAllow,
            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.White)
        ) {
            Text("Allow camera", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onOpenSettings) {
            Text("Open settings", color = Color.White)
        }
    }
}
