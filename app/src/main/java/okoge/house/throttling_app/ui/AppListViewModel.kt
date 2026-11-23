package okoge.house.throttling_app.ui

import androidx.appstate.transform.transform
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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

    private var pickerQuery by mutableStateOf("")

    val uiState: State<AppListUiState> = transform(
        initialValue = AppListUiState(),
        scope = viewModelScope,
    ) {
        val targetApps by repository.targetApps.collectAsState(initial = emptySet())
        val installedApps by produceState(initialValue = emptyList<InstalledApp>()) {
            value = withContext(Dispatchers.IO) { installedAppsProvider.listInstalledApps() }
        }
        AppListUiState(
            targetApps = targetApps,
            installedApps = installedApps,
            pickerQuery = pickerQuery,
        )
    }

    fun onEvent(event: AppListEvent) {
        when (event) {
            is AppListEvent.AddApp -> viewModelScope.launch { repository.addApp(event.packageName) }
            is AppListEvent.RemoveApp -> viewModelScope.launch { repository.removeApp(event.packageName) }
            is AppListEvent.ChangePickerQuery -> pickerQuery = event.query
        }
    }
}
