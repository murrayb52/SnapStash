package io.github.murrayb52.snapstash

import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    private val tree = "content://com.android.externalstorage.documents/tree/primary%3APictures%2FSnapStash%2FReceipts"
    private val receipts = CaptureButton(id = "1", label = "Receipts", folderUri = tree)

    private val opened = mutableListOf<String>()
    private val browsed = mutableListOf<String>()

    private fun show() = compose.setContent {
        SnapStashTheme {
            HomeScreen(
                listOf(receipts),
                onOpen = { opened += it.id },
                onBrowse = { browsed += it.id },
                onSettings = {},
            )
        }
    }

    // The tests invoke each node's click action directly: Robolectric drops the simulated touch on
    // this screen, but the click action is exactly what a real tap on that node runs.

    @Test
    fun tappingTheFolderShowsItsPhotos() {
        show()
        compose.onNodeWithText("Receipts").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals("browsed (opened=$opened)", listOf("1"), browsed)
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun tappingTheCameraOpensTheCamera() {
        show()
        compose.onNodeWithContentDescription("Receipts camera").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals("opened (browsed=$browsed)", listOf("1"), opened)
        assertEquals(emptyList<String>(), browsed)
    }

    @Test
    fun browsingAsksForTheFolderAsADirectory() {
        val first = FolderAccess.browseIntents(Uri.parse(tree)).first()
        assertEquals(DocumentsContract.Document.MIME_TYPE_DIR, first.type)
        assertEquals(
            "content://com.android.externalstorage.documents/document/primary%3APictures%2FSnapStash%2FReceipts",
            first.data.toString(),
        )
    }
}
