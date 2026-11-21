package com.nudge.drive.ui.screens.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import com.nudge.drive.AppContainer
import com.nudge.drive.data.ApiException
import com.nudge.drive.data.FileItem
import com.nudge.drive.data.SheetData
import com.nudge.drive.data.SheetTab
import com.nudge.drive.ui.components.Chip
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.components.softShadow
import com.nudge.drive.ui.screens.more.copyText
import com.nudge.drive.ui.theme.Nd

private fun colName(i: Int): String { var n = i + 1; val sb = StringBuilder(); while (n > 0) { val r = (n - 1) % 26; sb.insert(0, ('A' + r)); n = (n - 1) / 26 }; return sb.toString() }
private val numeric = Regex("^[-+(]?[$€£₹]?\\s?[\\d,]*\\.?\\d+%?\\)?$")

/**
 * Native spreadsheet grid: sheet tabs, frozen column letters and row numbers, smooth 2-D scrolling,
 * pinch to zoom, tap a cell to see/copy its full value.
 */
@Composable
fun SheetViewer(c: AppContainer, f: FileItem, top: Dp, reloadKey: Int, onError: (String) -> Unit) {
    var data by remember(f.id) { mutableStateOf<SheetData?>(null) }
    LaunchedEffect(f.id, reloadKey) {
        try { data = c.repo.sheetData(f.id) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { if (data == null) onError((e as? ApiException)?.friendly ?: "Couldn't open this spreadsheet.") }
    }
    val d = data
    if (d == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Nd.Iris) }; return }
    if (d.sheets.isEmpty()) { LaunchedEffect(d) { onError("This spreadsheet is empty.") }; return }
    var tab by remember(f.id) { mutableIntStateOf(0) }
    val sheet = d.sheets[tab.coerceIn(0, d.sheets.lastIndex)]
    Column(Modifier.fillMaxSize().padding(top = top)) {
        if (sheet.truncated) Text(
            "Showing the first ${sheet.rows.size} rows. Open in Google Sheets to see everything.",
            style = MaterialTheme.typography.labelSmall, color = Nd.Muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Box(Modifier.weight(1f)) { SheetGrid(sheet, key = "${f.id}-$tab") }
        if (d.sheets.size > 1) LazyRow(
            Modifier.fillMaxWidth().background(Nd.Surface).navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(d.sheets) { i, s -> Chip(s.name, i == tab) { tab = i } }
        }
    }
}

