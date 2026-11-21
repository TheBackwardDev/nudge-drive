package com.nudge.drive.desktop.ui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.input.pointer.isCtrlPressed as ptrCtrl
import androidx.compose.ui.input.pointer.isMetaPressed as ptrMeta
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowDownToLine
import com.composables.icons.lucide.ArrowUpDown
import com.composables.icons.lucide.CirclePlay
import com.composables.icons.lucide.FileArchive
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.FileType2
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.FolderInput
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.List
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Radar
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Upload
import com.composables.icons.lucide.X
import com.nudge.drive.data.FileItem
import com.nudge.drive.data.Folder
import com.nudge.drive.desktop.AppState
import com.nudge.drive.desktop.Crumb
import com.nudge.drive.desktop.Page
import com.nudge.drive.desktop.ui.Breadcrumbs
import com.nudge.drive.desktop.ui.ContextMenuBox
import com.nudge.drive.desktop.ui.FileActionDialogs
import com.nudge.drive.desktop.ui.MoreButton
import com.nudge.drive.desktop.ui.rememberContextMenu
import com.nudge.drive.desktop.ui.NewMenu
import com.nudge.drive.desktop.ui.PageHeader
import com.nudge.drive.desktop.ui.ToolButton
import com.nudge.drive.desktop.ui.rememberFileActions
import com.nudge.drive.ui.components.Chip
import com.nudge.drive.ui.components.EmptyState
import com.nudge.drive.ui.components.FileGridCell
import com.nudge.drive.ui.components.FileRow
import com.nudge.drive.ui.components.FolderCard
import com.nudge.drive.ui.components.PillField
import com.nudge.drive.ui.components.SecondaryButton
import com.nudge.drive.ui.components.Shimmer
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.util.FileKind
import com.nudge.drive.util.formatBytes
import com.nudge.drive.util.kindOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Sort(val label: String) { Name("Name"), Newest("Newest first"), Oldest("Oldest first"), Largest("Largest first") }

/** Load state of a page: never "empty" while loading or after an error. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

private data class Listing(
    val folders: List<Folder>,
    val files: List<FileItem>,
    /** Every folder (search results), to build the path of a matching folder. */
    val allFolders: List<Folder> = emptyList(),
    /** Files per kind across all drives (My files top level only), for the category tiles. */
    val counts: Map<FileKind, Int> = emptyMap(),
)

/** Filter chips, as on the phone. */
private val kindChips = listOf(FileKind.Image, FileKind.Video, FileKind.Pdf, FileKind.Doc, FileKind.Archive)

private data class Category(val label: String, val icon: ImageVector, val color: Color, val kind: FileKind? = null, val page: Page? = null)

