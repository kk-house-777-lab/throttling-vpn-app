package okoge.house.throttling_app.data

import androidx.datastore.core.DataMigration
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Moves the target app list from the old per-feature DataStore ("target_apps")
 * into the app-wide one, then deletes the old file.
 */
internal class TargetAppsDataStoreMigration(
    private val legacyFile: File,
) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = legacyFile.exists()

    override suspend fun migrate(currentData: Preferences): Preferences {
        // An absent key means the user never edited the list, so the default must keep applying.
        val legacyApps = readLegacy()[TARGET_APPS_KEY] ?: return currentData
        if (currentData[TARGET_APPS_KEY] != null) return currentData
        return currentData.toMutablePreferences().apply {
            this[TARGET_APPS_KEY] = legacyApps
        }.toPreferences()
    }

    override suspend fun cleanUp() {
        legacyFile.delete()
    }

    private suspend fun readLegacy(): Preferences {
        val job = SupervisorJob()
        // DataStore allows one active instance per file; cancelling the scope releases it before cleanUp deletes the file.
        val legacyStore = PreferenceDataStoreFactory.create(
            // A corrupted legacy file must not block the app-wide store from opening; treat it as having nothing to migrate.
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { legacyFile },
        )
        return try {
            legacyStore.data.first()
        } finally {
            job.cancelAndJoin()
        }
    }

    companion object {
        const val LEGACY_NAME = "target_apps"
    }
}
