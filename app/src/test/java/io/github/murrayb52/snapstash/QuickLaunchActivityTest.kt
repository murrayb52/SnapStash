package io.github.murrayb52.snapstash

import android.content.Context
import android.os.SystemClock
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class QuickLaunchActivityTest {

    private val sample = listOf(
        CaptureButton(id = "1", label = "Receipts", folderUri = "content://x/1", colorIndex = 0),
        CaptureButton(id = "2", label = "Whiteboards", folderUri = "content://x/2", colorIndex = 1),
        CaptureButton(id = "3", label = "Vitality", folderUri = "content://x/3", colorIndex = 2),
        CaptureButton(id = "4", label = "Medical aid claims", folderUri = "content://x/4", colorIndex = 3),
    )

    @Before
    fun folders() {
        ButtonStore.init(ApplicationProvider.getApplicationContext<Context>())
        ButtonStore.buttons.clear()
        ButtonStore.buttons.addAll(sample)
    }

    private fun launch() = Robolectric.buildActivity(QuickLaunchActivity::class.java).setup().get()

    private fun touch(activity: QuickLaunchActivity, action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val ev = MotionEvent.obtain(now, now, action, x, y, 0)
        activity.dispatchTouchEvent(ev)
        ev.recycle()
    }

    private fun viewDescribed(activity: QuickLaunchActivity, description: String): View {
        val found = ArrayList<View>()
        activity.window.decorView.findViewsWithText(found, description, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
        return found.single()
    }

    @Test
    fun swipeUpOpensTheSystemCamera() {
        val activity = launch()
        val density = activity.resources.displayMetrics.density
        touch(activity, MotionEvent.ACTION_DOWN, 500f, 1800f)
        touch(activity, MotionEvent.ACTION_MOVE, 505f, 1800f - (QuickLaunchActivity.SWIPE_DP + 4) * density)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA, started.action)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun shortDragDoesNothing() {
        val activity = launch()
        touch(activity, MotionEvent.ACTION_DOWN, 500f, 1800f)
        touch(activity, MotionEvent.ACTION_MOVE, 500f, 1795f)
        touch(activity, MotionEvent.ACTION_UP, 500f, 1795f)

        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun sidewaysSwipeDoesNothing() {
        val activity = launch()
        touch(activity, MotionEvent.ACTION_DOWN, 100f, 1800f)
        touch(activity, MotionEvent.ACTION_MOVE, 900f, 1650f)

        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun tappingAFolderOpensItsCamera() {
        val activity = launch()
        viewDescribed(activity, "Whiteboards camera").performClick()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(MainActivity::class.java.name, started.component?.className)
        assertEquals("2", started.getStringExtra(Shortcuts.EXTRA_BUTTON_ID))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun tappingOutsideTheCardCloses() {
        val activity = launch()
        val card = viewDescribed(activity, "SnapStash quick launcher")
        assertFalse(activity.isFinishing)
        (card.parent as View).performClick()

        assertTrue(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun screenshot() {
        launch().window.decorView.captureRoboImage("build/screenshots/7_quick_launcher.png")
    }

    @Test
    fun screenshotNoFolders() {
        ButtonStore.buttons.clear()
        launch().window.decorView.captureRoboImage("build/screenshots/8_quick_launcher_empty.png")
    }
}
