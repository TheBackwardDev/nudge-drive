package com.nudge.drive.ui.screens.files

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.nudge.drive.data.FileItem
import com.nudge.drive.ui.components.pressScale
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.util.ThumbSize
import com.nudge.drive.util.ViewerType
import com.nudge.drive.util.kind
import com.nudge.drive.util.parseInstant
import com.nudge.drive.util.soft
import com.nudge.drive.util.thumbUrl
import com.nudge.drive.util.viewerType
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface TimelineEntry {
    val key: String
    data class Month(val title: String, val count: Int, override val key: String) : TimelineEntry
    data class Photo(val file: FileItem) : TimelineEntry { override val key: String get() = file.id }
}

/** Flattened timeline plus what the date scrubber needs: a short label per entry and the first entry of each year. */
class Timeline(val entries: List<TimelineEntry>, val labels: List<String>, val yearMarks: List<Pair<Int, String>>, val files: List<FileItem>) {
    companion object { val Empty = Timeline(emptyList(), emptyList(), emptyList(), emptyList()) }
}

private val longMonth = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val shortMonth = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

fun buildTimeline(files: List<FileItem>, oldestFirst: Boolean): Timeline {
    val zone = ZoneId.systemDefault()
    val dated = files.map { f -> f to (parseInstant(f.momentAt)?.atZone(zone)?.let { YearMonth.from(it) } ?: YearMonth.of(1970, 1)) }
    val sorted = if (oldestFirst) dated.sortedBy { parseInstant(it.first.momentAt) } else dated.sortedByDescending { parseInstant(it.first.momentAt) }
    val entries = ArrayList<TimelineEntry>(sorted.size + 24)
    val labels = ArrayList<String>(sorted.size + 24)
    val marks = ArrayList<Pair<Int, String>>()
    var lastYear = -1
    sorted.groupBy({ it.second }, { it.first }).forEach { (month, list) ->
        val short = shortMonth.format(month)
        if (month.year != lastYear) { marks += entries.size to month.year.toString(); lastYear = month.year }
        entries += TimelineEntry.Month(longMonth.format(month), list.size, "m-$month"); labels += short
        list.forEach { entries += TimelineEntry.Photo(it); labels += short }
    }
    return Timeline(entries, labels, marks, sorted.map { it.first })
}

@Composable
fun MonthHeader(m: TimelineEntry.Month) {
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(m.title, style = MaterialTheme.typography.titleMedium, color = Nd.Ink, modifier = Modifier.weight(1f))
        Text("${m.count}", style = MaterialTheme.typography.labelMedium, color = Nd.Muted)
    }
}

/** Square photo/video tile for the timeline. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineCell(file: FileItem, selected: Boolean, selectionMode: Boolean, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val ctx = LocalPlatformContext.current
    val interaction = remember { MutableInteractionSource() }
    val inset by animateFloatAsState(if (selected) 0.86f else 1f, label = "sel")
    val bg by animateColorAsState(if (selected) Nd.Mist else Color.Transparent, label = "selbg")
    var loaded by remember(file.id) { mutableStateOf(false) }
    val request = remember(file.id) { ImageRequest.Builder(ctx).data(thumbUrl(file.id, ThumbSize.Grid)).crossfade(120).build() }
    Box(
        modifier.aspectRatio(1f).background(bg).pressScale(interaction, 0.96f)
            .combinedClickable(interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = inset; scaleY = inset }.clip(RoundedCornerShape(if (selected) 14.dp else 6.dp)).background(file.kind().soft)) {
            AsyncImage(request, file.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), onSuccess = { loaded = true })
            if (file.viewerType() == ViewerType.Video) Box(
                Modifier.align(if (loaded) Alignment.BottomStart else Alignment.Center).padding(6.dp).size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Lucide.Play, null, tint = Color.White, modifier = Modifier.size(11.dp)) }
        }
        if (selectionMode) Icon(
            if (selected) Lucide.CircleCheck else Lucide.Circle, null,
            tint = if (selected) Nd.Primary else Color.White,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp).background(if (selected) Nd.Surface else Color.Black.copy(alpha = 0.25f), CircleShape),
        )
    }
}
