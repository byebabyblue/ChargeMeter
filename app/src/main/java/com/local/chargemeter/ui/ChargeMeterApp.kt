package com.local.chargemeter.ui

import android.os.Build
import android.graphics.Paint
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ElectricMeter
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.graphics.drawable.toBitmap
import com.local.chargemeter.data.BatteryReading
import com.local.chargemeter.data.AppPowerAverage
import com.local.chargemeter.data.AppPowerSample
import com.local.chargemeter.data.ChargeSample
import com.local.chargemeter.data.ChargeSession
import com.local.chargemeter.data.TemperatureSample
import com.local.chargemeter.monitor.DualCellMode
import com.local.chargemeter.monitor.FluidCloudPublisher
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val ChargeGreen = Color(0xFF12D85A)
private val ChargeGreenDark = Color(0xFF00A943)
private val DischargeRed = Color(0xFFFF4F55)
private val DischargeRedDark = Color(0xFFD92D38)
private val PageBackground = Color(0xFFF5F8F3)
private val CardBackground = Color(0xFFFFFFFF)
private val TextPrimary = Color(0xFF101411)
private val TextSecondary = Color(0xFF727873)
private val TrackColor = Color(0xFFE4EAE4)

private enum class Screen { Home, History, Detail, Health, Settings, AppUsage, LevelHistory, TemperatureHistory, UsageDetail }

private data class UsagePeriod(
    val startAt: Long,
    val endAt: Long,
    val startLevel: Int,
    val endLevel: Int,
)

private sealed interface HistoryEntry {
    val timestamp: Long
    data class Charge(val session: ChargeSession) : HistoryEntry { override val timestamp = session.startedAt }
    data class Usage(val period: UsagePeriod) : HistoryEntry { override val timestamp = period.startAt }
}

