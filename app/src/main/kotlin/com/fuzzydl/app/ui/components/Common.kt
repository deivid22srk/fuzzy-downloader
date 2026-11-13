package com.fuzzydl.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fuzzydl.app.R
import com.fuzzydl.app.data.PartStage
import com.fuzzydl.app.ui.theme.AppShapes
import com.fuzzydl.app.ui.theme.LocalAppColors
import com.fuzzydl.app.ui.theme.LocalAppType
import com.fuzzydl.app.ui.theme.glass
import com.fuzzydl.app.ui.theme.rememberMotionEnabled
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FuzzyText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalAppType.current.body,
    color: Color = LocalAppColors.current.text,
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

/** Efeito de toque com escala curta, sem Material. */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(120),
        label = "press",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .alpha(if (enabled) 1f else 0.4f)
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = onClick,
        )
}

/**
 * Fundo "céu noturno": gradientes radiais suaves (o desfoque real é simulado
 * pelo próprio gradiente, então funciona em qualquer API) e um vinheta.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier, accent: Color? = null) {
    val colors = LocalAppColors.current
    val motion = rememberMotionEnabled()
    val transition = rememberInfiniteTransition(label = "ambient")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(26000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ambientT",
    )
    val p = if (motion) t else 0.35f

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(colors.night)

        fun blob(cx: Float, cy: Float, radius: Float, color: Color) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }

        blob(w * (0.88f - 0.10f * p), h * (0.14f + 0.05f * p), w * 0.74f, colors.violet.copy(alpha = 0.26f))
        blob(w * (0.06f + 0.10f * p), h * (0.70f - 0.04f * p), w * 0.82f, colors.azure.copy(alpha = 0.22f))
        blob(w * (0.48f + 0.08f * p), h * 0.99f, w * 0.66f, (accent ?: colors.sky).copy(alpha = 0.10f))

        drawRect(
            Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = if (colors.isDark) 0.46f else 0.05f)),
                center = Offset(w * 0.5f, h * 0.40f),
                radius = h * 0.88f,
            ),
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.card,
    strong: Boolean = false,
    tint: Color? = null,
    glow: Color? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.glass(shape, strong, tint, glow).padding(contentPadding),
        content = content,
    )
}

@Composable
fun GlassChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: AppIcon? = null,
    color: Color = LocalAppColors.current.textDim,
    tint: Color? = null,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .clip(AppShapes.chip)
            .background(tint ?: colors.glass)
            .border(1.dp, colors.glassBorderTop.copy(alpha = 0.35f), AppShapes.chip)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            AppIconView(it, color, size = 14.dp, strokeWidth = 1.6.dp)
            Spacer(Modifier.width(6.dp))
        }
        FuzzyText(text, style = LocalAppType.current.caption, color = color, maxLines = 1)
    }
}

@Composable
fun GlassIconButton(
    icon: AppIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    tint: Color = LocalAppColors.current.text,
    strong: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size)
            .glass(AppShapes.chip, strong)
            .pressable(enabled = enabled, onClickLabel = contentDescription, onClick = onClick)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        AppIconView(icon, tint, size = size * 0.44f)
    }
}

@Composable
fun PrimaryPillButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: AppIcon? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .height(56.dp)
            .shadow(
                elevation = 18.dp,
                shape = AppShapes.pill,
                ambientColor = colors.sky.copy(alpha = 0.25f),
                spotColor = colors.azure.copy(alpha = 0.35f),
            )
            .clip(AppShapes.pill)
            .background(Brush.horizontalGradient(listOf(colors.azure, colors.sky)))
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.24f), Color.Transparent),
                        endY = size.height * 0.62f,
                    ),
                )
            }
            .pressable(enabled = enabled, onClickLabel = label, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            AppIconView(it, colors.onAccent, size = 18.dp, strokeWidth = 2.dp)
            Spacer(Modifier.width(9.dp))
        }
        FuzzyText(label, style = LocalAppType.current.button, color = colors.onAccent, maxLines = 1)
    }
}

@Composable
fun GlassButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: AppIcon? = null,
    tint: Color = LocalAppColors.current.text,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(46.dp)
            .glass(AppShapes.pill)
            .pressable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            AppIconView(it, tint, size = 16.dp, strokeWidth = 1.8.dp)
            Spacer(Modifier.width(7.dp))
        }
        FuzzyText(label, style = LocalAppType.current.label, color = tint, maxLines = 1)
    }
}

/**
 * Assinatura do app: anel de pontos. O progresso é lido na fase de desenho,
 * então a atualização a ~10 Hz não recompoem a árvore.
 */
