package com.nudge.drive.ui.screens.viewer

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

/** Pinch / pan / double-tap zoom for one photo. Offsets are in screen pixels relative to the centre. */
@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f); private set
    var offset by mutableStateOf(Offset.Zero); private set
    var box: IntSize = IntSize.Zero
    var content: Size = Size.Unspecified
    val zoomed: Boolean get() = scale > 1.01f

    /** Size the picture occupies on screen at 1x (ContentScale.Fit). */
    private fun fitted(): Size {
        val bw = box.width.toFloat(); val bh = box.height.toFloat()
        if (bw <= 0f || bh <= 0f) return Size.Zero
        if (content.isUnspecified || content.width <= 0f || content.height <= 0f) return Size(bw, bh)
        val a = content.width / content.height
        return if (a > bw / bh) Size(bw, bw / a) else Size(bh * a, bh)
    }

    private fun clamp(o: Offset, s: Float): Offset {
        val f = fitted()
        val mx = max(0f, (f.width * s - box.width) / 2f)
        val my = max(0f, (f.height * s - box.height) / 2f)
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    /** Applies one gesture step; returns true if the picture moved (so the gesture is ours, not the pager's). */
    fun transform(zoom: Float, pan: Offset, centroid: Offset): Boolean {
        val old = scale
        val ns = (scale * zoom).coerceIn(1f, 6f)
        val c = centroid - Offset(box.width / 2f, box.height / 2f)
        val o = clamp(c - (c - offset) * (ns / old) + pan, ns)
        val moved = ns != old || o != offset
        scale = ns; offset = o
        return moved
    }

    suspend fun animateTo(target: Float, targetOffset: Offset) {
        val s0 = scale; val o0 = offset
        val end = clamp(targetOffset, target)
        animate(0f, 1f, animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { v, _ ->
            scale = lerp(s0, target, v); offset = lerp(o0, end, v)
        }
    }

    suspend fun toggle(tap: Offset) {
        if (zoomed) animateTo(1f, Offset.Zero)
        else {
            val c = tap - Offset(box.width / 2f, box.height / 2f)
            animateTo(2.5f, -c * 1.5f)
        }
    }

    fun snapReset() { scale = 1f; offset = Offset.Zero }
}

/**
 * Two fingers always zoom. One finger pans only while zoomed in; at 1x it is left alone so the
 * pager can swipe to the next photo and the screen can be swiped down to close.
 */
fun Modifier.zoomable(state: ZoomState, onTap: () -> Unit): Modifier = this
    .pointerInput(state) {
        coroutineScope {
            detectTapGestures(onTap = { onTap() }, onDoubleTap = { p -> launch { state.toggle(p) } })
        }
    }
    .pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed == 0) break
                if (pressed >= 2 || state.zoomed) {
                    val moved = state.transform(event.calculateZoom(), event.calculatePan(), event.calculateCentroid(useCurrent = true))
                    if (moved || pressed >= 2) event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
