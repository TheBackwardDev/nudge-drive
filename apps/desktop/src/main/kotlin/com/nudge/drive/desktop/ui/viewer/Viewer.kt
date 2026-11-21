package com.nudge.drive.desktop.ui.viewer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Radar
import com.nudge.drive.desktop.ui.copyText
import kotlin.math.roundToInt
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.composables.icons.lucide.ArrowDownToLine
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.FileQuestion
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SquarePen
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import com.composables.icons.lucide.ZoomIn
import com.composables.icons.lucide.ZoomOut
import com.nudge.drive.data.FileItem
import com.nudge.drive.data.SheetData
import com.nudge.drive.desktop.AppState
import com.nudge.drive.desktop.ViewerState
import com.nudge.drive.desktop.data.Platform
import com.nudge.drive.desktop.ui.FileActionDialogs
import com.nudge.drive.desktop.ui.canEdit
import com.nudge.drive.desktop.ui.rememberFileActions
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.util.ThumbSize
import com.nudge.drive.util.ViewerType
import com.nudge.drive.util.formatBytes
import com.nudge.drive.util.originalUrl
import com.nudge.drive.util.thumbUrl
import com.nudge.drive.util.viewerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File

/** Full-window viewer with ← / → between files, like the Photos app. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Viewer(app: AppState, state: ViewerState) {
    var index by remember(state) { mutableStateOf(state.index) }
    val file = state.list.getOrNull(index) ?: return
    val focus = remember { FocusRequester() }
    val actions = rememberFileActions(app)
    // Zoom for documents and spreadsheets: Ctrl + / Ctrl − / Ctrl 0, Ctrl + mouse wheel, or the buttons.
    var zoom by remember(file.id) { mutableFloatStateOf(1f) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun close() { app.viewer = null }
    Box(
        Modifier.fillMaxSize().background(Color(0xFF0B0A10)).focusRequester(focus).focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (e.isCtrlPressed || e.isMetaPressed) return@onKeyEvent when (e.key) {
                    Key.Equals, Key.Plus, Key.NumPadAdd -> { zoom = clampZoom(zoom * ZoomStep); true }
                    Key.Minus, Key.NumPadSubtract -> { zoom = clampZoom(zoom / ZoomStep); true }
                    Key.Zero, Key.NumPad0 -> { zoom = 1f; true }
                    else -> false
                }
                when (e.key) {
                    Key.Escape -> { close(); true }
                    Key.DirectionRight -> { if (index < state.list.lastIndex) index++; true }
                    Key.DirectionLeft -> { if (index > 0) index--; true }
                    Key.Delete -> { actions.delete = listOf(file); true }
                    else -> false
                }
            }
            .pointerInput(Unit) { detectTapGestures { } }, // swallow clicks behind
    ) {
        Column(Modifier.fillMaxSize()) {
            // Top bar
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                DarkButton(Lucide.X, "Close (Esc)") { close() }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(file.name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${formatBytes(file.size)}${if (state.list.size > 1) " · ${index + 1} of ${state.list.size}" else ""}", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                }
                if (canEdit(file)) { DarkButton(Lucide.SquarePen, "Edit") { app.editorFor = file }; Spacer(Modifier.width(8.dp)) }
                DarkButton(Lucide.ArrowDownToLine, "Download") { app.transfers.download(listOf(file)); app.showTransfers = true }
                Spacer(Modifier.width(8.dp))
                DarkButton(Lucide.Link, "Copy share link") { actions.share(file) }
                Spacer(Modifier.width(8.dp))
                DarkButton(Lucide.Radar, "Send to phone") { close(); app.sendToPhone(listOf(file)) }
                Spacer(Modifier.width(8.dp))
                DarkButton(Lucide.ExternalLink, "Open with another app") { openExternally(app, file) }
                Spacer(Modifier.width(8.dp))
                DarkButton(Lucide.Info, "Details") { actions.details = file }
                Spacer(Modifier.width(8.dp))
                DarkButton(Lucide.Trash2, "Move to trash") { actions.delete = listOf(file) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val dialogOpen = actions.details != null || actions.delete.isNotEmpty()
                AnimatedContent(file, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, contentKey = { it.id }, label = "viewer") { f ->
                    var htmlFailed by remember(f.id) { mutableStateOf(false) }
                    val onZoom: (Float) -> Unit = { zoom = clampZoom(it) }
                    when (f.viewerType()) {
                        ViewerType.Image -> ImageView(f)
                        ViewerType.Video, ViewerType.Audio -> MediaView(app, f)
                        ViewerType.Document -> when {
                            isSheet(f) -> SheetView(app, f, zoom, onZoom)
                            isWordDoc(f) && !htmlFailed && htmlViewAvailable -> DocHtmlView(app, f, zoom, hidden = dialogOpen) { htmlFailed = true }
                            else -> PdfView(app, f, zoom, onZoom)
                        }
                        ViewerType.Text -> TextView(app, f)
                        ViewerType.None -> NoPreview(app, f)
                    }
                }
                if (index > 0) DarkButton(Lucide.ChevronLeft, "Previous", Modifier.align(Alignment.CenterStart).padding(16.dp), 52) { index-- }
                if (index < state.list.lastIndex) DarkButton(Lucide.ChevronRight, "Next", Modifier.align(Alignment.CenterEnd).padding(16.dp), 52) { index++ }
            }
            // Photos and videos: a strip of the neighbouring ones to jump between.
            val media = remember(state.list) { state.list.filter { it.viewerType() == ViewerType.Image || it.viewerType() == ViewerType.Video } }
            if (media.size > 1 && media.any { it.id == file.id }) FilmStrip(media, file.id) { picked ->
                state.list.indexOfFirst { it.id == picked.id }.takeIf { it >= 0 }?.let { index = it }
            }
        }
    }
    // Close the viewer when the file was deleted from here.
    LaunchedEffect(actions.delete) { }
    FileActionDialogs(app, actions)
}

private fun isSheet(f: FileItem): Boolean {
    val ext = f.name.substringAfterLast('.', "").lowercase()
    return f.mimeType == "application/vnd.google-apps.spreadsheet" || ext in setOf("xlsx", "xls", "xlsm", "ods", "csv", "tsv") || f.mimeType.contains("spreadsheet") || f.mimeType == "text/csv"
}

private const val ZoomStep = 1.15f
private fun clampZoom(z: Float) = z.coerceIn(0.5f, 3f)

/** Ctrl + mouse wheel zooms (taken before the scrolling lists see the wheel). */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.ctrlWheelZoom(zoom: Float, onZoom: (Float) -> Unit): Modifier = onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { e ->
    if (!(e.keyboardModifiers.isCtrlPressed || e.keyboardModifiers.isMetaPressed)) return@onPointerEvent
    val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: return@onPointerEvent
    if (dy != 0f) onZoom(clampZoom(if (dy < 0) zoom * ZoomStep else zoom / ZoomStep))
    e.changes.forEach { it.consume() }
}

