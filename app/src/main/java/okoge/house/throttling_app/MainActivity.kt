package okoge.house.throttling_app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okoge.house.throttling_app.data.InstalledApp
import okoge.house.throttling_app.data.InstalledAppsProvider
import okoge.house.throttling_app.data.InstalledAppsProviderImpl
import okoge.house.throttling_app.data.AppDataRepository
import okoge.house.throttling_app.data.VpnMode
import okoge.house.throttling_app.data.appDataStore
import okoge.house.throttling_app.ui.AppListEvent
import okoge.house.throttling_app.ui.AppListScreen
import okoge.house.throttling_app.ui.AppListUiState
import okoge.house.throttling_app.ui.LicenseScreen
import okoge.house.throttling_app.ui.MainEvent
import okoge.house.throttling_app.ui.MainScreen
import okoge.house.throttling_app.ui.MainUiState
import okoge.house.throttling_app.ui.kbpsToKBps
import okoge.house.throttling_app.ui.sliderToKbps
import okoge.house.throttling_app.ui.theme.ThrottlingappTheme

@Serializable
data object MainRoute

@Serializable
data object AppListRoute

@Serializable
data object LicenseRoute

class MainActivity : ComponentActivity() {

    private val vpnStatusHolder get() = (application as ThrottlingApplication).vpnStatusHolder
    private val isVpnRunning get() = vpnStatusHolder.status is VpnStatus.Running

    // 権限ダイアログと対象アプリの読み込みの間は Service がまだ状態を書かないため、二度押しをここで防ぐ
    private var isStartRequested by mutableStateOf(false)

    private var selectedMode by mutableStateOf(VpnMode.Throttle)
    private var sliderKbps by mutableIntStateOf(sliderToKbps(0.5f))

    private lateinit var repository: AppDataRepository
    private lateinit var installedAppsProvider: InstalledAppsProvider

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Proceed to VPN permission flow regardless of notification permission result
        requestVpnPermission()
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        } else {
            isStartRequested = false
        }
    }

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = AppDataRepository(applicationContext.appDataStore)
        installedAppsProvider = InstalledAppsProviderImpl(applicationContext)
        setContent {
            ThrottlingappTheme {
                val backStack = remember { mutableStateListOf<Any>(MainRoute) }
                val targetApps by repository.targetApps.collectAsState(initial = emptySet())
                val listDetailStrategy = rememberListDetailSceneStrategy<Any>()
                val appListPane: @Composable (onBack: (() -> Unit)?) -> Unit = { onBack ->
                    val installedApps by produceState(initialValue = emptyList<InstalledApp>()) {
                        value = withContext(Dispatchers.IO) {
                            installedAppsProvider.listInstalledApps()
                        }
                    }
                    AppListScreen(
                        uiState = AppListUiState(targetApps = targetApps, installedApps = installedApps),
                        onEvent = { event ->
                            when (event) {
                                is AppListEvent.AddApp -> lifecycleScope.launch { repository.addApp(event.packageName) }
                                is AppListEvent.RemoveApp -> lifecycleScope.launch { repository.removeApp(event.packageName) }
                            }
                        },
                        onBack = onBack,
                    )
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.systemBars
                ) { innerPadding ->
                    NavDisplay(
                        modifier = Modifier.padding(innerPadding),
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        sceneStrategies = listOf(listDetailStrategy),
                        entryProvider = entryProvider {
                            // 2 ペインが入る幅では、アプリ一覧を開く前から右側に並べておく
                            entry<MainRoute>(
                                metadata = ListDetailSceneStrategy.listPane(
                                    detailPlaceholder = { appListPane(null) },
                                ),
                            ) {
                                val vpnStatus = vpnStatusHolder.status
                                MainScreen(
                                    uiState = MainUiState(
                                        vpnStatus = vpnStatus,
                                        isStartRequested = isStartRequested,
                                        mode = selectedMode,
                                        sliderKbps = sliderKbps,
                                        targetAppCount = targetApps.size,
                                    ),
                                    onEvent = { event ->
                                        when (event) {
                                            is MainEvent.ChangeMode -> {
                                                selectedMode = event.mode
                                                if (isVpnRunning) sendSpeed()
                                            }
                                            is MainEvent.DragSlider -> sliderKbps = event.kbps
                                            MainEvent.FinishSlider -> {
                                                if (isVpnRunning && selectedMode == VpnMode.Throttle) sendSpeed()
                                            }
                                            MainEvent.ToggleVpn -> {
                                                // 最後のコンポーズ時点の値ではなく、押された瞬間の状態で判定する
                                                val status = vpnStatusHolder.status
                                                when {
                                                    isStartRequested || status is VpnStatus.Starting -> Unit
                                                    status is VpnStatus.Running -> stopVpnService()
                                                    targetApps.isNotEmpty() -> {
                                                        isStartRequested = true
                                                        requestPermissionsAndStart()
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onNavigateToAppList = { backStack.add(AppListRoute) },
                                    onNavigateToLicenses = { backStack.add(LicenseRoute) },
                                )
                            }
                             entry<LicenseRoute> {
                                 LicenseScreen(
                                     onBack = { backStack.removeLastOrNull() },
                                     modifier = Modifier.padding(innerPadding),
                                 )
                             }
                            entry<AppListRoute>(metadata = ListDetailSceneStrategy.detailPane()) {
                                appListPane { backStack.removeLastOrNull() }
                            }
                        },
                    )
                }
            }
        }
    }

    // Speed value passed to Go layer (KB/s). -1=Block, 0=Unlimited, >0=Throttle
    private fun currentSpeedKBps(): Int = when (selectedMode) {
        VpnMode.Block -> -1
        VpnMode.Throttle -> kbpsToKBps(sliderKbps)
        VpnMode.Unlimited -> 0
    }

    private fun requestPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestVpnPermission()
        }
    }

    private fun requestVpnPermission() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        lifecycleScope.launch {
            val apps = ArrayList(repository.targetApps.first())
            val intent = Intent(this@MainActivity, ThrottleVpnService::class.java).apply {
                putExtra(ThrottleVpnService.EXTRA_SPEED_KBPS, currentSpeedKBps())
                putStringArrayListExtra(ThrottleVpnService.EXTRA_TARGET_APPS, apps)
            }
            startForegroundService(intent)
            isStartRequested = false
        }
    }

    private fun stopVpnService() {
        val intent = Intent(this, ThrottleVpnService::class.java).apply {
            action = ThrottleVpnService.ACTION_STOP
        }
        startService(intent)
    }

    private fun sendSpeed() {
        val intent = Intent(this, ThrottleVpnService::class.java).apply {
            action = ThrottleVpnService.ACTION_SET_SPEED
            putExtra(ThrottleVpnService.EXTRA_SPEED_KBPS, currentSpeedKBps())
        }
        startService(intent)
    }

}
