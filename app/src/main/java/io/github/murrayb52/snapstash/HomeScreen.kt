package io.github.murrayb52.snapstash

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(buttons: List<CaptureButton>, onOpen: (CaptureButton) -> Unit, onSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Stash.Cabinet)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("SNAPSTASH", style = Stash.TabLabel, color = Stash.Manila)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (buttons.isEmpty()) "Set up your cabinet" else "Where does this one go?",
                    color = Stash.Paper,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 34.sp,
                )
                if (buttons.isNotEmpty()) {
                    Text("Tap a folder to open its camera.", color = Stash.Muted, fontSize = 15.sp)
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Stash.Paper)
            }
        }
        Spacer(Modifier.height(24.dp))

        if (buttons.isEmpty()) {
            EmptyCabinet(onSettings, Modifier.weight(1f))
        } else {
            FolderStack(buttons, onOpen, Modifier.weight(1f))
        }
    }
}

/** Big folders that share the screen; past a handful they keep a minimum size and scroll. */
@Composable
private fun FolderStack(buttons: List<CaptureButton>, onOpen: (CaptureButton) -> Unit, modifier: Modifier) {
    val gap = 14.dp
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val n = buttons.size
        val fit = (maxHeight - gap * (n - 1) - 16.dp) / n
        val height = fit.coerceIn(132.dp, 220.dp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            buttons.forEachIndexed { i, button ->
                FolderCard(
                    button = button,
                    index = i + 1,
                    onClick = { onOpen(button) },
                    modifier = Modifier.fillMaxWidth().height(height),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** One button, drawn as a coloured file folder: index on the tab, name and destination on the front. */
@Composable
fun FolderCard(button: CaptureButton, index: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val path = shortPath(FolderAccess.displayPath(Uri.parse(button.folderUri)))
    FolderGraphic(
        color = button.color,
        modifier = modifier.clip(FolderShape(110.dp, 22.dp, 18.dp)).clickable(onClick = onClick),
        tab = {
            Text(
                "%02d".format(index),
                color = Stash.Paper.copy(alpha = 0.85f),
                fontFamily = Stash.Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        },
        front = {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(
                        button.label,
                        color = Stash.Ink,
                        fontSize = 28.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        path,
                        color = Stash.Ink.copy(alpha = 0.6f),
                        fontFamily = Stash.Mono,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier
                        .size(52.dp)
                        .background(Stash.Ink.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_camera),
                        contentDescription = null,
                        tint = Stash.Ink,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun EmptyCabinet(onSettings: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width = 180.dp, height = 132.dp)
                .border(2.dp, Stash.Muted.copy(alpha = 0.5f), FolderShape(70.dp, 18.dp, 16.dp))
        )
        Text(
            "No folders yet",
            color = Stash.Paper,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Add a folder for each kind of photo you file. Each one gets its own big button and its own camera.",
            color = Stash.Muted,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Button(
            onClick = onSettings,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.height(52.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Add a folder", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
