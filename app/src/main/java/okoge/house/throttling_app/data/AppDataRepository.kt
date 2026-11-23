package okoge.house.throttling_app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okoge.house.throttling_app.BuildConfig

internal val Context.appDataStore by preferencesDataStore(
    name = "app_data",
    produceMigrations = { context ->
        listOf(TargetAppsDataStoreMigration(context.preferencesDataStoreFile(TargetAppsDataStoreMigration.LEGACY_NAME)))
    },
)

internal val TARGET_APPS_KEY = stringSetPreferencesKey("target_app_ids")
internal val VPN_MODE_KEY = stringPreferencesKey("vpn_mode")
private val THROTTLE_KBPS_KEY = intPreferencesKey("throttle_kbps")

const val DEFAULT_THROTTLE_KBPS = 500

class AppDataRepository(private val dataStore: DataStore<Preferences>) {

    private val defaultApps: Set<String> =
        if (BuildConfig.DEBUG) setOf("okoge.house.throttling_app.testapp") else emptySet()

    val targetApps: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[TARGET_APPS_KEY] ?: defaultApps
    }

    // An unknown stored name (e.g. after renaming an enum entry) falls back to the default.
    val vpnMode: Flow<VpnMode> = dataStore.data.map { prefs ->
        prefs[VPN_MODE_KEY]?.let { name -> VpnMode.entries.firstOrNull { it.name == name } }
            ?: VpnMode.Throttle
    }

    val throttleKbps: Flow<Int> = dataStore.data.map { prefs ->
        prefs[THROTTLE_KBPS_KEY] ?: DEFAULT_THROTTLE_KBPS
    }

    suspend fun addApp(packageName: String) {
        dataStore.edit { prefs ->
            val current = prefs[TARGET_APPS_KEY] ?: defaultApps
            prefs[TARGET_APPS_KEY] = current + packageName
        }
    }

    suspend fun removeApp(packageName: String) {
        dataStore.edit { prefs ->
            val current = prefs[TARGET_APPS_KEY] ?: emptySet()
            prefs[TARGET_APPS_KEY] = current - packageName
        }
    }

    suspend fun setVpnMode(mode: VpnMode) {
        dataStore.edit { it[VPN_MODE_KEY] = mode.name }
    }

    suspend fun setThrottleKbps(kbps: Int) {
        dataStore.edit { it[THROTTLE_KBPS_KEY] = kbps }
    }
}
