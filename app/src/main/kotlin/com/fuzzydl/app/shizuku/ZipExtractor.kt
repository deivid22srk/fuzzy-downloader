package com.fuzzydl.app.shizuku

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.CRC32
import java.util.zip.ZipFile

data class ZipInfo(
    val entries: Int,
    val files: Int,
    val bytes: Long,
    val singleGameRoot: Boolean,
)

data class ExtractOutcome(val files: Int, val bytes: Long)

/**
 * Extrator puro (sem APIs Android) para poder rodar em testes JVM e dentro do
 * processo shell do Shizuku.
 *
 * - protege contra Zip Slip (.., absolutos, barras invertidas);
 * - suporta ZIP64 (java.util.zip);
 * - valida CRC de cada entrada;
 * - remove o prefixo `game/` apenas quando TODAS as entradas estão sob ele;
 * - usa buffer de 256 KiB e um pool de 3 threads para muitos arquivos pequenos.
 */
class ZipExtractor(
    private val onProgress: (doneBytes: Long, totalBytes: Long, files: Int) -> Unit = { _, _, _ -> },
) {
    private val progressLock = Any()
    private val lastProgressNs = AtomicLong(0)

    fun isZip(file: File): Boolean {
        if (!file.isFile || file.length() < 4) return false
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val b = ByteArray(4)
                raf.readFully(b)
                b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() &&
                    ((b[2].toInt() == 3 && b[3].toInt() == 4) ||
                        (b[2].toInt() == 5 && b[3].toInt() == 6) ||
                        (b[2].toInt() == 7 && b[3].toInt() == 8))
            }
        }.getOrDefault(false)
    }

    fun inspect(zip: File): ZipInfo {
        if (!isZip(zip)) throw IOException("não é um ZIP válido")
        ZipFile(zip).use { zf ->
            val entries = zf.entries().toList()
            if (entries.isEmpty()) throw IOException("ZIP vazio")
            entries.forEach { validateName(it.name) }
            val files = entries.filter { !it.isDirectory }
            val bytes = files.sumOf { if (it.size < 0) 0L else it.size }
            val singleGameRoot = entries.any { it.name.startsWith("game/") } &&
                entries.all { it.name == "game" || it.name == "game/" || it.name.startsWith("game/") }
            return ZipInfo(entries.size, files.size, bytes, singleGameRoot)
        }
    }

    fun extract(zip: File, destDir: File): ExtractOutcome {
        val info = inspect(zip)
        destDir.mkdirs()
        if (!destDir.isDirectory) throw IOException("destino inacessível: ${destDir.path}")
        val base = destDir.canonicalFile
        val basePrefix = base.path.trimEnd(File.separatorChar) + File.separator

        val dirCache = ConcurrentHashMap.newKeySet<String>()
        val filesWritten = AtomicInteger(0)
        val bytesWritten = AtomicLong(0)
        val failure = AtomicReference<Throwable?>(null)
        val totalFiles = info.files

        ZipFile(zip).use { zf ->
            val entries = zf.entries().toList()

            // Cria diretórios primeiro (entradas explícitas).
            entries.filter { it.isDirectory }.forEach { e ->
                val name = if (info.singleGameRoot) e.name.removePrefix("game/") else e.name
                if (name.isNotBlank()) {
                    val d = File(destDir, name)
                    ensureInside(base, basePrefix, d)
                    d.mkdirs()
                    runCatching { d.setExecutable(true, false) }
                }
            }

            val small = ArrayList<Pair<java.util.zip.ZipEntry, File>>()
            val large = ArrayList<Pair<java.util.zip.ZipEntry, File>>()
            for (e in entries) {
                if (e.isDirectory) continue
                val name = if (info.singleGameRoot) e.name.removePrefix("game/") else e.name
                if (name.isBlank()) continue
                val out = File(destDir, name)
                ensureInside(base, basePrefix, out)
                if (e.size in 0..(1024 * 1024)) small += e to out else large += e to out
            }

            fun process(entry: java.util.zip.ZipEntry, out: File) {
                if (failure.get() != null) return
                try {
                    writeEntry(zf, entry, out, base, basePrefix, dirCache, filesWritten, bytesWritten, totalFiles, info.bytes)
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                }
            }

            if (small.size >= 8) {
                val pool = Executors.newFixedThreadPool(3)
                try {
                    small.forEach { (e, out) -> pool.execute { process(e, out) } }
                    large.forEach { (e, out) -> process(e, out) }
                    pool.shutdown()
                    pool.awaitTermination(30, TimeUnit.MINUTES)
                } finally {
                    pool.shutdownNow()
                }
            } else {
                small.forEach { (e, out) -> process(e, out) }
                large.forEach { (e, out) -> process(e, out) }
            }
        }

        failure.get()?.let { throw it }
        if (filesWritten.get() != info.files) {
            throw IOException("contagem de arquivos divergente: ${filesWritten.get()}/${info.files}")
        }
        if (bytesWritten.get() != info.bytes) {
            throw IOException("tamanho extraído divergente: ${bytesWritten.get()}/${info.bytes}")
        }
        emitProgress(bytesWritten.get(), info.bytes, filesWritten.get(), force = true)
        return ExtractOutcome(filesWritten.get(), bytesWritten.get())
    }

    private fun writeEntry(
        zf: ZipFile,
        entry: java.util.zip.ZipEntry,
        out: File,
        base: File,
        basePrefix: String,
        dirCache: MutableSet<String>,
        filesWritten: AtomicInteger,
        bytesWritten: AtomicLong,
        totalFiles: Int,
        totalBytes: Long,
    ) {
        ensureInside(base, basePrefix, out)
        val parent = out.parentFile
        if (parent != null && dirCache.add(parent.path)) {
            parent.mkdirs()
        }
        val buffer = ByteArray(BUFFER)
        val crc = CRC32()
        zf.getInputStream(entry).use { input ->
            FileOutputStream(out).use { output ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    crc.update(buffer, 0, n)
                    val done = bytesWritten.addAndGet(n.toLong())
                    emitProgress(done, totalBytes, filesWritten.get(), force = false)
                }
                output.flush()
            }
        }
        if (entry.crc >= 0 && crc.value != entry.crc) {
            throw IOException("CRC inválido em ${entry.name}")
        }
        runCatching {
            out.setReadable(true, false)
            out.setWritable(true, true)
        }
        val count = filesWritten.incrementAndGet()
        emitProgress(bytesWritten.get(), totalBytes, count, force = count == totalFiles)
    }

    private fun emitProgress(done: Long, total: Long, files: Int, force: Boolean) {
        val now = System.nanoTime()
        val last = lastProgressNs.get()
        if (!force && now - last < 200_000_000L) return
        if (!lastProgressNs.compareAndSet(last, now) && !force) return
        synchronized(progressLock) {
            runCatching { onProgress(done, total, files) }
        }
    }

    private fun ensureInside(base: File, basePrefix: String, out: File) {
        val canonical = out.canonicalPath
        if (canonical != base.path && !canonical.startsWith(basePrefix)) {
            throw IOException("Zip Slip bloqueado: ${out.path}")
        }
    }

    private fun validateName(name: String) {
        if (name.isBlank()) return
        if (name.contains('\u0000')) throw IOException("nome inválido")
        if (name.contains('\\')) throw IOException("separador inválido: $name")
        if (name.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(name)) {
            throw IOException("caminho absoluto no ZIP: $name")
        }
        val parts = name.split('/')
        if (parts.any { it == ".." }) throw IOException("path traversal no ZIP: $name")
    }

    companion object {
        private const val BUFFER = 256 * 1024
    }
}
