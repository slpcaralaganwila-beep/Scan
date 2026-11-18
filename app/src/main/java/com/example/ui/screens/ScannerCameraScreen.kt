package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.AppLanguage
import com.example.ui.tr
import java.io.File

@Composable
fun ScannerCameraScreen(
    language: AppLanguage,
    draftCount: Int,
    isProcessing: Boolean,
    createOutputFile: () -> File,
    onPhotoCaptured: (File) -> Unit,
    onImportPhotos: (List<Uri>) -> Unit,
    onOpenReview: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportPhotos(uris)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var torchEnabled by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (hasCameraPermission && cameraError == null) {
                val previewView = remember {
                    PreviewView(context).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                }

                DisposableEffect(lifecycleOwner) {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    val mainExecutor = ContextCompat.getMainExecutor(context)
                    var providerRef: ProcessCameraProvider? = null

                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            providerRef = cameraProvider
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                .build()

                            val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                                CameraSelector.DEFAULT_BACK_CAMERA
                            } else {
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            }

                            cameraProvider.unbindAll()
                            val boundCamera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                selector,
                                preview,
                                capture
                            )
                            camera = boundCamera
                            imageCapture = capture
                        } catch (e: Exception) {
                            cameraError = e.localizedMessage ?: "Camera unavailable"
                        }
                    }, mainExecutor)

                    onDispose {
                        providerRef?.unbindAll()
                    }
                }

                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                // Paper Alignment Viewfinder Overlay with animated scanning beam
                PaperGuideOverlay(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp, vertical = 128.dp)
                )
            } else {
                // Permission Denied or No Camera Fallback
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.widthIn(max = 420.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(76.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.CameraAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = tr(
                                language,
                                R.string.camera_permission_title_si,
                                R.string.camera_permission_title_en
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = tr(
                                language,
                                R.string.camera_permission_desc_si,
                                R.string.camera_permission_desc_en
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        if (!hasCameraPermission) {
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("grant_camera_permission_button")
                            ) {
                                Text(tr(language, R.string.grant_permission_si, R.string.grant_permission_en))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("fallback_import_photo_button")
                        ) {
                            Icon(imageVector = Icons.Filled.PhotoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(tr(language, R.string.action_import_gallery_si, R.string.action_import_gallery_en))
                        }
                    }
                }
            }

            // Top Camera Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("camera_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = tr(language, R.string.back_cd_si, R.string.back_cd_en),
                            tint = Color.White
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = tr(language, R.string.camera_guide_si, R.string.camera_guide_en),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f)
                ) {
                    IconButton(
                        onClick = {
                            val next = !torchEnabled
                            torchEnabled = next
                            camera?.cameraControl?.enableTorch(next)
                        },
                        enabled = hasCameraPermission && camera != null,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("camera_flash_button")
                    ) {
                        Icon(
                            imageVector = if (torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            contentDescription = tr(language, R.string.flash_cd_si, R.string.flash_cd_en),
                            tint = if (torchEnabled) Color(0xFFFBBF24) else Color.White
                        )
                    }
                }
            }

            // Bottom Capture Bar
            if (hasCameraPermission && cameraError == null) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Gallery Picker Button
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.16f),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .testTag("camera_gallery_button")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(50.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoLibrary,
                                    contentDescription = tr(
                                        language,
                                        R.string.action_import_gallery_si,
                                        R.string.action_import_gallery_en
                                    ),
                                    tint = Color.White
                                )
                            }
                        }

                        // Shutter Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .border(4.dp, Color.White, CircleShape)
                                .padding(6.dp)
                                .clip(CircleShape)
                                .background(if (isCapturing || isProcessing) Color(0xFF14B8A6) else Color.White)
                                .clickable(enabled = !isCapturing && !isProcessing && imageCapture != null) {
                                    val cap = imageCapture ?: return@clickable
                                    isCapturing = true
                                    val outFile = createOutputFile()
                                    val opts = ImageCapture.OutputFileOptions.Builder(outFile).build()
                                    cap.takePicture(
                                        opts,
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageSavedCallback {
                                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                isCapturing = false
                                                onPhotoCaptured(outFile)
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                isCapturing = false
                                            }
                                        }
                                    )
                                }
                                .testTag("camera_capture_button")
                        ) {
                            if (isCapturing || isProcessing) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // Review Draft Pill or Spacer
                        if (draftCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF14B8A6),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(onClick = onOpenReview)
                                    .testTag("camera_done_button")
                            ) {
                                Text(
                                    text = tr(
                                        language,
                                        R.string.finish_scan_si,
                                        R.string.finish_scan_en,
                                        draftCount
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFF042F2E),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.size(50.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaperGuideOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scan_beam")
    val beamProgress by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beam_y"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cornerLen = 34.dp.toPx()
        val strokeW = 3.5.dp.toPx()
        val bracketColor = Color(0xFF2DD4BF)

        // Subtle inner guide rectangle
        drawRect(
            color = Color.White.copy(alpha = 0.22f),
            topLeft = Offset.Zero,
            size = Size(w, h),
            style = Stroke(width = 1.dp.toPx())
        )

        // Glowing scan line
        val beamY = h * beamProgress
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF2DD4BF).copy(alpha = 0.45f),
                    Color.Transparent
                ),
                startY = beamY - 22.dp.toPx(),
                endY = beamY + 22.dp.toPx()
            ),
            topLeft = Offset(0f, beamY - 22.dp.toPx()),
            size = Size(w, 44.dp.toPx())
        )
        drawLine(
            color = Color(0xFF5EEAD4),
            start = Offset(4.dp.toPx(), beamY),
            end = Offset(w - 4.dp.toPx(), beamY),
            strokeWidth = 2.dp.toPx()
        )

        // Four corner brackets
        val path = Path().apply {
            // Top-left
            moveTo(0f, cornerLen)
            lineTo(0f, 0f)
            lineTo(cornerLen, 0f)
            // Top-right
            moveTo(w - cornerLen, 0f)
            lineTo(w, 0f)
            lineTo(w, cornerLen)
            // Bottom-right
            moveTo(w, h - cornerLen)
            lineTo(w, h)
            lineTo(w - cornerLen, h)
            // Bottom-left
            moveTo(cornerLen, h)
            lineTo(0f, h)
            lineTo(0f, h - cornerLen)
        }
        drawPath(
            path = path,
            color = bracketColor,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
    }
}
