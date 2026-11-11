package io.github.murrayb52.snapstash

import android.content.Context

/** How the SnapStash Quick card appears when it opens. */
enum class QuickAnimation(val label: String) {
    SLIDE("Slide up"),
    POP("Pop"),
    FADE("Fade"),
    NONE("None"),
}

/** Settings for the quick launcher, kept apart from [ButtonStore] so reading them stays trivial. */
object QuickLaunchPrefs {
    private const val PREFS = "snapstash"
    private const val KEY_ANIMATION = "quick_animation"

    val DEFAULT = QuickAnimation.SLIDE

    fun animation(context: Context): QuickAnimation {
        val name = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ANIMATION, null) ?: return DEFAULT
        return QuickAnimation.entries.firstOrNull { it.name == name } ?: DEFAULT
    }

    fun setAnimation(context: Context, animation: QuickAnimation) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ANIMATION, animation.name).apply()
    }
}
