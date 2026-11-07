package io.github.murrayb52.snapstash

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class PhotoNamerTest {
    private val t = LocalDateTime.of(2026, 11, 7, 12, 13, 5)

    @Test
    fun namesIncludePrefixDateAndTime() {
        val namer = PhotoNamer()
        assertEquals("clicks_2026-11-07_121305.jpg", namer.fileName("clicks", t))
        assertEquals("vitality_2026-11-07_121306.jpg", namer.fileName("vitality", t.plusSeconds(1)))
    }

    @Test
    fun sameSecondShotsGetASuffix() {
        val namer = PhotoNamer()
        assertEquals("clicks_2026-11-07_121305.jpg", namer.fileName("clicks", t))
        assertEquals("clicks_2026-11-07_121305_2.jpg", namer.fileName("clicks", t))
        assertEquals("clicks_2026-11-07_121305_3.jpg", namer.fileName("clicks", t))
        assertEquals("clicks_2026-11-07_121306.jpg", namer.fileName("clicks", t.plusSeconds(1)))
    }
}