@Composable
private fun FilmStrip(items: List<FileItem>, currentId: String, onPick: (FileItem) -> Unit) {
    val state = rememberLazyListState()
    val current = items.indexOfFirst { it.id == currentId }
    LaunchedEffect(current) {
        val visible = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(5)
        if (current >= 0) state.animateScrollToItem((current - visible / 2).coerceAtLeast(0))
    }
    Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
        LazyRow(
            state = state,
            modifier = Modifier.widthIn(max = 980.dp).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(items, key = { _, f -> f.id }) { i, f ->
                val on = i == current
                val s by animateFloatAsState(if (on) 1f else 0.84f, tween(180), label = "strip")
                val shape = RoundedCornerShape(10.dp)
                Box(
                    Modifier.size(56.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = if (on) 1f else 0.6f }
                        .clip(shape).background(Color.White.copy(alpha = 0.1f))
                        .then(if (on) Modifier.border(2.dp, Color.White, shape) else Modifier)
                        .bounceClick { onPick(f) },
                ) {
                    AsyncImage(thumbUrl(f.id, ThumbSize.Small), f.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (f.viewerType() == ViewerType.Video) Icon(Lucide.Play, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DarkButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, size: Int = 40, onClick: () -> Unit) {
    androidx.compose.foundation.TooltipArea(tooltip = {
        Text(label, color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2833)).padding(horizontal = 10.dp, vertical = 6.dp))
    }, modifier = modifier) {
        Box(Modifier.size(size.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).bounceClick(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size((size * 0.48f).dp))
        }
    }
}

// ---------------------------------------------------------------------------------------- image

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ImageView(f: FileItem) {
    val ctx = LocalPlatformContext.current
    var scale by remember(f.id) { mutableFloatStateOf(1f) }
    var offset by remember(f.id) { mutableStateOf(Offset.Zero) }
    // Large thumbnail first (fast), then the original.
    val big = remember(f.id) { ImageRequest.Builder(ctx).data(thumbUrl(f.id, ThumbSize.Screen)).build() }
    val original = remember(f.id) { ImageRequest.Builder(ctx).data(if (f.provider == "google_drive" && f.size > 25_000_000) thumbUrl(f.id, 2400) else originalUrl(f.id)).build() }
    var originalReady by remember(f.id) { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().padding(horizontal = 80.dp, vertical = 10.dp)
            .onPointerEvent(PointerEventType.Scroll) { e ->
                val dy = e.changes.first().scrollDelta.y
                scale = (scale * if (dy < 0) 1.15f else 1 / 1.15f).coerceIn(1f, 8f)
                if (scale == 1f) offset = Offset.Zero
            }
            .pointerInput(f.id) { detectDragGestures { _, d -> if (scale > 1f) offset += d } }
            .pointerInput(f.id) { detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f }) },
        contentAlignment = Alignment.Center,
    ) {
        val layer = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
        if (!originalReady) AsyncImage(big, f.name, layer, contentScale = ContentScale.Fit)
        AsyncImage(original, f.name, layer, contentScale = ContentScale.Fit, onSuccess = { originalReady = true })
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xE61E1C26)).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DarkButton(Lucide.ZoomOut, "Zoom out") { scale = (scale / 1.4f).coerceAtLeast(1f); if (scale == 1f) offset = Offset.Zero }
            DarkButton(Lucide.ZoomIn, "Zoom in") { scale = (scale * 1.4f).coerceAtMost(8f) }
        }
    }
}

