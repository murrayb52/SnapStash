package io.github.murrayb52.snapstash

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureButtonTest {
    @Test
    fun prefixIsASafeSlugOfTheLabel() {
        assertEquals("clicks", CaptureButton.slug("Clicks"))
        assertEquals("clicks_receipts", CaptureButton.slug("  Clicks receipts! "))
        assertEquals("tax_2026", CaptureButton.slug("Tax/2026"))
        assertEquals("photo", CaptureButton.slug("😀"))
    }

    @Test
    fun folderPathsAreHumanReadable() {
        assertEquals(
            "Internal storage/Pictures/SnapStash/Clicks",
            FolderAccess.readablePath("primary:Pictures/SnapStash/Clicks"),
        )
        assertEquals("1234-ABCD/DCIM", FolderAccess.readablePath("1234-ABCD:DCIM"))
        assertEquals("Internal storage", FolderAccess.readablePath("primary:"))
    }
}
