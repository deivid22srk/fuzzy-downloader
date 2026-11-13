package com.fuzzydl.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fuzzydl.app.R
import com.fuzzydl.app.store.DownloadState
import com.fuzzydl.app.store.DownloadStatus
import com.fuzzydl.app.store.StoreGame
import com.fuzzydl.app.ui.components.AppIcon
import com.fuzzydl.app.ui.components.FuzzyText
import com.fuzzydl.app.ui.components.GameCover
import com.fuzzydl.app.ui.components.GlassButton
import com.fuzzydl.app.ui.components.GlassCard
import com.fuzzydl.app.ui.components.GlassChip
import com.fuzzydl.app.ui.components.GlassIconButton
import com.fuzzydl.app.ui.components.KeyValueRow
import com.fuzzydl.app.ui.components.NoticeBanner
import com.fuzzydl.app.ui.components.PrimaryPillButton
import com.fuzzydl.app.ui.components.ProgressBar
import com.fuzzydl.app.ui.formatBytes
import com.fuzzydl.app.ui.formatPercent
import com.fuzzydl.app.ui.theme.LocalAppColors
import com.fuzzydl.app.ui.theme.LocalAppType

@Composable
fun GameDetailScreen(
    game: StoreGame,
    download: DownloadState?,
    installed: Boolean,
    canInstall: Boolean,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onOpenApp: () -> Unit,
    onRequestUnknownSources: () -> Unit,
    onOpenInternal: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppType.current
    val status = download?.status ?: DownloadStatus.NONE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 40.dp),
    ) {
        GlassIconButton(
            icon = AppIcon.ChevronLeft,
            contentDescription = stringResource(R.string.action_back),
            onClick = onBack,
        )

        Spacer(Modifier.height(14.dp))

        GameCover(
            game = game,
            modifier = Modifier.fillMaxWidth(),
            wide = true,
            onClick = { },
        )

        Spacer(Modifier.height(18.dp))

        FuzzyText(text = game.name, style = type.title, color = colors.text)
        if (game.developer.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            FuzzyText(
                text = stringResource(R.string.store_developer, game.developer),
                style = type.bodySmall,
                color = colors.textDim,
            )
        }

        if (game.tags.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                game.tags.forEach { tag -> GlassChip(text = tag, color = colors.textDim) }
            }
        }

        Spacer(Modifier.height(16.dp))
        FuzzyText(text = game.description, style = type.body, color = colors.text)

        if (!game.requirements.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            NoticeBanner(
                text = game.requirements,
                tone = colors.amber,
                icon = AppIcon.Alert,
            )
        }

        Spacer(Modifier.height(18.dp))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                game.version?.let {
                    KeyValueRow(label = stringResource(R.string.store_version), value = it)
                }
                if (game.sizeBytes > 0) {
                    KeyValueRow(
                        label = stringResource(R.string.store_size),
                        value = formatBytes(game.sizeBytes),
                    )
                }
                if (game.license.isNotBlank()) {
                    KeyValueRow(label = stringResource(R.string.store_license), value = game.license)
                }
                game.minAndroid?.let {
                    KeyValueRow(
                        label = stringResource(R.string.store_version),
                        value = stringResource(R.string.store_android_min, it),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (game.sourceUrl.isNotBlank()) {
                GlassButton(
                    label = stringResource(R.string.store_source),
                    icon = AppIcon.ExternalLink,
                    onClick = { onOpenUrl(game.sourceUrl) },
                )
            }
            game.homepageUrl?.let { home ->
                if (home.isNotBlank()) {
                    GlassButton(
                        label = stringResource(R.string.store_homepage),
                        icon = AppIcon.ExternalLink,
                        onClick = { onOpenUrl(home) },
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        when {
            game.isInternal -> {
                PrimaryPillButton(
                    label = stringResource(R.string.store_internal_open),
                    modifier = Modifier.fillMaxWidth(),
                    icon = AppIcon.Play,
                    onClick = onOpenInternal,
                )
            }
            installed -> {
                PrimaryPillButton(
                    label = stringResource(R.string.store_open),
                    modifier = Modifier.fillMaxWidth(),
                    icon = AppIcon.Play,
                    onClick = onOpenApp,
                )
            }
            status == DownloadStatus.DOWNLOADING || status == DownloadStatus.VERIFYING -> {
                val total = download?.total ?: 0
                val done = download?.done ?: 0
                val fraction = if (total > 0) (done.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f
                ProgressBar(progress = fraction, height = 7.dp)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FuzzyText(
                        text = "${formatBytes(done)} / ${formatBytes(total)}",
                        style = type.bodySmall,
                        color = colors.textDim,
                    )
                    FuzzyText(
                        text = formatPercent(done, total),
                        style = type.numberSmall,
                        color = colors.sky,
                    )
                }
                Spacer(Modifier.height(14.dp))
                GlassButton(
                    label = stringResource(R.string.store_cancel),
                    modifier = Modifier.fillMaxWidth(),
                    icon = AppIcon.Close,
                    tint = colors.coral,
                    onClick = onCancel,
                )
            }
            status == DownloadStatus.READY -> {
                if (canInstall) {
                    PrimaryPillButton(
                        label = stringResource(R.string.store_install),
                        modifier = Modifier.fillMaxWidth(),
                        icon = AppIcon.Download,
                        onClick = onInstall,
                    )
                } else {
                    PrimaryPillButton(
                        label = stringResource(R.string.store_enable_unknown_sources),
                        modifier = Modifier.fillMaxWidth(),
                        icon = AppIcon.Download,
                        onClick = onRequestUnknownSources,
                    )
                }
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    label = stringResource(R.string.store_delete),
                    modifier = Modifier.fillMaxWidth(),
                    icon = AppIcon.Close,
                    tint = colors.coral,
                    onClick = onDelete,
                )
            }
            status == DownloadStatus.FAILED -> {
                if (!download?.error.isNullOrBlank()) {
                    NoticeBanner(text = download!!.error!!, tone = colors.coral, icon = AppIcon.Alert)
                    Spacer(Modifier.height(14.dp))
                }
                PrimaryPillButton(
                    label = stringResource(R.string.store_retry),
                    modifier = Modifier.fillMaxWidth(),
                    icon = AppIcon.Refresh,
                    onClick = onDownload,
                )
            }
            else -> {
                PrimaryPillButton(
                    label = stringResource(R.string.store_download),
                    modifier = Modifier.fillMaxWidth(),
                    icon = AppIcon.Download,
                    onClick = onDownload,
                )
            }
        }
    }
}