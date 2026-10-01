package com.local.chargemeter.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.local.chargemeter.BuildConfig
import java.util.Locale

@Composable
internal fun UpdateDialog(state: UpdateUiState, onDismiss: () -> Unit, onRetry: () -> Unit,
    onDownload: () -> Unit, onInstall: () -> Unit) {
    if (!state.visible) return
    val palette = LocalAppPalette.current
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)) {
        Surface(shape = RoundedCornerShape(28.dp), color = palette.CardBackground) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(if (state.release?.newer == true) "发现新版本" else "检查更新",
                        modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold,
                        fontSize = 22.sp, color = palette.TextPrimary)
                    TextButton(onClick = onDismiss) { Text("关闭", color = palette.TextSecondary) }
                }
                val release = state.release
                if (state.checking) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("正在获取版本信息…", color = palette.TextSecondary)
                } else if (release != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("v${release.version}", fontWeight = FontWeight.Bold,
                            fontSize = 26.sp, color = palette.ChargeGreenDark)
                        Text(String.format(Locale.getDefault(), "%.1f MB", release.size / 1048576.0),
                            color = palette.TextSecondary, modifier = Modifier.padding(top = 9.dp))
                    }
                    Text("当前版本 v${BuildConfig.VERSION_NAME}", color = palette.TextSecondary, fontSize = 12.sp)
                    Column(Modifier.fillMaxWidth().heightIn(max = 240.dp)
                        .background(palette.TrackColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Text(release.notes.replace("**", "").ifBlank { "此版本未提供更新说明" },
                            color = palette.TextPrimary, fontSize = 13.sp, lineHeight = 21.sp)
                    }
                }
                if (state.message.isNotBlank()) Text(state.message, color = palette.TextSecondary,
                    fontSize = 12.sp, lineHeight = 18.sp)
                if (state.downloading) LinearProgressIndicator(progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth())
                if (!state.checking && state.release?.newer == true) {
                    Button(onClick = if (state.ready) onInstall else onDownload,
                        enabled = !state.downloading, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)) {
                        Text(if (state.ready) "直接安装" else if (state.downloading) "正在下载" else "立即更新")
                    }
                } else if (!state.checking && state.release == null) {
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("重新检查") }
                }
                if (state.release != null) {
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.release.pageUrl))) }
                    }, modifier = Modifier.fillMaxWidth()) { Text("查看发布页") }
                }
            }
        }
    }
}
