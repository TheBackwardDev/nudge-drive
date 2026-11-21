package com.nudge.drive.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.prefStore by preferencesDataStore("nudge_prefs")

enum class ThemeMode(val label: String) { System("System"), Light("Light"), Dark("Dark") }

/** Small user preferences, loaded once at start so the theme never flashes. */
class Prefs(private val context: Context, private val scope: CoroutineScope) {
    private val kTheme = stringPreferencesKey("theme")
    private val kSysNotif = booleanPreferencesKey("system_notifications")
    private val kStorageAlerts = booleanPreferencesKey("storage_alerts")
    private val kGrid = booleanPreferencesKey("grid")
    private val kChecklist = booleanPreferencesKey("checklist_dismissed")
    private val kAskedNotif = booleanPreferencesKey("asked_notifications")
    private val kSeenInvites = stringSetPreferencesKey("seen_invites")
    private val kOnboarded = stringSetPreferencesKey("onboarded_users")
    private val kOnboardMigrated = booleanPreferencesKey("onboard_migrated")

    val theme = MutableStateFlow(ThemeMode.System)
    val systemNotifications = MutableStateFlow(true)
    val storageAlerts = MutableStateFlow(true)
    val grid = MutableStateFlow(false)
    val checklistDismissed = MutableStateFlow(false)
    val askedNotifications = MutableStateFlow(false)
    val onboardedUsers = MutableStateFlow<Set<String>>(emptySet())
    var onboardMigrated = false; private set
    private val _seenInvites = MutableStateFlow<Set<String>?>(null)
    val seenInvites: StateFlow<Set<String>?> = _seenInvites

    init {
        runBlocking {
            val p = context.prefStore.data.first()
            theme.value = p[kTheme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System
            systemNotifications.value = p[kSysNotif] ?: true
            storageAlerts.value = p[kStorageAlerts] ?: true
            grid.value = p[kGrid] ?: false
            checklistDismissed.value = p[kChecklist] ?: false
            askedNotifications.value = p[kAskedNotif] ?: false
            _seenInvites.value = p[kSeenInvites]
            onboardedUsers.value = p[kOnboarded] ?: emptySet()
            onboardMigrated = p[kOnboardMigrated] ?: false
        }
    }

    private fun <T> save(flow: MutableStateFlow<T>, value: T, write: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        flow.value = value
        scope.launch { context.prefStore.edit { write(it) } }
    }

    fun setTheme(m: ThemeMode) = save(theme, m) { it[kTheme] = m.name }
    fun setSystemNotifications(v: Boolean) = save(systemNotifications, v) { it[kSysNotif] = v }
    fun setStorageAlerts(v: Boolean) = save(storageAlerts, v) { it[kStorageAlerts] = v }
    fun setGrid(v: Boolean) = save(grid, v) { it[kGrid] = v }
    fun dismissChecklist() = save(checklistDismissed, true) { it[kChecklist] = true }
    fun markAskedNotifications() = save(askedNotifications, true) { it[kAskedNotif] = true }
    fun markOnboarded(userId: String) = save(onboardedUsers, onboardedUsers.value + userId) { it[kOnboarded] = onboardedUsers.value + userId }
    /** Users who had the app before onboarding existed shouldn't see it. */
    fun migrateOnboarding(existingUserId: String?) {
        if (onboardMigrated) return
        onboardMigrated = true
        scope.launch { context.prefStore.edit { it[kOnboardMigrated] = true } }
        if (existingUserId != null) markOnboarded(existingUserId)
    }
    fun setSeenInvites(ids: Set<String>) {
        _seenInvites.value = ids
        scope.launch { context.prefStore.edit { it[kSeenInvites] = ids } }
    }
}