@Composable
private fun SheetGrid(sheet: SheetTab, key: String) {
    val ctx = LocalContext.current
    var zoom by remember(key) { mutableFloatStateOf(1f) }
    var selected by remember(key) { mutableStateOf<Pair<Int, Int>?>(null) }
    val h = remember(key) { ScrollState(0) }
    val list = rememberLazyListState()
    LaunchedEffect(key) { list.scrollToItem(0) }

    val cols = sheet.rows.firstOrNull()?.size ?: 0
    // Column widths: from the file when it has them, otherwise sized to the content.
    val baseWidths = remember(sheet) {
        List(cols) { ci ->
            val fromFile = sheet.colWidths.getOrNull(ci)
            if (fromFile != null && fromFile > 0) (fromFile * 0.95).toFloat().coerceIn(48f, 320f)
            else {
                val longest = sheet.rows.asSequence().take(400).map { it.getOrNull(ci)?.length ?: 0 }.maxOrNull() ?: 0
                (longest * 7.2f + 18f).coerceIn(56f, 260f)
            }
        }
    }
    val widths = baseWidths.map { (it * zoom).dp }
    val starts = remember(widths) { widths.runningFold(0.dp) { acc, w -> acc + w } }
    val total = starts.lastOrNull() ?: 0.dp
    val rowH = (32 * zoom).dp
    val font = (13 * zoom).sp
    val numW = (44 * zoom).dp.coerceAtLeast(36.dp)

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Nd.Surface).pointerInput(key) {
            // Two-finger pinch zooms the grid (taken before the lists see it).
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                do {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    if (e.changes.count { it.pressed } >= 2) {
                        zoom = (zoom * e.calculateZoom()).coerceIn(0.6f, 2.2f)
                        e.changes.forEach { it.consume() }
                    }
                } while (e.changes.any { it.pressed })
            }
        },
    ) {
        val viewport = maxWidth - numW
        // Only compose columns that are on screen, so wide sheets stay smooth.
        val scrollDp = with(androidx.compose.ui.platform.LocalDensity.current) { h.value.toDp() }
        val first = starts.indexOfLast { it <= scrollDp }.coerceIn(0, (cols - 1).coerceAtLeast(0))
        val last = (starts.indexOfFirst { it >= scrollDp + viewport }.takeIf { it >= 0 } ?: cols).coerceIn(first, cols)

        Column(Modifier.fillMaxSize()) {
            // Column letters
            Row(Modifier.height(rowH).background(Nd.Mist)) {
                Box(Modifier.width(numW).height(rowH).border(0.5.dp, Nd.Line))
                Box(Modifier.weight(1f).horizontalScroll(h)) {
                    Row(Modifier.width(total)) {
                        Spacer(Modifier.width(starts.getOrElse(first) { 0.dp }))
                        for (ci in first until last) HeaderCell(colName(ci), widths[ci], rowH, font, selected?.second == ci)
                    }
                }
            }
            LazyColumn(state = list, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 120.dp)) {
                items(sheet.rows.size, key = { it }) { ri ->
                    val row = sheet.rows[ri]
                    Row(Modifier.height(rowH)) {
                        Box(Modifier.width(numW).height(rowH).background(Nd.Mist).border(0.5.dp, Nd.Line), contentAlignment = Alignment.Center) {
                            Text("${ri + 1}", fontSize = (11 * zoom).sp, color = if (selected?.first == ri) Nd.IrisDeep else Nd.Muted, maxLines = 1)
                        }
                        Box(Modifier.weight(1f).horizontalScroll(h)) {
                            Row(Modifier.width(total)) {
                                Spacer(Modifier.width(starts.getOrElse(first) { 0.dp }))
                                for (ci in first until last) {
                                    val v = row.getOrElse(ci) { "" }
                                    val sel = selected == ri to ci
                                    Box(
                                        Modifier.width(widths[ci]).height(rowH)
                                            .background(if (sel) Nd.Mist else Nd.Surface)
                                            .border(if (sel) 2.dp else 0.5.dp, if (sel) Nd.Primary else Nd.Line)
                                            .clickable { selected = if (sel) null else ri to ci }
                                            .padding(horizontal = 6.dp),
                                        contentAlignment = if (numeric.matches(v.trim())) Alignment.CenterEnd else Alignment.CenterStart,
                                    ) {
                                        Text(v, fontSize = font, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (ri == 0) FontWeight.SemiBold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Cell inspector
        val s = selected
        AnimatedVisibility(
            s != null, Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it },
        ) {
            val (ri, ci) = s ?: (0 to 0)
            val value = sheet.rows.getOrNull(ri)?.getOrNull(ci).orEmpty()
            val shape = RoundedCornerShape(22.dp)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp).softShadow(shape, 16.dp, 0.2f).clip(shape)
                    .background(Nd.GlassStrong).border(1.dp, Nd.GlassBorder, shape).padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${colName(ci)}${ri + 1}", style = MaterialTheme.typography.labelLarge, color = Nd.IrisDeep, modifier = Modifier.width(52.dp))
                Text(value.ifEmpty { "Empty cell" }, style = MaterialTheme.typography.bodyMedium, color = if (value.isEmpty()) Nd.Muted else Nd.Ink,
                    maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (value.isNotEmpty()) Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).bounceClick { copyText(ctx, "Cell", value) }, contentAlignment = Alignment.Center) {
                    Icon(Lucide.Copy, "Copy", tint = Nd.Ink, modifier = Modifier.size(18.dp))
                }
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).bounceClick { selected = null }, contentAlignment = Alignment.Center) {
                    Icon(Lucide.X, "Close", tint = Nd.Muted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(name: String, w: Dp, h: Dp, font: androidx.compose.ui.unit.TextUnit, on: Boolean) {
    Box(Modifier.width(w).height(h).background(if (on) Nd.Lilac else Nd.Mist).border(0.5.dp, Nd.Line), contentAlignment = Alignment.Center) {
        Text(name, fontSize = font, color = if (on) Nd.Ink else Nd.Muted, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}
