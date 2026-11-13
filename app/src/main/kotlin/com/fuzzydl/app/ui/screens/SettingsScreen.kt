package com.fuzzydl.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fuzzydl.app.BuildConfig
import com.fuzzydl.app.R
import com.fuzzydl.app.data.SettingsData
import com.fuzzydl.app.ui.DashboardViewModel
import com.fuzzydl.app.ui.components.DotSelector
import com.fuzzydl.app.ui.components.FuzzyText
import com.fuzzydl.app.ui.components.FuzzyToggle
import com.fuzzydl.app.ui.components.GhostButton
import com.fuzzydl.app.ui.components.Hairline
import com.fuzzydl.app.ui.components.KeyValue
import com.fuzzydl.app.ui.components.SectionLabel
import com.fuzzydl.app.ui.components.Segmented
import com.fuzzydl.app.ui.components.StatusDot
import com.fuzzydl.app.ui.components.pressable
import com.fuzzydl.app.ui.theme.LocalFuzzyColors
import com.fuzzydl.app.ui.theme.LocalFuzzyType

private const val GITHUB_URL = "https://github.com/deivid22srk/fuzzy-downloader"

@Composable
fun SettingsScreen(
    settings: SettingsData,
    vm: DashboardViewModel,
) {
    val colors = LocalFuzzyColors.current
    val type = LocalFuzzyType.current
    val context = LocalContext.current
    val shizuku by vm.shizuku.collectAsState()
    val freeBytes by vm.freeBytes.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 28.dp, bottom = 120.dp),
    ) {
        SectionLabel(stringResource(R.string.app_name))
        Spacer(Modifier.height(18.dp))
        FuzzyText(
            text = stringResource(R.string.settings_title),
            style = type.title,
            color = colors.text,
        )
        Spacer(Modifier.height(24.dp))

        // ---------------- Shizuku ----------------
        SectionLabel(stringResource(R.string.settings_shizuku))
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .padding(16.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(
                        color = when {
                            shizuku.ready -> colors.ok
                            shizuku.installed -> colors.accent
                            else -> colors.textFaint
                        },
                    )
                    Spacer(Modifier.width(10.dp))
                    FuzzyText(
                        text = when {
                            shizuku.legacy -> stringResource(R.string.status_ok)
                            shizuku.ready -> stringResource(R.string.status_ok)
                            shizuku.installed && !shizuku.running -> stringResource(R.string.stage_paused)
                            shizuku.installed -> stringResource(R.string.status_missing)
                            else -> stringResource(R.string.status_missing)
                        },
                        style = type.body,
                        color = colors.text,
                    )
                }
                if (!shizuku.ready) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton(
                            label = stringResource(R.string.action_grant),
                            onClick = { vm.requestShizukuPermission() },
                        )
                        GhostButton(
                            label = stringResource(R.string.settings_open_shizuku),
                            onClick = { vm.openShizuku() },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(26.dp))

        // ---------------- Conexões ----------------
        SectionLabel(stringResource(R.string.settings_connections))
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FuzzyText(
                text = settings.threads.toString(),
                style = type.number,
                color = colors.accent,
            )
            FuzzyText(
                text = "1–8",
                style = type.mono,
                color = colors.textFaint,
            )
        }
        DotSelector(
            selected = settings.threads,
            onSelect = { vm.setThreads(it) },
        )
        Spacer(Modifier.height(6.dp))
        FuzzyText(
            text = stringResource(R.string.settings_connections_hint),
            style = type.bodySmall,
            color = colors.textDim,
        )

        Spacer(Modifier.height(26.dp))

        // ---------------- Wi-Fi ----------------
        SettingToggleRow(
            title = stringResource(R.string.settings_wifi),
            subtitle = stringResource(R.string.settings_wifi_hint),
            checked = settings.wifiOnly,
            onChange = { vm.setWifiOnly(it) },
        )

        Spacer(Modifier.height(20.dp))
        Hairline()

        // ---------------- Tema ----------------
        Spacer(Modifier.height(20.dp))
        SectionLabel(stringResource(R.string.settings_theme))
        Spacer(Modifier.height(10.dp))
        Segmented(
            options = listOf(
                "system" to stringResource(R.string.theme_system),
                "light" to stringResource(R.string.theme_light),
                "dark" to stringResource(R.string.theme_dark),
            ),
            selected = settings.theme,
            onSelect = { vm.setTheme(it) },
        )

        Spacer(Modifier.height(26.dp))

        // ---------------- Destino ----------------
        SectionLabel(stringResource(R.string.settings_destination))
        Spacer(Modifier.height(8.dp))
        FuzzyText(
            text = vm.config.targetDir,
            style = type.mono,
            color = colors.textDim,
        )
        Spacer(Modifier.height(4.dp))
        FuzzyText(
            text = stringResource(R.string.settings_destination_readonly) +
                " · " + stringResource(R.string.label_free_space) + " " + com.fuzzydl.app.ui.formatBytes(freeBytes),
            style = type.bodySmall,
            color = colors.textFaint,
        )

        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(22.dp))
            SettingToggleRow(
                title = stringResource(R.string.settings_simulation),
                subtitle = stringResource(R.string.settings_simulation_hint),
                checked = settings.simulation,
                onChange = { vm.setSimulation(it) },
            )
        }

        Spacer(Modifier.height(24.dp))
        Hairline()

        // ---------------- Log / projeto ----------------
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton(
                label = stringResource(R.string.settings_export_log),
                onClick = {
                    val file = vm.exportLog()
                    if (file != null) {
                        val share = vm.shareLogIntent(file)
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(share, context.getString(R.string.export_log_chooser)),
                            )
                        }
                    }
                },
            )
            GhostButton(
                label = "GitHub",
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(GITHUB_URL))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
        }

        Spacer(Modifier.height(26.dp))

        SectionLabel(stringResource(R.string.settings_about))
        Spacer(Modifier.height(8.dp))
        FuzzyText(
            text = stringResource(R.string.settings_about_text),
            style = type.bodySmall,
            color = colors.textDim,
        )
        Spacer(Modifier.height(12.dp))
        KeyValue(
            label = "Rust + Shizuku",
            value = stringResource(
                R.string.settings_version,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
            ),
        )

        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GhostButton(
                label = stringResource(R.string.action_refresh),
                onClick = { vm.refresh() },
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = LocalFuzzyColors.current
    val type = LocalFuzzyType.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            FuzzyText(text = title, style = type.body, color = colors.text)
            Spacer(Modifier.height(4.dp))
            FuzzyText(text = subtitle, style = type.bodySmall, color = colors.textDim)
        }
        Spacer(Modifier.width(16.dp))
        FuzzyToggle(checked = checked, onChange = onChange)
    }
}
