package com.fuzzydl.app.core

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

/** Wrapper fino sobre a cdylib `libfuzzy_dl.so`. */
object NativeDownloader {
    init {
        System.loadLibrary("fuzzy_dl")
    }

    /** Retorna JSON `{ok,url,size,name,mirror}` ou `{ok:false,error}`. */
    external fun resolve(pageUrl: String, mirror: String?): String

    /** Inicia o download e retorna um handle para [cancel]. */
    external fun start(
        url: String,
        dest: String,
        total: Long,
        threads: Int,
        callback: NativeCallback,
    ): Long

    external fun cancel(handle: Long)
}
