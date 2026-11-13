package com.fuzzydl.app.shizuku

import org.json.JSONObject
import java.io.File

/**
 * Roda no processo shell (UID 2000/root) via Shizuku UserService.
 * Sem APIs Android: só java.io, para ser testável na JVM.
 */
class FileService : IFileService.Stub() {

    override fun destroy() {
        // Nada a liberar: o processo é encerrado pelo Shizuku.
    }

    override fun exists(path: String): Boolean = File(path).exists()

    override fun freeBytes(path: String): Long {
        var f: File? = File(path)
        while (f != null && !f.exists()) f = f.parentFile
        return f?.usableSpace ?: 0L
    }

    override fun mkdirs(path: String): Boolean {
        val dir = File(path)
        val ok = dir.isDirectory || dir.mkdirs()
        runCatching {
            dir.setExecutable(true, false)
            dir.setReadable(true, false)
        }
        return ok
    }

    override fun delete(path: String): Boolean {
        val file = File(path)
        val canonical = runCatching { file.canonicalPath }.getOrElse { return false }
        val allowed = canonical.startsWith("/storage/emulated/0/Android/data/") ||
            canonical.startsWith("/data/local/tmp/")
        if (!allowed) return false
        return runCatching { deleteRecursive(file) }.getOrDefault(false)
    }

    private fun deleteRecursive(f: File): Boolean {
        if (f.isDirectory) {
            f.listFiles()?.forEach { child -> deleteRecursive(child) }
        }
        return f.delete()
    }

    override fun extractZip(zipPath: String, destDir: String, cb: IExtractCallback?): String {
        return try {
            val zip = File(zipPath)
            if (!zip.isFile) return error("ZIP não encontrado em $zipPath")
            val extractor = ZipExtractor { done, total, files ->
                runCatching { cb?.onProgress(done, total, files) }
            }
            if (!extractor.isZip(zip)) return error("arquivo não é ZIP (formato não suportado)")
            val info = extractor.inspect(zip)
            if (info.bytes <= 0) return error("ZIP sem conteúdo")

            val dest = File(destDir)
            var existing: File? = dest
            while (existing != null && !existing.exists()) existing = existing.parentFile
            val free = existing?.usableSpace ?: 0L
            val margin = 256L * 1024 * 1024
            if (free in 1 until (info.bytes + margin)) {
                return error("espaço insuficiente no destino: livre=$free necessário=${info.bytes + margin}")
            }
            dest.mkdirs()
            if (!dest.isDirectory) return error("não foi possível criar $destDir")
            runCatching {
                dest.setExecutable(true, false)
                dest.setReadable(true, false)
            }

            val outcome = extractor.extract(zip, dest)
            if (!dest.isDirectory || dest.list() == null) return error("destino não legível após extrair")
            JSONObject().apply {
                put("ok", true)
                put("files", outcome.files)
                put("bytes", outcome.bytes)
                put("single_game_root", info.singleGameRoot)
            }.toString()
        } catch (t: Throwable) {
            error("${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun error(msg: String): String = JSONObject().apply {
        put("ok", false)
        put("error", msg)
    }.toString()
}
