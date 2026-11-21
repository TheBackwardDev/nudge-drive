package com.nudge.drive.ui.screens.share

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CloudUpload
import com.composables.icons.lucide.File
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderPlus
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.nudge.drive.AppContainer
import com.nudge.drive.data.ApiException
import com.nudge.drive.data.Folder
import com.nudge.drive.data.NearbyShare
import com.nudge.drive.data.OutgoingItem
import com.nudge.drive.ui.components.NudgeSheet
import com.nudge.drive.ui.components.PrimaryButton
import com.nudge.drive.ui.components.TextInputDialog
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.screens.common.ColorPicker
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.util.FolderPalette
import com.nudge.drive.util.folderColor
import com.nudge.drive.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "Save to Nudge Drive": files shared from another app, with a folder browser to pick (or create) where they go. */
@Composable
fun SaveSharedSheet(c: AppContainer) {
    val uris by c.incomingShares.collectAsState()
    if (uris.isEmpty()) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember(uris) { mutableStateOf<List<OutgoingItem>>(emptyList()) }
    var path by remember(uris) { mutableStateOf(listOf<Folder>()) }
    var folders by remember(uris) { mutableStateOf<List<Folder>?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var newColor by remember { mutableStateOf(FolderPalette.first()) }
    var reload by remember { mutableStateOf(0) }
    val current = path.lastOrNull()

    LaunchedEffect(uris) { items = withContext(Dispatchers.IO) { uris.map { NearbyShare.describe(ctx, it) } } }
    LaunchedEffect(current?.id, reload) {
        folders = null
        folders = runCatching { c.repo.folders(current?.id) }.getOrElse { emptyList() }.sortedBy { it.name.lowercase() }
    }
    val dismiss = { c.incomingShares.value = emptyList() }

    NudgeSheet(onDismiss = dismiss) {
        Text("Save to Nudge Drive", style = MaterialTheme.typography.titleLarge, color = Nd.Ink)
        Text(
            "${uris.size} item${if (uris.size == 1) "" else "s"}" + (items.sumOf { it.size }.takeIf { it > 0 }?.let { " · ${formatBytes(it)}" } ?: ""),
            style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items.take(20)) { it -> SharedThumb(it) }
        }
        Spacer(Modifier.height(16.dp))

        // Destination browser
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Save in", style = MaterialTheme.typography.labelMedium, color = Nd.Muted, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(14.dp)).bounceClick { newFolder = true }.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.FolderPlus, null, tint = Nd.IrisDeep, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("New folder", style = MaterialTheme.typography.labelMedium, color = Nd.IrisDeep)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nd.Mist).padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (path.isNotEmpty()) Box(Modifier.size(32.dp).clip(CircleShape).bounceClick { path = path.dropLast(1) }, contentAlignment = Alignment.Center) {
                Icon(Lucide.ChevronLeft, "Up one folder", tint = Nd.Ink, modifier = Modifier.size(18.dp))
            } else Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Icon(Lucide.House, null, tint = Nd.IrisDeep, modifier = Modifier.size(18.dp)) }
            Text(
                (listOf("My Files") + path.map { it.name }).joinToString("  ›  "),
                style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        AnimatedContent(
            current?.id to folders, label = "folders",
            transitionSpec = { (fadeIn() + slideInHorizontally { it / 6 }) togetherWith (fadeOut() + slideOutHorizontally { -it / 6 }) },
        ) { (_, list) ->
            when {
                list == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Nd.Iris) }
                list.isEmpty() -> Text(
                    "No folders here. Save here, or create a new folder.",
                    style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                )
                else -> LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                    items(list, key = { it.id }) { f ->
                        val color = folderColor(f.color)
                        Row(
                            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(Nd.Surface)
                                .border(1.dp, Nd.CardBorder, RoundedCornerShape(16.dp)).bounceClick { path = path + f }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                                Icon(Lucide.Folder, null, tint = color, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(f.name, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Lucide.ChevronRight, null, tint = Nd.Subtle, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Save to ${current?.name ?: "My Files"}", Modifier.fillMaxWidth(), icon = Lucide.CloudUpload) {
            c.toast("Saving ${uris.size} item${if (uris.size == 1) "" else "s"} to ${current?.name ?: "My Files"}…")
            c.transfers.upload(uris, current?.id)
            dismiss()
        }
        Spacer(Modifier.height(8.dp))
    }

    if (newFolder) TextInputDialog(
        "New folder", placeholder = "Folder name", confirm = "Create",
        extra = { Spacer(Modifier.height(16.dp)); ColorPicker(newColor) { newColor = it } },
        onDismiss = { newFolder = false },
    ) { name ->
        newFolder = false
        scope.launch {
            runCatching { c.repo.createFolder(name, current?.id, newColor) }
                .onSuccess { f -> path = path + f; c.refreshBus.tryEmit(Unit) }
                .onFailure { c.toast((it as? ApiException)?.friendly ?: "Couldn't create the folder") }
            reload++
        }
    }
}

@Composable
private fun SharedThumb(item: OutgoingItem) {
    val shape = RoundedCornerShape(14.dp)
    Box(Modifier.size(64.dp).clip(shape).background(Nd.Mist), contentAlignment = Alignment.Center) {
        if (item.mime.startsWith("image/") || item.mime.startsWith("video/")) {
            AsyncImage(item.uri, item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Lucide.File, null, tint = Nd.Iris, modifier = Modifier.size(22.dp))
            Text(item.name.substringAfterLast('.', "").uppercase().take(4), style = MaterialTheme.typography.labelSmall, color = Nd.Muted)
        }
    }
}

/** Shown while a file is being fetched so it can be shared as a real file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharePrepDialog(c: AppContainer) {
    val prep by c.shareOut.state.collectAsState()
    val p = prep ?: return
    BasicAlertDialog(onDismissRequest = { c.shareOut.cancel() }) {
        Column(Modifier.clip(RoundedCornerShape(28.dp)).background(Nd.Surface).padding(22.dp)) {
            Text("Getting it ready to share", style = MaterialTheme.typography.titleMedium, color = Nd.Ink)
            Text(p.title, style = MaterialTheme.typography.bodySmall, color = Nd.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            LinearProgressIndicator({ p.progress }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Nd.Primary, trackColor = Nd.Mist)
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${(p.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = Nd.Muted, modifier = Modifier.weight(1f))
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = Nd.IrisDeep,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).bounceClick { c.shareOut.cancel() }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }
}
