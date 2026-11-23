package okoge.house.throttling_app.ui

import androidx.appstate.transform.transform
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okoge.house.throttling_app.data.AppDataRepository
import okoge.house.throttling_app.data.InstalledApp
import okoge.house.throttling_app.data.InstalledAppsProvider

internal class AppListViewModel(
    private val repository: AppDataRepository,
    private val installedAppsProvider: InstalledAppsProvider,
) : ViewModel() {

    private var installedApps by mutableStateOf(emptyList<InstalledApp>())

    val uiState: State<AppListUiState> = transform(
        initialValue = AppListUiState(),
        scope = viewModelScope,
    ) {
        val targetApps by repository.targetApps.collectAsState(initial = emptySet())
        AppListUiState(targetApps = targetApps, installedApps = installedApps)
    }

    // 画面を開くたびに読み直す。アプリを起動したまま入れたり消したりしたアプリも一覧に反映するため
    fun loadInstalledApps() {
        viewModelScope.launch {
            installedApps = withContext(Dispatchers.IO) { installedAppsProvider.listInstalledApps() }
        }
    }

    fun onEvent(event: AppListEvent) {
        when (event) {
            is AppListEvent.AddApp -> viewModelScope.launch { repository.addApp(event.packageName) }
            is AppListEvent.RemoveApp -> viewModelScope.launch { repository.removeApp(event.packageName) }
        }
    }
}
