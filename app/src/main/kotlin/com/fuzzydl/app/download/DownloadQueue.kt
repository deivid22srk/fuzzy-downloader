package com.fuzzydl.app.download

import android.content.Context
import com.fuzzydl.app.core.NativeCallback
import com.fuzzydl.app.core.NativeDownloader
import com.fuzzydl.app.core.RemoteFile
import com.fuzzydl.app.data.AppLog
import com.fuzzydl.app.data.NetworkMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

enum class DlStatus { QUEUED, PROBING, DOWNLOADING, PAUSED, VERIFYING, READY, FAILED }

data class DlItem(
    val id: String,
    val gameId: String,
    val title: String,
    val url: String,
    val fileName: String = "",
    val total: Long = 0,
    val done: Long = 0,
    val speedBps: Long = 0,
    val etaSecs: Long = 0,
    val status: DlStatus = DlStatus.QUEUED,
    val error: String? = null,
    val filePath: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
) {
    val progress: Float
        get() = if (total > 0) (done.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f

    val executableName: String?
        get() = filePath?.let { it.substringAfterLast('/') }
}

/**
 * Fila genérica de downloads: uma por vez, com pausa/retomada (`.part` do Rust),
 * verificação de tamanho e persistência. Serve a qualquer item do catálogo.
 */
object DownloadQueue {
    private const val PREFS = "download_queue"
    private const val RESERVE = 256L * 1024 * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<Map<String, DlItem>>(emptyMap())
    val state: StateFlow<Map<String, DlItem>> = _state.asStateFlow()

    private val handles = ConcurrentHashMap<String, Long>()
    private val itemJobs = ConcurrentHashMap<String, Job>()
    private val paused = ConcurrentHashMap.newKeySet<String>()

    private var worker: Job? = null
    private var threads = 4
    private var wifiOnly = true
    private var context: Context? = null

    fun init(ctx: Context) {
        context = ctx.applicationContext
        restore()
        reconcile()
        startWorker()
    }

    fun configure(threads: Int, wifiOnly: Boolean) {
        this.threads = threads.coerceIn(1, 8)
        this.wifiOnly = wifiOnly
    }

    val active: Boolean
        get() = _state.value.values.any {
            it.status == DlStatus.DOWNLOADING || it.status == DlStatus.PROBING || it.status == DlStatus.VERIFYING
        }

    fun item(id: String): DlItem? = _state.value[id]

    fun enqueue(gameId: String, title: String, url: String, sizeHint: Long = 0) {
        paused.remove(gameId)
        update(gameId) {
            DlItem(
                id = gameId,
                gameId = gameId,
                title = title,
                url = url,
                total = sizeHint.coerceAtLeast(0),
                status = DlStatus.QUEUED,
                addedAt = System.currentTimeMillis(),
            )
        }
        persist()
        context?.let { DownloadService.start(it) }
        startWorker()
    }

    fun pause(id: String) {
        paused.add(id)
        handles[id]?.let { runCatching { NativeDownloader.cancel(it) } }
        val job = itemJobs[id]
        if (job != null) {
            job.cancel()
        } else {
            update(id) { it.copy(status = DlStatus.PAUSED, speedBps = 0, etaSecs = 0) }
            persist()
        }
    }

    fun resume(id: String) {
        paused.remove(id)
        update(id) { it.copy(status = DlStatus.QUEUED, error = null, speedBps = 0, etaSecs = 0) }
        persist()
        context?.let { DownloadService.start(it) }
        startWorker()
    }

    fun retry(id: String) = resume(id)

    fun cancel(id: String) {
        paused.remove(id)
        handles[id]?.let { runCatching { NativeDownloader.cancel(it) } }
        itemJobs[id]?.cancel()
        val item = _state.value[id]
        item?.filePath?.let { path ->
            runCatching { File(path).delete() }
            runCatching { File(path + ".part").delete() }
            runCatching { File(path + ".part.json").delete() }
        }
        _state.update { it - id }
        persist()
    }

    /** Remove um item já concluído ou com erro; opcionalmente apaga o arquivo. */
    fun remove(id: String, deleteFile: Boolean) {
        val item = _state.value[id]
        if (deleteFile) {
            cancel(id)
            return
        }
        itemJobs[id]?.cancel()
        _state.update { it - id }
        persist()
    }

    fun clearFinished() {
        _state.update { current ->
            current.filterValues { it.status != DlStatus.READY && it.status != DlStatus.FAILED }
        }
        persist()
    }

    private fun startWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (isActive) {
                val next = _state.value.values
                    .filter { it.status == DlStatus.QUEUED }
                    .minByOrNull { it.addedAt }
                if (next == null) {
                    delay(250)
                    continue
                }
                val job = launch { process(next.id) }
                itemJobs[next.id] = job
                job.join()
                itemJobs.remove(next.id)
            }
        }
    }

    private suspend fun process(id: String) {
        val ctx = context ?: return
        val item = _state.value[id] ?: return
        if (paused.contains(id)) return
        if (wifiOnly && !NetworkMonitor.isWifi(ctx)) {
            update(id) { it.copy(status = DlStatus.PAUSED, error = "Aguardando Wi-Fi") }
            persist()
            return
        }

        try {
            update(id) { it.copy(status = DlStatus.PROBING, error = null) }
            val remote: RemoteFile = if (item.total > 0 && item.fileName.isNotBlank()) {
                RemoteFile(item.url, item.total, item.fileName)
            } else {
                NativeDownloader.remoteFile(item.url)
                    ?: throw IllegalStateException("não foi possível obter o tamanho do arquivo")
            }
            val name = sanitize(remote.name)
            val dir = downloadsDir(ctx)
            val dest = File(dir, name)
            val free = dir.usableSpace
            if (free in 1 until remote.size + RESERVE) {
                throw IllegalStateException("espaço insuficiente para ${remote.size} bytes")
            }

            update(id) {
                it.copy(
                    status = DlStatus.DOWNLOADING,
                    total = remote.size,
                    fileName = name,
                    filePath = dest.path,
                    done = 0,
                    speedBps = 0,
                    etaSecs = 0,
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

            val handle = NativeDownloader.start(item.url, dest.path, remote.size, threads, callback)
            if (handle <= 0L) throw IllegalStateException("não foi possível iniciar o download")
            handles[id] = handle
            try {
                while (true) {
                    coroutineContext.ensureActive()
                    val e = withTimeoutOrNull(750) { events.receive() } ?: continue
                    when (e.type) {
                        0 -> update(id) {
                            it.copy(
                                done = e.done,
                                total = if (e.total > 0) e.total else it.total,
                                speedBps = e.speedBps,
                                etaSecs = e.etaSecs,
                            )
                        }
                        1 -> break
                        2 -> throw IllegalStateException(e.message ?: "falha no download")
                        3 -> throw CancellationException()
                    }
                }
            } finally {
                handles.remove(id)
                runCatching { NativeDownloader.cancel(handle) }
            }

            update(id) { it.copy(status = DlStatus.VERIFYING, speedBps = 0, etaSecs = 0) }
            if (!dest.isFile) throw IllegalStateException("arquivo não encontrado após o download")
            if (dest.length() != remote.size) {
                throw IllegalStateException("tamanho divergente (${dest.length()} de ${remote.size})")
            }
            runCatching { File(dest.path + ".part").delete() }
            runCatching { File(dest.path + ".part.json").delete() }
            update(id) {
                it.copy(status = DlStatus.READY, done = remote.size, speedBps = 0, etaSecs = 0)
            }
            persist()
            AppLog.d("Queue", "pronto: ${item.title} -> $name")
        } catch (c: CancellationException) {
            if (paused.contains(id)) {
                update(id) { it.copy(status = DlStatus.PAUSED, speedBps = 0, etaSecs = 0) }
                persist()
            }
            throw c
        } catch (t: Throwable) {
            AppLog.w("Queue", "falha em ${item.title}: ${t.message}")
            update(id) {
                it.copy(status = DlStatus.FAILED, error = t.message ?: "falha no download", speedBps = 0, etaSecs = 0)
            }
            persist()
        }
    }

    fun downloadsDir(ctx: Context): File {
        val dir = ctx.getExternalFilesDir("downloads") ?: File(ctx.filesDir, "downloads")
        runCatching { dir.mkdirs() }
        return dir
    }

    private fun sanitize(name: String): String {
        val cleaned = name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._-]"), "_")
        return cleaned.ifBlank { "arquivo.bin" }
    }

    private fun update(id: String, block: (DlItem) -> DlItem) {
        _state.update { current ->
            val existing = current[id] ?: return@update current
            current + (id to block(existing))
        }
    }

    // ------------------------------------------------------------------
    // Persistência
    // ------------------------------------------------------------------
    private fun prefs() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun persist() {
        val p = prefs() ?: return
        runCatching {
            val root = JSONObject()
            _state.value.values.forEach { s ->
                root.put(s.id, JSONObject().apply {
                    put("gameId", s.gameId)
                    put("title", s.title)
                    put("url", s.url)
                    put("fileName", s.fileName)
                    put("total", s.total)
                    put("done", s.done)
                    put("status", s.status.name)
                    put("filePath", s.filePath)
                    put("addedAt", s.addedAt)
                    s.error?.let { put("error", it) }
                })
            }
            p.edit().putString("json", root.toString()).apply()
        }
    }

    private fun restore() {
        val p = prefs() ?: return
        runCatching {
            val text = p.getString("json", null) ?: return
            val root = JSONObject(text)
            val map = HashMap<String, DlItem>()
            root.keys().forEach { id ->
                val o = root.optJSONObject(id) ?: return@forEach
                val status = runCatching { DlStatus.valueOf(o.optString("status")) }
                    .getOrDefault(DlStatus.PAUSED)
                val safe = when (status) {
                    DlStatus.DOWNLOADING, DlStatus.PROBING, DlStatus.VERIFYING, DlStatus.QUEUED -> DlStatus.PAUSED
                    else -> status
                }
                map[id] = DlItem(
                    id = id,
                    gameId = o.optString("gameId", id),
                    title = o.optString("title"),
                    url = o.optString("url"),
                    fileName = o.optString("fileName"),
                    total = o.optLong("total"),
                    done = o.optLong("done"),
                    status = safe,
                    filePath = o.optString("filePath").takeIf { it.isNotEmpty() && it != "null" },
                    error = o.optString("error").takeIf { it.isNotEmpty() && it != "null" },
                    addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                )
            }
            _state.value = map
        }
    }

    private fun reconcile() {
        _state.update { current ->
            current.filterValues { s ->
                s.status != DlStatus.READY || (s.filePath != null && File(s.filePath).isFile)
            }
        }
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