package com.fuzzydl.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fuzzydl.app.R
import com.fuzzydl.app.data.PartStage
import com.fuzzydl.app.download.PartUiState
import com.fuzzydl.app.ui.formatBytes
import com.fuzzydl.app.ui.formatPercent
import com.fuzzydl.app.ui.theme.LocalAppColors
import com.fuzzydl.app.ui.theme.LocalAppType
import com.fuzzydl.app.ui.theme.glass

/** Bottom sheet de vidro próprio, sem Material3. */
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
                .background(Color.Black.copy(alpha = 0.64f))
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
    val colors = LocalAppColors.current
    val type = LocalAppType.current
    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape, strong = true)
            .navigationBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(top = 12.dp, bottom = 22.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SheetHandle()
        }
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                FuzzyText(
                    text = stringResource(R.string.label_part, part.index),
                    style = type.headline,
                    color = colors.text,
                )
                Spacer(Modifier.height(5.dp))
                StatusBadge(stage = part.stage)
            }
            GlassIconButton(
                icon = AppIcon.Close,
                contentDescription = stringResource(R.string.part_close),
                onClick = onDismiss,
                size = 42.dp,
            )
        }

        Spacer(Modifier.height(18.dp))

        val done = when (part.stage) {
            PartStage.EXTRACTING -> part.extractDone
            else -> part.bytesDone
        }
        val total = when (part.stage) {
            PartStage.EXTRACTING -> part.extractTotal
            else -> part.bytesTotal
        }
        if (total > 0) {
            ProgressBar(
                progress = (done.toDouble() / total).toFloat().coerceIn(0f, 1f),
                height = 6.dp,
            )
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
            Spacer(Modifier.height(16.dp))
        }

        part.fileName?.let { name ->
            KeyValueRow(label = stringResource(R.string.part_zip), value = name)
        }
        KeyValueRow(
            label = stringResource(R.string.label_size),
            value = formatBytes(if (part.zipBytes > 0) part.zipBytes else part.bytesTotal),
        )
        KeyValueRow(
            label = stringResource(R.string.label_files),
            value = part.filesExtracted.toString(),
        )
        part.mirror?.let { mirror ->
            KeyValueRow(label = stringResource(R.string.label_mirror), value = mirror)
        }

        if (!part.error.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            NoticeBanner(
                text = part.error,
                tone = colors.coral,
                icon = AppIcon.Alert,
            )
        }

        Spacer(Modifier.height(18.dp))
        PrimaryPillButton(
            label = stringResource(R.string.part_restart),
            modifier = Modifier.fillMaxWidth(),
            icon = AppIcon.Refresh,
            enabled = !running,
            onClick = {
                onRestart(part.index)
                onDismiss()
            },
        )
    }
}