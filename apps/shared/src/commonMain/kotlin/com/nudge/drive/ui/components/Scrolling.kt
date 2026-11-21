package com.nudge.drive.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.ChevronsUpDown
import com.composables.icons.lucide.Lucide
import com.nudge.drive.ui.theme.Nd
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Round "back to top" button that pops in once the user has scrolled a long way. */
@Composable
fun ScrollToTopButton(visible: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    AnimatedVisibility(visible, modifier, enter = scaleIn(spring(dampingRatio = 0.6f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
        Box(
            Modifier.size(50.dp).softShadow(CircleShape, 16.dp, 0.25f).clip(CircleShape).background(Nd.Primary)
                .border(1.dp, Nd.GlassBorder, CircleShape).bounceClick(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(Lucide.ArrowUp, "Back to top", tint = Nd.OnPrimary, modifier = Modifier.size(22.dp)) }
    }
}

/**
 * Google Photos-style date scrubber for a timeline inside a [LazyGridState].
 * Entries [firstIndex, firstIndex + count) are the timeline; [labelAt] names the month of an entry,
 * [marks] are (entry index, year) ticks shown while dragging.
 */
@Composable
fun TimelineScroller(
    state: LazyGridState,
    firstIndex: () -> Int,
    count: Int,
    labelAt: (Int) -> String,
    marks: List<Pair<Int, String>>,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
) {
    if (count < 24) return
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val scrollFraction by remember(count) {
        derivedStateOf { ((state.firstVisibleItemIndex - firstIndex()).toFloat() / (count - 1)).coerceIn(0f, 1f) }
    }
    LaunchedEffect(state.isScrollInProgress, dragging) {
        if (state.isScrollInProgress || dragging) visible = true
        else { delay(1600); visible = false }
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(220), label = "scrubber")
    if (alpha == 0f && !visible) return

    val fraction = if (dragging) dragFraction else scrollFraction
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxHeight().width(160.dp).padding(top = topInset, bottom = bottomInset).graphicsLayer { this.alpha = alpha }) {
        val trackPx = with(density) { (maxHeight - 40.dp).toPx() }.coerceAtLeast(1f)
        fun seek(y: Float) {
            dragFraction = ((y - with(density) { 20.dp.toPx() }) / trackPx).coerceIn(0f, 1f)
            val entry = (dragFraction * (count - 1)).roundToInt()
            state.requestScrollToItem(firstIndex() + entry)
        }
        // Year ticks while dragging.
        if (dragging) marks.forEach { (entry, year) ->
            val y = with(density) { (20.dp.toPx() + trackPx * entry / (count - 1)).toDp() }
            Text(
                year, style = MaterialTheme.typography.labelSmall, color = Nd.Muted,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-50).dp, y = y - 7.dp)
                    .clip(RoundedCornerShape(8.dp)).background(Nd.GlassStrong).padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        val thumbY = with(density) { (trackPx * fraction).roundToInt() }
        Row(
            Modifier.align(Alignment.TopEnd).offset { IntOffset(0, thumbY) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(dragging, enter = fadeIn() + slideInHorizontally { it / 3 }, exit = fadeOut() + slideOutHorizontally { it / 3 }) {
                Text(
                    labelAt(((if (dragging) dragFraction else fraction) * (count - 1)).roundToInt()),
                    style = MaterialTheme.typography.labelLarge, color = Nd.OnPrimary,
                    modifier = Modifier.softShadow(RoundedCornerShape(16.dp), 12.dp, 0.25f).clip(RoundedCornerShape(16.dp))
                        .background(Nd.Primary).padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.padding(end = 4.dp).size(width = 34.dp, height = 40.dp)
                    .softShadow(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 8.dp, bottomEnd = 8.dp), 10.dp, 0.22f)
                    .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(Nd.GlassStrong).border(1.dp, Nd.GlassBorder, RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 8.dp, bottomEnd = 8.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Lucide.ChevronsUpDown, "Scroll by date", tint = Nd.Ink, modifier = Modifier.size(18.dp)) }
        }
        // Touch strip along the right edge: drag anywhere on it to scrub.
        Box(
            Modifier.align(Alignment.TopEnd).width(44.dp).fillMaxHeight()
                .pointerInput(count) {
                    detectVerticalDragGestures(
                        onDragStart = { o -> dragging = true; haptics.performHapticFeedback(HapticFeedbackType.LongPress); seek(o.y) },
                        onDragEnd = { dragging = false }, onDragCancel = { dragging = false },
                    ) { change, _ -> change.consume(); seek(change.position.y) }
                }
                .pointerInput(count) { detectTapGestures { o -> seek(o.y) } },
        )
    }
}

