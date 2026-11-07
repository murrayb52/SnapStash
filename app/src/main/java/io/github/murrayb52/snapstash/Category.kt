package io.github.murrayb52.snapstash

import android.content.Intent
import androidx.compose.ui.graphics.Color

/**
 * The two capture buttons. Each one owns its own folder and filename prefix, fixed at compile
 * time, so the folder a photo lands in is decided by which button was pressed — never afterwards.
 */
enum class Category(
    val label: String,
    val folder: String,
    val filePrefix: String,
    private val argb: Long,
) {
    CLICKS(label = "Clicks", folder = "Clicks", filePrefix = "clicks", argb = 0xFF1565C0),
    VITALITY(label = "Vitality", folder = "Vitality", filePrefix = "vitality", argb = 0xFFC2185B);

    val color: Color get() = Color(argb)

    /** Stable id used in intents and shortcuts. Do not rename: pinned shortcuts depend on it. */
    val id: String get() = filePrefix

    companion object {
        const val EXTRA_CATEGORY = "category"

        fun fromId(id: String?): Category? = entries.firstOrNull { it.id == id }

        fun fromIntent(intent: Intent?): Category? = fromId(intent?.getStringExtra(EXTRA_CATEGORY))
    }
}
