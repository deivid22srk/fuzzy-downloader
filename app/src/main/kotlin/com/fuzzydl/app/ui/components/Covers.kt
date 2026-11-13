package com.fuzzydl.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuzzydl.app.R
import com.fuzzydl.app.data.PartStage
import com.fuzzydl.app.ui.formatBytes
import com.fuzzydl.app.ui.theme.AppShapes
import com.fuzzydl.app.ui.theme.LocalAppColors
import com.fuzzydl.app.ui.theme.LocalAppType

/**
 * Arte procedural do pacote. Não usa capas de jogos reais nem marcas de
 * terceiros: cada parte recebe uma variação de matiz e formas geométricas.
 */
@Composable
fun HeroBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color(0xFF122A4A), Color(0xFF0A1830), Color(0xFF070B16)),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF3FA9FF).copy(alpha = 0.42f), Color.Transparent),
                center = Offset(w * 0.18f, h * 0.16f),
                radius = w * 0.85f,
            ),
            radius = w * 0.85f,
            center = Offset(w * 0.18f, h * 0.16f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF5B3DF5).copy(alpha = 0.38f), Color.Transparent),
                center = Offset(w * 0.92f, h * 0.72f),
                radius = w * 0.9f,
            ),
            radius = w * 0.9f,
            center = Offset(w * 0.92f, h * 0.72f),
        )
        repeat(4) { i ->
            drawCircle(
                color = Color.White.copy(alpha = 0.07f),
                radius = w * (0.24f + i * 0.13f),
                center = Offset(w * 0.78f, h * 0.30f),
                style = Stroke(width = 1.2f),
            )
        }
        val band = Path().apply {
            moveTo(w * 0.02f, h)
            lineTo(w * 0.60f, h * 0.20f)
            lineTo(w * 0.76f, h * 0.20f)
            lineTo(w * 0.20f, h)
            close()
        }
        drawPath(
            path = band,
            brush = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                start = Offset(0f, h),
                end = Offset(w, 0f),
            ),
        )
        for (x in 0..4) {
            for (y in 0..5) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.06f),
                    radius = 1.2.dp.toPx(),
                    center = Offset(w * (0.62f + x * 0.07f), h * (0.52f + y * 0.07f)),
                )
            }
        }
        drawRect(
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0xFF070B16).copy(alpha = 0.88f)),
                startY = h * 0.42f,
            ),
        )
    }
}

@Composable
fun PartCover(
    index: Int,
    stage: PartStage,
    progress: Float,
    sizeBytes: Long,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val hue = (208f + index * 29f) % 360f
    val base = Color.hsv(hue, 0.58f, 0.34f)
    val accent = Color.hsv((hue + 38f) % 360f, 0.66f, 0.58f)
    val type = LocalAppType.current
    val label = stringResource(R.string.label_part, index)

    Box(
        modifier = modifier
            .aspectRatio(if (compact) 0.74f else 0.72f)
            .clip(AppShapes.cover)
            .background(base)
            .pressable(onClickLabel = label, onClick = onClick),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                Brush.linearGradient(
                    colors = listOf(base, Color(0xFF070B16)),
                    start = Offset(0f, 0f),
                    end = Offset(w, h),
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.62f), Color.Transparent),
                    center = Offset(w * 0.22f, h * 0.18f),
                    radius = w * 0.95f,
                ),
                radius = w * 0.95f,
                center = Offset(w * 0.22f, h * 0.18f),
            )
            repeat(3) { i ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.09f),
                    radius = w * (0.22f + i * 0.13f),
                    center = Offset(w * 0.82f, h * 0.30f),
                    style = Stroke(width = 1.1f),
                )
            }
            val band = Path().apply {
                moveTo(w * 0.05f, h)
                lineTo(w * 0.58f, h * 0.22f)
                lineTo(w * 0.72f, h * 0.22f)
                lineTo(w * 0.20f, h)
                close()
            }
            drawPath(
                path = band,
                brush = Brush.linearGradient(
                    colors = listOf(accent.copy(alpha = 0.26f), Color.Transparent),
                    start = Offset(0f, h),
                    end = Offset(w, 0f),
                ),
            )
            for (x in 0..3) {
                for (y in 0..3) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.07f),
                        radius = 1.dp.toPx(),
                        center = Offset(w * (0.64f + x * 0.08f), h * (0.56f + y * 0.08f)),
                    )
                }
            }
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xFF070B16).copy(alpha = 0.92f)),
                    startY = h * 0.40f,
                ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                FuzzyText(
                    text = if (index < 10) "0$index" else "$index",
                    style = type.display.copy(
                        fontSize = if (compact) 34.sp else 46.sp,
                        lineHeight = if (compact) 34.sp else 46.sp,
                    ),
                    color = Color.White.copy(alpha = 0.22f),
                )
                StatusBadge(stage = stage)
            }

            Column(Modifier.fillMaxWidth()) {
                FuzzyText(
                    text = label,
                    style = if (compact) type.caption else type.label,
                    color = Color.White,
                    maxLines = 1,
                )
                if (!compact) {
                    Spacer(Modifier.height(2.dp))
                    FuzzyText(
                        text = formatBytes(sizeBytes),
                        style = type.caption,
                        color = Color.White.copy(alpha = 0.62f),
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(if (compact) 6.dp else 9.dp))
                ProgressBar(
                    progress = progress,
                    height = if (compact) 3.dp else 4.dp,
                    track = Color.White.copy(alpha = 0.16f),
                    color = accent,
                )
            }
        }
    }
}

/** Capa larga do hero, com o anel desenhado por cima. */
@Composable
fun HeroFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .shadow(
                elevation = 26.dp,
                shape = AppShapes.hero,
                ambientColor = colors.azure.copy(alpha = 0.18f),
                spotColor = colors.violet.copy(alpha = 0.22f),
            )
            .clip(AppShapes.hero)
            .background(Color(0xFF0A1830))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(colors.glassBorderTop, colors.glassBorderBottom)),
                shape = AppShapes.hero,
            ),
    ) {
        // Desfoque real no Android 12+; nas versões antigas o Compose ignora
        // e a arte permanece nítida, com o mesmo scrim escuro.
        HeroBackdrop(Modifier.matchParentSize().blur(22.dp))
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}