@Composable
fun ChargeMeterApp(viewModel: ChargeViewModel = viewModel()) {
    val reading by viewModel.reading.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val recentSamples by viewModel.recentSamples.collectAsStateWithLifecycle()
    val selectedSession by viewModel.selectedSession.collectAsStateWithLifecycle()
    val selectedSamples by viewModel.selectedSamples.collectAsStateWithLifecycle()
    val ratedMaxPowerW by viewModel.ratedMaxPowerW.collectAsStateWithLifecycle()
    val ratedCapacityMah by viewModel.ratedCapacityMah.collectAsStateWithLifecycle()
    val dualCellEnabled by viewModel.dualCellEnabled.collectAsStateWithLifecycle()
    val dualCellMode by viewModel.dualCellMode.collectAsStateWithLifecycle()
    val notificationEnabled by viewModel.notificationEnabled.collectAsStateWithLifecycle()
    val fluidCloudEnabled by viewModel.fluidCloudEnabled.collectAsStateWithLifecycle()
    val hideFromRecents by viewModel.hideFromRecents.collectAsStateWithLifecycle()
    val currentDirectionInverted by viewModel.currentDirectionInverted.collectAsStateWithLifecycle()
    val currentScaleExponent by viewModel.currentScaleExponent.collectAsStateWithLifecycle()
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
    val updateUrl by viewModel.updateUrl.collectAsStateWithLifecycle()
    val temperatureSamples by viewModel.temperatureSamples.collectAsStateWithLifecycle()
    val appPowerAverages by viewModel.appPowerAverages.collectAsStateWithLifecycle()
    val appPowerSamples by viewModel.appPowerSamples.collectAsStateWithLifecycle()
    val hazeState = rememberHazeState()
    var screen by remember { mutableStateOf(Screen.Home) }
    var detailReturnScreen by remember { mutableStateOf(Screen.Home) }
    var selectedUsagePeriod by remember { mutableStateOf<UsagePeriod?>(null) }

    BackHandler(enabled = screen != Screen.Home) {
        screen = when (screen) {
            Screen.Detail -> detailReturnScreen
            Screen.History, Screen.Health, Screen.Settings, Screen.AppUsage, Screen.LevelHistory, Screen.TemperatureHistory -> Screen.Home
            Screen.UsageDetail -> Screen.History
            Screen.Home -> Screen.Home
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = ChargeGreen,
            onPrimary = Color.White,
            background = PageBackground,
            surface = CardBackground,
            onSurface = TextPrimary,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(PageBackground)) {
            Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
                AnimatedContent(
                    targetState = screen,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val forward = screenDepth(targetState) >= screenDepth(initialState)
                        val direction = if (forward) 1 else -1
                        (slideInHorizontally(tween(280)) { it * direction } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(280)) { -it * direction } + fadeOut(tween(180)))
                    },
                    label = "screen_transition",
                ) { animatedScreen ->
                when (animatedScreen) {
                Screen.Home -> {
                    val currentId = sessions.firstOrNull { it.endedAt == null }?.id
                        ?: sessions.firstOrNull()?.id
                    val curveSamples = recentSamples
                        .filter { currentId == null || it.sessionId == currentId }
                        .asReversed()
                    HomeScreen(
                        reading = reading,
                        samples = curveSamples,
                        ratedMaxPowerW = ratedMaxPowerW,
                        ratedCapacityMah = ratedCapacityMah,
                        onOpenDetail = {
                            if (viewModel.selectCurrentOrLatest()) {
                                detailReturnScreen = Screen.Home
                                screen = Screen.Detail
                            }
                        },
                        onOpenHealth = {
                            screen = Screen.Health
                        },
                        onOpenSettings = {
                            screen = Screen.Settings
                        },
                        onOpenAppUsage = {
                            screen = Screen.AppUsage
                        },
                        onOpenLevelHistory = {
                            selectedUsagePeriod = latestUsagePeriod(sessions, temperatureSamples, reading)
                            screen = Screen.LevelHistory
                        },
                        onOpenTemperatureHistory = { screen = Screen.TemperatureHistory },
                    )
                }

                Screen.History -> HistoryScreen(
                    sessions = sessions,
                    temperatureSamples = temperatureSamples,
                    onSessionClick = {
                        viewModel.selectSession(it.id)
                        detailReturnScreen = Screen.History
                        screen = Screen.Detail
                    },
                    onUsageClick = {
                        selectedUsagePeriod = it
                        screen = Screen.UsageDetail
                    },
                )

                Screen.Detail -> DetailScreen(
                    session = selectedSession,
                    samples = selectedSamples,
                    reading = reading,
                    onBack = { screen = detailReturnScreen },
                )

                Screen.Health -> HealthScreen(
                    reading = reading,
                    sessions = sessions,
                    temperatureSamples = temperatureSamples,
                    onBack = { screen = Screen.Home },
                    onSessionClick = {
                        viewModel.selectSession(it.id)
                        detailReturnScreen = Screen.Health
                        screen = Screen.Detail
                    },
                )

                Screen.Settings -> SettingsScreen(
                    ratedMaxPowerW = ratedMaxPowerW,
                    ratedCapacityMah = ratedCapacityMah,
                    dualCellEnabled = dualCellEnabled,
                    dualCellMode = dualCellMode,
                    notificationEnabled = notificationEnabled,
                    fluidCloudEnabled = fluidCloudEnabled,
                    hideFromRecents = hideFromRecents,
                    currentDirectionInverted = currentDirectionInverted,
                    currentScaleExponent = currentScaleExponent,
                    updateStatus = updateStatus,
                    updateUrl = updateUrl,
                    onRatedPowerChange = viewModel::setRatedMaxPower,
                    onRatedCapacityChange = viewModel::setRatedCapacity,
                    onDualCellChange = viewModel::setDualCellEnabled,
                    onDualCellModeChange = viewModel::setDualCellMode,
                    onNotificationEnabledChange = viewModel::setNotificationEnabled,
                    onFluidCloudEnabledChange = viewModel::setFluidCloudEnabled,
                    onHideFromRecentsChange = viewModel::setHideFromRecents,
                    onCurrentDirectionInvertedChange = viewModel::setCurrentDirectionInverted,
                    onCurrentScaleExponentChange = viewModel::setCurrentScaleExponent,
                    onCheckForUpdates = viewModel::checkForUpdates,
                    onBack = { screen = Screen.Home },
                )

                Screen.AppUsage -> AppUsageScreen(
                    powerAverages = appPowerAverages,
                    onBack = { screen = Screen.Home },
                )

                Screen.LevelHistory -> UsageDetailScreen(
                    period = selectedUsagePeriod ?: latestUsagePeriod(sessions, temperatureSamples, reading),
                    temperatureSamples = temperatureSamples,
                    appPowerSamples = appPowerSamples,
                    title = "电量记录",
                    onBack = { screen = Screen.Home },
                )

                Screen.TemperatureHistory -> StandaloneTemperatureScreen(
                    samples = temperatureSamples.filter { it.recordedAt >= System.currentTimeMillis() - 24L * 60L * 60L * 1000L },
                    onBack = { screen = Screen.Home },
                )

                Screen.UsageDetail -> UsageDetailScreen(
                    period = selectedUsagePeriod,
                    temperatureSamples = temperatureSamples,
                    appPowerSamples = appPowerSamples,
                    title = "使用详情",
                    onBack = { screen = Screen.History },
                )
                }
                }
            }
            if (screen == Screen.Home || screen == Screen.History) {
                ChargeNavigation(
                    selected = screen,
                    onSelect = { screen = it },
                    hazeState = hazeState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

private fun screenDepth(screen: Screen): Int = when (screen) {
    Screen.Home -> 0
    Screen.History, Screen.Health, Screen.Settings, Screen.AppUsage, Screen.LevelHistory, Screen.TemperatureHistory -> 1
    Screen.Detail, Screen.UsageDetail -> 2
}

@Composable
private fun PageHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(25.dp),
                )
            }
            Text(
                title,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            subtitle,
            modifier = Modifier.padding(start = 40.dp),
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeScreen(
    reading: BatteryReading,
    samples: List<ChargeSample>,
    ratedMaxPowerW: Double,
    ratedCapacityMah: Int,
    modifier: Modifier = Modifier,
    onOpenDetail: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppUsage: () -> Unit,
    onOpenLevelHistory: () -> Unit,
    onOpenTemperatureHistory: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
            end = 20.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 132.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Header(reading) }
        item { PowerGauge(kotlin.math.abs(reading.powerW), reading.isCharging, ratedMaxPowerW) }
        item {
            Text(
                text = when {
                    reading.isCharging -> "正在${reading.plugType}充电"
                    reading.isPowerConnected -> "${reading.plugType}已连接 · 电池正在放电"
                    else -> "正在放电"
                },
                modifier = Modifier.fillMaxWidth(),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
        }
        item {
            Button(
                onClick = onOpenDetail,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ChargeGreen),
            ) {
                Icon(Icons.Rounded.TrendingUp, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text("充电详情", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    title = "电池电量",
                    value = "${reading.level}%",
                    subtitle = if (reading.isCharging) "正在补充电量" else "当前剩余电量",
                    icon = Icons.Rounded.BatteryChargingFull,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenLevelHistory,
                )
                MetricCard(
                    title = "电池温度",
                    value = oneDecimal(reading.temperatureC, "°C"),
                    subtitle = temperatureLabel(reading.temperatureC),
                    icon = Icons.Rounded.Thermostat,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenTemperatureHistory,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    title = "电池电压",
                    value = twoDecimals(reading.voltageV, " V"),
                    subtitle = "实时电池端电压",
                    icon = Icons.Rounded.ElectricMeter,
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = "实时电流",
                    value = twoDecimals(kotlin.math.abs(reading.currentA), " A"),
                    subtitle = "实时充电电流",
                    icon = Icons.Rounded.Speed,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            ChargeCurveCard(
                samples = samples,
                title = "本次充电曲线",
                onClick = onOpenDetail,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallStatusCard(
                    title = "电池健康",
                    value = reading.health,
                    icon = Icons.Rounded.HealthAndSafety,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenHealth,
                )
                SmallStatusCard(
                    title = "连接方式",
                    value = reading.plugType,
                    icon = Icons.Rounded.Cable,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item { AppUsageEntryCard(onOpenAppUsage) }
        item { SettingsEntryCard(ratedMaxPowerW, ratedCapacityMah, onOpenSettings) }
    }
}

@Composable
private fun AppUsageEntryCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).noIndicationClickable(onClick = onClick),
        color = CardBackground,
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(ChargeGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Apps, contentDescription = null, tint = ChargeGreenDark)
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("应用使用情况", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("查看近 24 小时前台与后台时长", color = TextSecondary, fontSize = 13.sp)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
private fun SettingsEntryCard(ratedMaxPowerW: Double, ratedCapacityMah: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).noIndicationClickable(onClick = onClick),
        color = CardBackground,
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(ChargeGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = null, tint = ChargeGreenDark)
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("设置", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "${ratedMaxPowerW.toInt()} W · $ratedCapacityMah mAh",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
private fun Header(reading: BatteryReading) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("充电信息", fontSize = 32.sp, fontWeight = FontWeight.Black, color = TextPrimary)
            Text("实时功率与电池状态", fontSize = 14.sp, color = TextSecondary)
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = if (reading.isCharging) ChargeGreen.copy(alpha = 0.13f) else DischargeRed.copy(alpha = 0.12f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(
                        if (reading.isCharging) ChargeGreen else DischargeRed,
                    ),
                )
                Spacer(Modifier.size(7.dp))
                Text(
                    if (reading.isCharging) "充电中" else "放电中",
                    fontWeight = FontWeight.Bold,
                    color = if (reading.isCharging) ChargeGreenDark else DischargeRedDark,
                )
            }
        }
    }
}

@Composable
private fun PowerGauge(powerW: Double, charging: Boolean, ratedMaxPowerW: Double) {
    val fraction by animateFloatAsState((powerW / ratedMaxPowerW).coerceIn(0.0, 1.0).toFloat(), label = "power")
    val numberFontSize = when {
        powerW >= 100.0 -> 40.sp
        powerW >= 10.0 -> 48.sp
        else -> 56.sp
    }
    val unitFontSize = if (powerW >= 100.0) 17.sp else 20.sp
    Box(
        modifier = Modifier.fillMaxWidth().height(252.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(244.dp)) {
            // Reference proportions: the tube is one eighth of the outer diameter.
            val stroke = 24.dp.toPx()
            val ringRadius = 84.dp.toPx()
            val outerRadius = ringRadius + stroke / 2f
            val innerRadius = ringRadius - stroke / 2f
            val activeColor = if (charging) Color(0xFF2ACD20) else DischargeRed
            val shadowCenter = center

            // Continuous falloff keeps the halo soft, without visible concentric bands.
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.Transparent,
                    0.56f to activeColor.copy(alpha = 0.025f),
                    0.75f to activeColor.copy(alpha = 0.085f),
                    1f to Color.Transparent,
                    center = shadowCenter,
                    radius = outerRadius + 24.dp.toPx(),
                ),
                radius = outerRadius + 24.dp.toPx(),
                center = shadowCenter,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0xFF71917B).copy(alpha = 0.10f),
                    0.75f to Color(0xFF71917B).copy(alpha = 0.10f),
                    1f to Color.Transparent,
                    center = shadowCenter,
                    radius = outerRadius + 17.dp.toPx(),
                ),
                radius = outerRadius + 17.dp.toPx(),
                center = shadowCenter,
            )

            // A broad pale tube with an even, concentric inset around the inner disc.
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0xFFFFFFFF),
                    0.76f to Color(0xFFFFFFFF),
                    1f to Color(0xFFEDF0EE),
                    center = center,
                    radius = outerRadius,
                ),
                radius = outerRadius,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0xFF739078).copy(alpha = 0.19f),
                    0.78f to Color(0xFF739078).copy(alpha = 0.19f),
                    1f to Color.Transparent,
                    center = shadowCenter,
                    radius = innerRadius + 14.dp.toPx(),
                ),
                radius = innerRadius + 14.dp.toPx(),
                center = shadowCenter,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0xFFF7FAF6),
                    0.82f to Color(0xFFF7FAF6),
                    1f to Color(0xFFFCFDFC),
                    center = center,
                    radius = innerRadius,
                ),
                radius = innerRadius,
            )
            if (fraction > 0f) {
                drawArc(
                    brush = if (charging) {
                        Brush.linearGradient(
                            0f to Color(0xFF27CB1C),
                            0.65f to Color(0xFF2DD021),
                            1f to Color(0xFF52DC3E),
                            start = Offset(center.x, center.y - outerRadius),
                            end = Offset(center.x, center.y + outerRadius),
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFE53B46), DischargeRed, Color(0xFFFF7478)),
                            start = Offset(center.x, center.y - outerRadius),
                            end = Offset(center.x, center.y + outerRadius),
                        )
                    },
                    startAngle = 90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                    size = androidx.compose.ui.geometry.Size(ringRadius * 2f, ringRadius * 2f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.getDefault(), "%.1f", powerW),
                    fontSize = numberFontSize,
                    lineHeight = numberFontSize * 1.04f,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                )
                Text(" W", modifier = Modifier.padding(bottom = 7.dp), fontSize = unitFontSize, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.height(166.dp).clip(RoundedCornerShape(28.dp))
            .then(if (onClick != null) Modifier.noIndicationClickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(27.dp))
            Column {
                Text(title, color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(value, color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SmallStatusCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(24.dp))
            .then(if (onClick != null) Modifier.noIndicationClickable(onClick = onClick) else Modifier),
        color = CardBackground,
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier.padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(ChargeGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = ChargeGreenDark, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.size(12.dp))
            Column {
                Text(title, color = TextSecondary, fontSize = 12.sp)
                Text(value, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ChargeCurveCard(
    samples: List<ChargeSample>,
    title: String,
    onClick: (() -> Unit)? = null,
    interactive: Boolean = false,
) {
    var selectedSample by remember(samples, interactive) {
        mutableStateOf(if (interactive) samples.lastOrNull() else null)
    }
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .then(if (onClick != null) Modifier.noIndicationClickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (samples.isEmpty()) "充电后生成实时曲线" else "${samples.size} 个采样点",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                if (onClick != null) Icon(Icons.Rounded.ChevronRight, contentDescription = "查看详情", tint = TextSecondary)
            }
            if (samples.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChartLegendDot(ChargeGreen, "充电功率")
                    ChartLegendDot(DischargeRed, "放电功率")
                    ChartLegendDot(temperatureColor(samples.last().temperatureC), "电池温度 · 右轴")
                }
            }
            Spacer(Modifier.height(18.dp))
            if (interactive && selectedSample != null) {
                val point = selectedSample!!
                val pointColor = if (point.powerW >= 0.0) ChargeGreenDark else DischargeRedDark
                Surface(
                    color = pointColor.copy(alpha = 0.11f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            "${formatTimeSeconds(point.recordedAt)}  ·  ${flowPowerLabel(point.powerW)}",
                            color = pointColor,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${twoDecimals(point.voltageV, " V")}  ·  ${twoDecimals(abs(point.currentA), " A")}  ·  ${oneDecimal(point.temperatureC, "°C")}",
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            PowerChart(
                samples = samples,
                selected = selectedSample,
                onSelect = if (interactive) ({ selectedSample = it }) else null,
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
            if (samples.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(samples.first().recordedAt), fontSize = 11.sp, color = TextSecondary)
                    Text(formatTime(samples.last().recordedAt), fontSize = 11.sp, color = TextSecondary)
                }
                val chargePeak = samples.filter { it.powerW > 0.0 }.maxOfOrNull { it.powerW }
                val dischargePeak = samples.filter { it.powerW < 0.0 }.maxOfOrNull { abs(it.powerW) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    chargePeak?.let {
                        Text(
                            "充电峰值 ${oneDecimal(it, " W")}",
                            fontSize = 12.sp,
                            color = ChargeGreenDark,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (chargePeak != null && dischargePeak != null) {
                        Text("  ·  ", color = TextSecondary, fontSize = 12.sp)
                    }
                    dischargePeak?.let {
                        Text(
                            "放电峰值 ${oneDecimal(it, " W")}",
                            fontSize = 12.sp,
                            color = DischargeRedDark,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PowerChart(
    samples: List<ChargeSample>,
    selected: ChargeSample? = null,
    onSelect: ((ChargeSample) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (samples.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("暂无足够数据", color = TextSecondary, fontSize = 13.sp)
        }
        return
    }
    val touchModifier = if (onSelect != null) {
        fun selectAt(x: Float, width: Float, left: Float, right: Float) {
            val fraction = ((x - left) / (width - left - right)).coerceIn(0f, 1f)
            val minTime = samples.first().recordedAt
            val maxTime = samples.last().recordedAt
            val target = minTime + ((maxTime - minTime) * fraction).toLong()
            onSelect(samples.minBy { kotlin.math.abs(it.recordedAt - target) })
        }
        Modifier.pointerInput(samples) {
            detectTapGestures { offset ->
                selectAt(offset.x, size.width.toFloat(), 44.dp.toPx(), 42.dp.toPx())
            }
        }.pointerInput(samples) {
            detectHorizontalDragGestures(
                onDragStart = { selectAt(it.x, size.width.toFloat(), 44.dp.toPx(), 42.dp.toPx()) },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    selectAt(change.position.x, size.width.toFloat(), 44.dp.toPx(), 42.dp.toPx())
                },
            )
        }
    } else Modifier
    Canvas(modifier = modifier.then(touchModifier)) {
        val maxPower = max(10.0, samples.maxOf { abs(it.powerW) })
        val minTime = samples.first().recordedAt
        val maxTime = samples.last().recordedAt
        val duration = (maxTime - minTime).coerceAtLeast(1L)
        val leftPadding = 44.dp.toPx()
        val rightPadding = 42.dp.toPx()
        val chartWidth = size.width - leftPadding - rightPadding
        val minTemp = min(15.0, floor(samples.minOf { it.temperatureC } - 1.0))
        val maxTemp = max(45.0, ceil(samples.maxOf { it.temperatureC } + 1.0))
        val tempSpan = (maxTemp - minTemp).coerceAtLeast(1.0)
        val labelPaint = Paint().apply {
            color = android.graphics.Color.rgb(114, 120, 115)
            textSize = 10.sp.toPx()
            isAntiAlias = true
        }
        repeat(4) { index ->
            val y = size.height * index / 3f
            drawLine(
                color = TrackColor,
                start = Offset(leftPadding, y),
                end = Offset(size.width - rightPadding, y),
                strokeWidth = 1.dp.toPx(),
            )
            val label = String.format(Locale.getDefault(), "%.1f W", maxPower * (3 - index) / 3.0)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                0f,
                (y + if (index == 0) labelPaint.textSize else if (index == 3) -3.dp.toPx() else labelPaint.textSize / 3f),
                labelPaint,
            )
            val tempLabel = String.format(Locale.getDefault(), "%.0f°C", maxTemp - tempSpan * index / 3.0)
            drawContext.canvas.nativeCanvas.drawText(
                tempLabel,
                size.width - rightPadding + 5.dp.toPx(),
                (y + if (index == 0) labelPaint.textSize else if (index == 3) -3.dp.toPx() else labelPaint.textSize / 3f),
                labelPaint,
            )
        }
        samples.zipWithNext().forEach { (first, second) ->
            val x1 = leftPadding + ((first.recordedAt - minTime).toFloat() / duration) * chartWidth
            val x2 = leftPadding + ((second.recordedAt - minTime).toFloat() / duration) * chartWidth
            val y1 = size.height - ((abs(first.powerW) / maxPower).toFloat() * size.height * 0.9f)
            val y2 = size.height - ((abs(second.powerW) / maxPower).toFloat() * size.height * 0.9f)
            val chargingSegment = (first.powerW + second.powerW) >= 0.0
            val missingInterval = second.recordedAt - first.recordedAt > MISSING_SAMPLE_INTERVAL_MS
            val segmentColor = if (chargingSegment) ChargeGreen else DischargeRed
            drawLine(
                color = if (missingInterval) segmentColor.copy(alpha = 0.55f) else segmentColor,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (missingInterval) PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 7.dp.toPx())) else null,
            )
            if (missingInterval) {
                drawCircle(segmentColor, 2.5.dp.toPx(), Offset(x1, y1))
                drawCircle(segmentColor, 2.5.dp.toPx(), Offset(x2, y2))
            }
        }
        samples.zipWithNext().forEach { (first, second) ->
            val x1 = leftPadding + ((first.recordedAt - minTime).toFloat() / duration) * chartWidth
            val x2 = leftPadding + ((second.recordedAt - minTime).toFloat() / duration) * chartWidth
            val y1 = size.height - (((first.temperatureC - minTemp) / tempSpan).toFloat() * size.height)
            val y2 = size.height - (((second.temperatureC - minTemp) / tempSpan).toFloat() * size.height)
            val missingInterval = second.recordedAt - first.recordedAt > MISSING_SAMPLE_INTERVAL_MS
            val firstColor = temperatureColor(first.temperatureC)
            val secondColor = temperatureColor(second.temperatureC)
            drawLine(
                color = temperatureColor((first.temperatureC + second.temperatureC) / 2.0).let {
                    if (missingInterval) it.copy(alpha = 0.55f) else it
                },
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (missingInterval) PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 7.dp.toPx())) else null,
            )
            if (missingInterval) {
                drawCircle(firstColor, 2.5.dp.toPx(), Offset(x1, y1))
                drawCircle(secondColor, 2.5.dp.toPx(), Offset(x2, y2))
            }
        }
        selected?.let { point ->
            val x = leftPadding + ((point.recordedAt - minTime).toFloat() / duration) * chartWidth
            val y = size.height - ((abs(point.powerW) / maxPower).toFloat() * size.height * 0.9f)
            val pointColor = if (point.powerW >= 0.0) ChargeGreenDark else DischargeRedDark
            drawLine(
                color = pointColor.copy(alpha = 0.45f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.5.dp.toPx(),
            )
            drawCircle(color = Color.White, radius = 7.dp.toPx(), center = Offset(x, y))
            drawCircle(color = pointColor, radius = 4.dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
private fun ChartLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(6.dp))
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun BatteryLevelCurveCard(samples: List<ChargeSample>) {
    var selectedIndex by remember(samples) { mutableStateOf<Int?>(null) }
    val selected = selectedIndex?.let(samples::getOrNull)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("电量曲线", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                when {
                    samples.isEmpty() -> "等待本次充电数据"
                    selected != null -> "${formatTimeSeconds(selected.recordedAt)} · ${selected.level}% · ${twoDecimals(selected.voltageV, " V")} · ${flowPowerLabel(selected.powerW)}" 
                    else -> "${samples.first().level}% → ${samples.last().level}% · 点按或左右滑动查看"
                },
                color = TextSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(16.dp))
            if (samples.size < 2) {
                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text("暂无足够数据", color = TextSecondary, fontSize = 13.sp)
                }
            } else {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .pointerInput(samples) {
                            detectTapGestures { offset ->
                                val left = 40.dp.toPx()
                                val fraction = ((offset.x - left) / (size.width - left)).coerceIn(0f, 1f)
                                val targetTime = samples.first().recordedAt +
                                    ((samples.last().recordedAt - samples.first().recordedAt) * fraction).toLong()
                                selectedIndex = samples.indices.minByOrNull {
                                    abs(samples[it].recordedAt - targetTime)
                                }
                            }
                        }
                        .pointerInput(samples) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    val left = 40.dp.toPx()
                                    val fraction = ((offset.x - left) / (size.width - left)).coerceIn(0f, 1f)
                                    val targetTime = samples.first().recordedAt +
                                        ((samples.last().recordedAt - samples.first().recordedAt) * fraction).toLong()
                                    selectedIndex = samples.indices.minByOrNull { abs(samples[it].recordedAt - targetTime) }
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    val left = 40.dp.toPx()
                                    val fraction = ((change.position.x - left) / (size.width - left)).coerceIn(0f, 1f)
                                    val targetTime = samples.first().recordedAt +
                                        ((samples.last().recordedAt - samples.first().recordedAt) * fraction).toLong()
                                    selectedIndex = samples.indices.minByOrNull { abs(samples[it].recordedAt - targetTime) }
                                },
                            )
                        },
                ) {
                    val left = 40.dp.toPx()
                    val width = size.width - left
                    val minTime = samples.first().recordedAt
                    val duration = (samples.last().recordedAt - minTime).coerceAtLeast(1L)
                    val paint = Paint().apply {
                        color = android.graphics.Color.rgb(114, 120, 115)
                        textSize = 10.sp.toPx()
                        isAntiAlias = true
                    }
                    repeat(4) { index ->
                        val y = size.height * index / 3f
                        drawLine(TrackColor, Offset(left, y), Offset(size.width, y), 1.dp.toPx())
                        val level = 100 - index * (100 / 3f)
                        drawContext.canvas.nativeCanvas.drawText(
                            String.format(Locale.getDefault(), "%.0f%%", level),
                            0f,
                            y + if (index == 0) paint.textSize else if (index == 3) -3.dp.toPx() else paint.textSize / 3f,
                            paint,
                        )
                    }
                    repeat(4) { index ->
                        val x = left + width * index / 3f
                        drawLine(
                            TrackColor.copy(alpha = 0.72f),
                            Offset(x, 0f),
                            Offset(x, size.height),
                            1.dp.toPx(),
                        )
                    }
                    samples.zipWithNext().forEach { (first, second) ->
                        val x1 = left + ((first.recordedAt - minTime).toFloat() / duration) * width
                        val x2 = left + ((second.recordedAt - minTime).toFloat() / duration) * width
                        val y1 = size.height - (first.level / 100f * size.height)
                        val y2 = size.height - (second.level / 100f * size.height)
                        val missingInterval = second.recordedAt - first.recordedAt > MISSING_SAMPLE_INTERVAL_MS
                        val segmentColor = Color(0xFF3978EB)
                        drawLine(
                            color = if (missingInterval) segmentColor.copy(alpha = 0.55f) else segmentColor,
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = if (missingInterval) PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 7.dp.toPx())) else null,
                        )
                        if (missingInterval) {
                            drawCircle(segmentColor, 2.5.dp.toPx(), Offset(x1, y1))
                            drawCircle(segmentColor, 2.5.dp.toPx(), Offset(x2, y2))
                        }
                    }
                    selected?.let { point ->
                        val x = left + ((point.recordedAt - minTime).toFloat() / duration) * width
                        val y = size.height - (point.level / 100f * size.height)
                        drawLine(
                            color = Color(0xFF3978EB).copy(alpha = 0.5f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.5.dp.toPx(),
                        )
                        drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(x, y))
                        drawCircle(Color(0xFF3978EB), radius = 4.dp.toPx(), center = Offset(x, y))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 40.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    repeat(4) { index ->
                        val timestamp = samples.first().recordedAt +
                            (samples.last().recordedAt - samples.first().recordedAt) * index / 3L
                        Text(formatTime(timestamp), fontSize = 10.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TemperatureHistoryCard(
    samples: List<TemperatureSample>,
) {
    var selectedIndex by remember(samples) { mutableStateOf<Int?>(null) }
    val selected = selectedIndex?.let(samples::getOrNull)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Column {
                Text("电池温度记录", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    selected?.let {
                        "${formatTimeSeconds(it.recordedAt)} · ${oneDecimal(it.temperatureC, "°C")} · ${it.level}% · ${flowPowerLabel(it.powerW)}" 
                    } ?: "最近 24 小时 · 点按或左右滑动查看",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
            TemperatureHistoryChart(
                samples = samples,
                selected = selected,
                onSelect = { selectedIndex = it },
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
            if (samples.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(samples.first().recordedAt), fontSize = 11.sp, color = TextSecondary)
                    Text(
                        "${oneDecimal(samples.minOf { it.temperatureC }, "°C")} – ${oneDecimal(samples.maxOf { it.temperatureC }, "°C")}",
                        fontSize = 11.sp,
                        color = TextSecondary,
                    )
                    Text(formatTime(samples.last().recordedAt), fontSize = 11.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun TemperatureHistoryChart(
    samples: List<TemperatureSample>,
    selected: TemperatureSample?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (samples.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("持续监控后生成温度曲线", color = TextSecondary, fontSize = 13.sp)
        }
        return
    }
    Canvas(
        modifier.pointerInput(samples) {
            detectTapGestures { offset ->
                val left = 38.dp.toPx()
                val fraction = ((offset.x - left) / (size.width - left)).coerceIn(0f, 1f)
                val targetTime = samples.first().recordedAt +
                    ((samples.last().recordedAt - samples.first().recordedAt) * fraction).toLong()
                samples.indices.minByOrNull { abs(samples[it].recordedAt - targetTime) }?.let(onSelect)
            }
        }.pointerInput(samples) {
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    val left = 38.dp.toPx()
                    val fraction = ((offset.x - left) / (size.width - left)).coerceIn(0f, 1f)
                    val targetTime = samples.first().recordedAt +
                        ((samples.last().recordedAt - samples.first().recordedAt) * fraction).toLong()
                    samples.indices.minByOrNull { abs(samples[it].recordedAt - targetTime) }?.let(onSelect)
                },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    val left = 38.dp.toPx()
                    val fraction = ((change.position.x - left) / (size.width - left)).coerceIn(0f, 1f)
                    val targetTime = samples.first().recordedAt +
                        ((samples.last().recordedAt - samples.first().recordedAt) * fraction).toLong()
                    samples.indices.minByOrNull { abs(samples[it].recordedAt - targetTime) }?.let(onSelect)
                },
            )
        },
    ) {
        val left = 38.dp.toPx()
        val width = size.width - left
        val minTime = samples.first().recordedAt
        val duration = (samples.last().recordedAt - minTime).coerceAtLeast(1L)
        val minTemp = min(15.0, floor(samples.minOf { it.temperatureC } - 1.0))
        val maxTemp = max(45.0, ceil(samples.maxOf { it.temperatureC } + 1.0))
        val span = (maxTemp - minTemp).coerceAtLeast(1.0)
        val paint = Paint().apply {
            color = android.graphics.Color.rgb(114, 120, 115)
            textSize = 10.sp.toPx()
            isAntiAlias = true
        }
        repeat(4) { index ->
            val y = size.height * index / 3f
            drawLine(TrackColor, Offset(left, y), Offset(size.width, y), 1.dp.toPx())
            val temp = maxTemp - span * index / 3.0
            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.getDefault(), "%.0f°", temp),
                0f,
                y + if (index == 0) paint.textSize else if (index == 3) -3.dp.toPx() else paint.textSize / 3f,
                paint,
            )
        }
        samples.zipWithNext().forEach { (first, second) ->
            val x1 = left + ((first.recordedAt - minTime).toFloat() / duration) * width
            val x2 = left + ((second.recordedAt - minTime).toFloat() / duration) * width
            val y1 = size.height - (((first.temperatureC - minTemp) / span).toFloat() * size.height)
            val y2 = size.height - (((second.temperatureC - minTemp) / span).toFloat() * size.height)
            val missingInterval = second.recordedAt - first.recordedAt > MISSING_SAMPLE_INTERVAL_MS
            val firstColor = temperatureColor(first.temperatureC)
            val secondColor = temperatureColor(second.temperatureC)
            drawLine(
                temperatureColor((first.temperatureC + second.temperatureC) / 2.0).let {
                    if (missingInterval) it.copy(alpha = 0.55f) else it
                },
                Offset(x1, y1),
                Offset(x2, y2),
                3.dp.toPx(),
                StrokeCap.Round,
                pathEffect = if (missingInterval) PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 7.dp.toPx())) else null,
            )
            if (missingInterval) {
                drawCircle(firstColor, 2.5.dp.toPx(), Offset(x1, y1))
                drawCircle(secondColor, 2.5.dp.toPx(), Offset(x2, y2))
            }
        }
        selected?.let { point ->
            val x = left + ((point.recordedAt - minTime).toFloat() / duration) * width
            val y = size.height - (((point.temperatureC - minTemp) / span).toFloat() * size.height)
            val color = temperatureColor(point.temperatureC)
            drawLine(
                color = color.copy(alpha = 0.45f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.5.dp.toPx(),
            )
            drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(x, y))
            drawCircle(color, radius = 4.dp.toPx(), center = Offset(x, y))
        }
    }
}

private fun temperatureColor(value: Double): Color = when {
    value < 20.0 -> Color.Black
    value < 33.0 -> Color(0xFF3D8BFF)
    value < 38.0 -> Color(0xFFF2C94C)
    value < 41.0 -> Color(0xFFFF8A34)
    else -> Color(0xFFFF3B45)
}

@Composable
private fun HistoryScreen(
    sessions: List<ChargeSession>,
    temperatureSamples: List<TemperatureSample>,
    modifier: Modifier = Modifier,
    onSessionClick: (ChargeSession) -> Unit,
    onUsageClick: (UsagePeriod) -> Unit,
) {
    val periods = remember(sessions, temperatureSamples) {
        buildUsagePeriods(sessions, temperatureSamples, System.currentTimeMillis())
    }
    val entries = remember(sessions, periods) {
        (sessions.map { HistoryEntry.Charge(it) } + periods.map { HistoryEntry.Usage(it) })
            .sortedByDescending { it.timestamp }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
            end = 20.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 132.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("历史", fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text("充电与两次充电之间的使用记录", color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
        }
        if (entries.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = CardBackground,
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 54.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = null, tint = TrackColor, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("还没有历史记录", fontWeight = FontWeight.Bold)
                        Text("充电和日常使用后会自动保存", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
        items(entries, key = {
            when (it) {
                is HistoryEntry.Charge -> "charge-${it.session.id}"
                is HistoryEntry.Usage -> "usage-${it.period.startAt}"
            }
        }) { entry ->
            when (entry) {
                is HistoryEntry.Charge -> SessionCard(entry.session) { onSessionClick(entry.session) }
                is HistoryEntry.Usage -> UsagePeriodCard(entry.period) { onUsageClick(entry.period) }
            }
        }
    }
}

@Composable
private fun UsagePeriodCard(period: UsagePeriod, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).noIndicationClickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = CardBackground,
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF4B7BFF).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Home, contentDescription = null, tint = Color(0xFF3568E8))
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("使用记录", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "${formatDate(period.startAt)} – ${formatTime(period.endAt)}",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
                Text(
                    "电量 ${period.startLevel}% → ${period.endLevel}% · ${formatDuration(period.endAt - period.startAt)}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

private fun buildUsagePeriods(
    sessions: List<ChargeSession>,
    temperatureSamples: List<TemperatureSample>,
    now: Long,
): List<UsagePeriod> {
    val ordered = sessions.sortedBy { it.startedAt }
    return ordered.mapIndexedNotNull { index, session ->
        val startAt = session.endedAt ?: return@mapIndexedNotNull null
        val nextSession = ordered.drop(index + 1).firstOrNull { it.startedAt > startAt }
        val endAt = nextSession?.startedAt ?: now
        if (endAt - startAt < 60_000L) return@mapIndexedNotNull null
        val samples = temperatureSamples.filter { it.recordedAt in startAt..endAt && !it.isCharging }
        UsagePeriod(
            startAt = startAt,
            endAt = endAt,
            startLevel = samples.firstOrNull()?.level ?: session.endLevel,
            endLevel = samples.lastOrNull()?.level ?: nextSession?.startLevel ?: session.endLevel,
        )
    }
}

private fun latestUsagePeriod(
    sessions: List<ChargeSession>,
    temperatureSamples: List<TemperatureSample>,
    reading: BatteryReading,
): UsagePeriod? {
    val lastFull = sessions.filter { it.endedAt != null && it.endLevel >= 99 }.maxByOrNull { it.endedAt ?: 0L }
    if (lastFull != null) {
        val startAt = lastFull.endedAt ?: lastFull.startedAt
        val samples = temperatureSamples.filter { it.recordedAt in startAt..reading.timestamp && !it.isCharging }
        return UsagePeriod(
            startAt = startAt,
            endAt = reading.timestamp,
            startLevel = samples.firstOrNull()?.level ?: lastFull.endLevel,
            endLevel = samples.lastOrNull()?.level ?: reading.level,
        )
    }
    return buildUsagePeriods(sessions, temperatureSamples, reading.timestamp).maxByOrNull { it.endAt }
        ?: temperatureSamples.filter { !it.isCharging }.takeIf { it.isNotEmpty() }?.let { samples ->
        UsagePeriod(
            startAt = samples.first().recordedAt,
            endAt = samples.last().recordedAt,
            startLevel = samples.first().level,
            endLevel = samples.last().level,
        )
    }
}

@Composable
private fun StandaloneTemperatureScreen(samples: List<TemperatureSample>, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
            end = 20.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PageHeader("电池温度", "最近 24 小时温度曲线", onBack) }
        item { TemperatureHistoryCard(samples) }
    }
}

private data class IntervalAppSummary(
    val packageName: String,
    val label: String,
    val durationMs: Long,
    val averagePowerW: Double,
    val minTemperatureC: Double?,
    val maxTemperatureC: Double?,
)

@Composable
private fun UsageDetailScreen(
    period: UsagePeriod?,
    temperatureSamples: List<TemperatureSample>,
    appPowerSamples: List<AppPowerSample>,
    title: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val reader = remember(context) { AppUsageReader(context.applicationContext) }
    val startAt = period?.startAt ?: (System.currentTimeMillis() - 24L * 60L * 60L * 1000L)
    val endAt = period?.endAt ?: System.currentTimeMillis()
    val levelSamples = remember(temperatureSamples, startAt, endAt) {
        temperatureSamples.filter { it.recordedAt in startAt..endAt }
    }
    val screenOnMs = remember(startAt, endAt) { reader.screenOnDuration(startAt, endAt) }
    val appSummaries = remember(appPowerSamples, levelSamples, startAt, endAt) {
        appPowerSamples.filter { it.recordedAt in startAt..endAt && !it.isCharging }
            .groupBy { it.packageName }
            .map { (packageName, samples) ->
                val temperatures = samples.mapNotNull { appSample ->
                    levelSamples.minByOrNull { abs(it.recordedAt - appSample.recordedAt) }
                        ?.takeIf { abs(it.recordedAt - appSample.recordedAt) <= 60_000L }
                        ?.temperatureC
                }
                val label = runCatching {
                    val info = context.packageManager.getApplicationInfo(packageName, 0)
                    context.packageManager.getApplicationLabel(info).toString()
                }.getOrDefault(packageName)
                IntervalAppSummary(
                    packageName = packageName,
                    label = label,
                    durationMs = samples.size * 30_000L,
                    averagePowerW = samples.map { it.powerW }.average(),
                    minTemperatureC = temperatures.minOrNull(),
                    maxTemperatureC = temperatures.maxOrNull(),
                )
            }.sortedWith(compareByDescending<IntervalAppSummary> { it.durationMs }.thenByDescending { it.averagePowerW })
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
            end = 20.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PageHeader(title, "${formatDate(startAt)} – ${formatDate(endAt)}", onBack) }
        item {
            UsageLevelCurveCard(
                samples = levelSamples,
                screenOnMs = screenOnMs,
                screenOnLabel = if (title == "电量记录") "上次充满后亮屏" else "本区间亮屏",
            )
        }
        item { Text("区间应用使用", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        if (appSummaries.isEmpty()) {
            item {
                Surface(color = CardBackground, shape = RoundedCornerShape(24.dp)) {
                    Text("这个区间暂无应用采样", modifier = Modifier.fillMaxWidth().padding(22.dp), color = TextSecondary)
                }
            }
        } else {
            items(appSummaries, key = { it.packageName }) { IntervalAppUsageCard(it) }
        }
    }
}

@Composable
private fun UsageLevelCurveCard(
    samples: List<TemperatureSample>,
    screenOnMs: Long,
    screenOnLabel: String,
) {
    var selectedIndex by remember(samples) { mutableStateOf<Int?>(null) }
    val selected = selectedIndex?.let(samples::getOrNull)
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = CardBackground)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("电量曲线", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                selected?.let { "${formatTime(it.recordedAt)} · ${it.level}% · ${oneDecimal(it.temperatureC, "°C")} · ${if (it.isCharging) "充电" else "放电"}" }
                    ?: "点按或左右滑动查看电量与温度",
                color = TextSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ChartLegendDot(ChargeGreenDark, "充电")
                ChartLegendDot(DischargeRed, "放电")
            }
            Spacer(Modifier.height(16.dp))
            if (samples.size < 2) {
                Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                    Text("暂无足够数据", color = TextSecondary)
                }
            } else {
                fun selectAt(x: Float, width: Float, left: Float) {
                    val fraction = ((x - left) / (width - left)).coerceIn(0f, 1f)
                    val target = samples.first().recordedAt +
                        ((samples.last().recordedAt - samples.first().recordedAt) * fraction).toLong()
                    selectedIndex = samples.indices.minByOrNull { abs(samples[it].recordedAt - target) }
                }
                Canvas(
                    Modifier.fillMaxWidth().height(190.dp)
                        .pointerInput(samples) {
                            detectTapGestures { selectAt(it.x, size.width.toFloat(), 42.dp.toPx()) }
                        }
                        .pointerInput(samples) {
                            detectHorizontalDragGestures(
                                onDragStart = { selectAt(it.x, size.width.toFloat(), 42.dp.toPx()) },
                                onHorizontalDrag = { change, _ -> change.consume(); selectAt(change.position.x, size.width.toFloat(), 42.dp.toPx()) },
                            )
                        },
                ) {
                    val left = 42.dp.toPx()
                    val width = size.width - left
                    val duration = (samples.last().recordedAt - samples.first().recordedAt).coerceAtLeast(1L)
                    val paint = Paint().apply { color = android.graphics.Color.rgb(114, 120, 115); textSize = 10.sp.toPx(); isAntiAlias = true }
                    listOf(100, 75, 50, 25, 0).forEachIndexed { index, value ->
                        val y = size.height * index / 4f
                        drawLine(TrackColor, Offset(left, y), Offset(size.width, y), 1.dp.toPx())
                        drawContext.canvas.nativeCanvas.drawText("$value%", 0f, (y + paint.textSize).coerceAtMost(size.height), paint)
                    }
                    samples.zipWithNext().forEach { (first, second) ->
                        val x1 = left + ((first.recordedAt - samples.first().recordedAt).toFloat() / duration) * width
                        val x2 = left + ((second.recordedAt - samples.first().recordedAt).toFloat() / duration) * width
                        val segmentColor = when {
                            second.level > first.level -> ChargeGreenDark
                            second.level < first.level -> DischargeRed
                            first.isCharging || second.isCharging -> ChargeGreenDark
                            else -> DischargeRed
                        }
                        val missingInterval = second.recordedAt - first.recordedAt > MISSING_SAMPLE_INTERVAL_MS
                        val y1 = size.height * (1f - first.level / 100f)
                        val y2 = size.height * (1f - second.level / 100f)
                        drawLine(
                            color = if (missingInterval) segmentColor.copy(alpha = 0.55f) else segmentColor,
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = if (missingInterval) {
                                PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 7.dp.toPx()))
                            } else null,
                        )
                        if (missingInterval) {
                            drawCircle(segmentColor, 2.5.dp.toPx(), Offset(x1, y1))
                            drawCircle(segmentColor, 2.5.dp.toPx(), Offset(x2, y2))
                        }
                    }
                    selected?.let { point ->
                        val x = left + ((point.recordedAt - samples.first().recordedAt).toFloat() / duration) * width
                        val y = size.height * (1f - point.level / 100f)
                        drawLine(ChargeGreenDark.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
                        drawCircle(Color.White, 7.dp.toPx(), Offset(x, y)); drawCircle(ChargeGreenDark, 4.dp.toPx(), Offset(x, y))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "$screenOnLabel ${formatDuration(screenOnMs)}",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun IntervalAppUsageCard(summary: IntervalAppSummary) {
    val context = LocalContext.current
    val icon = remember(summary.packageName) {
        runCatching { context.packageManager.getApplicationIcon(summary.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    Surface(modifier = Modifier.fillMaxWidth(), color = CardBackground, shape = RoundedCornerShape(24.dp)) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(TrackColor), contentAlignment = Alignment.Center) {
                if (icon != null) Image(icon, summary.label, Modifier.fillMaxSize()) else Text(summary.label.take(1), fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.size(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(summary.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("前台约 ${formatDuration(summary.durationMs)} · 平均 ${oneDecimal(summary.averagePowerW, " W")}", color = TextSecondary, fontSize = 12.sp)
                Text(
                    if (summary.minTemperatureC != null && summary.maxTemperatureC != null) "使用时温度 ${oneDecimal(summary.minTemperatureC, "°C")} – ${oneDecimal(summary.maxTemperatureC, "°C")}" else "使用时温度暂无采样",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

private data class DailyHealthSummary(
    val date: LocalDate,
    val sessions: List<ChargeSession>,
    val chargedMah: Double,
    val equivalentCycles: Double,
    val systemCycleCount: Int,
    val estimatedHealthPct: Double,
)

@Composable
private fun AppUsageScreen(
    powerAverages: List<AppPowerAverage>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val reader = remember(context) { AppUsageReader(context.applicationContext) }
    var hasPermission by remember { mutableStateOf(reader.hasPermission()) }
    var rows by remember { mutableStateOf(if (hasPermission) reader.readLast24Hours() else emptyList()) }
    fun refreshUsage() {
        hasPermission = reader.hasPermission()
        rows = if (hasPermission) reader.readLast24Hours() else emptyList()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshUsage()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshUsage() }
    val sortedRows = remember(rows, powerAverages) {
        val maxForeground = rows.maxOfOrNull { it.foregroundMs }?.coerceAtLeast(1L)?.toDouble() ?: 1.0
        val maxPower = powerAverages.maxOfOrNull { it.averagePowerW }?.coerceAtLeast(0.1) ?: 0.1
        rows.sortedByDescending { row ->
            val timeScore = row.foregroundMs / maxForeground
            val powerScore = powerAverages.firstOrNull { it.packageName == row.packageName }
                ?.averagePowerW?.div(maxPower) ?: 0.0
            timeScore * 0.65 + powerScore * 0.35
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
            end = 20.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageHeader("应用使用情况", "近 24 小时前台与后台活动", onBack)
        }
        if (!hasPermission) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = CardBackground,
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Column(modifier = Modifier.padding(22.dp)) {
                        Icon(Icons.Rounded.Apps, contentDescription = null, tint = ChargeGreenDark, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(14.dp))
                        Text("需要使用情况访问权限", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "授权后可读取每个应用的前台使用时长和前台服务时长。",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                permissionLauncher.launch(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ChargeGreen),
                        ) {
                            Text("授予使用情况访问", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (rows.isEmpty()) {
            item {
                Surface(color = CardBackground, shape = RoundedCornerShape(24.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("暂时没有使用记录", fontWeight = FontWeight.Bold)
                        Text("使用一段时间后再来看", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        } else {
            item {
                Surface(color = TrackColor.copy(alpha = 0.65f), shape = RoundedCornerShape(20.dp)) {
                    Text(
                        "列表按前台时长 65% 与前台平均功率 35% 综合排序。功率按应用位于前台时的整机放电采样计算，充电期间不计入；后台时长采用系统记录的前台服务活动时间。",
                        modifier = Modifier.padding(16.dp),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
            items(sortedRows, key = { it.packageName }) { row ->
                AppUsageCard(
                    row = row,
                    power = powerAverages.firstOrNull { it.packageName == row.packageName },
                )
            }
        }
    }
}

@Composable
private fun AppUsageCard(row: AppUsageRow, power: AppPowerAverage?) {
    val context = LocalContext.current
    val appIcon = remember(row.packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(row.packageName)
                .toBitmap(width = 96, height = 96)
                .asImageBitmap()
        }.getOrNull()
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CardBackground,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(TrackColor),
                    contentAlignment = Alignment.Center,
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = "${row.label}图标",
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(row.label.take(1).uppercase(), fontWeight = FontWeight.Black, color = TextPrimary)
                    }
                }
                Spacer(Modifier.size(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.label, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(row.packageName, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                UsageValue("前台时长", formatDuration(row.foregroundMs))
                UsageValue("后台时长", formatDuration(row.backgroundServiceMs))
            }
            Spacer(Modifier.height(12.dp))
            UsageValue(
                "前台平均功率",
                if (power != null && power.sampleCount > 0) {
                    String.format(Locale.getDefault(), "%.1f W · %d 次采样", power.averagePowerW, power.sampleCount)
                } else {
                    "等待放电采样"
                },
            )
        }
    }
}

@Composable
private fun UsageValue(title: String, value: String) {
    Column(modifier = Modifier.padding(end = 8.dp)) {
        Text(title, color = TextSecondary, fontSize = 11.sp)
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatDuration(milliseconds: Long): String {
    if (milliseconds <= 0L) return "0 分钟"
    val totalMinutes = (milliseconds / 60_000L).coerceAtLeast(1L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}小时 ${minutes}分" else "${minutes}分钟"
}

private fun currentScalePowerLabel(exponent: Int): String {
    val superscript = exponent.toString().map { character ->
        when (character) {
            '-' -> '⁻'
            '0' -> '⁰'
            '1' -> '¹'
            '2' -> '²'
            '3' -> '³'
            '4' -> '⁴'
            '5' -> '⁵'
            '6' -> '⁶'
            else -> character
        }
    }.joinToString("")
    return "10$superscript"
}

private fun currentScaleLabel(exponent: Int): String = when (exponent) {
    -6 -> "0.000001×"
    -5 -> "0.00001×"
    -4 -> "0.0001×"
    -3 -> "0.001×"
    -2 -> "0.01×"
    -1 -> "0.1×"
    0 -> "1×"
    1 -> "10×"
    2 -> "100×"
    3 -> "1,000×"
    4 -> "10,000×"
    5 -> "100,000×"
    else -> "1,000,000×"
}

@Composable
private fun PowerBandSelector(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minValue: Int = 10,
    maxValue: Int = 300,
    step: Int = 1,
    majorEvery: Int = 10,
    pixelsPerStep: Float = 8f,
    labelFormatter: (Int) -> String = { "$it" },
) {
    val haptic = LocalHapticFeedback.current
    var selectorValue by remember { mutableIntStateOf(value) }
    LaunchedEffect(value) { selectorValue = value }
    val accumulatedPixels = remember { floatArrayOf(0f) }
    val bandColor = Color(0xFF172019)
    val pixelsPerStepDp = pixelsPerStep
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val flingScope = rememberCoroutineScope()
    var flingJob by remember { mutableStateOf<Job?>(null) }

    fun applyMovement(pixelDelta: Float) {
        val movingTowardLower = pixelDelta < 0f
        val movingTowardUpper = pixelDelta > 0f
        val resistedDelta = if (
            (selectorValue <= minValue && movingTowardLower) ||
            (selectorValue >= maxValue && movingTowardUpper)
        ) pixelDelta * 0.18f else pixelDelta
        accumulatedPixels[0] += resistedDelta
        val stepCount = (accumulatedPixels[0] / pixelsPerStepDp).toInt()
        if (stepCount == 0) return
        val previous = selectorValue
        val next = (previous + stepCount * step).coerceIn(minValue, maxValue)
        val moved = next - previous
        if (moved != 0) {
            selectorValue = next
            onValueChange(next)
            repeat(abs(moved / step)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            accumulatedPixels[0] -= (moved / step) * pixelsPerStepDp
        } else {
            accumulatedPixels[0] = 0f
        }
    }

    val dragState = rememberDraggableState { dragAmount ->
        applyMovement(-dragAmount / density)
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(94.dp)
            .clip(RoundedCornerShape(47.dp))
            .background(bandColor)
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStarted = {
                    flingJob?.cancel()
                    accumulatedPixels[0] = 0f
                },
                onDragStopped = { velocity ->
                    flingJob?.cancel()
                    flingJob = flingScope.launch {
                        var speed = (-velocity / density).coerceIn(-1_800f, 1_800f)
                        var lastFrame = 0L
                        while (isActive && abs(speed) > 7f) {
                            androidx.compose.runtime.withFrameNanos { frameTime ->
                                if (lastFrame != 0L) {
                                    val deltaSeconds = ((frameTime - lastFrame) / 1_000_000_000f)
                                        .coerceIn(0.008f, 0.032f)
                                    val movement = speed * deltaSeconds
                                    val before = selectorValue
                                    applyMovement(movement)
                                    val pushingPastEdge = before == selectorValue && (
                                        (selectorValue == minValue && movement < 0f) ||
                                            (selectorValue == maxValue && movement > 0f)
                                        )
                                    if (pushingPastEdge) speed = 0f
                                    speed *= exp(-2.7f * deltaSeconds)
                                }
                                lastFrame = frameTime
                            }
                        }
                    }
                },
            ),
    ) {
        val centerX = size.width / 2f
        val pixelsPerStepPx = pixelsPerStep.dp.toPx()
        val labelPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 11.sp.toPx()
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        for (tick in minValue..maxValue step step) {
            val x = centerX + ((tick - selectorValue) / step.toFloat()) * pixelsPerStepPx
            if (x !in -30.dp.toPx()..size.width + 30.dp.toPx()) continue
            val distance = abs(x - centerX) / (size.width / 2f)
            val alpha = (1f - distance).coerceIn(0.12f, 1f)
            val major = tick % majorEvery == 0
            drawLine(
                color = Color.White.copy(alpha = if (major) alpha * 0.9f else alpha * 0.4f),
                start = Offset(x, if (major) 23.dp.toPx() else 31.dp.toPx()),
                end = Offset(x, if (major) 55.dp.toPx() else 48.dp.toPx()),
                strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
            if (major && x in 24.dp.toPx()..size.width - 24.dp.toPx()) {
                labelPaint.alpha = (alpha * 255).roundToInt()
                drawContext.canvas.nativeCanvas.drawText(labelFormatter(tick), x, 76.dp.toPx(), labelPaint)
            }
        }
        drawRect(
            brush = Brush.horizontalGradient(
                0f to bandColor,
                0.18f to Color.Transparent,
                0.82f to Color.Transparent,
                1f to bandColor,
            ),
        )
        drawRoundRect(
            color = ChargeGreen,
            topLeft = Offset(centerX - 2.dp.toPx(), 15.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(4.dp.toPx(), 51.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
        )
    }
}

@Composable
private fun DualCellModeChoice(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(18.dp)).noIndicationClickable(onClick = onClick),
        color = if (selected) ChargeGreen.copy(alpha = 0.14f) else TrackColor.copy(alpha = 0.65f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) ChargeGreenDark else TextSecondary,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.size(7.dp))
            Text(
                label,
                color = if (selected) ChargeGreenDark else TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    ratedMaxPowerW: Double,
    ratedCapacityMah: Int,
    dualCellEnabled: Boolean,
    dualCellMode: DualCellMode,
    notificationEnabled: Boolean,
    fluidCloudEnabled: Boolean,
    hideFromRecents: Boolean,
    currentDirectionInverted: Boolean,
    currentScaleExponent: Int,
    updateStatus: String,
    updateUrl: String?,
    onRatedPowerChange: (Double) -> Unit,
    onRatedCapacityChange: (Int) -> Unit,
    onDualCellChange: (Boolean) -> Unit,
    onDualCellModeChange: (DualCellMode) -> Unit,
    onNotificationEnabledChange: (Boolean) -> Unit,
    onFluidCloudEnabledChange: (Boolean) -> Unit,
    onHideFromRecentsChange: (Boolean) -> Unit,
    onCurrentDirectionInvertedChange: (Boolean) -> Unit,
    onCurrentScaleExponentChange: (Int) -> Unit,
    onCheckForUpdates: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val powerManager = context.getSystemService(PowerManager::class.java)
    val ignoresBatteryOptimization = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    val fluidCloudRoute = remember { FluidCloudPublisher.detectRoute(context) }
    var fluidCloudStatus by remember { mutableStateOf("") }
    fun refreshFluidCloudStatus() {
        fluidCloudStatus = context.getSharedPreferences(
            FluidCloudPublisher.PREFERENCES,
            android.content.Context.MODE_PRIVATE,
        ).getString(FluidCloudPublisher.KEY_LAST_STATUS, "").orEmpty()
    }
    var promotedNotificationsAllowed by remember { mutableStateOf(false) }
    fun refreshPromotedNotificationAccess() {
        promotedNotificationsAllowed = Build.VERSION.SDK_INT >= 36 &&
            notificationManager.canPostPromotedNotifications()
    }
    LaunchedEffect(Unit) {
        refreshPromotedNotificationAccess()
        while (true) {
            refreshFluidCloudStatus()
            kotlinx.coroutines.delay(1_000L)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refreshPromotedNotificationAccess()
        refreshFluidCloudStatus()
    }
    var sliderValue by remember(ratedMaxPowerW) { mutableStateOf(ratedMaxPowerW.toFloat()) }
    var capacityValue by remember(ratedCapacityMah) { mutableIntStateOf(ratedCapacityMah) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
            end = 20.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageHeader("设置", "充电规格、采样与应用选项", onBack)
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = CardBackground,
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text("厂商标称最大功率", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "${sliderValue.roundToInt()} W",
                        color = ChargeGreenDark,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                    )
                    PowerBandSelector(
                        value = sliderValue.roundToInt(),
                        onValueChange = {
                            sliderValue = it.toFloat()
                            onRatedPowerChange(it.toDouble())
                        },
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("10 W", color = TextSecondary, fontSize = 12.sp)
                        Text("300 W", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardBackground,
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().noIndicationClickable {
                            onCurrentDirectionInvertedChange(!currentDirectionInverted)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("电流方向取反", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                if (currentDirectionInverted) "已开启 · 交换充电与放电方向" else "已关闭 · 使用自动识别方向",
                                color = TextSecondary,
                                fontSize = 12.sp,
                            )
                        }
                        Switch(
                            checked = currentDirectionInverted,
                            onCheckedChange = onCurrentDirectionInvertedChange,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("电流单位倍率", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "${currentScalePowerLabel(currentScaleExponent)}  ·  ${currentScaleLabel(currentScaleExponent)}",
                        color = ChargeGreenDark,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "用于校正部分系统返回的电流单位，默认 ×1",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    PowerBandSelector(
                        value = currentScaleExponent,
                        onValueChange = onCurrentScaleExponentChange,
                        minValue = -6,
                        maxValue = 6,
                        step = 1,
                        majorEvery = 1,
                        pixelsPerStep = 46f,
                        labelFormatter = ::currentScalePowerLabel,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("10⁻⁶ · 0.000001×", color = TextSecondary, fontSize = 12.sp)
                        Text("10⁶ · 1,000,000×", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = CardBackground,
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text("厂商标称电池容量", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "$capacityValue mAh",
                        color = ChargeGreenDark,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Black,
                    )
                    PowerBandSelector(
                        value = capacityValue,
                        onValueChange = {
                            capacityValue = it
                            onRatedCapacityChange(it)
                        },
                        minValue = 1_000,
                        maxValue = 20_000,
                        step = 100,
                        majorEvery = 1_000,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("1000 mAh", color = TextSecondary, fontSize = 12.sp)
                        Text("20000 mAh", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardBackground,
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().noIndicationClickable { onDualCellChange(!dualCellEnabled) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("双电芯整包换算", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                when {
                                    !dualCellEnabled -> "已关闭 · 按系统原始值显示"
                                    dualCellMode == DualCellMode.Voltage -> "已开启 · 电压按 ×2 显示"
                                    else -> "已开启 · 电流按 ×2 显示"
                                },
                                color = TextSecondary,
                                fontSize = 12.sp,
                            )
                            Text("两种方式的总功率均按 ×2 计算", color = TextSecondary, fontSize = 11.sp)
                        }
                        Switch(checked = dualCellEnabled, onCheckedChange = onDualCellChange)
                    }
                    if (dualCellEnabled) {
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DualCellModeChoice(
                                label = "电压 ×2",
                                icon = Icons.Rounded.ElectricMeter,
                                selected = dualCellMode == DualCellMode.Voltage,
                                onClick = { onDualCellModeChange(DualCellMode.Voltage) },
                                modifier = Modifier.weight(1f),
                            )
                            DualCellModeChoice(
                                label = "电流 ×2",
                                icon = Icons.Rounded.Speed,
                                selected = dualCellMode == DualCellMode.Current,
                                onClick = { onDualCellModeChange(DualCellMode.Current) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardBackground,
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noIndicationClickable { onNotificationEnabledChange(!notificationEnabled) }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = if (notificationEnabled) ChargeGreenDark else TextSecondary,
                    )
                    Spacer(Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("状态栏实时通知", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            if (notificationEnabled) {
                                "已开启 · 同时保持后台持续记录"
                            } else {
                                "已关闭 · 后台持续记录已停止"
                            },
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = notificationEnabled,
                        onCheckedChange = onNotificationEnabledChange,
                    )
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardBackground,
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noIndicationClickable { onHideFromRecentsChange(!hideFromRecents) }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("隐藏最近任务卡片", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            if (hideFromRecents) {
                                "已开启 · 离开应用后不在最近任务中显示"
                            } else {
                                "已关闭 · 在最近任务中保留应用卡片"
                            },
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = hideFromRecents,
                        onCheckedChange = onHideFromRecentsChange,
                    )
                }
            }
        }
        item {
            Surface(color = TrackColor.copy(alpha = 0.65f), shape = RoundedCornerShape(22.dp)) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("当前设备：${Build.MODEL}", fontWeight = FontWeight.Bold)
                    Text(
                        "Android 没有统一接口提供包装或充电器上标注的最大功率。当前机型默认使用 100 W，你可以按充电器铭牌修改。这个数值只决定仪表满圈范围，实时功率仍来自电池数据。",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).noIndicationClickable {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    )
                    runCatching { context.startActivity(intent) }.onFailure {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                },
                color = if (ignoresBatteryOptimization) ChargeGreen.copy(alpha = 0.11f) else CardBackground,
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.BatteryChargingFull,
                        contentDescription = null,
                        tint = if (ignoresBatteryOptimization) ChargeGreenDark else TextPrimary,
                    )
                    Spacer(Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("后台持续记录", fontWeight = FontWeight.Bold)
                        Text(
                            if (ignoresBatteryOptimization) "已允许忽略电池优化" else "点击允许忽略电池优化",
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextSecondary)
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).noIndicationClickable {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                },
                color = CardBackground,
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, tint = TextPrimary)
                    Spacer(Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ColorOS 后台权限", fontWeight = FontWeight.Bold)
                        Text("在应用详情中允许后台活动与自启动", color = TextSecondary, fontSize = 12.sp)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextSecondary)
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardBackground,
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = fluidCloudRoute != FluidCloudPublisher.Route.UNSUPPORTED,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onFluidCloudEnabledChange(!fluidCloudEnabled) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = if (fluidCloudEnabled && fluidCloudRoute != FluidCloudPublisher.Route.UNSUPPORTED) {
                                ChargeGreenDark
                            } else {
                                TextSecondary
                            },
                        )
                        Spacer(Modifier.size(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("充电流体云", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                when {
                                    fluidCloudRoute == FluidCloudPublisher.Route.UNSUPPORTED -> "当前系统不支持流体云接口"
                                    !fluidCloudEnabled -> "已关闭 · ${FluidCloudPublisher.routeLabel(context)}"
                                    fluidCloudStatus.isNotBlank() -> fluidCloudStatus
                                    else -> "已开启 · ${FluidCloudPublisher.routeLabel(context)}"
                                },
                                color = TextSecondary,
                                fontSize = 12.sp,
                            )
                        }
                        Switch(
                            checked = fluidCloudEnabled && fluidCloudRoute != FluidCloudPublisher.Route.UNSUPPORTED,
                            enabled = fluidCloudRoute != FluidCloudPublisher.Route.UNSUPPORTED,
                            onCheckedChange = onFluidCloudEnabledChange,
                        )
                    }
                    if (Build.VERSION.SDK_INT >= 36) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    val intent = Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS").apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    runCatching { context.startActivity(intent) }.onFailure {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.parse("package:${context.packageName}"),
                                            ),
                                        )
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (promotedNotificationsAllowed) {
                                    "系统实时通知权限已允许"
                                } else {
                                    "点击允许系统实时通知权限"
                                },
                                color = if (promotedNotificationsAllowed) ChargeGreenDark else TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (updateStatus.startsWith("发现新版本") && updateUrl != null) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl)))
                        } else {
                            onCheckForUpdates()
                        }
                    },
                color = CardBackground,
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, contentDescription = null, tint = ChargeGreenDark)
                    Spacer(Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("检查更新", fontWeight = FontWeight.Bold)
                        Text(updateStatus, color = TextSecondary, fontSize = 12.sp)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun HealthScreen(
    reading: BatteryReading,
    sessions: List<ChargeSession>,
    temperatureSamples: List<TemperatureSample>,
    onBack: () -> Unit,
    onSessionClick: (ChargeSession) -> Unit,
) {
    val estimatedHealth = remember(sessions, reading.designCapacityMah) {
        estimatedBatteryHealth(sessions, reading.designCapacityMah)
    }
    val validHealthSamples = remember(sessions) { sessions.count(::isUsableHealthEstimate).coerceAtMost(5) }
    val daily = remember(sessions, reading.designCapacityMah) {
        sessions.groupBy {
            Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }.map { (date, daySessions) ->
            val measured = daySessions.filter(::isUsableHealthEstimate)
            val measuredGain = measured.sumOf { (it.endLevel - it.startLevel).coerceAtLeast(0) }
            DailyHealthSummary(
                date = date,
                sessions = daySessions,
                chargedMah = daySessions.sumOf { it.chargedMah },
                equivalentCycles = daySessions.sumOf { it.equivalentCycles },
                systemCycleCount = daySessions.maxOfOrNull { it.systemCycleCount } ?: 0,
                estimatedHealthPct = if (measuredGain > 0) {
                    val fullCapacity = measured.sumOf { it.chargedMah } / (measuredGain / 100.0)
                    (fullCapacity / reading.designCapacityMah.coerceAtLeast(1) * 100.0).coerceIn(0.0, 100.0)
                } else 0.0,
            )
        }.sortedByDescending { it.date }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
            end = 20.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageHeader("电池健康", "容量、循环与高电量损耗估算", onBack)
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF172019),
                shape = RoundedCornerShape(30.dp),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "电池健康度估算",
                        color = Color.White.copy(alpha = 0.68f),
                        fontSize = 13.sp,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            estimatedHealth?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "--",
                            color = Color.White,
                            fontSize = 58.sp,
                            lineHeight = 60.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            if (estimatedHealth != null) "%" else "",
                            color = ChargeGreen,
                            fontSize = 24.sp,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Text(
                        if (validHealthSamples < 5) "已收集 $validHealthSamples/5 次有效充电" else "最近 5 次有效充电",
                        color = Color.White.copy(alpha = 0.68f),
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DetailStat("系统状态", reading.health)
                        DetailStat("系统循环", "${reading.cycleCount} 次")
                        DetailStat("标称容量", "${reading.designCapacityMah} mAh")
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    title = "累计充入",
                    value = String.format(
                        Locale.getDefault(),
                        "%.0f mAh",
                        sessions.sumOf { it.chargedMah },
                    ),
                    subtitle = "从安装后开始统计",
                    icon = Icons.Rounded.BatteryChargingFull,
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = "加权循环",
                    value = String.format(Locale.getDefault(), "%.2f 次", sessions.sumOf { it.equivalentCycles }),
                    subtitle = "高电量区权重更高",
                    icon = Icons.Rounded.HealthAndSafety,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Surface(color = ChargeGreen.copy(alpha = 0.09f), shape = RoundedCornerShape(22.dp)) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("健康度权重", fontWeight = FontWeight.Bold, color = ChargeGreenDark)
                    Text(
                        "采用 AccuBattery 2.0 的样本标准：只有单次电量增加至少 60 个百分点的记录才参与估算，并仅使用最近 5 次有效充电。小于 60% 的记录仍会显示，但不进入健康度。5 次样本按实际充入量与电量增幅合并计算。",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
        item {
            TemperatureHistoryCard(
                samples = temperatureSamples.filter { it.recordedAt >= System.currentTimeMillis() - 24L * 60L * 60L * 1000L },
            )
        }
        item { Text("每日记录", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        if (daily.isEmpty()) {
            item {
                Surface(modifier = Modifier.fillMaxWidth(), color = CardBackground, shape = RoundedCornerShape(24.dp)) {
                    Text("完成一次充电后会生成每日健康记录", modifier = Modifier.padding(24.dp), color = TextSecondary)
                }
            }
        }
        items(daily, key = { it.date.toEpochDay() }) { day ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardBackground,
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                        Text(day.date.format(DateTimeFormatter.ofPattern("M月d日")), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            "${day.sessions.size} 次充电 · ${String.format(Locale.getDefault(), "%.0f", day.chargedMah)} mAh · 加权 ${String.format(Locale.getDefault(), "%.2f", day.equivalentCycles)} 循环",
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )
                        Text(
                            "系统循环 ${day.systemCycleCount} 次 · " + if (day.estimatedHealthPct > 0) {
                                "容量估算 ${String.format(Locale.getDefault(), "%.0f%%", day.estimatedHealthPct)}"
                            } else "当天没有达到 60% 的有效样本",
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    day.sessions.sortedByDescending { it.startedAt }.forEachIndexed { index, session ->
                        if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TrackColor))
                        Row(
                            modifier = Modifier.fillMaxWidth().noIndicationClickable { onSessionClick(session) }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${formatTime(session.startedAt)} · ${session.startLevel}% → ${session.endLevel}%", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("充入 ${String.format(Locale.getDefault(), "%.0f", session.chargedMah)} mAh · ${formatDuration(session)}", color = TextSecondary, fontSize = 12.sp)
                                if (isUsableHealthEstimate(session)) {
                                    val fullCapacity = session.chargedMah / ((session.endLevel - session.startLevel) / 100.0)
                                    Text(
                                        "计入健康度 · 推算满充 ${String.format(Locale.getDefault(), "%.0f", fullCapacity)} mAh · ${String.format(Locale.getDefault(), "%.0f%%", capacityHealthForSession(session, reading.designCapacityMah))}",
                                        color = ChargeGreenDark,
                                        fontSize = 12.sp,
                                    )
                                } else {
                                    Text("未计入健康度 · 单次充入不足 60%", color = TextSecondary, fontSize = 12.sp)
                                }
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: ChargeSession, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).noIndicationClickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = CardBackground,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(ChargeGreen.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = ChargeGreenDark)
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(formatDate(session.startedAt), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "${session.startLevel}% → ${session.endLevel}%  ·  ${formatDuration(session)}",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
                Text(
                    "峰值 ${oneDecimal(session.peakPowerW, " W")}  ·  ${twoDecimals(session.energyWh, " Wh")}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
                Text(
                    when {
                        session.endedAt == null -> "充电进行中 · 达到 60% 后成为有效样本"
                        isUsableHealthEstimate(session) -> "健康度有效样本"
                        else -> "未纳入健康度 · 单次充入不足 60%"
                    },
                    color = if (isUsableHealthEstimate(session)) ChargeGreenDark else TextSecondary,
                    fontSize = 11.sp,
                )
            }
            if (session.endedAt == null) {
                Surface(shape = CircleShape, color = ChargeGreen.copy(alpha = 0.14f)) {
                    Text("进行中", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = ChargeGreenDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextSecondary)
            }
        }
    }
}

@Composable
private fun DetailScreen(
    session: ChargeSession?,
    samples: List<ChargeSample>,
    reading: BatteryReading,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
            end = 20.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageHeader(
                title = "充电详情",
                subtitle = session?.let { formatDate(it.startedAt) } ?: "当前充电",
                onBack = onBack,
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                color = ChargeGreen,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("本次峰值功率", color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp)
                    Text(
                        oneDecimal(session?.peakPowerW ?: reading.powerW, " W"),
                        color = Color.White,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DetailStat("充入电量", twoDecimals(session?.energyWh ?: 0.0, " Wh"))
                        DetailStat("电量变化", session?.let { "+${(it.endLevel - it.startLevel).coerceAtLeast(0)}%" } ?: "--")
                        DetailStat("持续时间", session?.let(::formatDuration) ?: "--")
                    }
                }
            }
        }
        item { ChargeCurveCard(samples = samples, title = "功率与温度", interactive = true) }
        item { BatteryLevelCurveCard(samples) }
    }
}

@Composable
private fun DetailStat(label: String, value: String) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun ChargeNavigation(
    selected: Screen,
    onSelect: (Screen) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(34.dp)
    Box(
        modifier = modifier
            .padding(
                start = 92.dp,
                end = 92.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp,
            )
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.72f), shape)
            .height(64.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    style = HazeBlurStyle {
                        blurRadius(24.dp)
                        noiseFactor(0.035f)
                        backgroundColor(Color.White.copy(alpha = 0.52f))
                        colorEffects(listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.25f))))
                    },
                ),
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavItem(
                selected = selected == Screen.Home,
                onClick = { onSelect(Screen.Home) },
                icon = Icons.Rounded.Home,
                label = "首页",
                modifier = Modifier.weight(1f),
            )
            BottomNavItem(
                selected = selected == Screen.History,
                onClick = { onSelect(Screen.History) },
                icon = Icons.Rounded.History,
                label = "历史",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) ChargeGreen else TextSecondary,
            modifier = Modifier.size(24.dp),
        )
        Text(
            label,
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun Modifier.noIndicationClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = clickable(
    enabled = enabled,
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

private val dateFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")
private const val MISSING_SAMPLE_INTERVAL_MS = 2L * 60L * 1000L
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val timeSecondsFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun formatDate(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault()).format(dateFormatter)

private fun formatTime(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault()).format(timeFormatter)

private fun formatTimeSeconds(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault()).format(timeSecondsFormatter)

private fun formatDuration(session: ChargeSession): String {
    val end = session.endedAt ?: System.currentTimeMillis()
    val minutes = ((end - session.startedAt).coerceAtLeast(0L) / 60_000L)
    return if (minutes >= 60) "${minutes / 60}小时${minutes % 60}分" else "${minutes}分钟"
}

private fun oneDecimal(value: Double, suffix: String) = String.format(Locale.getDefault(), "%.1f%s", value, suffix)
private fun flowPowerLabel(powerW: Double): String =
    "${if (powerW >= 0.0) "充电" else "放电"} ${oneDecimal(abs(powerW), " W")}"
private fun twoDecimals(value: Double, suffix: String) = String.format(Locale.getDefault(), "%.2f%s", value, suffix)

private fun temperatureLabel(value: Double) = when {
    value >= 45 -> "温度偏高"
    value >= 38 -> "温热"
    value <= 10 -> "温度偏低"
    else -> "温度正常"
}

private fun estimatedBatteryHealth(
    sessions: List<ChargeSession>,
    designCapacityMah: Int,
): Double? {
    val measured = sessions
        .filter(::isUsableHealthEstimate)
        .sortedByDescending { it.endedAt ?: it.startedAt }
        .take(5)
    if (measured.size < 5) return null
    val totalGain = measured.sumOf { (it.endLevel - it.startLevel).coerceAtLeast(0) }
    if (totalGain <= 0) return null
    val estimatedFullCapacity = measured.sumOf { it.chargedMah } / (totalGain / 100.0)
    return (estimatedFullCapacity / designCapacityMah.coerceAtLeast(1) * 100.0).coerceIn(0.0, 100.0)
}

private fun isUsableHealthEstimate(session: ChargeSession): Boolean =
    session.chargedMah > 0.0 && session.endLevel - session.startLevel >= 60

private fun capacityHealthForSession(session: ChargeSession, designCapacityMah: Int): Double {
    val gainedPercent = (session.endLevel - session.startLevel).coerceAtLeast(1)
    val estimatedFullCapacity = session.chargedMah / (gainedPercent / 100.0)
    return (estimatedFullCapacity / designCapacityMah.coerceAtLeast(1) * 100.0).coerceIn(50.0, 100.0)
}
