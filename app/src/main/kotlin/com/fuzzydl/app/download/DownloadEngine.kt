package com.fuzzydl.app.download

import android.content.Context
import android.content.SharedPreferences
import com.fuzzydl.app.BuildConfig
import com.fuzzydl.app.core.NativeCallback
import com.fuzzydl.app.core.NativeDownloader
import com.fuzzydl.app.data.AppLog
import com.fuzzydl.app.data.EngineSnapshot
import com.fuzzydl.app.data.PartSpec
import com.fuzzydl.app.data.PartStage
import com.fuzzydl.app.data.PartState
import com.fuzzydl.app.data.PartsConfig
import com.fuzzydl.app.data.PartsRepository
import com.fuzzydl.app.data.SettingsData
import com.fuzzydl.app.data.StateStore
import com.fuzzydl.app.shizuku.ExtractOutcome
import com.fuzzydl.app.shizuku.IExtractCallback
import com.fuzzydl.app.shizuku.ShizukuManager
import com.fuzzydl.app.shizuku.ZipExtractor
import com.fuzzydl.app.shizuku.ZipInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File

/** Falha recuperável (ou definitiva, após as tentativas) de uma parte. */
class EngineException(message: String) : Exception(message)

/** Sinaliza pausa/cancelamento do pipeline. */
class CancelledException : Exception("cancelado")

private class StopPipeline(message: String) : Exception(message)

private data class ResolvedInfo(
    val url: String,
    val size: Long,
    val name: String,
    val mirror: String,
)

private data class NativeEvent(
    val type: Int,
    val done: Long,
    val total: Long,
    val speedBps: Long,
    val etaSecs: Long,
    val message: String?,
)

/**
 * Motor único do app.
 *
 * Pipeline por parte, estritamente sequencial:
 * PENDING → RESOLVING → DOWNLOADING → VERIFYING → EXTRACTING → DONE
 *
 * O ZIP só é apagado depois de a extração terminar e a contagem/tamanho
 * conferirem com o diretório central. Em caso de pausa/cancelamento, o `.part`
 * e o sidecar são preservados (pausa) ou removidos (cancelamento).
 */
object DownloadEngine {
    private const val RESERVE_BYTES = 256L * 1024 * 1024
    private const val PREFS = "fuzzy_settings"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var context: Context
    private lateinit var config: PartsConfig
    private lateinit var parts: List<PartSpec>
    private lateinit var staging: File
    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(SettingsData())
    val settings: StateFlow<SettingsData> = _settings.asStateFlow()

    private var pipeline: Job? = null
    private var persistence: Job? = null

    @Volatile private var pauseRequested = false
    @Volatile private var cancelRequested = false
    @Volatile private var currentHandle = 0L

    fun init(ctx: Context) {
        context = ctx.applicationContext
        config = PartsRepository(context).config
        parts = config.parts
        staging = (context.getExternalFilesDir("staging") ?: File(context.filesDir, "staging")).apply {
            runCatching { mkdirs() }
        }
        loadSettings()
        restore()
    }

    // ------------------------------------------------------------------
    // Estado persistido
    // ------------------------------------------------------------------
    private fun loadSettings() {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _settings.value = SettingsData(
            threads = p.getInt("threads", 4).coerceIn(1, 8),
            wifiOnly = p.getBoolean("wifiOnly", true),
            theme = p.getString("theme", "dark") ?: "dark",
            simulation = p.getBoolean("simulation", false) && BuildConfig.DEBUG,
        )
    }

    private fun restore() {
        val saved = StateStore.load()?.parts?.associateBy { it.index } ?: emptyMap()
        val restored = parts.map { spec ->
            val p = saved[spec.index]
            PartUiState(
                index = spec.index,
                stage = when (p?.stage) {
                    PartStage.RESOLVING, PartStage.DOWNLOADING, PartStage.VERIFYING,
                    PartStage.EXTRACTING -> PartStage.PAUSED
                    null -> PartStage.PENDING
                    else -> p.stage
                },
                bytesDone = p?.bytesDone ?: 0,
                bytesTotal = p?.bytesTotal ?: 0,
                zipBytes = p?.zipBytes ?: 0,
                filesExtracted = p?.filesExtracted ?: 0,
                mirror = p?.mirror,
                error = p?.error,
            )
        }
        _state.value = EngineState(
            parts = restored,
            finished = restored.isNotEmpty() && restored.all { it.stage == PartStage.DONE },
        )
    }

