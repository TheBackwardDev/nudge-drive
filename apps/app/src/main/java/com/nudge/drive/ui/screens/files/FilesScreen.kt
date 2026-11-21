package com.nudge.drive.ui.screens.files

import com.composables.icons.lucide.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.nudge.drive.ui.components.ScrollToTopButton
import com.nudge.drive.ui.components.TimelineScroller
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nudge.drive.AppContainer
import com.nudge.drive.data.FileItem
import com.nudge.drive.ui.components.Chip
import com.nudge.drive.ui.components.EmptyState
import com.nudge.drive.ui.components.FileGridCell
import com.nudge.drive.ui.components.FileRow
import com.nudge.drive.ui.components.FolderCard
import com.nudge.drive.ui.components.GlassIconButton
import com.nudge.drive.ui.components.PillField
import com.nudge.drive.ui.components.SectionHeader
import com.nudge.drive.ui.components.Shimmer
import com.nudge.drive.ui.components.TextInputDialog
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.components.softShadow
import com.nudge.drive.ui.nav.Routes
import com.nudge.drive.ui.nav.shared
import com.nudge.drive.ui.screens.common.ColorPicker
import com.nudge.drive.ui.screens.common.ItemDialogs
import com.nudge.drive.ui.screens.common.ItemDialogsHost
import com.nudge.drive.ui.screens.main.MainActions
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.util.FileKind
import com.nudge.drive.util.FolderPalette
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

private data class Category(val label: String, val icon: ImageVector, val color: Color, val kind: FileKind? = null, val route: String? = null)

private val categories = listOf(
    Category("Images", Lucide.Image, Nd.Image, FileKind.Image),
    Category("Videos", Lucide.CirclePlay, Color(0xFF7A5CF0), FileKind.Video),
    Category("PDFs", Lucide.FileType2, Nd.Pdf, FileKind.Pdf),
    Category("Docs", Lucide.FileText, Nd.Doc, FileKind.Doc),
    Category("Zips", Lucide.FileArchive, Nd.Zip, FileKind.Archive),
    Category("Shared", Lucide.Share2, Color(0xFF1E8F50), route = Routes.Shared),
    Category("Activity", Lucide.History, Nd.Audio, route = Routes.Activity),
    Category("Trash", Lucide.Trash2, Color(0xFFD9534F), route = Routes.Trash),
)

