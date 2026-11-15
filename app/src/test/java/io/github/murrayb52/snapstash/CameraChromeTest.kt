package io.github.murrayb52.snapstash

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CameraChromeTest {
    @get:Rule val compose = createComposeRule()

    private val receipts = CaptureButton(id = "1", label = "Receipts", folderUri = "content://x/1")

    @Test
    fun oneShotIsTheDefaultAndTheToggleKeepsTheCameraOpen() {
        var keepOpen by mutableStateOf(false)
        compose.setContent {
            SnapStashTheme {
                CameraChrome(
                    button = receipts,
                    savedCount = 0,
                    busy = false,
                    savedName = null,
                    keepOpen = keepOpen,
                    location = LocationState.OFF,
                    onBack = {},
                    onShoot = {},
                    onKeepOpen = { keepOpen = it },
                )
            }
        }
        val toggle = compose.onNodeWithText("MORE PHOTOS")
        toggle.assertIsDisplayed().assertIsOff()
        toggle.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(true, keepOpen)
        toggle.assertIsOn()
        toggle.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(false, keepOpen)
    }
}
