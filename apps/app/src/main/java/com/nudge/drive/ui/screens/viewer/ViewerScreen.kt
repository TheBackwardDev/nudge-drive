package com.nudge.drive.ui.screens.viewer

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size as CoilSize
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.FileWarning
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.Share2
import com.nudge.drive.AppContainer
import com.nudge.drive.R
import com.nudge.drive.data.ApiException
import com.nudge.drive.data.FileItem
import com.nudge.drive.data.Transfers
import com.nudge.drive.ui.components.GlassIconButton
import com.nudge.drive.ui.components.PrimaryButton
import com.nudge.drive.ui.components.ScreenBackground
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.components.softShadow
import com.composables.icons.lucide.SquarePen
import com.composables.icons.lucide.Radar
import com.nudge.drive.ui.nav.Routes
import com.nudge.drive.ui.screens.common.shareFileLink
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.util.ThumbSize
import com.nudge.drive.util.ViewerType
import com.nudge.drive.util.formatBytes
import com.nudge.drive.util.isMedia
import com.nudge.drive.util.kind
import com.nudge.drive.util.originalUrl
import com.nudge.drive.util.thumbUrl
import com.nudge.drive.util.viewerType
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** In-app viewer for every previewable file: photo/video gallery, documents, text and audio. */
@Composable
fun ViewerScreen(c: AppContainer, id: String, nav: NavController) {
    var file by remember(id) { mutableStateOf(c.fileCache[id]) }
    LaunchedEffect(id) {
        if (file == null) runCatching { c.repo.file(id) }
            .onSuccess { file = it; c.fileCache[id] = it }
            .onFailure { c.toast((it as? ApiException)?.friendly ?: "Couldn't open this file"); nav.popBackStack() }
    }
    val f = file ?: return Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Nd.Lavender)
    }
    val actions = rememberViewerActions(c, nav)
    when (f.viewerType()) {
        ViewerType.Image, ViewerType.Video -> MediaGallery(c, f, nav, actions)
        ViewerType.Document -> DocumentScreen(c, f, nav, actions)
        ViewerType.Text -> TextScreen(c, f, nav, actions)
        ViewerType.Audio -> AudioScreen(c, f, nav, actions)
        ViewerType.None -> LaunchedEffect(f.id) { nav.popBackStack(); nav.navigate(Routes.file(f.id)) }
    }
}

/** Top-bar actions shared by every viewer. */
private class ViewerActions(
    val details: (FileItem) -> Unit,
    val share: (FileItem) -> Unit,
    val download: (FileItem) -> Unit,
    val openWith: (FileItem) -> Unit,
)

@Composable
private fun rememberViewerActions(c: AppContainer, nav: NavController): ViewerActions {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(nav) {
        ViewerActions(
            details = { f -> nav.navigate(Routes.file(f.id)) },
            share = { f -> (ctx as? Activity)?.let { c.shareOut.share(it, listOf(f)) } },
            download = { f -> c.transfers.download(f); c.toast("Downloading ${f.name}") },
            openWith = { f ->
                scope.launch {
                    c.toast("Opening ${f.name}…")
                    runCatching { c.transfers.toCache(f) }
                        .onSuccess { out ->
                            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", out)
                            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, Transfers.mimeFor(f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            runCatching { ctx.startActivity(Intent.createChooser(intent, "Open with")) }.onFailure { c.toast("No app on this phone can open this file") }
                        }
                        .onFailure { c.toast((it as? ApiException)?.friendly ?: "Couldn't download the file") }
                }
            },
        )
    }
}

/* ------------------------------------------------------------------ system bars */

/** Light status-bar icons over the black gallery, and optional immersive mode while the controls are hidden. */
@Composable
private fun DarkSystemBars(hidden: Boolean = false) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    val controller = remember(window) { WindowCompat.getInsetsController(window, view) }
    DisposableEffect(controller) {
        val light = controller.isAppearanceLightStatusBars
        val lightNav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = light
            controller.isAppearanceLightNavigationBars = lightNav
        }
    }
    LaunchedEffect(hidden) {
        if (hidden) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

/* ------------------------------------------------------------------ photo & video gallery */

@OptIn(UnstableApi::class)
@Composable
private fun rememberGalleryPlayer(c: AppContainer): ExoPlayer {
    val ctx = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(c.api.http)))
            .build()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) player.pause() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); player.release() }
    }
    return player
}

