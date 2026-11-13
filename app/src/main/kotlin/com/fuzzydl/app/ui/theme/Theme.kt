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
 * Tokens centrais do app. Regra do vidro (v2): vidro só em barra inferior,
 * topo ao rolar, bottom sheets, chips e painel de download. Cards de conteúdo
 * usam superfície sólida com borda sutil.
 */
@Immutable
data class AppColors(
    val night: Color,
    val deep: Color,
    val surface: Color,
    val surfaceHi: Color,
    val surfacePressed: Color,
    val line: Color,
    val lineStrong: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val primary: Color,
    val primaryLight: Color,
    val violet: Color,
    val mint: Color,
    val amber: Color,
    val coral: Color,
    val onPrimary: Color,
    val glass: Color,
    val glassStrong: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    val navSurface: Color,
    val isDark: Boolean,
)

private val DarkColors = AppColors(
    night = Color(0xFF070B16),
    deep = Color(0xFF0A1120),
    surface = Color(0xFF101827),
    surfaceHi = Color(0xFF162034),
    surfacePressed = Color(0xFF1C2840),
    line = Color(0x1FFFFFFF),
    lineStrong = Color(0x38FFFFFF),
    text = Color(0xFFF4F7FF),
    textDim = Color(0xFFA9B4C9),
    textFaint = Color(0xFF6E7B94),
    primary = Color(0xFF0070D1),
    primaryLight = Color(0xFF3FA9FF),
    violet = Color(0xFF5B3DF5),
    mint = Color(0xFF2FD28A),
    amber = Color(0xFFFFB020),
    coral = Color(0xFFFF4D5E),
    onPrimary = Color(0xFFFFFFFF),
    glass = Color(0x14FFFFFF),
    glassStrong = Color(0x26FFFFFF),
    glassBorderTop = Color(0x42FFFFFF),
    glassBorderBottom = Color(0x0AFFFFFF),
    navSurface = Color(0xE80C1424),
    isDark = true,
)

private val LightColors = AppColors(
    night = Color(0xFFEDF1F8),
    deep = Color(0xFFE2E8F3),
    surface = Color(0xFFFFFFFF),
    surfaceHi = Color(0xFFF3F6FC),
    surfacePressed = Color(0xFFE7EDF7),
    line = Color(0x14000B1F),
    lineStrong = Color(0x33000B1F),
    text = Color(0xFF0B1526),
    textDim = Color(0xFF46566F),
    textFaint = Color(0xFF7A8AA3),
    primary = Color(0xFF0063B8),
    primaryLight = Color(0xFF2E93E6),
    violet = Color(0xFF6448E8),
    mint = Color(0xFF0E9A63),
    amber = Color(0xFFB87400),
    coral = Color(0xFFD62F44),
    onPrimary = Color(0xFFFFFFFF),
    glass = Color(0x99FFFFFF),
    glassStrong = Color(0xCCFFFFFF),
    glassBorderTop = Color(0xF2FFFFFF),
    glassBorderBottom = Color(0x33000000),
    navSurface = Color(0xF2FFFFFF),
    isDark = false,
)

@Immutable
data class AppType(
    val hero: TextStyle,
    val title: TextStyle,
    val section: TextStyle,
    val cardTitle: TextStyle,
    val body: TextStyle,
    val bodyMedium: TextStyle,
    val caption: TextStyle,
    val label: TextStyle,
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
    hero = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 42.sp,
        letterSpacing = (-1.2).sp,
        fontFeatureSettings = TABULAR,
    ),
    title = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.6).sp,
    ),
    section = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    cardTitle = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    body = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    caption = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    label = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
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
    val hero = RoundedCornerShape(24.dp)
    val card = RoundedCornerShape(16.dp)
    val cardSmall = RoundedCornerShape(13.dp)
    val cover = RoundedCornerShape(13.dp)
    val chip = RoundedCornerShape(percent = 50)
    val pill = RoundedCornerShape(percent = 50)
}

object AppSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 28.dp
    val xxl = 36.dp
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

/** Card de conteúdo: superfície sólida, borda sutil e sombra leve. Sem vidro. */
@Composable
fun Modifier.appSurface(
    shape: Shape = AppShapes.card,
    elevated: Boolean = true,
    fill: Color? = null,
): Modifier {
    val colors = LocalAppColors.current
    val base = this
        .clip(shape)
        .background(fill ?: colors.surface)
        .border(1.dp, colors.line, shape)
    return if (elevated) {
        base.shadow(
            elevation = 10.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.20f),
            spotColor = Color.Black.copy(alpha = 0.26f),
        )
    } else {
        base
    }
}

/** Vidro para barra inferior, sheets, topo e chips. Fica opaco o suficiente para ler. */
@Composable
fun Modifier.glassPanel(
    shape: Shape = AppShapes.card,
    opaque: Boolean = false,
): Modifier {
    val colors = LocalAppColors.current
    return this
        .shadow(
            elevation = 18.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.30f),
            spotColor = Color.Black.copy(alpha = 0.36f),
        )
        .clip(shape)
        .background(if (opaque) colors.navSurface else colors.glassStrong)
        .drawBehind {
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = if (colors.isDark) 0.07f else 0.4f), Color.Transparent),
                    endY = size.height * 0.5f,
                ),
            )
        }
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(listOf(colors.glassBorderTop, colors.glassBorderBottom)),
            shape = shape,
        )
}

/** Movimento desligado quando o sistema pede "reduzir animações". */
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