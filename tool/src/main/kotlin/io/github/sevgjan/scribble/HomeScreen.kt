package io.github.sevgjan.scribble

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/** A small squircle showing the sketch itself. */
@Composable
private fun Thumbnail(sketch: SketchInfo) {
    val colors = LightThemeTokens.colors
    val paper = lerp(colors.background, colors.content, 0.15f)
    Canvas(
        modifier = Modifier
            .padding(end = 0.8f.gridUnitsAsDp())
            .size(3f.gridUnitsAsDp())
            .clip(RoundedCornerShape(30)),
    ) {
        drawRect(paper)
        // The page is wider than tall: fit it by width and centre it vertically.
        val scale = size.width / Page.WIDTH
        translate(top = (size.height - Page.HEIGHT * scale) / 2) {
            drawStrokes(sketch.strokes, ink = colors.content, paper = paper, scale = scale, minWidth = 2f)
        }
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) : SimpleLightScreen<Unit>(sealedActivity) {
    private val store = SketchStore(
        dir = lightContext.filesDir.resolve("sketches"),
        writePng = { name, bytes -> lightContext.fileShare.writeBytes("$EXPORT_DIR/$name", bytes) },
        deletePng = { name -> lightContext.fileShare.delete("$EXPORT_DIR/$name") },
    )

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val sketches by store.sketches.collectAsState()

        fun open(id: Long) = navigateTo(screenFactory = { CanvasScreen(it, store, id) })

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    center = LightTopBarCenter.Text("Scribble"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                if (sketches.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        LightText(text = "no sketches yet", variant = LightTextVariant.Copy, lighten = true)
                    }
                } else {
                    LightScrollView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 1f.gridUnitsAsDp()),
                    ) {
                        sketches.forEach { sketch ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .lightClickable { open(sketch.id) }
                                    .padding(vertical = 0.45f.gridUnitsAsDp()),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Thumbnail(sketch)
                                Column(modifier = Modifier.weight(1f)) {
                                    LightText(
                                        text = sketch.title,
                                        variant = LightTextVariant.Copy,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    LightText(
                                        text = sketchDate(sketch.id),
                                        variant = LightTextVariant.Fine,
                                        color = LightThemeTokens.colors.content.copy(alpha = 0.45f),
                                    )
                                }
                            }
                        }
                    }
                }

                LightBottomBar(listOf(LightBarButton.Text(text = "NEW", onClick = { open(store.newId()) })))
            }
        }
    }
}
