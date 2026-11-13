package com.fuzzydl.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuzzydl.app.R
import com.fuzzydl.app.download.DlStatus
import com.fuzzydl.app.store.PortGame
import com.fuzzydl.app.ui.DashboardViewModel
import com.fuzzydl.app.ui.components.AppCard
import com.fuzzydl.app.ui.components.AppDivider
import com.fuzzydl.app.ui.components.AppIcon
import com.fuzzydl.app.ui.components.AppIconView
import com.fuzzydl.app.ui.components.AsyncCover
import com.fuzzydl.app.ui.components.FuzzyText
import com.fuzzydl.app.ui.components.IconButton
import com.fuzzydl.app.ui.components.KeyValueRow
import com.fuzzydl.app.ui.components.NoticeBanner
import com.fuzzydl.app.ui.components.pressable
import com.fuzzydl.app.ui.components.ProgressBar
import com.fuzzydl.app.ui.components.SectionHeader
import com.fuzzydl.app.ui.components.StatusPill
import com.fuzzydl.app.ui.components.statusColor
import com.fuzzydl.app.ui.formatBytes
import com.fuzzydl.app.ui.theme.AppShapes
import com.fuzzydl.app.ui.theme.LocalAppColors
import com.fuzzydl.app.ui.theme.LocalAppType
import com.fuzzydl.app.ui.theme.glassPanel

@Composable
fun DetailScreen(
    vm: DashboardViewModel,
    gameId: String,
    refreshKey: Int,
    onBack: () -> Unit,
) {
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val game = catalog?.find(gameId) ?: return
    val download = downloads[gameId]
    val colors = LocalAppColors.current
    val type = LocalAppType.current
    var expanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 104.dp),
        ) {
            // ---------------- hero ----------------
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(260.dp),
            ) {
                AsyncCover(
                    url = vm.imageUrl(game.banner ?: game.cover),
                    contentDescription = game.title,
                    fallbackSeed = game.title,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(0.dp),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x66070B16), Color(0x22070B16), Color(0xF2070B16)),
                                startY = 0f,
                            ),
                        ),
                )
                IconButton(
                    icon = AppIcon.ChevronLeft,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = onBack,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(12.dp),
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
                        text = "${game.port} · ${game.original}",
                        style = type.caption,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 2,
                    )
                }
            }

            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(14.dp))

                // ---------------- descrição ----------------
                val fullText = game.description.joinToString("\n\n").ifBlank { game.shortDescription }
                FuzzyText(
                    text = fullText,
                    style = type.body,
                    color = colors.text,
                    maxLines = if (expanded) Int.MAX_VALUE else 5,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .clip(AppShapes.chip)
                        .pressableText { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FuzzyText(
                        text = stringResource(if (expanded) R.string.store_see_less else R.string.store_see_more),
                        style = type.label,
                        color = colors.primaryLight,
                    )
                    Spacer(Modifier.width(3.dp))
                    AppIconViewChevron(expanded, colors.primaryLight)
                }

                // ---------------- screenshots ----------------
                if (game.screenshots.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    SectionHeader(title = stringResource(R.string.store_screenshots))
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(game.screenshots) { shot ->
                            AsyncCover(
                                url = vm.imageUrl(shot),
                                contentDescription = null,
                                fallbackSeed = game.title,
                                modifier = Modifier
                                    .width(240.dp)
                                    .height(135.dp),
                                shape = AppShapes.card,
                            )
                        }
                    }
                }

                // ---------------- requisitos ----------------
                if (game.requirements.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    SectionHeader(title = stringResource(R.string.store_requirements))
                    Spacer(Modifier.height(10.dp))
                    game.requirements.forEach { section ->
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            FuzzyText(section.title, style = type.cardTitle, color = colors.primaryLight)
                            Spacer(Modifier.height(6.dp))
                            section.items.forEach { (k, v) -> KeyValueRow(label = k, value = v) }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }

                // ---------------- arquivos (download) ----------------
                Spacer(Modifier.height(10.dp))
                SectionHeader(
                    title = stringResource(R.string.store_files),
                    subtitle = game.apkSize?.let { stringResource(R.string.store_files_size, it) },
                )
                Spacer(Modifier.height(10.dp))
                if (download == null) {
                    FuzzyText(
                        text = stringResource(R.string.store_files_empty),
                        style = type.caption,
                        color = colors.textDim,
                    )
                } else {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                FuzzyText(
                                    text = download.fileName.ifBlank { game.port },
                                    style = type.cardTitle,
                                    color = colors.text,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.height(3.dp))
                                FuzzyText(
                                    text = downloadLabel(download.status),
                                    style = type.caption,
                                    color = downloadColor(download.status),
                                )
                            }
                            if (download.status == DlStatus.DOWNLOADING) {
                                FuzzyText(
                                    text = "${(download.progress * 100).toInt()}%",
                                    style = type.numberSmall,
                                    color = colors.primaryLight,
                                )
                            }
                        }
                        if (download.status == DlStatus.DOWNLOADING || download.status == DlStatus.READY) {
                            Spacer(Modifier.height(9.dp))
                            ProgressBar(progress = download.progress, height = 5.dp)
                        }
                        if (download.status == DlStatus.DOWNLOADING) {
                            Spacer(Modifier.height(4.dp))
                            FuzzyText(
                                text = "${formatBytes(download.done)} / ${formatBytes(download.total)}",
                                style = type.caption,
                                color = colors.textDim,
                            )
                        }
                        if (download.status == DlStatus.FAILED) {
                            Spacer(Modifier.height(8.dp))
                            NoticeBanner(
                                text = download.error ?: stringResource(R.string.store_failed),
                                tone = colors.coral,
                                icon = AppIcon.Alert,
                            )
                        }
                    }
                }

                // ---------------- controles ----------------
                if (game.controls.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    SectionHeader(title = stringResource(R.string.store_controls))
                    Spacer(Modifier.height(10.dp))
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        game.controls.forEach { line ->
                            BulletLine(line, colors.textDim)
                        }
                    }
                }

                // ---------------- como instalar ----------------
                if (game.install.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    SectionHeader(title = stringResource(R.string.store_how_to_install))
                    Spacer(Modifier.height(10.dp))
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        game.install.forEachIndexed { index, line ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                FuzzyText(
                                    text = "${index + 1}.",
                                    style = type.numberSmall,
                                    color = colors.primaryLight,
                                    modifier = Modifier.width(24.dp),
                                )
                                FuzzyText(line, style = type.body, color = colors.text)
                            }
                        }
                    }
                }

                // ---------------- links / licença / créditos ----------------
                Spacer(Modifier.height(20.dp))
                SectionHeader(title = stringResource(R.string.store_about))
                Spacer(Modifier.height(10.dp))
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    game.license?.let { KeyValueRow(stringResource(R.string.store_license), it) }
                    game.chipsetRecommended?.let { KeyValueRow(stringResource(R.string.store_chipsets), it) }
                    game.downloads.takeIf { it > 0 }?.let {
                        KeyValueRow(stringResource(R.string.store_popularity), "$it")
                    }
                    game.updated?.let { KeyValueRow(stringResource(R.string.store_updated), it) }
                    AppDivider(Modifier.padding(vertical = 8.dp))
                    LinkRow(stringResource(R.string.store_source), game.links["github"], vm)
                    LinkRow(stringResource(R.string.store_releases), game.links["releases"], vm)
                    LinkRow(stringResource(R.string.store_project), game.links["project"], vm)
                    LinkRow(stringResource(R.string.store_tutorial), game.links["tutorial"], vm)
                    val gameCredit = game.credit["game"]
                    if (!gameCredit.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        FuzzyText(gameCredit, style = type.caption, color = colors.textFaint)
                    }
                    val portCredit = game.credit["port"]
                    if (!portCredit.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        FuzzyText(
                            text = stringResource(R.string.store_port_credit, portCredit),
                            style = type.caption,
                            color = colors.textFaint,
                        )
                    }
                }

                if (!game.notes.isNullOrBlank()) {
                    Spacer(Modifier.height(16.dp))
                    NoticeBanner(text = game.notes ?: "", tone = colors.primaryLight, icon = AppIcon.Info)
                }

                Spacer(Modifier.height(24.dp))
            }
        }

        // ---------------- ação fixa ----------------
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .glassPanel(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), opaque = true)
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            GameActionButtonLarge(
                game = game,
                download = download,
                installed = remember(game.id, download?.status, refreshKey) { vm.isInstalled(game.packageName) },
                vm = vm,
            )
        }
    }
}

