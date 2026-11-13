package com.fuzzydl.app.data

import android.content.Context
import java.io.File

/** Estado persistido em JSON pequeno (partes + preferências). */
object StateStore {
    private lateinit var file: File
    private val lock = Any()

    fun init(context: Context) {
        file = File(context.filesDir, "state.json")
    }

    fun load(): EngineSnapshot? = synchronized(lock) {
        runCatching {
            if (!file.exists()) null else EngineSnapshot.fromJson(file.readText())
        }.getOrNull()
    }

    fun save(snapshot: EngineSnapshot) = synchronized(lock) {
        runCatching {
            val tmp = File(file.parentFile, "state.json.tmp")
            tmp.writeText(snapshot.toJson())
            if (!tmp.renameTo(file)) {
                file.writeText(snapshot.toJson())
                tmp.delete()
            }
        }
    }
}
