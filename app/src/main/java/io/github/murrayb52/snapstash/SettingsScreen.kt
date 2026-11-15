package io.github.murrayb52.snapstash

import android.net.Uri
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Add, rename, re-folder, recolour, reorder and delete folders. This is the only setup in the app. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val buttons = ButtonStore.buttons

    /** The button being added or edited; null when no dialog is open. */
    var editing by remember { mutableStateOf<CaptureButton?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CaptureButton?>(null) }

    Column(Modifier.fillMaxSize().background(Stash.Cabinet).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Stash.Paper)
            }
            Column {
                Text("SETTINGS", style = Stash.TabLabel, color = Stash.Manila)
                Text("Your folders", color = Stash.Paper, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            "Each folder is a button on the home screen. Its photos are saved to the location you pick. Tap a folder to edit it.",
            color = Stash.Muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(buttons, key = { _, b -> b.id }) { index, button ->
                FolderRow(
                    button = button,
                    canMoveUp = index > 0,
                    canMoveDown = index < buttons.lastIndex,
                    onEdit = { editing = button; isNew = false },
                    onMove = { ButtonStore.move(context, button.id, it) },
                    onDelete = { deleting = button },
                    onPin = {
                        if (!Shortcuts.requestPin(context, button)) {
                            Toast.makeText(context, "Your launcher can't add shortcuts", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
            item(key = "camera") { CameraSection() }
            item(key = "quick") { QuickLauncherSection() }
        }

        Column(Modifier.padding(16.dp)) {
            Button(
                onClick = {
                    editing = CaptureButton(label = "", folderUri = "", colorIndex = ButtonStore.nextColorIndex())
                    isNew = true
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New folder", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
            }
            Text(
                "SnapStash ${version ?: ""}".trim(),
                color = Stash.Muted.copy(alpha = 0.7f),
                fontFamily = Stash.Mono,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp),
            )
        }
    }

    editing?.let { draft ->
        EditFolderDialog(
            initial = draft,
            title = if (isNew) "New folder" else "Edit folder",
            onDismiss = {
                editing = null
                FolderAccess.releaseUnused(context, ButtonStore.buttons)
                FolderIcons.releaseUnused(context, ButtonStore.buttons)
            },
            onSave = {
                ButtonStore.upsert(context, it)
                editing = null
            },
        )
    }

    deleting?.let { button ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Stash.Drawer,
            title = { Text("Delete “${button.label}”?") },
            text = { Text("Photos already saved stay where they are. Only the button is removed.", color = Stash.Muted) },
            confirmButton = {
                TextButton(onClick = {
                    ButtonStore.delete(context, button.id)
                    deleting = null
                }) { Text("Delete", color = Stash.Danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** Camera settings: tag photos with where they were taken. */
@Composable
private fun CameraSection() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(CameraPrefs.saveLocation(context)) }
    var allowed by remember { mutableStateOf(LocationTracker.hasPermission(context)) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        allowed = results.values.any { it }
    }
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 22.dp)) {
        Text("CAMERA", style = Stash.TabLabel, color = Stash.Manila)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Save location", color = Stash.Paper, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        !on -> "Photos aren't tagged with where they were taken."
                        allowed -> "Photos are tagged with where they were taken, like the built-in camera."
                        else -> "Needs location permission. Tap the switch to allow it."
                    },
                    color = if (on && !allowed) Stash.Danger else Stash.Muted,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = on && allowed,
                onCheckedChange = { checked ->
                    on = checked
                    CameraPrefs.setSaveLocation(context, checked)
                    if (checked && !allowed) askLocation.launch(LocationTracker.PERMISSIONS)
                },
            )
        }
    }
}

/** Settings for SnapStash Quick, the gesture launcher card: how it animates in. */
@Composable
private fun QuickLauncherSection() {
    val context = LocalContext.current
    var chosen by remember { mutableStateOf(QuickLaunchPrefs.animation(context)) }
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 22.dp, bottom = 8.dp)) {
        Text("QUICK LAUNCHER", style = Stash.TabLabel, color = Stash.Manila)
        Text(
            "Opening animation",
            color = Stash.Paper,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text("How the SnapStash Quick card appears.", color = Stash.Muted, fontSize = 13.sp)
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickAnimation.entries.forEach { animation ->
                val selected = animation == chosen
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Stash.Manila else Stash.Drawer)
                        .clickable {
                            chosen = animation
                            QuickLaunchPrefs.setAnimation(context, animation)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        animation.label,
                        color = if (selected) Stash.Ink else Stash.Paper,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/**
 * The folder's picture if it has one (a rounded square edged in the folder's colour), otherwise a
 * small drawn folder in its colour.
 */
@Composable
fun FolderBadge(button: CaptureButton, width: Dp = 46.dp, height: Dp = 36.dp) {
    val context = LocalContext.current
    val icon = remember(button.iconFile) { FolderIcons.load(context, button.iconFile) }
    if (icon == null) {
        MiniFolder(button.color, width, height)
    } else {
        Image(
            icon.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width)
                .clip(RoundedCornerShape(width * 0.24f))
                .border(2.dp, button.color, RoundedCornerShape(width * 0.24f)),
        )
    }
}

/** A small folder icon in the button's colour. */
@Composable
fun MiniFolder(color: Color, width: Dp = 46.dp, height: Dp = 36.dp) {
    FolderGraphic(
        color = color,
        modifier = Modifier.size(width, height),
        tabWidth = width * 0.38f,
        tabHeight = height * 0.16f,
        corner = 5.dp,
    )
}

@Composable
private fun FolderRow(
    button: CaptureButton,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
) {
    val context = LocalContext.current
    val folder = Uri.parse(button.folderUri)
    val reachable = FolderAccess.hasAccess(context, folder)
    var menu by remember { mutableStateOf(false) }

    Surface(onClick = onEdit, shape = RoundedCornerShape(18.dp), color = Stash.Drawer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FolderBadge(button)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    button.label,
                    color = Stash.Paper,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    shortPath(FolderAccess.displayPath(folder)),
                    color = if (reachable) Stash.Muted else Stash.Danger,
                    fontFamily = Stash.Mono,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!reachable) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Stash.Danger, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Access lost. Tap to pick the folder again.", color = Stash.Danger, fontSize = 12.sp)
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More for ${button.label}", tint = Stash.Muted)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (canMoveUp) DropdownMenuItem(
                        text = { Text("Move up") },
                        leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, null) },
                        onClick = { menu = false; onMove(-1) },
                    )
                    if (canMoveDown) DropdownMenuItem(
                        text = { Text("Move down") },
                        leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, null) },
                        onClick = { menu = false; onMove(1) },
                    )
                    if (Shortcuts.canPin(context)) DropdownMenuItem(
                        text = { Text("Add to home screen") },
                        leadingIcon = { Icon(Icons.Filled.Home, null) },
                        onClick = { menu = false; onPin() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Stash.Danger) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Stash.Danger) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun EditFolderDialog(
    initial: CaptureButton,
    title: String,
    onDismiss: () -> Unit,
    onSave: (CaptureButton) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        EditFolderCard(initial, title, onDismiss, onSave)
    }
}

