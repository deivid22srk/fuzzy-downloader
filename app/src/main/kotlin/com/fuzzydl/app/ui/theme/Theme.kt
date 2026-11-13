package com.fuzzydl.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fuzzydl.app.R

@Immutable
data class FuzzyColors(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val line: Color,
    val lineStrong: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val accent: Color,
    val accentDim: Color,
    val onAccent: Color,
    val danger: Color,
    val ok: Color,
)

private val DarkColors = FuzzyColors(
    bg = Color(0xFF000000),
    surface = Color(0xFF0A0A0A),
    surfaceAlt = Color(0xFF141414),
    line = Color(0xFF242424),
    lineStrong = Color(0xFF3A3A3A),
    text = Color(0xFFF5F5F5),
    textDim = Color(0xFF9A9A9A),
    textFaint = Color(0xFF5C5C5C),
    accent = Color(0xFFD71921),
    accentDim = Color(0xFF5E0A0E),
    onAccent = Color(0xFFFFFFFF),
    danger = Color(0xFFFF5A5F),
    ok = Color(0xFF63E6A4),
)

private val LightColors = FuzzyColors(
    bg = Color(0xFFF6F6F4),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEDEDEA),
    line = Color(0xFFDCDCD7),
    lineStrong = Color(0xFFB9B9B2),
    text = Color(0xFF0B0B0B),
    textDim = Color(0xFF5E5E5A),
    textFaint = Color(0xFF9A9A94),
    accent = Color(0xFFC4121A),
    accentDim = Color(0xFFF0C9CB),
    onAccent = Color(0xFFFFFFFF),
    danger = Color(0xFFC3262C),
    ok = Color(0xFF117A4C),
)

val LocalFuzzyColors = staticCompositionLocalOf { DarkColors }

val Doto = FontFamily(Font(R.font.doto, FontWeight.Medium))

@Immutable
data class FuzzyType(
    val hero: TextStyle,
    val title: TextStyle,
    val section: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val mono: TextStyle,
    val label: TextStyle,
    val number: TextStyle,
)

private val Type = FuzzyType(
    hero = TextStyle(
        fontFamily = Doto,
        fontWeight = FontWeight.Medium,
        fontSize = 56.sp,
        letterSpacing = (-1).sp,
    ),
    title = TextStyle(
        fontFamily = Doto,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        letterSpacing = 0.5.sp,
    ),
    section = TextStyle(
        fontFamily = Doto,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 1.5.sp,
    ),
    body = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 0.4.sp),
    label = TextStyle(
        fontFamily = Doto,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
    ),
    number = TextStyle(
        fontFamily = Doto,
        fontWeight = FontWeight.Medium,
        fontSize = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
)

val LocalFuzzyType = staticCompositionLocalOf { Type }

@Composable
fun FuzzyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalFuzzyColors provides if (darkTheme) DarkColors else LightColors,
        LocalFuzzyType provides Type,
        content = content,
    )
}
