package io.github.murrayb52.snapstash

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(onOpen: (Category) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Category.entries.forEach { CategoryButton(it, onOpen) }
    }
}

@Composable
private fun ColumnScope.CategoryButton(category: Category, onOpen: (Category) -> Unit) {
    val context = LocalContext.current
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        Button(
            onClick = { onOpen(category) },
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = category.color, contentColor = Color.White),
        ) {
            Text(category.label, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        }
        // One-time setup helper; not needed during normal use.
        if (Shortcuts.canPin(context)) {
            TextButton(
                onClick = {
                    if (!Shortcuts.requestPin(context, category)) {
                        Toast.makeText(context, "Your launcher can't add shortcuts", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
            ) {
                Text("Add to home screen", color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}