@Composable
fun DotRing(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    dots: Int = 60,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = LocalAppColors.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val p = progress().coerceIn(0f, 1f)
            val minDim = size.minDimension
            val ringRadius = minDim * 0.425f
            val dotRadius = minDim * 0.0095f
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(
                color = colors.textFaint.copy(alpha = 0.12f),
                radius = ringRadius * 1.10f,
                center = center,
                style = Stroke(width = 1f),
            )
            drawCircle(
                color = colors.textFaint.copy(alpha = 0.10f),
                radius = ringRadius * 0.90f,
                center = center,
                style = Stroke(width = 1f),
            )

            for (i in 0 until dots) {
                val frac = i.toFloat() / dots
                val angle = (-90f + frac * 360f) * (PI.toFloat() / 180f)
                val position = Offset(
                    center.x + cos(angle) * ringRadius,
                    center.y + sin(angle) * ringRadius,
                )
                val active = frac < p
                val leading = active && frac > p - 1.5f / dots
                val trailing = !active && frac < p + 2.8f / dots
                val color = when {
                    leading -> colors.sky
                    active -> colors.sky.copy(alpha = 0.82f)
                    trailing -> colors.azure.copy(alpha = 0.34f)
                    else -> colors.textFaint.copy(alpha = 0.26f)
                }
                val radius = if (leading) dotRadius * 2.1f else dotRadius
                if (leading) {
                    drawCircle(colors.sky.copy(alpha = 0.16f), radius * 3.4f, position)
                }
                drawCircle(color = color, radius = radius, center = position)
            }
        }
        content()
    }
}

@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    track: Color = LocalAppColors.current.glassStrong,
    color: Color = LocalAppColors.current.sky,
) {
    val colors = LocalAppColors.current
    val p = progress.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(AppShapes.chip)
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(p)
                .fillMaxHeight()
                .clip(AppShapes.chip)
                .background(Brush.horizontalGradient(listOf(colors.azure, color))),
        )
    }
}

@Composable
fun StatusBadge(stage: PartStage, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val label: String
    val color: Color
    when (stage) {
        PartStage.DONE -> {
            label = stringResource(R.string.stage_done)
            color = colors.mint
        }
        PartStage.FAILED -> {
            label = stringResource(R.string.stage_failed)
            color = colors.coral
        }
        PartStage.DOWNLOADING -> {
            label = stringResource(R.string.stage_downloading)
            color = colors.sky
        }
        PartStage.EXTRACTING -> {
            label = stringResource(R.string.stage_extracting)
            color = colors.violet
        }
        PartStage.RESOLVING, PartStage.VERIFYING -> {
            label = stringResource(R.string.stage_verifying)
            color = colors.amber
        }
        PartStage.PAUSED -> {
            label = stringResource(R.string.stage_paused)
            color = colors.amber
        }
        PartStage.PENDING -> {
            label = stringResource(R.string.stage_pending)
            color = colors.textFaint
        }
    }

    Row(
        modifier = modifier
            .clip(AppShapes.chip)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.30f), AppShapes.chip)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        FuzzyText(label, style = LocalAppType.current.caption, color = color, maxLines = 1)
    }
}

