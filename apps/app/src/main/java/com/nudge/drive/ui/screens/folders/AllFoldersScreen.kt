package com.nudge.drive.ui.screens.folders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.FolderPlus
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import com.nudge.drive.AppContainer
import com.nudge.drive.data.ApiException
import com.nudge.drive.data.Folder
import com.nudge.drive.ui.components.Chip
import com.nudge.drive.ui.components.EmptyState
import com.nudge.drive.ui.components.GlassIconButton
import com.nudge.drive.ui.components.PillField
import com.nudge.drive.ui.components.ScrollToTopButton
import com.nudge.drive.ui.components.Shimmer
import com.nudge.drive.ui.components.TextInputDialog
import com.nudge.drive.ui.components.pressScale
import com.nudge.drive.ui.components.softShadow
import com.nudge.drive.ui.screens.common.ColorPicker
import com.nudge.drive.ui.screens.common.SubTopBar
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.ui.theme.Sora
import com.nudge.drive.util.FolderPalette
import com.nudge.drive.util.folderColor
import com.nudge.drive.util.friendlyDate
import kotlinx.coroutines.launch

private enum class FolderSort(val label: String) { NameAZ("Name A–Z"), NameZA("Name Z–A"), Newest("Newest"), Oldest("Oldest"), Updated("Recently changed") }

/** Every folder in the pool (nested ones too), sortable and searchable. Tapping one opens it in Files. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AllFoldersScreen(c: AppContainer, nav: NavController) {
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf<List<Folder>?>(null) }
    var sort by rememberSaveable { mutableStateOf(FolderSort.NameAZ) }
    var query by rememberSaveable { mutableStateOf("") }
    var newFolder by remember { mutableStateOf(false) }
    var newColor by remember { mutableStateOf(FolderPalette.first()) }
    var reload by remember { mutableStateOf(0) }
    val grid = rememberLazyGridState()

    LaunchedEffect(reload) {
        runCatching { c.repo.allFolders() }
            .onSuccess { all = it }
            .onFailure { all = all ?: emptyList(); c.toast((it as? ApiException)?.friendly ?: "Couldn't load folders") }
    }
    val byId = remember(all) { all.orEmpty().associateBy { it.id } }
    fun trail(f: Folder): List<Folder> {
        val out = ArrayList<Folder>(); var cur: Folder? = f; var guard = 0
        while (cur != null && guard++ < 50) { out.add(0, cur); cur = cur.parentId?.let(byId::get) }
        return out
    }
    val shown = remember(all, sort, query) {
        val list = all.orEmpty().filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        when (sort) {
            FolderSort.NameAZ -> list.sortedBy { it.name.lowercase() }
            FolderSort.NameZA -> list.sortedByDescending { it.name.lowercase() }
            FolderSort.Newest -> list.sortedByDescending { it.createdAt }
            FolderSort.Oldest -> list.sortedBy { it.createdAt }
            FolderSort.Updated -> list.sortedByDescending { it.updatedAt.ifBlank { it.createdAt } }
        }
    }
    val showTop by remember { derivedStateOf { grid.firstVisibleItemIndex > 6 } }

    com.nudge.drive.ui.components.ScreenBackground {
      Column(Modifier.fillMaxSize()) {
        SubTopBar("All folders", onBack = { nav.popBackStack() }) {
            GlassIconButton(Lucide.FolderPlus, "New folder") { newFolder = true }
        }
        LazyVerticalGrid(
            GridCells.Fixed(2), state = grid, modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "top") {
                Column {
                    Text(
                        all?.let { "${it.size} folder${if (it.size == 1) "" else "s"} across your drives" } ?: "Loading…",
                        style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.padding(bottom = 10.dp),
                    )
                    PillField(query, { query = it }, "Search folders", leading = Lucide.Search, glass = true, trailing = {
                        if (query.isNotEmpty()) Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).pressScale(remember { MutableInteractionSource() })
                            .combinedClickable(onClick = { query = "" }), contentAlignment = Alignment.Center) { Icon(Lucide.X, "Clear", tint = Nd.Muted) }
                    })
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                        items(FolderSort.entries) { s -> Chip(s.label, sort == s) { sort = s } }
                    }
                }
            }
            when {
                all == null -> items(6) { Shimmer(Modifier.fillMaxWidth().height(118.dp), corner = 22.dp) }
                shown.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    if (query.isNotBlank()) EmptyState(Lucide.Search, "No folders match", "Try a different name.")
                    else EmptyState(Lucide.FolderOpen, "No folders yet", "Create one to keep things tidy.")
                }
                else -> items(shown, key = { it.id }) { f ->
                    val parent = f.parentId?.let(byId::get)
                    FolderTile(
                        f, subtitle = parent?.let { "in ${it.name}" } ?: friendlyDate(f.createdAt).ifBlank { "My Files" },
                        modifier = Modifier.animateItem(),
                    ) {
                        c.openFolderTrail.value = trail(f)
                        nav.popBackStack()
                    }
                }
            }
        }
      }
        ScrollToTopButton(showTop, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 24.dp)) {
            scope.launch { if (grid.firstVisibleItemIndex > 40) grid.scrollToItem(20); grid.animateScrollToItem(0) }
        }
    }

    if (newFolder) TextInputDialog(
        "New folder", placeholder = "Folder name", confirm = "Create",
        extra = { Spacer(Modifier.height(16.dp)); ColorPicker(newColor) { newColor = it } },
        onDismiss = { newFolder = false },
    ) { name ->
        newFolder = false
        scope.launch {
            runCatching { c.repo.createFolder(name, null, newColor) }
                .onSuccess { c.toast("Folder created"); c.refreshBus.tryEmit(Unit); reload++ }
                .onFailure { c.toast((it as? ApiException)?.friendly ?: "Couldn't create the folder") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTile(folder: Folder, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = folderColor(folder.color)
    val tint = color.copy(alpha = if (Nd.isDark) 0.28f else 0.2f).compositeOver(Nd.Surface)
    val interaction = remember { MutableInteractionSource() }
    val bodyShape = RoundedCornerShape(0.dp, 22.dp, 22.dp, 22.dp)
    Box(modifier.fillMaxWidth().height(122.dp).pressScale(interaction).combinedClickable(interaction, null, onClick = onClick)) {
        Box(Modifier.width(58.dp).height(22.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(tint))
        Column(
            Modifier.padding(top = 12.dp).fillMaxSize().softShadow(bodyShape, 10.dp, 0.08f).clip(bodyShape).background(tint)
                .border(1.dp, color.copy(alpha = 0.18f), bodyShape).padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(Lucide.Folder, null, tint = color, modifier = Modifier.size(24.dp))
            Column {
                Text(folder.name, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Nd.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
