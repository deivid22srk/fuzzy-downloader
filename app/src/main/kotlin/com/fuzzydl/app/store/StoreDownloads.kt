package com.fuzzydl.app.store

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.fuzzydl.app.core.NativeCallback
import com.fuzzydl.app.core.NativeDownloader
import com.fuzzydl.app.data.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

enum class DownloadStatus { NONE, DOWNLOADING, VERIFYING, READY, FAILED }

data class DownloadState(
    val gameId: String,
    val status: DownloadStatus = DownloadStatus.NONE,
    val done: Long = 0,
    val total: Long = 0,
    val speedBps: Long = 0,
    val etaSecs: Long = 0,
    val error: String? = null,
    val filePath: String? = null,
)

/**
 * Baixa APKs do catálogo reaproveitando o motor nativo (Range, retomada, retry).
 * Mantém um estado por jogo e persiste o que já foi baixado.
 */
object StoreDownloads {
    private const val PREFS = "store_downloads"
    private const val THREADS = 4
    private const val RESERVE = 256L * 1024 * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val state: StateFlow<Map<String, DownloadState>> = _state.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val handles = ConcurrentHashMap<String, Long>()

    private lateinit var context: Context

    fun init(ctx: Context) {
        context = ctx.applicationContext
        restore()
        reconcile()
    }

    fun stateOf(gameId: String): DownloadState =
        _state.value[gameId] ?: DownloadState(gameId)

    fun fileFor(game: StoreGame): File {
        val dir = context.getExternalFilesDir("store") ?: File(context.filesDir, "store")
        runCatching { dir.mkdirs() }
        return File(dir, "${game.id}.apk")
    }

    fun isActive(gameId: String): Boolean = jobs[gameId]?.isActive == true

    fun download(game: StoreGame) {
        if (isActive(game.id)) return
        val url = game.downloadUrl
        if (url.isNullOrBlank() || game.sizeBytes <= 0) {
            fail(game, "download indisponível")
            return
        }
        jobs[game.id] = scope.launch {
            try {
                run(game)
            } catch (c: CancellationException) {
                cleanup(game.id)
                update(game.id) { it.copy(status = DownloadStatus.NONE, done = 0, speedBps = 0, etaSecs = 0) }
                throw c
            } catch (t: Throwable) {
                fail(game, t.message ?: "falha no download")
            } finally {
                jobs.remove(game.id)
            }
        }
    }

    fun cancel(gameId: String) {
        handles[gameId]?.let { runCatching { NativeDownloader.cancel(it) } }
        jobs[gameId]?.cancel()
    }

    fun delete(game: StoreGame) {
        val state = stateOf(game.id)
        state.filePath?.let { runCatching { File(it).delete() } }
        runCatching { fileFor(game).delete() }
        runCatching { File(fileFor(game).path + ".part").delete() }
        update(game.id) { it.copy(status = DownloadStatus.NONE, done = 0, filePath = null, error = null) }
        persist()
    }

    private suspend fun run(game: StoreGame) {
        val url = game.downloadUrl ?: error("sem URL")
        if (!NativeDownloader.loaded) error("motor nativo indisponível")
        val dest = fileFor(game)
        dest.parentFile?.mkdirs()
        val total = game.sizeBytes

        val free = dest.parentFile?.usableSpace ?: -1
        if (free in 1 until (total + RESERVE)) error("espaço insuficiente (${total + RESERVE} bytes)")

        update(game.id) {
            it.copy(
                status = DownloadStatus.DOWNLOADING,
                total = total,
                done = 0,
                speedBps = 0,
                etaSecs = 0,
                error = null,
                filePath = dest.path,
            )
        }

        val events = Channel<Evt>(Channel.UNLIMITED)
        val callback = object : NativeCallback {
            override fun onEvent(
                type: Int,
                done: Long,
                total: Long,
                speedBps: Long,
                etaSecs: Long,
                message: String?,
            ) {
                events.trySend(Evt(type, done, total, speedBps, etaSecs, message))
            }
        }

        val handle = NativeDownloader.start(url, dest.path, total, THREADS, callback)
        if (handle <= 0L) error("não foi possível iniciar o download")
        handles[game.id] = handle

        try {
            while (true) {
                coroutineContext.ensureActive()
                val e = withTimeoutOrNull(750) { events.receive() }
                if (e == null) continue
                when (e.type) {
                    0 -> update(game.id) {
                        it.copy(
                            done = e.done,
                            total = if (e.total > 0) e.total else it.total,
                            speedBps = e.speedBps,
                            etaSecs = e.etaSecs,
                        )
                    }
                    1 -> break
                    2 -> error(e.message ?: "falha no download")
                    3 -> throw CancellationException()
                }
            }

            update(game.id) { it.copy(status = DownloadStatus.VERIFYING, speedBps = 0, etaSecs = 0) }
            if (!dest.isFile) error("arquivo não encontrado após o download")
            if (dest.length() != total) error("tamanho divergente (${dest.length()} de $total)")
            game.sha256?.let { expected ->
                val actual = sha256(dest)
                if (!actual.equals(expected, ignoreCase = true)) {
                    dest.delete()
                    error("hash divergente")
                }
            }
            runCatching { File(dest.path + ".part").delete() }
            runCatching { File(dest.path + ".part.json").delete() }
            update(game.id) {
                it.copy(status = DownloadStatus.READY, done = total, speedBps = 0, etaSecs = 0, filePath = dest.path)
            }
            persist()
            AppLog.d("Store", "APK pronto: ${game.id} (${dest.length()} bytes)")
        } finally {
            handles.remove(game.id)
            runCatching { NativeDownloader.cancel(handle) }
        }
    }

