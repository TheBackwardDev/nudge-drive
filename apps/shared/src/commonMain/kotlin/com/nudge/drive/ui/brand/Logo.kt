package com.nudge.drive.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nudge.drive.ui.theme.Nd

/** Centre line of the "Kick n" mark on a 256 grid; drawn with a 40-unit round stroke. */
private const val KICK_N = "M56 202V110a56 56 0 0 1 112 0v60a32 32 0 0 0 32 32"

private val kickPath: Path by lazy { PathParser().parsePathString(KICK_N).toPath() }

/**
 * The Kick‑n mark. [progress] (0..1) draws the stroke along its length, used for the splash animation.
 */
@Composable
fun KickN(
    modifier: Modifier = Modifier,
    color: Color = Nd.Ink,
    progress: Float = 1f,
) {
    Canvas(modifier) {
        val s = size.minDimension / 256f
        val drawn = if (progress >= 1f) kickPath else Path().also { out ->
            val pm = PathMeasure().apply { setPath(kickPath, false) }
            pm.getSegment(0f, pm.length * progress.coerceIn(0f, 1f), out, true)
        }
        translate((size.width - 256f * s) / 2f, (size.height - 256f * s) / 2f) {
            scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                drawPath(drawn, color, style = Stroke(width = 40f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** App icon tile: white Kick‑n on the lavender→iris glow. */
@Composable
fun LogoTile(size: Dp = 56.dp, progress: Float = 1f, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(listOf(Color(0xFFCDBFFF), Nd.Iris))),
        contentAlignment = Alignment.Center,
    ) {
        KickN(Modifier.size(size * 0.62f), color = Color.White, progress = progress)
    }
}

/** Logo tile that draws itself once, then holds. */
@Composable
fun AnimatedLogoTile(size: Dp = 96.dp, modifier: Modifier = Modifier, durationMs: Int = 900) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
    LogoTile(size = size, progress = p.value, modifier = modifier)
}
