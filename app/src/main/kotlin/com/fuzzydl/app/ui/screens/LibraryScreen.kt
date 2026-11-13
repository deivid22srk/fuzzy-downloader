package com.fuzzydl.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuzzydl.app.R
import com.fuzzydl.app.download.DlStatus
import com.fuzzydl.app.ui.DashboardViewModel
import com.fuzzydl.app.ui.components.AppIcon
import com.fuzzydl.app.ui.components.AppRow
import com.fuzzydl.app.ui.components.CategoryChip
import com.fuzzydl.app.ui.components.EmptyState
import com.fuzzydl.app.ui.components.FuzzyText
import com.fuzzydl.app.ui.components.IconButton
import com.fuzzydl.app.ui.theme.LocalAppColors
import com.fuzzydl.app.ui.theme.LocalAppType

@Composable
fun LibraryScreen(
    vm: DashboardViewModel,
    refreshKey: Int,
    onOpenGame: (String) -> Unit,
    onOpenStore: () -> Unit,
) {
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current
    var sort by rememberSaveable { mutableStateOf("recent") }

    val entries = (catalog?.games ?: emptyList())
        .filter { game ->
            vm.isInstalled(game.packageName) || downloads[game.id] != null
        }
        .let { list ->
            if (sort == "name") list.sortedBy { it.title.lowercase() }
            else list.sortedByDescending {
                downloads[it.id]?.addedAt ?: it.dateAdded?.let { d -> d.length.toLong() } ?: 0L
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp),
    ) {
        FuzzyText(stringResource(R.string.library_title), style = LocalAppType.current.title, color = colors.text)
        Spacer(Modifier.padding(top = 2.dp))
        FuzzyText(
            text = stringResource(R.string.library_subtitle),
            style = LocalAppType.current.caption,
            color = colors.textDim,
            maxLines = 1,
        )
        Spacer(Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryChip(
                label = stringResource(R.string.library_sort_recent),
                selected = sort == "recent",
                onClick = { sort = "recent" },
            )
            CategoryChip(
                label = stringResource(R.string.library_sort_name),
                selected = sort == "name",
                onClick = { sort = "name" },
            )
        }
        Spacer(Modifier.padding(top = 12.dp))

        if (entries.isEmpty()) {
            EmptyState(
                icon = AppIcon.Library,
                title = stringResource(R.string.library_empty_title),
                body = stringResource(R.string.library_empty_body),
                actionLabel = stringResource(R.string.library_explore),
                onAction = onOpenStore,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.id }) { game ->
                    val download = downloads[game.id]
                    AppRow(
                        game = game,
                        imageUrl = { vm.imageUrl(it) },
                        download = download,
                        installed = remember(game.id, download?.status, refreshKey) {
                            vm.isInstalled(game.packageName)
                        },
                        onClick = { onOpenGame(game.id) },
                        action = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GameActionButtonCompact(game, download, vm.isInstalled(game.packageName), vm)
                                if (download?.status == DlStatus.READY || download?.status == DlStatus.FAILED) {
                                    Spacer(Modifier.width(6.dp))
                                    IconButton(
                                        icon = AppIcon.Trash,
                                        contentDescription = stringResource(R.string.store_delete),
                                        onClick = { vm.deleteDownload(game.id) },
                                        size = 36.dp,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}