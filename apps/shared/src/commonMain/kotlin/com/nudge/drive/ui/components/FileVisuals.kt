package com.nudge.drive.ui.components

import com.composables.icons.lucide.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nudge.drive.util.ThumbSize
import com.nudge.drive.util.ViewerType
import com.nudge.drive.util.hasThumbnail
import com.nudge.drive.util.thumbUrl
import com.nudge.drive.util.viewerType
import com.nudge.drive.data.FileItem
import com.nudge.drive.data.Folder
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.ui.theme.Sora
import com.nudge.drive.util.FileKind
import com.nudge.drive.util.badge
import com.nudge.drive.util.folderColor
import com.nudge.drive.util.formatBytes
import com.nudge.drive.util.friendlyDate
import com.nudge.drive.util.kind
import com.nudge.drive.util.soft

/**
 * Coloured type tile; files with a server thumbnail (photos, videos, documents) show it on top.
 * Uses the small /thumbnail endpoint (~30 KB) instead of the original, and a plain AsyncImage
 * (no subcomposition) so long grids scroll smoothly.
 */
@Composable
fun FileThumb(file: FileItem, modifier: Modifier = Modifier, size: Dp = 44.dp, corner: Dp = 14.dp, showThumbnail: Boolean = true) {
    val k = file.kind()
    val shape = RoundedCornerShape(corner)
    val big = size >= 64.dp
    Box(modifier.size(size).clip(shape).background(k.soft), contentAlignment = Alignment.Center) {
        if (big) Icon(k.icon, null, tint = k.color, modifier = Modifier.size(size * 0.42f))
        else Text(file.badge(), color = k.color, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.24f).sp, maxLines = 1)
        val vt = file.viewerType()
        val wantThumb = showThumbnail && file.hasThumbnail() && (big || vt == ViewerType.Image || vt == ViewerType.Video)
        if (wantThumb) {
            val ctx = LocalPlatformContext.current
            val px = if (big) ThumbSize.Grid else ThumbSize.Small
            val request = remember(file.id, px) {
                ImageRequest.Builder(ctx).data(thumbUrl(file.id, px)).crossfade(140).build()
            }
            var loaded by remember(file.id) { mutableStateOf(false) }
            AsyncImage(
                request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                onSuccess = { loaded = true },
            )
            if (vt == ViewerType.Video && loaded) Box(
                Modifier.size(if (big) 34.dp else 18.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Lucide.Play, null, tint = Color.White, modifier = Modifier.size(if (big) 16.dp else 9.dp)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    file: FileItem,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    subtitle: String? = null,
    thumbModifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
) {
    val bg by animateColorAsState(if (selected) Nd.Mist else Nd.Surface, label = "rowbg")
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(66.dp)
            .softShadow(shape, 8.dp, 0.06f)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) Nd.Lavender else Nd.CardBorder, shape)
            .pressScale(interaction, 0.98f)
            .combinedClickable(interaction, indication = null, onClick = onClick, onLongClick = onLongClick)
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            FileThumb(file, thumbModifier)
            androidx.compose.animation.AnimatedVisibility(selectionMode, enter = scaleIn(), exit = scaleOut(), modifier = Modifier.align(Alignment.BottomEnd)) {
                Icon(
                    if (selected) Lucide.CircleCheck else Lucide.Circle, null,
                    tint = if (selected) Nd.Iris else Nd.Subtle,
                    modifier = Modifier.size(20.dp).background(Nd.Surface, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle ?: "${formatBytes(file.size)} · ${file.connectedAccount?.email?.substringBefore('@') ?: "drive"} · ${friendlyDate(file.createdAt)}",
                style = MaterialTheme.typography.bodySmall, color = Nd.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (onMore != null && !selectionMode) {
            Box(Modifier.size(44.dp).clip(CircleShape).bounceClick(onClick = onMore), contentAlignment = Alignment.Center) {
                Icon(Lucide.EllipsisVertical, "More", tint = Nd.Ink)
            }
        }
    }
}

/** Grid cell for the grid view. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridCell(
    file: FileItem, selected: Boolean, selectionMode: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
    modifier: Modifier = Modifier, thumbModifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .softShadow(shape, 8.dp, 0.06f)
            .clip(shape)
            .background(if (selected) Nd.Mist else Nd.Surface)
            .border(1.dp, if (selected) Nd.Lavender else Nd.CardBorder, shape)
            .pressScale(interaction, 0.97f)
            .combinedClickable(interaction, indication = null, onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
    ) {
        Box {
            FileThumb(file, thumbModifier.fillMaxWidth().aspectRatio(1.15f), size = 200.dp, corner = 16.dp)
            if (selectionMode) Icon(
                if (selected) Lucide.CircleCheck else Lucide.Circle, null,
                tint = if (selected) Nd.Iris else Nd.Subtle,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).background(Nd.Surface, CircleShape),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(file.name, style = MaterialTheme.typography.labelMedium, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Text(formatBytes(file.size), style = MaterialTheme.typography.labelSmall, color = Nd.Muted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
    }
}

/** Folder-shaped card (tab + body) tinted with the folder's colour. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderCard(folder: Folder, modifier: Modifier = Modifier, subtitle: String? = null, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val c = folderColor(folder.color)
    val tint = c.copy(alpha = if (Nd.isDark) 0.28f else 0.22f).compositeOver(Nd.Surface)
    val interaction = remember { MutableInteractionSource() }
    Box(modifier.width(124.dp).height(118.dp).pressScale(interaction).combinedClickable(interaction, null, onClick = onClick, onLongClick = onLongClick)) {
        Box(Modifier.padding(top = 0.dp).width(54.dp).height(22.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(tint))
        Column(
            Modifier
                .padding(top = 12.dp)
                .fillMaxSize()
                .softShadow(RoundedCornerShape(0.dp, 20.dp, 20.dp, 20.dp), 10.dp, 0.08f)
                .clip(RoundedCornerShape(0.dp, 20.dp, 20.dp, 20.dp))
                .background(tint)
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(Lucide.Folder, null, tint = c, modifier = Modifier.size(22.dp))
            Column {
                Text(folder.name, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Nd.Muted)
            }
        }
    }
}

/** Full-width folder row used in pickers and lists. */
@Composable
fun FolderRow(folder: Folder, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val c = folderColor(folder.color)
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.fillMaxWidth().height(58.dp).clip(shape).background(Nd.Surface).border(1.dp, Nd.CardBorder, shape)
            .bounceClick(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Lucide.Folder, null, tint = c, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(folder.name, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}

/** Animated usage ring with percentage label. */
@Composable
fun UsageRing(fraction: Float, modifier: Modifier = Modifier, size: Dp = 56.dp, stroke: Dp = 7.dp, color: Color = Nd.Iris, track: Color = Nd.Mist, label: String? = null) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(1000), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val d = this.size.minDimension - w
            val tl = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, tl, androidx.compose.ui.geometry.Size(d, d), style = Stroke(w))
            drawArc(color, -90f, 360f * f, false, tl, androidx.compose.ui.geometry.Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
        }
        Text(label ?: "${(fraction * 100).toInt()}%", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.22f).sp, color = Nd.Ink)
    }
}

private fun Color.compositeOverWhite(): Color = Color(
    red = red * alpha + (1 - alpha), green = green * alpha + (1 - alpha), blue = blue * alpha + (1 - alpha), alpha = 1f,
)
