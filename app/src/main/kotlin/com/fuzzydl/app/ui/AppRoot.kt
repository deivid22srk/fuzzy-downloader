package com.fuzzydl.app.ui

import android.app.Activity
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuzzydl.app.R
import com.fuzzydl.app.ui.components.AmbientBackground
import com.fuzzydl.app.ui.components.AppIcon
import com.fuzzydl.app.ui.components.GlassNavBar
import com.fuzzydl.app.ui.screens.DetailScreen
import com.fuzzydl.app.ui.screens.DownloadsScreen
import com.fuzzydl.app.ui.screens.LibraryScreen
import com.fuzzydl.app.ui.screens.SettingsScreen
import com.fuzzydl.app.ui.screens.StoreScreen
import com.fuzzydl.app.ui.theme.FuzzyTheme

@Composable
fun AppRoot(vm: DashboardViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val dark = when (settings.theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    FuzzyTheme(darkTheme = dark) {
        val view = LocalView.current
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }

        var tab by rememberSaveable { mutableStateOf(0) }
        var detailId by rememberSaveable { mutableStateOf<String?>(null) }

        val lifecycleOwner = LocalLifecycleOwner.current
        var resumeTick by remember { mutableIntStateOf(0) }
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) resumeTick++
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        Box(Modifier.fillMaxSize()) {
            AmbientBackground()

            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                when {
                    tab == 0 && detailId != null -> DetailScreen(
                        vm = vm,
                        gameId = detailId!!,
                        refreshKey = resumeTick,
                        onBack = { detailId = null },
                    )
                    tab == 0 -> StoreScreen(
                        vm = vm,
                        refreshKey = resumeTick,
                        onOpenGame = { detailId = it },
                    )
                    tab == 1 -> LibraryScreen(
                        vm = vm,
                        refreshKey = resumeTick,
                        onOpenGame = {
                            detailId = it
                            tab = 0
                        },
                        onOpenStore = { tab = 0 },
                    )
                    tab == 2 -> DownloadsScreen(vm)
                    else -> SettingsScreen(vm)
                }
            }

            GlassNavBar(
                items = listOf(
                    AppIcon.Store to stringResource(R.string.store_title),
                    AppIcon.Library to stringResource(R.string.library_title),
                    AppIcon.Download to stringResource(R.string.downloads_title),
                    AppIcon.Sliders to stringResource(R.string.settings_title),
                ),
                selected = tab,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp),
                onSelect = { index ->
                    tab = index
                    detailId = null
                },
            )
        }
    }
}