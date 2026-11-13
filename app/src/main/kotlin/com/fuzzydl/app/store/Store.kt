package com.fuzzydl.app.store

import android.content.Context
import com.fuzzydl.app.data.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Uma entrada do catálogo remoto. */
data class StoreGame(
    val id: String,
    val name: String,
    val tagline: String,
    val description: String,
    val developer: String,
    val license: String,
    val licenseUrl: String?,
    val categories: List<String>,
    val tags: List<String>,
    val requirements: String?,
    val sourceUrl: String,
    val homepageUrl: String?,
    val featured: Boolean,
    val accent: String,
    val packageName: String?,
    val type: String,
    val minAndroid: String?,
    val version: String?,
    val sizeBytes: Long,
    val sha256: String?,
    val downloadUrl: String?,
) {
    val isInternal: Boolean get() = type == "internal"
}

data class StoreCategory(val id: String, val name: String)

data class StoreCatalog(
    val schema: Int,
    val updated: String,
    val categories: List<StoreCategory>,
    val games: List<StoreGame>,
) {
    fun find(id: String): StoreGame? = games.firstOrNull { it.id == id }
}

/** Lê o catálogo do repositório `fuzzy-catalog`, com cache e reserva embutida. */
class StoreRepository(private val context: Context) {

    private val cacheFile = File(context.filesDir, "catalog_cache.json")

    fun cached(): StoreCatalog? = runCatching {
        if (!cacheFile.exists()) null else parse(cacheFile.readText())
    }.getOrNull()

    fun bundled(): StoreCatalog? = runCatching {
        parse(context.assets.open("catalog.json").bufferedReader().use { it.readText() })
    }.onFailure { AppLog.e("Store", "catálogo embutido inválido", it) }.getOrNull()

    /** Busca o remoto; cai para o cache e depois para o catálogo embutido. */
    suspend fun load(force: Boolean = false): StoreCatalog = withContext(Dispatchers.IO) {
        if (!force) cached()?.let { return@withContext it }
        val remoteText = runCatching { fetch() }
            .onSuccess { runCatching { cacheFile.writeText(it) } }
            .onFailure { AppLog.w("Store", "falha ao buscar catálogo: ${it.message}") }
            .getOrNull()
        remoteText?.let { text -> runCatching { parse(text) }.getOrNull() }
            ?: cached()
            ?: bundled()
            ?: StoreCatalog(1, "", emptyList(), emptyList())
    }

    private fun fetch(): String {
        val url = URL(CATALOG_URL)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "FuzzyDownloader/1.1")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(text: String): StoreCatalog {
        val root = JSONObject(text)
        val catArr = root.optJSONArray("categories") ?: JSONArray()
        val categories = ArrayList<StoreCategory>(catArr.length())
        for (i in 0 until catArr.length()) {
            val o = catArr.optJSONObject(i) ?: continue
            categories += StoreCategory(o.optString("id"), o.optString("name"))
        }
        val gamesArr = root.optJSONArray("games") ?: JSONArray()
        val games = ArrayList<StoreGame>(gamesArr.length())
        for (i in 0 until gamesArr.length()) {
            val o = gamesArr.optJSONObject(i) ?: continue
            games += StoreGame(
                id = o.getString("id"),
                name = o.optString("name", o.getString("id")),
                tagline = o.optString("tagline"),
                description = o.optString("description"),
                developer = o.optString("developer"),
                license = o.optString("license"),
                licenseUrl = o.optStringOrNull("licenseUrl"),
                categories = o.optStringList("categories"),
                tags = o.optStringList("tags"),
                requirements = o.optStringOrNull("requirements"),
                sourceUrl = o.optString("sourceUrl"),
                homepageUrl = o.optStringOrNull("homepageUrl"),
                featured = o.optBoolean("featured", false),
                accent = o.optString("accent", "#3FA9FF"),
                packageName = o.optStringOrNull("packageName"),
                type = o.optString("type", "apk"),
                minAndroid = o.optStringOrNull("minAndroid"),
                version = o.optStringOrNull("version"),
                sizeBytes = o.optLong("sizeBytes", 0),
                sha256 = o.optStringOrNull("sha256"),
                downloadUrl = o.optStringOrNull("downloadUrl"),
            )
        }
        return StoreCatalog(
            schema = root.optInt("schema", 1),
            updated = root.optString("updated"),
            categories = categories,
            games = games,
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key)
        return v.takeIf { it.isNotEmpty() && it != "null" }
    }

    private fun JSONObject.optStringList(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val v = arr.optString(i)
            if (v.isNotEmpty()) out += v
        }
        return out
    }

    companion object {
        const val CATALOG_URL =
            "https://raw.githubusercontent.com/deivid22srk/fuzzy-catalog/main/catalog.json"
    }
}