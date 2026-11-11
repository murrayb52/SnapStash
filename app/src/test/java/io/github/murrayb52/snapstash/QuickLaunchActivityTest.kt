package io.github.murrayb52.snapstash

import android.content.Context
import android.os.Looper
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
import java.time.Duration

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

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun folders() {
        QuickLaunchPrefs.setAnimation(context, QuickLaunchPrefs.DEFAULT)
        ButtonStore.init(context)
        ButtonStore.buttons.clear()
        ButtonStore.buttons.addAll(sample)
    }

    private fun launch() = Robolectric.buildActivity(QuickLaunchActivity::class.java).setup().get()

    /** Lets entrance/exit animations run to the end. */
    private fun settle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

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
        settle()
        val card = viewDescribed(activity, "SnapStash quick launcher")
        assertFalse(activity.isFinishing)
        (card.parent as View).performClick()
        settle() // the card animates out first

        assertTrue(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun withNoAnimationTappingOutsideClosesAtOnce() {
        QuickLaunchPrefs.setAnimation(context, QuickAnimation.NONE)
        val activity = launch()
        (viewDescribed(activity, "SnapStash quick launcher").parent as View).performClick()

        assertTrue(activity.isFinishing)
    }

    @Test
    fun everyAnimationShowsTheCardFully() {
        QuickAnimation.entries.forEach { animation ->
            QuickLaunchPrefs.setAnimation(context, animation)
            val activity = launch()
            settle()
            val card = viewDescribed(activity, "SnapStash quick launcher")
            assertEquals("$animation alpha", 1f, card.alpha)
            assertEquals("$animation translation", 0f, card.translationY)
            assertEquals("$animation scale", 1f, card.scaleX)
        }
    }

    @Test
    fun animationChoiceIsRemembered() {
        assertEquals(QuickAnimation.SLIDE, QuickLaunchPrefs.animation(context))
        QuickLaunchPrefs.setAnimation(context, QuickAnimation.POP)
        assertEquals(QuickAnimation.POP, QuickLaunchPrefs.animation(context))
    }

    private fun shot(name: String) {
        val activity = launch()
        settle()
        activity.window.decorView.captureRoboImage("build/screenshots/$name.png")
    }

    @Test
    fun screenshot() = shot("7_quick_launcher")

    @Test
    fun screenshotOneFolder() {
        ButtonStore.buttons.retainAll { it.id == "1" }
        shot("8_quick_launcher_one_folder")
    }

    @Test
    fun screenshotManyFolders() {
        val extra = listOf("Groceries", "Car service", "Warranty cards", "School notices", "Plants", "Paint colours",
            "Parking", "Wine labels", "Recipes", "Serial numbers", "Gym", "Kids' art")
        extra.forEachIndexed { i, label ->
            ButtonStore.buttons.add(CaptureButton(id = "x$i", label = label, folderUri = "content://x/x$i", colorIndex = i))
        }
        shot("9_quick_launcher_many_folders")
    }

    @Test
    fun screenshotNoFolders() {
        ButtonStore.buttons.clear()
        shot("10_quick_launcher_empty")
    }
}