// ---------------------------------------------------------------------------------------- PDF / office

private sealed interface DocLoad {
    data class Downloading(val progress: Float) : DocLoad
    data class Ready(val doc: PDDocument, val pages: Int) : DocLoad
    data class Failed(val message: String) : DocLoad
}

@Composable
private fun PdfView(app: AppState, f: FileItem, zoom: Float, onZoom: (Float) -> Unit) {
    var state by remember(f.id) { mutableStateOf<DocLoad>(DocLoad.Downloading(0f)) }
    var attempt by remember(f.id) { mutableStateOf(0) }
    LaunchedEffect(f.id, attempt) {
        state = DocLoad.Downloading(0f)
        state = runCatching {
            val pdf = app.transfers.documentPreview(f) { p -> state = DocLoad.Downloading(p) }
            withContext(Dispatchers.IO) { Loader.loadPDF(pdf) }.let { DocLoad.Ready(it, it.numberOfPages) }
        }.getOrElse { DocLoad.Failed((it as? com.nudge.drive.data.ApiException)?.friendly ?: "This document can't be previewed.") }
    }
    DisposableEffect(f.id) { onDispose { (state as? DocLoad.Ready)?.doc?.let { d -> app.scope.launch(Dispatchers.IO) { runCatching { d.close() } } } } }
    when (val s = state) {
        is DocLoad.Downloading -> Loading(if (s.progress > 0f) "Opening… ${(s.progress * 100).toInt()}%" else "Opening…")
        is DocLoad.Failed -> NoPreview(app, f, s.message, onRetry = { attempt++ })
        is DocLoad.Ready -> {
            val renderer = remember(s.doc) { PDFRenderer(s.doc) }
            val pages = remember(s.doc) { mutableStateMapOf<Int, ImageBitmap>() }
            val list = rememberLazyListState()
            Box(Modifier.fillMaxSize().ctrlWheelZoom(zoom, onZoom)) {
                LazyColumn(Modifier.fillMaxSize(), state = list, horizontalAlignment = Alignment.CenterHorizontally, contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(List(s.pages) { it }) { _, i ->
                        val bmp = pages[i]
                        LaunchedEffect(i, s.doc) {
                            if (pages[i] == null) withContext(Dispatchers.IO) {
                                runCatching { synchronized(renderer) { renderer.renderImageWithDPI(i, 110f, ImageType.RGB) } }.getOrNull()
                            }?.let { pages[i] = it.toComposeImageBitmap() }
                        }
                        val ratio = runCatching { s.doc.getPage(i).mediaBox.let { it.width / it.height } }.getOrDefault(0.707f)
                        Box(Modifier.widthIn(max = (820 * zoom).dp).fillMaxWidth(0.9f).aspectRatio(ratio).clip(RoundedCornerShape(6.dp)).background(Color.White)) {
                            if (bmp != null) Image(bmp, "Page ${i + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            else CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp), color = Nd.Iris, strokeWidth = 2.dp)
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xE61E1C26)).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DarkButton(Lucide.ZoomOut, "Smaller (Ctrl −)") { onZoom(zoom / ZoomStep) }
                    Text("Page ${list.firstVisibleItemIndex + 1} of ${s.pages}", color = Color.White, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp))
                    DarkButton(Lucide.ZoomIn, "Bigger (Ctrl +)") { onZoom(zoom * ZoomStep) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------- spreadsheet

@Composable
private fun SheetView(app: AppState, f: FileItem, zoom: Float, onZoom: (Float) -> Unit) {
    var state by remember(f.id) { mutableStateOf<Result<SheetData>?>(null) }
    var tab by remember(f.id) { mutableStateOf(0) }
    var selected by remember(f.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(f.id) { state = runCatching { app.repo.sheetData(f.id) } }
    LaunchedEffect(tab) { selected = null }
    val data = state?.getOrNull()
    when {
        state == null -> Loading("Opening…")
        data == null || data.sheets.isEmpty() -> PdfView(app, f, zoom, onZoom)
        else -> Column(Modifier.fillMaxSize().padding(horizontal = 84.dp, vertical = 8.dp)) {
            val sheet = data.sheets[tab.coerceIn(0, data.sheets.lastIndex)]
            val h = rememberScrollState(); val v = rememberScrollState()
            val cols = sheet.rows.maxOfOrNull { it.size } ?: 0
            val baseWidths = remember(sheet) { (0 until cols).map { c -> ((sheet.colWidths.getOrNull(c) ?: 12.0) * 7.5).coerceIn(48.0, 420.0).toFloat() } }
            val rowH = (26 * zoom).dp
            val numW = (44 * zoom).dp
            val font = (12 * zoom).sp
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White).ctrlWheelZoom(zoom, onZoom)) {
                Column(Modifier.verticalScroll(v).horizontalScroll(h)) {
                    Row {
                        Cell("", numW, rowH, font, header = true)
                        baseWidths.forEachIndexed { c, w -> Cell(colName(c), (w * zoom).dp, rowH, font, header = true, highlight = selected?.second == c) }
                    }
                    sheet.rows.forEachIndexed { r, row ->
                        Row {
                            Cell("${r + 1}", numW, rowH, font, header = true, highlight = selected?.first == r)
                            baseWidths.forEachIndexed { c, w ->
                                val sel = selected == (r to c)
                                Cell(row.getOrElse(c) { "" }, (w * zoom).dp, rowH, font, selected = sel) { selected = if (sel) null else r to c }
                            }
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(v), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                HorizontalScrollbar(rememberScrollbarAdapter(h), Modifier.align(Alignment.BottomCenter).fillMaxWidth())
                // Cell inspector: which cell it is and its full value.
                selected?.let { (r, c) ->
                    val value = sheet.rows.getOrNull(r)?.getOrNull(c).orEmpty()
                    val shape = RoundedCornerShape(18.dp)
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 20.dp).widthIn(max = 760.dp).fillMaxWidth()
                            .clip(shape).background(Color(0xF21E1C26)).padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${colName(c)}${r + 1}", color = Nd.Lavender, style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(min = 52.dp).padding(end = 10.dp))
                        SelectionContainer(Modifier.weight(1f)) {
                            Text(value.ifEmpty { "Empty cell" }, color = if (value.isEmpty()) Color.White.copy(alpha = 0.55f) else Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState()))
                        }
                        if (value.isNotEmpty()) DarkButton(Lucide.Copy, "Copy") { copyText(value); app.toast("Copied") }
                        Spacer(Modifier.width(6.dp))
                        DarkButton(Lucide.X, "Close") { selected = null }
                    }
                }
            }
            Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                data.sheets.forEachIndexed { i, s ->
                    Text(s.name, color = if (i == tab) Color.Black else Color.White, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (i == tab) Color.White else Color.White.copy(alpha = 0.1f)).bounceClick { tab = i }.padding(horizontal = 14.dp, vertical = 7.dp))
                }
                if (sheet.truncated) Text("Showing the first rows. Open in the editor to see everything.", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 10.dp))
                Spacer(Modifier.weight(1f))
                DarkButton(Lucide.ZoomOut, "Zoom out (Ctrl −)", size = 34) { onZoom(zoom / ZoomStep) }
                Text("${(zoom * 100).roundToInt()}%", color = Color.White, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).bounceClick { onZoom(1f) }.padding(horizontal = 6.dp, vertical = 4.dp))
                DarkButton(Lucide.ZoomIn, "Zoom in (Ctrl +)", size = 34) { onZoom(zoom * ZoomStep) }
            }
        }
    }
}

private val numeric = Regex("""^[-+(]?[$€£₹]?\s?[\d,]*\.?\d+%?\)?$""")

@Composable
private fun Cell(
    text: String, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, font: androidx.compose.ui.unit.TextUnit,
    header: Boolean = false, highlight: Boolean = false, selected: Boolean = false, onClick: (() -> Unit)? = null,
) {
    val bg = when { selected -> Color(0xFFEDE8FF); header && highlight -> Color(0xFFDCD3FF); header -> Color(0xFFF1EFF7); else -> Color.White }
    Box(
        Modifier.width(width).height(height).background(bg)
            .border(if (selected) 2.dp else 0.5.dp, if (selected) Nd.Primary else Color(0xFFDDDAE6))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp),
        contentAlignment = when { header -> Alignment.Center; numeric.matches(text.trim()) -> Alignment.CenterEnd; else -> Alignment.CenterStart },
    ) {
        Text(text, color = if (header) Color(0xFF6B6390) else Color(0xFF1E1440), fontSize = font, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (header && highlight) FontWeight.SemiBold else FontWeight.Normal)
    }
}

