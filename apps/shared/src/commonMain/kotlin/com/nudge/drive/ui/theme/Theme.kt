package com.nudge.drive.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Theme-dependent colours. Brand + file-type accents stay constant in [Nd]. */
@Immutable
data class NdColors(
    val dark: Boolean,
    val background: Brush,
    val authBackground: Brush,
    val surface: Color,
    val sheet: Color,
    val glass: Color,
    val glassStrong: Color,
    val glassSoft: Color,
    val glassButton: Color,
    val glassBorder: Color,
    val field: Color,
    val fieldSolid: Color,
    val ink: Color,
    val muted: Color,
    val subtle: Color,
    val mist: Color,
    val lilac: Color,
    val line: Color,
    val cardBorder: Color,
    val haze: Color,
    val link: Color,
    val accent: Color,
    val inverse: Color,
    val onInverse: Color,
    val navTint: Color,
    val danger: Color,
    val dangerSoft: Color,
    val success: Color,
    val successSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val shadowAlpha: Float,
)

val LightNd = NdColors(
    dark = false,
    background = Brush.verticalGradient(0f to Color(0xFFD9CEFF), 0.28f to Color(0xFFEBE5FF), 0.56f to Color(0xFFF7F4FF), 0.85f to Color.White),
    authBackground = Brush.verticalGradient(listOf(Color(0xFFCDBFFF), Color(0xFFDCD2FF), Color(0xFFEBE5FF))),
    surface = Color.White,
    sheet = Color(0xFFFBFAFF),
    glass = Color.White.copy(alpha = 0.72f),
    glassStrong = Color.White.copy(alpha = 0.92f),
    glassSoft = Color.White.copy(alpha = 0.45f),
    glassButton = Color.White.copy(alpha = 0.5f),
    glassBorder = Color.White.copy(alpha = 0.92f),
    field = Color.White.copy(alpha = 0.65f),
    fieldSolid = Color(0xFFF6F3FF),
    ink = Color(0xFF1E1440),
    muted = Color(0xFF4E4670),
    subtle = Color(0xFF6B6390),
    mist = Color(0xFFECE6FF),
    lilac = Color(0xFFD9CEFF),
    line = Color(0xFFE7E1F7),
    cardBorder = Color(0xFFEEE9FA),
    haze = Color(0xFFF7F4FF),
    link = Color(0xFF6A4FE0),
    accent = Color(0xFF8B72F5),
    inverse = Color(0xFF1E1440),
    onInverse = Color.White,
    navTint = Color.White.copy(alpha = 0.45f),
    danger = Color(0xFFC62828),
    dangerSoft = Color(0xFFFFE4E4),
    success = Color(0xFF1E8F50),
    successSoft = Color(0xFFE2F6EA),
    warn = Color(0xFF9A6700),
    warnSoft = Color(0xFFFFF4D1),
    shadowAlpha = 1f,
)

val DarkNd = NdColors(
    dark = true,
    // True black with a faint violet glow at the very top.
    background = Brush.verticalGradient(0f to Color(0xFF16121F), 0.22f to Color(0xFF07060A), 0.5f to Color.Black, 1f to Color.Black),
    authBackground = Brush.verticalGradient(listOf(Color(0xFF1E1830), Color(0xFF0A0910), Color.Black)),
    surface = Color(0xFF131218),
    sheet = Color(0xFF0E0D12),
    glass = Color.White.copy(alpha = 0.06f),
    glassStrong = Color(0xF0151419),
    glassSoft = Color.White.copy(alpha = 0.04f),
    glassButton = Color.White.copy(alpha = 0.08f),
    glassBorder = Color.White.copy(alpha = 0.10f),
    field = Color.White.copy(alpha = 0.06f),
    fieldSolid = Color(0xFF1A1920),
    ink = Color(0xFFF4F2FA),
    muted = Color(0xFFB4B0C3),
    subtle = Color(0xFF8A8699),
    mist = Color(0xFF1F1D27),
    lilac = Color(0xFF3A3550),
    line = Color(0xFF26242E),
    cardBorder = Color(0xFF232129),
    haze = Color.Black,
    link = Color(0xFFC4B5FF),
    accent = Color(0xFFB9A6FF),
    inverse = Color(0xFFF4F2FA),
    onInverse = Color(0xFF0E0D12),
    navTint = Color(0xB3121116),
    danger = Color(0xFFFF8A80),
    dangerSoft = Color(0x33FF6B6B),
    success = Color(0xFF6EE7A8),
    successSoft = Color(0x332EB872),
    warn = Color(0xFFF5C451),
    warnSoft = Color(0x33F5B400),
    shadowAlpha = 0f,
)

// Dynamic (not static) so lazy-list items pick up a theme switch in the same frame.
val LocalNd = androidx.compose.runtime.compositionLocalOf { LightNd }

/** Lavender Glass palette. Theme tokens read the current light/dark set; brand accents are constants. */
object Nd {
    // ---- constants (same in both themes)
    val Lavender = Color(0xFFB9A6FF)
    val Iris = Color(0xFF8B72F5)
    /** Main action colour (buttons, selected tab). White content passes 4.5:1 contrast. */
    val Primary = Color(0xFF7059F0)
    val OnPrimary = Color.White
    val White = Color.White
    val Pdf = Color(0xFFE8402B)
    val Video = Color(0xFFF59E0B)
    val Image = Color(0xFF1E9E57)
    val Doc = Color(0xFF2E64E0)
    val Audio = Color(0xFFB07800)
    val Zip = Color(0xFFA23FCF)
    val Shadow = Color(0xFF6E50DC)
    val Stripes = listOf(Iris, Lavender)

