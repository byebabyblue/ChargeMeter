package com.local.chargemeter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
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
internal fun BuildCreditCard(onClick: () -> Unit) {
    val palette = LocalAppPalette.current
    val shape = RoundedCornerShape(28.dp)
    val green = Color(0xFF5ADEA7)
    val violet = Color(0xFFB59AF8)
    val accent = if (palette.isDark) green else Color(0xFF237B5A)
    Box(
        Modifier.fillMaxWidth().height(172.dp).clip(shape)
            .background(palette.CardBackground)
            .border(1.dp, Brush.linearGradient(listOf(
                green.copy(alpha = if (palette.isDark) 0.32f else 0.27f),
                palette.TextPrimary.copy(alpha = 0.05f),
                violet.copy(alpha = 0.30f),
            )), shape)
            .noIndicationClickable(onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(
                listOf(green.copy(alpha = if (palette.isDark) 0.12f else 0.09f), Color.Transparent),
                center = Offset(0f, size.height), radius = size.width * 0.7f,
            ))
            drawRect(Brush.radialGradient(
                listOf(violet.copy(alpha = if (palette.isDark) 0.14f else 0.09f), Color.Transparent),
                center = Offset(size.width, 0f), radius = size.width * 0.7f,
            ))
            // Quiet linework adds depth without competing with the attribution.
            for (index in 0..3) {
                val x = size.width * 0.73f + index * 18.dp.toPx()
                drawLine(palette.TextPrimary.copy(alpha = 0.035f),
                    Offset(x, 0f), Offset(x - size.height * 0.45f, size.height), 1.dp.toPx())
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("AI ASSISTED", color = accent, fontSize = 9.sp,
                    letterSpacing = 2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("v${BuildConfig.VERSION_NAME}", color = palette.TextSecondary.copy(alpha = 0.75f), fontSize = 10.sp)
                Icon(Icons.Rounded.ChevronRight, "查看构建贡献", Modifier.padding(start = 4.dp).size(14.dp), tint = accent)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(22.dp))
                        .background(palette.CardBackground.copy(alpha = 0.6f))
                        .border(1.dp, palette.TextPrimary.copy(alpha = 0.07f), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_openai_blossom), "GPT 图标",
                        Modifier.fillMaxSize(), tint = if (palette.isDark) Color.White else Color.Black)
                }
                Spacer(Modifier.size(16.dp))
                Column {
                    Text("本版本由", color = palette.TextSecondary, fontSize = 12.sp)
                    Text("GPT-6.1 Sol", color = palette.TextPrimary, fontSize = 25.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                    Text("参与构建", color = palette.TextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}