    private fun fail(game: StoreGame, message: String) {
        AppLog.w("Store", "falha em ${game.id}: $message")
        update(game.id) { it.copy(status = DownloadStatus.FAILED, speedBps = 0, etaSecs = 0, error = message) }
        persist()
    }

    private fun cleanup(gameId: String) {
        _state.value[gameId]?.filePath?.let { path ->
            runCatching { File(path + ".part").delete() }
            runCatching { File(path + ".part.json").delete() }
        }
    }

    private fun update(gameId: String, block: (DownloadState) -> DownloadState) {
        _state.update { current ->
            val existing = current[gameId] ?: DownloadState(gameId)
            current + (gameId to block(existing))
        }
    }

    // ------------------------------------------------------------------
    // Persistência
    // ------------------------------------------------------------------
    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun persist() {
        runCatching {
            val root = JSONObject()
            _state.value.values.forEach { s ->
                if (s.status == DownloadStatus.READY || s.status == DownloadStatus.FAILED) {
                    root.put(s.gameId, JSONObject().apply {
                        put("status", s.status.name)
                        put("filePath", s.filePath)
                        put("done", s.done)
                        put("total", s.total)
                        s.error?.let { put("error", it) }
                    })
                }
            }
            prefs().edit().putString("json", root.toString()).apply()
        }
    }

    private fun restore() {
        runCatching {
            val text = prefs().getString("json", null) ?: return
            val root = JSONObject(text)
            val map = HashMap<String, DownloadState>()
            root.keys().forEach { id ->
                val o = root.optJSONObject(id) ?: return@forEach
                map[id] = DownloadState(
                    gameId = id,
                    status = runCatching { DownloadStatus.valueOf(o.optString("status")) }
                        .getOrDefault(DownloadStatus.NONE),
                    done = o.optLong("done"),
                    total = o.optLong("total"),
                    filePath = o.optString("filePath").takeIf { it.isNotEmpty() && it != "null" },
                    error = o.optString("error").takeIf { it.isNotEmpty() && it != "null" },
                )
            }
            _state.value = map
        }
    }

    /** Descarta entradas cujo arquivo sumiu. */
    private fun reconcile() {
        _state.update { current ->
            current.mapValues { (_, s) ->
                if (s.status == DownloadStatus.READY && (s.filePath == null || !File(s.filePath).isFile)) {
                    s.copy(status = DownloadStatus.NONE, done = 0, filePath = null)
                } else {
                    s
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private data class Evt(
        val type: Int,
        val done: Long,
        val total: Long,
        val speedBps: Long,
        val etaSecs: Long,
        val message: String?,
    )
}

/** Instalação de APK baixado, com o fluxo de "fontes desconhecidas". */
object StoreInstaller {
    private const val MIME_APK = "application/vnd.android.package-archive"

    fun isInstalled(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun requestUnknownSources(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Abre o instalador do Android para o APK. Requer "fontes desconhecidas" liberado. */
    fun install(context: Context, file: File) {
        if (!file.isFile) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { AppLog.e("Store", "não foi possível abrir o instalador", it) }
    }
}