@Composable
private fun BulletLine(text: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .width(5.dp)
                .height(5.dp)
                .clip(AppShapes.chip)
                .background(LocalAppColors.current.primaryLight),
        )
        Spacer(Modifier.width(9.dp))
        FuzzyText(text, style = LocalAppType.current.body, color = color)
    }
}

@Composable
private fun LinkRow(label: String, url: String?, vm: DashboardViewModel) {
    if (url.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
            .pressableText { vm.openUrl(url) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FuzzyText(label, style = LocalAppType.current.label, color = LocalAppColors.current.primaryLight)
        Spacer(Modifier.weight(1f))
        AppIconView(AppIcon.ExternalLink, LocalAppColors.current.textDim, size = 15.dp)
    }
}

@Composable
private fun downloadLabel(status: DlStatus): String = when (status) {
    DlStatus.QUEUED, DlStatus.PROBING -> stringResource(R.string.store_queued)
    DlStatus.DOWNLOADING -> stringResource(R.string.store_downloading)
    DlStatus.PAUSED -> stringResource(R.string.store_paused)
    DlStatus.VERIFYING -> stringResource(R.string.store_verifying)
    DlStatus.READY -> stringResource(R.string.store_ready)
    DlStatus.FAILED -> stringResource(R.string.store_failed)
}

@Composable
private fun downloadColor(status: DlStatus): Color {
    val colors = LocalAppColors.current
    return when (status) {
        DlStatus.READY -> colors.mint
        DlStatus.FAILED -> colors.coral
        DlStatus.DOWNLOADING, DlStatus.PROBING -> colors.primaryLight
        DlStatus.PAUSED -> colors.amber
        else -> colors.textDim
    }
}

@Composable
private fun AppIconViewChevron(expanded: Boolean, tint: Color) {
    AppIconView(
        icon = if (expanded) AppIcon.ChevronLeft else AppIcon.ChevronRight,
        tint = tint,
        size = 13.dp,
        strokeWidth = 1.8.dp,
    )
}

@Composable
private fun Modifier.pressableText(onClick: () -> Unit): Modifier =
    this.pressable(onClick = onClick)