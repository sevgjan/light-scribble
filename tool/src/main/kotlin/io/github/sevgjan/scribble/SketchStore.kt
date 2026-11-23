package io.github.sevgjan.scribble

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * One JSON file per sketch under [dir]. Every save also writes a PNG through [writePng], which
 * the Tool Manager offers for download; [deletePng] removes it with the sketch.
 */
class SketchStore(
    private val dir: File,
    private val writePng: (name: String, bytes: ByteArray) -> Unit,
    private val deletePng: (name: String) -> Unit,
) {
    private val _sketches = MutableStateFlow(scan())

    /** Newest first. */
    val sketches: StateFlow<List<SketchInfo>> = _sketches

    /** A fresh sketch id, never colliding with a saved one. */
    fun newId(): Long {
        var id = System.currentTimeMillis()
        while (file(id).exists()) id++
        return id
    }

    suspend fun load(id: Long): Sketch? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(Sketch.serializer(), file(id).readText()) }.getOrNull()
    }

    /** A blank sketch isn't kept: it's removed instead, so backing out of an empty page leaves nothing behind. */
    suspend fun save(sketch: Sketch) = withContext(Dispatchers.IO) {
        if (sketch.isBlank) {
            removeFiles(sketch)
        } else {
            dir.mkdirs()
            val target = file(sketch.id)
            val temp = File(dir, ".${target.name}.tmp")
            temp.writeText(json.encodeToString(Sketch.serializer(), sketch))
            check(temp.renameTo(target)) { "could not save sketch" }
            runCatching { renderPng(sketch.strokes).also { writePng(sketch.fileName, it) } }
                .onSuccess { Log.d(TAG, "exported ${sketch.fileName}, ${it.size} bytes") }
                .onFailure { Log.w(TAG, "PNG export failed", it) }
        }
        _sketches.value = scan()
    }

    suspend fun rename(id: Long, name: String) {
        load(id)?.let { save(it.renamed(name)) }
    }

    suspend fun delete(sketch: Sketch) = withContext(Dispatchers.IO) {
        removeFiles(sketch)
        _sketches.value = scan()
    }

    private fun removeFiles(sketch: Sketch) {
        file(sketch.id).delete()
        runCatching { deletePng(sketch.fileName) }
    }

    private fun scan(): List<SketchInfo> =
        dir.listFiles { f -> f.name.endsWith(".json") && !f.name.startsWith(".") }.orEmpty()
            .mapNotNull { f ->
                val id = f.nameWithoutExtension.toLongOrNull() ?: return@mapNotNull null
                val sketch = runCatching { json.decodeFromString(Sketch.serializer(), f.readText()) }.getOrNull()
                    ?: return@mapNotNull null
                SketchInfo(id, sketch.title, sketch.strokes)
            }
            .sortedByDescending { it.id }

    private fun file(id: Long) = File(dir, "$id.json")

    private companion object {
        const val TAG = "Scribble"
        val json = Json { ignoreUnknownKeys = true }
    }
}
