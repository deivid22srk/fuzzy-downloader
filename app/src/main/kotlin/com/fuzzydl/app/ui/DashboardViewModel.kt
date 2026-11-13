package com.fuzzydl.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fuzzydl.app.data.AppSettings
import com.fuzzydl.app.data.SettingsStore
import com.fuzzydl.app.download.ApkInstaller
import com.fuzzydl.app.download.DlItem
import com.fuzzydl.app.download.DownloadQueue
import com.fuzzydl.app.store.PortCatalog
import com.fuzzydl.app.store.PortDbRepository
import com.fuzzydl.app.store.PortGame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application = application
    private val repo = PortDbRepository(app)

    private val _settings = MutableStateFlow(SettingsStore.load(app))
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _catalog = MutableStateFlow<PortCatalog?>(null)
    val catalog: StateFlow<PortCatalog?> = _catalog.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val downloads: StateFlow<Map<String, DlItem>> = DownloadQueue.state

    init {
        DownloadQueue.configure(_settings.value.threads, _settings.value.wifiOnly)
        refresh(force = false)
    }

    fun refresh(force: Boolean) {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching {
                DownloadQueue.configure(_settings.value.threads, _settings.value.wifiOnly)
                repo.load(_settings.value.dataSourceUrl, force)
            }
                .onSuccess { _catalog.value = it }
                .onFailure { _error.value = it.message ?: "falha ao carregar o catálogo" }
            _loading.value = false
        }
    }

    fun imageUrl(path: String?): String? = repo.imageUrl(_settings.value.dataSourceUrl, path)

    // ------------------------------------------------------------------
    // Ajustes
    // ------------------------------------------------------------------
    private fun persist(settings: AppSettings) {
        _settings.value = settings
        SettingsStore.save(app, settings)
        DownloadQueue.configure(settings.threads, settings.wifiOnly)
    }

    fun setThreads(value: Int) = persist(_settings.value.copy(threads = value.coerceIn(1, 8)))

    fun setWifiOnly(value: Boolean) = persist(_settings.value.copy(wifiOnly = value))

    fun setTheme(value: String) = persist(_settings.value.copy(theme = value))

    fun setDataSource(value: String) {
        val normalized = if (value.endsWith("/")) value else "$value/"
        persist(_settings.value.copy(dataSourceUrl = normalized))
        refresh(force = true)
    }

    fun clearCache() {
        repo.clearCache()
        refresh(force = true)
    }

    // ------------------------------------------------------------------
    // Downloads
    // ------------------------------------------------------------------
    fun download(game: PortGame) {
        val url = game.downloadUrl ?: return
        DownloadQueue.enqueue(game.id, game.title, url)
    }

    fun pause(gameId: String) = DownloadQueue.pause(gameId)

    fun resume(gameId: String) = DownloadQueue.resume(gameId)

    fun retry(gameId: String) = DownloadQueue.retry(gameId)

    fun cancel(gameId: String) = DownloadQueue.cancel(gameId)

    fun remove(gameId: String) = DownloadQueue.remove(gameId, deleteFile = false)

    fun deleteDownload(gameId: String) = DownloadQueue.remove(gameId, deleteFile = true)

    fun clearFinished() = DownloadQueue.clearFinished()

    // ------------------------------------------------------------------
    // Sistema
    // ------------------------------------------------------------------
    fun isInstalled(packageName: String?): Boolean = ApkInstaller.isInstalled(app, packageName)

    fun canInstallApk(): Boolean = ApkInstaller.canInstall(app)

    fun requestUnknownSources() = ApkInstaller.requestUnknownSources(app)

    fun installGame(gameId: String) {
        val path = DownloadQueue.item(gameId)?.filePath ?: return
        ApkInstaller.install(app, File(path))
    }

    fun launchGame(packageName: String?) {
        if (packageName.isNullOrBlank()) return
        val intent = app.packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }
    }

    fun openUrl(url: String) {
        runCatching {
            app.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    companion object {
        fun factory(application: Application) = viewModelFactory {
            initializer { DashboardViewModel(application) }
        }
    }
}