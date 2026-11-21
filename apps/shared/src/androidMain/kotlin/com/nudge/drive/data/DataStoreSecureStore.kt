package com.nudge.drive.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.sessionStore by preferencesDataStore("nudge_session")

/** Session storage on Android (unchanged on-disk format, so an app update keeps people signed in). */
class DataStoreSecureStore(private val context: Context) : SecureStore {
    override suspend fun read(): Map<String, String> =
        context.sessionStore.data.first().asMap().entries.mapNotNull { (k, v) -> (v as? String)?.let { k.name to it } }.toMap()

    override suspend fun write(values: Map<String, String?>) {
        context.sessionStore.edit { p ->
            values.forEach { (k, v) -> val key = stringPreferencesKey(k); if (v == null) p.remove(key) else p[key] = v }
        }
    }

    override suspend fun clear() { context.sessionStore.edit { it.clear() } }
}
