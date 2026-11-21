package com.nudge.drive.ui.theme

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Light/dark switch with a circular reveal: the old screen is frozen in a snapshot,
 * the new theme is applied underneath, and a growing circle from the button uncovers it.
 */
object ThemeReveal {
    var snapshot by mutableStateOf<ImageBitmap?>(null); private set
    var center by mutableStateOf(Offset.Zero); private set
    val progress = Animatable(0f)
    private var running = false

    fun switch(activity: Activity?, from: Offset, scope: CoroutineScope, apply: () -> Unit) {
        val window = activity?.window
        val view = window?.decorView
        if (running || window == null || view == null || view.width == 0 || android.os.Build.VERSION.SDK_INT < 26) { apply(); return }
        running = true
        val bmp = runCatching { Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888) }.getOrNull()
        if (bmp == null) { running = false; apply(); return }
        try {
            PixelCopy.request(window, bmp, { result ->
                if (result == PixelCopy.SUCCESS) {
                    snapshot = bmp.asImageBitmap(); center = from
                }
                apply()
                scope.launch(kotlinx.coroutines.NonCancellable) {
                    try {
                        progress.snapTo(0f)
                        if (snapshot != null) progress.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
                    } finally {
                        snapshot = null; running = false
                    }
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            running = false; apply()
        }
    }
}

/** Draw last, above everything: the old theme with a hole that grows over the new one. Blocks taps while it runs. */
@Composable
fun ThemeRevealOverlay() {
    val snap = ThemeReveal.snapshot ?: return
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }) {
        val c = ThemeReveal.center
        val far = listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height))
            .maxOf { hypot(it.x - c.x, it.y - c.y) }
        val r = far * ThemeReveal.progress.value
        val hole = Path().apply { addOval(Rect(c, r)) }
        clipPath(hole, ClipOp.Difference) { drawImage(snap) }
    }
}
