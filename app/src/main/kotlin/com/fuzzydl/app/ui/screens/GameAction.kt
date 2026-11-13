package com.fuzzydl.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fuzzydl.app.R
import com.fuzzydl.app.download.DlItem
import com.fuzzydl.app.download.DlStatus
import com.fuzzydl.app.store.PortGame
import com.fuzzydl.app.ui.DashboardViewModel
import com.fuzzydl.app.ui.components.ActionButton
import com.fuzzydl.app.ui.components.AppIcon
import com.fuzzydl.app.ui.components.FuzzyText
import com.fuzzydl.app.ui.components.MiniRing
import com.fuzzydl.app.ui.components.PrimaryButton
import com.fuzzydl.app.ui.components.pressable
import com.fuzzydl.app.ui.theme.AppShapes
import com.fuzzydl.app.ui.theme.LocalAppColors
import com.fuzzydl.app.ui.theme.LocalAppType
import com.fuzzydl.app.ui.theme.appSurface

enum class GameActionKind { DOWNLOAD, PAUSE, RESUME, INSTALL, OPEN_APP, OPEN_WEB, RETRY }

fun actionKind(game: PortGame, download: DlItem?, installed: Boolean): GameActionKind = when {
    download?.status == DlStatus.READY -> GameActionKind.INSTALL
    installed -> GameActionKind.OPEN_APP
    download?.status == DlStatus.DOWNLOADING ||
        download?.status == DlStatus.PROBING ||
        download?.status == DlStatus.VERIFYING ||
        download?.status == DlStatus.QUEUED -> GameActionKind.PAUSE
    download?.status == DlStatus.PAUSED -> GameActionKind.RESUME
    download?.status == DlStatus.FAILED -> GameActionKind.RETRY
    game.isWebOnly -> GameActionKind.OPEN_WEB
    else -> GameActionKind.DOWNLOAD
}

private data class ActionSpec(val label: String, val icon: AppIcon)

@Composable
private fun spec(kind: GameActionKind): ActionSpec = when (kind) {
    GameActionKind.DOWNLOAD -> ActionSpec(stringResource(R.string.store_download), AppIcon.Download)
    GameActionKind.PAUSE -> ActionSpec(stringResource(R.string.store_pause), AppIcon.Pause)
    GameActionKind.RESUME -> ActionSpec(stringResource(R.string.store_resume), AppIcon.Play)
    GameActionKind.INSTALL -> ActionSpec(stringResource(R.string.store_install), AppIcon.Download)
    GameActionKind.OPEN_APP -> ActionSpec(stringResource(R.string.store_open), AppIcon.Play)
    GameActionKind.OPEN_WEB -> ActionSpec(stringResource(R.string.store_open_site), AppIcon.ExternalLink)
    GameActionKind.RETRY -> ActionSpec(stringResource(R.string.store_retry), AppIcon.Refresh)
}

private fun perform(kind: GameActionKind, game: PortGame, download: DlItem?, vm: DashboardViewModel) {
    when (kind) {
        GameActionKind.DOWNLOAD, GameActionKind.RETRY -> vm.download(game)
        GameActionKind.PAUSE -> vm.pause(game.id)
        GameActionKind.RESUME -> vm.resume(game.id)
        GameActionKind.INSTALL -> {
            if (vm.canInstallApk()) vm.installGame(game.id) else vm.requestUnknownSources()
        }
        GameActionKind.OPEN_APP -> vm.launchGame(game.packageName)
        GameActionKind.OPEN_WEB -> game.webUrl?.let { vm.openUrl(it) }
    }
}

/** Botão compacto para as linhas da loja. */
@Composable
fun GameActionButtonCompact(
    game: PortGame,
    download: DlItem?,
    installed: Boolean,
    vm: DashboardViewModel,
    modifier: Modifier = Modifier,
) {
    val kind = actionKind(game, download, installed)
    val s = spec(kind)
    val progress: (() -> Float)? = if (kind == GameActionKind.PAUSE) {
        { download?.progress ?: 0f }
    } else {
        null
    }
    ActionButton(
        label = s.label,
        icon = s.icon,
        progress = progress,
        highlight = kind == GameActionKind.PAUSE || kind == GameActionKind.OPEN_APP,
        onClick = { perform(kind, game, download, vm) },
        modifier = modifier,
    )
}

/** Botão grande e fixo da tela de detalhes. */
@Composable
fun GameActionButtonLarge(
    game: PortGame,
    download: DlItem?,
    installed: Boolean,
    vm: DashboardViewModel,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val kind = actionKind(game, download, installed)
    val s = spec(kind)
    if (kind == GameActionKind.PAUSE) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(54.dp)
                .appSurface(AppShapes.pill, elevated = false, fill = colors.surfaceHi)
                .pressable(onClickLabel = s.label) { perform(kind, game, download, vm) },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniRing(
                progress = { download?.progress ?: 0f },
                size = 20.dp,
                color = colors.primaryLight,
                track = colors.surfacePressed,
            )
            Spacer(Modifier.width(10.dp))
            FuzzyText(
                text = stringResource(
                    R.string.store_downloading_percent,
                    ((download?.progress ?: 0f) * 100).toInt(),
                ),
                style = LocalAppType.current.button,
                color = colors.text,
            )
        }
    } else {
        PrimaryButton(
            label = s.label,
            modifier = modifier.fillMaxWidth(),
            icon = s.icon,
            enabled = !(kind == GameActionKind.OPEN_WEB && game.webUrl == null),
            onClick = { perform(kind, game, download, vm) },
        )
    }
}