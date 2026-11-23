package okoge.house.throttling_app.ui

import okoge.house.throttling_app.VpnStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MainUiStateTest {

    private fun state(vpnStatus: VpnStatus, isStartRequested: Boolean = false) =
        MainUiState(vpnStatus = vpnStatus, isStartRequested = isStartRequested, sliderKbps = 500)

    @Test
    fun `Serviceが Starting を書いた状態は切り替え中で動作中ではない`() {
        val s = state(VpnStatus.Starting)
        assertEquals(true, s.isTransitioning)
        assertEquals(false, s.isVpnRunning)
    }

    @Test
    fun `停止中でも開始を受け付けた直後は切り替え中になる`() {
        val s = state(VpnStatus.Stopped, isStartRequested = true)
        assertEquals(true, s.isTransitioning)
        assertEquals(false, s.isVpnRunning)
    }

    @Test
    fun `Running は動作中で切り替え中ではない`() {
        val s = state(VpnStatus.Running(speedKBps = 63))
        assertEquals(false, s.isTransitioning)
        assertEquals(true, s.isVpnRunning)
    }

    @Test
    fun `Failed は動作中でも切り替え中でもない`() {
        val s = state(VpnStatus.Failed(reason = "establish failed"))
        assertEquals(false, s.isTransitioning)
        assertEquals(false, s.isVpnRunning)
    }
}
