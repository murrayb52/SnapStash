package io.github.murrayb52.snapstash

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Builds filenames such as `clicks_2026-11-07_121305.jpg` and the MediaStore folder
 * `Pictures/SnapStash/Clicks/`.
 *
 * Names sort chronologically. If two shots land in the same second, later ones get a
 * `_2`, `_3`... suffix so nothing is ever overwritten.
 */
class PhotoNamer {
    private var lastBase: String? = null
    private var repeat = 0

    @Synchronized
    fun fileName(category: Category, now: LocalDateTime): String {
        val base = "${category.filePrefix}_${now.format(STAMP)}"
        if (base == lastBase) {
            repeat++
        } else {
            lastBase = base
            repeat = 1
        }
        return if (repeat == 1) "$base.jpg" else "${base}_$repeat.jpg"
    }

    companion object {
        const val ROOT_FOLDER = "SnapStash"
        private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")

        /** MediaStore RELATIVE_PATH, under the shared Pictures directory. */
        fun relativePath(category: Category): String = "Pictures/$ROOT_FOLDER/${category.folder}/"
    }
}
