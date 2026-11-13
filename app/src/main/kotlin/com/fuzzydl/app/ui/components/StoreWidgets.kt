package com.fuzzydl.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fuzzydl.app.download.DlItem
import com.fuzzydl.app.download.DlStatus
import com.fuzzydl.app.R
import com.fuzzydl.app.store.PortGame
import com.fuzzydl.app.ui.theme.AppShapes
import com.fuzzydl.app.ui.theme.LocalAppColors
import com.fuzzydl.app.ui.theme.LocalAppType
import com.fuzzydl.app.ui.theme.appSurface
import com.fuzzydl.app.ui.theme.glassPanel

@Composable
fun statusColor(label: String?): Color {
    val colors = LocalAppColors.current
    return when {
        label == null -> colors.textDim
        label.contains("Ativo", true) -> colors.mint
        label.contains("Experimental", true) -> colors.amber
        label.contains("Web", true) -> colors.primaryLight
        else -> colors.textDim
    }
}

/** Hero com páginas de destaque, banner real e ação. Sem rotação automática. */
@Composable
fun HeroPager(
    games: List<PortGame>,
    imageUrl: (String?) -> String?,
    actionLabel: @Composable (PortGame) -> String,
    onOpen: (PortGame) -> Unit,
    onAction: (PortGame) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (games.isEmpty()) return
    val colors = LocalAppColors.current
    val type = LocalAppType.current
    val pagerState = rememberPagerState(pageCount = { games.size })

    Column(modifier) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val game = games[page]
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(AppShapes.hero)
                    .background(colors.surface),
            ) {
                AsyncCover(
                    url = imageUrl(game.banner ?: game.cover),
                    contentDescription = game.title,
                    fallbackSeed = game.title,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xE6070B16)),
                                startY = 0f,
                            ),
                        ),
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(game.statusLabel ?: game.status ?: "", statusColor(game.statusLabel ?: game.status))
                        game.performance?.let {
                            Spacer(Modifier.width(6.dp))
                            StatusPill(it, colors.violet)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    FuzzyText(game.title, style = type.title, color = Color.White, maxLines = 2)
                    Spacer(Modifier.height(3.dp))
                    FuzzyText(
                        game.shortDescription,
                        style = type.caption,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(
                            label = actionLabel(game),
                            icon = AppIcon.Download,
                            onClick = { onAction(game) },
                        )
                        GhostButton(
                            label = stringResource(R.string.store_details),
                            icon = AppIcon.ChevronRight,
                            onClick = { onOpen(game) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(games.size) { i ->
                val active = i == pagerState.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (active) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (active) colors.primaryLight else colors.textFaint.copy(alpha = 0.4f)),
                )
            }
        }
    }
}

/** Card de prateleira horizontal (capa 3:4 + título). */
@Composable
fun ShelfCard(
    game: PortGame,
    imageUrl: (String?) -> String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalAppType.current
    val colors = LocalAppColors.current
    Column(
        modifier = modifier
            .width(132.dp)
            .pressable(onClickLabel = game.title, onClick = onClick),
    ) {
        AsyncCover(
            url = imageUrl(game.cover),
            contentDescription = game.title,
            fallbackSeed = game.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f),
        )
        Spacer(Modifier.height(7.dp))
        FuzzyText(game.title, style = type.cardTitle, color = colors.text, maxLines = 2)
        Spacer(Modifier.height(2.dp))
        FuzzyText(game.port, style = type.caption, color = colors.textDim, maxLines = 1)
    }
}

/** Linha compacta e informativa para a lista completa. */
@Composable
fun AppRow(
    game: PortGame,
    imageUrl: (String?) -> String?,
    download: DlItem?,
    installed: Boolean,
    onClick: () -> Unit,
    action: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val type = LocalAppType.current
    AppCard(modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncCover(
                url = imageUrl(game.cover),
                contentDescription = game.title,
                fallbackSeed = game.title,
                modifier = Modifier
                    .width(56.dp)
                    .aspectRatio(3f / 4f)
                    .pressable(onClickLabel = game.title, onClick = onClick),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                FuzzyText(game.title, style = type.cardTitle, color = colors.text, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                FuzzyText(game.shortDescription, style = type.caption, color = colors.textDim, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    game.apkSize?.let {
                        FuzzyText(it, style = type.caption, color = colors.textFaint, maxLines = 1)
                    }
                    game.categories.firstOrNull()?.let {
                        if (game.apkSize != null) {
                            FuzzyText(" · ", style = type.caption, color = colors.textFaint)
                        }
                        FuzzyText(it.replaceFirstChar { c -> c.uppercase() }, style = type.caption, color = colors.textFaint, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            action()
        }
    }
}

@Composable
fun ActionButton(
    label: String,
    icon: AppIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    highlight: Boolean = false,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(AppShapes.pill)
            .background(if (highlight) colors.primary.copy(alpha = 0.18f) else colors.surfaceHi)
            .pressable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (progress != null) {
            MiniRing(progress = progress, size = 16.dp, color = colors.primaryLight)
        } else {
            AppIconView(icon, if (highlight) colors.primaryLight else colors.text, size = 15.dp, strokeWidth = 1.9.dp)
        }
        Spacer(Modifier.width(6.dp))
        FuzzyText(label, style = LocalAppType.current.label, color = if (highlight) colors.primaryLight else colors.text, maxLines = 1)
    }
}

/** Pull-to-refresh simples com indicador de anel. */
@Composable
fun PullRefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    val pull = remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 76.dp.toPx() }
    val connection = remember(refreshing) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0 && !refreshing) {
                    val next = (pull.floatValue + available.y * 0.55f).coerceAtMost(threshold * 1.4f)
                    val delta = next - pull.floatValue
                    pull.floatValue = next
                    return Offset(0f, delta)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull.floatValue >= threshold && !refreshing) {
                    pull.floatValue = threshold
                    onRefresh()
                } else {
                    pull.floatValue = 0f
                }
                return Velocity.Zero
            }
        }
    }
    Box(modifier.nestedScroll(connection)) {
        content()
        if (pull.floatValue > 0f || refreshing) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MiniRing(
                    progress = { if (refreshing) 1f else (pull.floatValue / threshold).coerceIn(0f, 1f) },
                    size = 22.dp,
                    color = colors.primaryLight,
                    track = colors.surfacePressed,
                )
            }
        }
    }
}