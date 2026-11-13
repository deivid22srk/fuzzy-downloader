package com.fuzzydl.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuzzydl.app.R
import com.fuzzydl.app.store.DownloadStatus
import com.fuzzydl.app.store.StoreCatalog
import com.fuzzydl.app.store.StoreGame
import com.fuzzydl.app.ui.DashboardViewModel
import com.fuzzydl.app.ui.components.AppIcon
import com.fuzzydl.app.ui.components.EmptyState
import com.fuzzydl.app.ui.components.FuzzyText
import com.fuzzydl.app.ui.components.GameCover
import com.fuzzydl.app.ui.components.GameCoverBadge
import com.fuzzydl.app.ui.components.GlassButton
import com.fuzzydl.app.ui.components.GlassCard
import com.fuzzydl.app.ui.components.GlassIconButton
import com.fuzzydl.app.ui.components.ProgressBar
import com.fuzzydl.app.ui.components.SectionHeader
import com.fuzzydl.app.ui.components.SegmentedGlass
import com.fuzzydl.app.ui.formatBytes
import com.fuzzydl.app.ui.theme.AppShapes
import com.fuzzydl.app.ui.theme.LocalAppColors
import com.fuzzydl.app.ui.theme.LocalAppType
import java.util.Locale

@Composable
fun StoreScreen(
    vm: DashboardViewModel,
    refreshKey: Int,
    onOpenGame: (StoreGame) -> Unit,
    onOpenInternal: () -> Unit,
) {
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val loading by vm.catalogLoading.collectAsStateWithLifecycle()
    val downloads by vm.storeDownloads.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current
    val type = LocalAppType.current

    var mode by rememberSaveable { mutableStateOf(0) }
    var category by rememberSaveable { mutableStateOf("all") }
    val installed = remember(catalog, refreshKey, downloads.size) {
        catalog?.games?.associate { it.id to vm.isInstalled(it.packageName) } ?: emptyMap()
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                FuzzyText(
                    text = stringResource(R.string.store_title),
                    style = type.title,
                    color = colors.text,
                )
                Spacer(Modifier.height(2.dp))
                FuzzyText(
                    text = stringResource(R.string.store_subtitle),
                    style = type.bodySmall,
                    color = colors.textDim,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(12.dp))
            GlassIconButton(
                icon = AppIcon.Refresh,
                contentDescription = stringResource(R.string.store_refresh),
                onClick = { vm.loadCatalog(force = true) },
            )
        }

        Spacer(Modifier.height(14.dp))

        SegmentedGlass(
            options = listOf(
                "store" to stringResource(R.string.store_tab_store),
                "library" to stringResource(R.string.store_tab_library),
            ),
            selected = if (mode == 0) "store" else "library",
            onSelect = { mode = if (it == "store") 0 else 1 },
        )

        Spacer(Modifier.height(16.dp))

        when {
            catalog == null -> StoreMessage(text = stringResource(if (loading) R.string.store_loading else R.string.store_no_catalog))
            catalog!!.games.isEmpty() -> StoreMessage(text = stringResource(R.string.store_no_catalog))
            mode == 1 -> LibraryContent(
                catalog = catalog!!,
                vm = vm,
                downloads = downloads,
                installed = installed,
                onOpenGame = onOpenGame,
            )
            else -> StoreContent(
                catalog = catalog!!,
                category = category,
                onCategory = { category = it },
                downloads = downloads,
                installed = installed,
                onOpenGame = onOpenGame,
                onOpenInternal = onOpenInternal,
            )
        }
    }
}

@Composable
private fun StoreMessage(text: String) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        FuzzyText(text = text, style = LocalAppType.current.bodyMedium, color = colors.textDim)
    }
}

