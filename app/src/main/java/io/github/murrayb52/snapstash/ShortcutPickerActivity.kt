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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
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
            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .safeDrawingPadding()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val buttons = ButtonStore.buttons
                    Text(
                        if (buttons.isEmpty()) "No buttons yet.\nAdd one in SnapStash first, then add the shortcut again."
                        else "Which camera should this shortcut open?",
                        color = Color.White,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                    )
                    buttons.forEach { button ->
                        Button(
                            onClick = { choose(button) },
                            modifier = Modifier.fillMaxWidth().height(96.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = button.color, contentColor = Color.White),
                        ) {
                            Text(button.label, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                    }
                    if (buttons.isEmpty()) {
                        Button(onClick = {
                            startActivity(Intent(this@ShortcutPickerActivity, MainActivity::class.java))
                            finish()
                        }) { Text("Open SnapStash", fontSize = 18.sp) }
                    }
                    TextButton(onClick = { finish() }) { Text("Cancel", color = Color.White, fontSize = 16.sp) }
                }
            }
        }
    }

    private fun choose(button: CaptureButton) {
        setResult(Activity.RESULT_OK, Shortcuts.createResultIntent(this, button))
        finish()
    }
}