    private fun persist() {
        runCatching {
            val snapshot = EngineSnapshot(
                parts = _state.value.parts.map {
                    PartState(
                        index = it.index,
                        stage = it.stage,
                        bytesDone = it.bytesDone,
                        bytesTotal = it.bytesTotal,
                        zipBytes = it.zipBytes,
                        error = it.error,
                        mirror = it.mirror,
                        filesExtracted = it.filesExtracted,
                    )
                },
                settings = _settings.value,
            )
            StateStore.save(snapshot)
        }
    }

    private fun startPersistence() {
        persistence?.cancel()
        persistence = scope.launch {
            while (isActive) {
                delay(5_000)
                persist()
            }
        }
    }

    private fun stopPersistence() {
        persistence?.cancel()
        persistence = null
    }

    // ------------------------------------------------------------------
    // Ações públicas
    // ------------------------------------------------------------------
    fun start() {
        if (pipeline?.isActive == true) return
        val shizuku = ShizukuManager.status.value
        if (!_settings.value.simulation && !shizuku.legacy && !shizuku.ready) {
            _state.update { it.copy(notice = EngineNotice.SHIZUKU_REQUIRED) }
            return
        }
        if (!_settings.value.simulation && ShizukuManager.serviceOrNull() == null) {
            ShizukuManager.connect()
        }
        pauseRequested = false
        cancelRequested = false
        _state.update {
            it.copy(
                running = true,
                paused = false,
                finished = false,
                notice = null,
                lastError = null,
            )
        }
        pipeline = scope.launch { runPipeline() }
    }

    fun pause() {
        if (pipeline?.isActive != true) return
        pauseRequested = true
        cancelCurrentDownload()
    }

    fun cancel() {
        cancelRequested = true
        pauseRequested = false
        cancelCurrentDownload()
        if (pipeline?.isActive != true) {
            // Cancelar estando pausado: o pipeline já terminou, então limpamos
            // aqui o parcial da parte pendente e voltamos para PENDING.
            val idx = _state.value.currentIndex
                ?: _state.value.parts.firstOrNull { it.stage != PartStage.DONE }?.index
            if (idx != null) {
                deletePartials(idx)
                updatePart(idx) {
                    it.copy(
                        stage = PartStage.PENDING,
                        error = null,
                        bytesDone = 0,
                        bytesTotal = 0,
                        zipBytes = 0,
                        speedBps = 0,
                        etaSecs = 0,
                        extractDone = 0,
                        extractTotal = 0,
                    )
                }
            }
            _state.update {
                it.copy(
                    running = false,
                    paused = false,
                    finished = false,
                    currentIndex = null,
                    lastError = null,
                    notice = null,
                )
            }
            persist()
        }
    }

    /** Tenta de novo a parte indicada (mantém o progresso baixado). */
    fun retry(index: Int) {
        if (pipeline?.isActive == true) return
        updatePart(index) {
            it.copy(
                stage = PartStage.PENDING,
                error = null,
                speedBps = 0,
                etaSecs = 0,
                extractDone = 0,
                extractTotal = 0,
            )
        }
        start()
    }

    /** Refaz a parte do zero: apaga ZIP/parcial e baixa novamente. */
    fun restartPart(index: Int) {
        if (pipeline?.isActive == true) return
        deletePartials(index)
        updatePart(index) {
            it.copy(
                stage = PartStage.PENDING,
                error = null,
                bytesDone = 0,
                bytesTotal = 0,
                zipBytes = 0,
                filesExtracted = 0,
                speedBps = 0,
                etaSecs = 0,
                extractDone = 0,
                extractTotal = 0,
            )
        }
        persist()
        start()
    }

