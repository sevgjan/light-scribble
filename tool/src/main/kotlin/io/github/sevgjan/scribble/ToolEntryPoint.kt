package io.github.sevgjan.scribble

import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.toolmanager.ClientLeafNode
import com.thelightphone.toolmanager.ClientToolManifest
import com.thelightphone.toolmanager.FileBrowserSpec

/** Folder (inside the Tool Manager's shared files) the PNG of every sketch is written to. */
const val EXPORT_DIR = "sketches"

@EntryPoint
object ToolEntryPoint : LightEntryPoint {
    override fun getToolManagerManifest() = ClientToolManifest(
        title = "Scribble",
        roots = listOf(
            ClientLeafNode(
                FileBrowserSpec(
                    label = "Sketches",
                    path = EXPORT_DIR,
                    headerText = "Every sketch is saved here as a PNG. Download the ones you want to keep.",
                ),
            ),
        ),
    )
}
