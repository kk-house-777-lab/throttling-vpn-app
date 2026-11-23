package okoge.house.throttling_app

internal class VpnTunnel(
    private val holder: VpnStatusHolder,
    private val engine: TunnelEngine,
) {

    fun start(speedKBps: Int, establishTun: () -> Int?): Result<Unit> {
        val current = holder.status
        if (current is VpnStatus.Starting || current is VpnStatus.Running) return Result.success(Unit)

        holder.status = VpnStatus.Starting
        val result = runCatching {
            val fd = checkNotNull(establishTun()) { "VPN インターフェースを作成できませんでした" }
            engine.start(fd, speedKBps)
        }
        holder.status = result.fold(
            onSuccess = { VpnStatus.Running(speedKBps) },
            onFailure = { VpnStatus.Failed(it.message ?: it.javaClass.simpleName) },
        )
        return result
    }

    fun setSpeed(speedKBps: Int) {
        engine.setSpeed(speedKBps)
        if (holder.status is VpnStatus.Running) holder.status = VpnStatus.Running(speedKBps)
    }

    fun stop() {
        engine.stop()
        // 開始に失敗したあとの後片付けでも呼ばれるので、失敗の理由を Stopped で上書きしない
        if (holder.status !is VpnStatus.Failed) holder.status = VpnStatus.Stopped
    }
}
