package com.nudge.drive.desktop.ui.pages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.*
import com.nudge.drive.data.ApiException
import androidx.compose.ui.platform.LocalWindowInfo
import com.nudge.drive.desktop.AppState
import com.nudge.drive.desktop.data.NoticeRoute
import com.nudge.drive.desktop.data.NoticeType
import com.nudge.drive.desktop.data.Platform
import com.nudge.drive.desktop.ui.DriveGlyph
import com.nudge.drive.desktop.ui.PageHeader
import com.nudge.drive.desktop.ui.ToolButton
import com.nudge.drive.data.FreeUpDestination
import com.nudge.drive.data.FreeUpJob
import com.nudge.drive.data.FreeUpPlan
import com.nudge.drive.ui.components.ConfirmDialog
import com.nudge.drive.ui.components.GlassCard
import com.nudge.drive.ui.components.PrimaryButton
import com.nudge.drive.ui.components.SecondaryButton
import com.nudge.drive.ui.components.Shimmer
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.ui.theme.Sora
import com.nudge.drive.util.formatBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val GB = 1024L * 1024 * 1024

private data class Kind(val key: String, val label: String, val icon: ImageVector, val color: Color)
private val Kinds = listOf(
    Kind("videos", "Videos", Lucide.Video, Nd.Video),
    Kind("photos", "Photos", Lucide.Image, Nd.Image),
    Kind("documents", "Documents", Lucide.FileText, Nd.Doc),
    Kind("other", "Other files", Lucide.Package, Nd.Zip),
)

private fun Throwable.msg() = (this as? ApiException)?.friendly ?: message ?: "Something went wrong"
private fun String?.bytes() = this?.toLongOrNull() ?: 0L

