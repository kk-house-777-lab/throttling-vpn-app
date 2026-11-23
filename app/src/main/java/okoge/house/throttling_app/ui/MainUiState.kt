package okoge.house.throttling_app.ui

import okoge.house.throttling_app.VpnStatus
import okoge.house.throttling_app.data.VpnMode

internal data class MainUiState(
    val vpnStatus: VpnStatus = VpnStatus.Stopped,
    // 権限ダイアログの間は Service がまだ Starting を書かないので、開始の受付を別に持つ
    val isStartRequested: Boolean = false,
    val mode: VpnMode = VpnMode.Throttle,
    val sliderKbps: Int,
    val targetAppCount: Int = 0,
) {
    val isVpnRunning: Boolean get() = vpnStatus is VpnStatus.Running
    val isTransitioning: Boolean get() = isStartRequested || vpnStatus is VpnStatus.Starting
}

internal sealed interface MainEvent {
    data class ChangeMode(val mode: VpnMode) : MainEvent
    data class DragSlider(val kbps: Int) : MainEvent
    data object FinishSlider : MainEvent
    data object ToggleVpn : MainEvent
}
