package io.github.murrayb52.snapstash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
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
fun CameraScreen(button: CaptureButton, onBack: () -> Unit) {
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
        CameraContent(button, onBack)
    } else {
        PermissionNeeded(
            showSettings = askedOnce,
            onRetry = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onBack = onBack,
        )
    }
}

@Composable
private fun CameraContent(button: CaptureButton, onBack: () -> Unit) {
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

    var savedCount by remember(button.id) { mutableIntStateOf(0) }
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

    // Warn straight away if the folder became unreachable since it was chosen.
    LaunchedEffect(button.folderUri) {
        if (!FolderAccess.hasAccess(context, Uri.parse(button.folderUri))) {
            error = "SnapStash no longer has access to ${FolderAccess.displayPath(Uri.parse(button.folderUri))}. " +
                "Photos can't be saved until you pick the folder again in Settings."
        }
    }

    LaunchedEffect(confirmationId) {
        if (confirmation != null) {
            delay(2_000)
            confirmation = null
        }
    }

    fun shoot() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        val name = namer.fileName(button.filePrefix, LocalDateTime.now())
        val folder = Uri.parse(button.folderUri)

        // Create the file in this button's folder *before* capturing, so a missing or
        // inaccessible folder is reported immediately instead of silently losing the shot.
        val (doc, stream) = try {
            FolderAccess.createPhoto(context, folder, name)
        } catch (e: Exception) {
            error = "Photo NOT saved.\n\nCan't write to ${FolderAccess.displayPath(folder)}. " +
                "It may have been moved, deleted or disconnected — pick the folder again in Settings." +
                "\n\n(${e.message ?: e.javaClass.simpleName})"
            return
        }

        inFlight++
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(stream).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    inFlight--
                    val closed = runCatching { stream.close() }
                    if (closed.isFailure) {
                        FolderAccess.delete(context, doc)
                        error = "Photo NOT saved ($name).\n\n${closed.exceptionOrNull()?.message ?: "Write failed"}"
                        return
                    }
                    savedCount++
                    confirmation = FolderAccess.displayName(context, doc) ?: name
                    confirmationId++
                }

                override fun onError(exception: ImageCaptureException) {
                    inFlight--
                    runCatching { stream.close() }
                    FolderAccess.delete(context, doc)
                    error = "Photo NOT saved ($name).\n\n${exception.message ?: "Unknown error"}"
                }
            },
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        CameraChrome(
            button = button,
            savedCount = savedCount,
            busy = inFlight > 0,
            savedName = confirmation,
            onBack = onBack,
            onShoot = { shoot() },
        )
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            containerColor = Stash.Drawer,
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = Stash.Danger) },
            title = { Text("Not saved") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        )
    }
}

/**
 * Everything drawn over the camera preview: where photos are going, how many were saved, the
 * "Saved" confirmation and the shutter. Kept separate from the camera so it can be screenshot-tested.
 */
@Composable
fun CameraChrome(
    button: CaptureButton,
    savedCount: Int,
    busy: Boolean,
    savedName: String?,
    onBack: () -> Unit,
    onShoot: () -> Unit,
) {
    val path = shortPath(FolderAccess.displayPath(Uri.parse(button.folderUri)))
    Column(Modifier.fillMaxSize()) {
        // Top: back, the destination folder, and the running count.
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                .statusBarsPadding()
                .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Stash.Paper)
            }
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier
                    .weight(1f)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniFolder(button.color, 30.dp, 24.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        button.label,
                        color = Stash.Paper,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        path,
                        color = Stash.Paper.copy(alpha = 0.6f),
                        fontFamily = Stash.Mono,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("$savedCount", color = Stash.Paper, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Stash.Mono)
                Text("SAVED", style = Stash.TabLabel.copy(fontSize = 9.sp, letterSpacing = 1.5.sp), color = Stash.Paper.copy(alpha = 0.6f))
            }
        }

        Spacer(Modifier.weight(1f))

        // Bottom: confirmation and shutter.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .navigationBarsPadding()
                .padding(top = 32.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.height(48.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                savedName?.let { name ->
                    Row(
                        Modifier
                            .background(Stash.Filed, RoundedCornerShape(50))
                            .padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Saved", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            name,
                            color = Color.White.copy(alpha = 0.85f),
                            fontFamily = Stash.Mono,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            // Shutter. Stays live while earlier shots save, so several in a row is one tap each.
            Box(
                Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Take photo", onClick = onShoot)
                    .border(4.dp, Stash.Paper, CircleShape)
                    .padding(9.dp)
                    .background(if (busy) lerp(button.color, Color.White, 0.5f) else button.color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_camera), contentDescription = null, tint = Stash.Ink.copy(alpha = 0.75f), modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun PermissionNeeded(showSettings: Boolean, onRetry: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(Stash.Cabinet).safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).background(Stash.Drawer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_camera), contentDescription = null, tint = Stash.Manila, modifier = Modifier.size(34.dp))
        }
        Text(
            "SnapStash needs the camera to take photos.",
            color = Stash.Paper,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(52.dp)) {
            Text("Allow camera", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        if (showSettings) {
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null))
                )
            }) { Text("Open app settings") }
        }
        TextButton(onClick = onBack) { Text("Back", color = Stash.Muted) }
    }
}
