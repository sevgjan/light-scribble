package io.github.sevgjan.scribble

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as StrokeStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.io.ByteArrayOutputStream

/** Draws [strokes] scaled from [Page] units by [scale]. Erasers paint in [paper], so they only work over a [paper] background. */
fun DrawScope.drawStrokes(strokes: List<Stroke>, ink: Color, paper: Color, scale: Float, minWidth: Float = 0f) {
    for (stroke in strokes) {
        val color = if (stroke.brush == Brush.ERASER) paper else ink
        val width = maxOf(stroke.brush.width * scale, minWidth)
        val pts = stroke.points
        when {
            stroke.pointCount == 0 -> Unit
            stroke.pointCount == 1 -> drawCircle(color, radius = width / 2, center = Offset(pts[0] * scale, pts[1] * scale))
            else -> drawPath(
                path = smoothPath(pts, scale),
                color = color,
                style = StrokeStyle(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** Quadratic curve through the midpoints of the touch samples, so fast strokes don't show corners. */
internal fun smoothPath(pts: List<Float>, scale: Float): Path {
    val path = Path()
    val count = pts.size / 2
    path.moveTo(pts[0] * scale, pts[1] * scale)
    for (i in 1 until count - 1) {
        val mx = (pts[2 * i] + pts[2 * i + 2]) / 2
        val my = (pts[2 * i + 1] + pts[2 * i + 3]) / 2
        path.quadraticTo(pts[2 * i] * scale, pts[2 * i + 1] * scale, mx * scale, my * scale)
    }
    path.lineTo(pts[2 * count - 2] * scale, pts[2 * count - 1] * scale)
    return path
}

/** Black ink on white paper whatever the on-screen theme, so the export prints and shares well. */
fun renderPng(strokes: List<Stroke>): ByteArray {
    val scale = EXPORT_WIDTH / Page.WIDTH
    val width = EXPORT_WIDTH
    val height = (Page.HEIGHT * scale).toInt()
    val bitmap = ImageBitmap(width, height)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
        drawRect(Color.White)
        drawStrokes(strokes, ink = Color.Black, paper = Color.White, scale = scale)
    }
    return ByteArrayOutputStream().also { bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        .toByteArray()
}

private const val EXPORT_WIDTH = 1500