@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(c: AppContainer, vm: FilesViewModel, actions: MainActions) {
    val d = remember { ItemDialogs() }
    var newFolder by remember { mutableStateOf(false) }
    var newFolderColor by remember { mutableStateOf(FolderPalette.first()) }

    BackHandler(enabled = vm.selectionMode || vm.searching || vm.path.isNotEmpty()) { vm.up() }
    LaunchedEffect(Unit) { snapshotFlow { vm.query }.drop(1).debounce(350).collect { vm.load(quiet = true) } }

    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    // Photos & videos get a Google Photos-style timeline: month headers, 3 per row, date scrubber.
    val timeline = (vm.kind == FileKind.Image || vm.kind == FileKind.Video) && !vm.loading
    val tl = remember(vm.files, timeline, vm.sort) { if (timeline) buildTimeline(vm.files, oldestFirst = vm.sort == SortMode.Oldest) else Timeline.Empty }
    val timelineStart = remember { intArrayOf(0) }
    val showTop by remember { derivedStateOf { gridState.firstVisibleItemIndex > 10 } }

    PullToRefreshBox(vm.refreshing, onRefresh = { vm.load(pull = true) }, modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            GridCells.Fixed(6),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = if (timeline) 12.dp else 20.dp, end = if (timeline) 12.dp else 20.dp, bottom = 150.dp),
            horizontalArrangement = Arrangement.spacedBy(if (timeline) 4.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (timeline) 4.dp else 10.dp),
        ) {
            emitted = 0
            full("header") {
                AnimatedContent(
                    vm.selectionMode,
                    transitionSpec = { (slideInVertically { -it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                    label = "bar",
                ) { sel ->
                    if (sel) SelectionBar(vm) else FilesHeader(vm, onNewFolder = { newFolder = true }, onUpload = { actions.upload(vm.current?.id) })
                }
            }
            full("search") {
                PillField(
                    vm.query, { vm.query = it }, if (vm.current != null) "Search all drives…" else "Search files, folders…",
                    leading = Lucide.Search, glass = true,
                    trailing = {
                        if (vm.searching) Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).bounceClick { vm.clearSearch() }, contentAlignment = Alignment.Center) {
                            Icon(Lucide.X, "Clear search", tint = Nd.Muted)
                        }
                    },
                )
            }
            full("chips") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                    item { Chip("All", vm.kind == null) { if (vm.kind != null) vm.setKindFilter(vm.kind) } }
                    items(listOf(FileKind.Image, FileKind.Video, FileKind.Pdf, FileKind.Doc, FileKind.Archive)) { k ->
                        Chip(k.label, vm.kind == k, icon = k.icon) { vm.setKindFilter(k) }
                    }
                }
            }
            if (vm.path.isNotEmpty() && !vm.searching) full("crumbs") { Breadcrumbs(vm) }

            if (vm.current == null && !vm.searching) {
                full("cats") { CategoryGrid(vm) { cat -> if (cat.kind != null) vm.setKindFilter(cat.kind) else cat.route?.let(actions.navigate) } }
            }

            if (vm.folders.isNotEmpty() || (!vm.searching && !vm.loading)) {
                full("folders-h") {
                    SectionHeader(if (vm.searching) "Matching folders" else "Folders", Modifier.padding(top = 8.dp),
                        action = if (vm.searching) null else "New", onAction = { newFolder = true })
                }
                full("folders") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp)) {
                        items(vm.folders, key = { it.id }) { f ->
                            FolderCard(f, Modifier.animateItem(), onClick = { vm.open(f) }, onLongClick = { d.folderOptions = f })
                        }
                        if (!vm.searching) item("new") { NewFolderCard { newFolder = true } }
                    }
                }
            }

            full("files-h") {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (vm.searching) "Results" else if (vm.current == null) "Files in My Files" else "Files",
                        style = MaterialTheme.typography.titleLarge, color = Nd.Ink, modifier = Modifier.weight(1f),
                    )
                    if (!vm.loading) Text("${vm.files.size}", style = MaterialTheme.typography.labelMedium, color = Nd.Muted)
                }
            }

            timelineStart[0] = emitted
            when {
                vm.loading -> items(6, span = { GridItemSpan(if (vm.grid) 3 else 6) }) {
                    Shimmer(Modifier.fillMaxWidth().height(if (vm.grid) 170.dp else 66.dp), corner = 20.dp)
                }
                vm.files.isEmpty() && vm.error != null -> full("error") {
                    EmptyState(Lucide.CloudOff, "Couldn't load files", vm.error ?: "") {
                        com.nudge.drive.ui.components.PrimaryButton("Try again", icon = Lucide.RotateCw) { vm.load() }
                    }
                }
                vm.files.isEmpty() -> full("empty") {
                    if (vm.searching) EmptyState(Lucide.SearchX, "Nothing found", "Try another name or remove a filter.")
                    else EmptyState(Lucide.FolderOpen, "No files here yet", "Upload photos, videos or documents. They go to whichever drive has the most room.") {
                        com.nudge.drive.ui.components.PrimaryButton("Upload files", icon = Lucide.Plus) { actions.upload(vm.current?.id) }
                    }
                }
                timeline -> items(tl.entries, key = { it.key }, span = { if (it is TimelineEntry.Month) GridItemSpan(maxLineSpan) else GridItemSpan(2) }) { e ->
                    when (e) {
                        is TimelineEntry.Month -> MonthHeader(e)
                        is TimelineEntry.Photo -> TimelineCell(
                            e.file, e.file.id in vm.selected, vm.selectionMode, Modifier.animateItem(),
                            onClick = { if (vm.selectionMode) vm.toggle(e.file.id) else actions.openFile(e.file, tl.files) },
                            onLongClick = { vm.toggle(e.file.id) },
                        )
                    }
                }
                vm.grid -> items(vm.files, key = { it.id }, span = { GridItemSpan(3) }) { f ->
                    FileGridCell(
                        f, f.id in vm.selected, vm.selectionMode,
                        onClick = { if (vm.selectionMode) vm.toggle(f.id) else actions.openFile(f, vm.files) },
                        onLongClick = { vm.toggle(f.id) },
                        modifier = Modifier.animateItem(),
                        thumbModifier = Modifier.shared("thumb-${f.id}"),
                    )
                }
                else -> items(vm.files, key = { it.id }, span = { GridItemSpan(6) }) { f ->
                    FileRow(
                        f, Modifier.animateItem(),
                        selected = f.id in vm.selected, selectionMode = vm.selectionMode,
                        thumbModifier = Modifier.shared("thumb-${f.id}"),
                        subtitle = f.folder?.takeIf { vm.searching }?.let { "${com.nudge.drive.util.formatBytes(f.size)} · in ${it.name}" },
                        onClick = { if (vm.selectionMode) vm.toggle(f.id) else actions.openFile(f, vm.files) },
                        onLongClick = { vm.toggle(f.id) },
                        onMore = { d.fileOptions = f },
                    )
                }
            }
        }
        if (timeline) TimelineScroller(
            gridState, firstIndex = { timelineStart[0] }, count = tl.entries.size,
            labelAt = { i -> tl.labels.getOrElse(i) { "" } }, marks = tl.yearMarks,
            modifier = Modifier.align(Alignment.TopEnd), topInset = 130.dp, bottomInset = 200.dp,
        )
        ScrollToTopButton(showTop, Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 118.dp)) {
            scope.launch { if (gridState.firstVisibleItemIndex > 60) gridState.scrollToItem(30); gridState.animateScrollToItem(0) }
        }
    }

    ItemDialogsHost(
        c, d, vm.allFolders, onNeedFolders = { vm.ensureAllFolders() },
        onOpenFile = actions.openDetails, onRenameFile = vm::renameFile, onMoveFile = vm::moveFile, onTrashFile = vm::trashFile,
        onDownload = vm::download, onOpenFolder = vm::open, onRenameFolder = vm::renameFolder, onMoveFolder = vm::moveFolder,
        onRecolorFolder = vm::recolorFolder, onDeleteFolder = vm::deleteFolder,
    )
    if (newFolder) {
        TextInputDialog(
            "New folder", placeholder = "Folder name", confirm = "Create",
            extra = { Spacer(Modifier.height(16.dp)); ColorPicker(newFolderColor) { newFolderColor = it } },
            onDismiss = { newFolder = false },
        ) { vm.createFolder(it, newFolderColor); newFolder = false }
    }
    MoveSelectedHost(vm)
}

