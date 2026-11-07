package io.github.murrayb52.snapstash

import androidx.compose.ui.graphics.Color
import java.util.UUID

/**
 * A user-defined capture button: a name and the folder its photos go into. The folder is fixed
 * per button, so where a photo lands is decided by which button was pressed — never afterwards.
 *
 * Nothing is predefined: buttons are created in Settings.
 */
data class CaptureButton(
    /** Stable id; shortcuts on the home screen refer to it, so it never changes. */
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    /** Storage Access Framework tree URI of the chosen folder. */
    val folderUri: String,
    val colorIndex: Int = 0,
) {
    val color: Color get() = Color(PALETTE[Math.floorMod(colorIndex, PALETTE.size)])

    /** Filename prefix derived from the label, e.g. "Clicks receipts" -> "clicks_receipts". */
    val filePrefix: String get() = slug(label)

    companion object {
        val PALETTE = longArrayOf(
            // Folder colours. Light enough for dark ink text, like real coloured file folders.
            0xFFE9C46A, // manila
            0xFF8EB8E5, // sky
            0xFF8FC79F, // sage
            0xFFF2998E, // coral
            0xFFBBA8E3, // lavender
            0xFF6CC8C2, // teal
        )

        fun slug(label: String): String =
            label.trim().lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifEmpty { "photo" }
    }
}
