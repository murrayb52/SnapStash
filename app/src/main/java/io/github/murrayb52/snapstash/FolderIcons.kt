package io.github.murrayb52.snapstash

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Optional pictures for folders. A chosen image is cropped to a square, shrunk and copied into
 * the app's private storage, so it keeps working even if the original photo is moved or deleted.
 */
object FolderIcons {
    private const val DIR = "folder_icons"
    private const val SIZE = 256

    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun dir(context: Context) = File(context.filesDir, DIR)

    /** Copies [source] in as a new icon and returns its file name. Slow: call off the main thread. */
    fun import(context: Context, source: Uri): String {
        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, source)) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = SIZE.toFloat() / min(w, h)
            if (scale < 1f) decoder.setTargetSize(max(1, (w * scale).roundToInt()), max(1, (h * scale).roundToInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val side = min(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val name = "${UUID.randomUUID()}.jpg"
        dir(context).mkdirs()
        File(dir(context), name).outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        cache.put(name, square)
        return name
    }

    /** The icon called [name], or null if there is none (or it can't be read). */
    fun load(context: Context, name: String?): Bitmap? {
        if (name.isNullOrEmpty()) return null
        cache.get(name)?.let { return it }
        val file = File(dir(context), name)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.path)?.also { cache.put(name, it) }
    }

    /** Deletes icon files no folder uses any more (replaced, removed, or folder deleted). */
    fun releaseUnused(context: Context, buttons: List<CaptureButton>) {
        val inUse = buttons.mapNotNull { it.iconFile }.toSet()
        dir(context).listFiles()?.filter { it.name !in inUse }?.forEach {
            cache.remove(it.name)
            it.delete()
        }
    }
}
