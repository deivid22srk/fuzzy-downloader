package com.fuzzydl.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuzzydl.app.R
import com.fuzzydl.app.data.PartStage
import com.fuzzydl.app.download.DownloadService
import com.fuzzydl.app.ui.components.AmbientBackground
import com.fuzzydl.app.ui.components.AppIcon
import com.fuzzydl.app.ui.components.GlassNavBar
import com.fuzzydl.app.ui.components.PartDetailSheet
import com.fuzzydl.app.ui.screens.GameDetailScreen
import com.fuzzydl.app.ui.screens.HomeScreen
import com.fuzzydl.app.ui.screens.PartsScreen
import com.fuzzydl.app.ui.screens.SettingsScreen
import com.fuzzydl.app.ui.screens.SetupScreen
import com.fuzzydl.app.ui.screens.StoreScreen
import com.fuzzydl.app.ui.theme.FuzzyTheme

@Composable
fun AppRoot(vm: DashboardViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    val setupDone by vm.setupDone.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val downloads by vm.storeDownloads.collectAsStateWithLifecycle()

    val dark = when (settings.theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    FuzzyTheme(darkTheme = dark) {
        val context = LocalContext.current
        val view = LocalView.current
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }

        var tab by rememberSaveable { mutableStateOf(0) }
        var selectedPart by remember { mutableStateOf<Int?>(null) }
        var storeGameId by rememberSaveable { mutableStateOf<String?>(null) }
        val selected = selectedPart?.let { index -> engine.parts.firstOrNull { it.index == index } }

        val lifecycleOwner = LocalLifecycleOwner.current
        var resumeTick by remember { mutableIntStateOf(0) }
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) resumeTick++
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        val canInstall = remember(resumeTick) { vm.canInstallApk() }

        Box(Modifier.fillMaxSize()) {
            AmbientBackground()

            if (!setupDone) {
                SetupScreen(vm = vm, onDone = { })
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    when (tab) {
                        0 -> {
                            val detail = storeGameId?.let { id -> catalog?.find(id) }
                            if (detail != null) {
                                GameDetailScreen(
                                    game = detail,
                                    download = downloads[detail.id],
                                    installed = remember(detail.id, downloads[detail.id]?.status, resumeTick) {
                                        vm.isInstalled(detail.packageName)
                                    },
                                    canInstall = canInstall,
                                    onBack = { storeGameId = null },
                                    onDownload = { vm.downloadGame(detail) },
                                    onCancel = { vm.cancelDownload(detail.id) },
                                    onInstall = { vm.installGame(detail) },
                                    onOpenApp = { vm.launchGame(detail) },
                                    onRequestUnknownSources = { vm.requestUnknownSources() },
                                    onOpenInternal = {
                                        storeGameId = null
                                        tab = 1
                                    },
                                    onOpenUrl = { url ->
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }
                                    },
                                    onDelete = { vm.deleteDownload(detail) },
                                )
                            } else {
                                StoreScreen(
                                    vm = vm,
                                    refreshKey = resumeTick,
                                    onOpenGame = { game ->
                                        if (game.isInternal) tab = 1 else storeGameId = game.id
                                    },
                                    onOpenInternal = { tab = 1 },
                                )
                            }
                        }
                        1 -> HomeScreen(
                            state = engine,
                            settings = settings,
                            onStart = {
                                DownloadService.start(context)
                                vm.start()
                            },
                            onPause = { vm.pause() },
                            onCancel = { vm.cancel() },
                            onRetry = { vm.retry(it) },
                            onDetails = {
                                val index = engine.currentIndex
                                    ?: engine.parts.firstOrNull { it.stage != PartStage.DONE }?.index
                                if (index != null) {
                                    selectedPart = index
                                    tab = 2
                                }
                            },
                            onOpenPart = { index ->
                                selectedPart = index
                                tab = 2
                            },
                            onOpenParts = { tab = 2 },
                            onOpenSettings = {
                                tab = 3
                                vm.refresh()
                            },
                        )
                        2 -> PartsScreen(
                            state = engine,
                            onOpenPart = { selectedPart = it },
                        )
                        else -> SettingsScreen(settings = settings, vm = vm)
                    }
                }

                GlassNavBar(
                    items = listOf(
                        AppIcon.Store to stringResource(R.string.store_title),
                        AppIcon.Download to stringResource(R.string.tab_download),
                        AppIcon.Grid to stringResource(R.string.tab_parts),
                        AppIcon.Sliders to stringResource(R.string.tab_settings),
                    ),
                    selected = tab,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 10.dp),
                    onSelect = { index ->
                        tab = index
                        storeGameId = null
                        if (index == 3) vm.refresh()
                    },
                )
            }

            if (setupDone) {
                PartDetailSheet(
                    part = selected,
                    running = engine.running,
                    onDismiss = { selectedPart = null },
                    onRestart = { vm.restartPart(it) },
                )
            }
        }
    }
}