/** The add/edit form. Separate from its dialog window so it can be screenshot-tested. */
@Composable
internal fun EditFolderCard(
    initial: CaptureButton,
    title: String,
    onDismiss: () -> Unit,
    onSave: (CaptureButton) -> Unit,
) {
    val context = LocalContext.current
    var label by remember { mutableStateOf(initial.label) }
    var folderUri by remember { mutableStateOf(initial.folderUri) }
    var colorIndex by remember { mutableStateOf(initial.colorIndex) }
    var iconFile by remember { mutableStateOf(initial.iconFile) }
    var importing by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val draft = initial.copy(label = label.trim(), folderUri = folderUri, colorIndex = colorIndex, iconFile = iconFile)
    val scope = rememberCoroutineScope()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { FolderIcons.import(context, uri) } }
            importing = false
            result
                .onSuccess { iconFile = it; problem = null }
                .onFailure { problem = "Couldn't use that image. Try another one." }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            FolderAccess.persist(context, uri)
            folderUri = uri.toString()
            problem = null
            if (label.isBlank()) label = FolderAccess.displayPath(uri).substringAfterLast('/')
        } catch (e: SecurityException) {
            problem = "Android wouldn't let SnapStash keep access to that folder. Try another one."
        }
    }

    Surface(shape = RoundedCornerShape(28.dp), color = Stash.Drawer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, color = Stash.Paper, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SAVES TO", style = Stash.TabLabel, color = Stash.Muted)
                    Surface(
                        onClick = { picker.launch(FolderAccess.pickerStartUri()) },
                        shape = RoundedCornerShape(14.dp),
                        color = Stash.DrawerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            MiniFolder(draft.color, 34.dp, 26.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (folderUri.isEmpty()) "Choose a folder…"
                                else shortPath(FolderAccess.displayPath(Uri.parse(folderUri))),
                                color = if (folderUri.isEmpty()) Stash.Muted else Stash.Paper,
                                fontFamily = if (folderUri.isEmpty()) null else Stash.Mono,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(if (folderUri.isEmpty()) "Pick" else "Change", color = Stash.Manila, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("PICTURE", style = Stash.TabLabel, color = Stash.Muted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FolderBadge(draft, 44.dp, 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            when {
                                importing -> "Adding…"
                                iconFile == null -> "None. Shows the folder."
                                else -> "Shown on this folder"
                            },
                            color = Stash.Muted,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (iconFile != null) {
                            TextButton(onClick = { iconFile = null }) { Text("Remove", color = Stash.Muted) }
                        }
                        TextButton(
                            onClick = {
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            enabled = !importing,
                        ) { Text(if (iconFile == null) "Choose" else "Change", color = Stash.Manila, fontWeight = FontWeight.SemiBold) }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("COLOUR", style = Stash.TabLabel, color = Stash.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CaptureButton.PALETTE.indices.forEach { i ->
                            val selected = Math.floorMod(colorIndex, CaptureButton.PALETTE.size) == i
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .background(Color(CaptureButton.PALETTE[i]), CircleShape)
                                    .then(if (selected) Modifier.border(3.dp, Stash.Paper, CircleShape) else Modifier)
                                    .clickable { colorIndex = i },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Stash.Ink, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                if (label.isNotBlank()) {
                    Text(
                        "${CaptureButton.slug(label)}_${LocalDateTime.now().format(EXAMPLE_STAMP)}.jpg",
                        color = Stash.Muted,
                        fontFamily = Stash.Mono,
                        fontSize = 12.sp,
                    )
                }
                problem?.let { Text(it, color = Stash.Danger) }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Stash.Muted) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(draft) },
                        enabled = label.isNotBlank() && folderUri.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Save") }
                }
            }
    }
}

private val EXAMPLE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")
