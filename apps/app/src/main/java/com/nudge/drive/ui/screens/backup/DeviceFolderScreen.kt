package com.nudge.drive.ui.screens.backup

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.nudge.drive.AppContainer
import com.nudge.drive.data.DeviceFolder
import com.nudge.drive.data.DeviceMedia
import com.nudge.drive.data.DeviceMediaItem
import com.nudge.drive.ui.components.GlassIconButton
import com.nudge.drive.ui.components.ScreenBackground
import com.nudge.drive.ui.components.ScrollToTopButton
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.screens.viewer.ZoomState
import com.nudge.drive.ui.screens.viewer.zoomable
import com.nudge.drive.ui.theme.Nd
import kotlinx.coroutines.launch

/** One phone photo folder: every photo/video with a "backed up" tick, a switch for the folder, and a viewer. */
@Composable
fun DeviceFolderScreen(c: AppContainer, bucketId: Long, nav: NavController) {
    val ctx = LocalContext.current
    val cfg by c.backup.config.collectAsState()
    val done by c.backup.backedUp.collectAsState()
    var folder by remember { mutableStateOf<DeviceFolder?>(null) }
    var items by remember { mutableStateOf<List<DeviceMediaItem>?>(null) }
    var open by remember { mutableStateOf<Int?>(null) }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val showTop by remember { derivedStateOf { grid.firstVisibleItemIndex > 30 } }

    LaunchedEffect(bucketId) {
        folder = DeviceMedia.folders(ctx).firstOrNull { it.bucketId == bucketId }
        items = DeviceMedia.items(ctx, bucketId)
    }
    val selected = cfg.buckets.any { it.bucketId == bucketId }
    val list = items.orEmpty()
    val backed = remember(list, done) { list.count { it.key in done } }

    BackHandler(open != null) { open = null }
    ScreenBackground {
        LazyVerticalGrid(
            GridCells.Fixed(4), state = grid, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = 40.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassIconButton(Lucide.ArrowLeft, "Back") { nav.popBackStack() }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(folder?.name ?: "Folder", style = MaterialTheme.typography.headlineSmall, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (items == null) "Loading…" else "${list.size} items" + if (selected) " · $backed backed up" else "",
                                style = MaterialTheme.typography.bodySmall, color = Nd.Muted,
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(20.dp)).background(Nd.Glass).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Lucide.CircleCheck, null, tint = if (selected) Nd.Primary else Nd.Muted, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(if (selected) "Backing up this folder" else "Back up this folder", style = MaterialTheme.typography.titleSmall, color = Nd.Ink, modifier = Modifier.weight(1f))
                        NdSwitch(selected) { v ->
                            folder?.let { c.backup.setBucket(it, v) }
                            if (v) { c.backup.update { it.copy(enabled = true) }; c.backup.runNow(); c.toast("Backing up ${folder?.name ?: "folder"}") }
                        }
                    }
                }
            }
            if (items == null) item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Nd.Iris) }
            }
            itemsIndexed(list, key = { _, m -> m.id }) { i, m ->
                Box(Modifier.padding(2.dp).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(Nd.Mist).bounceClick { open = i }) {
                    AsyncImage(m.uri, m.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (m.isVideo) Text(
                        duration(m.durationMs), style = MaterialTheme.typography.labelSmall, color = Color.White,
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                    if (m.key in done) Icon(
                        Lucide.CircleCheck, "Backed up", tint = Color.White,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).clip(CircleShape).background(Nd.Primary.copy(alpha = 0.85f)).padding(3.dp),
                    )
                }
            }
        }
        ScrollToTopButton(showTop, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp)) { scope.launch { grid.animateScrollToItem(0) } }
        AnimatedVisibility(open != null, enter = fadeIn() + scaleIn(initialScale = 0.94f), exit = fadeOut() + scaleOut(targetScale = 0.94f)) {
            open?.let { start -> LocalMediaViewer(list, start, done, onClose = { open = null }) }
        }
    }
}

private fun duration(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

/** Full-screen swipe viewer for photos/videos on the phone. */
@OptIn(UnstableApi::class)
@Composable
private fun LocalMediaViewer(items: List<DeviceMediaItem>, start: Int, done: Set<String>, onClose: () -> Unit) {
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    val ctx = LocalContext.current
    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { items[it].id }) { page ->
            val m = items[page]
            if (m.isVideo) {
                if (pager.settledPage == page) {
                    DisposableEffect(m.id) {
                        player.setMediaItem(MediaItem.fromUri(m.uri)); player.prepare(); player.playWhenReady = true
                        onDispose { player.stop(); player.clearMediaItems() }
                    }
                    AndroidView({ PlayerView(it).apply { this.player = player } }, Modifier.fillMaxSize().navigationBarsPadding(), onRelease = { it.player = null })
                } else AsyncImage(m.uri, m.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                val zoom = remember(m.id) { ZoomState() }
                Box(Modifier.fillMaxSize().onSizeChanged { zoom.box = it }.zoomable(zoom) {}) {
                    AsyncImage(
                        m.uri, m.name, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom.scale; scaleY = zoom.scale; translationX = zoom.offset.x; translationY = zoom.offset.y },
                    )
                }
            }
        }
        val cur = items.getOrNull(pager.currentPage)
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).bounceClick(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Lucide.ArrowLeft, "Close", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(cur?.name.orEmpty(), style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${pager.currentPage + 1} of ${items.size}" + if (cur != null && cur.key in done) " · backed up" else "",
                    style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
            }
            if (cur?.isVideo == true) Icon(Lucide.Play, null, tint = Color.White.copy(alpha = 0.6f))
        }
    }
}
