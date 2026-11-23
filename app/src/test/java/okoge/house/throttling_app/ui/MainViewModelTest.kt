package okoge.house.throttling_app.ui

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okoge.house.throttling_app.ThrottleVpnService
import okoge.house.throttling_app.VpnStatus
import okoge.house.throttling_app.VpnStatusHolder
import okoge.house.throttling_app.data.AppDataRepository
import okoge.house.throttling_app.data.VpnMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper

@RunWith(AndroidJUnit4::class)
class MainViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var app: Application
    private lateinit var holder: VpnStatusHolder
    private lateinit var repository: AppDataRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        holder = VpnStatusHolder()
        repository = AppDataRepository(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { tempFolder.root.resolve("app_data.preferences_pb") },
            ),
        )
        viewModel = MainViewModel(app, holder, repository)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    // transform は別スレッドで導出するので、条件を満たすまで待つ
    private fun awaitState(condition: (MainUiState) -> Boolean): MainUiState {
        repeat(100) {
            ShadowLooper.idleMainLooper()
            viewModel.uiState.value?.let { if (condition(it)) return it }
            Thread.sleep(20)
        }
        error("UiState が条件を満たさなかった: ${viewModel.uiState.value}")
    }

    @Test
    fun `保存済みのモードと速度がUiStateに出る`() {
        runBlocking {
            repository.setVpnMode(VpnMode.Block)
            repository.setThrottleKbps(120)
        }

        val state = awaitState { it.mode == VpnMode.Block }

        assertEquals(120, state.sliderKbps)
    }

    @Test
    fun `VpnStatusHolderの状態がUiStateに反映される`() {
        awaitState { true }

        holder.status = VpnStatus.Starting
        assertEquals(true, awaitState { it.vpnStatus is VpnStatus.Starting }.isTransitioning)

        holder.status = VpnStatus.Running(speedKBps = 63)
        assertEquals(true, awaitState { it.vpnStatus is VpnStatus.Running }.isVpnRunning)

        holder.status = VpnStatus.Failed(reason = "establish failed")
        val failed = awaitState { it.vpnStatus is VpnStatus.Failed }
        assertEquals(false, failed.isVpnRunning)
        assertEquals(false, failed.isTransitioning)
    }

    @Test
    fun `モードを切り替えるとDataStoreに保存される`() {
        awaitState { true }

        viewModel.onEvent(MainEvent.ChangeMode(VpnMode.Unlimited)) {}

        assertEquals(VpnMode.Unlimited, awaitState { it.mode == VpnMode.Unlimited }.mode)
        assertEquals(VpnMode.Unlimited, runBlocking { repository.vpnMode.first() })
    }

    @Test
    fun `ドラッグ中の速度は画面にだけ出て離したときにDataStoreへ保存される`() {
        awaitState { true }

        viewModel.onEvent(MainEvent.DragSlider(kbps = 2000)) {}

        assertEquals(2000, awaitState { it.sliderKbps == 2000 }.sliderKbps)
        assertEquals(500, runBlocking { repository.throttleKbps.first() })

        viewModel.onEvent(MainEvent.FinishSlider) {}

        awaitState { true }
        repeat(100) {
            if (runBlocking { repository.throttleKbps.first() } == 2000) return
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        error("速度が保存されなかった")
    }

    @Test
    fun `停止中に開始を押すと権限の確認を頼み切り替え中になる`() {
        awaitState { it.targetAppCount > 0 }
        var permissionRequested = 0

        viewModel.onEvent(MainEvent.ToggleVpn) { permissionRequested++ }

        assertEquals(1, permissionRequested)
        assertEquals(true, awaitState { it.isStartRequested }.isTransitioning)
    }

    @Test
    fun `切り替え中に開始をもう一度押しても権限の確認は頼まない`() {
        awaitState { it.targetAppCount > 0 }
        var permissionRequested = 0

        viewModel.onEvent(MainEvent.ToggleVpn) { permissionRequested++ }
        viewModel.onEvent(MainEvent.ToggleVpn) { permissionRequested++ }

        assertEquals(1, permissionRequested)
    }

    @Test
    fun `動作中に押すとServiceへ停止を送る`() {
        awaitState { true }
        holder.status = VpnStatus.Running(speedKBps = 63)

        viewModel.onEvent(MainEvent.ToggleVpn) { error("権限の確認は不要") }

        val intent = shadowOf(app).nextStartedService
        assertEquals(ThrottleVpnService.ACTION_STOP, intent.action)
    }

    @Test
    fun `権限が通ると保存済みの速度と対象アプリでServiceを開始し受付を解く`() {
        runBlocking { repository.setThrottleKbps(800) }
        awaitState { it.sliderKbps == 800 && it.targetAppCount > 0 }
        viewModel.onEvent(MainEvent.ToggleVpn) {}

        viewModel.onStartPermissionsGranted()

        awaitState { !it.isStartRequested }
        val intent = shadowOf(app).nextStartedService
        assertEquals(100, intent.getIntExtra(ThrottleVpnService.EXTRA_SPEED_KBPS, -99))
        assertEquals(
            listOf("okoge.house.throttling_app.testapp"),
            intent.getStringArrayListExtra(ThrottleVpnService.EXTRA_TARGET_APPS),
        )
    }

    @Test
    fun `権限が拒否されると開始の受付を解く`() {
        awaitState { it.targetAppCount > 0 }
        viewModel.onEvent(MainEvent.ToggleVpn) {}

        viewModel.onStartPermissionsDenied()

        assertEquals(false, awaitState { !it.isStartRequested }.isTransitioning)
        assertNull(shadowOf(app).nextStartedService)
    }
}
