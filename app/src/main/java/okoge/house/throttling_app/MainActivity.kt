package okoge.house.throttling_app

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import okoge.house.throttling_app.data.AppDataRepository
import okoge.house.throttling_app.data.InstalledAppsProviderImpl
import okoge.house.throttling_app.data.appDataStore
import okoge.house.throttling_app.ui.AppListScreen
import okoge.house.throttling_app.ui.AppListViewModel
import okoge.house.throttling_app.ui.LicenseScreen
import okoge.house.throttling_app.ui.MainScreen
import okoge.house.throttling_app.ui.MainViewModel
import okoge.house.throttling_app.ui.theme.ThrottlingappTheme

@Serializable
data object MainRoute : NavKey

@Serializable
data object AppListRoute : NavKey

@Serializable
data object LicenseRoute : NavKey

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as ThrottlingApplication
                MainViewModel(app, app.vpnStatusHolder, AppDataRepository(app.appDataStore))
            }
        }
    }

    private val appListViewModel: AppListViewModel by viewModels {
        viewModelFactory {
            initializer {
                AppListViewModel(AppDataRepository(application.appDataStore), InstalledAppsProviderImpl(application))
            }
        }
    }

    // 権限ダイアログは Activity からしか出せないので、権限の流れだけはここに残す
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // 通知を拒否されても VPN は使えるので、結果に関わらず VPN の許可へ進む
        requestVpnPermission()
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            mainViewModel.onStartPermissionsGranted()
        } else {
            mainViewModel.onStartPermissionsDenied()
        }
    }

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // transform は作られた直後から別スレッドで State に書き込む。描画中に作ると、その書き込みが
        // 描画側のスナップショットの初期値で上書きされて画面が更新されないので、setContent より前に作る
        val mainViewModel = mainViewModel
        val appListViewModel = appListViewModel
        setContent {
            ThrottlingappTheme {
                val backStack = rememberNavBackStack(MainRoute)
                val paneDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
                val isTwoPane = paneDirective.maxHorizontalPartitions > 1
                val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = paneDirective)
                val appListPane: @Composable (onBack: (() -> Unit)?) -> Unit = { onBack ->
                    AppListScreen(
                        uiState = appListViewModel.uiState.value,
                        onEvent = appListViewModel::onEvent,
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
                                val uiState = mainViewModel.uiState.value
                                if (uiState != null) {
                                    MainScreen(
                                        uiState = uiState,
                                        onEvent = { mainViewModel.onEvent(it, ::requestPermissionsAndStart) },
                                        onNavigateToAppList = if (isTwoPane) null else ({ backStack.add(AppListRoute) }),
                                        onNavigateToLicenses = { backStack.add(LicenseRoute) },
                                    )
                                }
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
            mainViewModel.onStartPermissionsGranted()
        }
    }
}