    // ---- theme tokens
    val isDark: Boolean @Composable @ReadOnlyComposable get() = LocalNd.current.dark
    val Background: Brush @Composable @ReadOnlyComposable get() = LocalNd.current.background
    val AuthBackground: Brush @Composable @ReadOnlyComposable get() = LocalNd.current.authBackground
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalNd.current.surface
    val Sheet: Color @Composable @ReadOnlyComposable get() = LocalNd.current.sheet
    val Glass: Color @Composable @ReadOnlyComposable get() = LocalNd.current.glass
    val GlassStrong: Color @Composable @ReadOnlyComposable get() = LocalNd.current.glassStrong
    val GlassSoft: Color @Composable @ReadOnlyComposable get() = LocalNd.current.glassSoft
    val GlassButton: Color @Composable @ReadOnlyComposable get() = LocalNd.current.glassButton
    val GlassBorder: Color @Composable @ReadOnlyComposable get() = LocalNd.current.glassBorder
    val Field: Color @Composable @ReadOnlyComposable get() = LocalNd.current.field
    val FieldSolid: Color @Composable @ReadOnlyComposable get() = LocalNd.current.fieldSolid
    val Ink: Color @Composable @ReadOnlyComposable get() = LocalNd.current.ink
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalNd.current.muted
    val Subtle: Color @Composable @ReadOnlyComposable get() = LocalNd.current.subtle
    val Mist: Color @Composable @ReadOnlyComposable get() = LocalNd.current.mist
    val Lilac: Color @Composable @ReadOnlyComposable get() = LocalNd.current.lilac
    val Line: Color @Composable @ReadOnlyComposable get() = LocalNd.current.line
    val CardBorder: Color @Composable @ReadOnlyComposable get() = LocalNd.current.cardBorder
    val Haze: Color @Composable @ReadOnlyComposable get() = LocalNd.current.haze
    val IrisDeep: Color @Composable @ReadOnlyComposable get() = LocalNd.current.link
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalNd.current.accent
    val Inverse: Color @Composable @ReadOnlyComposable get() = LocalNd.current.inverse
    val OnInverse: Color @Composable @ReadOnlyComposable get() = LocalNd.current.onInverse
    val NavTint: Color @Composable @ReadOnlyComposable get() = LocalNd.current.navTint
    val Danger: Color @Composable @ReadOnlyComposable get() = LocalNd.current.danger
    val DangerSoft: Color @Composable @ReadOnlyComposable get() = LocalNd.current.dangerSoft
    val Success: Color @Composable @ReadOnlyComposable get() = LocalNd.current.success
    val SuccessSoft: Color @Composable @ReadOnlyComposable get() = LocalNd.current.successSoft
    val Warn: Color @Composable @ReadOnlyComposable get() = LocalNd.current.warn
    val WarnSoft: Color @Composable @ReadOnlyComposable get() = LocalNd.current.warnSoft
    val ShadowAlpha: Float @Composable @ReadOnlyComposable get() = LocalNd.current.shadowAlpha

    /** Pastel tile behind a coloured icon: light pastel by day, translucent tint at night. */
    @Composable @ReadOnlyComposable
    fun soft(c: Color): Color = if (LocalNd.current.dark) c.copy(alpha = 0.2f) else lerpWhite(c, 0.86f)

    private fun lerpWhite(c: Color, t: Float) = Color(c.red + (1 - c.red) * t, c.green + (1 - c.green) * t, c.blue + (1 - c.blue) * t, 1f)
}

/** Brand fonts (variable TTFs bundled per platform). */
expect val Sora: FontFamily
expect val Inter: FontFamily

/** Light/dark status- and navigation-bar icons (Android); nothing to do on desktop. */
@Composable
internal expect fun SystemBarsEffect(dark: Boolean)

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.8).sp),
    headlineSmall = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
)

private fun scheme(t: NdColors) = if (t.dark) darkColorScheme(
    primary = Nd.Primary, onPrimary = Nd.OnPrimary, primaryContainer = t.mist, onPrimaryContainer = t.ink,
    secondary = Nd.Iris, onSecondary = Color.White, background = t.haze, onBackground = t.ink,
    surface = t.surface, onSurface = t.ink, surfaceVariant = t.mist, onSurfaceVariant = t.muted,
    surfaceContainer = t.surface, surfaceContainerHigh = t.surface, surfaceContainerHighest = t.mist,
    surfaceContainerLow = t.haze, surfaceContainerLowest = t.haze, outline = t.line, outlineVariant = t.line,
    error = t.danger, onError = Color.Black,
) else lightColorScheme(
    primary = Nd.Primary, onPrimary = Nd.OnPrimary, primaryContainer = t.mist, onPrimaryContainer = t.ink,
    secondary = Nd.Iris, onSecondary = Color.White, tertiary = t.link, background = t.haze, onBackground = t.ink,
    surface = t.surface, onSurface = t.ink, surfaceVariant = t.mist, onSurfaceVariant = t.muted,
    surfaceContainer = t.surface, surfaceContainerHigh = t.surface, surfaceContainerHighest = t.haze,
    surfaceContainerLow = t.haze, surfaceContainerLowest = t.surface, outline = t.line, outlineVariant = t.line,
    error = t.danger, onError = Color.White,
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(26.dp), extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun NudgeTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    val tokens = if (dark) DarkNd else LightNd
    SystemBarsEffect(dark)
    CompositionLocalProvider(LocalNd provides tokens) {
        MaterialTheme(colorScheme = scheme(tokens), typography = typography, shapes = shapes, content = content)
    }
}
