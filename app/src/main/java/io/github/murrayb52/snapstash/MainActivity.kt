package io.github.murrayb52.snapstash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    /** null = home screen with the two buttons; otherwise the camera for that category. */
    private var category by mutableStateOf<Category?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        category = Category.fromId(savedInstanceState?.getString(STATE_CATEGORY))
            ?: Category.fromIntent(intent)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val current = category
                if (current == null) {
                    HomeScreen(onOpen = { category = it })
                } else {
                    CameraScreen(category = current, onBack = { category = null })
                }
            }
        }
    }

    // A shortcut tapped while the app is already open switches straight to that camera.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Category.fromIntent(intent)?.let { category = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CATEGORY, category?.id)
    }

    private companion object {
        const val STATE_CATEGORY = "category"
    }
}