@Composable
fun SegmentedGlass(
    options: List<Pair<String, String>>,
    selected: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glass(AppShapes.chip)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            val cell = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(AppShapes.chip)
            Box(
                modifier = (if (active) {
                    cell.background(Brush.horizontalGradient(listOf(colors.azure, colors.sky)))
                } else {
                    cell
                }).pressable(onClickLabel = label) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                FuzzyText(
                    text = label,
                    style = LocalAppType.current.label,
                    color = if (active) colors.onAccent else colors.textDim,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun AppToggle(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    val offset by animateDpAsState(
        targetValue = if (checked) 25.dp else 3.dp,
        animationSpec = tween(160),
        label = "toggle",
    )
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 30.dp)
            .clip(AppShapes.chip)
            .background(
                if (checked) {
                    Brush.horizontalGradient(listOf(colors.azure, colors.sky))
                } else {
                    Brush.horizontalGradient(listOf(colors.glassStrong, colors.glassStrong))
                },
            )
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Box(
            Modifier
                .padding(top = 3.dp)
                .padding(start = offset)
                .size(24.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(if (checked) colors.onAccent else colors.textDim),
        )
    }
}

@Composable
fun ThreadSelector(
    selected: Int,
    modifier: Modifier = Modifier,
    range: IntRange = 1..8,
    onSelect: (Int) -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glass(AppShapes.chip)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        range.forEach { value ->
            val active = value == selected
            val cell = Modifier
                .weight(1f)
                .height(36.dp)
                .clip(AppShapes.chip)
            Box(
                modifier = (if (active) {
                    cell.background(Brush.horizontalGradient(listOf(colors.azure, colors.sky)))
                } else {
                    cell
                }).pressable(onClickLabel = value.toString()) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                FuzzyText(
                    text = value.toString(),
                    style = LocalAppType.current.numberSmall,
                    color = if (active) colors.onAccent else colors.textDim,
                )
            }
        }
    }
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = LocalAppColors.current.text,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FuzzyText(text = label, style = LocalAppType.current.bodySmall, color = colors.textDim)
        Spacer(Modifier.width(16.dp))
        FuzzyText(
            text = value,
            style = LocalAppType.current.numberSmall,
            color = valueColor,
            maxLines = 1,
        )
    }
}

@Composable
fun NoticeBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: Color = LocalAppColors.current.amber,
    icon: AppIcon = AppIcon.Info,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glass(AppShapes.cardSmall, tint = tone.copy(alpha = 0.12f), glow = tone)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconView(icon, tone, size = 18.dp, strokeWidth = 1.8.dp)
        Spacer(Modifier.width(10.dp))
        FuzzyText(
            text = text,
            modifier = Modifier.weight(1f),
            style = LocalAppType.current.bodySmall,
            color = colors.text,
        )
    }
}

@Composable
fun EmptyState(
    icon: AppIcon,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    GlassCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.glassStrong),
                contentAlignment = Alignment.Center,
            ) {
                AppIconView(icon, colors.textDim, size = 24.dp)
            }
            Spacer(Modifier.height(16.dp))
            FuzzyText(
                text = title,
                style = LocalAppType.current.headline,
                color = colors.text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            FuzzyText(
                text = body,
                style = LocalAppType.current.bodySmall,
                color = colors.textDim,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalAppColors.current.glassBorderTop.copy(alpha = 0.10f)),
    )
}

@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(38.dp)
            .height(4.dp)
            .clip(AppShapes.chip)
            .background(LocalAppColors.current.textFaint.copy(alpha = 0.45f)),
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            FuzzyText(text = title, style = LocalAppType.current.headline, color = colors.text)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                FuzzyText(text = subtitle, style = LocalAppType.current.bodySmall, color = colors.textDim)
            }
        }
        if (action != null && onAction != null) {
            Row(
                modifier = Modifier
                    .clip(AppShapes.chip)
                    .background(colors.glass)
                    .border(1.dp, colors.glassBorderTop.copy(alpha = 0.30f), AppShapes.chip)
                    .pressable(onClickLabel = action, onClick = onAction)
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FuzzyText(text = action, style = LocalAppType.current.caption, color = colors.textDim, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                AppIconView(AppIcon.ChevronRight, colors.textDim, size = 12.dp, strokeWidth = 1.7.dp)
            }
        }
    }
}

@Composable
fun GlassNavBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val items = listOf(
        Triple(AppIcon.Download, R.string.tab_download, 0),
        Triple(AppIcon.Grid, R.string.tab_parts, 1),
        Triple(AppIcon.Sliders, R.string.tab_settings, 2),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glass(AppShapes.pill, strong = true)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (icon, labelRes, index) ->
            val active = index == selected
            val label = stringResource(labelRes)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clip(AppShapes.pill)
                    .background(if (active) colors.glassStrong else Color.Transparent)
                    .pressable(onClickLabel = label) { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AppIconView(
                    icon = icon,
                    tint = if (active) colors.sky else colors.textFaint,
                    size = 21.dp,
                    strokeWidth = if (active) 2.1.dp else 1.7.dp,
                )
                Spacer(Modifier.height(4.dp))
                FuzzyText(
                    text = label,
                    style = LocalAppType.current.caption,
                    color = if (active) colors.text else colors.textFaint,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .width(14.dp)
                        .height(2.dp)
                        .clip(AppShapes.chip)
                        .background(if (active) colors.sky else Color.Transparent),
                )
            }
        }
    }
}