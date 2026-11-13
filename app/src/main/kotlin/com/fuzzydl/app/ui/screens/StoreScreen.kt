package com.fuzzydl.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuzzydl.app.R
import com.fuzzydl.app.download.DlItem
import com.fuzzydl.app.store.PortGame
import com.fuzzydl.app.ui.DashboardViewModel
import com.fuzzydl.app.ui.components.AppIcon
import com.fuzzydl.app.ui.components.AppRow
import com.fuzzydl.app.ui.components.CategoryChip
import com.fuzzydl.app.ui.components.EmptyState
import com.fuzzydl.app.ui.components.FuzzyText
import com.fuzzydl.app.ui.components.HeroPager
import com.fuzzydl.app.ui.components.PullRefreshBox
import com.fuzzydl.app.ui.components.SearchField
import com.fuzzydl.app.ui.components.SectionHeader
import com.fuzzydl.app.ui.components.ShelfCard
import com.fuzzydl.app.ui.components.SkeletonBox
import com.fuzzydl.app.ui.theme.AppShapes
import com.fuzzydl.app.ui.theme.LocalAppColors
import com.fuzzydl.app.ui.theme.LocalAppType

@Composable
fun StoreScreen(
    vm: DashboardViewModel,
    refreshKey: Int,
    onOpenGame: (String) -> Unit,
) {
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current

    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("all") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp),
    ) {
        FuzzyText(stringResource(R.string.store_title), style = LocalAppType.current.title, color = colors.text)
        Spacer(Modifier.height(2.dp))
        FuzzyText(
            text = catalog?.sourceTagline ?: stringResource(R.string.store_subtitle),
            style = LocalAppType.current.caption,
            color = colors.textDim,
            maxLines = 1,
        )
        Spacer(Modifier.height(12.dp))
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.store_search_hint),
        )
        Spacer(Modifier.height(12.dp))

        val data = catalog
        when {
            data == null && error != null -> EmptyState(
                icon = AppIcon.Alert,
                title = stringResource(R.string.store_no_catalog),
                body = error ?: "",
                actionLabel = stringResource(R.string.store_retry),
                onAction = { vm.refresh(force = true) },
            )
            data == null -> StoreSkeleton()
            else -> PullRefreshBox(
                refreshing = loading,
                onRefresh = { vm.refresh(force = true) },
                modifier = Modifier.fillMaxSize(),
            ) {
                StoreList(
                    games = data.games,
                    categories = data.categories,
                    query = query,
                    category = category,
                    onCategory = { category = it },
                    vm = vm,
                    downloads = downloads,
                    refreshKey = refreshKey,
                    onOpenGame = onOpenGame,
                )
            }
        }
    }
}

@Composable
private fun StoreList(
    games: List<PortGame>,
    categories: List<String>,
    query: String,
    category: String,
    onCategory: (String) -> Unit,
    vm: DashboardViewModel,
    downloads: Map<String, DlItem>,
    refreshKey: Int,
    onOpenGame: (String) -> Unit,
) {
    val searching = query.isNotBlank()
    val browsing = !searching && category == "all"
    val filtered = games.filter { g ->
        (category == "all" || category in g.categories) &&
            (!searching || g.title.contains(query, true) || g.port.contains(query, true) ||
                g.shortDescription.contains(query, true) || g.categories.any { it.contains(query, true) })
    }

    val featured = if (browsing) games.filter { it.featured } else emptyList()
    val newShelf = if (browsing) {
        games.filter { !it.featured }.sortedByDescending { it.dateAdded ?: "" }.take(6)
    } else {
        emptyList()
    }
    val shelfIds = newShelf.map { it.id }.toSet()
    val rest = if (browsing) {
        games.filter { !it.featured && it.id !in shelfIds }.sortedByDescending { it.downloads }
    } else {
        filtered.sortedByDescending { it.downloads }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (featured.isNotEmpty()) {
            item {
                HeroPager(
                    games = featured,
                    imageUrl = { vm.imageUrl(it) },
                    actionLabel = { game ->
                        val kind = actionKind(game, downloads[game.id], vm.isInstalled(game.packageName))
                        when (kind) {
                            GameActionKind.OPEN_APP -> stringResource(R.string.store_open)
                            GameActionKind.OPEN_WEB -> stringResource(R.string.store_open_site)
                            GameActionKind.INSTALL -> stringResource(R.string.store_install)
                            else -> stringResource(R.string.store_download)
                        }
                    },
                    onOpen = { onOpenGame(it.id) },
                    onAction = { game ->
                        when (actionKind(game, downloads[game.id], vm.isInstalled(game.packageName))) {
                            GameActionKind.OPEN_APP -> vm.launchGame(game.packageName)
                            GameActionKind.OPEN_WEB -> game.webUrl?.let { vm.openUrl(it) }
                            GameActionKind.INSTALL ->
                                if (vm.canInstallApk()) vm.installGame(game.id) else vm.requestUnknownSources()
                            else -> vm.download(game)
                        }
                    },
                )
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    CategoryChip(
                        label = stringResource(R.string.store_all),
                        selected = category == "all",
                        onClick = { onCategory("all") },
                    )
                }
                items(categories, key = { it }) { cat ->
                    CategoryChip(
                        label = cat.replaceFirstChar { it.uppercase() },
                        selected = category == cat,
                        onClick = { onCategory(cat) },
                    )
                }
            }
        }

        if (newShelf.isNotEmpty()) {
            item {
                Column {
                    SectionHeader(title = stringResource(R.string.store_new))
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(newShelf, key = { it.id }) { game ->
                            ShelfCard(game = game, imageUrl = { vm.imageUrl(it) }, onClick = { onOpenGame(game.id) })
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = if (browsing) stringResource(R.string.store_more) else stringResource(R.string.store_results),
                subtitle = stringResource(R.string.store_count, rest.size),
            )
        }

        if (rest.isEmpty()) {
            item {
                EmptyState(
                    icon = AppIcon.Search,
                    title = stringResource(R.string.store_empty_title),
                    body = stringResource(R.string.store_empty_body),
                )
            }
        } else {
            items(rest, key = { it.id }) { game ->
                val download = downloads[game.id]
                AppRow(
                    game = game,
                    imageUrl = { vm.imageUrl(it) },
                    download = download,
                    installed = remember(game.id, download?.status, refreshKey) {
                        vm.isInstalled(game.packageName)
                    },
                    onClick = { onOpenGame(game.id) },
                    action = { GameActionButtonCompact(game, download, vm.isInstalled(game.packageName), vm) },
                )
            }
        }
    }
}

@Composable
private fun StoreSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SkeletonBox(Modifier.fillMaxWidth().height(190.dp), AppShapes.hero)
        SkeletonBox(Modifier.fillMaxWidth().height(32.dp), AppShapes.chip)
        repeat(4) { SkeletonBox(Modifier.fillMaxWidth().height(96.dp), AppShapes.card) }
    }
}