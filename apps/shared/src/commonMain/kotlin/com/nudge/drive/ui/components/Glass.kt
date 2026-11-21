package com.nudge.drive.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nudge.drive.ui.theme.Nd
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Full-screen lavender → white backdrop. */
@Composable
fun ScreenBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(Nd.Background), content = content)
}

/** Springy press feedback used on every tappable surface. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.96f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer { scaleX = s; scaleY = s }
}

fun Modifier.bounceClick(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    this
        .pressScale(interaction)
        .clickable(interaction, indication = null, enabled = enabled, role = role) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Lavender glow in light mode, soft black depth in dark mode. */
fun Modifier.softShadow(shape: Shape, elevation: Dp = 18.dp, alpha: Float = 0.14f): Modifier = composed {
    val dark = com.nudge.drive.ui.theme.LocalNd.current.dark
    val c = if (dark) Color.Black.copy(alpha = (alpha * 2.4f).coerceAtMost(0.6f)) else Nd.Shadow.copy(alpha = alpha)
    shadow(elevation, shape, clip = false, ambientColor = c, spotColor = c)
}

/** Frosted white card. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 26.dp,
    strong: Boolean = false,
    padding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Column(
        modifier
            .softShadow(shape)
            .clip(shape)
            .background(if (strong) Nd.GlassStrong else Nd.Glass)
            .border(1.dp, Nd.GlassBorder, shape)
            .then(if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    container: Color = Nd.GlassButton,
    tint: Color = if (container == Nd.Primary) Nd.OnPrimary else Nd.Ink,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .border(1.dp, Nd.GlassBorder, CircleShape)
            .bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.44f)) }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    container: Color = Nd.Primary,
    content: Color = Nd.OnPrimary,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val bg by animateColorAsState(if (enabled) container else container.copy(alpha = 0.5f), label = "btn")
    Row(
        modifier
            .height(56.dp)
            .softShadow(shape, 16.dp, if (enabled) 0.22f else 0f)
            .clip(shape)
            .background(bg)
            .bounceClick(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = content, strokeWidth = 2.4.dp)
        } else {
            if (icon != null) {
                Icon(icon, null, tint = content, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize * 1.1f), color = content)
        }
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(Nd.Surface)
            .border(1.dp, Nd.Line, shape)
            .bounceClick(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = Nd.Ink, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = Nd.Ink)
    }
}

/** Pill-shaped input in the Lavender style. */
@Composable
fun PillField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    glass: Boolean = false,
    singleLine: Boolean = true,
) {
    val shape = RoundedCornerShape(28.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Nd.Ink),
        cursorBrush = SolidColor(Nd.Iris),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(shape)
                    .background(if (glass) Nd.Field else Nd.FieldSolid)
                    .border(1.dp, if (glass) Nd.GlassBorder else Nd.Line, shape)
                    .padding(start = if (leading != null) 16.dp else 22.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) { Icon(leading, null, tint = Nd.Muted, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(10.dp)) }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = Nd.Subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

/** Diagonal-striped storage bar; stripes drift slowly so the bar feels alive. */
@Composable
fun StripedBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 22.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(1100), label = "bar")
    val shift by rememberInfiniteTransition(label = "stripes").animateFloat(
        0f, 20f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "shift",
    )
    val shape = RoundedCornerShape(height / 2)
    Box(modifier.height(height).clip(shape).background(Nd.Mist)) {
        Box(
            Modifier
                .fillMaxWidth(animated.coerceAtLeast(0.02f))
                .height(height)
                .clip(shape)
                .drawBehind {
                    drawRect(
                        Brush.linearGradient(
                            0f to Nd.Iris, 0.5f to Nd.Iris, 0.5f to Nd.Lavender, 1f to Nd.Lavender,
                            start = Offset(shift, 0f), end = Offset(shift + 10f, 10f), tileMode = TileMode.Repeated,
                        ),
                        size = Size(size.width, size.height),
                    )
                },
        )
    }
}

/** Shimmering placeholder block for loading states. */
@Composable
fun Shimmer(modifier: Modifier = Modifier, corner: Dp = 16.dp) {
    val x by rememberInfiniteTransition(label = "shimmer").animateFloat(
        -400f, 1200f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x",
    )
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(
                Brush.linearGradient(
                    listOf(Nd.Mist, Nd.Surface, Nd.Mist), start = Offset(x, 0f), end = Offset(x + 400f, 200f),
                ),
            ),
    )
}

data class NavItem(val key: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

/**
 * iOS-style floating glass capsule. The selected tab grows into a lavender pill that shows its name.
 */
@Composable
fun GlassBottomBar(
    items: List<NavItem>,
    selected: String,
    hazeState: HazeState,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(36.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(70.dp)
            .softShadow(shape, 22.dp, 0.20f)
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = Nd.Haze,
                    tints = listOf(HazeTint(Nd.NavTint)),
                    blurRadius = 24.dp,
                    noiseFactor = 0.05f,
                ),
            )
            .border(BorderStroke(1.dp, Brush.verticalGradient(listOf(Nd.GlassBorder, Nd.GlassSoft))), shape)
            .padding(horizontal = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val active = item.key == selected
            val bg by animateColorAsState(if (active) Nd.Primary else Color.Transparent, tween(260), label = "navbg")
            Row(
                Modifier
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(bg)
                    .bounceClick(role = Role.Tab) { onSelect(item.key) }
                    .padding(horizontal = if (active) 18.dp else 17.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val fg by animateColorAsState(if (active) Nd.OnPrimary else Nd.Ink, label = "navfg")
                Icon(if (active) item.selectedIcon else item.icon, item.label, tint = fg, modifier = Modifier.size(22.dp))
                AnimatedVisibility(
                    visible = active,
                    enter = expandHorizontally(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(220, 80)),
                    exit = shrinkHorizontally(tween(200)) + fadeOut(tween(120)),
                ) {
                    Text(item.label, style = MaterialTheme.typography.labelLarge, color = Nd.OnPrimary, modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Nd.Ink, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action, style = MaterialTheme.typography.labelMedium, color = Nd.IrisDeep,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onAction).padding(6.dp),
            )
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Nd.Inverse else Nd.Glass, label = "chip")
    val fg by animateColorAsState(if (selected) Nd.OnInverse else Nd.Ink, label = "chipfg")
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .height(38.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) Nd.Inverse else Nd.GlassBorder, shape)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(28.dp)).background(Nd.Glass).border(1.dp, Nd.GlassBorder, RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Nd.Iris, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Nd.Ink)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(18.dp)); action() }
    }
}
