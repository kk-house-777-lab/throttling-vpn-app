package okoge.house.throttling_app.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.ln
import okoge.house.throttling_app.data.VpnMode
import kotlin.math.exp
import kotlin.math.roundToInt

// 大画面でボタンやスライダーが横に伸びきらないようにする
internal val ContentMaxWidth = 600.dp

@Composable
internal fun MainScreen(
    uiState: MainUiState,
    onEvent: (MainEvent) -> Unit,
    // 2 ペイン表示でアプリ一覧が常に横にあるときは null を渡してボタンを隠す
    onNavigateToAppList: (() -> Unit)?,
    onNavigateToLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = ContentMaxWidth)
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "Throttle VPN",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when {
                !uiState.isVpnRunning -> "VPN is OFF"
                uiState.mode == VpnMode.Block -> "VPN is ON — BLOCKED"
                uiState.mode == VpnMode.Unlimited -> "VPN is ON — Unlimited"
                else -> "VPN is ON — ${formatKbps(uiState.sliderKbps)}"
            },
            style = MaterialTheme.typography.titleLarge,
            color = if (uiState.isVpnRunning) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Target: ${uiState.targetAppCount} apps")
        if (onNavigateToAppList != null) {
            TextButton(onClick = onNavigateToAppList) {
                Text("Manage →")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Mode selector
        Text(
            text = "Mode",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeButton("Block", uiState.mode == VpnMode.Block, { onEvent(MainEvent.ChangeMode(VpnMode.Block)) }, Modifier.weight(1f))
            ModeButton("Throttle", uiState.mode == VpnMode.Throttle, { onEvent(MainEvent.ChangeMode(VpnMode.Throttle)) }, Modifier.weight(1f))
            ModeButton("Unlimited", uiState.mode == VpnMode.Unlimited, { onEvent(MainEvent.ChangeMode(VpnMode.Unlimited)) }, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        val isThrottleMode = uiState.mode == VpnMode.Throttle
        val kbps = uiState.sliderKbps

        Text(
            text = formatKbps(kbps),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (isThrottleMode) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
        )

        Text(
            text = networkGeneration(kbps),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = kbpsToSlider(uiState.sliderKbps),
            onValueChange = { onEvent(MainEvent.DragSlider(sliderToKbps(it))) },
            onValueChangeFinished = { onEvent(MainEvent.FinishSlider) },
            valueRange = 0f..1f,
            enabled = isThrottleMode,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("50 kbps", style = MaterialTheme.typography.bodySmall)
            Text("5 Mbps", style = MaterialTheme.typography.bodySmall)
        }

        Text(
            text = "Actual speed may vary depending on conditions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        val canStart = uiState.isVpnRunning || uiState.targetAppCount > 0
        Button(
            onClick = { onEvent(MainEvent.ToggleVpn) },
            enabled = canStart && !uiState.isTransitioning,
            modifier = Modifier.fillMaxWidth(),
            colors = if (uiState.isVpnRunning) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.buttonColors()
            },
        ) {
            if (uiState.isTransitioning) {
                CircularProgressIndicator()
            } else {
                Text(
                    text = if (uiState.isVpnRunning) "Stop VPN" else "Start VPN",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (!canStart) {
                "Add target apps first to start VPN."
            } else {
                "VPN stops automatically when the app is closed."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (!canStart) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

         Spacer(modifier = Modifier.height(16.dp))
         TextButton(onClick = onNavigateToLicenses) {
             Text("Open Source Licenses")
         }
    }
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 2 ペイン表示の狭いペインでも「Unlimited」が折り返さないよう、左右の余白を詰める
    val contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    if (selected) {
        Button(onClick = onClick, modifier = modifier, contentPadding = contentPadding) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = contentPadding) { Text(label) }
    }
}

// Logarithmic scale mapping: Slider 0.0–1.0 → 50–5000 kbps.
// Low speeds (50–384 kbps) get finer control, high speeds (1–5 Mbps) coarser.

private const val MIN_KBPS = 50f     // ~2G
private const val MAX_KBPS = 5000f   // ~3G/HSPA+
private val LN_MIN = ln(MIN_KBPS)
private val LN_MAX = ln(MAX_KBPS)

/** Slider value (0..1) → kbps */
fun sliderToKbps(slider: Float): Int {
    val kbps = exp(LN_MIN + slider * (LN_MAX - LN_MIN))
    return kbps.roundToInt().coerceIn(MIN_KBPS.toInt(), MAX_KBPS.toInt())
}

/** kbps → Slider value (0..1) */
fun kbpsToSlider(kbps: Int): Float {
    return ((ln(kbps.toFloat()) - LN_MIN) / (LN_MAX - LN_MIN)).coerceIn(0f, 1f)
}

/** kbps → KB/s (value passed to Go layer) */
fun kbpsToKBps(kbps: Int): Int {
    return (kbps / 8f).roundToInt().coerceAtLeast(1)
}

/** kbps → display string */
fun formatKbps(kbps: Int): String {
    return if (kbps >= 1000) {
        String.format("%.1f Mbps", kbps / 1000f)
    } else {
        "$kbps kbps"
    }
}

/** kbps → network generation label */
fun networkGeneration(kbps: Int): String {
    return when {
        kbps <= 100 -> "≈ 2G"
        kbps <= 500 -> "≈ Slow 3G"
        kbps <= 2000 -> "≈ Fast 3G"
        else -> "≈ 3G/HSPA+"
    }
}