@Composable
private fun MediaGallery(c: AppContainer, start: FileItem, nav: NavController, actions: ViewerActions) {
    val items = remember(start.id) {
        val list = c.viewerList.filter { it.isMedia }
        if (list.any { it.id == start.id }) list else listOf(start)
    }
    val pager = rememberPagerState(initialPage = items.indexOfFirst { it.id == start.id }.coerceAtLeast(0)) { items.size }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    val player = rememberGalleryPlayer(c)
    val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]
    val fade = (abs(drag) / 900f).coerceIn(0f, 0.75f)

    DarkSystemBars(hidden = !chrome)
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { zoomed = false } }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - fade))) {
        HorizontalPager(
            pager,
            modifier = Modifier.fillMaxSize()
                .offset { IntOffset(0, drag.roundToInt()) }
                .graphicsLayer { val s = 1f - fade * 0.25f; scaleX = s; scaleY = s }
                .draggable(
                    rememberDraggableState { d -> drag += d },
                    Orientation.Vertical,
                    enabled = !zoomed,
                    onDragStopped = { v ->
                        if (abs(drag) > 260f || abs(v) > 2600f) nav.popBackStack()
                        else animate(drag, 0f, animationSpec = spring(dampingRatio = 0.8f)) { x, _ -> drag = x }
                    },
                ),
            beyondViewportPageCount = 1,
            pageSpacing = 18.dp,
            userScrollEnabled = !zoomed,
            key = { items[it].id },
        ) { page ->
            val f = items[page]
            if (f.viewerType() == ViewerType.Video) {
                VideoPage(f, player, active = pager.settledPage == page, onControls = { chrome = it })
            } else {
                PhotoPage(
                    f, isCurrent = pager.currentPage == page,
                    onZoom = { z -> if (pager.currentPage == page) zoomed = z },
                    onTap = { chrome = !chrome },
                )
            }
        }

        AnimatedVisibility(
            chrome && abs(drag) < 40f, enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut() + slideOutVertically { -it / 2 },
        ) {
            TopBar(
                title = current.name,
                subtitle = if (items.size > 1) "${pager.currentPage + 1} of ${items.size}" else formatBytes(current.size),
                onBack = { nav.popBackStack() }, dark = true,
            ) {
                BarButton(Lucide.Info, "Details", true) { actions.details(current) }
                BarButton(Lucide.Share2, "Share", true) { actions.share(current) }
                BarButton(Lucide.Radar, "Send to a nearby phone", true) { com.nudge.drive.ui.screens.common.sendNearby(c, ctx, listOf(current)) }
                BarButton(Lucide.Download, "Download", true) { actions.download(current) }
            }
        }

        // Hidden on videos: the player's own seek bar lives at the bottom.
        if (items.size > 1) AnimatedVisibility(
            chrome && abs(drag) < 40f && !zoomed && current.viewerType() != ViewerType.Video, Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            FilmStrip(items, pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } }
        }
    }
}

