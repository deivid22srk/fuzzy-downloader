package com.fuzzydl.app.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuzzydl.app.data.PartStage
import com.fuzzydl.app.download.DownloadService
import com.fuzzydl.app.ui.components.AmbientBackground
import com.fuzzydl.app.ui.components.GlassNavBar
import com.fuzzydl.app.ui.components.PartDetailSheet
import com.fuzzydl.app.ui.screens.HomeScreen
import com.fuzzydl.app.ui.screens.PartsScreen
import com.fuzzydl.app.ui.screens.SettingsScreen
import com.fuzzydl.app.ui.screens.SetupScreen
import com.fuzzydl.app.ui.theme.FuzzyTheme
import com.fuzzydl.app.ui.theme.LocalAppColors

@Composable
fun AppRoot(vm: DashboardViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    val setupDone by vm.setupDone.collectAsStateWithLifecycle()

    val dark = when (settings.theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    FuzzyTheme(darkTheme = dark) {
        val colors = LocalAppColors.current
        val context = LocalContext.current
        val view = LocalView.current
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }

        var tab by rememberSaveable { mutableIntStateOf(0) }
        var selectedPart by remember { mutableStateOf<Int?>(null) }
        val selected = selectedPart?.let { index -> engine.parts.firstOrNull { it.index == index } }

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
                        0 -> HomeScreen(
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
                                    tab = 1
                                    selectedPart = index
                                }
                            },
                            onOpenPart = { index ->
                                tab = 1
                                selectedPart = index
                            },
                            onOpenParts = { tab = 1 },
                            onOpenSettings = {
                                tab = 2
                                vm.refresh()
                            },
                        )
                        1 -> PartsScreen(
                            state = engine,
                            onOpenPart = { selectedPart = it },
                        )
                        else -> SettingsScreen(settings = settings, vm = vm)
                    }
                }

                GlassNavBar(
                    selected = tab,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 10.dp),
                    onSelect = { index ->
                        tab = index
                        if (index == 2) vm.refresh()
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