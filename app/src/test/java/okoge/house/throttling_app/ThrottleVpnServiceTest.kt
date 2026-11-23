package okoge.house.throttling_app

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ServiceController

@RunWith(AndroidJUnit4::class)
class ThrottleVpnServiceTest {

    private class RecordingEngine : TunnelEngine {
        val calls = mutableListOf<String>()
        override fun start(fd: Int, speedKBps: Int) {
            calls += "start($fd, $speedKBps)"
        }
        override fun setSpeed(speedKBps: Int) {
            calls += "setSpeed($speedKBps)"
        }
        override fun stop() {
            calls += "stop"
        }
    }

    private val app = ApplicationProvider.getApplicationContext<ThrottlingApplication>()
    private val holder get() = app.vpnStatusHolder
    private val engine = RecordingEngine()
    private lateinit var controller: ServiceController<ThrottleVpnService>

    @Before
    fun setUp() {
        controller = Robolectric.buildService(ThrottleVpnService::class.java)
        controller.get().tunnelEngine = engine
        controller.create()
        holder.status = VpnStatus.Running(63)
    }

    private fun send(action: String, speedKBps: Int? = null) {
        val intent = Intent(app, ThrottleVpnService::class.java).apply {
            this.action = action
            speedKBps?.let { putExtra(ThrottleVpnService.EXTRA_SPEED_KBPS, it) }
        }
        controller.withIntent(intent).startCommand(0, 1)
    }

    @Test
    fun `動作中に速度変更を受けるとGoの速度を変えて状態もRunningの新しい速度になる`() {
        send(ThrottleVpnService.ACTION_SET_SPEED, speedKBps = 13)

        assertEquals(VpnStatus.Running(13), holder.status)
        assertEquals(listOf("setSpeed(13)"), engine.calls)
    }

    @Test
    fun `停止を受けるとGoを止めて状態はStoppedになる`() {
        send(ThrottleVpnService.ACTION_STOP)

        assertEquals(VpnStatus.Stopped, holder.status)
        assertEquals("stop", engine.calls.first())
    }

    @Test
    fun `Serviceが破棄されると状態はStoppedになる`() {
        controller.destroy()

        assertEquals(VpnStatus.Stopped, holder.status)
    }
}
