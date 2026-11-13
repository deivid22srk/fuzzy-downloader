package com.fuzzydl.app.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fuzzydl.app.BuildConfig
import com.fuzzydl.app.data.AppLog
import com.fuzzydl.app.data.PartsConfig
import com.fuzzydl.app.data.PartsRepository
import com.fuzzydl.app.data.SettingsData
import com.fuzzydl.app.download.DownloadEngine
import com.fuzzydl.app.download.EngineState
import com.fuzzydl.app.shizuku.ShizukuManager
import com.fuzzydl.app.store.DownloadState
import com.fuzzydl.app.store.StoreCatalog
import com.fuzzydl.app.store.StoreDownloads
import com.fuzzydl.app.store.StoreGame
import com.fuzzydl.app.store.StoreInstaller
import com.fuzzydl.app.store.StoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application = application
    private val prefs = application.getSharedPreferences("fuzzy_settings", Context.MODE_PRIVATE)

    val config: PartsConfig = PartsRepository(application).config

    val engine: StateFlow<EngineState> = DownloadEngine.state
    val settings: StateFlow<SettingsData> = DownloadEngine.settings
    val shizuku = ShizukuManager.status

    private val _targetInstalled = MutableStateFlow(false)
    val targetInstalled: StateFlow<Boolean> = _targetInstalled.asStateFlow()

    private val _notificationsGranted = MutableStateFlow(true)
    val notificationsGranted: StateFlow<Boolean> = _notificationsGranted.asStateFlow()

    private val _freeBytes = MutableStateFlow(0L)
    val freeBytes: StateFlow<Long> = _freeBytes.asStateFlow()

    private val _setupDone = MutableStateFlow(prefs.getBoolean("setup_done", false))
    val setupDone: StateFlow<Boolean> = _setupDone.asStateFlow()

    // ---------------------------------------------------------------
    // Loja
    // ---------------------------------------------------------------
    private val storeRepo = StoreRepository(application)
    private val _catalog = MutableStateFlow<StoreCatalog?>(null)
    val catalog: StateFlow<StoreCatalog?> = _catalog.asStateFlow()
    private val _catalogLoading = MutableStateFlow(false)
    val catalogLoading: StateFlow<Boolean> = _catalogLoading.asStateFlow()
    val storeDownloads: StateFlow<Map<String, DownloadState>> = StoreDownloads.state

    init {
        refresh()
        ShizukuManager.refresh()
        loadCatalog()
    }

    fun loadCatalog(force: Boolean = false) {
        if (_catalogLoading.value) return
        viewModelScope.launch {
            _catalogLoading.value = true
            _catalog.value = storeRepo.load(force)
            _catalogLoading.value = false
        }
    }

    fun downloadGame(game: StoreGame) = StoreDownloads.download(game)

    fun cancelDownload(gameId: String) = StoreDownloads.cancel(gameId)

    fun deleteDownload(game: StoreGame) = StoreDownloads.delete(game)

    fun installGame(game: StoreGame) {
        val path = StoreDownloads.stateOf(game.id).filePath ?: return
        StoreInstaller.install(app, File(path))
    }

    fun canInstallApk(): Boolean = StoreInstaller.canInstall(app)

    fun requestUnknownSources() = StoreInstaller.requestUnknownSources(app)

    fun isInstalled(packageName: String?): Boolean = StoreInstaller.isInstalled(app, packageName)

    fun launchGame(game: StoreGame) {
        val pkg = game.packageName ?: return
        val intent = app.packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }
    }

    fun refresh() {
        _targetInstalled.value = runCatching {
            app.packageManager.getPackageInfo(config.targetPackage, 0)
            true
        }.getOrDefault(false)
        _notificationsGranted.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        viewModelScope.launch(Dispatchers.IO) {
            val shizukuReady = ShizukuManager.status.value.ready
            val service = if (shizukuReady && !ShizukuManager.status.value.legacy) {
                ShizukuManager.awaitService(5_000)
            } else {
                null
            }
            val free = if (service != null) {
                runCatching { service.freeBytes(config.targetDir) }.getOrDefault(-1L)
            } else {
                runCatching { File(app.filesDir.absolutePath).usableSpace }.getOrDefault(-1L)
            }
            _freeBytes.value = free
        }
        ShizukuManager.refresh()
    }

    fun dismissSetup() {
        _setupDone.value = true
        prefs.edit().putBoolean("setup_done", true).apply()
    }

    fun openSetup() {
        _setupDone.value = false
    }

    // ---------------------------------------------------------------
    // Ações do motor
    // ---------------------------------------------------------------
    fun start() {
        DownloadEngine.start()
    }

    fun pause() = DownloadEngine.pause()

    fun cancel() = DownloadEngine.cancel()

    fun retry(index: Int) = DownloadEngine.retry(index)

    fun restartPart(index: Int) = DownloadEngine.restartPart(index)

    fun clearNotice() = DownloadEngine.clearNotice()

    fun setThreads(value: Int) = DownloadEngine.updateSettings { it.copy(threads = value) }

    fun setWifiOnly(value: Boolean) = DownloadEngine.updateSettings { it.copy(wifiOnly = value) }

    fun setTheme(value: String) = DownloadEngine.updateSettings { it.copy(theme = value) }

    fun setSimulation(value: Boolean) {
        if (!BuildConfig.DEBUG) return
        DownloadEngine.updateSettings { it.copy(simulation = value) }
    }

    // ---------------------------------------------------------------
    // Shizuku / notificações
    // ---------------------------------------------------------------
    fun requestShizukuPermission() {
        ShizukuManager.refresh()
        ShizukuManager.requestPermission()
    }

    fun connectShizuku() {
        ShizukuManager.refresh()
        ShizukuManager.connect()
    }

    fun openShizuku() = ShizukuManager.openShizuku(app)

    fun openTargetApp() {
        val market = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            Uri.parse("market://details?id=${config.targetPackage}"),
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(market) }.onFailure {
            runCatching {
                app.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(BuildConfig.TARGET_APP_URL))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    fun notifications() {
        refresh()
    }

    fun exportLog(): File? = AppLog.export(app)

    fun shareLogIntent(file: File): android.content.Intent {
        val uri: Uri = androidx.core.content.FileProvider.getUriForFile(
            app,
            "${app.packageName}.files",
            file,
        )
        return android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, app.getString(com.fuzzydl.app.R.string.export_log_subject))
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object {
        fun factory(application: Application) = viewModelFactory {
            initializer { DashboardViewModel(application) }
        }
    }
}
