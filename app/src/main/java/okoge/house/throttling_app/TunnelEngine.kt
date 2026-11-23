package okoge.house.throttling_app

import tun2sockslib.Tun2sockslib

// Go のネイティブライブラリは JVM の単体テストで読み込めないため、差し替えられるようにする
internal interface TunnelEngine {
    fun start(fd: Int, speedKBps: Int)
    fun setSpeed(speedKBps: Int)
    fun stop()
}

internal object GoTunnelEngine : TunnelEngine {
    override fun start(fd: Int, speedKBps: Int) = Tun2sockslib.start(fd.toLong(), speedKBps.toLong())
    override fun setSpeed(speedKBps: Int) = Tun2sockslib.setSpeed(speedKBps.toLong())
    override fun stop() = Tun2sockslib.stop()
}
