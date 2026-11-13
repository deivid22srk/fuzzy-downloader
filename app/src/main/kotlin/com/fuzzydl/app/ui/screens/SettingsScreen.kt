package com.fuzzydl.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.fuzzydl.app.ui.components.AppDivider
import com.fuzzydl.app.ui.components.AppIcon
import com.fuzzydl.app.ui.components.AppIconView
import com.fuzzydl.app.ui.components.AppToggle
import com.fuzzydl.app.ui.components.FuzzyText
import com.fuzzydl.app.ui.components.GlassButton
import com.fuzzydl.app.ui.components.GlassCard
import com.fuzzydl.app.ui.components.GlassChip
import com.fuzzydl.app.ui.components.KeyValueRow
import com.fuzzydl.app.ui.components.SectionHeader
import com.fuzzydl.app.ui.components.SegmentedGlass
import com.fuzzydl.app.ui.components.ThreadSelector
import com.fuzzydl.app.ui.formatBytes
import com.fuzzydl.app.ui.theme.LocalAppColors
import com.fuzzydl.app.ui.theme.LocalAppType

private const val GITHUB_URL = "https://github.com/deivid22srk/fuzzy-downloader"

@Composable
fun SettingsScreen(
    settings: SettingsData,
    vm: DashboardViewModel,
) {
    val colors = LocalAppColors.current
    val type = LocalAppType.current
    val context = LocalContext.current
    val shizuku by vm.shizuku.collectAsState()
    val freeBytes by vm.freeBytes.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 136.dp),
    ) {
        FuzzyText(
            text = stringResource(R.string.settings_title),
            style = type.title,
            color = colors.text,
        )
        Spacer(Modifier.height(3.dp))
        FuzzyText(
            text = stringResource(R.string.settings_subtitle),
            style = type.bodySmall,
            color = colors.textDim,
        )

        Spacer(Modifier.height(22.dp))

        // ---------------- Conexão ----------------
        SectionHeader(title = stringResource(R.string.settings_group_connection))
        Spacer(Modifier.height(10.dp))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(colors.glassStrong),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIconView(AppIcon.Link, colors.sky, size = 21.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        FuzzyText(
                            text = stringResource(R.string.settings_shizuku),
                            style = type.bodyMedium,
                            color = colors.text,
                        )
                        Spacer(Modifier.height(2.dp))
                        FuzzyText(
                            text = when {
                                shizuku.legacy -> stringResource(R.string.settings_shizuku_legacy)
                                shizuku.ready -> stringResource(R.string.settings_shizuku_ready)
                                shizuku.installed && !shizuku.running -> stringResource(R.string.settings_shizuku_stopped)
                                else -> stringResource(R.string.settings_shizuku_missing)
                            },
                            style = type.bodySmall,
                            color = when {
                                shizuku.ready || shizuku.legacy -> colors.mint
                                shizuku.installed -> colors.amber
                                else -> colors.textDim
                            },
                        )
                    }
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    shizuku.ready || shizuku.legacy -> colors.mint
                                    shizuku.installed -> colors.amber
                                    else -> colors.textFaint
                                },
                            ),
                    )
                }

                if (!shizuku.ready && !shizuku.legacy) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(
                            label = stringResource(R.string.action_grant),
                            icon = AppIcon.Check,
                            onClick = { vm.requestShizukuPermission() },
                        )
                        GlassButton(
                            label = stringResource(R.string.settings_open_shizuku),
                            icon = AppIcon.ChevronRight,
                            onClick = { vm.openShizuku() },
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                AppDivider()
                Spacer(Modifier.height(14.dp))

                SettingRow(
                    icon = AppIcon.Wifi,
                    title = stringResource(R.string.settings_wifi),
                    subtitle = stringResource(R.string.settings_wifi_hint),
                ) {
                    AppToggle(checked = settings.wifiOnly, onChange = { vm.setWifiOnly(it) })
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // ---------------- Desempenho ----------------
        SectionHeader(title = stringResource(R.string.settings_group_performance))
        Spacer(Modifier.height(10.dp))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FuzzyText(
                        text = stringResource(R.string.settings_connections),
                        style = type.bodyMedium,
                        color = colors.text,
                    )
                    FuzzyText(
                        text = settings.threads.toString(),
                        style = type.number,
                        color = colors.sky,
                    )
                }
                Spacer(Modifier.height(12.dp))
                ThreadSelector(
                    selected = settings.threads,
                    onSelect = { vm.setThreads(it) },
                )
                Spacer(Modifier.height(9.dp))
                FuzzyText(
                    text = stringResource(R.string.settings_connections_hint),
                    style = type.bodySmall,
                    color = colors.textDim,
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // ---------------- Aparência ----------------
        SectionHeader(title = stringResource(R.string.settings_group_appearance))
        Spacer(Modifier.height(10.dp))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIconView(AppIcon.Moon, colors.violet, size = 18.dp)
                    Spacer(Modifier.width(9.dp))
                    FuzzyText(
                        text = stringResource(R.string.settings_theme),
                        style = type.bodyMedium,
                        color = colors.text,
                    )
                }
                Spacer(Modifier.height(12.dp))
                SegmentedGlass(
                    options = listOf(
                        "system" to stringResource(R.string.theme_system),
                        "light" to stringResource(R.string.theme_light),
                        "dark" to stringResource(R.string.theme_dark),
                    ),
                    selected = settings.theme,
                    onSelect = { vm.setTheme(it) },
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // ---------------- Armazenamento ----------------
        SectionHeader(title = stringResource(R.string.settings_group_storage))
        Spacer(Modifier.height(10.dp))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIconView(AppIcon.Storage, colors.sky, size = 18.dp)
                    Spacer(Modifier.width(9.dp))
                    FuzzyText(
                        text = stringResource(R.string.settings_destination),
                        style = type.bodyMedium,
                        color = colors.text,
                    )
                }
                Spacer(Modifier.height(8.dp))
                FuzzyText(
                    text = vm.config.targetDir,
                    style = type.bodySmall,
                    color = colors.textDim,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassChip(
                        text = stringResource(R.string.space_line, formatBytes(freeBytes)),
                        icon = AppIcon.Database,
                        color = colors.textDim,
                    )
                    GlassButton(
                        label = stringResource(R.string.action_refresh),
                        icon = AppIcon.Refresh,
                        onClick = { vm.refresh() },
                    )
                }
                Spacer(Modifier.height(8.dp))
                FuzzyText(
                    text = stringResource(R.string.settings_destination_readonly),
                    style = type.caption,
                    color = colors.textFaint,
                )
            }
        }

        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(22.dp))
            SectionHeader(title = stringResource(R.string.settings_group_debug))
            Spacer(Modifier.height(10.dp))
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
                SettingRow(
                    icon = AppIcon.Sparkle,
                    title = stringResource(R.string.settings_simulation),
                    subtitle = stringResource(R.string.settings_simulation_hint),
                ) {
                    AppToggle(checked = settings.simulation, onChange = { vm.setSimulation(it) })
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // ---------------- Projeto ----------------
        SectionHeader(title = stringResource(R.string.settings_group_project))
        Spacer(Modifier.height(10.dp))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        label = stringResource(R.string.settings_export_log),
                        modifier = Modifier.weight(1f),
                        icon = AppIcon.Folder,
                        onClick = {
                            val file = vm.exportLog()
                            if (file != null) {
                                val share = vm.shareLogIntent(file)
                                runCatching {
                                    context.startActivity(
                                        Intent.createChooser(
                                            share,
                                            context.getString(R.string.export_log_chooser),
                                        ),
                                    )
                                }
                            }
                        },
                    )
                    GlassButton(
                        label = "GitHub",
                        modifier = Modifier.weight(1f),
                        icon = AppIcon.Code,
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

                Spacer(Modifier.height(14.dp))
                AppDivider()
                Spacer(Modifier.height(10.dp))

                KeyValueRow(
                    label = stringResource(R.string.settings_version_label),
                    value = BuildConfig.VERSION_NAME,
                )
                KeyValueRow(
                    label = stringResource(R.string.settings_build_label),
                    value = BuildConfig.VERSION_CODE.toString(),
                )

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    AppIconView(AppIcon.Info, colors.textFaint, size = 16.dp, strokeWidth = 1.7.dp)
                    Spacer(Modifier.width(8.dp))
                    FuzzyText(
                        text = stringResource(R.string.settings_about_text),
                        style = type.bodySmall,
                        color = colors.textDim,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: AppIcon,
    title: String,
    subtitle: String,
    control: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppType.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colors.glassStrong),
            contentAlignment = Alignment.Center,
        ) {
            AppIconView(icon, colors.textDim, size = 19.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            FuzzyText(text = title, style = type.bodyMedium, color = colors.text)
            Spacer(Modifier.height(3.dp))
            FuzzyText(text = subtitle, style = type.bodySmall, color = colors.textDim)
        }
        Spacer(Modifier.width(12.dp))
        control()
    }
}