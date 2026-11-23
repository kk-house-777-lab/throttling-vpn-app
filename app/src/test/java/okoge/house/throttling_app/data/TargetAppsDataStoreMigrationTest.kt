package okoge.house.throttling_app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TargetAppsDataStoreMigrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val legacyFile: File get() = tempFolder.root.resolve("target_apps.preferences_pb")
    private val newFile: File get() = tempFolder.root.resolve("app_data.preferences_pb")

    @After
    fun tearDown() {
        scope.cancel()
    }

    private suspend fun writeLegacy(block: suspend (DataStore<Preferences>) -> Unit) {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { legacyFile },
        )
        block(store)
        job.cancelAndJoin()
    }

    private fun newStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(TargetAppsDataStoreMigration(legacyFile)),
        scope = scope,
        produceFile = { newFile },
    )

    @Test
    fun `旧ファイルに対象アプリがあると新しいDataStoreへ同じ集合が移り旧ファイルは消える`() = runTest {
        writeLegacy { store -> store.edit { it[TARGET_APPS_KEY] = setOf("com.example.a", "com.example.b") } }

        val prefs = newStore().data.first()

        assertEquals(setOf("com.example.a", "com.example.b"), prefs[TARGET_APPS_KEY])
        assertFalse(legacyFile.exists())
    }

    @Test
    fun `旧ファイルで全アプリを削除していた場合は空集合のまま移りデフォルトに戻らず旧ファイルは消える`() = runTest {
        writeLegacy { store -> store.edit { it[TARGET_APPS_KEY] = emptySet() } }

        val prefs = newStore().data.first()

        assertEquals(emptySet<String>(), prefs[TARGET_APPS_KEY])
        assertFalse(legacyFile.exists())
    }

    @Test
    fun `旧ファイルに対象アプリのキーがなければ新しい側にも書かずデフォルトが効く状態を保つ`() = runTest {
        writeLegacy { store -> store.edit { it[stringPreferencesKey("unrelated")] = "x" } }

        val prefs = newStore().data.first()

        assertNull(prefs[TARGET_APPS_KEY])
        assertFalse(legacyFile.exists())
    }

    @Test
    fun `旧ファイルがなければ移行せず新しいDataStoreは空のまま`() = runTest {
        val prefs = newStore().data.first()

        assertNull(prefs[TARGET_APPS_KEY])
    }

    @Test
    fun `移行後のDataStoreをAppDataRepositoryから読むと旧ファイルの対象アプリが返る`() = runTest {
        writeLegacy { store -> store.edit { it[TARGET_APPS_KEY] = setOf("com.example.a") } }

        val repository = AppDataRepository(newStore())

        assertEquals(setOf("com.example.a"), repository.targetApps.first())
    }

    @Test
    fun `旧ファイルが壊れていても新しいDataStoreは開けて対象アプリは未設定のまま旧ファイルは消える`() = runTest {
        legacyFile.writeBytes(byteArrayOf(0x7F, 0x00, 0x13, 0x37))

        val prefs = newStore().data.first()

        assertNull(prefs[TARGET_APPS_KEY])
        assertFalse(legacyFile.exists())
    }
}
