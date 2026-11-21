package com.nudge.drive.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.ui.theme.Sora
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

private data class Flyer(val label: String, val bg: Color, val fg: Color, val fromX: Float, val fromY: Float, val rot: Float, val delayMs: Long)

private val flyers = listOf(
    Flyer("JPG", Color(0xFF1E9E57), Color.White, -150f, -260f, -18f, 0),
    Flyer("MP4", Color(0xFFF59E0B), Color(0xFF1F1300), 150f, -230f, 16f, 50),
    Flyer("PDF", Color.White, Color(0xFFE8402B), -170f, 210f, 12f, 100),
    Flyer("DOC", Color(0xFF2E64E0), Color.White, 165f, 240f, -14f, 150),
    Flyer("ZIP", Color(0xFFA23FCF), Color.White, -10f, -330f, 6f, 200),
    Flyer("MP3", Color(0xFFB07800), Color.White, 20f, 330f, -8f, 250),
)

/**
 * Opening animation: file tiles fly in from around the screen and drop into the logo,
 * which bounces as each one lands, then the name fades in. Tap to skip.
 */
@Composable
fun AnimatedSplash(onDone: () -> Unit) {
    val progress = remember { flyers.map { Animatable(0f) } }
    val logoScale = remember { Animatable(0.6f) }
    val draw = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { logoScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f)) }
        // Starts on the first frame (the system splash is a blank screen of the same colour), ~1.1 s total.
        flyers.mapIndexed { i, f ->
            launch {
                delay(f.delayMs)
                progress[i].animateTo(1f, tween(360, easing = FastOutLinearInEasing))
                // the logo "swallows" the tile
                logoScale.animateTo(1.1f, tween(50)); logoScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 700f))
            }
        }.joinAll()
        launch { draw.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) }
        title.animateTo(1f, tween(260))
        delay(180)
        onDone()
    }

    Box(
        Modifier.fillMaxSize().background(if (Nd.isDark) Color(0xFF07060A) else Color(0xFFF1EDFF))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        flyers.forEachIndexed { i, f ->
            val p = progress[i].value
            if (p < 1f) Column(
                Modifier
                    .offset(x = (f.fromX * (1 - p)).dp, y = (f.fromY * (1 - p)).dp)
                    .graphicsLayer {
                        val s = 1f - 0.75f * p
                        scaleX = s; scaleY = s; rotationZ = f.rot * (1 - p); alpha = if (p > 0.85f) (1 - p) / 0.15f else 1f
                    }
                    .size(66.dp, 76.dp).clip(RoundedCornerShape(16.dp)).background(f.bg).padding(10.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(f.label, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = f.fg)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(32.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(f.fg.copy(alpha = 0.5f)))
            }
        }
        LogoTile(
            108.dp,
            progress = if (draw.value == 0f) 1f else draw.value,
            modifier = Modifier.graphicsLayer { scaleX = logoScale.value; scaleY = logoScale.value },
        )
        Column(
            Modifier.align(Alignment.Center).offset(y = 110.dp).graphicsLayer { alpha = title.value; translationY = (1 - title.value) * 30f },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Nudge Drive", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.8).sp, color = Nd.Ink)
            Text("All your drives, one pool", fontSize = 15.sp, color = Nd.Muted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
