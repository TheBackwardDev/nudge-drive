package com.nudge.drive.ui.screens.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nudge.drive.AppContainer
import com.nudge.drive.ui.components.softShadow
import com.nudge.drive.ui.theme.Nd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** One open PDF. PdfRenderer is single-threaded, so every call goes through [lock]. Pages are cached by size. */
class PdfDoc private constructor(private val pfd: ParcelFileDescriptor, private val renderer: PdfRenderer, val sizes: List<Size>) {
    val pageCount get() = sizes.size
    private val lock = Mutex()
    private var closed = false
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt().coerceAtMost(96 * 1024 * 1024)) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(index: Int, width: Int): Bitmap? = cache.get("$index@$width")

    suspend fun render(index: Int, width: Int): Bitmap? {
        cached(index, width)?.let { return it }
        return lock.withLock {
            withContext(Dispatchers.IO) {
                if (closed) return@withContext null
                runCatching {
                    renderer.openPage(index).use { p ->
                        val h = (width.toFloat() * p.height / p.width).roundToInt().coerceAtLeast(1)
                        Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(android.graphics.Color.WHITE)
                            p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            cache.put("$index@$width", it)
                        }
                    }
                }.getOrNull()
            }
        }
    }

    suspend fun close() = lock.withLock {
        if (!closed) { closed = true; runCatching { renderer.close() }; runCatching { pfd.close() }; cache.evictAll() }
    }

    companion object {
        suspend fun open(file: File): PdfDoc = withContext(Dispatchers.IO) {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val r = PdfRenderer(pfd)
            val sizes = (0 until r.pageCount).map { i -> r.openPage(i).use { Size(it.width.toFloat(), it.height.toFloat()) } }
            PdfDoc(pfd, r, sizes)
        }
    }
}

/**
 * Continuous page reader. Pinch zoom re-lays the pages out wider (so they stay sharp and both
 * directions scroll and fling natively); double-tap toggles 1x / 2x.
 */
@Composable
fun PdfPages(c: AppContainer, file: File, topPadding: androidx.compose.ui.unit.Dp) {
    var doc by remember(file) { mutableStateOf<PdfDoc?>(null) }
    var failed by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) {
        val opened = try { PdfDoc.open(file) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
        if (opened == null) { failed = true; return@LaunchedEffect }
        if (!isActive) { c.scope.launch { opened.close() }; return@LaunchedEffect }
        doc = opened
    }
    DisposableEffect(file) { onDispose { doc?.let { d -> c.scope.launch { d.close() } } } }

    val d = doc
    when {
        failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("This document could not be opened.", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted)
        }
        d == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Nd.Iris) }
        else -> ZoomablePages(d, topPadding)
    }
}

@Composable
private fun ZoomablePages(doc: PdfDoc, topPadding: androidx.compose.ui.unit.Dp) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var hx by remember { mutableFloatStateOf(0f) }
    var showPill by remember { mutableStateOf(true) }
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val vw = constraints.maxWidth.toFloat()
        val maxH = { (vw * scale - vw).coerceAtLeast(0f) }

        fun zoomTo(target: Float, focal: Offset, pan: Offset = Offset.Zero) {
            val ns = target.coerceIn(1f, 4f)
            val k = ns / scale
            val idx = list.firstVisibleItemIndex
            val off = list.firstVisibleItemScrollOffset
            hx = ((hx + focal.x) * k - focal.x - pan.x).coerceIn(0f, (vw * ns - vw).coerceAtLeast(0f))
            scale = ns
            val newOff = ((off + focal.y) * k - focal.y - pan.y).roundToInt()
            if (k != 1f || pan.y != 0f) list.requestScrollToItem(idx, newOff.coerceAtLeast(0))
        }

        val hState = rememberScrollableState { delta ->
            val old = hx
            hx = (hx - delta).coerceIn(0f, maxH())
            old - hx
        }
        // Render at the zoom level (in steps) so text stays crisp without re-rendering every pinch frame.
        val renderWidth by remember(vw) {
            derivedStateOf {
                val step = when { scale < 1.25f -> 1f; scale < 1.75f -> 1.5f; scale < 2.5f -> 2f; else -> 3f }
                (vw * step).roundToInt().coerceAtMost(2600)
            }
        }
        LaunchedEffect(list.isScrollInProgress) {
            if (list.isScrollInProgress) showPill = true else { delay(1600); showPill = false }
        }

        Box(
            Modifier.fillMaxSize()
                .pointerInput(doc) {
                    // Two-finger pinch is taken before the lists see it.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = e.changes.count { it.pressed }
                            if (pressed >= 2) {
                                zoomTo(scale * e.calculateZoom(), e.calculateCentroid(useCurrent = true), e.calculatePan())
                                e.changes.forEach { it.consume() }
                            }
                        } while (e.changes.any { it.pressed })
                    }
                }
                .pointerInput(doc) {
                    detectTapGestures(onDoubleTap = { p ->
                        scope.launch {
                            val to = if (scale > 1.2f) 1f else 2f
                            animate(scale, to, animationSpec = spring(dampingRatio = 0.9f)) { v, _ -> zoomTo(v, p) }
                        }
                    })
                }
                .scrollable(hState, Orientation.Horizontal, enabled = scale > 1.01f),
        ) {
            val widthDp = with(density) { (vw * scale).toDp() }
            LazyColumn(
                state = list,
                modifier = Modifier
                    .offset { IntOffset((((vw * scale - vw) / 2f) - hx).roundToInt(), 0) }
                    .requiredWidth(widthDp)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = topPadding, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(doc.pageCount, key = { it }) { i -> PdfPage(doc, i, renderWidth) }
            }
        }

        AnimatedVisibility(
            showPill, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp),
            enter = fadeIn(), exit = fadeOut(),
        ) {
            Text(
                "${(list.firstVisibleItemIndex + 1).coerceAtMost(doc.pageCount)} / ${doc.pageCount}",
                style = MaterialTheme.typography.labelMedium, color = Color.White,
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.62f)).padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun PdfPage(doc: PdfDoc, index: Int, width: Int) {
    val size = doc.sizes[index]
    var bmp by remember(index) { mutableStateOf(doc.cached(index, width)) }
    LaunchedEffect(index, width) { doc.render(index, width)?.let { bmp = it } }
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.fillMaxWidth().aspectRatio((size.width / size.height).coerceIn(0.2f, 5f))
            .softShadow(shape, 6.dp, 0.12f).clip(shape).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Medium)
        else CircularProgressIndicator(color = Nd.Iris, strokeWidth = 2.dp)
    }
}

/** Plain text / code: first 2 MB, one lazy line at a time so even long logs scroll smoothly. */
@Composable
fun TextLines(file: File, topPadding: androidx.compose.ui.unit.Dp) {
    var lines by remember(file) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(file) {
        lines = withContext(Dispatchers.IO) {
            runCatching {
                file.inputStream().use { input ->
                    val buf = ByteArray(2 * 1024 * 1024)
                    var n = 0
                    while (n < buf.size) { val r = input.read(buf, n, buf.size - n); if (r < 0) break; n += r }
                    String(buf, 0, n, Charsets.UTF_8).lines()
                }
            }.getOrElse { listOf("This file could not be read as text.") }
        }
    }
    val l = lines
    if (l == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Nd.Iris) }
    else LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding + 4.dp, bottom = 60.dp),
    ) {
        items(l.size) { i ->
            Text(
                l[i].ifEmpty { " " },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, lineHeight = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp)),
                color = Nd.Ink,
            )
        }
    }
}
