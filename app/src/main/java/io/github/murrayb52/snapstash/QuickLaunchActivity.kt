package io.github.murrayb52.snapstash

import android.app.Activity
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs

/**
 * The quick launcher: a thin card over the home screen, meant to be opened by a launcher gesture.
 *
 * - Tap a folder to open SnapStash's camera for it.
 * - Swipe up anywhere to hand straight over to the phone's normal camera app.
 * - Tap outside the card to go back to the home screen.
 *
 * It is built from plain Android views (no Compose) and has no window animation, so it appears
 * as fast as the process can start. It never stays around: leaving it in any way finishes it.
 */
class QuickLaunchActivity : Activity() {

    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ButtonStore.init(this)
        setContentView(buildContent())

        // Once the card is on screen, warm up CameraX in the background so a folder's camera
        // opens faster. This does not open the camera device itself.
        window.decorView.post { runCatching { ProcessCameraProvider.getInstance(applicationContext) } }
    }

    // Anything that takes us off screen (home button, camera opened, screen off) ends the wrapper.
    override fun onStop() {
        super.onStop()
        if (!isFinishing) finish()
    }

    /**
     * An upward swipe anywhere fires the system camera as soon as it passes [SWIPE_DP], without
     * waiting for the finger to lift. Any taps the swipe started on are cancelled.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                tracking = true
            }
            MotionEvent.ACTION_MOVE -> if (tracking) {
                val dy = downY - ev.rawY
                val dx = abs(ev.rawX - downX)
                if (dy > SWIPE_DP * resources.displayMetrics.density && dy > dx) {
                    tracking = false
                    val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    openSystemCamera()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun openSystemCamera() {
        if (leaving) return
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            leave(intent)
        } catch (e: ActivityNotFoundException) {
            leaving = false
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFolder(button: CaptureButton) {
        leave(
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(Shortcuts.EXTRA_BUTTON_ID, button.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun openApp() {
        leave(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Starts [intent] and disappears instantly, with no transition animation either way. */
    private fun leave(intent: Intent) {
        leaving = true
        startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
        dismiss()
    }

    private fun dismiss() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    // ---- Layout -------------------------------------------------------------------------------

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun buildContent(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(SCRIM)
            setOnClickListener { dismiss() } // tap outside the card
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(DRAWER)
                val r = dp(28f).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            isClickable = true // taps on the card's own background don't dismiss
            contentDescription = "SnapStash quick launcher"
        }
        val cardHeight = (resources.displayMetrics.heightPixels * CARD_FRACTION).toInt()
        root.addView(card, FrameLayout.LayoutParams(MATCH_PARENT, cardHeight, Gravity.BOTTOM))

        // Keep content clear of the navigation bar; the card itself runs to the screen edge.
        ViewCompat.setOnApplyWindowInsetsListener(card) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(dp(20f), dp(10f), dp(20f), dp(16f) + nav.bottom)
            card.layoutParams = (card.layoutParams as FrameLayout.LayoutParams).apply {
                height = cardHeight + nav.bottom
            }
            insets
        }

        card.addView(header())

        val buttons = ButtonStore.buttons
        if (buttons.isEmpty()) {
            card.addView(text("No folders yet", 22f, PAPER, bold = true).apply {
                setPadding(0, dp(24f), 0, dp(6f))
            })
            card.addView(text("Swipe up for the normal camera, or add folders in SnapStash.", 15f, MUTED))
            card.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)) // push the link down
        } else {
            card.addView(folderGrid(buttons), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(14f)
            })
        }

        card.addView(text("Open SnapStash", 14f, MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12f), 0, dp(2f))
            setOnClickListener { openApp() }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return root
    }

    /** The grab handle and the swipe hint. */
    private fun header(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(View(context).apply {
            background = GradientDrawable().apply {
                setColor(HANDLE)
                cornerRadius = dp(3f).toFloat()
            }
        }, LinearLayout.LayoutParams(dp(44f), dp(5f)))
        addView(text("↑  SWIPE UP FOR CAMERA", 12f, MANILA, bold = true).apply {
            letterSpacing = 0.2f
            setPadding(0, dp(12f), 0, 0)
        })
    }

    /** Folders as big coloured tiles, two per row. Scrolls if there are many. */
    private fun folderGrid(buttons: List<CaptureButton>): View {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val rows = buttons.chunked(2)
        // With up to four folders the tiles share the card's height; beyond that they scroll.
        val fill = rows.size <= 2
        rows.forEachIndexed { r, pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { c, button ->
                row.addView(tile(button), LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply {
                    if (c == 1) marginStart = dp(12f)
                })
            }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply {
                marginStart = dp(12f)
            })
            column.addView(
                row,
                if (fill) LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f) else LinearLayout.LayoutParams(MATCH_PARENT, dp(112f)),
            )
            if (r < rows.lastIndex) column.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, dp(12f)))
        }
        if (fill) return column
        return ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(column, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
    }

    private fun tile(button: CaptureButton): View {
        val color = folderColor(button)
        val shape = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(20f).toFloat()
        }
        return TextView(this).apply {
            text = button.label
            setTextColor(INK)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.BOTTOM or Gravity.START
            maxLines = 3
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
            background = RippleDrawable(ColorStateList.valueOf(0x33000000), shape, null)
            contentDescription = "${button.label} camera"
            setOnClickListener { openFolder(button) }
        }
    }

    private fun text(s: String, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = sizeSp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    companion object {
        /** How far up (in dp) a swipe must travel before the camera opens. */
        const val SWIPE_DP = 36f

        /** The card's share of the screen height. */
        const val CARD_FRACTION = 0.52f

        // The SnapStash palette (see Stash in Theme.kt), as plain ARGB ints.
        private const val SCRIM = 0x66000000
        private val DRAWER = Color.parseColor("#1C1F25")
        private val HANDLE = Color.parseColor("#3A3F48")
        private val PAPER = Color.parseColor("#F3EFE6")
        private val MUTED = Color.parseColor("#959BA5")
        private val INK = Color.parseColor("#1A1D22")
        private val MANILA = Color.parseColor("#E9C46A")

        fun folderColor(button: CaptureButton): Int =
            CaptureButton.PALETTE[Math.floorMod(button.colorIndex, CaptureButton.PALETTE.size)].toInt()
    }
}
