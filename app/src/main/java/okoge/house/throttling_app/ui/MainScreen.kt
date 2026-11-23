package okoge.house.throttling_app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import kotlin.math.ln
import okoge.house.throttling_app.data.VpnMode
import kotlin.math.exp
import kotlin.math.roundToInt

// 大画面でボタンやスライダーが横に伸びきらないようにする
internal val ContentMaxWidth = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp

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

        // 関係するものをまとまりに入れ、いちばん大事な開始・停止を周りより目立つ面に置く
        Section(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            VpnStatusText(uiState)
            Spacer(modifier = Modifier.height(8.dp))
            TargetAppsRow(uiState.targetAppCount, onNavigateToAppList)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Section(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ModeSelector(uiState.mode, onModeChange = { onEvent(MainEvent.ChangeMode(it)) })
            Spacer(modifier = Modifier.height(24.dp))
            SpeedControl(
                kbps = uiState.sliderKbps,
                enabled = uiState.mode == VpnMode.Throttle,
                onDrag = { onEvent(MainEvent.DragSlider(it)) },
                onDragFinished = { onEvent(MainEvent.FinishSlider) },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Section(color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            VpnToggleButton(uiState, onToggle = { onEvent(MainEvent.ToggleVpn) })
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onNavigateToLicenses) {
            Text("Open Source Licenses")
        }
    }
}

@Composable
private fun Section(color: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = color,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
private fun VpnStatusText(uiState: MainUiState) {
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
}

@Composable
private fun TargetAppsRow(targetAppCount: Int, onNavigateToAppList: (() -> Unit)?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Target: $targetAppCount apps")
        if (onNavigateToAppList != null) {
            TextButton(onClick = onNavigateToAppList) {
                Text("Manage →")
            }
        }
    }
}

@Composable
private fun ModeSelector(mode: VpnMode, onModeChange: (VpnMode) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Mode",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // つながった Button Group にし、選んだものを形と色で強調する
        Row(
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            val modes = listOf(VpnMode.Block to "Block", VpnMode.Throttle to "Throttle", VpnMode.Unlimited to "Unlimited")
            modes.forEachIndexed { index, (value, label) ->
                ToggleButton(
                    checked = mode == value,
                    onCheckedChange = { onModeChange(value) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    // 2 ペイン表示の狭いペインでも「Unlimited」が折り返さないよう、左右の余白を詰める
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Text(label, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SpeedControl(
    kbps: Int,
    enabled: Boolean,
    onDrag: (Int) -> Unit,
    onDragFinished: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    // 操作中の値は、まだ VPN に反映されていない。色を変えて、反映済みの値と見分けられるようにする
    val numberColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            isPressed || isDragged -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatKbps(kbps),
            style = MaterialTheme.typography.displayMediumEmphasized,
            // 端末の既定フォントでは強調スタイルの太さが控えめなので、今と同じ太字にそろえる
            fontWeight = FontWeight.Bold,
            color = numberColor,
        )

        Text(
            text = networkGeneration(kbps),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = kbpsToSlider(kbps),
            onValueChange = { onDrag(sliderToKbps(it)) },
            onValueChangeFinished = onDragFinished,
            valueRange = 0f..1f,
            enabled = enabled,
            interactionSource = interactionSource,
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
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VpnToggleButton(uiState: MainUiState, onToggle: () -> Unit) {
    val canStart = uiState.isVpnRunning || uiState.targetAppCount > 0
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 画面全体は standard のモーションのまま、山場にするこのボタンだけ expressive で動かす
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = MaterialTheme.shapes,
            typography = MaterialTheme.typography,
        ) {
            val height = ButtonDefaults.LargeContainerHeight
            ToggleButton(
                checked = uiState.isVpnRunning,
                onCheckedChange = { onToggle() },
                enabled = canStart && !uiState.isTransitioning,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = height),
                // 停止中は丸、動作中は角を立てた形にして、文字を読まなくても状態がわかるようにする
                shapes = ToggleButtonDefaults.shapesFor(height),
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    checkedContainerColor = MaterialTheme.colorScheme.error,
                    checkedContentColor = MaterialTheme.colorScheme.onError,
                ),
                contentPadding = ButtonDefaults.contentPaddingFor(height),
            ) {
                if (uiState.isTransitioning) {
                    LoadingIndicator(color = LocalContentColor.current)
                } else {
                    Text(
                        text = if (uiState.isVpnRunning) "Stop VPN" else "Start VPN",
                        style = ButtonDefaults.textStyleFor(height),
                    )
                }
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
