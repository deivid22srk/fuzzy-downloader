package com.fuzzydl.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fuzzydl.app.ui.theme.AppShapes
import com.fuzzydl.app.ui.theme.LocalAppColors
import com.fuzzydl.app.ui.theme.LocalAppType
import com.fuzzydl.app.ui.theme.appSurface
import com.fuzzydl.app.ui.theme.glassPanel
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
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = textAlign ?: TextAlign.Unspecified),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

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
        targetValue = if (pressed && enabled) 0.975f else 1f,
        animationSpec = tween(110),
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

/** Fundo ambiente: gradientes radiais suaves (funciona em qualquer API). */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
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
                brush = Brush.radialGradient(listOf(color, Color.Transparent), Offset(cx, cy), radius),
                radius = radius,
                center = Offset(cx, cy),
            )
        }
        blob(w * (0.88f - 0.10f * p), h * (0.12f + 0.05f * p), w * 0.72f, colors.violet.copy(alpha = 0.20f))
        blob(w * (0.06f + 0.10f * p), h * (0.70f - 0.05f * p), w * 0.80f, colors.primary.copy(alpha = 0.18f))
    }
}

/** Card de conteúdo (superfície sólida). */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.card,
    elevated: Boolean = true,
    fill: Color? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.appSurface(shape, elevated, fill).padding(contentPadding),
        content = content,
    )
}

@Composable
fun IconButton(
    icon: AppIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = LocalAppColors.current.text,
    fill: Color? = null,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size)
            .appSurface(CircleShape, elevated = false, fill = fill ?: LocalAppColors.current.surfaceHi)
            .pressable(enabled = enabled, onClickLabel = contentDescription, onClick = onClick)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        AppIconView(icon, tint, size = size * 0.46f)
    }
}

@Composable
fun PrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: AppIcon? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .height(54.dp)
            .shadow(
                elevation = 14.dp,
                shape = AppShapes.pill,
                ambientColor = colors.primaryLight.copy(alpha = 0.20f),
                spotColor = colors.primary.copy(alpha = 0.30f),
            )
            .clip(AppShapes.pill)
            .background(Brush.horizontalGradient(listOf(colors.primary, colors.primaryLight)))
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                        endY = size.height * 0.62f,
                    ),
                )
            }
            .pressable(enabled = enabled, onClickLabel = label, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            AppIconView(it, colors.onPrimary, size = 18.dp, strokeWidth = 2.dp)
            Spacer(Modifier.width(9.dp))
        }
        FuzzyText(label, style = LocalAppType.current.button, color = colors.onPrimary, maxLines = 1)
    }
}

@Composable
fun GhostButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: AppIcon? = null,
    tint: Color = LocalAppColors.current.text,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .appSurface(AppShapes.pill, elevated = false, fill = LocalAppColors.current.surfaceHi)
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            FuzzyText(text = title, style = LocalAppType.current.section, color = colors.text)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                FuzzyText(text = subtitle, style = LocalAppType.current.caption, color = colors.textDim)
            }
        }
        if (action != null && onAction != null) {
            Row(
                modifier = Modifier
                    .clip(AppShapes.chip)
                    .pressable(onClickLabel = action, onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FuzzyText(text = action, style = LocalAppType.current.label, color = colors.primaryLight)
                Spacer(Modifier.width(3.dp))
                AppIconView(AppIcon.ChevronRight, colors.primaryLight, size = 12.dp, strokeWidth = 1.8.dp)
            }
        }
    }
}

@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    track: Color = LocalAppColors.current.surfacePressed,
    color: Color = LocalAppColors.current.primaryLight,
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
                .background(Brush.horizontalGradient(listOf(colors.primary, color))),
        )
    }
}

/** Anel pontilhado — assinatura do app. Lido na fase de desenho. */
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
            val ringRadius = minDim * 0.43f
            val dotRadius = minDim * 0.0095f
            val center = Offset(size.width / 2f, size.height / 2f)
            for (i in 0 until dots) {
                val frac = i.toFloat() / dots
                val angle = (-90f + frac * 360f) * (PI.toFloat() / 180f)
                val position = Offset(center.x + cos(angle) * ringRadius, center.y + sin(angle) * ringRadius)
                val active = frac < p
                val leading = active && frac > p - 1.5f / dots
                val trailing = !active && frac < p + 2.6f / dots
                val color = when {
                    leading -> colors.primaryLight
                    active -> colors.primaryLight.copy(alpha = 0.82f)
                    trailing -> colors.primary.copy(alpha = 0.34f)
                    else -> colors.textFaint.copy(alpha = 0.24f)
                }
                val radius = if (leading) dotRadius * 2.1f else dotRadius
                if (leading) drawCircle(colors.primaryLight.copy(alpha = 0.16f), radius * 3.4f, position)
                drawCircle(color = color, radius = radius, center = position)
            }
        }
        content()
    }
}

