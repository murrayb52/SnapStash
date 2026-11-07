package io.github.murrayb52.snapstash

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Builds filenames such as `clicks_2026-11-07_121305.jpg`.
 *
 * Names sort chronologically. If two shots with the same prefix land in the same second, later
 * ones get a `_2`, `_3`... suffix so nothing is ever overwritten.
 */
class PhotoNamer {
    private var lastBase: String? = null
    private var repeat = 0

    @Synchronized
    fun fileName(prefix: String, now: LocalDateTime): String {
        val base = "${prefix}_${now.format(STAMP)}"
        if (base == lastBase) {
            repeat++
        } else {
            lastBase = base
            repeat = 1
        }
        return if (repeat == 1) "$base.jpg" else "${base}_$repeat.jpg"
    }

    private companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")
    }
}
