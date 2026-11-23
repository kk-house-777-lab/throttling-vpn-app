package okoge.house.throttling_app

import android.app.Application
import okoge.house.throttling_app.data.AppDataRepository
import okoge.house.throttling_app.data.appDataStore

class ThrottlingApplication : Application() {
    // Activity と VpnService の両方から同じ状態を読むため、プロセスに 1 つだけ持つ
    internal val vpnStatusHolder = VpnStatusHolder()

    internal val appDataRepository by lazy { AppDataRepository(appDataStore) }
}
