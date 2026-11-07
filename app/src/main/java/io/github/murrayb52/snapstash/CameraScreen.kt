package io.github.murrayb52.snapstash

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** One namer for the whole process so same-second shots never collide. */
private val namer = PhotoNamer()

@Composable
fun CameraScreen(category: Category, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var askedOnce by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        askedOnce = true
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (hasPermission) {
        CameraContent(category, onBack)
    } else {
        PermissionNeeded(
            showSettings = askedOnce,
            onRetry = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onBack = onBack,
        )
    }
}

@Composable
private fun CameraContent(category: Category, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }
    // MAXIMIZE_QUALITY + the camera's full default resolution keeps receipt text legible.
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    var savedCount by remember(category) { mutableIntStateOf(0) }
    var inFlight by remember { mutableIntStateOf(0) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var confirmationId by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    // Bind preview + capture to the screen's lifecycle.
    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
            } catch (e: Exception) {
                error = "Couldn't start the camera: ${e.message ?: e.javaClass.simpleName}"
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            if (future.isDone) runCatching { future.get().unbindAll() }
        }
    }

    // The UI is locked to portrait, so tell the camera how the phone is actually held;
    // otherwise landscape shots of a wide board would be saved sideways.
    DisposableEffect(Unit) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                imageCapture.targetRotation = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }

    LaunchedEffect(confirmationId) {
        if (confirmation != null) {
            delay(2_000)
            confirmation = null
        }
    }

    fun shoot() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        val name = namer.fileName(category, LocalDateTime.now())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, PhotoNamer.relativePath(category))
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ).build()

        inFlight++
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    inFlight--
                    val uri = output.savedUri
                    if (uri == null) {
                        error = "The camera reported success but returned no file for $name."
                        return
                    }
                    savedCount++
                    confirmation = "✓ Saved ${displayName(context, uri) ?: name}"
                    confirmationId++
                }

                override fun onError(exception: ImageCaptureException) {
                    inFlight--
                    error = "Photo NOT saved ($name).\n\n${exception.message ?: "Unknown error"}"
                }
            },
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            // Top bar: which folder we're shooting into, and how many saved so far.
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onBack) { Text("‹ Back", color = Color.White, fontSize = 18.sp) }
                Text(
                    category.label,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(category.color, RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
                Text(
                    "$savedCount saved",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                confirmation?.let {
                    Text(
                        it,
                        color = Color.White,
                        modifier = Modifier
                            .padding(16.dp)
                            .background(Color(0xCC2E7D32), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }

            // Shutter. Stays live while earlier shots save, so several in a row is one tap each.
            Box(Modifier.fillMaxWidth().padding(bottom = 32.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .clickable { shoot() }
                        .border(5.dp, Color.White, CircleShape)
                        .padding(9.dp)
                        .background(if (inFlight > 0) Color.LightGray else Color.White, CircleShape),
                )
            }
        }
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Not saved") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        )
    }
}

private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(
        uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null
    )?.use { if (it.moveToFirst()) it.getString(0) else null }
}.getOrNull()

@Composable
private fun PermissionNeeded(showSettings: Boolean, onRetry: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "SnapStash needs the camera to take photos.",
            color = Color.White,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text("Allow camera") }
        if (showSettings) {
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null))
                )
            }) { Text("Open app settings") }
        }
        TextButton(onClick = onBack) { Text("Back") }
    }
}
