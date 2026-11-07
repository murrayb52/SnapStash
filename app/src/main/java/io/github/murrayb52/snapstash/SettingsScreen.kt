package io.github.murrayb52.snapstash

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Add, rename, re-folder, reorder and delete buttons. This is the only setup in the app. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val buttons = ButtonStore.buttons

    /** The button being added or edited; null when no dialog is open. */
    var editing by remember { mutableStateOf<CaptureButton?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CaptureButton?>(null) }

    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back", color = Color.White, fontSize = 18.sp) }
            Text("Buttons", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "Each button saves its photos into its own folder. Tap a button to change its name or folder.",
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(vertical = 8.dp),
        )

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(buttons, key = { _, b -> b.id }) { index, button ->
                ButtonRow(
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
        }

        Button(
            onClick = {
                editing = CaptureButton(label = "", folderUri = "", colorIndex = ButtonStore.nextColorIndex())
                isNew = true
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text("+ Add button", fontSize = 18.sp) }

        val version = remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        }
        Text(
            "SnapStash ${version ?: ""}".trim(),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
        )
    }

    editing?.let { draft ->
        EditButtonDialog(
            initial = draft,
            title = if (isNew) "New button" else "Edit button",
            onDismiss = {
                editing = null
                FolderAccess.releaseUnused(context, ButtonStore.buttons)
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
            title = { Text("Delete “${button.label}”?") },
            text = { Text("Photos already saved stay in their folder. Only the button is removed.") },
            confirmButton = {
                TextButton(onClick = {
                    ButtonStore.delete(context, button.id)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ButtonRow(
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
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).background(button.color, CircleShape))
            TextButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                Column(Modifier.fillMaxWidth()) {
                    Text(button.label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        FolderAccess.displayPath(folder),
                        color = if (reachable) Color.White.copy(alpha = 0.7f) else Color(0xFFFF8A80),
                    )
                    if (!reachable) Text("Folder access lost — tap to pick it again", color = Color(0xFFFF8A80))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onMove(-1) }, enabled = canMoveUp) { Text("↑") }
            TextButton(onClick = { onMove(1) }, enabled = canMoveDown) { Text("↓") }
            if (Shortcuts.canPin(context)) TextButton(onClick = onPin) { Text("Add to home screen") }
            TextButton(onClick = onDelete) { Text("Delete", color = Color(0xFFFF8A80)) }
        }
    }
}

@Composable
private fun EditButtonDialog(
    initial: CaptureButton,
    title: String,
    onDismiss: () -> Unit,
    onSave: (CaptureButton) -> Unit,
) {
    val context = LocalContext.current
    var label by remember { mutableStateOf(initial.label) }
    var folderUri by remember { mutableStateOf(initial.folderUri) }
    var problem by remember { mutableStateOf<String?>(null) }

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Button name") },
                    singleLine = true,
                )
                if (label.isNotBlank()) {
                    Text("Files will be named ${CaptureButton.slug(label)}_2026-11-07_121305.jpg")
                }
                OutlinedButton(onClick = { picker.launch(FolderAccess.pickerStartUri()) }) {
                    Text(if (folderUri.isEmpty()) "Choose folder…" else "Change folder…")
                }
                if (folderUri.isNotEmpty()) Text(FolderAccess.displayPath(Uri.parse(folderUri)))
                problem?.let { Text(it, color = Color(0xFFFF8A80)) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(initial.copy(label = label.trim(), folderUri = folderUri)) },
                enabled = label.isNotBlank() && folderUri.isNotEmpty(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
