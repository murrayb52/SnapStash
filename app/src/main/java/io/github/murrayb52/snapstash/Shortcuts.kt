package io.github.murrayb52.snapstash

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Home-screen shortcuts, one per button. They are kept in sync with the user's buttons: they
 * appear when the app icon is long-pressed, can be pinned to the home screen, and are disabled
 * if their button is deleted.
 */
object Shortcuts {
    const val EXTRA_BUTTON_ID = "button_id"

    fun canPin(context: Context): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun requestPin(context: Context, button: CaptureButton): Boolean =
        ShortcutManagerCompat.requestPinShortcut(context, info(context, button), null)

    fun sync(context: Context, buttons: List<CaptureButton>) {
        runCatching {
            val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
            val infos = buttons.map { info(context, it) }
            ShortcutManagerCompat.setDynamicShortcuts(context, infos.take(max))
            ShortcutManagerCompat.updateShortcuts(context, infos) // renames pinned ones too

            val live = buttons.map { it.id }.toSet()
            val stale = ShortcutManagerCompat
                .getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
                .map { it.id }
                .filter { it !in live }
            if (stale.isNotEmpty()) {
                ShortcutManagerCompat.disableShortcuts(context, stale, "This button was deleted")
            }
        }
    }

    private fun info(context: Context, button: CaptureButton): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, button.id)
            .setShortLabel(button.label)
            .setLongLabel("${button.label} camera")
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut))
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .putExtra(EXTRA_BUTTON_ID, button.id)
            )
            .build()
}