private fun colName(i: Int): String { var n = i; val sb = StringBuilder(); do { sb.insert(0, ('A' + n % 26)); n = n / 26 - 1 } while (n >= 0); return sb.toString() }

// ---------------------------------------------------------------------------------------- text

@Composable
private fun TextView(app: AppState, f: FileItem) {
    var text by remember(f.id) { mutableStateOf<String?>(null) }
    var failed by remember(f.id) { mutableStateOf(false) }
    LaunchedEffect(f.id) {
        runCatching {
            val file = app.transfers.toCache(f)
            withContext(Dispatchers.IO) { file.inputStream().use { String(it.readNBytes(2_000_000), Charsets.UTF_8) } }
        }.onSuccess { text = it }.onFailure { failed = true }
    }
    when {
        failed -> NoPreview(app, f)
        text == null -> Loading("Opening…")
        else -> {
            val v = rememberScrollState()
            Box(Modifier.fillMaxSize().padding(horizontal = 84.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF16141C))) {
                SelectionContainer {
                    Text(text!!, color = Color(0xFFE9E6F2), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp,
                        modifier = Modifier.fillMaxSize().verticalScroll(v).padding(20.dp))
                }
                VerticalScrollbar(rememberScrollbarAdapter(v), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

// ---------------------------------------------------------------------------------------- fallbacks

@Composable
fun Loading(label: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(34.dp), color = Nd.Lavender, strokeWidth = 3.dp)
        Spacer(Modifier.height(14.dp))
        Text(label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun NoPreview(app: AppState, f: FileItem, message: String = "There's no preview for this kind of file.", onRetry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Lucide.FileQuestion, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(14.dp))
        Text(message, color = Color.White, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onRetry != null) Text("Try again", color = Color.White, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.12f)).bounceClick(onClick = onRetry).padding(horizontal = 18.dp, vertical = 11.dp))
            if (canEdit(f)) Text("Edit it instead", color = Color.White, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Nd.Primary).bounceClick { app.editorFor = f }.padding(horizontal = 18.dp, vertical = 11.dp))
            Text("Open with another app", color = Color.White, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (canEdit(f)) Color.White.copy(alpha = 0.12f) else Nd.Primary).bounceClick { openExternally(app, f) }.padding(horizontal = 18.dp, vertical = 11.dp))
            Text("Download", color = Color.White, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.12f)).bounceClick { app.transfers.download(listOf(f)); app.showTransfers = true }.padding(horizontal = 18.dp, vertical = 11.dp))
        }
    }
}

/** Downloads to the cache and opens with the default Windows / macOS app. */
fun openExternally(app: AppState, f: FileItem) {
    app.toast("Opening ${f.name}…")
    app.scope.launch {
        runCatching { app.transfers.toCache(f) }.onSuccess { Platform.openFile(it) }.onFailure { app.fail(it, "Couldn't open the file") }
    }
}
