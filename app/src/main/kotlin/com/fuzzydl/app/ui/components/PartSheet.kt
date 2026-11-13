package com.fuzzydl.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fuzzydl.app.R
import com.fuzzydl.app.data.PartStage
import com.fuzzydl.app.download.PartUiState
import com.fuzzydl.app.ui.formatBytes
import com.fuzzydl.app.ui.formatPercent
import com.fuzzydl.app.ui.theme.LocalFuzzyColors
import com.fuzzydl.app.ui.theme.LocalFuzzyType

/** Bottom sheet próprio (sem Material3): scrim + painel deslizante. */
@Composable
fun BoxScope.PartDetailSheet(
    part: PartUiState?,
    running: Boolean,
    onDismiss: () -> Unit,
    onRestart: (Int) -> Unit,
) {
    var last by remember { mutableStateOf<PartUiState?>(null) }
    LaunchedEffect(part) {
        if (part != null) last = part
    }
    val shown = part ?: last
    val visible = part != null

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.matchParentSize(),
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180)),
    ) {
        val interaction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(interactionSource = interaction, indication = null, onClick = onDismiss),
        )
    }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(240)) { it },
        exit = slideOutVertically(tween(200)) { it },
    ) {
        if (shown != null) {
            PartPanel(
                part = shown,
                running = running,
                onDismiss = onDismiss,
                onRestart = onRestart,
            )
        }
    }
}

@Composable
private fun PartPanel(
    part: PartUiState,
    running: Boolean,
    onDismiss: () -> Unit,
    onRestart: (Int) -> Unit,
) {
    val colors = LocalFuzzyColors.current
    val type = LocalFuzzyType.current
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .navigationBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(top = 12.dp, bottom = 22.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SheetHandle()
        }
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                FuzzyText(
                    text = stringResource(R.string.label_part, part.index),
                    style = type.title,
                    color = colors.text,
                )
                Spacer(Modifier.height(6.dp))
                StageChip(
                    stage = stageLabel(part.stage),
                    color = stageColor(part.stage),
                )
            }
            GhostButton(label = stringResource(R.string.part_close), onClick = onDismiss)
        }

        Spacer(Modifier.height(20.dp))

        val done = when (part.stage) {
            PartStage.EXTRACTING -> part.extractDone
            else -> part.bytesDone
        }
        val total = when (part.stage) {
            PartStage.EXTRACTING -> part.extractTotal
            else -> part.bytesTotal
        }
        if (total > 0) {
            ProgressLine(progress = (done.toDouble() / total).toFloat().coerceIn(0f, 1f))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FuzzyText(
                    text = "${formatBytes(done)} / ${formatBytes(total)}",
                    style = type.mono,
                    color = colors.textDim,
                )
                FuzzyText(text = formatPercent(done, total), style = type.mono, color = colors.accent)
            }
            Spacer(Modifier.height(18.dp))
        }

        part.fileName?.let { name ->
            KeyValue(label = stringResource(R.string.part_zip), value = name)
        }
        KeyValue(
            label = stringResource(R.string.label_size),
            value = formatBytes(if (part.zipBytes > 0) part.zipBytes else part.bytesTotal),
        )
        KeyValue(
            label = stringResource(R.string.label_files),
            value = part.filesExtracted.toString(),
        )
        part.mirror?.let { mirror ->
            KeyValue(label = stringResource(R.string.label_mirror), value = mirror)
        }

        if (!part.error.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.danger.copy(alpha = 0.10f))
                    .padding(12.dp),
            ) {
                FuzzyText(
                    text = part.error,
                    style = type.bodySmall,
                    color = colors.danger,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            label = stringResource(R.string.part_restart),
            modifier = Modifier.fillMaxWidth(),
            enabled = !running,
            onClick = {
                onRestart(part.index)
                onDismiss()
            },
        )
    }
}

@Composable
private fun stageLabel(stage: PartStage): String = stringResource(
    when (stage) {
        PartStage.PENDING -> R.string.stage_pending
        PartStage.RESOLVING -> R.string.stage_resolving
        PartStage.DOWNLOADING -> R.string.stage_downloading
        PartStage.VERIFYING -> R.string.stage_verifying
        PartStage.EXTRACTING -> R.string.stage_extracting
        PartStage.PAUSED -> R.string.stage_paused
        PartStage.DONE -> R.string.stage_done
        PartStage.FAILED -> R.string.stage_failed
    },
)

@Composable
private fun stageColor(stage: PartStage): Color {
    val colors = LocalFuzzyColors.current
    return when (stage) {
        PartStage.DONE -> colors.ok
        PartStage.FAILED -> colors.danger
        PartStage.PENDING, PartStage.PAUSED -> colors.textFaint
        else -> colors.accent
    }
}