/** Items emitted so far in the current grid pass, so the date scrubber knows where the timeline starts. */
private var emitted = 0

private fun LazyGridScope.full(key: String, content: @Composable () -> Unit) {
    emitted++
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun FilesHeader(vm: FilesViewModel, onNewFolder: () -> Unit, onUpload: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (vm.current != null) {
            GlassIconButton(Lucide.ArrowLeft, "Back") { vm.up() }
            Spacer(Modifier.width(12.dp))
        }
        AnimatedContent(vm.current?.name ?: "Files", label = "title", modifier = Modifier.weight(1f),
            transitionSpec = { (fadeIn(tween(220)) + slideInVertically { it / 3 }) togetherWith fadeOut(tween(120)) }) { t ->
            Text(t, style = MaterialTheme.typography.headlineMedium, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        GlassIconButton(if (vm.grid) Lucide.LayoutList else Lucide.LayoutGrid, if (vm.grid) "List view" else "Grid view") { vm.grid = !vm.grid }
        Spacer(Modifier.width(8.dp))
        Box {
            GlassIconButton(Lucide.ArrowUpDown, "Sort and more") { menu = true }
            DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(20.dp), containerColor = Nd.Surface) {
                SortMode.entries.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m.label, style = MaterialTheme.typography.bodyMedium, color = if (vm.sort == m) Nd.IrisDeep else Nd.Ink) },
                        onClick = { vm.sort = m; menu = false },
                    )
                }
                DropdownMenuItem(text = { Text("New folder") }, leadingIcon = { Icon(Lucide.FolderPlus, null) }, onClick = { menu = false; onNewFolder() })
                DropdownMenuItem(text = { Text("Sync from Google Drive") }, leadingIcon = { Icon(Lucide.RefreshCw, null) }, onClick = { menu = false; vm.sync() })
            }
        }
        Spacer(Modifier.width(8.dp))
        GlassIconButton(Lucide.Plus, "Upload", container = Nd.Primary, onClick = onUpload)
    }
}

