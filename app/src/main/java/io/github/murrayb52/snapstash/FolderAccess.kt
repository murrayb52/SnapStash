package io.github.murrayb52.snapstash

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Folders are chosen with the system folder picker (Storage Access Framework), so a button can
 * point at any folder the user likes — including ones a sync/backup app watches. The app keeps a
 * persistent permission for each chosen folder.
 */
object FolderAccess {
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    const val DEFAULT_RELATIVE = "Pictures/SnapStash"

    /** Where the folder picker opens: Pictures/SnapStash. */
    fun pickerStartUri(): Uri {
        // Best effort: make sure the folder exists so the picker can open there. If it can't be
        // created the picker simply opens at its own default location.
        runCatching {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "SnapStash").mkdirs()
        }
        return DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "primary:$DEFAULT_RELATIVE")
    }

    fun persist(context: Context, tree: Uri) {
        context.contentResolver.takePersistableUriPermission(
            tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    /** Drop folder permissions no button uses any more (Android caps how many an app may hold). */
    fun releaseUnused(context: Context, buttons: List<CaptureButton>) {
        val inUse = buttons.map { it.folderUri }.toSet()
        context.contentResolver.persistedUriPermissions
            .filter { it.uri.toString() !in inUse }
            .forEach {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        it.uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
            }
    }

    fun hasAccess(context: Context, tree: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }

    /** A path a person recognises, e.g. "Internal storage/Pictures/SnapStash/Clicks". */
    fun displayPath(tree: Uri): String =
        runCatching { readablePath(DocumentsContract.getTreeDocumentId(tree)) }.getOrDefault(tree.toString())

    /**
     * Shows the folder in the phone's file manager (the system Files app, or another app that can
     * browse folders). Tries the folder itself first, then its storage root. Returns false if
     * nothing on the phone can show it.
     */
    fun openInFiles(context: Context, tree: Uri): Boolean = browseIntents(tree).any { intent ->
        try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    /** The ways to ask for [tree] to be shown, best first. */
    fun browseIntents(tree: Uri): List<Intent> {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return emptyList()
        val authority = tree.authority ?: return emptyList()
        fun view(uri: Uri, type: String) = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return listOf(
            view(DocumentsContract.buildDocumentUri(authority, docId), DocumentsContract.Document.MIME_TYPE_DIR),
            view(DocumentsContract.buildDocumentUriUsingTree(tree, docId), DocumentsContract.Document.MIME_TYPE_DIR),
            view(DocumentsContract.buildRootUri(authority, docId.substringBefore(':')), DocumentsContract.Root.MIME_TYPE_ITEM),
        )
    }

    /** Creates an empty JPEG in [tree] and opens it for writing. Throws if the folder is unusable. */
    fun createPhoto(context: Context, tree: Uri, name: String): Pair<Uri, OutputStream> {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val doc = DocumentsContract.createDocument(resolver, parent, "image/jpeg", name)
            ?: throw IOException("The folder refused to create $name")
        val out = resolver.openOutputStream(doc, "w")
        if (out == null) {
            delete(context, doc)
            throw IOException("Couldn't open $name for writing")
        }
        return doc to out
    }

    fun delete(context: Context, doc: Uri) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, doc) }
    }

    fun displayName(context: Context, doc: Uri): String? = runCatching {
        context.contentResolver.query(
            doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    /** "primary:Pictures/SnapStash/Clicks" -> "Internal storage/Pictures/SnapStash/Clicks". */
    fun readablePath(documentId: String): String {
        val volume = documentId.substringBefore(':')
        val path = documentId.substringAfter(':', "")
        val root = if (volume == "primary") "Internal storage" else volume
        return if (path.isEmpty()) root else "$root/$path"
    }
}
