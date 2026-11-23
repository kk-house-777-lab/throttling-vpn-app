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

internal class TargetAppsDataStoreMigration(
    private val legacyFile: File,
) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = legacyFile.exists()

    override suspend fun migrate(currentData: Preferences): Preferences {
        // キーがないのは一度も編集していないということなので、デフォルトが効く状態のまま残す
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
        // DataStore は 1 ファイルに 1 インスタンスしか持てないので、cleanUp で消す前に scope を閉じて手放す
        val legacyStore = PreferenceDataStoreFactory.create(
            // 旧ファイルが壊れていても新しいストアを開けるよう、移すものなしとして扱う
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
