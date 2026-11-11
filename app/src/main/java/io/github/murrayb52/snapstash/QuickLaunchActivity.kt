package io.github.murrayb52.snapstash

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs

/**
 * The quick launcher: a card that floats up over the home screen, meant to be opened by a
 * launcher gesture.
 *
 * - Tap a folder to open SnapStash's camera for it.
 * - Swipe up anywhere to hand straight over to the phone's normal camera app.
 * - Tap outside the card, or press back, to go back to the home screen.
 *
 * It is built from plain Android views (no Compose) and the window itself has no animation, so it
 * appears as fast as the process can start. The card's own entrance animation is chosen in
 * Settings ([QuickLaunchPrefs]) and never delays a tap or swipe. It never stays around: leaving it
 * in any way finishes it.
 */
class QuickLaunchActivity : Activity() {

    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var leaving = false

    private lateinit var scrim: View
    private lateinit var card: LinearLayout
    private var hintArrow: View? = null
    private var bob: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ButtonStore.init(this)
        setContentView(buildContent())
        animateIn(QuickLaunchPrefs.animation(this))

        // Once the card is on screen, warm up CameraX in the background so a folder's camera
        // opens faster. This does not open the camera device itself.
        window.decorView.post { runCatching { ProcessCameraProvider.getInstance(applicationContext) } }
    }

    // Anything that takes us off screen (home button, camera opened, screen off) ends the wrapper.
    override fun onStop() {
        super.onStop()
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        bob?.cancel()
        super.onDestroy()
    }

    @Deprecated("Back is handled the same way on every Android version")
    override fun onBackPressed() = close()

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
        if (leaving) return
        leave(
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(Shortcuts.EXTRA_BUTTON_ID, button.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun openApp() {
        if (leaving) return
        leave(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Starts [intent] and disappears instantly: going somewhere is never slowed by animation. */
    private fun leave(intent: Intent) {
        leaving = true
        startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
        finishNow()
    }

    /** Going back to the home screen: a quick reverse of the entrance, then gone. */
    private fun close() {
        if (leaving) return
        leaving = true
        val animation = QuickLaunchPrefs.animation(this)
        if (animation == QuickAnimation.NONE) {
            finishNow()
            return
        }
        scrim.animate().alpha(0f).setDuration(EXIT_MS).start()
        card.animate().cancel()
        card.animate()
            .setDuration(EXIT_MS)
            .setInterpolator(PathInterpolator(0.4f, 0f, 1f, 1f))
            .alpha(if (animation == QuickAnimation.SLIDE) 1f else 0f)
            .translationY(if (animation == QuickAnimation.SLIDE) slideDistance() else dp(16f).toFloat())
            .scaleX(if (animation == QuickAnimation.POP) 0.94f else 1f)
            .scaleY(if (animation == QuickAnimation.POP) 0.94f else 1f)
            .withEndAction { finishNow() }
            .start()
    }

    private fun finishNow() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    // ---- Entrance animation -------------------------------------------------------------------

    private fun slideDistance(): Float =
        (card.height + (card.layoutParams as FrameLayout.LayoutParams).bottomMargin).toFloat()

    private fun animateIn(animation: QuickAnimation) {
        if (animation == QuickAnimation.NONE) {
            startBob()
            return
        }
        // Start hidden; the first layout tells us the card's size, then we animate from there.
        card.alpha = 0f
        scrim.alpha = 0f
        card.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                card.viewTreeObserver.removeOnPreDrawListener(this)
                val a = card.animate()
                when (animation) {
                    QuickAnimation.SLIDE -> {
                        card.alpha = 1f
                        card.translationY = slideDistance()
                        a.translationY(0f).setDuration(260)
                            .setInterpolator(PathInterpolator(0.2f, 0.9f, 0.25f, 1f))
                    }
                    QuickAnimation.POP -> {
                        card.pivotY = card.height.toFloat()
                        card.scaleX = 0.86f
                        card.scaleY = 0.86f
                        card.translationY = dp(24f).toFloat()
                        a.alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(280)
                            .setInterpolator(OvershootInterpolator(1.4f))
                    }
                    QuickAnimation.FADE -> {
                        a.alpha(1f).setDuration(170).setInterpolator(DecelerateInterpolator())
                    }
                    QuickAnimation.NONE -> Unit
                }
                a.withEndAction { startBob() }.start()
                scrim.animate().alpha(1f).setDuration(200).start()
                return true
            }
        })
    }

    /** The "swipe up" arrow gently bobs, so the hint reads as a gesture. */
    private fun startBob() {
        val arrow = hintArrow ?: return
        bob = ObjectAnimator.ofFloat(arrow, View.TRANSLATION_Y, 0f, -dp(5f).toFloat()).apply {
            duration = 650
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    // ---- Layout -------------------------------------------------------------------------------

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun buildContent(): View {
        val root = FrameLayout(this).apply {
            setOnClickListener { close() } // tap outside the card
        }
        scrim = View(this).apply { background = ColorDrawable(SCRIM) }
        root.addView(scrim, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(CARD_TOP, CARD_BOTTOM),
            ).apply {
                cornerRadius = dp(30f).toFloat()
                setStroke(dp(1f), CARD_EDGE)
            }
            elevation = dp(18f).toFloat()
            clipToOutline = true
            isClickable = true // taps on the card's own background don't dismiss
            contentDescription = "SnapStash quick launcher"
            setPadding(dp(16f), dp(18f), dp(16f), dp(14f))
        }
        // The card hugs its content and grows upwards with more folders. Its margins keep the home
        // screen visible all round; once it reaches the top margin, the list scrolls.
        root.addView(card, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            card.layoutParams = (card.layoutParams as FrameLayout.LayoutParams).apply {
                leftMargin = dp(CARD_MARGIN_DP) + bars.left
                rightMargin = dp(CARD_MARGIN_DP) + bars.right
                bottomMargin = dp(CARD_MARGIN_DP) + bars.bottom
                topMargin = dp(48f) + bars.top
            }
            insets
        }

        card.addView(header())

        val buttons = ButtonStore.buttons
        if (buttons.isEmpty()) {
            card.addView(emptyState())
        } else {
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            buttons.forEachIndexed { i, button ->
                list.addView(row(button), LinearLayout.LayoutParams(MATCH_PARENT, dp(62f)).apply {
                    if (i > 0) topMargin = dp(8f)
                })
            }
            val scroll = ScrollView(this).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isVerticalFadingEdgeEnabled = true
                setFadingEdgeLength(dp(24f))
                addView(list, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            }
            // Height 0 + weight: natural height when it fits, squeezed to scroll when it doesn't.
            card.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(16f) })
        }

        card.addView(swipeHint())
        return root
    }

    /** "SNAPSTASH / File it in…" with a small "Open app" pill on the right. */
    private fun header(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6f), 0, 0, 0)

        val titles = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("SNAPSTASH", 11f, MANILA, bold = true).apply { letterSpacing = 0.22f })
            addView(text("File it in…", 22f, PAPER, bold = true).apply { setPadding(0, dp(2f), 0, 0) })
        }
        addView(titles, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        addView(text("Open app", 13f, PAPER).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14f), 0, dp(14f), 0)
            background = RippleDrawable(
                ColorStateList.valueOf(RIPPLE),
                GradientDrawable().apply {
                    setColor(ROW)
                    cornerRadius = dp(18f).toFloat()
                    setStroke(dp(1f), CARD_EDGE)
                },
                null,
            )
            setOnClickListener { openApp() }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(36f)))
    }

    /** One folder: coloured folder icon, name, chevron. */
    private fun row(button: CaptureButton): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14f), 0, dp(14f), 0)
        background = RippleDrawable(
            ColorStateList.valueOf(RIPPLE),
            GradientDrawable().apply {
                setColor(ROW)
                cornerRadius = dp(18f).toFloat()
            },
            null,
        )
        contentDescription = "${button.label} camera"
        setOnClickListener { openFolder(button) }

        addView(FolderIcon(context, folderColor(button)), LinearLayout.LayoutParams(dp(34f), dp(28f)))
        addView(text(button.label, 18f, PAPER, bold = true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(14f), 0, dp(8f), 0)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(text("›", 26f, MUTED).apply { setPadding(0, 0, 0, dp(3f)) })
    }

    private fun emptyState(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(6f), dp(18f), dp(6f), dp(6f))
        addView(text("No folders yet", 18f, PAPER, bold = true))
        addView(text("Add folders in SnapStash. You can still swipe up for the camera.", 14f, MUTED).apply {
            setPadding(0, dp(4f), 0, 0)
        })
    }

    /** A bobbing up arrow and "Swipe up for camera", below the folders. */
    private fun swipeHint(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, dp(14f), 0, 0)
        val arrow = ImageView(context).apply {
            setImageResource(R.drawable.ic_chevron_up)
            imageTintList = ColorStateList.valueOf(MANILA)
        }
        hintArrow = arrow
        addView(arrow, LinearLayout.LayoutParams(dp(26f), dp(26f)))
        addView(text("SWIPE UP FOR CAMERA", 11f, MUTED, bold = true).apply { letterSpacing = 0.2f })
    }

    private fun text(s: String, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = sizeSp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    /** A little file folder in the button's colour: back panel with a tab, a sheet, a front flap. */
    private class FolderIcon(context: Context, private val color: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val r = h * 0.14f
            paint.color = ColorUtils.blendARGB(color, Color.BLACK, 0.3f)
            rect.set(0f, 0f, w * 0.45f, h * 0.4f); canvas.drawRoundRect(rect, r, r, paint)
            rect.set(0f, h * 0.14f, w, h); canvas.drawRoundRect(rect, r, r, paint)
            paint.color = PAPER
            rect.set(w * 0.12f, h * 0.26f, w * 0.9f, h * 0.7f); canvas.drawRect(rect, paint)
            paint.color = color
            rect.set(0f, h * 0.4f, w, h); canvas.drawRoundRect(rect, r, r, paint)
        }
    }

    companion object {
        /** How far up (in dp) a swipe must travel before the camera opens. */
        const val SWIPE_DP = 36f

        /** Gap between the card and the screen edges, so the home screen frames it. */
        const val CARD_MARGIN_DP = 14f

        private const val EXIT_MS = 150L

        // The SnapStash palette (see Stash in Theme.kt), as plain ARGB ints.
        private const val SCRIM = 0x59000000
        private const val RIPPLE = 0x33F3EFE6
        private val CARD_TOP = Color.parseColor("#252932")
        private val CARD_BOTTOM = Color.parseColor("#1A1D23")
        private val CARD_EDGE = Color.parseColor("#353A44")
        private val ROW = Color.parseColor("#2C313A")
        private val PAPER = Color.parseColor("#F3EFE6")
        private val MUTED = Color.parseColor("#959BA5")
        private val MANILA = Color.parseColor("#E9C46A")

        fun folderColor(button: CaptureButton): Int =
            CaptureButton.PALETTE[Math.floorMod(button.colorIndex, CaptureButton.PALETTE.size)].toInt()
    }
}
