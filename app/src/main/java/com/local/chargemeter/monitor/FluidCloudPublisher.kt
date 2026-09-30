package com.local.chargemeter.monitor

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import com.local.chargemeter.data.BatteryReading
import org.json.JSONObject
import org.json.JSONArray
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/** Selects the public Android 16 route or ColorOS 15 intent-sharing route at runtime. */
class FluidCloudPublisher(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun route(): Route = detectRoute(context)

    fun publish(reading: BatteryReading) {
        if (!preferences.getBoolean(KEY_ENABLED, true) || route() != Route.COLOR_OS_15) return

        val hasPreviousEntity = preferences.getBoolean(KEY_ACTIVE, false)
        val wasActive = hasPreviousEntity &&
            preferences.getInt(KEY_SCHEMA_VERSION, 0) == SCHEMA_VERSION
        if (!reading.isPowerConnected && !hasPreviousEntity) return

        val identifier = if (hasPreviousEntity) {
            preferences.getString(KEY_IDENTIFIER, null)
        } else {
            null
        } ?: UUID.randomUUID().toString()
        val actionStatus = when {
            !reading.isPowerConnected -> ACTION_END
            wasActive -> ACTION_UPDATE
            else -> ACTION_CREATE
        }

        val result = runCatching {
            val authority = Settings.Global.getString(
                context.contentResolver,
                "intelligent_intent_authorities",
            )?.takeIf { it.isNotBlank() && it != "null" } ?: DEFAULT_AUTHORITY
            val extras = Bundle().apply {
                putString("intentData", payload(reading, identifier, actionStatus).toString())
            }
            context.contentResolver.acquireContentProviderClient(authority)?.use { client ->
                client.call("shareIntent", null, extras)
            } ?: error("未找到系统意图共享服务")
        }

        val response = result.getOrNull()?.getString("result")
        val status = when {
            result.isFailure -> "ColorOS 15 接口调用失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
            response.isNullOrBlank() -> "ColorOS 15 接口没有返回结果"
            else -> statusText(response)
        }
        Log.i(TAG, "action=$actionStatus, response=$response, status=$status")
        preferences.edit()
            .putString(KEY_LAST_STATUS, status)
            .putLong(KEY_LAST_STATUS_AT, System.currentTimeMillis())
            .apply()

        val success = response?.let(::isSuccess) == true
        if (success && actionStatus != ACTION_END) {
            preferences.edit()
                .putBoolean(KEY_ACTIVE, true)
                .putString(KEY_IDENTIFIER, identifier)
                .putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
                .apply()
        } else if (success && actionStatus == ACTION_END) {
            preferences.edit()
                .putBoolean(KEY_ACTIVE, false)
                .remove(KEY_IDENTIFIER)
                .apply()
        }
    }

    private fun payload(reading: BatteryReading, identifier: String, actionStatus: Int): JSONObject {
        val power = String.format(Locale.US, "%.1f W", abs(reading.powerW))
        val details = String.format(
            Locale.getDefault(),
            "%d%% · %.1f°C · %.2f V · %.2f A",
            reading.level,
            reading.temperatureC,
            reading.voltageV,
            abs(reading.currentA),
        )
        // TASK milestones are lifecycle states, not the battery percentage.
        // ColorOS uses these codes to select capsule behavior and card expiry.
        val (milestoneCode, milestoneText) = when (actionStatus) {
            ACTION_CREATE -> 10 to "task_start"
            ACTION_END -> 30 to "finished"
            else -> 20 to "in_progress"
        }
        return JSONObject()
            .put("intentName", TEST_INTENT_NAME)
            .put("intentVersion", "1.0")
            // Each sharing event has its own identifier; entityId stays stable.
            .put("identifier", UUID.randomUUID().toString())
            .put("timestamp", reading.timestamp)
            .put(
                "serviceId",
                JSONObject()
                    .put("launcher", TEST_LAUNCHER_SERVICE_ID)
                    .put("fluidCloud", TEST_FLUID_SERVICE_ID),
            )
            .put("intentAction", JSONObject().put("actionStatus", actionStatus))
            .put(
                "intentEntity",
                JSONObject()
                    .put("entityName", "TASK")
                    .put("entityId", identifier)
                    .put(
                        "milestone",
                        JSONObject()
                            .put("code", milestoneCode)
                            .put("text", milestoneText),
                    )
                    .put(
                        "capsule",
                        JSONObject()
                            .put("leftText", "充电")
                            .put("rightText", power)
                            .put("legacyText", power),
                    )
                    .put(
                        "primary",
                        JSONObject()
                            .put(
                                "title",
                                JSONArray().put(
                                    JSONObject()
                                        .put("text", if (reading.isCharging) "充电中" else "电源已连接")
                                        .put("color", "#16C784")
                                        .put("darkColor", "#47E6A3"),
                                ),
                            )
                            .put("content", details),
                    )
                    .put(
                        "secondaryData",
                        JSONObject()
                            .put("type", "PROGRESS")
                            .put("progress", reading.level.coerceIn(0, 100))
                            .put("style", "inside")
                            .put("nodeLabels", JSONArray().put("0%").put("充电").put("100%")),
                    ),
            )
    }

    enum class Route {
        ANDROID_16,
        COLOR_OS_15,
        UNSUPPORTED,
    }

    companion object {
        const val PREFERENCES = "charge_settings"
        const val KEY_ENABLED = "fluid_cloud_enabled"
        const val KEY_LAST_STATUS = "fluid_cloud_last_status"
        const val KEY_LAST_STATUS_AT = "fluid_cloud_last_status_at"
        private const val KEY_ACTIVE = "fluid_cloud_active"
        private const val KEY_IDENTIFIER = "fluid_cloud_identifier"
        private const val KEY_SCHEMA_VERSION = "fluid_cloud_schema_version"
        private const val SCHEMA_VERSION = 3
        private const val DEFAULT_AUTHORITY = "IntelligentIntent"
        // OPPO's public sample IDs require its configured test environment.
        // Production intentName/serviceIds must be issued by OPPO during onboarding;
        // shareIntent returning code 0 confirms acceptance, not visible rendering.
        private const val TEST_INTENT_NAME = "Example.Progress"
        private const val TEST_LAUNCHER_SERVICE_ID = "999800001"
        private const val TEST_FLUID_SERVICE_ID = "999900001"
        private const val ACTION_CREATE = 0
        private const val ACTION_UPDATE = 1
        private const val ACTION_END = 2
        private const val TAG = "ChargeMeterFluid"

        fun detectRoute(context: Context): Route {
            if (Build.VERSION.SDK_INT >= 36) return Route.ANDROID_16
            val supportsIntent = Settings.Global.getInt(
                context.contentResolver,
                "support_intelligent_intent",
                0,
            ) > 0
            return if (Build.VERSION.SDK_INT >= 35 && supportsIntent) {
                Route.COLOR_OS_15
            } else {
                Route.UNSUPPORTED
            }
        }

        fun routeLabel(context: Context): String = when (detectRoute(context)) {
            Route.ANDROID_16 -> "Android 16 系统实时通知"
            Route.COLOR_OS_15 -> "ColorOS 15 意图共享"
            Route.UNSUPPORTED -> "当前系统不支持"
        }

        private fun isSuccess(raw: String): Boolean = runCatching {
            JSONObject(raw).optInt("code", -1) == 0
        }.getOrDefault(false)

        private fun statusText(raw: String): String = runCatching {
            val json = JSONObject(raw)
            val code = json.optInt("code", -1)
            val message = json.optString("message")
            when (code) {
                0 -> "系统已接收；测试模板显示需 OPPO 调测环境"
                10101001 -> "系统支持，但应用尚未获得 OPPO 意图共享授权"
                10103003 -> "系统流体云开关已关闭"
                else -> "ColorOS 15 返回 $code${if (message.isBlank()) "" else "：$message"}"
            }
        }.getOrElse { "ColorOS 15 返回：$raw" }
    }
}