/** Mini anel de progresso para botões de ação. */
@Composable
fun MiniRing(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    color: Color = LocalAppColors.current.onPrimary,
    track: Color = LocalAppColors.current.onPrimary.copy(alpha = 0.30f),
) {
    Canvas(modifier.size(size)) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2f
        drawArc(
            color = track,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(this.size.width - stroke, this.size.height - stroke),
            style = Stroke(width = stroke),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * progress().coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(this.size.width - stroke, this.size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(AppShapes.chip)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.32f), AppShapes.chip)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        FuzzyText(text, style = LocalAppType.current.label, color = color, maxLines = 1)
    }
}

@Composable
fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .clip(AppShapes.chip)
            .background(if (selected) colors.primaryLight.copy(alpha = 0.18f) else colors.surfaceHi)
            .border(
                width = 1.dp,
                color = if (selected) colors.primaryLight.copy(alpha = 0.55f) else colors.line,
                shape = AppShapes.chip,
            )
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        FuzzyText(
            text = label,
            style = LocalAppType.current.label,
            color = if (selected) colors.primaryLight else colors.textDim,
            maxLines = 1,
        )
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .appSurface(AppShapes.pill, elevated = false, fill = colors.surfaceHi)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconView(AppIcon.Search, colors.textDim, size = 18.dp, strokeWidth = 1.9.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                FuzzyText(placeholder, style = LocalAppType.current.body, color = colors.textFaint, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalAppType.current.body.copy(color = colors.text),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primaryLight),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .pressable(onClickLabel = "limpar") { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                AppIconView(AppIcon.Close, colors.textDim, size = 15.dp, strokeWidth = 1.9.dp)
            }
        }
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
            .clip(AppShapes.cardSmall)
            .background(tone.copy(alpha = 0.12f))
            .border(1.dp, tone.copy(alpha = 0.28f), AppShapes.cardSmall)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconView(icon, tone, size = 17.dp, strokeWidth = 1.8.dp)
        Spacer(Modifier.width(9.dp))
        FuzzyText(text, modifier = Modifier.weight(1f), style = LocalAppType.current.caption, color = colors.text)
    }
}

@Composable
fun EmptyState(
    icon: AppIcon,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(colors.surfaceHi),
            contentAlignment = Alignment.Center,
        ) {
            AppIconView(icon, colors.textDim, size = 28.dp)
        }
        Spacer(Modifier.height(16.dp))
        FuzzyText(title, style = LocalAppType.current.section, color = colors.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        FuzzyText(body, style = LocalAppType.current.body, color = colors.textDim, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            PrimaryButton(label = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LocalAppColors.current.line))
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FuzzyText(label, style = LocalAppType.current.caption, color = colors.textDim)
        Spacer(Modifier.width(12.dp))
        FuzzyText(value, style = LocalAppType.current.label, color = colors.text, maxLines = 2, textAlign = TextAlign.End)
    }
}

@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(38.dp)
            .height(4.dp)
            .clip(AppShapes.chip)
            .background(LocalAppColors.current.textFaint.copy(alpha = 0.5f)),
    )
}

@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = AppShapes.cardSmall) {
    val colors = LocalAppColors.current
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(modifier.clip(shape).background(colors.surfaceHi.copy(alpha = alpha)))
}

@Composable
fun GlassNavBar(
    items: List<Pair<AppIcon, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(AppShapes.pill, opaque = true)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, (icon, label) ->
            val active = index == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(AppShapes.pill)
                    .background(if (active) colors.surfacePressed else Color.Transparent)
                    .pressable(onClickLabel = label) { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AppIconView(
                    icon = icon,
                    tint = if (active) colors.primaryLight else colors.textDim,
                    size = 21.dp,
                    strokeWidth = if (active) 2.1.dp else 1.8.dp,
                )
                Spacer(Modifier.height(3.dp))
                FuzzyText(
                    text = label,
                    style = LocalAppType.current.caption,
                    color = if (active) colors.text else colors.textDim,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun AppSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    val offset by animateDpAsState(
        targetValue = if (checked) 23.dp else 3.dp,
        animationSpec = tween(150),
        label = "switch",
    )
    Box(
        modifier = modifier
            .size(width = 50.dp, height = 30.dp)
            .clip(AppShapes.chip)
            .background(if (checked) colors.primary else colors.surfacePressed)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .padding(start = offset)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) colors.onPrimary else colors.textDim),
        )
    }
}
