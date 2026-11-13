package com.fuzzydl.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuzzydl.app.store.StoreGame
import com.fuzzydl.app.ui.theme.AppShapes
import com.fuzzydl.app.ui.theme.LocalAppType

/**
 * Capa procedural do jogo: nada de arte protegida. Usa a cor de destaque do
 * catálogo, formas geométricas e as iniciais do nome.
 */
@Composable
fun GameCover(
    game: StoreGame,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    wide: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = parseAccent(game.accent)
    val dark = Color(0xFF070B16)
    val type = LocalAppType.current
    val initials = rememberInitials(game.name)

    Box(
        modifier = modifier
            .aspectRatio(if (wide) 1.6f else 0.72f)
            .clip(if (wide) AppShapes.hero else AppShapes.cover)
            .background(dark)
            .pressable(onClickLabel = game.name, onClick = onClick),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                Brush.linearGradient(
                    colors = listOf(accent.copy(alpha = 0.85f), dark),
                    start = Offset(0f, 0f),
                    end = Offset(w, h),
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(w * 0.24f, h * 0.20f),
                    radius = w * 0.9f,
                ),
                radius = w * 0.9f,
                center = Offset(w * 0.24f, h * 0.20f),
            )
            repeat(3) { i ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.10f),
                    radius = w * (0.20f + i * 0.13f),
                    center = Offset(w * 0.82f, h * 0.28f),
                    style = Stroke(width = 1.2f),
                )
            }
            val band = Path().apply {
                moveTo(w * 0.04f, h)
                lineTo(w * 0.56f, h * 0.20f)
                lineTo(w * 0.70f, h * 0.20f)
                lineTo(w * 0.18f, h)
                close()
            }
            drawPath(
                path = band,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                    start = Offset(0f, h),
                    end = Offset(w, 0f),
                ),
            )
            for (x in 0..3) {
                for (y in 0..3) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.07f),
                        radius = 1.dp.toPx(),
                        center = Offset(w * (0.64f + x * 0.08f), h * (0.54f + y * 0.08f)),
                    )
                }
            }
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, dark.copy(alpha = 0.94f)),
                    startY = h * 0.36f,
                ),
            )
        }

        FuzzyText(
            text = initials,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(if (compact) 12.dp else 16.dp),
            style = type.display.copy(
                fontSize = if (wide) 54.sp else if (compact) 40.sp else 48.sp,
                lineHeight = if (wide) 54.sp else if (compact) 40.sp else 48.sp,
            ),
            color = Color.White.copy(alpha = 0.22f),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxSize()
                .padding(if (compact) 11.dp else 15.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            FuzzyText(
                text = game.name,
                style = if (wide) type.headline else if (compact) type.label else type.bodyMedium,
                color = Color.White,
                maxLines = if (wide) 1 else 2,
            )
            Spacer(Modifier.height(2.dp))
            FuzzyText(
                text = game.tagline,
                style = type.caption,
                color = Color.White.copy(alpha = 0.68f),
                maxLines = if (wide) 1 else 2,
            )
        }
    }
}

@Composable
fun GameCoverBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color,
) {
    Row(modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
        GlassChip(text = text, color = color, tint = Color.Black.copy(alpha = 0.34f))
    }
}

@Composable
private fun rememberInitials(name: String): String {
    val words = name.split(' ', '-', '_').filter { it.isNotBlank() }
    val initials = words.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
    return initials.ifEmpty { name.take(2).uppercase() }
}

private fun parseAccent(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF3FA9FF))