@Composable
private fun PhotoPage(f: FileItem, isCurrent: Boolean, onZoom: (Boolean) -> Unit, onTap: () -> Unit) {
    val zoom = remember(f.id) { ZoomState() }
    val ctx = LocalPlatformContext.current
    // Which picture is on screen. Screen-size preview first (fast); the original once zoomed in;
    // if the original fails we fall back to the preview instead of going blank.
    var src by remember(f.id) { mutableStateOf(PhotoSrc.Screen) }
    var loading by remember(f.id) { mutableStateOf(true) }
    var retry by remember(f.id) { mutableIntStateOf(0) }

    LaunchedEffect(isCurrent) { if (!isCurrent) zoom.snapReset() }
    LaunchedEffect(zoom) { snapshotFlow { zoom.zoomed }.distinctUntilChanged().collect(onZoom) }
    LaunchedEffect(zoom) { snapshotFlow { zoom.scale > 1.6f }.filter { it }.first(); if (src == PhotoSrc.Screen) src = PhotoSrc.Original }

    val request = remember(f.id, src, retry) {
        val screen = thumbUrl(f.id, ThumbSize.Screen)
        val b = ImageRequest.Builder(ctx).crossfade(180)
        when (src) {
            PhotoSrc.Screen, PhotoSrc.OriginalFailed -> b.data(screen).placeholderMemoryCacheKey(thumbUrl(f.id, ThumbSize.Grid))
            PhotoSrc.Original, PhotoSrc.ScreenFailed -> b.data(originalUrl(f.id)).size(CoilSize(3072, 3072)).placeholderMemoryCacheKey(screen)
            PhotoSrc.AllFailed -> b.data(null)
        }
        b.build()
    }
    Box(
        Modifier.fillMaxSize().onSizeChanged { zoom.box = it }.zoomable(zoom, onTap),
        contentAlignment = Alignment.Center,
    ) {
        if (src != PhotoSrc.AllFailed) AsyncImage(
            request, f.name, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom.scale; scaleY = zoom.scale; translationX = zoom.offset.x; translationY = zoom.offset.y
            },
            onState = { s ->
                loading = s is AsyncImagePainter.State.Loading
                if (s is AsyncImagePainter.State.Success) zoom.content = s.painter.intrinsicSize.let { Size(it.width, it.height) }
                if (s is AsyncImagePainter.State.Error) src = when (src) {
                    PhotoSrc.Screen -> PhotoSrc.ScreenFailed       // no preview: try the original
                    PhotoSrc.Original -> PhotoSrc.OriginalFailed   // original failed: keep showing the preview
                    else -> PhotoSrc.AllFailed
                }
            },
        )
        if (src == PhotoSrc.AllFailed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Couldn't load this photo", style = MaterialTheme.typography.titleSmall, color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text("Try again", style = MaterialTheme.typography.labelLarge, color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.16f))
                    .bounceClick { src = PhotoSrc.Screen; retry++ }.padding(horizontal = 18.dp, vertical = 10.dp))
        } else if (loading) CircularProgressIndicator(Modifier.size(34.dp), color = Nd.Lavender, strokeWidth = 3.dp)
    }
}

private enum class PhotoSrc { Screen, Original, ScreenFailed, OriginalFailed, AllFailed }