@Composable
private fun StoreContent(
    catalog: StoreCatalog,
    category: String,
    onCategory: (String) -> Unit,
    downloads: Map<String, com.fuzzydl.app.store.DownloadState>,
    installed: Map<String, Boolean>,
    onOpenGame: (StoreGame) -> Unit,
    onOpenInternal: () -> Unit,
) {
    val featured = catalog.games.filter { it.featured }
    val visible = if (category == "all") catalog.games else catalog.games.filter { category in it.categories }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 136.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (featured.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    SectionHeader(title = stringResource(R.string.store_featured))
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(featured, key = { it.id }) { game ->
                            GameCover(
                                game = game,
                                modifier = Modifier.width(272.dp),
                                wide = true,
                                onClick = { if (game.isInternal) onOpenInternal() else onOpenGame(game) },
                            )
                        }
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        CategoryChip(
                            label = stringResource(R.string.store_all),
                            selected = category == "all",
                            onClick = { onCategory("all") },
                        )
                    }
                    items(catalog.categories, key = { it.id }) { cat ->
                        CategoryChip(
                            label = cat.name,
                            selected = category == cat.id,
                            onClick = { onCategory(cat.id) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        items(visible, key = { it.id }) { game ->
            Box {
                GameCover(
                    game = game,
                    onClick = { if (game.isInternal) onOpenInternal() else onOpenGame(game) },
                )
                val state = downloads[game.id]
                when {
                    installed[game.id] == true -> GameCoverBadge(
                        text = stringResource(R.string.store_installed),
                        color = LocalAppColors.current.mint,
                    )
                    state != null && state.status == DownloadStatus.DOWNLOADING && state.total > 0 -> {
                        val pct = (state.done.toDouble() / state.total).toFloat().coerceIn(0f, 1f)
                        GameCoverBadge(
                            text = "${String.format(Locale.getDefault(), "%.0f", pct * 100f)}%",
                            color = LocalAppColors.current.sky,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .clip(AppShapes.chip)
            .background(if (selected) colors.sky.copy(alpha = 0.20f) else colors.glass)
            .border(
                width = 1.dp,
                color = if (selected) colors.sky.copy(alpha = 0.55f) else colors.glassBorderTop.copy(alpha = 0.30f),
                shape = AppShapes.chip,
            )
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        FuzzyText(
            text = label,
            style = LocalAppType.current.label,
            color = if (selected) colors.sky else colors.textDim,
            maxLines = 1,
        )
    }
}

@Composable
private fun LibraryContent(
    catalog: StoreCatalog,
    vm: DashboardViewModel,
    downloads: Map<String, com.fuzzydl.app.store.DownloadState>,
    installed: Map<String, Boolean>,
    onOpenGame: (StoreGame) -> Unit,
) {
    val colors = LocalAppColors.current
    val entries = catalog.games.filter { game ->
        val state = downloads[game.id]
        (state != null && state.status != DownloadStatus.NONE) || installed[game.id] == true
    }

    if (entries.isEmpty()) {
        EmptyState(
            icon = AppIcon.Library,
            title = stringResource(R.string.store_library_empty_title),
            body = stringResource(R.string.store_library_empty_body),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 136.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = { it.id }) { game ->
            LibraryRow(
                game = game,
                vm = vm,
                state = downloads[game.id],
                installed = installed[game.id] == true,
                onOpenGame = onOpenGame,
            )
        }
    }
}

@Composable
private fun LibraryRow(
    game: StoreGame,
    vm: DashboardViewModel,
    state: com.fuzzydl.app.store.DownloadState?,
    installed: Boolean,
    onOpenGame: (StoreGame) -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppType.current
    val status = state?.status ?: DownloadStatus.NONE

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameCover(
                game = game,
                modifier = Modifier.width(66.dp),
                compact = true,
                onClick = { onOpenGame(game) },
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                FuzzyText(text = game.name, style = type.bodyMedium, color = colors.text, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                val progress = when {
                    status == DownloadStatus.DOWNLOADING && (state?.total ?: 0) > 0 ->
                        (state!!.done.toDouble() / state.total).toFloat().coerceIn(0f, 1f)
                    status == DownloadStatus.READY -> 1f
                    else -> 0f
                }
                FuzzyText(
                    text = when {
                        installed -> stringResource(R.string.store_installed)
                        status == DownloadStatus.DOWNLOADING -> stringResource(R.string.store_downloading)
                        status == DownloadStatus.VERIFYING -> stringResource(R.string.store_verifying)
                        status == DownloadStatus.READY -> stringResource(R.string.store_ready)
                        status == DownloadStatus.FAILED -> stringResource(R.string.store_failed)
                        else -> formatBytes(game.sizeBytes)
                    },
                    style = type.caption,
                    color = when {
                        installed -> colors.mint
                        status == DownloadStatus.FAILED -> colors.coral
                        status == DownloadStatus.READY -> colors.sky
                        else -> colors.textDim
                    },
                )
                if (status == DownloadStatus.DOWNLOADING || status == DownloadStatus.READY) {
                    Spacer(Modifier.height(7.dp))
                    ProgressBar(progress = progress, height = 4.dp)
                }
            }
            Spacer(Modifier.width(10.dp))
            LibraryActions(game = game, vm = vm, status = status, installed = installed)
        }
    }
}

@Composable
private fun LibraryActions(
    game: StoreGame,
    vm: DashboardViewModel,
    status: DownloadStatus,
    installed: Boolean,
) {
    val colors = LocalAppColors.current
    when {
        status == DownloadStatus.DOWNLOADING || status == DownloadStatus.VERIFYING -> {
            GlassButton(
                label = stringResource(R.string.store_cancel),
                icon = AppIcon.Close,
                tint = colors.coral,
                onClick = { vm.cancelDownload(game.id) },
            )
        }
        installed -> {
            GlassButton(
                label = stringResource(R.string.store_open),
                icon = AppIcon.Play,
                onClick = { vm.launchGame(game) },
            )
        }
        status == DownloadStatus.READY -> {
            GlassButton(
                label = stringResource(R.string.store_install),
                icon = AppIcon.Download,
                onClick = { vm.installGame(game) },
            )
        }
        status == DownloadStatus.FAILED -> {
            GlassButton(
                label = stringResource(R.string.store_retry),
                icon = AppIcon.Refresh,
                onClick = { vm.downloadGame(game) },
            )
        }
        else -> {
            GlassButton(
                label = stringResource(R.string.store_download),
                icon = AppIcon.Download,
                onClick = { vm.downloadGame(game) },
            )
        }
    }
}