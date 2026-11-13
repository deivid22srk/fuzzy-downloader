package com.fuzzydl.app.ui.screens

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuzzydl.app.BuildConfig
import com.fuzzydl.app.R
import com.fuzzydl.app.ui.DashboardViewModel
import com.fuzzydl.app.ui.components.AppCard
import com.fuzzydl.app.ui.components.AppIcon
import com.fuzzydl.app.ui.components.AppIconView
import com.fuzzydl.app.ui.components.AppSwitch
import com.fuzzydl.app.ui.components.AppDivider
import com.fuzzydl.app.ui.components.CategoryChip
import com.fuzzydl.app.ui.components.FuzzyText
import com.fuzzydl.app.ui.components.GhostButton
import com.fuzzydl.app.ui.components.IconButton
import com.fuzzydl.app.ui.components.SearchField
import com.fuzzydl.app.ui.components.SectionHeader
import com.fuzzydl.app.ui.theme.LocalAppColors
import com.fuzzydl.app.ui.theme.LocalAppType

private const val APP_URL = "https://github.com/deivid22srk/fuzzy-downloader"
private const val PORTDB_URL = "https://github.com/deivid22srk/port-db"

@Composable
fun SettingsScreen(vm: DashboardViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current
    val type = LocalAppType.current

    var source by rememberSaveable { mutableStateOf(settings.dataSourceUrl) }
    var sourceDirty by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp, bottom = 120.dp),
    ) {
        FuzzyText(stringResource(R.string.settings_title), style = type.title, color = colors.text)
        Spacer(Modifier.height(12.dp))

        // ---------------- Catálogo ----------------
        SectionHeader(title = stringResource(R.string.settings_catalog))
        Spacer(Modifier.height(10.dp))
        AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            FuzzyText(stringResource(R.string.settings_source), style = type.bodyMedium, color = colors.text)
            Spacer(Modifier.height(8.dp))
            SearchField(
                value = source,
                onValueChange = {
                    source = it
                    sourceDirty = true
                },
                placeholder = "https://.../port-db/main/",
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(
                    label = stringResource(R.string.settings_save_source),
                    icon = AppIcon.Refresh,
                    enabled = sourceDirty,
                    onClick = {
                        vm.setDataSource(source.trim())
                        sourceDirty = false
                    },
                )
                GhostButton(
                    label = stringResource(R.string.settings_clear_cache),
                    icon = AppIcon.Trash,
                    onClick = { vm.clearCache() },
                )
            }
            catalog?.updated?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                FuzzyText(
                    text = stringResource(R.string.settings_updated_at, it),
                    style = type.caption,
                    color = colors.textFaint,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---------------- Downloads ----------------
        SectionHeader(title = stringResource(R.string.settings_downloads))
        Spacer(Modifier.height(10.dp))
        AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    FuzzyText(stringResource(R.string.settings_threads), style = type.bodyMedium, color = colors.text)
                    Spacer(Modifier.height(2.dp))
                    FuzzyText(stringResource(R.string.settings_threads_hint), style = type.caption, color = colors.textDim)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        icon = AppIcon.Minus,
                        contentDescription = "-",
                        size = 36.dp,
                        enabled = settings.threads > 1,
                        onClick = { vm.setThreads(settings.threads - 1) },
                    )
                    Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                        FuzzyText(settings.threads.toString(), style = type.number, color = colors.primaryLight)
                    }
                    IconButton(
                        icon = AppIcon.Plus,
                        contentDescription = "+",
                        size = 36.dp,
                        enabled = settings.threads < 8,
                        onClick = { vm.setThreads(settings.threads + 1) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            AppDivider()
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    FuzzyText(stringResource(R.string.settings_wifi), style = type.bodyMedium, color = colors.text)
                    Spacer(Modifier.height(2.dp))
                    FuzzyText(stringResource(R.string.settings_wifi_hint), style = type.caption, color = colors.textDim)
                }
                Spacer(Modifier.width(12.dp))
                AppSwitch(checked = settings.wifiOnly, onChange = { vm.setWifiOnly(it) })
            }
            Spacer(Modifier.height(14.dp))
            GhostButton(
                label = stringResource(R.string.settings_clear_finished),
                icon = AppIcon.Trash,
                onClick = { vm.clearFinished() },
            )
        }

        Spacer(Modifier.height(20.dp))

        // ---------------- Aparência ----------------
        SectionHeader(title = stringResource(R.string.settings_appearance))
        Spacer(Modifier.height(10.dp))
        AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconView(AppIcon.Moon, colors.violet, size = 18.dp)
                Spacer(Modifier.width(9.dp))
                FuzzyText(stringResource(R.string.settings_theme), style = type.bodyMedium, color = colors.text)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "system" to stringResource(R.string.theme_system),
                    "light" to stringResource(R.string.theme_light),
                    "dark" to stringResource(R.string.theme_dark),
                ).forEach { (value, label) ->
                    CategoryChip(
                        label = label,
                        selected = settings.theme == value,
                        onClick = { vm.setTheme(value) },
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---------------- Sobre ----------------
        SectionHeader(title = stringResource(R.string.settings_about))
        Spacer(Modifier.height(10.dp))
        AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            FuzzyText(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = type.caption,
                color = colors.textDim,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(
                    label = stringResource(R.string.settings_app_repo),
                    icon = AppIcon.Code,
                    onClick = { vm.openUrl(APP_URL) },
                )
                GhostButton(
                    label = stringResource(R.string.settings_portdb_repo),
                    icon = AppIcon.Database,
                    onClick = { vm.openUrl(PORTDB_URL) },
                )
            }
            Spacer(Modifier.height(12.dp))
            FuzzyText(
                text = stringResource(R.string.settings_about_text),
                style = type.caption,
                color = colors.textFaint,
            )
        }
    }
}