package com.fuzzydl.app.data

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Log curto em memória + arquivo com rotação (256 KiB). Nunca registra segredos. */
object AppLog {
    private const val TAG = "FuzzyDl"
    private const val MAX_BYTES = 256 * 1024
    private const val TAIL_LINES = 60

    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private var file: File? = null
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        synchronized(lock) {
            val dir = File(context.filesDir, "logs").apply { mkdirs() }
            val f = File(dir, "app.log")
            if (f.length() > MAX_BYTES) {
                runCatching {
                    val old = File(dir, "app.old.log")
                    old.delete()
                    f.renameTo(old)
                }
            }
            file = f
        }
    }

    fun d(tag: String, msg: String) {
        Log.d(TAG, "[$tag] $msg")
        append("D/$tag", msg)
    }

    fun w(tag: String, msg: String) {
        Log.w(TAG, "[$tag] $msg")
        append("W/$tag", msg)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        Log.e(TAG, "[$tag] $msg", tr)
        append("E/$tag", if (tr == null) msg else "$msg: ${tr.javaClass.simpleName}: ${tr.message}")
    }

    private fun append(level: String, msg: String) {
        val line = "${fmt.format(Date())} $level $msg"
        synchronized(lock) {
            lines.addLast(line)
            while (lines.size > TAIL_LINES) lines.removeFirst()
            runCatching { file?.appendText(line + "\n") }
        }
    }

    fun tail(): String = synchronized(lock) { lines.joinToString("\n") }

    /** Copia o log para uma pasta compartilhável via FileProvider. */
    fun export(context: Context): File? = runCatching {
        val src = file ?: return null
        val dir = File(context.getExternalFilesDir(null), "export").apply { mkdirs() }
        val out = File(dir, "fuzzy-downloader.log")
        out.writeText(src.readText())
        out
    }.getOrNull()
}
