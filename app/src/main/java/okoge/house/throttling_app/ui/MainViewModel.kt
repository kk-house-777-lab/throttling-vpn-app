package okoge.house.throttling_app.ui

import android.app.Application
import android.content.Intent
import androidx.appstate.transform.transform
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okoge.house.throttling_app.ThrottleVpnService
import okoge.house.throttling_app.VpnStatus
import okoge.house.throttling_app.VpnStatusHolder
import okoge.house.throttling_app.data.AppDataRepository
import okoge.house.throttling_app.data.VpnMode

internal class MainViewModel(
    private val application: Application,
    private val vpnStatusHolder: VpnStatusHolder,
    private val repository: AppDataRepository,
) : ViewModel() {

    private var draggingKbps by mutableStateOf<Int?>(null)

    // 権限ダイアログの間は Service がまだ Starting を書かないので、開始の受付をここで持つ
    private var isStartRequested by mutableStateOf(false)

    // DataStore を読み終えるまでは null。保存済みの速度と違う値を一瞬でも見せたり送ったりしないため
    val uiState: State<MainUiState?> = transform(
        initialValue = null,
        scope = viewModelScope,
    ) {
        val mode by repository.vpnMode.collectAsState(initial = null)
        val savedKbps by repository.throttleKbps.collectAsState(initial = null)
        val targetApps by repository.targetApps.collectAsState(initial = null)
        val m = mode
        val k = savedKbps
        val apps = targetApps
        if (m == null || k == null || apps == null) {
            null
        } else {
            MainUiState(
                vpnStatus = vpnStatusHolder.status,
                isStartRequested = isStartRequested,
                mode = m,
                sliderKbps = draggingKbps ?: k,
                targetAppCount = apps.size,
            )
        }
    }

    fun onEvent(event: MainEvent, requestStartPermissions: () -> Unit) {
        when (event) {
            is MainEvent.ChangeMode -> viewModelScope.launch {
                repository.setVpnMode(event.mode)
                if (vpnStatusHolder.status is VpnStatus.Running) sendSpeed()
            }
            is MainEvent.DragSlider -> draggingKbps = event.kbps
            MainEvent.FinishSlider -> {
                val kbps = draggingKbps ?: return
                viewModelScope.launch {
                    repository.setThrottleKbps(kbps)
                    draggingKbps = null
                    if (vpnStatusHolder.status is VpnStatus.Running && repository.vpnMode.first() == VpnMode.Throttle) {
                        sendSpeed()
                    }
                }
            }
            MainEvent.ToggleVpn -> {
                // 最後に導出した UiState ではなく、押された瞬間の状態で判定する
                val status = vpnStatusHolder.status
                when {
                    isStartRequested || status is VpnStatus.Starting -> Unit
                    status is VpnStatus.Running -> stopVpnService()
                    (uiState.value?.targetAppCount ?: 0) > 0 -> {
                        isStartRequested = true
                        requestStartPermissions()
                    }
                }
            }
        }
    }

    fun onStartPermissionsGranted() {
        viewModelScope.launch {
            val intent = Intent(application, ThrottleVpnService::class.java).apply {
                putExtra(ThrottleVpnService.EXTRA_SPEED_KBPS, currentSpeedKBps())
                putStringArrayListExtra(ThrottleVpnService.EXTRA_TARGET_APPS, ArrayList(repository.targetApps.first()))
            }
            application.startForegroundService(intent)
            isStartRequested = false
        }
    }

    fun onStartPermissionsDenied() {
        isStartRequested = false
    }

    private fun stopVpnService() {
        application.startService(
            Intent(application, ThrottleVpnService::class.java).setAction(ThrottleVpnService.ACTION_STOP),
        )
    }

    private suspend fun sendSpeed() {
        application.startService(
            Intent(application, ThrottleVpnService::class.java)
                .setAction(ThrottleVpnService.ACTION_SET_SPEED)
                .putExtra(ThrottleVpnService.EXTRA_SPEED_KBPS, currentSpeedKBps()),
        )
    }

    // Go 層に渡す速度（KB/s）。-1 がブロック、0 が無制限
    private suspend fun currentSpeedKBps(): Int = when (repository.vpnMode.first()) {
        VpnMode.Block -> -1
        VpnMode.Throttle -> kbpsToKBps(repository.throttleKbps.first())
        VpnMode.Unlimited -> 0
    }
}
