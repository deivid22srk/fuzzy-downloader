package com.fuzzydl.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuzzydl.app.R
import com.fuzzydl.app.download.DlItem
import com.fuzzydl.app.download.DlStatus
import com.fuzzydl.app.ui.DashboardViewModel
import com.fuzzydl.app.ui.components.AppCard
import com.fuzzydl.app.ui.components.AppIcon
import com.fuzzydl.app.ui.components.DotRing
import com.fuzzydl.app.ui.components.EmptyState
import com.fuzzydl.app.ui.components.FuzzyText
import com.fuzzydl.app.ui.components.GhostButton
import com.fuzzydl.app.ui.components.ProgressBar
import com.fuzzydl.app.ui.formatBytes
import com.fuzzydl.app.ui.formatEta
import com.fuzzydl.app.ui.formatSpeed
import com.fuzzydl.app.ui.theme.LocalAppColors
import com.fuzzydl.app.ui.theme.LocalAppType
import java.util.Locale

@Composable
fun DownloadsScreen(vm: DashboardViewModel) {
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current

    val items = downloads.values.sortedByDescending { it.addedAt }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp),
    ) {
        FuzzyText(stringResource(R.string.downloads_title), style = LocalAppType.current.title, color = colors.text)
        Spacer(Modifier.height(12.dp))

        if (items.isEmpty()) {
            EmptyState(
                icon = AppIcon.Download,
                title = stringResource(R.string.downloads_empty_title),
                body = stringResource(R.string.downloads_empty_body),
            )
            return
        }

        val totalSize = items.sumOf { if (it.total > 0) it.total else it.done }
        val totalDone = items.sumOf { it.done }
        val overall = if (totalSize > 0) (totalDone.toDouble() / totalSize).toFloat().coerceIn(0f, 1f) else 0f
        val current = items.firstOrNull { it.status == DlStatus.DOWNLOADING }

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DotRing(
                    progress = { overall },
                    modifier = Modifier.size(132.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FuzzyText(
                            text = String.format(Locale.getDefault(), "%.0f", overall * 100f),
                            style = LocalAppType.current.hero,
                            color = colors.text,
                        )
                        FuzzyText(
                            text = "%",
                            style = LocalAppType.current.label,
                            color = colors.textDim,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Metric(stringResource(R.string.label_speed), if (current != null) formatSpeed(current.speedBps) else "—")
                    Metric(stringResource(R.string.label_eta), if (current != null) formatEta(current.etaSecs) else "—")
                    Metric(stringResource(R.string.label_size), formatBytes(totalSize))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        FuzzyText(
            text = stringResource(R.string.downloads_count, items.size),
            style = LocalAppType.current.caption,
            color = colors.textDim,
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { item ->
                DownloadRow(item = item, vm = vm)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FuzzyText(label, style = LocalAppType.current.caption, color = colors.textDim)
        FuzzyText(value, style = LocalAppType.current.numberSmall, color = colors.text)
    }
}

@Composable
private fun DownloadRow(item: DlItem, vm: DashboardViewModel) {
    val colors = LocalAppColors.current
    val type = LocalAppType.current
    AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    FuzzyText(item.title, style = type.cardTitle, color = colors.text, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    FuzzyText(
                        text = item.fileName.ifBlank { item.url.substringAfterLast('/') },
                        style = type.caption,
                        color = colors.textDim,
                        maxLines = 1,
                    )
                }
                FuzzyText(
                    text = when (item.status) {
                        DlStatus.PAUSED -> stringResource(R.string.store_paused)
                        DlStatus.READY -> stringResource(R.string.store_ready)
                        DlStatus.FAILED -> stringResource(R.string.store_failed)
                        DlStatus.VERIFYING -> stringResource(R.string.store_verifying)
                        else -> "${(item.progress * 100).toInt()}%"
                    },
                    style = type.numberSmall,
                    color = when (item.status) {
                        DlStatus.READY -> colors.mint
                        DlStatus.FAILED -> colors.coral
                        DlStatus.PAUSED -> colors.amber
                        else -> colors.primaryLight
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar(progress = item.progress, height = 5.dp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FuzzyText(
                    text = buildString {
                        append(formatBytes(item.done))
                        if (item.total > 0) append(" / ").append(formatBytes(item.total))
                        if (item.status == DlStatus.DOWNLOADING) {
                            append(" · ").append(formatSpeed(item.speedBps))
                            append(" · ").append(formatEta(item.etaSecs))
                        }
                    },
                    style = type.caption,
                    color = colors.textDim,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
                when (item.status) {
                    DlStatus.DOWNLOADING, DlStatus.PROBING, DlStatus.QUEUED -> {
                        GhostButton(
                            label = stringResource(R.string.store_pause),
                            icon = AppIcon.Pause,
                            onClick = { vm.pause(item.id) },
                        )
                        Spacer(Modifier.width(6.dp))
                        GhostButton(
                            label = stringResource(R.string.store_cancel),
                            icon = AppIcon.Close,
                            tint = colors.coral,
                            onClick = { vm.cancel(item.id) },
                        )
                    }
                    DlStatus.PAUSED -> {
                        GhostButton(
                            label = stringResource(R.string.store_resume),
                            icon = AppIcon.Play,
                            onClick = { vm.resume(item.id) },
                        )
                        Spacer(Modifier.width(6.dp))
                        GhostButton(
                            label = stringResource(R.string.store_cancel),
                            icon = AppIcon.Close,
                            tint = colors.coral,
                            onClick = { vm.cancel(item.id) },
                        )
                    }
                    DlStatus.READY -> {
                        GhostButton(
                            label = stringResource(R.string.store_install),
                            icon = AppIcon.Download,
                            onClick = { vm.installGame(item.gameId) },
                        )
                        Spacer(Modifier.width(6.dp))
                        GhostButton(
                            label = stringResource(R.string.store_delete),
                            icon = AppIcon.Trash,
                            tint = colors.coral,
                            onClick = { vm.deleteDownload(item.gameId) },
                        )
                    }
                    DlStatus.FAILED -> {
                        GhostButton(
                            label = stringResource(R.string.store_retry),
                            icon = AppIcon.Refresh,
                            onClick = { vm.retry(item.gameId) },
                        )
                        Spacer(Modifier.width(6.dp))
                        GhostButton(
                            label = stringResource(R.string.store_delete),
                            icon = AppIcon.Trash,
                            tint = colors.coral,
                            onClick = { vm.deleteDownload(item.gameId) },
                        )
                    }
                    DlStatus.VERIFYING -> {
                        FuzzyText(stringResource(R.string.store_verifying), style = type.caption, color = colors.textDim)
                    }
                }
            }
        }
    }
}