package io.github.sevgjan.scribble

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Strokes live in a fixed logical page so a sketch looks the same on any screen and in the exported PNG. */
object Page {
    const val WIDTH = 1000f
    const val HEIGHT = 900f

    /** Points closer than this to the previous one add nothing the eye can see. */
    const val MIN_STEP = 0.8f
}

@Serializable
enum class Brush(val width: Float, val label: String) {
    PEN(6f, "PEN"),
    THICK(16f, "THICK"),
    ERASER(44f, "ERASE");

    fun next(): Brush = entries[(ordinal + 1) % entries.size]
    fun previous(): Brush = entries[(ordinal + entries.size - 1) % entries.size]
}

/** [points] is a flat x0, y0, x1, y1 … list in [Page] units. An eraser stroke paints in the page colour. */
@Serializable
data class Stroke(val brush: Brush, val points: List<Float>) {
    val pointCount: Int get() = points.size / 2
}

/** The strokes behind the page plus the ones undone and waiting to be redone. */
@Serializable
data class Sketch(
    val id: Long,
    val strokes: List<Stroke> = emptyList(),
    val undone: List<Stroke> = emptyList(),
    val name: String = "",
) {
    val title: String get() = name.ifBlank { UNTITLED }

    fun renamed(newName: String): Sketch = copy(name = newName.trim())

    val isBlank: Boolean get() = strokes.isEmpty()

    /** Drawing something new forgets whatever could have been redone. */
    fun add(stroke: Stroke): Sketch = copy(strokes = strokes + stroke, undone = emptyList())

    fun undo(): Sketch =
        if (strokes.isEmpty()) this else copy(strokes = strokes.dropLast(1), undone = undone + strokes.last())

    fun redo(): Sketch =
        if (undone.isEmpty()) this else copy(strokes = strokes + undone.last(), undone = undone.dropLast(1))

    val canUndo: Boolean get() = strokes.isNotEmpty()
    val canRedo: Boolean get() = undone.isNotEmpty()

    /** Stable name for the exported PNG, so a re-export overwrites rather than duplicates. */
    val fileName: String get() = "scribble-" + FILE_STAMP.format(Instant.ofEpochMilli(id)) + ".png"

    companion object {
        private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())
    }
}

/** What the home list needs about a saved sketch, without loading its strokes. */
data class SketchInfo(val id: Long, val title: String, val strokes: List<Stroke>)

/** Collects touch points into one stroke, dropping near-duplicates. */
class StrokeBuilder(private val brush: Brush) {
    private val points = ArrayList<Float>()

    val size: Int get() = points.size / 2

    fun add(x: Float, y: Float) {
        val cx = x.coerceIn(0f, Page.WIDTH)
        val cy = y.coerceIn(0f, Page.HEIGHT)
        val n = points.size
        if (n >= 2 && abs(points[n - 2] - cx) < Page.MIN_STEP && abs(points[n - 1] - cy) < Page.MIN_STEP) return
        points.add(cx)
        points.add(cy)
    }

    fun build(): Stroke = Stroke(brush, points.map { (it * 10).toInt() / 10f })
}

const val UNTITLED = "Untitled"

private val LIST_DATE = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault()).withZone(ZoneId.systemDefault())

fun sketchDate(id: Long): String = LIST_DATE.format(Instant.ofEpochMilli(id))
