package com.nudge.drive.ui.nav

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
private val springBounds = BoundsTransform { _: Rect, _: Rect -> spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow) }

/** Shared-element hook: the same [key] on two screens morphs between them during navigation. */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.shared(key: String): Modifier = composed {
    val s = LocalSharedScope.current
    val a = LocalNavScope.current
    if (s == null || a == null) this else with(s) {
        this@composed.sharedElement(rememberSharedContentState(key), a, boundsTransform = springBounds)
    }
}

@Composable
fun <T> navSpring() = spring<T>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

val offsetSpring = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1))