@OptIn(UnstableApi::class)
@Composable
private fun VideoPage(f: FileItem, player: ExoPlayer, active: Boolean, onControls: (Boolean) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(thumbUrl(f.id, ThumbSize.Large), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        if (active) {
            DisposableEffect(f.id) {
                player.setMediaItem(MediaItem.fromUri(originalUrl(f.id)))
                player.prepare()
                player.playWhenReady = true
                onDispose { player.stop(); player.clearMediaItems() }
            }
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.viewer_player, null) as PlayerView).apply {
                        this.player = player
                        controllerShowTimeoutMs = 2800
                        setShowPreviousButton(false)
                        setShowNextButton(false)
                        setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v -> onControls(v == View.VISIBLE) })
                    }
                },
                update = { it.player = player },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            )
        } else {
            Box(Modifier.size(68.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Icon(Lucide.Play, "Play", tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun FilmStrip(items: List<FileItem>, current: Int, onPick: (Int) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(current) {
        val visible = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(5)
        state.animateScrollToItem((current - visible / 2).coerceAtLeast(0))
    }
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
            .navigationBarsPadding().padding(top = 18.dp, bottom = 14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(items, key = { _, f -> f.id }) { i, f ->
            val on = i == current
            val s by animateFloatAsState(if (on) 1f else 0.82f, tween(180), label = "strip")
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier.size(52.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = if (on) 1f else 0.65f }
                    .clip(shape).background(Color.White.copy(alpha = 0.1f))
                    .then(if (on) Modifier.border(2.dp, Color.White, shape) else Modifier)
                    .bounceClick { onPick(i) },
            ) {
                AsyncImage(thumbUrl(f.id, ThumbSize.Small), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (f.viewerType() == ViewerType.Video) Icon(Lucide.Play, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(16.dp))
            }
        }
    }
}

/* ------------------------------------------------------------------ documents, text, audio */

@Composable
private fun DocumentScreen(c: AppContainer, f: FileItem, nav: NavController, actions: ViewerActions) {
    val flavor = remember(f.id) { docFlavor(f) }
    val ctx = LocalContext.current
    // One state at a time: no stale error over a loaded document, no lost progress.
    var load by remember(f.id) { mutableStateOf<DocLoad>(DocLoad.Loading(0f)) }
    var version by remember(f.id) { mutableIntStateOf(0) }
    // Saved in the in-app editor: reload so the edits show.
    LaunchedEffect(f.id) { c.fileEdited.collect { if (it == f.id) version++ } }
    val usesPdf = flavor != DocFlavor.Sheet && flavor != DocFlavor.Word
    if (usesPdf) LaunchedEffect(f.id, version) {
        val keep = (load as? DocLoad.Ready)?.file
        if (keep == null) load = DocLoad.Loading(0f)
        load = try {
            DocLoad.Ready(c.transfers.documentPreview(f, fresh = version > 0) { p -> if (load is DocLoad.Loading) load = DocLoad.Loading(p) })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            if (keep != null) DocLoad.Ready(keep) else DocLoad.Failed((e as? ApiException)?.friendly ?: "Couldn't load a preview of this file.")
        }
    }
    val editable = canEditInApp(f)
    val openEditor = { c.fileCache[f.id] = f; nav.navigate(com.nudge.drive.ui.nav.Routes.edit(f.id)) }
    ReaderScaffold(f, nav, actions, edit = if (editable) openEditor else null, editLabel = "Edit") { top ->
        when (val st = load) {
            is DocLoad.Failed -> ReaderMessage(Lucide.FileWarning, "No preview", st.message) {
                PrimaryButton("Try again", icon = Lucide.RotateCw) { load = DocLoad.Loading(0f); version++ }
                Spacer(Modifier.height(10.dp))
                if (editable) {
                    PrimaryButton("Edit it instead", icon = Lucide.SquarePen, onClick = openEditor)
                    Spacer(Modifier.height(10.dp))
                }
                PrimaryButton("Open with another app", icon = Lucide.ExternalLink, container = Nd.GlassStrong, content = Nd.Ink) { actions.openWith(f) }
            }
            else -> when {
                flavor == DocFlavor.Sheet -> SheetViewer(c, f, top, version) { load = DocLoad.Failed(it) }
                flavor == DocFlavor.Word -> DocHtmlViewer(c, f, top, version) { load = DocLoad.Failed(it) }
                st is DocLoad.Ready -> androidx.compose.runtime.key(st.file.path, version) { PdfPages(c, st.file, top) }
                st is DocLoad.Loading -> Loading(
                    if (f.mimeType == "application/pdf") "Loading ${f.name}…" else "Preparing preview…",
                    if (f.mimeType == "application/pdf") null else "PowerPoint files are converted the first time you open them. Next time it's quicker.",
                    st.progress,
                )
            }
        }
    }
}

private sealed interface DocLoad {
    data class Loading(val progress: Float) : DocLoad
    data class Ready(val file: File) : DocLoad
    data class Failed(val message: String) : DocLoad
}

@Composable
private fun TextScreen(c: AppContainer, f: FileItem, nav: NavController, actions: ViewerActions) {
    var local by remember(f.id) { mutableStateOf<File?>(null) }
    var progress by remember(f.id) { mutableFloatStateOf(0f) }
    var error by remember(f.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(f.id) {
        if (f.size > 20L * 1024 * 1024) { error = "This file is too big to show here."; return@LaunchedEffect }
        runCatching { c.transfers.toCache(f) { progress = it } }.onSuccess { local = it }
            .onFailure { error = (it as? ApiException)?.friendly ?: "Couldn't load this file." }
    }
    ReaderScaffold(f, nav, actions) { top ->
        val out = local
        when {
            out != null -> TextLines(out, top)
            error != null -> ReaderMessage(Lucide.FileWarning, "Can't show this file", error!!) {
                PrimaryButton("Open with another app", icon = Lucide.ExternalLink) { actions.openWith(f) }
            }
            else -> Loading("Loading ${f.name}…", null, progress)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun AudioScreen(c: AppContainer, f: FileItem, nav: NavController, actions: ViewerActions) {
    val player = rememberGalleryPlayer(c)
    DisposableEffect(f.id) {
        player.setMediaItem(MediaItem.fromUri(originalUrl(f.id)))
        player.prepare(); player.playWhenReady = true
        onDispose { player.stop() }
    }
    ReaderScaffold(f, nav, actions) { top ->
        Column(Modifier.fillMaxSize().padding(top = top).navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(200.dp).clip(RoundedCornerShape(56.dp))
                    .background(Brush.linearGradient(listOf(Nd.Lavender, Nd.Primary))),
                contentAlignment = Alignment.Center,
            ) { Icon(Lucide.Music, null, tint = Color.White, modifier = Modifier.size(84.dp)) }
            Spacer(Modifier.height(24.dp))
            Text(f.name, style = MaterialTheme.typography.titleLarge, color = Nd.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(formatBytes(f.size), style = MaterialTheme.typography.bodyMedium, color = Nd.Muted)
            Spacer(Modifier.weight(1f))
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        controllerShowTimeoutMs = 0
                        setControllerHideOnTouch(false)
                        showController()
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        clipToOutline = true
                        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                        background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 28f * resources.displayMetrics.density; setColor(0xFF1E1440.toInt()) }
                    }
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
        }
    }
}

@Composable
private fun ReaderScaffold(
    f: FileItem, nav: NavController, actions: ViewerActions,
    edit: (() -> Unit)? = null, editLabel: String = "Edit",
    content: @Composable (top: Dp) -> Unit,
) {
    val top = androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 80.dp
    ScreenBackground {
        content(top)
        TopBar(
            title = f.name,
            subtitle = listOf(f.kind().label.removeSuffix("s"), formatBytes(f.size).takeIf { f.size > 0 }).filterNotNull().joinToString(" · "),
            onBack = { nav.popBackStack() }, dark = false,
        ) {
            BarButton(Lucide.Info, "Details", false) { actions.details(f) }
            BarButton(Lucide.ExternalLink, "Open with another app", false) { actions.openWith(f) }
            BarButton(Lucide.Download, "Download", false) { actions.download(f) }
        }
        if (edit != null) {
            val shape = RoundedCornerShape(28.dp)
            Row(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = if (docFlavor(f) == DocFlavor.Sheet) 64.dp else 22.dp)
                    .softShadow(shape, 18.dp, 0.3f).clip(shape).background(Nd.Primary).bounceClick(onClick = edit)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.SquarePen, null, tint = Nd.OnPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(editLabel, style = MaterialTheme.typography.labelLarge, color = Nd.OnPrimary)
            }
        }
    }
}

@Composable
private fun Loading(title: String, body: String?, progress: Float) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (progress in 0.01f..0.999f) CircularProgressIndicator({ progress }, color = Nd.Iris, trackColor = Nd.Mist, modifier = Modifier.size(54.dp))
        else CircularProgressIndicator(color = Nd.Iris, modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Nd.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (body != null) Text(
            body, style = MaterialTheme.typography.bodyMedium, color = Nd.Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ReaderMessage(icon: ImageVector, title: String, body: String, buttons: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(28.dp)).background(Nd.Mist), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Nd.Iris, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Nd.Ink)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 22.dp))
        buttons()
    }
}

/* ------------------------------------------------------------------ chrome */

@Composable
private fun TopBar(title: String, subtitle: String, onBack: () -> Unit, dark: Boolean, actions: @Composable () -> Unit) {
    val ink = if (dark) Color.White else Nd.Ink
    val muted = if (dark) Color.White.copy(alpha = 0.7f) else Nd.Muted
    Row(
        Modifier.fillMaxWidth()
            .background(
                if (dark) Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent))
                else Brush.verticalGradient(listOf(Nd.Haze, Nd.Haze.copy(alpha = 0.92f), Nd.Haze.copy(alpha = 0f))),
            )
            .statusBarsPadding().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(Lucide.ArrowLeft, "Back", dark, onBack)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { actions() }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, dark: Boolean, onClick: () -> Unit) {
    if (dark) Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp)) }
    else GlassIconButton(icon, label, size = 44.dp, onClick = onClick)
}