/** runCatching for suspend calls: a cancelled coroutine stays cancelled instead of turning into an error. */
private inline fun <T> attempt(block: () -> T): Result<T> =
    try { Result.success(block()) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

/**
 * Copying out of Google Photos, one step at a time. Kept at page level, so scrolling the card out of
 * view doesn't drop the picks or stop waiting for them.
 */
private sealed interface PhotosFlow {
    data object Idle : PhotosFlow
    data object CreatingSession : PhotosFlow
    /** Google asks for Photos access in the browser; carries on when the user comes back. */
    data object AwaitingGrant : PhotosFlow
    /** The picker is open; polling until the picks are set. */
    data class Picking(val sessionId: String, val pickerUri: String?, val pollMs: Long) : PhotosFlow
    /** Picks are in and the server is asked to start copying; nothing to cancel any more. */
    data class Starting(val sessionId: String) : PhotosFlow
}

/** A result button that was clicked and is still working; all of them stay disabled meanwhile. */
private enum class JobAction { Stop, Done, More }

/**
 * Free up space on a full Google Drive: shows what is using the 15 GB, offers the quick win
 * (empty trash), then moves the biggest files to the user's other drives while the server
 * does the work and this screen shows it live.
 */
@Composable
fun FreeUpPage(c: AppState, accountId: String) {
    val scope = rememberCoroutineScope()
    var plan by remember { mutableStateOf<FreeUpPlan?>(null) }
    var job by remember { mutableStateOf<FreeUpJob?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Where files / photos go (null = automatic). Lives here so plan reloads and the photos flow keep it.
    var destination by remember(accountId) { mutableStateOf<String?>(null) }
    val currentDestination by rememberUpdatedState(destination)
    var photos by remember(accountId) { mutableStateOf<PhotosFlow>(PhotosFlow.Idle) }
    var jobAction by remember { mutableStateOf<JobAction?>(null) }

    fun load() = scope.launch {
        error = null
        runCatching { c.repo.freeUpPlan(accountId) }
            .onSuccess { plan = it; job = it.job }
            .onFailure { error = it.msg() }
    }
    LaunchedEffect(Unit) { load() }

    // Live progress while the server moves files.
    val running = job?.running == true
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var misses = 0
        while (true) {
            // Every 1.5 s; backs off (up to ~24 s) while the server can't be reached.
            delay(1500L shl misses.coerceAtMost(4))
            val next = try { c.repo.freeUpStatus(accountId) } catch (e: CancellationException) { throw e } catch (e: Exception) { misses++; continue }
            misses = 0
            if (next == null) {
                // No job any more (server restarted, or it was cleared on another device): back to the plan.
                job = null; load(); break
            }
            job = next
            if (!next.running) {
                if (next.isPhotos) {
                    val copied = next.doneFiles - next.skipped
                    if (copied > 0) c.inbox.post("Copied $copied photos & videos from ${plan?.account?.email ?: "Google Photos"}",
                        "They're safe on your other drives. Delete them in Google Photos to free the space.", NoticeRoute.Drives, type = NoticeType.Storage)
                } else {
                    val freed = next.freedBytes.bytes().takeIf { it > 0 } ?: next.movedBytes.bytes()
                    c.inbox.post("Freed ${formatBytes(freed)} on ${plan?.account?.email ?: "your Drive"}",
                        "Moved ${next.doneFiles} files to your other drives.", NoticeRoute.Drives, type = NoticeType.Storage)
                }
                c.changed()
                break
            }
        }
    }

    // ---- Google Photos: pick → the server copies them (see PhotosCard).

    fun openPicker(uri: String) = Platform.openInBrowser(uri.trimEnd('/') + "/autoclose")

    suspend fun openPhotosSession(allowGrant: Boolean): PhotosFlow {
        val acct = plan?.account ?: return PhotosFlow.Idle
        return try {
            val s = c.repo.freeUpPhotosSession(acct.id)
            s.pickerUri?.let { openPicker(it) }
            PhotosFlow.Picking(s.id, s.pickerUri, s.pollIntervalMs)
        } catch (e: ApiException) {
            if (e.code != "PHOTOS_ACCESS_NEEDED" || !allowGrant) throw e
            // Google asks in the browser; we carry on when this window gets focus again.
            Platform.openInBrowser(c.repo.googlePhotosConnectUrl(acct.email))
            PhotosFlow.AwaitingGrant
        }
    }

    fun choosePhotos() {
        if (photos != PhotosFlow.Idle) return
        photos = PhotosFlow.CreatingSession
        scope.launch {
            photos = try { openPhotosSession(allowGrant = true) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { c.toast(e.msg()); PhotosFlow.Idle }
        }
    }

    // Back from allowing it in the browser: carry on by itself (without asking again). Not allowed yet
    // (the user came back early) → keep waiting, so allowing it later still carries on.
    fun checkPhotosGrant() {
        if (photos != PhotosFlow.AwaitingGrant) return
        photos = PhotosFlow.CreatingSession
        scope.launch {
            photos = try { openPhotosSession(allowGrant = false) }
            catch (e: CancellationException) { throw e }
            catch (e: ApiException) { if (e.code == "PHOTOS_ACCESS_NEEDED") PhotosFlow.AwaitingGrant else { c.toast(e.msg()); PhotosFlow.Idle } }
            catch (e: Exception) { c.toast(e.msg()); PhotosFlow.Idle }
        }
    }
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) { if (focused) checkPhotosGrant() }

    fun reopenPhotos() {
        when (val f = photos) {
            is PhotosFlow.Picking -> f.pickerUri?.let { openPicker(it) }
            PhotosFlow.AwaitingGrant -> plan?.account?.email?.let { email ->
                scope.launch { attempt { c.repo.googlePhotosConnectUrl(email) }.onSuccess { Platform.openInBrowser(it) }.onFailure { c.toast(it.msg()) } }
            }
            else -> Unit
        }
    }

    // Waiting for the picks; once Google says they're set, the server starts copying.
    val photosSession = when (val f = photos) { is PhotosFlow.Picking -> f.sessionId; is PhotosFlow.Starting -> f.sessionId; else -> null }
    LaunchedEffect(photosSession) {
        val s = photos as? PhotosFlow.Picking ?: return@LaunchedEffect
        while (true) {
            delay(s.pollMs.coerceIn(2000L, 10_000L))
            val now = try {
                c.repo.freeUpPhotosSessionStatus(accountId, s.sessionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.code == "PHOTOS_SESSION_GONE") { c.toast(e.msg()); photos = PhotosFlow.Idle; return@LaunchedEffect }
                null
            } catch (e: Exception) { null } ?: continue
            if (!now.mediaItemsSet) continue
            photos = PhotosFlow.Starting(s.sessionId) // same key: this effect keeps running
            c.bringToFront() // back to Nudge by itself, straight into the copy
            val started = attempt { c.repo.freeUpPhotosStart(accountId, s.sessionId, currentDestination) }
                .onFailure { c.toast(it.msg()) }.getOrNull()
            photos = PhotosFlow.Idle
            started?.let { job = it }
            return@LaunchedEffect
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageHeader("Free up space", plan?.account?.email) { ToolButton(Lucide.ArrowLeft, "Back") { c.back() } }
            val p = plan
            val j = job
            AnimatedContent(
                when { p == null && error != null -> "error"; p == null -> "loading"; j != null -> "job"; else -> "plan" },
                transitionSpec = { (fadeIn(tween(300)) + scaleIn(initialScale = 0.97f)) togetherWith fadeOut(tween(180)) },
                label = "freeUp",
            ) { stage ->
                when (stage) {
                    "loading" -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Shimmer(Modifier.fillMaxWidth().height(210.dp), corner = 28.dp)
                        Shimmer(Modifier.fillMaxWidth().height(140.dp), corner = 28.dp)
                        Shimmer(Modifier.fillMaxWidth().height(220.dp), corner = 28.dp)
                    }
                    "error" -> Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Lucide.CloudOff, null, tint = Nd.Muted, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(error ?: "", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        SecondaryButton("Try again", icon = Lucide.RefreshCw) { load() }
                    }
                    "job" -> if (p != null && j != null) JobView(c, p, j, jobAction,
                        onCancel = {
                            if (jobAction == null) {
                                jobAction = JobAction.Stop
                                scope.launch {
                                    attempt { c.repo.freeUpCancel(accountId) }.onSuccess { if (it != null) job = it else { job = null; load() } }
                                    jobAction = null
                                }
                            }
                        },
                        onDone = {
                            // Stays pending: the page is leaving, a second click must not go back twice.
                            if (jobAction == null) {
                                jobAction = JobAction.Done
                                scope.launch { attempt { c.repo.freeUpClear(accountId) }; c.back() }
                            }
                        },
                        onMore = {
                            if (jobAction == null) {
                                jobAction = JobAction.More
                                scope.launch {
                                    attempt { c.repo.freeUpClear(accountId) }
                                    job = null; plan = null; jobAction = null; load()
                                }
                            }
                        },
                    )
                    else -> if (p != null) PlanView(c, p, destination, onDestination = { destination = it }, reload = { load() }, onStarted = { job = it }) {
                        PhotosCard(p, photos, onChoose = { choosePhotos() }, onOpenAgain = { reopenPhotos() },
                            onCancel = { if (photos is PhotosFlow.Picking || photos == PhotosFlow.AwaitingGrant) photos = PhotosFlow.Idle })
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Plan

@Composable
private fun PlanView(
    c: AppState, p: FreeUpPlan, destination: String?, onDestination: (String?) -> Unit,
    reload: () -> Unit, onStarted: (FreeUpJob) -> Unit, photosCard: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val total = p.quota.totalBytes.bytes()
    val used = p.quota.usedBytes.bytes()
    val trashBytes = p.quota.driveTrashBytes.bytes() + p.nudgeTrash.bytes.bytes()
    val catBytes = p.categories.associate { it.key to it.bytes.bytes() }
    val catCount = p.categories.associate { it.key to it.count }

    // Keyed by the account, not the plan: a reload (trash emptied, import done) keeps what the user chose.
    var selected by remember(p.account.id) {
        val media = setOf("photos", "videos").filter { (catBytes[it] ?: 0) > 0 }.toSet()
        mutableStateOf(media.ifEmpty { catBytes.filterValues { it > 0 }.keys })
    }
    val selectedBytes = selected.sumOf { catBytes[it] ?: 0L }
    // Recommended: enough to bring the Drive back under 80 %, so Gmail has room to breathe.
    val needed = if (total > 0) (used - total * 8 / 10).coerceAtLeast(0) else 0L
    val amounts = listOf(1L, 2L, 5L, 10L).map { it * GB }.filter { it < selectedBytes }
    var amount by remember(p.account.id, selected) { mutableStateOf(amounts.firstOrNull { it >= needed && needed > 0 }) } // null = everything
    var confirmTrash by remember { mutableStateOf(false) }
    var emptying by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    var importing by remember(p) { mutableStateOf(p.importing?.state == "running") }

    val planned = amount?.coerceAtMost(selectedBytes) ?: selectedBytes
    val destFree: Long? = if (p.destinations.any { it.availableBytes == null }) null else p.destinations.sumOf { it.availableBytes.bytes() }
    val chosenFree = destination?.let { id -> p.destinations.firstOrNull { it.id == id }?.let { d -> d.availableBytes?.toLongOrNull() } }
    val roomFor = if (destination != null) chosenFree else destFree
    val short = roomFor != null && roomFor < planned

    LaunchedEffect(importing) {
        if (!importing) return@LaunchedEffect
        while (true) {
            delay(2000)
            val s = runCatching { c.repo.importStatus(p.account.id) }.getOrNull() ?: continue
            if (s.state != "running") { importing = false; reload(); break }
        }
    }

    fun connectDrive() = scope.launch {
        runCatching { Platform.openInBrowser(c.repo.googleConnectUrl()); c.toast("Finish connecting in your browser, then come back here") }.onFailure { c.toast(it.msg()) }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { HeroCard(p, total, used) }
        item { UsageBreakdown(p, total) }

        if (trashBytes > 0) item {
            ActionCard(Lucide.Trash2, Nd.Warn, "Empty the trash first",
                "Frees about ${formatBytes(trashBytes)} right away. Files in the trash are deleted for good.") {
                PrimaryButton("Empty trash · ${formatBytes(trashBytes)}", Modifier.fillMaxWidth(), loading = emptying, icon = Lucide.Trash2, container = Nd.Warn) { confirmTrash = true }
            }
        }

        val known = p.knownBytes.bytes()
        val driveBytes = p.quota.driveBytes.bytes()
        if (!p.account.fullSync && driveBytes > known + 50 * 1024 * 1024) item {
            ActionCard(Lucide.HardDriveDownload, Nd.Iris, "Let Nudge see the whole Drive",
                "Nudge knows ${formatBytes(known)} of the ${formatBytes(driveBytes)} of files on this Drive. Import it so everything can be moved.") {
                PrimaryButton(if (importing) "Importing…" else "Import this Drive", Modifier.fillMaxWidth(), loading = importing, icon = Lucide.HardDriveDownload) {
                    scope.launch { runCatching { c.repo.importDrive(p.account.id) }.onSuccess { importing = true }.onFailure { c.toast(it.msg()) } }
                }
            }
        }

        // Most full accounts are full of Google Photos, which only the Photos picker can reach.
        val hasFiles = catBytes.values.any { it > 0 }
        val photosWorth = p.quota.otherBytes.bytes() > 256L * 1024 * 1024
        val showMove = hasFiles || !photosWorth
        if (photosWorth) {
            item { FreeUpLabel("Google Photos") }
            item { photosCard() }
            p.photosCopied?.let { pc -> item { DeleteInPhotosCard(c, p, pc.count, pc.takenFrom, pc.takenTo) } }
        }
        if (showMove) item { FreeUpLabel("Move files to your other drives") }

        if (p.destinations.isEmpty()) item {
            ActionCard(Lucide.Plus, Nd.Iris, "Add a drive to move files to",
                "Connect another Google account (15 GB free each) or an S3 bucket. Then Nudge can move files there and empty this one.") {
                PrimaryButton("Connect Google Drive", Modifier.fillMaxWidth(), icon = Lucide.Plus) { connectDrive() }
            }
        } else {
            if (showMove) item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Kinds.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { k ->
                                val bytes = catBytes[k.key] ?: 0L
                                KindTile(k, bytes, catCount[k.key] ?: 0, k.key in selected, enabled = bytes > 0) {
                                    selected = if (k.key in selected) selected - k.key else selected + k.key
                                }
                            }
                        }
                    }
                }
            }
            if (showMove && selectedBytes > 0) item {
                Column {
                    Text("How much to move", style = MaterialTheme.typography.labelLarge, color = Nd.Muted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        amounts.forEach { a ->
                            AmountChip(formatBytes(a), amount == a, recommended = needed > 0 && a == amounts.firstOrNull { it >= needed }) { amount = a }
                        }
                        AmountChip("All · ${formatBytes(selectedBytes)}", amount == null, recommended = needed > 0 && amounts.none { it >= needed }) { amount = null }
                    }
                }
            }
            item {
                Column {
                    Text(if (showMove) "Move them to" else "Copy them to", style = MaterialTheme.typography.labelLarge, color = Nd.Muted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(6.dp)) {
                        DestRow(null, "Automatic", "Spread across the drives with the most free space", destFree, destination == null) { onDestination(null) }
                        p.destinations.forEach { d ->
                            DestRow(d, d.displayName ?: d.email, d.email, d.availableBytes?.toLongOrNull(), destination == d.id) { onDestination(d.id) }
                        }
                    }
                }
            }
            if (short && showMove) item {
                ActionCard(Lucide.TriangleAlert, Nd.Warn, "Not enough room for all of it",
                    "Your other drives have ${formatBytes(roomFor)} free. Nudge will move as much as fits. Connect another Google account for 15 GB more.") {
                    SecondaryButton("Connect another Google account", Modifier.fillMaxWidth(), icon = Lucide.Plus) { connectDrive() }
                }
            }
            val preview = if (showMove) p.largest.filter { it.category in selected }.take(5) else emptyList()
            if (preview.isNotEmpty()) item {
                Column {
                    Text("Biggest files go first", style = MaterialTheme.typography.labelLarge, color = Nd.Muted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                        preview.forEach { f ->
                            val k = Kinds.firstOrNull { it.key == f.category } ?: Kinds.last()
                            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(k.color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                                    Icon(k.icon, null, tint = k.color, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(f.name, style = MaterialTheme.typography.bodyMedium, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(formatBytes(f.sizeBytes.bytes()), style = MaterialTheme.typography.labelMedium, color = Nd.Muted)
                            }
                        }
                    }
                }
            }
            if (showMove) item {
                Column(Modifier.padding(top = 4.dp)) {
                    PrimaryButton(
                        if (selectedBytes == 0L) "Pick what to move" else "Free up ${formatBytes(planned)}",
                        Modifier.fillMaxWidth(), enabled = selectedBytes > 0 && !importing, loading = starting, icon = Lucide.Sparkles,
                    ) {
                        starting = true
                        scope.launch {
                            attempt { c.repo.freeUpStart(p.account.id, selected.toList(), amount, destination) }
                                .onSuccess { it?.let(onStarted) }
                                .onFailure { c.toast(it.msg()) }
                            starting = false
                        }
                    }
                    Text(
                        "Each file is copied to the other drive first and only then removed here. It keeps its folder, shares and links in Nudge.",
                        style = MaterialTheme.typography.bodySmall, color = Nd.Muted, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 8.dp, end = 8.dp),
                    )
                }
            }
        }
    }

    if (confirmTrash) ConfirmDialog(
        "Empty the trash?",
        "Everything in this Drive's trash (and Nudge's trash for it) is deleted for good. This frees about ${formatBytes(trashBytes)}.",
        "Empty trash", onDismiss = { confirmTrash = false },
    ) {
        confirmTrash = false
        emptying = true
        scope.launch {
            attempt { c.repo.freeUpEmptyTrash(p.account.id) }
                .onSuccess { c.toast("Trash emptied" + (it.freedBytes.bytes().takeIf { b -> b > 0 }?.let { b -> " · ${formatBytes(b)} freed" } ?: "")); reload() }
                .onFailure { c.toast(it.msg()) }
            emptying = false
        }
    }
}

@Composable
private fun HeroCard(p: FreeUpPlan, total: Long, used: Long) {
    val frac = if (total > 0) used.toFloat() / total else 0f
    val color = when { frac >= 0.95f -> Nd.Danger; frac >= 0.85f -> Nd.Warn; else -> Nd.Iris }
    val (status, body) = when {
        frac >= 0.99f -> "Full" to "Gmail can't receive new email and Google Photos can't back up until space is freed."
        frac >= 0.85f -> "Almost full" to "Free some space now so Gmail and Google Photos keep working."
        else -> "Healthy" to "There's room on this Drive. You can still move files off it."
    }
    GlassCard(Modifier.fillMaxWidth(), strong = true, padding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (p.account.avatarUrl != null) AsyncImage(p.account.avatarUrl, null, Modifier.size(34.dp).clip(CircleShape))
            else Box(Modifier.size(34.dp).clip(CircleShape).background(Nd.Mist), contentAlignment = Alignment.Center) { DriveGlyph(Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.account.displayName ?: p.account.email, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p.account.email, style = MaterialTheme.typography.bodySmall, color = Nd.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(status, style = MaterialTheme.typography.labelMedium, color = color,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 5.dp))
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BigRing(frac, color, 116.dp) {
                Text("${(frac * 100).toInt().coerceAtMost(100)}%", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Nd.Ink)
                Text("used", style = MaterialTheme.typography.labelSmall, color = Nd.Muted)
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(formatBytes(used), fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Nd.Ink)
                Text("of ${formatBytes(total)}", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted)
                Spacer(Modifier.height(8.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
            }
        }
    }
}

@Composable
private fun UsageBreakdown(p: FreeUpPlan, total: Long) {
    val drive = p.quota.driveBytes.bytes()
    val trash = p.quota.driveTrashBytes.bytes()
    val other = p.quota.otherBytes.bytes()
    val parts = listOf(
        Triple("Drive files", drive, Nd.Iris),
        Triple("Trash", trash, Nd.Warn),
        Triple("Gmail & Google Photos", other, Nd.Subtle),
    )
    val denom = (total.takeIf { it > 0 } ?: (drive + trash + other)).coerceAtLeast(1)
    GlassCard(Modifier.fillMaxWidth()) {
        Text("What's using space", style = MaterialTheme.typography.titleMedium, color = Nd.Ink)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Nd.Mist)) {
            parts.forEach { (_, b, col) ->
                val w by animateFloatAsState((b.toFloat() / denom).coerceIn(0f, 1f), tween(900), label = "seg")
                if (w > 0.002f) Box(Modifier.weight(w).height(14.dp).background(col))
            }
            val rest = (1f - parts.sumOf { it.second }.toFloat() / denom).coerceIn(0f, 1f)
            if (rest > 0.002f) Spacer(Modifier.weight(rest))
        }
        Spacer(Modifier.height(10.dp))
        parts.forEach { (label, b, col) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(col))
                Spacer(Modifier.width(10.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = Nd.Ink, modifier = Modifier.weight(1f))
                Text(formatBytes(b), style = MaterialTheme.typography.labelLarge, color = Nd.Ink)
            }
        }
        if (other > 512L * 1024 * 1024) Text(
            "Gmail and Google Photos share the same 15 GB. Nudge can copy your Google Photos out (below). Gmail has to be cleaned up in Gmail.",
            style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ActionCard(icon: ImageVector, tint: Color, title: String, body: String, action: @Composable () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
                Text(body, style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        action()
    }
}

@Composable
private fun FreeUpLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = Nd.Ink, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
}

@Composable
private fun RowScope.KindTile(k: Kind, bytes: Long, count: Int, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val bg by animateColorAsState(if (selected) k.color.copy(alpha = 0.13f) else Nd.Glass, label = "kbg")
    val border by animateColorAsState(if (selected) k.color else Nd.GlassBorder, label = "kbd")
    Column(
        Modifier.weight(1f).clip(shape).background(bg).border(if (selected) 1.5.dp else 1.dp, border, shape)
            .bounceClick(enabled = enabled, onClick = onClick).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(k.color.copy(alpha = if (enabled) 0.18f else 0.06f)), contentAlignment = Alignment.Center) {
                Icon(k.icon, null, tint = if (enabled) k.color else Nd.Subtle, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            Icon(if (selected) Lucide.CircleCheck else Lucide.Circle, null, tint = if (selected) k.color else Nd.Subtle, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(k.label, style = MaterialTheme.typography.titleSmall, color = if (enabled) Nd.Ink else Nd.Subtle)
        Text(if (enabled) "${formatBytes(bytes)} · $count" else "None here", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
    }
}

@Composable
private fun AmountChip(text: String, selected: Boolean, recommended: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Nd.Inverse else Nd.Glass, label = "abg")
    val fg by animateColorAsState(if (selected) Nd.OnInverse else Nd.Ink, label = "afg")
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.clip(shape).background(bg).border(1.dp, if (selected) Color.Transparent else Nd.GlassBorder, shape).bounceClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
        if (recommended) Text("Suggested", style = MaterialTheme.typography.labelSmall, color = if (selected) fg.copy(alpha = 0.75f) else Nd.Iris)
    }
}

@Composable
private fun DestRow(d: FreeUpDestination?, title: String, sub: String, free: Long?, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Nd.Mist else Color.Transparent, label = "dbg")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg).bounceClick(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Nd.Surface), contentAlignment = Alignment.Center) {
            when {
                d == null -> Icon(Lucide.Sparkles, null, tint = Nd.Iris, modifier = Modifier.size(18.dp))
                d.avatarUrl != null -> AsyncImage(d.avatarUrl, null, Modifier.size(36.dp).clip(CircleShape))
                d.isGoogle -> DriveGlyph(Modifier.size(18.dp))
                else -> Icon(Lucide.Database, null, tint = Nd.Iris, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Nd.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(if (free == null) "No limit" else "${formatBytes(free)} free", style = MaterialTheme.typography.labelMedium, color = Nd.Muted, modifier = Modifier.padding(horizontal = 8.dp))
        Icon(if (selected) Lucide.CircleDot else Lucide.Circle, null, tint = if (selected) Nd.Iris else Nd.Subtle, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun BigRing(fraction: Float, color: Color, size: Dp, stroke: Dp = 11.dp, content: @Composable () -> Unit) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(dampingRatio = 0.85f, stiffness = 60f), label = "big")
    val track = Nd.Mist
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val d = this.size.minDimension - w
            val tl = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(w))
            drawArc(color, -90f, 360f * f, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

// ------------------------------------------------------------------ Job

@Composable
private fun JobView(c: AppState, p: FreeUpPlan, j: FreeUpJob, pending: JobAction?, onCancel: () -> Unit, onDone: () -> Unit, onMore: () -> Unit) {
    val scope = rememberCoroutineScope()
    var confirmCancel by remember { mutableStateOf(false) }
    var showFailures by remember { mutableStateOf(false) }
    val moved = j.movedBytes.bytes()
    val totalBytes = j.totalBytes.bytes().coerceAtLeast(1)
    val running = j.running
    val freed = j.freedBytes.bytes().takeIf { it > 0 } ?: moved
    val photos = j.isPhotos
    val copied = j.doneFiles - j.skipped
    val progress = if (photos) j.doneFiles.toFloat() / j.totalFiles.coerceAtLeast(1) else moved.toFloat() / totalBytes

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth(), strong = true, padding = PaddingValues(22.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (running) TransferVisual(p)
                    BigRing(
                        if (running) progress else 1f,
                        when { running -> Nd.Iris; j.state == "done" && j.doneFiles > 0 -> Nd.Success; j.state == "failed" -> Nd.Danger; else -> Nd.Warn },
                        168.dp, 14.dp,
                    ) {
                        AnimatedContent(running, label = "ringCenter") { r ->
                            if (r && photos) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${j.doneFiles}", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Nd.Ink)
                                Text(if (j.totalFiles > 0) "of ${j.totalFiles}" else "getting ready", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                            } else if (r) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(formatBytes(moved), fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Nd.Ink)
                                Text("of ${formatBytes(totalBytes)}", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                            } else Icon(
                                if (j.state == "done" && j.doneFiles > 0) Lucide.Check else if (j.state == "failed") Lucide.X else Lucide.Pause,
                                null, tint = if (j.state == "done" && j.doneFiles > 0) Nd.Success else Nd.Warn, modifier = Modifier.size(56.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    val title = when {
                        photos && running -> "Copying from Google Photos…"
                        photos && j.state == "done" && copied > 0 -> "Copied $copied photo${if (copied == 1) "" else "s"} & videos"
                        photos && j.state == "done" && j.skipped > 0 -> "Already copied"
                        photos && j.state == "done" -> "Nothing could be copied"
                        running -> "Freeing up space…"
                        j.state == "done" && j.doneFiles > 0 -> "Freed ${formatBytes(freed)}"
                        j.state == "done" -> "Nothing could be moved"
                        j.state == "cancelled" -> "Stopped"
                        else -> "Something went wrong"
                    }
                    Text(title, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Nd.Ink, textAlign = TextAlign.Center)
                    Text(
                        when {
                            photos && running -> "Copying ${j.doneFiles} of ${j.totalFiles} from Google Photos · ${formatBytes(moved)} so far"
                            photos && j.state == "done" && copied > 0 -> "${formatBytes(moved)} is safe on your other drives, in the folder “${j.folderName ?: "Google Photos"}”." +
                                (if (j.skipped > 0) " ${j.skipped} were already copied before." else "")
                            photos && j.state == "done" && j.skipped > 0 -> "All ${j.skipped} were copied before. They're in “${j.folderName ?: "Google Photos"}”."
                            photos && j.state == "cancelled" -> "Copied $copied (${formatBytes(moved)}) before stopping. Nothing was removed from Google Photos."
                            photos && j.state == "done" -> "None of them could be copied. Everything is still in Google Photos."
                            running -> "Moving ${j.doneFiles} of ${j.totalFiles} files from ${p.account.email}"
                            j.state == "done" && j.doneFiles > 0 -> "Moved ${j.doneFiles} files off ${p.account.email}. Gmail and Google Photos can use this space again. Google may take a few minutes to show the new total."
                            j.state == "cancelled" -> "Moved ${j.doneFiles} files (${formatBytes(moved)}) before stopping. The rest stayed where they were."
                            j.state == "failed" -> j.error ?: "The move stopped. Files that weren't moved are still safe on this Drive."
                            else -> "None of the files could be moved. They are all still safe on this Drive."
                        },
                        style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
                    )
                    if (running && !photos) {
                        Spacer(Modifier.height(14.dp))
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Nd.Mist)) {
                            val f by animateFloatAsState(if (j.totalFiles == 0) 0f else j.doneFiles.toFloat() / j.totalFiles, tween(600), label = "files")
                            Box(Modifier.fillMaxWidth(f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Nd.Iris))
                        }
                    }
                }
            }
        }
        if (running && j.current.isNotEmpty()) item {
            GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                j.current.take(3).forEach { name ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Nd.Iris, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(name, style = MaterialTheme.typography.bodyMedium, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (j.destinations.isNotEmpty()) item {
            GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                Text(if (running) (if (photos) "Copying to" else "Going to") else "Now stored on", style = MaterialTheme.typography.labelLarge, color = Nd.Muted, modifier = Modifier.padding(vertical = 6.dp))
                j.destinations.forEach { d ->
                    val avatar = p.destinations.firstOrNull { it.id == d.id }?.avatarUrl
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Nd.Surface), contentAlignment = Alignment.Center) {
                            when {
                                avatar != null -> AsyncImage(avatar, null, Modifier.size(32.dp).clip(CircleShape))
                                d.provider == "s3" -> Icon(Lucide.Database, null, tint = Nd.Iris, modifier = Modifier.size(16.dp))
                                else -> DriveGlyph(Modifier.size(16.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(d.email, style = MaterialTheme.typography.bodyMedium, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${d.files} · ${formatBytes(d.bytes.bytes())}", style = MaterialTheme.typography.labelMedium, color = Nd.Muted)
                    }
                }
            }
        }
        if (photos && !running && j.doneFiles > 0) item { DeleteInPhotosCard(c, p, j.doneFiles, j.takenFrom, j.takenTo) }
        if (!running && j.outOfSpace) item {
            ActionCard(Lucide.TriangleAlert, Nd.Warn, "Your other drives ran out of space",
                "Connect another Google account (15 GB free) and run Free up space again to move the rest.") {
                SecondaryButton("Connect another Google account", Modifier.fillMaxWidth(), icon = Lucide.Plus) {
                    scope.launch { runCatching { Platform.openInBrowser(c.repo.googleConnectUrl()); c.toast("Finish connecting in your browser, then come back here") }.onFailure { c.toast(it.msg()) } }
                }
            }
        }
        if (j.failedCount > 0) item {
            GlassCard(Modifier.fillMaxWidth(), onClick = { showFailures = !showFailures }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.CircleAlert, null, tint = Nd.Warn, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("${j.failedCount} file${if (j.failedCount == 1) "" else "s"} not ${if (photos) "copied" else "moved"}", style = MaterialTheme.typography.titleSmall, color = Nd.Ink, modifier = Modifier.weight(1f))
                    Icon(if (showFailures) Lucide.ChevronUp else Lucide.ChevronDown, null, tint = Nd.Muted)
                }
                AnimatedVisibility(showFailures, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(Modifier.padding(top = 8.dp)) {
                        j.failures.forEach { f ->
                            Column(Modifier.padding(vertical = 5.dp)) {
                                Text(f.name, style = MaterialTheme.typography.bodyMedium, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(f.reason, style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (running) {
                    Text("This runs on your server. You can leave this screen or close the app and it keeps going.",
                        style = MaterialTheme.typography.bodySmall, color = Nd.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
                    SecondaryButton("Stop after the current files", Modifier.fillMaxWidth(), icon = Lucide.Pause, enabled = pending == null) { confirmCancel = true }
                } else {
                    PrimaryButton("Done", Modifier.fillMaxWidth(), enabled = pending == null || pending == JobAction.Done, loading = pending == JobAction.Done, icon = Lucide.Check, onClick = onDone)
                    SecondaryButton(if (photos) "Copy more photos" else "Free up more", Modifier.fillMaxWidth(), icon = Lucide.Sparkles, enabled = pending == null, onClick = onMore)
                }
            }
        }
    }

    if (confirmCancel) ConfirmDialog(
        if (photos) "Stop copying?" else "Stop moving files?",
        if (photos) "Photos already copied stay copied. Nothing is removed from Google Photos." else "Files already moved stay on their new drive. The rest stay on ${p.account.email}.", "Stop",
        onDismiss = { confirmCancel = false }) { confirmCancel = false; onCancel() }
}

/** Source drive → a stream of dots → the destination drives, while files are moving. */
@Composable
private fun TransferVisual(p: FreeUpPlan) {
    val t = rememberInfiniteTransition(label = "flow")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val dot = Nd.Iris
    Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        DriveBubble(p.account.avatarUrl)
        Canvas(Modifier.weight(1f).height(20.dp).padding(horizontal = 8.dp)) {
            val n = 6
            for (i in 0 until n) {
                val x = ((i.toFloat() / n + phase) % 1f)
                val a = kotlin.math.sin(x * Math.PI).toFloat()
                drawCircle(dot.copy(alpha = 0.25f + 0.75f * a), radius = 3.dp.toPx() + 2.dp.toPx() * a, center = Offset(x * size.width, size.height / 2))
            }
        }
        val firstDest = p.destinations.firstOrNull()
        DriveBubble(firstDest?.avatarUrl, s3 = firstDest?.isGoogle == false, extra = (p.destinations.size - 1).coerceAtLeast(0))
    }
}

@Composable
private fun DriveBubble(avatar: String?, s3: Boolean = false, extra: Int = 0) {
    Box(contentAlignment = Alignment.BottomEnd) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Nd.Surface).border(1.dp, Nd.GlassBorder, CircleShape), contentAlignment = Alignment.Center) {
            when {
                avatar != null -> AsyncImage(avatar, null, Modifier.size(52.dp).clip(CircleShape))
                s3 -> Icon(Lucide.Database, null, tint = Nd.Iris, modifier = Modifier.size(24.dp))
                else -> DriveGlyph(Modifier.size(24.dp))
            }
        }
        if (extra > 0) Text("+$extra", style = MaterialTheme.typography.labelSmall, color = Nd.OnInverse,
            modifier = Modifier.clip(CircleShape).background(Nd.Inverse).padding(horizontal = 5.dp, vertical = 1.dp))
    }
}

// ------------------------------------------------------------------ Google Photos

/**
 * Google Photos can't be read through Drive. Google lets apps see only what the user picks in Google's
 * own picker, so: pick → the server copies the originals to the other drives → the user deletes them
 * in Google Photos (no app may delete there).
 */
@Composable
private fun PhotosCard(p: FreeUpPlan, flow: PhotosFlow, onChoose: () -> Unit, onOpenAgain: () -> Unit, onCancel: () -> Unit) {
    val other = p.quota.otherBytes.bytes()

    GlassCard(Modifier.fillMaxWidth(), strong = true, padding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Nd.Image.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Lucide.Images, null, tint = Nd.Image, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Copy your Google Photos out", style = MaterialTheme.typography.titleMedium, color = Nd.Ink)
                Text(
                    "${formatBytes(other)} of this account is Google Photos and Gmail. Pick photos and videos, and Nudge copies the full-quality originals to your other drives.",
                    style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        AnimatedContent(flow, contentKey = { it is PhotosFlow.Idle || it is PhotosFlow.CreatingSession }, label = "photos") { f ->
            if (f is PhotosFlow.Idle || f is PhotosFlow.CreatingSession) Column {
                PrimaryButton("Choose from Google Photos", Modifier.fillMaxWidth(), enabled = p.destinations.isNotEmpty(), loading = f is PhotosFlow.CreatingSession, icon = Lucide.Images, onClick = onChoose)
                Spacer(Modifier.height(14.dp))
                Step(1, "Pick photos and videos in Google Photos (up to 2,000 at a time).")
                Step(2, "Nudge copies the originals to your other drives. Nothing is removed yet.")
                Step(3, "You delete them in Google Photos, and the space is free again.")
                if (!p.photosAccess) Text(
                    "Google will ask once to let Nudge see the photos you pick. Nudge never sees the rest of your library.",
                    style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.padding(top = 8.dp),
                )
            } else Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Nd.Image, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(if (f is PhotosFlow.AwaitingGrant) "Allow Nudge to see the photos you pick in your browser, then come back here. Nudge carries on by itself." else "Select photos and videos in the browser, then click Done. Nudge starts copying by itself.",
                        style = MaterialTheme.typography.bodyMedium, color = Nd.Ink, modifier = Modifier.weight(1f))
                }
                // Once the picks are in, the copy is being started: nothing left to cancel.
                if (f !is PhotosFlow.Starting) {
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("Open again", Modifier.weight(1f), icon = Lucide.ExternalLink, onClick = onOpenAgain)
                        SecondaryButton("Cancel", Modifier.weight(1f), icon = Lucide.X, onClick = onCancel)
                    }
                }
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Nd.Mist), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.labelSmall, color = Nd.Ink)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Nd.Ink, modifier = Modifier.weight(1f).padding(top = 2.dp))
    }
}

/**
 * No app may delete from Google Photos, so the last step is the user's. It still happens inside Nudge:
 * Google Photos opens in the same in-app sheet as the picker (already signed in), with the dates to
 * select, and the space freed is shown live when the user comes back.
 */
@Composable
private fun DeleteInPhotosCard(c: AppState, p: FreeUpPlan, count: Int, takenFrom: String?, takenTo: String?) {
    val scope = rememberCoroutineScope()
    val usedBefore = remember(p) { p.quota.usedBytes.bytes() }
    var usedNow by remember { mutableStateOf<Long?>(null) }
    var checking by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf(false) }

    fun check() = scope.launch {
        checking = true
        runCatching { c.repo.freeUpPlan(p.account.id) }.onSuccess { usedNow = it.quota.usedBytes.bytes() }
        checking = false
    }
    // Back from Google Photos: show what came free.
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) { if (focused && opened) check() }

    val fmt = remember { java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy") }
    fun day(iso: String?) = iso?.let { runCatching { java.time.Instant.parse(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(fmt) }.getOrNull() }
    val from = day(takenFrom)
    val to = day(takenTo)
    val range = when {
        from == null || to == null -> null
        from == to -> "all taken on $from"
        else -> "taken between $from and $to"
    }

    GlassCard(Modifier.fillMaxWidth(), strong = true, padding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Nd.Warn.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(Lucide.Trash2, null, tint = Nd.Warn, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Now delete the originals", style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
                Text("Google only lets you delete in Google Photos itself, so it opens in your browser. Your copies are safe.",
                    style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
            }
        }
        if (range != null) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nd.Mist).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.CalendarDays, null, tint = Nd.Iris, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("The $count copied are $range.", style = MaterialTheme.typography.bodySmall, color = Nd.Ink)
            }
        }
        Spacer(Modifier.height(12.dp))
        Step(1, "Click the circle next to a date to select that whole day, or click the first photo and Shift-click the last.")
        Step(2, "Click the bin icon to delete them.")
        Step(3, "Then empty the bin, or Google keeps using the space for 60 days.")
        Spacer(Modifier.height(14.dp))
        PrimaryButton("1 · Delete in Google Photos", Modifier.fillMaxWidth(), icon = Lucide.Trash2, container = Nd.Warn) {
            opened = true
            Platform.openInBrowser("https://photos.google.com/")
        }
        Spacer(Modifier.height(10.dp))
        SecondaryButton("2 · Empty the bin", Modifier.fillMaxWidth(), icon = Lucide.Trash) {
            opened = true
            Platform.openInBrowser("https://photos.google.com/trash")
        }

        val now = usedNow
        if (now != null || checking) {
            Spacer(Modifier.height(14.dp))
            val freed = (usedBefore - (now ?: usedBefore)).coerceAtLeast(0)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background((if (freed > 0) Nd.Success else Nd.Mist).copy(alpha = if (freed > 0) 0.14f else 1f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (checking) CircularProgressIndicator(Modifier.size(16.dp), color = Nd.Iris, strokeWidth = 2.dp)
                else Icon(if (freed > 0) Lucide.CircleCheck else Lucide.Clock, null, tint = if (freed > 0) Nd.Success else Nd.Muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        checking -> "Checking the space on ${p.account.email}…"
                        freed > 0 -> "${formatBytes(freed)} freed · ${formatBytes(now!!)} used now"
                        else -> "Still ${formatBytes(now!!)} used. Google can take a few minutes after the bin is emptied."
                    },
                    style = MaterialTheme.typography.bodySmall, color = Nd.Ink, modifier = Modifier.weight(1f),
                )
                if (!checking) Text("Check", style = MaterialTheme.typography.labelLarge, color = Nd.Iris, modifier = Modifier.bounceClick { check() }.padding(start = 8.dp))
            }
        }
    }
}
