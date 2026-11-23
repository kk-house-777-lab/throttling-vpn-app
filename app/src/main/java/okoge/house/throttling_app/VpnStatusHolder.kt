package okoge.house.throttling_app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal sealed interface VpnStatus {
    data object Stopped : VpnStatus
    data object Starting : VpnStatus
    data class Running(val speedKBps: Int) : VpnStatus
    data class Failed(val reason: String) : VpnStatus
}

internal class VpnStatusHolder {
    var status: VpnStatus by mutableStateOf(VpnStatus.Stopped)
        internal set
}