    fun updateSettings(transform: (SettingsData) -> SettingsData) {
        val next = transform(_settings.value).let {
            it.copy(
                threads = it.threads.coerceIn(1, 8),
                simulation = it.simulation && BuildConfig.DEBUG,
            )
        }
        _settings.value = next
        prefs?.edit()
            ?.putInt("threads", next.threads)
            ?.putBoolean("wifiOnly", next.wifiOnly)
            ?.putString("theme", next.theme)
            ?.putBoolean("simulation", next.simulation)
            ?.apply()
        persist()
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun stagingDir(): File = staging

    fun destinationDir(): File = destination()

    fun partState(index: Int): PartUiState = _state.value.parts.firstOrNull { it.index == index }
        ?: PartUiState(index)

    // ------------------------------------------------------------------
    // Pipeline
    // ------------------------------------------------------------------
    private suspend fun runPipeline() {
        try {
            startPersistence()
            for (spec in parts) {
                if (cancelRequested) throw CancelledException()
                if (partState(spec.index).stage == PartStage.DONE) continue
                awaitWifi()
                processPart(spec)
            }
            val allDone = _state.value.parts.all { it.stage == PartStage.DONE }
            if (allDone && !cancelRequested) {
                cleanupStaging()
                persist()
                _state.update {
                    it.copy(running = false, paused = false, finished = true, currentIndex = null)
                }
                AppLog.d("Engine", "todas as 9 partes concluídas")
            }
        } catch (c: CancelledException) {
            val cancelled = cancelRequested
            _state.update {
                it.copy(
                    running = false,
                    paused = !cancelled,
                    currentIndex = if (cancelled) null else it.currentIndex,
                    notice = null,
                )
            }
            AppLog.d("Engine", if (cancelled) "cancelado pelo usuário" else "pausado")
        } catch (s: StopPipeline) {
            _state.update {
                it.copy(running = false, paused = true, lastError = s.message, currentIndex = null)
            }
            AppLog.w("Engine", "pipeline interrompido: ${s.message}")
        } catch (t: Exception) {
            _state.update {
                it.copy(
                    running = false,
                    paused = true,
                    currentIndex = null,
                    lastError = t.message ?: "erro inesperado",
                )
            }
            AppLog.e("Engine", "pipeline falhou", t)
        } finally {
            stopPersistence()
            _state.update { it.copy(running = false) }
            persist()
        }
    }

    private suspend fun awaitWifi() {
        if (!_settings.value.wifiOnly) return
        if (NetworkMonitor.isWifi(context)) return
        _state.update { it.copy(notice = EngineNotice.NOT_WIFI) }
        while (!NetworkMonitor.isWifi(context)) {
            if (cancelRequested || pauseRequested) throw CancelledException()
            delay(1_000)
        }
        _state.update { if (it.notice == EngineNotice.NOT_WIFI) it.copy(notice = null) else it }
    }

    private suspend fun processPart(spec: PartSpec) {
        val idx = spec.index
        _state.update { it.copy(currentIndex = idx, notice = null) }
        updatePart(idx) {
            it.copy(stage = PartStage.RESOLVING, error = null, speedBps = 0, etaSecs = 0)
        }
        var attempt = 0
        while (true) {
            if (cancelRequested || pauseRequested) throw CancelledException()
            try {
                val resolved = resolvePart(spec)
                AppLog.d(
                    "Engine",
                    "parte $idx: ${resolved.name} ${resolved.size} bytes (${resolved.mirror})",
                )
                downloadAndExtract(spec, resolved)
                return
            } catch (c: CancelledException) {
                handleInterruption(idx)
                throw c
            } catch (c: CancellationException) {
                handleInterruption(idx)
                throw CancelledException()
            } catch (e: Exception) {
                attempt++
                if (attempt >= 2) {
                    val msg = e.message ?: "falha na parte $idx"
                    updatePart(idx) {
                        it.copy(stage = PartStage.FAILED, error = msg, speedBps = 0, etaSecs = 0)
                    }
                    persist()
                    throw StopPipeline(msg)
                }
                AppLog.w("Engine", "parte $idx falhou (tentativa $attempt): ${e.message}")
                updatePart(idx) { it.copy(stage = PartStage.RESOLVING, error = e.message) }
                delay(1_500)
            }
        }
    }

    private fun handleInterruption(idx: Int) {
        if (cancelRequested) {
            deletePartials(idx)
            updatePart(idx) {
                it.copy(
                    stage = PartStage.PENDING,
                    bytesDone = 0,
                    bytesTotal = 0,
                    zipBytes = 0,
                    speedBps = 0,
                    etaSecs = 0,
                    extractDone = 0,
                    extractTotal = 0,
                    error = null,
                )
            }
        } else {
            updatePart(idx) { it.copy(stage = PartStage.PAUSED, speedBps = 0, etaSecs = 0) }
        }
        persist()
    }

    private suspend fun resolvePart(spec: PartSpec): ResolvedInfo = withContext(Dispatchers.IO) {
        if (!NativeDownloader.loaded) {
            throw EngineException("motor nativo indisponível (${NativeDownloader.loadError})")
        }
        val raw = NativeDownloader.resolve(spec.url, null)
        val obj = JSONObject(raw)
        if (!obj.optBoolean("ok")) {
            throw EngineException(obj.optString("error", "não foi possível resolver a parte ${spec.index}"))
        }
        val size = obj.optLong("size", 0)
        if (size <= 0) throw EngineException("tamanho inválido para a parte ${spec.index}")
        ResolvedInfo(
            url = obj.getString("url"),
            size = size,
            name = obj.optString("name", "part${spec.index}.zip"),
            mirror = obj.optString("mirror", "auto"),
        )
    }

    private suspend fun downloadAndExtract(spec: PartSpec, resolved: ResolvedInfo) {
        val idx = spec.index
        val zip = File(staging, "part%02d.zip".format(idx))
        val partial = File(zip.path + ".part")
        val sidecar = File(zip.path + ".part.json")
        val extractor = ZipExtractor()

        // Um ZIP completo pode ter sobrado de uma sessão interrompida em
        // VERIFYING/EXTRACTING (o `.part` já tinha sido renomeado). Reaproveitar
        // evita rebaixar GiBs que já estão íntegros no staging.
        if (reusableZip(zip, partial, resolved.size, extractor)) {
            AppLog.d("Engine", "parte $idx: reaproveitando ZIP já baixado (${resolved.size} bytes)")
            updatePart(idx) {
                it.copy(
                    stage = PartStage.VERIFYING,
                    bytesTotal = resolved.size,
                    bytesDone = resolved.size,
                    speedBps = 0,
                    etaSecs = 0,
                    fileName = resolved.name,
                    mirror = resolved.mirror,
                    zipBytes = resolved.size,
                    error = null,
                    extractDone = 0,
                    extractTotal = 0,
                )
            }
            verifyAndExtract(idx, resolved, zip, sidecar, extractor, reused = true)
            return
        }
        if (partial.isFile.not() && zip.isFile) {
            AppLog.w("Engine", "parte $idx: ZIP presente porém inválido, baixando de novo")
            zip.delete()
            sidecar.delete()
        }

        // ---------------- download ----------------
        val already = if (partial.isFile) partial.length() else 0L
        val stagingFree = freeBytes(staging)
        val needed = (resolved.size - already).coerceAtLeast(0) + RESERVE_BYTES
        if (stagingFree in 1 until needed) {
            throw EngineException("espaço insuficiente para baixar a parte $idx")
        }

        updatePart(idx) {
            it.copy(
                stage = PartStage.DOWNLOADING,
                bytesTotal = resolved.size,
                bytesDone = already,
                fileName = resolved.name,
                mirror = resolved.mirror,
                zipBytes = resolved.size,
                error = null,
                extractDone = 0,
                extractTotal = 0,
            )
        }
        downloadPart(idx, resolved, zip)
        verifyAndExtract(idx, resolved, zip, sidecar, extractor, reused = false)
    }

    /**
     * Valida o ZIP baixado, extrai e só então apaga ZIP + sidecar.
     *
     * A ordem importa: `DONE` é persistido **antes** de apagar o ZIP. Se o
     * processo morrer logo depois, a parte continua registrada como concluída e
     * o ZIP sobra para a limpeza global, em vez de o app rebaixar tudo de novo.
     */
    private suspend fun verifyAndExtract(
        idx: Int,
        resolved: ResolvedInfo,
        zip: File,
        sidecar: File,
        extractor: ZipExtractor,
        reused: Boolean,
    ) {
        var extractionStarted = false
        try {
            // ---------------- validação ----------------
            updatePart(idx) { it.copy(stage = PartStage.VERIFYING, bytesDone = resolved.size, speedBps = 0, etaSecs = 0) }
            if (!zip.isFile) throw EngineException("ZIP não encontrado após o download")
            if (zip.length() != resolved.size) {
                throw EngineException("tamanho do ZIP divergente (${zip.length()} de ${resolved.size})")
            }
            if (!extractor.isZip(zip)) throw EngineException("o arquivo baixado não é um ZIP válido")
            val info: ZipInfo = runCatching { extractor.inspect(zip) }.getOrElse {
                throw EngineException("ZIP inválido: ${it.message}")
            }
            if (info.files <= 0) throw EngineException("ZIP sem arquivos")

            val targetFree = targetFreeBytes()
            val targetNeeded = info.bytes + RESERVE_BYTES
            if (targetFree in 1 until targetNeeded) {
                throw EngineException("espaço insuficiente no destino para a parte $idx")
            }

            // ---------------- extração ----------------
            updatePart(idx) {
                it.copy(
                    stage = PartStage.EXTRACTING,
                    filesExtracted = 0,
                    extractDone = 0,
                    extractTotal = info.bytes,
                )
            }
            extractionStarted = true
            val outcome = extractArchive(idx, zip, info)
            if (outcome.files != info.files) {
                throw EngineException("contagem extraída divergente (${outcome.files} de ${info.files})")
            }
            if (outcome.bytes != info.bytes) {
                throw EngineException("tamanho extraído divergente (${outcome.bytes} de ${info.bytes})")
            }

            updatePart(idx) {
                it.copy(
                    stage = PartStage.DONE,
                    bytesDone = resolved.size,
                    bytesTotal = resolved.size,
                    zipBytes = 0,
                    filesExtracted = outcome.files,
                    speedBps = 0,
                    etaSecs = 0,
                    error = null,
                )
            }
            persist()

            // Só apaga o ZIP depois de a extração conferir e o DONE persistir.
            if (!zip.delete()) AppLog.w("Engine", "não foi possível apagar ${zip.name}")
            sidecar.delete()
            AppLog.d("Engine", "parte $idx concluída (${outcome.files} arquivos)")
        } catch (t: Throwable) {
            // Cancelamento/pausa preserva o ZIP para a retomada.
            val interrupted = t is CancellationException || t is CancelledException
            // Um ZIP reaproveitado que falhou na extração é descartado mesmo em
            // falha "ambiental": como não custou banda nesta sessão, é melhor
            // baixar do zero do que ficar reusando lixo a cada Retry.
            if (!interrupted && (isZipIntegrityFailure(t) || (reused && extractionStarted))) {
                AppLog.w("Engine", "parte $idx: descartando ZIP após falha (${t.message})")
                zip.delete()
                sidecar.delete()
            }
            throw t
        }
    }

    private suspend fun downloadPart(idx: Int, resolved: ResolvedInfo, zip: File) {
        zip.parentFile?.mkdirs()
        val events = Channel<NativeEvent>(Channel.UNLIMITED)
        val callback = object : NativeCallback {
            override fun onEvent(
                type: Int,
                done: Long,
                total: Long,
                speedBps: Long,
                etaSecs: Long,
                message: String?,
            ) {
                events.trySend(NativeEvent(type, done, total, speedBps, etaSecs, message))
            }
        }
        val threads = _settings.value.threads.coerceIn(1, 8)
        val handle = NativeDownloader.start(resolved.url, zip.path, resolved.size, threads, callback)
        if (handle <= 0L) throw EngineException("não foi possível iniciar o download")
        currentHandle = handle
        try {
            while (true) {
                if (pauseRequested || cancelRequested) {
                    runCatching { NativeDownloader.cancel(handle) }
                }
                val event = withTimeoutOrNull(750) { events.receive() }
                if (event == null) {
                    if (pauseRequested || cancelRequested) throw CancelledException()
                    continue
                }
                when (event.type) {
                    0 -> updatePart(idx) {
                        it.copy(
                            bytesDone = event.done,
                            bytesTotal = if (event.total > 0) event.total else it.bytesTotal,
                            speedBps = event.speedBps,
                            etaSecs = event.etaSecs,
                        )
                    }
                    1 -> return
                    2 -> throw EngineException(event.message ?: "falha no download")
                    3 -> throw CancelledException()
                }
            }
        } finally {
            currentHandle = 0
            runCatching { NativeDownloader.cancel(handle) }
        }
    }

    private suspend fun extractArchive(idx: Int, zip: File, info: ZipInfo): ExtractOutcome {
        val dest = destination()
        val local = _settings.value.simulation || ShizukuManager.status.value.legacy
        if (local) {
            dest.mkdirs()
            return withContext(Dispatchers.IO) {
                ZipExtractor { done, total, files ->
                    updatePart(idx) {
                        it.copy(extractDone = done, extractTotal = total, filesExtracted = files)
                    }
                }.extract(zip, dest)
            }
        }

        val service = ShizukuManager.awaitService(30_000)
            ?: throw EngineException("Shizuku indisponível para gravar em Android/data")
        val callback = object : IExtractCallback.Stub() {
            override fun onProgress(doneBytes: Long, totalBytes: Long, files: Int) {
                updatePart(idx) {
                    it.copy(extractDone = doneBytes, extractTotal = totalBytes, filesExtracted = files)
                }
            }
        }
        val raw = withContext(Dispatchers.IO) { service.extractZip(zip.path, dest.path, callback) }
        val obj = JSONObject(raw)
        if (!obj.optBoolean("ok")) {
            throw EngineException(obj.optString("error", "falha ao extrair a parte $idx"))
        }
        return ExtractOutcome(obj.optInt("files"), obj.optLong("bytes"))
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------
    private fun updatePart(index: Int, block: (PartUiState) -> PartUiState) {
        _state.update { st ->
            st.copy(parts = st.parts.map { if (it.index == index) block(it) else it })
        }
    }

    private fun cancelCurrentDownload() {
        val handle = currentHandle
        if (handle > 0L) runCatching { NativeDownloader.cancel(handle) }
    }

    private fun destination(): File = if (_settings.value.simulation) {
        File(context.getExternalFilesDir(null), "simulation/game")
    } else {
        File(config.targetDir)
    }

    private fun deletePartials(index: Int) {
        val zip = File(staging, "part%02d.zip".format(index))
        File(zip.path + ".part").delete()
        File(zip.path + ".part.json").delete()
        zip.delete()
    }

    private fun cleanupStaging() {
        runCatching {
            staging.listFiles()?.forEach { f ->
                if (f.isFile && (f.name.endsWith(".zip") || f.name.contains(".zip."))) f.delete()
            }
        }
    }

    private fun nearestExisting(file: File): File {
        var cur: File? = file
        while (cur != null && !cur.exists()) cur = cur.parentFile
        return cur ?: File("/")
    }

    private fun freeBytes(file: File): Long = runCatching { nearestExisting(file).usableSpace }.getOrDefault(-1L)

    private fun targetFreeBytes(): Long {
        if (_settings.value.simulation || ShizukuManager.status.value.legacy) {
            return freeBytes(destination())
        }
        val service = ShizukuManager.serviceOrNull() ?: return -1L
        return runCatching { service.freeBytes(config.targetDir) }.getOrDefault(-1L)
    }
}

/**
 * Retorna `true` se existe um ZIP completo e íntegro em staging que pode ser
 * extraído sem passar pelo motor nativo — útil quando o app morreu em
 * `VERIFYING`/`EXTRACTING` e o `.part` já tinha sido renomeado. Exige a
 * ausência do `.part` (download em andamento) e confere tamanho, assinatura e
 * diretório central. Serve de base para os testes JVM.
 */
internal fun reusableZip(
    zip: File,
    partial: File,
    total: Long,
    extractor: ZipExtractor = ZipExtractor(),
): Boolean {
    if (partial.isFile || !zip.isFile || zip.length() != total) return false
    return runCatching { extractor.isZip(zip) && extractor.inspect(zip).files > 0 }.getOrDefault(false)
}

/** Heurística que separa ZIP corrompido (descartar) de falha ambiental (preservar). */
internal fun isZipIntegrityFailure(t: Throwable): Boolean {
    var cur: Throwable? = t
    while (cur != null) {
        if (cur is java.util.zip.ZipException || cur is java.util.zip.DataFormatException) return true
        val msg = (cur.message ?: "").lowercase()
        if (ZIP_INTEGRITY_MARKERS.any { msg.contains(it) }) return true
        cur = cur.cause
    }
    return false
}

private val ZIP_INTEGRITY_MARKERS = listOf(
    // Nossas próprias mensagens (engine, ZipExtractor e FileService).
    "não é um zip", "nao e um zip", "arquivo não é zip", "arquivo nao e zip",
    "zip inválido", "zip invalido", "zip sem arquivos", "zip sem conteúdo", "zip sem conteudo",
    "zip corrompido", "tamanho do zip divergente",
    "crc", "zip slip", "traversal", "caminho absoluto",
    "separador inválido", "separador invalido", "nome inválido", "nome invalido",
    "divergente",
    // Mensagens em inglês do java.util.zip (defesa extra além do tipo).
    "invalid distance", "invalid stored block", "invalid block type",
    "invalid literal", "invalid code lengths", "invalid bit length",
    "unknown compression method", "invalid loc header", "invalid cen header",
    "invalid header", "unexpected end of zlib", "incorrect header check",
    "invalid entry size", "invalid entry compressed size", "end header",
)
