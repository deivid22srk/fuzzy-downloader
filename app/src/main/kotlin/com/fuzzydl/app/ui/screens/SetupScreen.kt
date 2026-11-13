package com.fuzzydl.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fuzzydl.app.R
import com.fuzzydl.app.ui.DashboardViewModel
import com.fuzzydl.app.ui.components.FuzzyText
import com.fuzzydl.app.ui.components.GhostButton
import com.fuzzydl.app.ui.components.Hairline
import com.fuzzydl.app.ui.components.PrimaryButton
import com.fuzzydl.app.ui.components.SectionLabel
import com.fuzzydl.app.ui.components.StatusDot
import com.fuzzydl.app.ui.formatBytes
import com.fuzzydl.app.ui.theme.LocalFuzzyColors
import com.fuzzydl.app.ui.theme.LocalFuzzyType
import androidx.compose.ui.res.stringResource

@Composable
fun SetupScreen(
    vm: DashboardViewModel,
    onDone: () -> Unit,
) {
    val colors = LocalFuzzyColors.current
    val type = LocalFuzzyType.current
    val shizuku by vm.shizuku.collectAsState()
    val targetInstalled by vm.targetInstalled.collectAsState()
    val freeBytes by vm.freeBytes.collectAsState()
    val notifications by vm.notificationsGranted.collectAsState()

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.notifications() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 32.dp, bottom = 32.dp),
    ) {
        SectionLabel(stringResource(R.string.app_name))
        Spacer(Modifier.height(18.dp))
        FuzzyText(
            text = stringResource(R.string.setup_title),
            style = type.title,
            color = colors.text,
        )
        Spacer(Modifier.height(10.dp))
        FuzzyText(
            text = stringResource(R.string.setup_subtitle),
            style = type.bodySmall,
            color = colors.textDim,
        )
        Spacer(Modifier.height(28.dp))

        // Shizuku
        val installed = shizuku.installed || shizuku.legacy
        val running = shizuku.running || shizuku.legacy
        val permission = shizuku.permission || shizuku.legacy
        SetupRow(
            ok = installed,
            title = stringResource(R.string.setup_shizuku_installed),
            subtitle = if (shizuku.legacy) stringResource(R.string.status_ok) else null,
            action = if (!installed) stringResource(R.string.action_install) else null,
            onAction = { vm.openShizuku() },
        )
        SetupRow(
            ok = running,
            title = stringResource(R.string.setup_shizuku_running),
            subtitle = if (!running && installed) stringResource(R.string.setup_shizuku_steps) else null,
            action = if (installed && !running) stringResource(R.string.action_open) else null,
            onAction = { vm.openShizuku() },
        )
        SetupRow(
            ok = permission,
            title = stringResource(R.string.setup_shizuku_permission),
            subtitle = null,
            action = if (running && !permission) stringResource(R.string.action_grant) else null,
            onAction = { vm.requestShizukuPermission() },
        )

        Hairline(Modifier.padding(vertical = 6.dp))

        SetupRow(
            ok = targetInstalled,
            title = stringResource(R.string.setup_target_app),
            subtitle = if (!targetInstalled) stringResource(R.string.warn_target_missing) else null,
            action = if (!targetInstalled) stringResource(R.string.action_install) else null,
            onAction = { vm.openTargetApp() },
        )
        SetupRow(
            ok = freeBytes <= 0 || freeBytes > 70L * 1024 * 1024 * 1024,
            title = stringResource(R.string.setup_space),
            subtitle = stringResource(R.string.space_line, formatBytes(freeBytes)),
            action = stringResource(R.string.action_refresh),
            onAction = { vm.refresh() },
        )
        SetupRow(
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

        Spacer(Modifier.height(30.dp))
        PrimaryButton(
            label = stringResource(R.string.setup_ok),
            modifier = Modifier.fillMaxWidth(),
            enabled = shizuku.ready || shizuku.legacy,
            onClick = {
                vm.dismissSetup()
                onDone()
            },
        )
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GhostButton(
                label = stringResource(R.string.action_skip),
                onClick = {
                    vm.dismissSetup()
                    onDone()
                },
            )
        }
        if (!shizuku.ready && !shizuku.legacy) {
            Spacer(Modifier.height(14.dp))
            FuzzyText(
                text = stringResource(R.string.warn_shizuku_required),
                modifier = Modifier.fillMaxWidth(),
                style = type.bodySmall,
                color = colors.danger,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SetupRow(
    ok: Boolean,
    title: String,
    subtitle: String?,
    action: String?,
    onAction: () -> Unit,
) {
    val colors = LocalFuzzyColors.current
    val type = LocalFuzzyType.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = if (ok) colors.ok else colors.textFaint, size = 8.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            FuzzyText(text = title, style = type.body, color = colors.text)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                FuzzyText(text = subtitle, style = type.bodySmall, color = colors.textDim)
            }
        }
        if (action != null) {
            Spacer(Modifier.width(12.dp))
            GhostButton(label = action, onClick = onAction)
        } else if (ok) {
            Spacer(Modifier.width(12.dp))
            FuzzyText(
                text = stringResource(R.string.status_ok),
                style = type.label,
                color = colors.ok,
            )
        }
    }
}