@Composable
private fun SelectionBar(vm: FilesViewModel) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp, bottom = 6.dp).softShadow(shape, 14.dp, 0.16f).clip(shape)
            .background(Nd.Inverse).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelIcon(Lucide.X, "Cancel") { vm.clearSelection() }
        AnimatedContent(vm.selected.size, label = "count", modifier = Modifier.weight(1f)) { n ->
            Text("$n selected", style = MaterialTheme.typography.titleMedium, color = Nd.OnInverse, modifier = Modifier.padding(start = 4.dp))
        }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var more by remember { mutableStateOf(false) }
        SelIcon(Lucide.ListChecks, "Select all") { vm.selectAll() }
        SelIcon(Lucide.Share2, "Share") { (ctx as? android.app.Activity)?.let { vm.shareSelected(it) } }
        SelIcon(Lucide.FolderInput, "Move") { vm.ensureAllFolders(); vm.moveTarget = Unit }
        SelIcon(Lucide.Trash2, "Move to trash") { vm.trashSelected() }
        Box {
            SelIcon(Lucide.EllipsisVertical, "More") { more = true }
            DropdownMenu(more, { more = false }, shape = RoundedCornerShape(20.dp), containerColor = Nd.Surface) {
                DropdownMenuItem(text = { Text("Send to a nearby phone") }, leadingIcon = { Icon(Lucide.Radar, null) }, onClick = { more = false; vm.sendNearbySelected(ctx) })
                DropdownMenuItem(text = { Text("Download as zip") }, leadingIcon = { Icon(Lucide.Download, null) }, onClick = { more = false; vm.downloadSelected() })
            }
        }
    }
}

@Composable
private fun SelIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).bounceClick(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Nd.OnInverse)
    }
}

@Composable
private fun MoveSelectedHost(vm: FilesViewModel) {
    if (vm.moveTarget != null) {
        com.nudge.drive.ui.screens.common.FolderPickerSheet(
            "Move ${vm.selected.size} file${if (vm.selected.size == 1) "" else "s"}", vm.allFolders, currentId = vm.current?.id,
            onDismiss = { vm.moveTarget = null },
        ) { vm.moveSelected(it); vm.moveTarget = null }
    }
}

@Composable
private fun Breadcrumbs(vm: FilesViewModel) {
    LazyRow(verticalAlignment = Alignment.CenterVertically, contentPadding = PaddingValues(vertical = 2.dp)) {
        item {
            Text("My Files", style = MaterialTheme.typography.labelMedium, color = Nd.IrisDeep,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).bounceClick { vm.jumpTo(-1) }.padding(6.dp))
        }
        items(vm.path.size) { i ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.ChevronRight, null, tint = Nd.Subtle, modifier = Modifier.size(16.dp))
                val last = i == vm.path.lastIndex
                Text(vm.path[i].name, style = MaterialTheme.typography.labelMedium, color = if (last) Nd.Ink else Nd.IrisDeep,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).bounceClick(enabled = !last) { vm.jumpTo(i) }.padding(6.dp))
            }
        }
    }
}

@Composable
private fun CategoryGrid(vm: FilesViewModel, onPick: (Category) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
        categories.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { cat ->
                    val shape = RoundedCornerShape(22.dp)
                    Column(
                        Modifier.weight(1f).height(84.dp).softShadow(shape, 10.dp, 0.07f).clip(shape)
                            .background(Nd.Glass).border(1.dp, Nd.GlassBorder, shape).bounceClick { onPick(cat) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(cat.icon, null, tint = cat.color, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(cat.label, style = MaterialTheme.typography.labelMedium, color = Nd.Ink)
                        val n = cat.kind?.let { vm.kindCounts[it] }
                        if (n != null) Text(if (n == 1) "1 item" else "$n items", style = MaterialTheme.typography.labelSmall, color = Nd.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun NewFolderCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.padding(top = 12.dp).size(96.dp, 106.dp).clip(shape).background(Nd.GlassButton)
            .border(1.5.dp, Nd.Lavender, shape).bounceClick(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(Lucide.FolderPlus, null, tint = Nd.IrisDeep)
        Spacer(Modifier.height(6.dp))
        Text("New", style = MaterialTheme.typography.labelMedium, color = Nd.IrisDeep)
    }
}
