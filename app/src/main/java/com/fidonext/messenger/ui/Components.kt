package com.fidonext.messenger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import android.graphics.Canvas
import com.caverock.androidsvg.SVG

@Composable
fun SvgIcon(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val context = LocalContext.current
    val assetManager = context.assets

    val bitmap = try {
        assetManager.open(path).use { inputStream ->
            val svg = SVG.getFromInputStream(inputStream)
            
            // Default size if not specified in SVG
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
        Box(modifier = modifier.size(24.dp))
    }
}
