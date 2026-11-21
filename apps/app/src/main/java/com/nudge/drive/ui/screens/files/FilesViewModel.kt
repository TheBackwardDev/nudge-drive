package com.nudge.drive.ui.screens.files

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nudge.drive.AppContainer
import com.nudge.drive.data.ApiException
import com.nudge.drive.data.FileItem
import com.nudge.drive.data.Folder
import com.nudge.drive.util.FileKind
import com.nudge.drive.util.kind
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

enum class SortMode(val label: String) { Newest("Newest first"), Oldest("Oldest first"), Name("Name A–Z"), Largest("Largest first") }

class FilesViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repo

    var path by mutableStateOf(listOf<Folder>()); private set
    val current: Folder? get() = path.lastOrNull()
    var folders by mutableStateOf(listOf<Folder>()); private set
    private var rawFiles by mutableStateOf(listOf<FileItem>())
    var kindCounts by mutableStateOf(mapOf<FileKind, Int>()); private set
    var loading by mutableStateOf(true); private set
    var refreshing by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    var query by mutableStateOf("")
    var kind by mutableStateOf<FileKind?>(null); private set
    var accountId by mutableStateOf<String?>(null); private set
    private var gridState by mutableStateOf(c.prefs.grid.value)
    var grid: Boolean
        get() = gridState
        set(v) { gridState = v; c.prefs.setGrid(v) }
    var sort by mutableStateOf(SortMode.Newest)
    var selected by mutableStateOf(setOf<String>()); private set
    val selectionMode get() = selected.isNotEmpty()
    val searching get() = query.isNotBlank() || kind != null || accountId != null
    /** Non-null while the "move selected files" picker is open. */
    var moveTarget by mutableStateOf<Unit?>(null)

    /** Every folder (for the move picker and global search), refreshed lazily. */
    var allFolders by mutableStateOf(listOf<Folder>()); private set

    val files: List<FileItem>
        get() = when (sort) {
            SortMode.Newest -> rawFiles.sortedByDescending { it.createdAt }
            SortMode.Oldest -> rawFiles.sortedBy { it.createdAt }
            SortMode.Name -> rawFiles.sortedBy { it.name.lowercase() }
            SortMode.Largest -> rawFiles.sortedByDescending { it.size }
        }

    private var job: Job? = null

    init {
        load()
        viewModelScope.launch { c.transfers.uploaded.collect { load(quiet = true) } }
        viewModelScope.launch { c.refreshBus.collect { load(quiet = true) } }
    }

    fun load(quiet: Boolean = false, pull: Boolean = false) {
        job?.cancel()
        if (!quiet && rawFiles.isEmpty() && folders.isEmpty()) loading = true
        if (pull) refreshing = true
        lateinit var mine: Job
        mine = viewModelScope.launch {
            try {
                if (searching) {
                    val filesD = async { repo.files(q = query, kind = kind?.apiKind, accountId = accountId) }
                    val all = try { repo.allFolders() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { allFolders }
                    allFolders = all
                    folders = if (query.isBlank()) emptyList() else all.filter { it.name.contains(query.trim(), ignoreCase = true) }
                    rawFiles = filesD.await()
                } else {
                    val folderId = current?.id
                    val foldersD = async { repo.folders(folderId) }
                    val filesD = async { if (folderId == null) repo.files() else repo.files(folderId = folderId) }
                    val fetched = filesD.await()
                    if (folderId == null) kindCounts = fetched.groupingBy { it.kind() }.eachCount()
                    rawFiles = if (folderId == null) fetched.filter { it.folderId == null } else fetched
                    folders = foldersD.await()
                }
                rawFiles.forEach { c.fileCache[it.id] = it }
                // A selection can't point at files that are no longer listed.
                if (selected.isNotEmpty()) { val ids = rawFiles.mapTo(HashSet()) { it.id }; selected = selected.filterTo(HashSet()) { it in ids } }
                error = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = (e as? ApiException)?.friendly ?: "Couldn't load your files. Check your connection."
            } finally {
                // Only the newest load may end the loading state (an older, cancelled one must not).
                if (job === mine) { loading = false; refreshing = false }
            }
        }
        job = mine
    }

    fun open(folder: Folder) { path = path + folder; clearSearch(reload = false); selected = emptySet(); rawFiles = emptyList(); folders = emptyList(); load() }
    fun openPath(trail: List<Folder>) { path = trail; clearSearch(reload = false); selected = emptySet(); load() }
    fun jumpTo(index: Int) { path = if (index < 0) emptyList() else path.take(index + 1); selected = emptySet(); load() }
    fun up(): Boolean {
        if (selectionMode) { selected = emptySet(); return true }
        if (searching) { clearSearch(); return true }
        if (path.isEmpty()) return false
        path = path.dropLast(1); load(); return true
    }

    fun setKindFilter(k: FileKind?) { kind = if (kind == k) null else k; selected = emptySet(); load() }
    fun setAccountFilter(id: String?) { accountId = id; selected = emptySet(); load() }
    fun clearSearch(reload: Boolean = true) { query = ""; kind = null; accountId = null; selected = emptySet(); if (reload) load() }

    fun toggle(id: String) { selected = if (id in selected) selected - id else selected + id }
    fun selectAll() { selected = rawFiles.map { it.id }.toSet() }
    fun clearSelection() { selected = emptySet() }

    fun ensureAllFolders() = viewModelScope.launch { runCatching { repo.allFolders() }.onSuccess { allFolders = it } }

    private fun act(success: String?, block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }
            .onSuccess { success?.let(c::toast); load(quiet = true) }
            .onFailure { c.toast((it as? ApiException)?.friendly ?: it.message ?: "Something went wrong") }
    }

    // --- batch
    fun trashSelected() {
        val ids = selected.toList(); selected = emptySet()
        act(null) { repo.trashFiles(ids); c.toast("Moved ${ids.size} to trash", "Undo") { undoTrash(ids) } }
    }
    private fun undoTrash(ids: List<String>) = act("Restored") { repo.restore(ids); c.refreshBus.tryEmit(Unit) }
    fun moveSelected(target: Folder?) { val ids = selected.toList(); selected = emptySet(); act("Moved ${ids.size} file${if (ids.size > 1) "s" else ""}") { repo.moveFiles(ids, target?.id) } }
    private fun selectedFiles() = rawFiles.filter { it.id in selected }
    fun shareSelected(activity: android.app.Activity) { val f = selectedFiles(); selected = emptySet(); c.shareOut.share(activity, f) }
    fun sendNearbySelected(ctx: android.content.Context) { val f = selectedFiles(); selected = emptySet(); com.nudge.drive.ui.screens.common.sendNearby(c, ctx, f) }
    fun downloadSelected() { val ids = selected.toList(); selected = emptySet(); c.transfers.downloadZip(ids); c.toast("Preparing zip of ${ids.size} files…") }

    // --- single file
    fun renameFile(f: FileItem, name: String) = act("Renamed") { repo.renameFile(f.id, name) }
    fun trashFile(f: FileItem) = act(null) { repo.trashFile(f.id); c.toast("Moved “${f.name}” to trash", "Undo") { undoTrash(listOf(f.id)) } }
    fun moveFile(f: FileItem, target: Folder?) = act("Moved to ${target?.name ?: "My Files"}") { repo.moveFile(f.id, target?.id) }
    fun download(f: FileItem) { c.transfers.download(f); c.toast("Downloading ${f.name}") }

    // --- folders
    fun createFolder(name: String, color: String) = act("Folder created") { repo.createFolder(name, current?.id, color) }
    fun renameFolder(f: Folder, name: String) = act("Renamed") { repo.updateFolder(f.id, name = name) }
    fun recolorFolder(f: Folder, color: String) = act(null) { repo.updateFolder(f.id, color = color) }
    fun moveFolder(f: Folder, target: Folder?) = act("Folder moved") { repo.updateFolder(f.id, moveTo = true to target?.id) }
    fun deleteFolder(f: Folder) = act("Folder deleted") { repo.deleteFolder(f.id) }

    fun sync() = act(null) {
        val r = repo.syncGoogle()
        val n = r.results.sumOf { it.created + it.updated }
        c.toast(if (n == 0) "Everything is already in sync" else "Synced $n change${if (n == 1) "" else "s"} from Google Drive")
    }
}
