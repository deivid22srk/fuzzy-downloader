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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fuzzydl.app.R
import com.fuzzydl.app.data.PartStage
import com.fuzzydl.app.data.SettingsData
import com.fuzzydl.app.download.EngineNotice
import com.fuzzydl.app.download.EngineState
import com.fuzzydl.app.download.PartUiState
import com.fuzzydl.app.ui.components.AppIcon
import com.fuzzydl.app.ui.components.AppIconView
import com.fuzzydl.app.ui.components.DotRing
import com.fuzzydl.app.ui.components.FuzzyText
import com.fuzzydl.app.ui.components.GlassButton
import com.fuzzydl.app.ui.components.GlassCard
import com.fuzzydl.app.ui.components.GlassChip
import com.fuzzydl.app.ui.components.GlassIconButton
import com.fuzzydl.app.ui.components.HeroFrame
import com.fuzzydl.app.ui.components.NoticeBanner
import com.fuzzydl.app.ui.components.PartCover
import com.fuzzydl.app.ui.components.PrimaryPillButton
import com.fuzzydl.app.ui.components.ProgressBar
import com.fuzzydl.app.ui.components.SectionHeader
import com.fuzzydl.app.ui.components.StatusBadge
import com.fuzzydl.app.ui.formatBytes
import com.fuzzydl.app.ui.formatEta
import com.fuzzydl.app.ui.formatPercent
import com.fuzzydl.app.ui.formatSpeed
import com.fuzzydl.app.ui.theme.LocalAppColors
import com.fuzzydl.app.ui.theme.LocalAppType
import java.util.Locale

