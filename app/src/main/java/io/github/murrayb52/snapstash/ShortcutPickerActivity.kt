package io.github.murrayb52.snapstash

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Opened by the launcher when the user adds a "SnapStash camera" shortcut from its widgets /
 * shortcuts list (ACTION_CREATE_SHORTCUT). The user picks one of their buttons, and the launcher
 * places a shortcut that opens that button's camera directly.
 */
class ShortcutPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ButtonStore.init(this)
        setResult(Activity.RESULT_CANCELED)

        if (intent?.action != Intent.ACTION_CREATE_SHORTCUT) {
            finish()
            return
        }

        setContent {
            SnapStashTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Stash.Cabinet)
                        .safeDrawingPadding()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val buttons = ButtonStore.buttons
                    Text("NEW SHORTCUT", style = Stash.TabLabel, color = Stash.Manila)
                    Text(
                        if (buttons.isEmpty()) "No folders yet" else "Which folder should it open?",
                        color = Stash.Paper,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (buttons.isEmpty()) {
                        Text(
                            "Add a folder in SnapStash first, then add the shortcut again.",
                            color = Stash.Muted,
                            fontSize = 15.sp,
                        )
                        Button(
                            onClick = {
                                startActivity(Intent(this@ShortcutPickerActivity, MainActivity::class.java))
                                finish()
                            },
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("Open SnapStash", fontSize = 17.sp) }
                    }
                    Spacer(Modifier.height(4.dp))
                    buttons.forEachIndexed { i, button ->
                        FolderCard(
                            button = button,
                            index = i + 1,
                            onClick = { choose(button) },
                            modifier = Modifier.fillMaxWidth().height(132.dp),
                        )
                    }
                    TextButton(onClick = { finish() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Cancel", color = Stash.Muted, fontSize = 16.sp)
                    }
                }
            }
        }
    }

    private fun choose(button: CaptureButton) {
        setResult(Activity.RESULT_OK, Shortcuts.createResultIntent(this, button))
        finish()
    }
}
