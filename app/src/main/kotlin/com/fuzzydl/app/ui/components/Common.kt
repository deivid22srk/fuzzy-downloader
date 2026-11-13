package com.fuzzydl.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fuzzydl.app.ui.theme.LocalFuzzyColors
import com.fuzzydl.app.ui.theme.LocalFuzzyType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FuzzyText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalFuzzyType.current.body,
    color: Color = LocalFuzzyColors.current.text,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = textAlign ?: TextAlign.Unspecified),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    FuzzyText(
        text = text.uppercase(),
        modifier = modifier,
        style = LocalFuzzyType.current.label,
        color = LocalFuzzyColors.current.textFaint,
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = LocalFuzzyColors.current.line) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}

/** Efeito de toque sem depender de Material. */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return this
        .alpha(if (!enabled) 0.35f else if (pressed) 0.7f else 1f)
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = Role.Button,
            onClick = onClick,
        )
}

@Composable
fun PrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalFuzzyColors.current
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(colors.accent)
            .pressable(enabled = enabled, onClick = onClick)
            .semantics { role = Role.Button; contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        FuzzyText(
            text = label,
            style = LocalFuzzyType.current.section,
            color = colors.onAccent,
        )
    }
}

@Composable
fun GhostButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalFuzzyColors.current
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, colors.lineStrong, RoundedCornerShape(22.dp))
            .pressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        FuzzyText(
            text = label,
            style = LocalFuzzyType.current.bodySmall,
            color = colors.text,
        )
    }
}

/** Anel de 60 pontos. O progresso é lido na fase de desenho (sem recompor a tela). */
@Composable
fun DotRing(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    dots: Int = 60,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = LocalFuzzyColors.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val p = progress().coerceIn(0f, 1f)
            val minDim = size.minDimension
            val ringRadius = (minDim / 2f) * 0.86f
            val dotRadius = (minDim / 2f) * 0.017f
            val center = Offset(size.width / 2f, size.height / 2f)
            for (i in 0 until dots) {
                val frac = i.toFloat() / dots
                val angle = (-90f + frac * 360f) * (PI.toFloat() / 180f)
                val position = Offset(
                    center.x + cos(angle) * ringRadius,
                    center.y + sin(angle) * ringRadius,
                )
                val active = frac < p
                val leading = active && frac > p - 1.5f / dots
                val trailing = !active && frac < p + 3f / dots
                val color = when {
                    leading -> colors.accent
                    active -> colors.accent.copy(alpha = 0.88f)
                    trailing -> colors.accent.copy(alpha = 0.30f)
                    else -> colors.lineStrong.copy(alpha = 0.6f)
                }
                val radius = if (leading) dotRadius * 1.75f else dotRadius
                drawCircle(color = color, radius = radius, center = position)
            }
        }
        content()
    }
}

@Composable
fun ProgressLine(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 2.dp,
    color: Color = LocalFuzzyColors.current.accent,
) {
    val track = LocalFuzzyColors.current.line
    Canvas(modifier.fillMaxWidth().height(height)) {
        val y = size.height / 2f
        drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = size.height, cap = StrokeCap.Round)
        val p = progress.coerceIn(0f, 1f)
        if (p > 0f) {
            drawLine(color, Offset(0f, y), Offset(size.width * p, y), strokeWidth = size.height, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun FuzzyToggle(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit,
) {
    val colors = LocalFuzzyColors.current
    val offset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = tween(140),
        label = "toggle",
    )
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (checked) colors.accent else colors.lineStrong)
            .pressable(onClick = { onChange(!checked) })
            .semantics { role = Role.Switch },
    ) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .padding(start = offset)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) colors.onAccent else colors.text),
        )
    }
}

@Composable
fun Segmented(
    options: List<Pair<String, String>>,
    selected: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    val colors = LocalFuzzyColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) colors.accent else Color.Transparent)
                    .pressable(onClick = { onSelect(value) }),
                contentAlignment = Alignment.Center,
            ) {
                FuzzyText(
                    text = label,
                    style = LocalFuzzyType.current.bodySmall,
                    color = if (active) colors.onAccent else colors.textDim,
                )
            }
        }
    }
}

/** Seletor 1..8 desenhado como pontos (evita Slider do Material). */
@Composable
fun DotSelector(
    selected: Int,
    modifier: Modifier = Modifier,
    range: IntRange = 1..8,
    onSelect: (Int) -> Unit,
) {
    val colors = LocalFuzzyColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        range.forEach { value ->
            val active = value <= selected
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .pressable(onClickLabel = value.toString()) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                if (value == selected) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .border(1.dp, colors.accent, CircleShape),
                    )
                }
                Box(
                    Modifier
                        .size(if (active) 14.dp else 10.dp)
                        .clip(CircleShape)
                        .background(if (active) colors.accent else colors.lineStrong),
                )
            }
        }
    }
}

@Composable
fun KeyValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = LocalFuzzyColors.current.text,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FuzzyText(text = label, style = LocalFuzzyType.current.bodySmall, color = LocalFuzzyColors.current.textDim)
        Spacer(Modifier.width(16.dp))
        FuzzyText(
            text = value,
            style = LocalFuzzyType.current.mono,
            color = valueColor,
            maxLines = 1,
        )
    }
}

@Composable
fun StageChip(stage: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = color, size = 6.dp)
        Spacer(Modifier.width(6.dp))
        FuzzyText(
            text = stage.uppercase(),
            style = LocalFuzzyType.current.label,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(38.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(LocalFuzzyColors.current.lineStrong),
    )
}

@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    FuzzyText(
        text = text,
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
        style = LocalFuzzyType.current.bodySmall,
        color = LocalFuzzyColors.current.textFaint,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun RoundedCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalFuzzyColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(20.dp)),
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

/** Barra inferior flutuante, 3 abas. */
@Composable
fun FloatingTabBar(
    selected: Int,
    labels: List<String>,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val colors = LocalFuzzyColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(18.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(28.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (active) colors.surfaceAlt else Color.Transparent)
                    .pressable(onClickLabel = label) { onSelect(index) }
                    .semantics { role = Role.Tab },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                StatusDot(
                    color = if (active) colors.accent else colors.textFaint.copy(alpha = 0.45f),
                    size = if (active) 5.dp else 4.dp,
                )
                Spacer(Modifier.height(5.dp))
                FuzzyText(
                    text = label,
                    style = LocalFuzzyType.current.label,
                    color = if (active) colors.text else colors.textDim,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Contorno fino (usado no anel e no scrim de foco). */
@Composable
fun RingOutline(modifier: Modifier = Modifier, color: Color = LocalFuzzyColors.current.line) {
    Canvas(modifier) {
        drawCircle(
            color = color,
            radius = size.minDimension / 2f - 1f,
            style = Stroke(width = 1f),
        )
    }
}
