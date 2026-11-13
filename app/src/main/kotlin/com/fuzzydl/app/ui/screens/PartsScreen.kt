package com.fuzzydl.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fuzzydl.app.R
import com.fuzzydl.app.data.PartStage
import com.fuzzydl.app.download.EngineState
import com.fuzzydl.app.download.PartUiState
import com.fuzzydl.app.ui.components.AppIcon
import com.fuzzydl.app.ui.components.EmptyState
import com.fuzzydl.app.ui.components.FuzzyText
import com.fuzzydl.app.ui.components.PartCover
import com.fuzzydl.app.ui.components.SegmentedGlass
import com.fuzzydl.app.ui.theme.LocalAppColors
import com.fuzzydl.app.ui.theme.LocalAppType

@Composable
fun PartsScreen(
    state: EngineState,
    onOpenPart: (Int) -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppType.current
    val done = state.parts.count { it.stage == PartStage.DONE }
    var filter by rememberSaveable { mutableStateOf("all") }

    val filters = listOf(
        "all" to stringResource(R.string.parts_filter_all),
        "active" to stringResource(R.string.parts_filter_active),
        "done" to stringResource(R.string.parts_filter_done),
        "pending" to stringResource(R.string.parts_filter_pending),
    )

    val visible = when (filter) {
        "active" -> state.parts.filter {
            it.stage in setOf(
                PartStage.RESOLVING,
                PartStage.DOWNLOADING,
                PartStage.VERIFYING,
                PartStage.EXTRACTING,
                PartStage.PAUSED,
            )
        }
        "done" -> state.parts.filter { it.stage == PartStage.DONE }
        "pending" -> state.parts.filter { it.stage == PartStage.PENDING || it.stage == PartStage.FAILED }
        else -> state.parts
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                FuzzyText(
                    text = stringResource(R.string.parts_title),
                    style = type.title,
                    color = colors.text,
                )
                Spacer(Modifier.height(3.dp))
                FuzzyText(
                    text = stringResource(R.string.parts_subtitle),
                    style = type.bodySmall,
                    color = colors.textDim,
                )
            }
            FuzzyText(
                text = "$done / ${state.parts.size}",
                style = type.number,
                color = colors.sky,
            )
        }

        Spacer(Modifier.height(16.dp))

        SegmentedGlass(
            options = filters,
            selected = filter,
            onSelect = { filter = it },
        )

        Spacer(Modifier.height(16.dp))

        if (visible.isEmpty()) {
            EmptyState(
                icon = AppIcon.Grid,
                title = stringResource(R.string.parts_empty_title),
                body = stringResource(R.string.parts_empty_body),
                modifier = Modifier.padding(top = 18.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 136.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(visible, key = { it.index }) { part ->
                    PartCover(
                        index = part.index,
                        stage = part.stage,
                        progress = progressOf(part),
                        sizeBytes = if (part.zipBytes > 0) part.zipBytes else part.bytesTotal,
                        onClick = { onOpenPart(part.index) },
                    )
                }
            }
        }
    }
}

private fun progressOf(part: PartUiState): Float = when (part.stage) {
    PartStage.DONE -> 1f
    PartStage.EXTRACTING -> if (part.extractTotal > 0) {
        (part.extractDone.toDouble() / part.extractTotal).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    else -> if (part.bytesTotal > 0) {
        (part.bytesDone.toDouble() / part.bytesTotal).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
}