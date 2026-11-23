package okoge.house.throttling_app.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okoge.house.throttling_app.data.AppDataRepository
import okoge.house.throttling_app.data.InstalledApp
import okoge.house.throttling_app.data.InstalledAppsProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper

@RunWith(AndroidJUnit4::class)
class AppListViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var viewModel: AppListViewModel

    private val installed = listOf(InstalledApp("com.example.a", "App A"))

    @Before
    fun setUp() {
        val repository = AppDataRepository(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { tempFolder.root.resolve("app_data.preferences_pb") },
            ),
        )
        val provider = object : InstalledAppsProvider {
            override fun listInstalledApps() = installed
        }
        viewModel = AppListViewModel(repository, provider)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun awaitState(condition: (AppListUiState) -> Boolean): AppListUiState {
        repeat(100) {
            ShadowLooper.idleMainLooper()
            viewModel.uiState.value.let { if (condition(it)) return it }
            Thread.sleep(20)
        }
        error("UiState が条件を満たさなかった: ${viewModel.uiState.value}")
    }

    @Test
    fun `画面を開いて一覧を読み込むとインストール済みアプリがUiStateに出る`() {
        assertEquals(emptyList<InstalledApp>(), awaitState { true }.installedApps)

        viewModel.loadInstalledApps()

        assertEquals(installed, awaitState { it.installedApps.isNotEmpty() }.installedApps)
    }

    @Test
    fun `アプリを追加すると対象アプリに入り削除すると外れる`() {
        awaitState { it.targetApps.isNotEmpty() }

        viewModel.onEvent(AppListEvent.AddApp("com.example.a"))
        val added = awaitState { "com.example.a" in it.targetApps }
        assertEquals(setOf("okoge.house.throttling_app.testapp", "com.example.a"), added.targetApps)

        viewModel.onEvent(AppListEvent.RemoveApp("com.example.a"))
        val removed = awaitState { "com.example.a" !in it.targetApps }
        assertEquals(setOf("okoge.house.throttling_app.testapp"), removed.targetApps)
    }
}
