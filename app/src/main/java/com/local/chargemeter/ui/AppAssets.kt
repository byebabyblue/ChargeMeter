package com.local.chargemeter.ui

import android.content.Context
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Process-wide bounded caches survive lazy-list item disposal and re-entry. */
internal object AppAssets {
    private val labels = LruCache<String, String>(256)
    private val icons = LruCache<String, ImageBitmap>(128)
    private val iconLock = Mutex()

    fun label(context: Context, packageName: String): String {
        labels.get(packageName)?.let { return it }
        return runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName).also { labels.put(packageName, it) }
    }

    fun cachedIcon(packageName: String): ImageBitmap? = icons.get(packageName)

    suspend fun icon(context: Context, packageName: String): ImageBitmap? = withContext(Dispatchers.IO) {
        iconLock.withLock {
            icons.get(packageName) ?: runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()?.also { icons.put(packageName, it) }
        }
    }
}

@Composable
internal fun rememberAppIcon(context: Context, packageName: String): ImageBitmap? {
    val icon by produceState(AppAssets.cachedIcon(packageName), packageName) {
        value = AppAssets.icon(context.applicationContext, packageName)
    }
    return icon
}
