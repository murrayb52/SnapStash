package io.github.murrayb52.snapstash

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Home-screen shortcuts. Static shortcuts (long-press the app icon) are declared in
 * res/xml/shortcuts.xml; this asks the launcher to pin one straight onto the home screen.
 */
object Shortcuts {
    fun canPin(context: Context): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun requestPin(context: Context, category: Category): Boolean {
        val icon = when (category) {
            Category.CLICKS -> R.drawable.ic_shortcut_clicks
            Category.VITALITY -> R.drawable.ic_shortcut_vitality
        }
        val info = ShortcutInfoCompat.Builder(context, "pin_${category.id}")
            .setShortLabel(category.label)
            .setLongLabel("${category.label} camera")
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(captureIntent(context, category))
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }

    fun captureIntent(context: Context, category: Category): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(Category.EXTRA_CATEGORY, category.id)
}
