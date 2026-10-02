package com.local.chargemeter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.chargemeter.BuildConfig
import com.local.chargemeter.R

@Composable
internal fun BuildCreditsScreen(onBack: () -> Unit) {
    val palette = LocalAppPalette.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PageHeader("构建贡献", "记录每个版本背后的协作", onBack) }
        item { CurrentBuildHero() }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("历史构建", color = palette.TextPrimary, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${releasedBuilds.size} 个版本", color = palette.TextSecondary, fontSize = 12.sp)
            }
        }
        itemsIndexed(releasedBuilds, key = { _, build -> build.version }) { index, build ->
            ContributionTimelineItem(build, index == releasedBuilds.lastIndex)
        }
    }
}

@Composable
private fun CurrentBuildHero() {
    val palette = LocalAppPalette.current
    val shape = RoundedCornerShape(28.dp)
    val green = Color(0xFF5ADEA7)
    val violet = Color(0xFFB59AF8)
    Box(Modifier.fillMaxWidth().clip(shape).background(palette.CardBackground)
        .border(1.dp, Brush.linearGradient(listOf(green.copy(alpha = 0.35f), violet.copy(alpha = 0.28f))), shape)) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.radialGradient(
                listOf(green.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(0f, size.height), radius = size.width * 0.85f,
            ))
            drawRect(Brush.radialGradient(
                listOf(violet.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width, 0f), radius = size.width * 0.7f,
            ))
        }
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("CURRENT BUILD", fontSize = 9.sp, letterSpacing = 2.sp,
                    color = palette.ChargeGreenDark, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(color = palette.TrackColor.copy(alpha = 0.6f), shape = RoundedCornerShape(50)) {
                    Text(currentBuildDate, Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = palette.TextSecondary, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(68.dp).clip(RoundedCornerShape(21.dp))
                    .background(palette.CardBackground.copy(alpha = 0.7f))
                    .border(1.dp, palette.TextPrimary.copy(alpha = 0.07f), RoundedCornerShape(21.dp))) {
                    Icon(painterResource(R.drawable.ic_openai_blossom), null,
                        Modifier.fillMaxSize(), tint = if (palette.isDark) Color.White else Color.Black)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("v${BuildConfig.VERSION_NAME}", color = palette.TextSecondary, fontSize = 14.sp)
                    currentBuildModels.forEachIndexed { index, model ->
                        Text(model, color = buildModelColor(model, palette.isDark),
                            fontSize = if (index == 0) 26.sp else 17.sp,
                            fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium,
                            letterSpacing = (-0.5).sp)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.TextPrimary.copy(alpha = 0.07f)))
            Spacer(Modifier.height(14.dp))
            Text("本版本由以上模型参与构建", color = palette.TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ContributionTimelineItem(build: BuildContribution, last: Boolean) {
    val palette = LocalAppPalette.current
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Canvas(Modifier.width(24.dp).fillMaxHeight()) {
            val dot = Offset(6.dp.toPx(), 27.dp.toPx())
            if (!last) drawLine(palette.TrackColor, dot,
                Offset(dot.x, size.height + 16.dp.toPx()), 1.5.dp.toPx())
            drawCircle(palette.TextSecondary.copy(alpha = 0.12f), 9.dp.toPx(), dot)
            drawCircle(palette.TextSecondary, 3.dp.toPx(), dot)
        }
        Surface(Modifier.weight(1f), color = palette.CardBackground, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("v${build.version}", color = palette.TextPrimary, fontSize = 22.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(build.publishedAt, color = palette.TextSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    build.models.forEach { model ->
                        val accent = buildModelColor(model, palette.isDark)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(3.dp).height(18.dp)
                                .background(accent, RoundedCornerShape(50)))
                            Text(model, Modifier.weight(1f).padding(start = 10.dp),
                                color = accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("参与构建", color = palette.TextSecondary.copy(alpha = 0.65f), fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
