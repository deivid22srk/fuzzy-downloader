package com.fuzzydl.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fuzzydl.app.ui.theme.AppShapes
import com.fuzzydl.app.ui.theme.LocalAppColors
import com.fuzzydl.app.ui.theme.LocalAppType

/**
 * Imagem remota (capa/banner/screenshot) com Coil. Enquanto carrega (ou se
 * falhar), mostra um fallback discreto derivado do nome — nunca a inicial gigante.
 */
@Composable
fun AsyncCover(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallbackSeed: String = contentDescription.orEmpty(),
    fallbackIcon: AppIcon = AppIcon.Store,
    shape: androidx.compose.ui.graphics.Shape = AppShapes.cover,
) {
    Box(modifier.clip(shape).background(LocalAppColors.current.surfaceHi)) {
        CoverFallback(seed = fallbackSeed, icon = fallbackIcon, modifier = Modifier.fillMaxSize())
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Fallback elegante: gradiente determinístico + monograma pequeno central. */
@Composable
fun CoverFallback(
    seed: String,
    modifier: Modifier = Modifier,
    icon: AppIcon = AppIcon.Store,
) {
    val colors = LocalAppColors.current
    val hue = rememberHue(seed)
    val base = Color.hsv(hue, 0.45f, if (colors.isDark) 0.34f else 0.72f)
    val second = Color.hsv((hue + 34f) % 360f, 0.5f, if (colors.isDark) 0.20f else 0.58f)
    Box(
        modifier = modifier.background(Brush.linearGradient(listOf(base, second))),
        contentAlignment = Alignment.Center,
    ) {
        val initials = seed.split(' ', '-', '_')
            .filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }
        Box(
            Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.22f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            FuzzyText(
                text = initials,
                style = LocalAppType.current.numberSmall,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
    }
}

@Composable
private fun rememberHue(seed: String): Float =
    androidx.compose.runtime.remember(seed) {
        val h = seed.fold(7) { acc, c -> (acc * 31 + c.code) and 0x7fffffff }
        (h % 360).toFloat()
    }