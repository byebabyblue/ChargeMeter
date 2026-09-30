package com.local.chargemeter.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.local.chargemeter.ChargeMeterApplication
import com.local.chargemeter.BuildConfig
import com.local.chargemeter.data.BatteryReading
import com.local.chargemeter.data.AppPowerAverage
import com.local.chargemeter.data.AppPowerSample
import com.local.chargemeter.data.ChargeSample
import com.local.chargemeter.data.ChargeSession
import com.local.chargemeter.data.TemperatureSample
import com.local.chargemeter.monitor.BatteryReader
import com.local.chargemeter.monitor.DualCellMode
import com.local.chargemeter.monitor.BatteryMonitorService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class ChargeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ChargeMeterApplication
    private val repository = app.repository
    private val reader = BatteryReader(application)
    private val preferences = application.getSharedPreferences("charge_settings", Context.MODE_PRIVATE)

    private val _reading = MutableStateFlow(reader.read())
    val reading: StateFlow<BatteryReading> = _reading
    private val _ratedMaxPowerW = MutableStateFlow(
        preferences.getFloat("rated_max_power_w", defaultRatedPower().toFloat()).toDouble(),
    )
    val ratedMaxPowerW: StateFlow<Double> = _ratedMaxPowerW
    private val _ratedCapacityMah = MutableStateFlow(
        preferences.getInt("rated_capacity_mah", defaultRatedCapacity()),
    )
    val ratedCapacityMah: StateFlow<Int> = _ratedCapacityMah
    private val _dualCellEnabled = MutableStateFlow(
        preferences.getBoolean("dual_cell_enabled", defaultDualCell()),
    )
    val dualCellEnabled: StateFlow<Boolean> = _dualCellEnabled
    private val _dualCellMode = MutableStateFlow(
        runCatching {
            DualCellMode.valueOf(
                preferences.getString("dual_cell_mode", DualCellMode.Current.name) ?: DualCellMode.Current.name,
            )
        }.getOrDefault(DualCellMode.Current),
    )
    val dualCellMode: StateFlow<DualCellMode> = _dualCellMode
    private val _notificationEnabled = MutableStateFlow(
        preferences.getBoolean("monitor_notification_enabled", true),
    )
    val notificationEnabled: StateFlow<Boolean> = _notificationEnabled
    private val _fluidCloudEnabled = MutableStateFlow(
        preferences.getBoolean("fluid_cloud_enabled", true),
    )
    val fluidCloudEnabled: StateFlow<Boolean> = _fluidCloudEnabled
    private val _hideFromRecents = MutableStateFlow(
        preferences.getBoolean("hide_from_recents", true),
    )
    val hideFromRecents: StateFlow<Boolean> = _hideFromRecents
    private val _currentDirectionInverted = MutableStateFlow(
        preferences.getBoolean("current_direction_inverted", false),
    )
    val currentDirectionInverted: StateFlow<Boolean> = _currentDirectionInverted
    private val _currentScaleExponent = MutableStateFlow(
        preferences.getInt("current_scale_exponent", 0).coerceIn(-6, 6),
    )
    val currentScaleExponent: StateFlow<Int> = _currentScaleExponent
    private val _updateStatus = MutableStateFlow("当前版本 ${BuildConfig.VERSION_NAME}")
    val updateStatus: StateFlow<String> = _updateStatus
    private val _updateUrl = MutableStateFlow<String?>(null)
    val updateUrl: StateFlow<String?> = _updateUrl

    val sessions: StateFlow<List<ChargeSession>> = repository.sessions.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val recentSamples: StateFlow<List<ChargeSample>> = repository.recentSamples.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val temperatureSamples: StateFlow<List<TemperatureSample>> = repository.observeTemperatureSince(
        System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val appPowerSamples: StateFlow<List<AppPowerSample>> = repository.observeAppPowerSamplesSince(
        System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val appPowerAverages: StateFlow<List<AppPowerAverage>> = repository.observeAppPowerAverages(
        System.currentTimeMillis() - 24L * 60L * 60L * 1000L,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedSessionId = MutableStateFlow<Long?>(null)
    val selectedSession: StateFlow<ChargeSession?> = selectedSessionId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.observeSession(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val selectedSamples: StateFlow<List<ChargeSample>> = selectedSessionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeSamples(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            while (isActive) {
                _reading.value = reader.read()
                delay(1_000)
            }
        }
    }

    fun selectSession(id: Long) {
        selectedSessionId.value = id
    }

    fun selectCurrentOrLatest(): Boolean {
        val session = sessions.value.firstOrNull { it.endedAt == null } ?: sessions.value.firstOrNull()
        if (session != null) selectedSessionId.value = session.id
        return session != null
    }

    fun setRatedMaxPower(value: Double) {
        val safeValue = value.coerceIn(10.0, 300.0)
        _ratedMaxPowerW.value = safeValue
        preferences.edit().putFloat("rated_max_power_w", safeValue.toFloat()).apply()
    }

    fun setRatedCapacity(value: Int) {
        val safeValue = value.coerceIn(1_000, 20_000)
        _ratedCapacityMah.value = safeValue
        preferences.edit().putInt("rated_capacity_mah", safeValue).apply()
        _reading.value = reader.read()
    }

    fun setDualCellEnabled(enabled: Boolean) {
        _dualCellEnabled.value = enabled
        preferences.edit().putBoolean("dual_cell_enabled", enabled).apply()
        _reading.value = reader.read()
    }

    fun setDualCellMode(mode: DualCellMode) {
        _dualCellMode.value = mode
        preferences.edit().putString("dual_cell_mode", mode.name).apply()
        _reading.value = reader.read()
    }

    fun setNotificationEnabled(enabled: Boolean) {
        _notificationEnabled.value = enabled
        preferences.edit().putBoolean("monitor_notification_enabled", enabled).apply()
        val serviceIntent = Intent(getApplication(), BatteryMonitorService::class.java)
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getApplication<Application>().startForegroundService(serviceIntent)
            } else {
                getApplication<Application>().startService(serviceIntent)
            }
        } else {
            getApplication<Application>().stopService(serviceIntent)
        }
    }

    fun setFluidCloudEnabled(enabled: Boolean) {
        _fluidCloudEnabled.value = enabled
        preferences.edit().putBoolean("fluid_cloud_enabled", enabled).apply()
    }

    fun setHideFromRecents(enabled: Boolean) {
        _hideFromRecents.value = enabled
        preferences.edit().putBoolean("hide_from_recents", enabled).apply()
    }

    fun setCurrentDirectionInverted(inverted: Boolean) {
        _currentDirectionInverted.value = inverted
        preferences.edit().putBoolean("current_direction_inverted", inverted).apply()
        _reading.value = reader.read()
    }

    fun setCurrentScaleExponent(exponent: Int) {
        val safeExponent = exponent.coerceIn(-6, 6)
        _currentScaleExponent.value = safeExponent
        preferences.edit().putInt("current_scale_exponent", safeExponent).apply()
        _reading.value = reader.read()
    }

    fun checkForUpdates() {
        _updateStatus.value = "正在检查更新…"
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "ChargeMeter/${BuildConfig.VERSION_NAME}")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val tag = Regex("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(body)?.groupValues?.get(1)
                    ?: error("没有找到版本号")
                val url = Regex("\\\"html_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(body)?.groupValues?.get(1)
                    ?: RELEASES_URL
                tag.removePrefix("v") to url
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { (latest, url) ->
                    _updateUrl.value = url
                    _updateStatus.value = if (isNewerVersion(latest, BuildConfig.VERSION_NAME)) {
                        "发现新版本 $latest · 点击查看"
                    } else {
                        "已是最新版本 ${BuildConfig.VERSION_NAME}"
                    }
                }.onFailure {
                    _updateStatus.value = "检查失败 · 点击重试"
                }
            }
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val left = latest.split('.').map { it.toIntOrNull() ?: 0 }
        val right = current.split('.').map { it.toIntOrNull() ?: 0 }
        repeat(maxOf(left.size, right.size)) { index ->
            val comparison = (left.getOrNull(index) ?: 0).compareTo(right.getOrNull(index) ?: 0)
            if (comparison != 0) return comparison > 0
        }
        return false
    }

    private fun defaultRatedPower(): Double = when (Build.MODEL.uppercase()) {
        "PLC110" -> 100.0
        else -> 100.0
    }

    private fun defaultRatedCapacity(): Int = when (Build.MODEL.uppercase()) {
        "PLC110" -> 6700
        else -> 5000
    }

    private fun defaultDualCell(): Boolean = Build.MODEL.equals("PLC110", ignoreCase = true)

    companion object {
        private const val LATEST_RELEASE_API = "https://api.github.com/repos/byebabyblue/ChargeMeter/releases/latest"
        const val RELEASES_URL = "https://github.com/byebabyblue/ChargeMeter/releases"
    }
}