@Composable
fun HomeScreen(
    state: EngineState,
    settings: SettingsData,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onRetry: (Int) -> Unit,
    onDetails: () -> Unit,
    onOpenPart: (Int) -> Unit,
    onOpenParts: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppType.current

    val total = state.parts.size.coerceAtLeast(1)
    val current = state.parts.firstOrNull { it.index == state.currentIndex }
    val finishedCount = state.parts.count { it.stage == PartStage.DONE }
    val failed = state.parts.firstOrNull { it.stage == PartStage.FAILED }
    val partFraction = if (state.finished) 1f else fractionOf(current)
    val overall = (finishedCount + if (current != null && current.stage != PartStage.DONE) partFraction else 0f) / total

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 132.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                FuzzyText(
                    text = stringResource(R.string.app_name),
                    style = type.title,
                    color = colors.text,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                FuzzyText(
                    text = stringResource(R.string.home_tagline),
                    style = type.bodySmall,
                    color = colors.textDim,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(12.dp))
            GlassIconButton(
                icon = AppIcon.Sliders,
                contentDescription = stringResource(R.string.tab_settings),
                onClick = onOpenSettings,
            )
        }

        Spacer(Modifier.height(18.dp))

        HeroFrame(
            modifier = Modifier
                .fillMaxWidth()
                .height(348.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    GlassChip(
                        text = stringResource(R.string.home_package),
                        icon = AppIcon.Sparkle,
                        color = Color.White.copy(alpha = 0.82f),
                        tint = Color.Black.copy(alpha = 0.22f),
                    )
                    GlassChip(
                        text = stringResource(R.string.home_parts_count, total),
                        color = Color.White.copy(alpha = 0.82f),
                        tint = Color.Black.copy(alpha = 0.22f),
                    )
                }

                DotRing(
                    progress = { partFraction },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(236.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            FuzzyText(
                                text = String.format(Locale.getDefault(), "%.0f", partFraction * 100f),
                                style = type.heroNumber,
                                color = Color.White,
                            )
                            FuzzyText(
                                text = "%",
                                modifier = Modifier.padding(bottom = 9.dp),
                                style = type.label,
                                color = Color.White.copy(alpha = 0.62f),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        StatusBadge(
                            stage = when {
                                state.finished -> PartStage.DONE
                                current != null -> current.stage
                                else -> PartStage.PENDING
                            },
                        )
                        if (current != null &&
                            (current.stage == PartStage.DOWNLOADING || current.stage == PartStage.EXTRACTING)
                        ) {
                            Spacer(Modifier.height(7.dp))
                            FuzzyText(
                                text = formatPercent(
                                    if (current.stage == PartStage.EXTRACTING) current.extractDone else current.bytesDone,
                                    if (current.stage == PartStage.EXTRACTING) current.extractTotal else current.bytesTotal,
                                ),
                                style = type.numberSmall,
                                color = Color.White.copy(alpha = 0.58f),
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FuzzyText(
                        text = when {
                            state.finished -> stringResource(R.string.home_done)
                            current != null -> stringResource(R.string.home_part_of, current.index, total)
                            else -> stringResource(R.string.home_idle)
                        },
                        style = type.bodyMedium,
                        color = Color.White.copy(alpha = 0.84f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = AppIcon.Gauge,
                label = stringResource(R.string.label_speed),
                value = if (state.running) formatSpeed(current?.speedBps ?: 0) else "—",
                tint = colors.sky,
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = AppIcon.Clock,
                label = stringResource(R.string.label_eta),
                value = if (state.running) formatEta(current?.etaSecs ?: 0) else "—",
                tint = colors.amber,
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = AppIcon.Database,
                label = stringResource(R.string.label_size),
                value = formatBytes(current?.bytesTotal ?: current?.zipBytes ?: 0),
                tint = colors.violet,
            )
        }

        Spacer(Modifier.height(16.dp))

        val notice = noticeText(state.notice)
        if (notice != null) {
            NoticeBanner(
                text = notice,
                tone = when (state.notice) {
                    EngineNotice.NO_SPACE, EngineNotice.DOWNLOAD_FAILED -> colors.coral
                    else -> colors.amber
                },
                icon = AppIcon.Alert,
            )
            Spacer(Modifier.height(12.dp))
        }
        if (state.paused && !state.lastError.isNullOrBlank()) {
            NoticeBanner(text = state.lastError!!, tone = colors.coral, icon = AppIcon.Alert)
            Spacer(Modifier.height(12.dp))
        }

        when {
            state.running -> {
                PrimaryPillButton(
                    label = stringResource(R.string.action_pause),
                    modifier = Modifier.fillMaxWidth(),
                    icon = AppIcon.Pause,
                    onClick = onPause,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GlassButton(
                        label = stringResource(R.string.action_details),
                        modifier = Modifier.weight(1f),
                        icon = AppIcon.ChevronRight,
                        onClick = onDetails,
                    )
                    GlassButton(
                        label = stringResource(R.string.action_cancel),
                        modifier = Modifier.weight(1f),
                        icon = AppIcon.Close,
                        tint = colors.coral,
                        onClick = onCancel,
                    )
                }
            }
            state.paused -> {
                PrimaryPillButton(
                    label = stringResource(R.string.action_resume),
                    modifier = Modifier.fillMaxWidth(),
                    icon = AppIcon.Play,
                    onClick = onStart,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.lastError != null) {
                        GlassButton(
                            label = stringResource(R.string.action_retry),
                            modifier = Modifier.weight(1f),
                            icon = AppIcon.Refresh,
                            onClick = { onRetry(failed?.index ?: state.currentIndex ?: 1) },
                        )
                    }
                    GlassButton(
                        label = stringResource(R.string.action_details),
                        modifier = Modifier.weight(1f),
                        icon = AppIcon.ChevronRight,
                        onClick = onDetails,
                    )
                    GlassButton(
                        label = stringResource(R.string.action_cancel),
                        modifier = Modifier.weight(1f),
                        icon = AppIcon.Close,
                        tint = colors.coral,
                        onClick = onCancel,
                    )
                }
            }
            state.finished -> {
                PrimaryPillButton(
                    label = stringResource(R.string.action_details),
                    modifier = Modifier.fillMaxWidth(),
                    icon = AppIcon.ChevronRight,
                    onClick = onDetails,
                )
            }
            else -> {
                PrimaryPillButton(
                    label = stringResource(R.string.action_start),
                    modifier = Modifier.fillMaxWidth(),
                    icon = AppIcon.Play,
                    onClick = onStart,
                )
                Spacer(Modifier.height(10.dp))
                FuzzyText(
                    text = stringResource(R.string.space_needed, "64,13 GiB"),
                    modifier = Modifier.fillMaxWidth(),
                    style = type.bodySmall,
                    color = colors.textFaint,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FuzzyText(
                        text = stringResource(R.string.home_total),
                        style = type.bodyMedium,
                        color = colors.text,
                    )
                    FuzzyText(
                        text = "$finishedCount / $total · ${
                            String.format(Locale.getDefault(), "%.0f", overall * 100f)
                        }%",
                        style = type.numberSmall,
                        color = colors.textDim,
                    )
                }
                Spacer(Modifier.height(10.dp))
                ProgressBar(progress = overall, height = 6.dp)
            }
        }

        Spacer(Modifier.height(24.dp))

        SectionHeader(
            title = stringResource(R.string.parts_title),
            subtitle = stringResource(R.string.parts_subtitle),
            action = stringResource(R.string.action_see_all),
            onAction = onOpenParts,
        )
        Spacer(Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 8.dp),
        ) {
            items(state.parts, key = { it.index }) { part ->
                PartCover(
                    index = part.index,
                    stage = part.stage,
                    progress = fractionOf(part),
                    sizeBytes = if (part.zipBytes > 0) part.zipBytes else part.bytesTotal,
                    modifier = Modifier.width(142.dp),
                    compact = true,
                    onClick = { onOpenPart(part.index) },
                )
            }
        }

        if (settings.simulation) {
            Spacer(Modifier.height(14.dp))
            GlassChip(
                text = stringResource(R.string.simulation_badge),
                icon = AppIcon.Sparkle,
                color = colors.amber,
                tint = colors.amber.copy(alpha = 0.12f),
            )
        }
    }
}

@Composable
private fun MetricCard(
    icon: AppIcon,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val type = LocalAppType.current
    GlassCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIconView(icon, tint, size = 16.dp, strokeWidth = 1.7.dp)
            Spacer(Modifier.height(7.dp))
            FuzzyText(
                text = value,
                style = type.numberSmall,
                color = colors.text,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            FuzzyText(
                text = label,
                style = type.caption,
                color = colors.textFaint,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun noticeText(notice: EngineNotice?): String? = when (notice) {
    EngineNotice.NOT_WIFI -> stringResource(R.string.warn_not_wifi)
    EngineNotice.SHIZUKU_REQUIRED -> stringResource(R.string.warn_shizuku_required)
    EngineNotice.TARGET_MISSING -> stringResource(R.string.warn_target_missing)
    EngineNotice.NO_SPACE -> stringResource(R.string.err_no_space_staging)
    EngineNotice.DOWNLOAD_FAILED -> stringResource(R.string.err_download)
    null -> null
}

private fun fractionOf(part: PartUiState?): Float {
    if (part == null) return 0f
    return when (part.stage) {
        PartStage.DONE -> 1f
        PartStage.DOWNLOADING -> if (part.bytesTotal > 0) {
            (part.bytesDone.toDouble() / part.bytesTotal).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
        PartStage.EXTRACTING -> if (part.extractTotal > 0) {
            (part.extractDone.toDouble() / part.extractTotal).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
        PartStage.VERIFYING -> 0.99f
        PartStage.PAUSED -> when {
            part.extractTotal > 0 -> (part.extractDone.toDouble() / part.extractTotal).toFloat().coerceIn(0f, 1f)
            part.bytesTotal > 0 -> (part.bytesDone.toDouble() / part.bytesTotal).toFloat().coerceIn(0f, 1f)
            else -> 0f
        }
        else -> 0f
    }
}