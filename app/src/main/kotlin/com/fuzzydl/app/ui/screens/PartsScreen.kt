package com.fuzzydl.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fuzzydl.app.R
import com.fuzzydl.app.data.PartStage
import com.fuzzydl.app.download.EngineState
import com.fuzzydl.app.download.PartUiState
import com.fuzzydl.app.ui.components.FuzzyText
import com.fuzzydl.app.ui.components.Hairline
import com.fuzzydl.app.ui.components.ProgressLine
import com.fuzzydl.app.ui.components.SectionLabel
import com.fuzzydl.app.ui.components.StageChip
import com.fuzzydl.app.ui.components.pressable
import com.fuzzydl.app.ui.formatBytes
import com.fuzzydl.app.ui.theme.LocalFuzzyColors
import com.fuzzydl.app.ui.theme.LocalFuzzyType
import java.util.Locale

@Composable
fun PartsScreen(
    state: EngineState,
    onOpenPart: (Int) -> Unit,
) {
    val colors = LocalFuzzyColors.current
    val type = LocalFuzzyType.current
    val done = state.parts.count { it.stage == PartStage.DONE }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 24.dp).padding(top = 28.dp)) {
            SectionLabel(stringResource(R.string.app_name))
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                FuzzyText(
                    text = stringResource(R.string.parts_title),
                    style = type.title,
                    color = colors.text,
                )
                FuzzyText(
                    text = "$done / ${state.parts.size}",
                    style = type.mono,
                    color = colors.textDim,
                )
            }
            Spacer(Modifier.height(4.dp))
            FuzzyText(
                text = stringResource(R.string.parts_subtitle),
                style = type.bodySmall,
                color = colors.textDim,
            )
            Spacer(Modifier.height(14.dp))
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(state.parts, key = { it.index }) { part ->
                PartRow(
                    part = part,
                    onClick = { onOpenPart(part.index) },
                )
                Hairline(Modifier.padding(horizontal = 24.dp))
            }
            item {
                Spacer(Modifier.height(120.dp))
            }
        }
    }
}

@Composable
private fun PartRow(part: PartUiState, onClick: () -> Unit) {
    val colors = LocalFuzzyColors.current
    val type = LocalFuzzyType.current
    val fraction = when (part.stage) {
        PartStage.DONE -> 1f
        PartStage.EXTRACTING -> if (part.extractTotal > 0) {
            (part.extractDone.toDouble() / part.extractTotal).toFloat()
        } else {
            0f
        }
        else -> if (part.bytesTotal > 0) {
            (part.bytesDone.toDouble() / part.bytesTotal).toFloat()
        } else {
            0f
        }
    }.coerceIn(0f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClickLabel = "Parte ${part.index}") { onClick() }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FuzzyText(
            text = String.format(Locale.US, "%02d", part.index),
            modifier = Modifier.width(44.dp),
            style = type.number.copy(fontSize = type.section.fontSize),
            color = if (part.stage == PartStage.DONE) colors.ok else colors.textFaint,
        )
        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StageChip(
                    stage = stageLabel(part.stage),
                    color = stageColor(part.stage),
                )
                Spacer(Modifier.width(10.dp))
                FuzzyText(
                    text = formatBytes(if (part.zipBytes > 0) part.zipBytes else part.bytesTotal),
                    style = type.mono,
                    color = colors.textDim,
                )
            }
            if (part.fileName != null) {
                Spacer(Modifier.height(5.dp))
                FuzzyText(
                    text = part.fileName,
                    style = type.bodySmall,
                    color = colors.textFaint,
                    maxLines = 1,
                )
            }
            if (part.error != null && part.stage == PartStage.FAILED) {
                Spacer(Modifier.height(3.dp))
                FuzzyText(
                    text = part.error,
                    style = type.bodySmall,
                    color = colors.danger,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.height(8.dp))
            ProgressLine(
                progress = fraction,
                color = when (part.stage) {
                    PartStage.DONE -> colors.ok
                    PartStage.FAILED -> colors.danger
                    else -> colors.accent
                },
            )
        }
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
private fun stageColor(stage: PartStage): androidx.compose.ui.graphics.Color {
    val colors = LocalFuzzyColors.current
    return when (stage) {
        PartStage.DONE -> colors.ok
        PartStage.FAILED -> colors.danger
        PartStage.PENDING, PartStage.PAUSED -> colors.textFaint
        else -> colors.accent
    }
}
