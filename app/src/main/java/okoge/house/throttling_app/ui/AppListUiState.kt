package okoge.house.throttling_app.ui

import okoge.house.throttling_app.data.InstalledApp

internal data class AppListUiState(
    val targetApps: Set<String> = emptySet(),
    val installedApps: List<InstalledApp> = emptyList(),
    val pickerQuery: String = "",
)

internal sealed interface AppListEvent {
    data class AddApp(val packageName: String) : AppListEvent
    data class RemoveApp(val packageName: String) : AppListEvent
    data class ChangePickerQuery(val query: String) : AppListEvent
}
