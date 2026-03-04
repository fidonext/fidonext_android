package com.fidonext.messenger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import android.graphics.Canvas
import com.caverock.androidsvg.SVG

/**
 * Maps SVG asset path to a Material icon for Preview / fallback when assets are not available
 * (e.g. Compose Preview doesn't load app assets the same way).
 */
private fun pathToPreviewIcon(path: String): ImageVector = when {
    path.endsWith("back.svg") -> Icons.Default.ArrowBack
    path.endsWith("more.svg") -> Icons.Default.MoreVert
    path.endsWith("lock.svg") -> Icons.Default.Lock
    path.endsWith("plus-circle.svg") -> Icons.Default.Add
    path.endsWith("plus-circle2.svg") -> Icons.Default.Add
    path.endsWith("emoji.svg") -> Icons.Default.EmojiEmotions
    path.endsWith("microphone.svg") -> Icons.Default.Mic
    path.endsWith("send.svg") -> Icons.Default.Send
    path.endsWith("saved.svg") -> Icons.Default.Bookmark
    path.endsWith("chats.svg") -> Icons.Default.Chat
    path.endsWith("phone.svg") -> Icons.Default.Phone
    path.endsWith("contacts.svg") -> Icons.Default.Person
    path.endsWith("settings.svg") -> Icons.Default.Settings
    path.endsWith("search.svg") -> Icons.Default.Search
    path.endsWith("pin.svg") -> Icons.Default.PushPin
    else -> Icons.Default.Info
}

@Composable
fun SvgIcon(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val context = LocalContext.current
    val isInPreview = LocalInspectionMode.current

    // In Compose Preview, assets often aren't available; use Material icons so preview shows correct icons
    if (isInPreview) {
        Icon(
            imageVector = pathToPreviewIcon(path),
            contentDescription = contentDescription,
            modifier = modifier.size(24.dp),
            tint = tint
        )
        return
    }

    val assetManager = context.assets
    val bitmap = try {
        assetManager.open(path).use { inputStream ->
            val svg = SVG.getFromInputStream(inputStream)
            val width = if (svg.documentWidth > 0) svg.documentWidth else 100f
            val height = if (svg.documentHeight > 0) svg.documentHeight else 100f
            val bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            svg.renderToCanvas(canvas)
            bitmap
        }
    } catch (e: Exception) {
        null
    }

    if (bitmap != null) {
        Icon(
            painter = BitmapPainter(bitmap.asImageBitmap()),
            contentDescription = contentDescription,
            modifier = modifier.size(24.dp),
            tint = tint
        )
    } else {
        Icon(
            imageVector = pathToPreviewIcon(path),
            contentDescription = contentDescription,
            modifier = modifier.size(24.dp),
            tint = tint
        )
    }
}
