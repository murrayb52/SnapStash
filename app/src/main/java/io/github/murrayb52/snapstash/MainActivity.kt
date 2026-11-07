package io.github.murrayb52.snapstash

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    /** Id of the button whose camera is open, or null for the home screen. */
    private var cameraId by mutableStateOf<String?>(null)
    private var showSettings by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always dark, whatever the system theme: light status/navigation icons on the cabinet.
        enableEdgeToEdge(SystemBarStyle.dark(AndroidColor.TRANSPARENT), SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        ButtonStore.init(this)

        if (savedInstanceState != null) {
            cameraId = savedInstanceState.getString(STATE_CAMERA)
            showSettings = savedInstanceState.getBoolean(STATE_SETTINGS)
        } else {
            openFromIntent(intent)
        }

        setContent {
            SnapStashTheme {
                val button = ButtonStore.get(cameraId)
                when {
                    showSettings -> SettingsScreen(onBack = { showSettings = false })
                    button != null -> CameraScreen(button = button, onBack = { cameraId = null })
                    else -> HomeScreen(
                        buttons = ButtonStore.buttons,
                        onOpen = { cameraId = it.id },
                        onSettings = { showSettings = true },
                    )
                }
            }
        }
    }

    // A shortcut tapped while the app is already open switches straight to that camera.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFromIntent(intent)
    }

    private fun openFromIntent(intent: Intent?) {
        val id = intent?.getStringExtra(Shortcuts.EXTRA_BUTTON_ID) ?: return
        if (ButtonStore.get(id) != null) {
            showSettings = false
            cameraId = id
        } else {
            Toast.makeText(this, "That button no longer exists", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CAMERA, cameraId)
        outState.putBoolean(STATE_SETTINGS, showSettings)
    }

    private companion object {
        const val STATE_CAMERA = "camera"
        const val STATE_SETTINGS = "settings"
    }
}
