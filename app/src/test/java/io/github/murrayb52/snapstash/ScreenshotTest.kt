package io.github.murrayb52.snapstash

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the main screens to PNGs so the UI can be reviewed without a phone. They're only
 * written when run with -Proborazzi.test.record=true (CI does this and publishes them).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun tree(name: String) =
        "content://com.android.externalstorage.documents/tree/primary%3APictures%2FSnapStash%2F$name"

    private val sample = listOf(
        CaptureButton(id = "1", label = "Receipts", folderUri = tree("Receipts"), colorIndex = 0),
        CaptureButton(id = "2", label = "Whiteboards", folderUri = tree("Whiteboards"), colorIndex = 1),
        CaptureButton(id = "3", label = "Vitality", folderUri = tree("Vitality"), colorIndex = 2),
        CaptureButton(id = "4", label = "Medical aid claims", folderUri = tree("Medical"), colorIndex = 3),
    )

    @Before
    fun grantFolders() {
        // All folders reachable except the last, so Settings also shows the "access lost" state.
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        sample.dropLast(1).forEach {
            runCatching {
                resolver.takePersistableUriPermission(
                    Uri.parse(it.folderUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        ButtonStore.buttons.clear()
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/screenshots/$name.png")

    @Test
    fun home() {
        compose.setContent { SnapStashTheme { HomeScreen(sample, onOpen = {}, onSettings = {}) } }
        shot("1_home")
    }

    @Test
    fun homeTwoFolders() {
        compose.setContent { SnapStashTheme { HomeScreen(sample.take(2), onOpen = {}, onSettings = {}) } }
        shot("2_home_two_folders")
    }

    @Test
    fun homeEmpty() {
        compose.setContent { SnapStashTheme { HomeScreen(emptyList(), onOpen = {}, onSettings = {}) } }
        shot("3_home_empty")
    }

    @Test
    fun settings() {
        ButtonStore.buttons.addAll(sample)
        compose.setContent { SnapStashTheme { SettingsScreen(onBack = {}) } }
        shot("4_settings")
    }

    @Test
    fun editDialog() {
        // The form as shown inside its dialog, over a dimmed screen.
        compose.setContent {
            SnapStashTheme {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    EditFolderCard(initial = sample[0], title = "Edit folder", onDismiss = {}, onSave = {})
                }
            }
        }
        shot("5_edit_folder")
    }

    @Test
    fun camera() {
        compose.setContent {
            SnapStashTheme {
                // Stand-in for the live preview.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(Color(0xFF5B6470), Color(0xFF2B2F36), Color(0xFF8A7F6E))))
                ) {
                    CameraChrome(
                        button = sample[0],
                        savedCount = 3,
                        busy = false,
                        savedName = "receipts_2026-11-07_181305.jpg",
                        onBack = {},
                        onShoot = {},
                    )
                }
            }
        }
        shot("6_camera")
    }
}
