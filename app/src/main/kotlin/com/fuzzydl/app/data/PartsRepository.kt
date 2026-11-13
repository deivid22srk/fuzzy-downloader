package com.fuzzydl.app.data

import android.content.Context
import org.json.JSONObject

/** Lê `assets/parts.json` — a única fonte de IDs/URLs. */
class PartsRepository(private val context: Context) {

    val config: PartsConfig by lazy { load() }

    private fun load(): PartsConfig {
        val text = context.assets.open("parts.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(text)
        val arr = root.getJSONArray("parts")
        val parts = ArrayList<PartSpec>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            parts += PartSpec(
                index = o.getInt("index"),
                id = o.getString("id"),
                url = o.getString("url"),
            )
        }
        require(parts.isNotEmpty()) { "parts.json vazio" }
        require(parts.map { it.index }.sorted() == (1..parts.size).toList()) { "índices inválidos em parts.json" }
        return PartsConfig(
            targetPackage = root.getString("target_package"),
            targetDir = root.getString("target_dir"),
            parts = parts.sortedBy { it.index },
        )
    }
}
