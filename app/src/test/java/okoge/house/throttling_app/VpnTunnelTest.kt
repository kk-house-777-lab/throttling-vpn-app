package okoge.house.throttling_app

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnTunnelTest {

    private class FakeEngine(private val startError: Exception? = null) : TunnelEngine {
        val calls = mutableListOf<String>()
        override fun start(fd: Int, speedKBps: Int) {
            calls += "start($fd, $speedKBps)"
            startError?.let { throw it }
        }
        override fun setSpeed(speedKBps: Int) {
            calls += "setSpeed($speedKBps)"
        }
        override fun stop() {
            calls += "stop"
        }
    }

    private val holder = VpnStatusHolder()

    @Test
    fun `TUNを作れてGoの開始も成功するとRunningになり指定した速度で始まる`() {
        val engine = FakeEngine()
        val tunnel = VpnTunnel(holder, engine)

        tunnel.start(speedKBps = 63) { 42 }

        assertEquals(VpnStatus.Running(63), holder.status)
        assertEquals(listOf("start(42, 63)"), engine.calls)
    }

    @Test
    fun `TUNを作っている間はStartingになっている`() {
        val tunnel = VpnTunnel(holder, FakeEngine())
        var statusWhileEstablishing: VpnStatus? = null

        tunnel.start(speedKBps = 63) {
            statusWhileEstablishing = holder.status
            42
        }

        assertEquals(VpnStatus.Starting, statusWhileEstablishing)
    }

    @Test
    fun `TUNを作れないとFailedになりGoは呼ばれない`() {
        val engine = FakeEngine()
        val tunnel = VpnTunnel(holder, engine)

        val result = tunnel.start(speedKBps = 63) { null }

        assertEquals(VpnStatus.Failed("VPN インターフェースを作成できませんでした"), holder.status)
        assertEquals(true, result.isFailure)
        assertEquals(emptyList<String>(), engine.calls)
    }

    @Test
    fun `TUNの作成で例外が出るとその理由でFailedになる`() {
        val tunnel = VpnTunnel(holder, FakeEngine())

        tunnel.start(speedKBps = 63) { throw IllegalStateException("not prepared") }

        assertEquals(VpnStatus.Failed("not prepared"), holder.status)
    }

    @Test
    fun `Goの開始が例外を投げるとその理由でFailedになる`() {
        val tunnel = VpnTunnel(holder, FakeEngine(startError = Exception("socketpair failed: EMFILE")))

        val result = tunnel.start(speedKBps = 63) { 42 }

        assertEquals(VpnStatus.Failed("socketpair failed: EMFILE"), holder.status)
        assertEquals(true, result.isFailure)
    }

    @Test
    fun `Running中にもう一度開始してもGoは二重に呼ばれない`() {
        val engine = FakeEngine()
        val tunnel = VpnTunnel(holder, engine)
        tunnel.start(speedKBps = 63) { 42 }

        tunnel.start(speedKBps = 10) { 43 }

        assertEquals(VpnStatus.Running(63), holder.status)
        assertEquals(listOf("start(42, 63)"), engine.calls)
    }

    @Test
    fun `Failedのあとはもう一度開始できる`() {
        val engine = FakeEngine()
        val tunnel = VpnTunnel(holder, engine)
        tunnel.start(speedKBps = 63) { null }

        tunnel.start(speedKBps = 63) { 42 }

        assertEquals(VpnStatus.Running(63), holder.status)
    }

    @Test
    fun `Running中に速度を変えるとRunningの速度も新しい値になる`() {
        val engine = FakeEngine()
        val tunnel = VpnTunnel(holder, engine)
        tunnel.start(speedKBps = 63) { 42 }

        tunnel.setSpeed(-1)

        assertEquals(VpnStatus.Running(-1), holder.status)
        assertEquals(listOf("start(42, 63)", "setSpeed(-1)"), engine.calls)
    }

    @Test
    fun `停止中に速度を変えてもStoppedのまま`() {
        val tunnel = VpnTunnel(holder, FakeEngine())

        tunnel.setSpeed(-1)

        assertEquals(VpnStatus.Stopped, holder.status)
    }

    @Test
    fun `Running中に停止するとStoppedになりGoが止まる`() {
        val engine = FakeEngine()
        val tunnel = VpnTunnel(holder, engine)
        tunnel.start(speedKBps = 63) { 42 }

        tunnel.stop()

        assertEquals(VpnStatus.Stopped, holder.status)
        assertEquals(listOf("start(42, 63)", "stop"), engine.calls)
    }

    @Test
    fun `開始に失敗したあとの後片付けで停止してもFailedの理由は残る`() {
        val tunnel = VpnTunnel(holder, FakeEngine())
        tunnel.start(speedKBps = 63) { null }

        tunnel.stop()

        assertEquals(VpnStatus.Failed("VPN インターフェースを作成できませんでした"), holder.status)
    }
}
