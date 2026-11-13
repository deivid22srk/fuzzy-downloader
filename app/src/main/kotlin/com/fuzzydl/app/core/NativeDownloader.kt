package com.fuzzydl.app.core

import org.json.JSONObject

/**
 * Callback chamado pelo Rust. Não renomear/obfuscar (ver proguard-rules.pro).
 *
 * type: 0 = progresso, 1 = concluído, 2 = erro, 3 = cancelado.
 */
interface NativeCallback {
    fun onEvent(
        type: Int,
        done: Long,
        total: Long,
        speedBps: Long,
        etaSecs: Long,
        message: String?,
    )
}

data class RemoteFile(val url: String, val size: Long, val name: String)

/** Wrapper fino sobre a cdylib `libfuzzy_dl.so` (motor genérico). */
object NativeDownloader {
    val loadError: String? = runCatching { System.loadLibrary("fuzzy_dl") }
        .exceptionOrNull()
        ?.let { "${it.javaClass.simpleName}: ${it.message ?: "falha ao carregar libfuzzy_dl.so"}" }

    val loaded: Boolean get() = loadError == null

    /** Retorna JSON `{ok,url,size,name}` ou `{ok:false,error}`. */
    external fun probe(url: String): String

    /** Inicia o download e retorna um handle para [cancel]. */
    external fun start(
        url: String,
        dest: String,
        total: Long,
        threads: Int,
        callback: NativeCallback,
    ): Long

    external fun cancel(handle: Long)

    /** Sonda a URL e devolve tamanho/nome. Retorna `null` em caso de erro. */
    fun remoteFile(url: String): RemoteFile? {
        if (!loaded) return null
        return runCatching {
            val obj = JSONObject(probe(url))
            if (!obj.optBoolean("ok")) return null
            val size = obj.optLong("size", 0)
            if (size <= 0) return null
            RemoteFile(
                url = obj.optString("url", url),
                size = size,
                name = obj.optString("name", "download.bin"),
            )
        }.getOrNull()
    }
}

/** Extensões que consideramos arquivos baixáveis direto (o resto abre no navegador). */
fun isDirectFile(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val path = url.substringBefore('?').lowercase()
    return listOf(".apk", ".zip", ".7z", ".obb", ".tar", ".gz", ".xz").any { path.endsWith(it) }
}