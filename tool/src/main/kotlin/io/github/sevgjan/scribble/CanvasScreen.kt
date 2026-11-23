package io.github.sevgjan.scribble

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class CanvasViewModel(private val store: SketchStore, private val id: Long) : LightViewModel<Unit>() {
    val sketch = MutableStateFlow(Sketch(id))
    val brush = MutableStateFlow(Brush.PEN)

    /** The stroke under the finger, drawn before it is part of the sketch. */
    val live = MutableStateFlow<Stroke?>(null)
    val deleteArmed = MutableStateFlow(false)

    private var builder: StrokeBuilder? = null
    private var disarmJob: Job? = null

    init {
        viewModelScope.launch { store.load(id)?.let { sketch.value = it } }
    }

    fun strokeStart(x: Float, y: Float) {
        builder = StrokeBuilder(brush.value).also { it.add(x, y); live.value = it.build() }
    }

    fun strokeMove(x: Float, y: Float) {
        val b = builder ?: return
        b.add(x, y)
        live.value = b.build()
    }

    fun strokeEnd() {
        val stroke = builder?.build() ?: return
        builder = null
        live.value = null
        sketch.value = sketch.value.add(stroke)
        persist()
    }

    fun rename(name: String) = change { it.renamed(name) }

    fun undo() = change { it.undo() }

    fun redo() = change { it.redo() }

    fun cycleBrush(forward: Boolean = true) {
        brush.value = if (forward) brush.value.next() else brush.value.previous()
    }

    /** First tap arms, second tap within 3 s deletes the sketch. */
    fun delete(onDeleted: () -> Unit) {
        if (!deleteArmed.value) {
            deleteArmed.value = true
            disarmJob = viewModelScope.launch {
                delay(3.seconds)
                deleteArmed.value = false
            }
            return
        }
        disarmJob?.cancel()
        deleteArmed.value = false
        // Cleared first, so leaving the screen doesn't save it again.
        val doomed = sketch.value
        sketch.value = Sketch(id)
        viewModelScope.launch {
            store.delete(doomed)
            onDeleted()
        }
    }

    /** Saves now, so a sketch isn't lost if the app is closed or the screen is left mid-edit. */
    fun persist() {
        val snapshot = sketch.value
        viewModelScope.launch { store.save(snapshot) }
    }

    private fun change(transform: (Sketch) -> Sketch) {
        val next = transform(sketch.value)
        if (next == sketch.value) return
        sketch.value = next
        persist()
    }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) = persist()

    override fun onAppPause() = persist()

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!ScribbleKeys.handles(keyCode)) return false
        if (event.repeatCount == 0) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> undo()
                KeyEvent.KEYCODE_VOLUME_UP -> redo()
                ScribbleKeys.wheelDown -> cycleBrush(forward = true)
                ScribbleKeys.wheelUp -> cycleBrush(forward = false)
            }
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = ScribbleKeys.handles(keyCode)
}

/**
 * Volume down/up undo and redo. The LP3 scroll wheel steps through the brushes; its key labels
 * exist only in LightOS, so they are resolved at runtime and other builds simply skip them.
 */
object ScribbleKeys {
    val wheelUp by lazy { keyCode("WHEEL_CCW") }
    val wheelDown by lazy { keyCode("WHEEL_CW") }

    fun handles(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == wheelUp || keyCode == wheelDown

    private fun keyCode(label: String): Int =
        runCatching { KeyEvent.keyCodeFromString(label) }.getOrDefault(KeyEvent.KEYCODE_UNKNOWN)
            .takeIf { it != KeyEvent.KEYCODE_UNKNOWN } ?: Int.MIN_VALUE
}

class CanvasScreen(
    sealedActivity: SealedLightActivity,
    private val store: SketchStore,
    private val id: Long,
) : LightScreen<Unit, CanvasViewModel>(sealedActivity) {

    override val viewModelClass: Class<CanvasViewModel>
        get() = CanvasViewModel::class.java

    override fun createViewModel() = CanvasViewModel(store, id)

    private fun rename() {
        navigateTo(
            screenFactory = { TextEditorScreen(it, title = "Name", initialValue = viewModel.sketch.value.name, submitLabel = "SAVE") },
            resultCallback = { viewModel.rename(it) },
        )
    }

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val sketch by viewModel.sketch.collectAsState()
        val brush by viewModel.brush.collectAsState()
        val live by viewModel.live.collectAsState()
        val deleteArmed by viewModel.deleteArmed.collectAsState()

        LightTheme(colors = themeColors) {
            val colors = LightThemeTokens.colors
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                    center = if (deleteArmed) {
                        LightTopBarCenter.Text("TAP AGAIN TO DELETE")
                    } else {
                        LightTopBarCenter.TwoLineDetail(sketch.title, sketchDate(id), onClick = ::rename)
                    },
                    rightButton = LightBarButton.LightIcon(
                        icon = LightIcons.TRASH,
                        onClick = { viewModel.delete { goBack() } },
                    ),
                )

                BoxWithConstraints(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val density = LocalDensity.current
                    // The page keeps its proportions and fills as much of the space as it can.
                    val scale = minOf(
                        with(density) { maxWidth.toPx() } / Page.WIDTH,
                        with(density) { maxHeight.toPx() } / Page.HEIGHT,
                    )
                    val width = with(density) { (Page.WIDTH * scale).toDp() }
                    val height = with(density) { (Page.HEIGHT * scale).toDp() }

                    Canvas(
                        modifier = Modifier
                            .size(width, height)
                            .pointerInput(scale) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    viewModel.strokeStart(down.position.x / scale, down.position.y / scale)
                                    down.consume()
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        viewModel.strokeMove(change.position.x / scale, change.position.y / scale)
                                        change.consume()
                                    }
                                    viewModel.strokeEnd()
                                }
                            },
                    ) {
                        drawStrokes(sketch.strokes, ink = colors.content, paper = colors.background, scale = scale)
                        live?.let { drawStrokes(listOf(it), ink = colors.content, paper = colors.background, scale = scale) }
                    }
                }

                LightBottomBar(
                    listOf(
                        LightBarButton.Text(text = "UNDO", enabled = sketch.canUndo, onClick = viewModel::undo),
                        LightBarButton.Text(text = brush.label, onClick = { viewModel.cycleBrush() }),
                        LightBarButton.Text(text = "REDO", enabled = sketch.canRedo, onClick = viewModel::redo),
                    ),
                )
            }
        }
    }
}
