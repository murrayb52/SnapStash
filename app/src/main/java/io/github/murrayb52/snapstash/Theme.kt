package io.github.murrayb52.snapstash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The SnapStash look: a dark filing cabinet with colour-coded paper folders. */
object Stash {
    val Cabinet = Color(0xFF131519)      // app background
    val Drawer = Color(0xFF1C1F25)       // cards, rows, dialogs
    val DrawerHigh = Color(0xFF262A32)   // fields, pressed surfaces
    val Paper = Color(0xFFF3EFE6)        // primary text, paper sheets
    val PaperShade = Color(0xFFDCD6C9)
    val Muted = Color(0xFF959BA5)        // secondary text
    val Ink = Color(0xFF1A1D22)          // text on folders
    val Manila = Color(0xFFE9C46A)       // accent
    val Danger = Color(0xFFFF8A80)
    val Filed = Color(0xFF2F8F5B)        // "saved" confirmation

    val Mono = FontFamily.Monospace

    /** Small all-caps label, like the writing on a file tab. */
    val TabLabel = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp)
}

@Composable
fun SnapStashTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Stash.Manila,
            onPrimary = Stash.Ink,
            secondary = Stash.Manila,
            onSecondary = Stash.Ink,
            background = Stash.Cabinet,
            onBackground = Stash.Paper,
            surface = Stash.Drawer,
            onSurface = Stash.Paper,
            surfaceVariant = Stash.DrawerHigh,
            onSurfaceVariant = Stash.Muted,
            surfaceContainerHigh = Stash.Drawer,
            surfaceContainer = Stash.Drawer,
            outline = Color(0xFF3A3F48),
            error = Stash.Danger,
        ),
        typography = Typography(),
        content = content,
    )
}

/**
 * A file-folder outline: a rounded body with an index tab on the top-left.
 * [tabWidth] is capped at 60% of the width so the tab always reads as a tab.
 */
class FolderShape(
    private val tabWidth: Dp,
    private val tabHeight: Dp,
    private val corner: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { corner.toPx() }
        val th = with(density) { tabHeight.toPx() }
        val tw = with(density) { tabWidth.toPx() }.coerceAtMost(size.width * 0.6f)
        val slope = th * 1.1f
        val path = Path().apply {
            moveTo(0f, r)
            quadraticBezierTo(0f, 0f, r, 0f)
            lineTo(tw, 0f)
            cubicTo(tw + slope * 0.5f, 0f, tw + slope * 0.5f, th, tw + slope, th)
            lineTo(size.width - r, th)
            quadraticBezierTo(size.width, th, size.width, th + r)
            lineTo(size.width, size.height - r)
            quadraticBezierTo(size.width, size.height, size.width - r, size.height)
            lineTo(r, size.height)
            quadraticBezierTo(0f, size.height, 0f, size.height - r)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * A drawn file folder: tabbed back panel, a couple of paper sheets peeking out, and a front
 * flap. [front] is drawn on the flap. Sizes scale from [tabHeight], so the same drawing works as
 * a big home-screen card or a small icon.
 */
@Composable
fun FolderGraphic(
    color: Color,
    modifier: Modifier = Modifier,
    tabWidth: Dp = 110.dp,
    tabHeight: Dp = 22.dp,
    corner: Dp = 18.dp,
    showPaper: Boolean = true,
    tab: @Composable () -> Unit = {},
    front: @Composable () -> Unit = {},
) {
    val back = lerp(color, Color.Black, 0.28f)
    val paperTop = tabHeight + tabHeight * 0.25f
    val frontTop = tabHeight + tabHeight * 0.75f
    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(back, FolderShape(tabWidth, tabHeight, corner))
        )
        Box(
            Modifier.padding(start = corner * 0.8f).height(tabHeight).fillMaxWidth(0.6f),
            contentAlignment = Alignment.CenterStart,
        ) { tab() }
        if (showPaper) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = paperTop, end = corner * 1.6f)
                    .fillMaxWidth(0.62f)
                    .fillMaxHeight()
                    .background(Stash.PaperShade, RoundedCornerShape(corner * 0.3f))
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = paperTop + tabHeight * 0.18f, end = corner * 0.9f)
                    .fillMaxWidth(0.5f)
                    .fillMaxHeight()
                    .background(Stash.Paper, RoundedCornerShape(corner * 0.3f))
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = frontTop)
                .clip(RoundedCornerShape(corner))
                .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.08f), color)))
        ) { front() }
    }
}

/** "Internal storage/Pictures/SnapStash/Receipts" -> "Pictures/SnapStash/Receipts". */
fun shortPath(path: String): String = path.removePrefix("Internal storage/")
