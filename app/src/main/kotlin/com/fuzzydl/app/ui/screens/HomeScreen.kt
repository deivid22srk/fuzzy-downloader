package com.fuzzydl.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fuzzydl.app.R
import com.fuzzydl.app.data.PartStage
import com.fuzzydl.app.data.SettingsData
import com.fuzzydl.app.download.EngineNotice
import com.fuzzydl.app.download.EngineState
import com.fuzzydl.app.download.PartUiState
import com.fuzzydl.app.ui.components.DotRing
import com.fuzzydl.app.ui.components.FuzzyText
import com.fuzzydl.app.ui.components.GhostButton
import com.fuzzydl.app.ui.components.PrimaryButton
import com.fuzzydl.app.ui.components.SectionLabel
import com.fuzzydl.app.ui.formatBytes
import com.fuzzydl.app.ui.formatEta
import com.fuzzydl.app.ui.formatPercent
import com.fuzzydl.app.ui.formatSpeed
import com.fuzzydl.app.ui.theme.LocalFuzzyColors
import com.fuzzydl.app.ui.theme.LocalFuzzyType
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
) {
    val colors = LocalFuzzyColors.current
    val type = LocalFuzzyType.current

    val total = state.parts.size.coerceAtLeast(1)
    val current = state.parts.firstOrNull { it.index == state.currentIndex }
    val finishedCount = state.parts.count { it.stage == PartStage.DONE }
    val failed = state.parts.firstOrNull { it.stage == PartStage.FAILED }
    val partFraction = fractionOf(current)
    val overall = (finishedCount + if (current != null && current.stage != PartStage.DONE) partFraction else 0f) / total

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 28.dp, bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(stringResource(R.string.app_name))
            if (settings.simulation) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.accentDim)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    FuzzyText(
                        text = stringResource(R.string.simulation_badge),
                        style = type.label,
                        color = colors.accent,
                    )
                }
            }
        }
        Spacer(Modifier.height(30.dp))

        FuzzyText(
            text = stringResource(R.string.home_title),
            style = type.title,
            color = colors.text,
        )
        Spacer(Modifier.height(6.dp))
        val subtitle = when {
            state.finished -> stringResource(R.string.home_done)
            current != null -> stringResource(R.string.home_part_of, current.index, total)
            else -> stringResource(R.string.home_idle)
        }
        FuzzyText(
            text = subtitle,
            style = type.label,
            color = if (state.finished) colors.ok else colors.accent,
        )

        Spacer(Modifier.height(34.dp))

        DotRing(
            progress = { partFraction },
            modifier = Modifier.size(268.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FuzzyText(
                    text = String.format(Locale.getDefault(), "%.0f", partFraction * 100f),
                    style = type.hero,
                    color = colors.text,
                )
                FuzzyText(
                    text = "%",
                    style = type.label,
                    color = colors.textDim,
                )
                Spacer(Modifier.height(8.dp))
                FuzzyText(
                    text = stageLabel(current?.stage),
                    style = type.label,
                    color = colors.textDim,
                )
                if (current != null && (current.stage == PartStage.DOWNLOADING || current.stage == PartStage.EXTRACTING)) {
                    Spacer(Modifier.height(6.dp))
                    FuzzyText(
                        text = formatPercent(
                            if (current.stage == PartStage.EXTRACTING) current.extractDone else current.bytesDone,
                            if (current.stage == PartStage.EXTRACTING) current.extractTotal else current.bytesTotal,
                        ),
                        style = type.mono,
                        color = colors.textFaint,
                    )
                }
            }
        }

        Spacer(Modifier.height(30.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Stat(
                label = stringResource(R.string.label_speed),
                value = if (state.running) formatSpeed(current?.speedBps ?: 0) else "—",
            )
            Box(
                Modifier
                    .height(34.dp)
                    .width(1.dp)
                    .background(colors.line),
            )
            Stat(
                label = stringResource(R.string.label_eta),
                value = if (state.running) formatEta(current?.etaSecs ?: 0) else "—",
            )
            Box(
                Modifier
                    .height(34.dp)
                    .width(1.dp)
                    .background(colors.line),
            )
            Stat(
                label = stringResource(R.string.label_size),
                value = formatBytes(current?.bytesTotal ?: current?.zipBytes ?: 0),
            )
        }

        Spacer(Modifier.height(22.dp))

        val notice = noticeText(state.notice)
        if (notice != null) {
            NoticeBanner(text = notice)
            Spacer(Modifier.height(14.dp))
        }
        if (state.paused && !state.lastError.isNullOrBlank()) {
            NoticeBanner(text = state.lastError!!, danger = true)
            Spacer(Modifier.height(14.dp))
        }

        when {
            state.running -> {
                PrimaryButton(
                    label = stringResource(R.string.action_pause),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onPause,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(label = stringResource(R.string.action_details), onClick = onDetails)
                    GhostButton(label = stringResource(R.string.action_cancel), onClick = onCancel)
                }
            }
            state.paused -> {
                PrimaryButton(
                    label = stringResource(R.string.action_resume),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onStart,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.lastError != null) {
                        GhostButton(
                            label = stringResource(R.string.action_retry),
                            onClick = { onRetry(failed?.index ?: state.currentIndex ?: 1) },
                        )
                    }
                    GhostButton(label = stringResource(R.string.action_details), onClick = onDetails)
                    GhostButton(label = stringResource(R.string.action_cancel), onClick = onCancel)
                }
            }
            state.finished -> {
                PrimaryButton(
                    label = stringResource(R.string.action_details),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDetails,
                )
            }
            else -> {
                PrimaryButton(
                    label = stringResource(R.string.action_start),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onStart,
                )
                Spacer(Modifier.height(10.dp))
                FuzzyText(
                    text = stringResource(R.string.space_needed, "64,13 GiB"),
                    style = type.bodySmall,
                    color = colors.textFaint,
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // Progresso geral (9 partes)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FuzzyText(
                text = stringResource(R.string.home_total).uppercase(),
                style = type.label,
                color = colors.textFaint,
            )
            FuzzyText(
                text = "$finishedCount / $total · ${String.format(Locale.getDefault(), "%.0f", overall * 100f)}%",
                style = type.mono,
                color = colors.textDim,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(colors.line),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(overall.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(colors.accent),
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    val colors = LocalFuzzyColors.current
    val type = LocalFuzzyType.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FuzzyText(text = value, style = type.mono, color = colors.text)
        Spacer(Modifier.height(4.dp))
        FuzzyText(text = label.uppercase(), style = type.label, color = colors.textFaint)
    }
}

@Composable
private fun NoticeBanner(text: String, danger: Boolean = false) {
    val colors = LocalFuzzyColors.current
    val type = LocalFuzzyType.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (danger) colors.danger.copy(alpha = 0.10f) else colors.surfaceAlt)
            .border(
                1.dp,
                if (danger) colors.danger.copy(alpha = 0.35f) else colors.line,
                RoundedCornerShape(14.dp),
            )
            .padding(14.dp),
    ) {
        FuzzyText(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            style = type.bodySmall,
            color = if (danger) colors.danger else colors.textDim,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun stageLabel(stage: PartStage?): String = stringResource(
    when (stage) {
        PartStage.RESOLVING -> R.string.stage_resolving
        PartStage.DOWNLOADING -> R.string.stage_downloading
        PartStage.VERIFYING -> R.string.stage_verifying
        PartStage.EXTRACTING -> R.string.stage_extracting
        PartStage.PAUSED -> R.string.stage_paused
        PartStage.DONE -> R.string.stage_done
        PartStage.FAILED -> R.string.stage_failed
        else -> R.string.home_idle
    },
)

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
        else -> 0f
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
