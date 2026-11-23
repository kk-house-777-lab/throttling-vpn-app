package okoge.house.throttling_app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppDataRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: AppDataRepository

    private val defaultApp = "okoge.house.throttling_app.testapp"

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFolder.root.resolve("app_data.preferences_pb") },
        )
        repository = AppDataRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    // ── initial state ──

    @Test
    fun initialState_returnsDefaultApps() = runTest {
        val apps = repository.targetApps.first()
        assertEquals(setOf(defaultApp), apps)
    }

    // ── addApp ──

    @Test
    fun addApp_addsPackageToSet() = runTest {
        repository.addApp("com.example.app")
        val apps = repository.targetApps.first()
        assertEquals(setOf(defaultApp, "com.example.app"), apps)
    }

    @Test
    fun addApp_duplicate_doesNotCreateDuplicate() = runTest {
        repository.addApp("com.example.app")
        repository.addApp("com.example.app")
        val apps = repository.targetApps.first()
        assertEquals(setOf(defaultApp, "com.example.app"), apps)
    }

    @Test
    fun addApp_multiplePackages_allPresent() = runTest {
        repository.addApp("com.example.a")
        repository.addApp("com.example.b")
        repository.addApp("com.example.c")
        val apps = repository.targetApps.first()
        assertEquals(
            setOf(defaultApp, "com.example.a", "com.example.b", "com.example.c"),
            apps,
        )
    }

    // ── removeApp ──

    @Test
    fun removeApp_removesPackageFromSet() = runTest {
        repository.addApp("com.example.a")
        repository.addApp("com.example.b")
        repository.removeApp("com.example.a")
        val apps = repository.targetApps.first()
        assertEquals(setOf(defaultApp, "com.example.b"), apps)
    }

    @Test
    fun removeApp_nonExistentPackage_isNoOp() = runTest {
        repository.addApp("com.example.app")
        repository.removeApp("com.example.nonexistent")
        val apps = repository.targetApps.first()
        assertEquals(setOf(defaultApp, "com.example.app"), apps)
    }

    @Test
    fun removeApp_onEmptyDataStore_isNoOp() = runTest {
        repository.removeApp("com.example.app")
        val apps = repository.targetApps.first()
        assertTrue(apps.isEmpty())
    }

    @Test
    fun removeApp_defaultApp_removesIt() = runTest {
        repository.addApp(defaultApp)
        repository.removeApp(defaultApp)
        val apps = repository.targetApps.first()
        assertTrue(apps.isEmpty())
    }

    // ── vpnMode ──

    @Test
    fun vpnMode_initialState_isThrottle() = runTest {
        assertEquals(VpnMode.Throttle, repository.vpnMode.first())
    }

    @Test
    fun setVpnMode_persistsMode() = runTest {
        repository.setVpnMode(VpnMode.Block)
        assertEquals(VpnMode.Block, repository.vpnMode.first())
    }

    @Test
    fun vpnMode_unknownStoredName_fallsBackToThrottle() = runTest {
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                this[VPN_MODE_KEY] = "Removed"
            }
        }
        assertEquals(VpnMode.Throttle, repository.vpnMode.first())
    }

    // ── throttleKbps ──

    @Test
    fun throttleKbps_initialState_isDefault() = runTest {
        assertEquals(500, repository.throttleKbps.first())
    }

    @Test
    fun setThrottleKbps_persistsValue() = runTest {
        repository.setThrottleKbps(120)
        assertEquals(120, repository.throttleKbps.first())
    }
}
