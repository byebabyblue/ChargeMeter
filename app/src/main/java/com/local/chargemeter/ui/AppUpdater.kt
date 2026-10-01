package com.local.chargemeter.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.local.chargemeter.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal data class AppRelease(
    val version: String, val notes: String, val pageUrl: String,
    val apkUrl: String, val size: Long,
) {
    val newer: Boolean get() {
        val latest = version.split('.').map { it.toIntOrNull() ?: 0 }
        val current = BuildConfig.VERSION_NAME.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(latest.size, current.size)) {
            val difference = (latest.getOrNull(i) ?: 0) - (current.getOrNull(i) ?: 0)
            if (difference != 0) return difference > 0
        }
        return false
    }
}

internal data class UpdateUiState(
    val visible: Boolean = false, val checking: Boolean = false,
    val release: AppRelease? = null, val message: String = "",
    val downloading: Boolean = false, val progress: Int = 0, val ready: Boolean = false,
)

internal class AppUpdater(private val context: Context) {
    private val prefs = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(DownloadManager::class.java)
    private var validatedFile: String? = null
    private var validatedModified = 0L
    private var validatedSize = 0L
    private var validatedResult = false
    private fun file(release: AppRelease) = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
        "updates/ChargeMeter-v${release.version}.apk")

    fun cached(): AppRelease? = runCatching {
        val json = JSONObject(prefs.getString("release", "") ?: "")
        AppRelease(json.getString("version"), json.getString("notes"), json.getString("pageUrl"),
            json.getString("apkUrl"), json.getLong("size"))
    }.getOrNull()

    suspend fun latest(): AppRelease = withContext(Dispatchers.IO) {
        val connection = URL("https://api.github.com/repos/byebabyblue/ChargeMeter/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "ChargeMeter/${BuildConfig.VERSION_NAME}")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val version = json.getString("tag_name").removePrefix("v")
            require(version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .first { it.getString("name") == "ChargeMeter-v$version.apk" }
            val url = apk.getString("browser_download_url")
            require(url.startsWith("https://github.com/byebabyblue/ChargeMeter/releases/download/"))
            AppRelease(version, json.optString("body"), json.getString("html_url"), url, apk.getLong("size")).also {
                val cached = JSONObject().put("version", it.version).put("notes", it.notes)
                    .put("pageUrl", it.pageUrl).put("apkUrl", it.apkUrl).put("size", it.size)
                prefs.edit().putString("release", cached.toString()).apply()
            }
        } finally { connection.disconnect() }
    }

    fun download(release: AppRelease) {
        require(release.newer)
        val destination = file(release)
        destination.parentFile?.mkdirs()
        val oldId = prefs.getLong("download_id", -1)
        if (oldId != -1L) manager.remove(oldId)
        if (destination.exists()) destination.delete()
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("充电信息 v${release.version}")
            .setDescription("应用更新 · 下载完成后可安装")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS,
                "updates/${destination.name}")
        val id = manager.enqueue(request)
        prefs.edit().putLong("download_id", id).putString("version", release.version).apply()
    }

    suspend fun status(state: UpdateUiState): UpdateUiState = withContext(Dispatchers.IO) {
        val release = state.release ?: return@withContext state
        if (!release.newer) return@withContext state.copy(ready = false, downloading = false)
        val destination = file(release)
        // A partial file is never offered as an installable package.
        if (destination.isFile && destination.length() == release.size && validApk(destination)) {
            return@withContext state.copy(ready = true, downloading = false, progress = 100,
                message = "安装包已下载，可直接安装")
        }
        if (prefs.getString("version", "") != release.version) return@withContext state
        val id = prefs.getLong("download_id", -1)
        if (id == -1L) return@withContext state
        manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val progress = if (total > 0) (bytes * 100 / total).toInt().coerceIn(0, 100) else 0
                val active = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED
                return@withContext state.copy(downloading = active, ready = false, progress = progress,
                    message = when (status) {
                        DownloadManager.STATUS_RUNNING -> "正在下载 · $progress%"
                        DownloadManager.STATUS_PENDING -> "下载已加入系统队列"
                        DownloadManager.STATUS_PAUSED -> "等待网络恢复或 Wi-Fi 连接"
                        DownloadManager.STATUS_FAILED -> "下载失败，可重新下载"
                        DownloadManager.STATUS_SUCCESSFUL -> "安装包校验失败，请重新下载"
                        else -> state.message
                    })
            }
        }
        state.copy(downloading = false, ready = false)
    }

    @Suppress("DEPRECATION")
    private fun validApk(file: File): Boolean {
        if (validatedFile == file.absolutePath && validatedModified == file.lastModified() && validatedSize == file.length()) {
            return validatedResult
        }
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES) ?: return false
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        val valid = archive.packageName == context.packageName &&
            PackageInfoCompat.getLongVersionCode(archive) > PackageInfoCompat.getLongVersionCode(installed) &&
            archive.signatures?.toList() == installed.signatures?.toList()
        validatedFile = file.absolutePath
        validatedModified = file.lastModified()
        validatedSize = file.length()
        validatedResult = valid
        return valid
    }

    suspend fun install(release: AppRelease): Boolean {
        val valid = withContext(Dispatchers.IO) { validApk(file(release)) }
        require(valid) { "安装包无效，请重新下载" }
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file(release))
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
