package com.fuzzydl.app.ui.theme

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuzzydl.app.R

/**
 * Tokens centrais do redesign "vitrine de vidro sobre um céu noturno".
 *
 * Nada de Material3: cores, tipografia, raios, vidro e movimento vivem aqui e
 * são consumidos pelos componentes e pelas três telas.
 */
@Immutable
data class AppColors(
    val night: Color,
    val deep: Color,
    val panel: Color,
    val panelHi: Color,
    val glass: Color,
    val glassStrong: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val azure: Color,
    val sky: Color,
    val violet: Color,
    val mint: Color,
    val amber: Color,
    val coral: Color,
    val onAccent: Color,
    val isDark: Boolean,
)

private val DarkColors = AppColors(
    night = Color(0xFF070B16),
    deep = Color(0xFF0A1120),
    panel = Color(0xFF0C1628),
    panelHi = Color(0xFF111D33),
    glass = Color(0x14FFFFFF),
    glassStrong = Color(0x24FFFFFF),
    glassBorderTop = Color(0x47FFFFFF),
    glassBorderBottom = Color(0x0AFFFFFF),
    text = Color(0xFFF4F7FF),
    textDim = Color(0xFFA7B1C6),
    textFaint = Color(0xFF6C7891),
    azure = Color(0xFF0070D1),
    sky = Color(0xFF3FA9FF),
    violet = Color(0xFF5B3DF5),
    mint = Color(0xFF2FD28A),
    amber = Color(0xFFFFB020),
    coral = Color(0xFFFF4D5E),
    onAccent = Color(0xFFFFFFFF),
    isDark = true,
)

private val LightColors = AppColors(
    night = Color(0xFFEAF0FA),
    deep = Color(0xFFDDE7F6),
    panel = Color(0xFFF7FAFF),
    panelHi = Color(0xFFFFFFFF),
    glass = Color(0x8CFFFFFF),
    glassStrong = Color(0xC7FFFFFF),
    glassBorderTop = Color(0xF2FFFFFF),
    glassBorderBottom = Color(0x59FFFFFF),
    text = Color(0xFF0B1526),
    textDim = Color(0xFF46566F),
    textFaint = Color(0xFF7A8AA3),
    azure = Color(0xFF0063B8),
    sky = Color(0xFF2E93E6),
    violet = Color(0xFF6448E8),
    mint = Color(0xFF0E9A63),
    amber = Color(0xFFB87400),
    coral = Color(0xFFD62F44),
    onAccent = Color(0xFFFFFFFF),
    isDark = false,
)

@Immutable
data class AppType(
    val display: TextStyle,
    val heroNumber: TextStyle,
    val title: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val number: TextStyle,
    val numberSmall: TextStyle,
    val button: TextStyle,
)

private const val TABULAR = "tnum"

private fun manrope(weight: FontWeight): Font = Font(
    R.font.manrope,
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(weight, FontStyle.Normal),
)

val Manrope = FontFamily(
    manrope(FontWeight.Normal),
    manrope(FontWeight.Medium),
    manrope(FontWeight.Bold),
)

private val Type = AppType(
    display = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 58.sp,
        lineHeight = 58.sp,
        letterSpacing = (-1.8).sp,
        fontFeatureSettings = TABULAR,
    ),
    heroNumber = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 64.sp,
        lineHeight = 64.sp,
        letterSpacing = (-2).sp,
        fontFeatureSettings = TABULAR,
    ),
    title = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.7).sp,
    ),
    headline = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.2).sp,
    ),
    body = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    label = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    caption = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
    ),
    number = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.3).sp,
        fontFeatureSettings = TABULAR,
    ),
    numberSmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = TABULAR,
    ),
    button = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ),
)

object AppShapes {
    val hero = RoundedCornerShape(30.dp)
    val card = RoundedCornerShape(22.dp)
    val cardSmall = RoundedCornerShape(18.dp)
    val cover = RoundedCornerShape(18.dp)
    val chip = RoundedCornerShape(percent = 50)
    val pill = RoundedCornerShape(percent = 50)
}

object AppSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 22.dp
    val xl = 30.dp
    val xxl = 40.dp
}

val LocalAppColors = staticCompositionLocalOf { DarkColors }
val LocalAppType = staticCompositionLocalOf { Type }

@Composable
fun FuzzyTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppColors provides if (darkTheme) DarkColors else LightColors,
        LocalAppType provides Type,
        content = content,
    )
}

/**
 * Receita do vidro: sombra suave, preenchimento translúcido, brilho interno no
 * topo e borda em gradiente. O desfoque real fica no fundo ambiente (gradientes
 * radiais), que funciona em qualquer API; nos cards usamos transparência, que é
 * o fallback previsto no briefing para Android < 12.
 */
@Composable
fun Modifier.glass(
    shape: Shape = AppShapes.card,
    strong: Boolean = false,
    tint: Color? = null,
    glow: Color? = null,
): Modifier {
    val colors = LocalAppColors.current
    val fill = tint ?: if (strong) colors.glassStrong else colors.glass
    return this
        .shadow(
            elevation = 22.dp,
            shape = shape,
            ambientColor = (glow ?: Color.Black).copy(alpha = 0.22f),
            spotColor = (glow ?: Color.Black).copy(alpha = 0.32f),
        )
        .clip(shape)
        .background(fill)
        .drawBehind {
            drawRect(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (colors.isDark) 0.08f else 0.55f),
                        Color.Transparent,
                    ),
                    endY = size.height * 0.42f,
                ),
            )
        }
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(colors.glassBorderTop, colors.glassBorderBottom),
            ),
            shape = shape,
        )
}

/** Movimento ambiente desligado quando o sistema pede "reduzir animações". */
@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f) > 0f
    }
}