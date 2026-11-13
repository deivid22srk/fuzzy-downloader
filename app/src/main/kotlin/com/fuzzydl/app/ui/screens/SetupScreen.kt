package com.fuzzydl.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fuzzydl.app.R
import com.fuzzydl.app.ui.DashboardViewModel
import com.fuzzydl.app.ui.components.AppDivider
import com.fuzzydl.app.ui.components.AppIcon
import com.fuzzydl.app.ui.components.AppIconView
import com.fuzzydl.app.ui.components.FuzzyText
import com.fuzzydl.app.ui.components.GlassButton
import com.fuzzydl.app.ui.components.GlassCard
import com.fuzzydl.app.ui.components.PrimaryPillButton
import com.fuzzydl.app.ui.components.NoticeBanner
import com.fuzzydl.app.ui.formatBytes
import com.fuzzydl.app.ui.theme.LocalAppColors
import com.fuzzydl.app.ui.theme.LocalAppType

@Composable
fun SetupScreen(
    vm: DashboardViewModel,
    onDone: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppType.current
    val shizuku by vm.shizuku.collectAsState()
    val targetInstalled by vm.targetInstalled.collectAsState()
    val freeBytes by vm.freeBytes.collectAsState()
    val notifications by vm.notificationsGranted.collectAsState()

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.notifications() }

    val installed = shizuku.installed || shizuku.legacy
    val running = shizuku.running || shizuku.legacy
    val permission = shizuku.permission || shizuku.legacy

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 26.dp, bottom = 32.dp),
    ) {
        FuzzyText(
            text = stringResource(R.string.app_name),
            style = type.label,
            color = colors.sky,
        )
        Spacer(Modifier.height(10.dp))
        FuzzyText(
            text = stringResource(R.string.setup_title),
            style = type.title,
            color = colors.text,
        )
        Spacer(Modifier.height(8.dp))
        FuzzyText(
            text = stringResource(R.string.setup_subtitle),
            style = type.bodySmall,
            color = colors.textDim,
        )

        Spacer(Modifier.height(22.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                SetupRow(
                    icon = AppIcon.Link,
                    ok = installed,
                    title = stringResource(R.string.setup_shizuku_installed),
                    subtitle = if (shizuku.legacy) stringResource(R.string.status_ok) else null,
                    action = if (!installed) stringResource(R.string.action_install) else null,
                    onAction = { vm.openShizuku() },
                )
                AppDivider()
                SetupRow(
                    icon = AppIcon.Play,
                    ok = running,
                    title = stringResource(R.string.setup_shizuku_running),
                    subtitle = if (!running && installed) stringResource(R.string.setup_shizuku_steps) else null,
                    action = if (installed && !running) stringResource(R.string.action_open) else null,
                    onAction = { vm.openShizuku() },
                )
                AppDivider()
                SetupRow(
                    icon = AppIcon.Check,
                    ok = permission,
                    title = stringResource(R.string.setup_shizuku_permission),
                    subtitle = null,
                    action = if (running && !permission) stringResource(R.string.action_grant) else null,
                    onAction = { vm.requestShizukuPermission() },
                )
                AppDivider()
                SetupRow(
                    icon = AppIcon.Sparkle,
                    ok = targetInstalled,
                    title = stringResource(R.string.setup_target_app),
                    subtitle = if (!targetInstalled) stringResource(R.string.warn_target_missing) else null,
                    action = if (!targetInstalled) stringResource(R.string.action_install) else null,
                    onAction = { vm.openTargetApp() },
                )
                AppDivider()
                SetupRow(
                    icon = AppIcon.Storage,
                    ok = freeBytes <= 0 || freeBytes > 70L * 1024 * 1024 * 1024,
                    title = stringResource(R.string.setup_space),
                    subtitle = stringResource(R.string.space_line, formatBytes(freeBytes)),
                    action = stringResource(R.string.action_refresh),
                    onAction = { vm.refresh() },
                )
                AppDivider()
                SetupRow(
                    icon = AppIcon.Bell,
                    ok = notifications,
                    title = stringResource(R.string.setup_notifications),
                    subtitle = if (!notifications) stringResource(R.string.warn_notifications) else null,
                    action = if (!notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        stringResource(R.string.action_enable)
                    } else {
                        null
                    },
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }
        }

        if (!shizuku.ready && !shizuku.legacy) {
            Spacer(Modifier.height(14.dp))
            NoticeBanner(
                text = stringResource(R.string.warn_shizuku_required),
                tone = colors.amber,
                icon = AppIcon.Alert,
            )
        }

        Spacer(Modifier.height(22.dp))
        PrimaryPillButton(
            label = stringResource(R.string.setup_ok),
            modifier = Modifier.fillMaxWidth(),
            icon = AppIcon.Check,
            enabled = shizuku.ready || shizuku.legacy,
            onClick = {
                vm.dismissSetup()
                onDone()
            },
        )
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GlassButton(
                label = stringResource(R.string.action_skip),
                onClick = {
                    vm.dismissSetup()
                    onDone()
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        FuzzyText(
            text = stringResource(R.string.setup_footer),
            modifier = Modifier.fillMaxWidth(),
            style = type.caption,
            color = colors.textFaint,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SetupRow(
    icon: AppIcon,
    ok: Boolean,
    title: String,
    subtitle: String?,
    action: String?,
    onAction: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppType.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colors.glassStrong),
            contentAlignment = Alignment.Center,
        ) {
            AppIconView(
                icon = icon,
                tint = if (ok) colors.mint else colors.textDim,
                size = 19.dp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            FuzzyText(text = title, style = type.bodyMedium, color = colors.text)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                FuzzyText(text = subtitle, style = type.bodySmall, color = colors.textDim)
            }
        }
        Spacer(Modifier.width(10.dp))
        when {
            action != null -> GlassButton(
                label = action,
                icon = AppIcon.ChevronRight,
                onClick = onAction,
            )
            ok -> AppIconView(AppIcon.Check, colors.mint, size = 20.dp, strokeWidth = 2.dp)
            else -> Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.textFaint),
            )
        }
    }
}