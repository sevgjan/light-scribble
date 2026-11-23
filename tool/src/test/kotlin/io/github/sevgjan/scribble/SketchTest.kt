package io.github.sevgjan.scribble

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SketchTest {
    private fun stroke(brush: Brush = Brush.PEN, n: Int = 3) =
        Stroke(brush, (0 until n).flatMap { listOf(it * 10f, it * 5f) })

    @Test
    fun undoAndRedoWalkThroughStrokes() {
        val a = stroke(n = 2)
        val b = stroke(Brush.THICK)
        var s = Sketch(1).add(a).add(b)
        assertEquals(listOf(a, b), s.strokes)
        s = s.undo()
        assertEquals(listOf(a), s.strokes)
        assertTrue(s.canRedo)
        s = s.undo().undo()
        assertTrue(s.isBlank)
        assertFalse(s.canUndo)
        s = s.redo().redo().redo()
        assertEquals(listOf(a, b), s.strokes)
        assertFalse(s.canRedo)
    }

    @Test
    fun drawingAfterUndoDropsTheRedoStack() {
        val s = Sketch(1).add(stroke()).add(stroke()).undo().add(stroke(Brush.ERASER))
        assertFalse(s.canRedo)
        assertEquals(2, s.strokes.size)
    }

    @Test
    fun builderDropsNearDuplicatesAndClampsToThePage() {
        val b = StrokeBuilder(Brush.PEN)
        b.add(10f, 10f)
        b.add(10.2f, 10.1f)
        b.add(-50f, 2000f)
        val stroke = b.build()
        assertEquals(listOf(10f, 10f, 0f, Page.HEIGHT), stroke.points)
    }

    @Test
    fun aTapIsASinglePointStroke() {
        val b = StrokeBuilder(Brush.THICK)
        b.add(500f, 400f)
        assertEquals(1, b.build().pointCount)
    }

    @Test
    fun blankNameShowsAsUntitledAndRenameKeepsStrokes() {
        val s = Sketch(1).add(stroke())
        assertEquals(UNTITLED, s.title)
        val named = s.renamed("  Garden  ")
        assertEquals("Garden", named.title)
        assertEquals(s.strokes, named.strokes)
        assertEquals(UNTITLED, named.renamed("   ").title)
    }

    @Test
    fun brushesCycleBothWays() {
        assertEquals(Brush.THICK, Brush.PEN.next())
        assertEquals(Brush.PEN, Brush.ERASER.next())
        assertEquals(Brush.ERASER, Brush.PEN.previous())
    }

    @Test
    fun sketchSurvivesJson() {
        val s = Sketch(42, listOf(stroke()), listOf(stroke(Brush.ERASER)))
        val back = Json.decodeFromString(Sketch.serializer(), Json.encodeToString(Sketch.serializer(), s))
        assertEquals(s, back)
    }
}