private val categories = listOf(
    Category("Images", Lucide.Image, Nd.Image, FileKind.Image),
    Category("Videos", Lucide.CirclePlay, Color(0xFF7A5CF0), FileKind.Video),
    Category("PDFs", Lucide.FileType2, Nd.Pdf, FileKind.Pdf),
    Category("Docs", Lucide.FileText, Nd.Doc, FileKind.Doc),
    Category("Zips", Lucide.FileArchive, Nd.Zip, FileKind.Archive),
    Category("Shared", Lucide.Share2, Color(0xFF1E8F50), page = Page.Shared),
    Category("Activity", Lucide.History, Nd.Audio, page = Page.Activity),
    Category("Trash", Lucide.Trash2, Color(0xFFD9534F), page = Page.Trash),
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FilesPage(app: AppState, page: Page.Files, search: String? = null, recent: Boolean = false) {
    val settings by app.settings.state.collectAsState()
    val changes by app.changes.collectAsState()
    val actions = rememberFileActions(app)
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Load<Listing>>(Load.Loading) }
    var sort by remember { mutableStateOf(Sort.Name) }
    var query by remember(search) { mutableStateOf(search.orEmpty()) }
    val selected = remember(page, search, recent) { mutableStateListOf<String>() }
    val focus = remember { FocusRequester() }
    val windowInfo = LocalWindowInfo.current
    var reload by remember { mutableStateOf(0) }
    // File-type filter (All / Images / ...). Like the phone, a filter searches every drive, not just this folder.
    var kind by remember(page.folderId, search == null, recent) { mutableStateOf<FileKind?>(null) }
    val filtering = search != null || kind != null

    LaunchedEffect(page.folderId, search, recent, changes, reload, kind) {
        if (state !is Load.Ready) state = Load.Loading
        state = runCatching {
            when {
                filtering -> coroutineScope {
                    val q = search?.trim().orEmpty()
                    val files = async { app.repo.files(q = q, kind = kind?.apiKind) }
                    // Folders whose name matches show above the files ("Matching folders"), as on the phone.
                    val all = if (q.isBlank()) emptyList() else runCatching { app.repo.allFolders() }.getOrDefault(emptyList())
                    Listing(all.filter { it.name.contains(q, ignoreCase = true) }, files.await(), all)
                }
                recent -> Listing(emptyList(), app.repo.files().sortedByDescending { it.updatedAt }.take(200))
                else -> coroutineScope {
                    val f = async { app.repo.folders(page.folderId) }
                    val files = async { if (page.folderId == null) app.repo.files() else app.repo.files(folderId = page.folderId) }
                    val fetched = files.await()
                    if (page.folderId == null) Listing(f.await(), fetched.filter { it.folderId == null }, counts = fetched.groupingBy { kindOf(it.mimeType, it.name) }.eachCount())
                    else Listing(f.await(), fetched)
                }
            }
        }.fold({ Load.Ready(it) }, { Load.Failed((it as? com.nudge.drive.data.ApiException)?.friendly ?: "Couldn't load your files. Check your connection.") })
    }
    // Search as you type (debounced).
    LaunchedEffect(query) {
        if (search != null && query != search && query.isNotBlank()) { delay(350); app.go(Page.Search(query.trim())) }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val listing = (state as? Load.Ready)?.data
    val files = remember(listing, sort) {
        listing?.files.orEmpty().let { l ->
            when (sort) {
                Sort.Name -> l.sortedBy { it.name.lowercase() }
                Sort.Newest -> l.sortedByDescending { it.createdAt }
                Sort.Oldest -> l.sortedBy { it.createdAt }
                Sort.Largest -> l.sortedByDescending { it.size }
            }
        }
    }
    val folders = remember(listing, sort) { listing?.folders.orEmpty().let { if (sort == Sort.Name) it.sortedBy { f -> f.name.lowercase() } else it } }
    val selection = files.filter { it.id in selected }

    fun clickFile(f: FileItem) {
        val mods = windowInfo.keyboardModifiers
        if (mods.ptrCtrl || mods.ptrMeta || selected.isNotEmpty()) { if (f.id in selected) selected.remove(f.id) else selected.add(f.id) }
        else app.openFile(f, files)
    }
    fun openFolder(f: Folder) {
        val trail = when {
            filtering -> pathOf(f, listing?.allFolders.orEmpty())
            recent -> listOf(Crumb(f.id, f.name))
            else -> page.trail + Crumb(f.id, f.name)
        }
        app.openFolder(f.id, trail)
    }

    Column(
        Modifier.fillMaxSize().focusRequester(focus).focusable().onKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
            when {
                e.key == Key.Escape && selected.isNotEmpty() -> { selected.clear(); true }
                e.key == Key.Escape && kind != null -> { kind = null; true }
                e.key == Key.Delete && selection.isNotEmpty() -> { actions.delete = selection; true }
                e.key == Key.A && (e.isCtrlPressed || e.isMetaPressed) -> { selected.clear(); selected.addAll(files.map { it.id }); true }
                e.key == Key.F5 -> { reload++; true }
                else -> false
            }
        },
    ) {
        val title = when {
            search != null -> "Search"
            recent -> "Recent"
            else -> page.trail.lastOrNull()?.name ?: "My files"
        }
        PageHeader(title, sub = listing?.let { "${plural(it.folders.size, "folder")} · ${plural(it.files.size, "file")}" }) {
            if (search != null) PillField(query, { query = it }, "Search your drives", Modifier.width(300.dp), leading = Lucide.Search)
            else ToolButton(Lucide.Search, null) { app.go(Page.Search("")) }
            SortButton(sort) { sort = it }
            ToolButton(if (settings.grid) Lucide.List else Lucide.LayoutGrid, null) { app.settings.update { it.copy(grid = !it.grid) } }
            ToolButton(Lucide.RefreshCw, null) { reload++ }
            if (search == null && !recent) {
                var newOpen by remember { mutableStateOf(false) }
                Box {
                    ToolButton(Lucide.Plus, "New") { newOpen = true }
                    NewMenu(app, newOpen) { newOpen = false }
                }
                ToolButton(Lucide.Upload, "Upload", primary = true) { app.pickAndUpload(page.folderId) }
            }
        }
        if (search == null && !recent && kind == null) Breadcrumbs(app, page.trail)
        if (!recent) Row(Modifier.padding(start = 28.dp, end = 28.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("All", kind == null) { if (kind != null) { kind = null; selected.clear() } }
            kindChips.forEach { k -> Chip(k.label, kind == k, icon = k.icon) { kind = if (kind == k) null else k; selected.clear() } }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val s = state) {
                Load.Loading -> LoadingGrid()
                is Load.Failed -> EmptyState(Lucide.RefreshCw, "Couldn't load", s.message, Modifier.align(Alignment.Center)) {
                    SecondaryButton("Try again") { reload++ }
                }
                is Load.Ready -> if (folders.isEmpty() && files.isEmpty()) {
                    val (t, b) = when {
                        search != null && search.isBlank() && kind == null -> "Search your drives" to "Type a name above to find files and folders across every drive."
                        filtering -> "Nothing found" to "Try another name or remove a filter."
                        else -> "This folder is empty" to "Drop files here or use Upload to add some."
                    }
                    EmptyState(if (filtering) Lucide.Search else Lucide.FolderOpen, t, b, Modifier.align(Alignment.Center))
                } else {
                    val gridState = rememberLazyGridState()
                    LazyVerticalGrid(
                        if (settings.grid) GridCells.Adaptive(168.dp) else GridCells.Fixed(1), state = gridState,
                        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 6.dp, bottom = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(if (settings.grid) 14.dp else 4.dp),
                    ) {
                        if (!filtering && !recent && page.folderId == null) item(span = { GridItemSpan(maxLineSpan) }) {
                            CategoryGrid(listing?.counts.orEmpty()) { cat -> if (cat.kind != null) { kind = cat.kind; selected.clear() } else cat.page?.let(app::go) }
                        }
                        if (folders.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(if (filtering) "Matching folders" else "Folders") }
                            items(folders, key = { "d" + it.id }, span = { if (settings.grid) GridItemSpan(1) else GridItemSpan(maxLineSpan) }) { f ->
                                ContextMenuBox({ actions.folderMenu(f) { openFolder(f) } }) {
                                    FolderCard(f, onClick = { openFolder(f) })
                                }
                            }
                        }
                        if (files.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(if (filtering) "Results" else "Files") }
                            items(files, key = { it.id }) { f ->
                                val menu = rememberContextMenu()
                                ContextMenuBox({ actions.menu(f, files) }, state = menu) {
                                    if (settings.grid) Box {
                                        FileGridCell(f, f.id in selected, selected.isNotEmpty(), onClick = { clickFile(f) }, onLongClick = { selected.add(f.id) })
                                        if (selected.isEmpty()) MoreButton(menu, Modifier.align(Alignment.TopEnd).padding(12.dp), size = 30, background = Nd.Surface.copy(alpha = 0.88f))
                                    }
                                    else FileRow(
                                        f, selected = f.id in selected, selectionMode = selected.isNotEmpty(),
                                        subtitle = if (filtering) f.folder?.name?.let { "${formatBytes(f.size)} · in $it" } else null,
                                        onClick = { clickFile(f) }, onLongClick = { selected.add(f.id) },
                                        onMore = { menu.show() },
                                    )
                                }
                            }
                        }
                    }
                    com.nudge.drive.ui.components.ScrollToTopButton(gridState.firstVisibleItemIndex > 8, Modifier.align(Alignment.BottomEnd).padding(28.dp)) {
                        scope.launch { gridState.animateScrollToItem(0) }
                    }
                }
            }
            SelectionBar(app, selection, onClear = { selected.clear() }, actions = actions, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
    FileActionDialogs(app, actions)
}

/** Path of [f] from the top, using every folder's parent. */
private fun pathOf(f: Folder, all: List<Folder>): List<Crumb> {
    val byId = all.associateBy { it.id }
    val out = ArrayList<Crumb>()
    var cur: Folder? = f
    while (cur != null && out.size < 30) { out.add(0, Crumb(cur.id, cur.name)); cur = cur.parentId?.let(byId::get) }
    return out
}

/** Category tiles with item counts at the top of My files, as on the phone. */
@Composable
private fun CategoryGrid(counts: Map<FileKind, Int>, onPick: (Category) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        val perRow = if (maxWidth < 760.dp) 4 else 8
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            categories.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { cat ->
                        val shape = RoundedCornerShape(20.dp)
                        Column(
                            Modifier.weight(1f).height(84.dp).clip(shape).background(Nd.Surface).border(1.dp, Nd.CardBorder, shape).bounceClick { onPick(cat) },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(cat.icon, null, tint = cat.color, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(cat.label, style = MaterialTheme.typography.labelMedium, color = Nd.Ink)
                            val n = cat.kind?.let { counts[it] }
                            if (n != null) Text(if (n == 1) "1 item" else "$n items", style = MaterialTheme.typography.labelSmall, color = Nd.Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Nd.Muted, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp))
}

@Composable
private fun SortButton(sort: Sort, onSort: (Sort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolButton(Lucide.ArrowUpDown, sort.label) { open = true }
        DropdownMenu(open, { open = false }, modifier = Modifier.background(Nd.Surface)) {
            Sort.entries.forEach { s ->
                DropdownMenuItem({ Text(s.label, color = if (s == sort) Nd.IrisDeep else Nd.Ink) }, onClick = { open = false; onSort(s) })
            }
        }
    }
}

@Composable
fun LoadingGrid() {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(3) { Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { repeat(5) { Shimmer(Modifier.weight(1f).height(150.dp), corner = 20.dp) } } }
    }
}

@Composable
private fun SelectionBar(app: AppState, selection: List<FileItem>, onClear: () -> Unit, actions: com.nudge.drive.desktop.ui.FileActionsState, modifier: Modifier) {
    AnimatedVisibility(selection.isNotEmpty(), modifier.padding(bottom = 22.dp), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        Row(
            Modifier.clip(RoundedCornerShape(20.dp)).background(Nd.Inverse).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Lucide.X, "Clear selection", tint = Nd.OnInverse, modifier = Modifier.size(20.dp).bounceClick(onClick = onClear))
            Text("${selection.size} selected", color = Nd.OnInverse, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp))
            BarAction(Lucide.ArrowDownToLine, "Download") {
                if (selection.size > 1) app.transfers.downloadZip(selection.map { it.id }) else app.transfers.download(selection)
                app.showTransfers = true; onClear()
            }
            if (selection.size == 1) BarAction(Lucide.Link, "Link") { actions.share(selection[0]); onClear() }
            BarAction(Lucide.Radar, "Send to phone") { app.sendToPhone(selection); onClear() }
            BarAction(Lucide.FolderInput, "Move") { actions.move = selection }
            BarAction(Lucide.Trash2, "Delete") { actions.delete = selection }
        }
    }
}

@Composable
private fun BarAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(12.dp)).bounceClick(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Nd.OnInverse, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Nd.OnInverse, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun CenteredSpinner() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(30.dp), color = Nd.Iris, strokeWidth = 3.dp) }
}

fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"
