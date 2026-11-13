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

/** Um jogo/port do port-db. Todos os campos vêm do banco; nada é inventado. */
data class PortGame(
    val id: String,
    val title: String,
    val port: String,
    val original: String,
    val categories: List<String>,
    val tags: List<String>,
    val chipsets: List<String>,
    val performance: String?,
    val apkSize: String?,
    val storage: String?,
    val updated: String?,
    val dateAdded: String?,
    val status: String?,
    val statusLabel: String?,
    val featured: Boolean,
    val downloads: Long,
    val cover: String?,
    val banner: String?,
    val screenshots: List<String>,
    val videoId: String?,
    val shortDescription: String,
    val description: List<String>,
    val requirements: List<RequirementSection>,
    val chipsetRecommended: String?,
    val controls: List<String>,
    val install: List<String>,
    val notes: String?,
    val links: Map<String, String>,
    val credit: Map<String, String>,
    val license: String?,
    val type: String?,
    val packageName: String?,
) {
    val downloadUrl: String? get() = links["download"] ?: links["releases"] ?: links["github"]
    val webUrl: String? get() = links["download"] ?: links["releases"] ?: links["github"] ?: links["project"] ?: links["reference"]
    val isWebOnly: Boolean get() = type == "web" || downloadUrl.isNullOrBlank()
}

data class RequirementSection(val title: String, val items: List<Pair<String, String>>)

data class PortCatalog(
    val sourceName: String,
    val sourceTagline: String,
    val updated: String,
    val games: List<PortGame>,
) {
    fun find(id: String): PortGame? = games.firstOrNull { it.id == id }
    val categories: List<String> get() = games.flatMap { it.categories }.distinct().sorted()
}

/**
 * Lê o `data/games.json` do port-db (uma única requisição, com cache em disco e
 * cópia embutida no APK). O host é configurável em Ajustes.
 */
class PortDbRepository(private val context: Context) {

    private val cacheFile = File(context.filesDir, "portdb_cache.json")

    fun cached(): PortCatalog? = runCatching {
        if (cacheFile.exists()) parse(cacheFile.readText()) else null
    }.getOrNull()

    fun cachedAt(): Long = cacheFile.lastModified()

    fun bundled(): PortCatalog = parse(
        context.assets.open("portdb.json").bufferedReader().use { it.readText() },
    )

    /** Cache fresco (<= 30 min) ou busca; em falha usa cache e depois o embutido. */
    suspend fun load(baseUrl: String, force: Boolean): PortCatalog = withContext(Dispatchers.IO) {
        val fresh = !force && cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < TTL_MS
        if (fresh) cached()?.let { return@withContext it }
        val fetched = runCatching { fetch(baseUrl) }
            .onSuccess { text -> runCatching { cacheFile.writeText(text) } }
            .onFailure { AppLog.w("PortDb", "falha ao buscar catálogo: ${it.message}") }
            .getOrNull()
        (fetched?.let { runCatching { parse(it) }.getOrNull() })
            ?: cached()
            ?: bundled()
    }

    fun clearCache() {
        runCatching { cacheFile.delete() }
    }

    fun imageUrl(baseUrl: String, path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }

    private fun fetch(baseUrl: String): String {
        val url = URL(baseUrl.trimEnd('/') + "/data/games.json")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 25_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "FuzzyDownloader/2.0")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(text: String): PortCatalog {
        val root = JSONObject(text)
        val site = root.optJSONObject("site") ?: JSONObject()
        val arr = root.optJSONArray("games") ?: JSONArray()
        val games = ArrayList<PortGame>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            games += PortGame(
                id = o.optString("id"),
                title = o.optString("title"),
                port = o.optString("port"),
                original = o.optString("original"),
                categories = o.stringList("categories"),
                tags = o.stringList("tags"),
                chipsets = o.stringList("chipsets"),
                performance = o.stringOrNull("performance"),
                apkSize = o.stringOrNull("apkSize"),
                storage = o.stringOrNull("storage"),
                updated = o.stringOrNull("updated"),
                dateAdded = o.stringOrNull("dateAdded"),
                status = o.stringOrNull("status"),
                statusLabel = o.stringOrNull("statusLabel"),
                featured = o.optBoolean("featured", false),
                downloads = o.optLong("downloads", 0),
                cover = o.stringOrNull("cover"),
                banner = o.stringOrNull("banner"),
                screenshots = o.stringList("screenshots"),
                videoId = o.stringOrNull("videoId"),
                shortDescription = o.optString("shortDescription"),
                description = o.stringList("description"),
                requirements = parseRequirements(o.optJSONObject("requirements")),
                chipsetRecommended = o.stringOrNull("chipsetRecommended"),
                controls = o.stringList("controls"),
                install = o.stringList("install"),
                notes = o.stringOrNull("notes"),
                links = o.stringMap("links"),
                credit = o.stringMap("credit"),
                license = o.stringOrNull("license"),
                type = o.stringOrNull("type"),
                packageName = o.stringOrNull("packageName"),
            )
        }
        return PortCatalog(
            sourceName = site.optString("name", "port-db"),
            sourceTagline = site.optString("tagline", "Ports de jogos para Android"),
            updated = site.optString("updated"),
            games = games,
        )
    }

    private fun parseRequirements(obj: JSONObject?): List<RequirementSection> {
        if (obj == null) return emptyList()
        val out = ArrayList<RequirementSection>()
        val labels = mapOf("minimo" to "Mínimo", "recomendado" to "Recomendado")
        labels.forEach { (key, title) ->
            val section = obj.optJSONObject(key) ?: return@forEach
            val items = ArrayList<Pair<String, String>>()
            section.keys().forEach { k -> items += k to section.optString(k) }
            if (items.isNotEmpty()) out += RequirementSection(title, items)
        }
        return out
    }

    private fun JSONObject.stringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotEmpty() && it != "null" }
    }

    private fun JSONObject.stringList(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val v = arr.optString(i)
            if (v.isNotEmpty()) out += v
        }
        return out
    }

    private fun JSONObject.stringMap(key: String): Map<String, String> {
        val obj = optJSONObject(key) ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        obj.keys().forEach { k ->
            val v = obj.optString(k)
            if (v.isNotEmpty() && v != "null") out[k] = v
        }
        return out
    }

    private companion object {
        const val TTL_MS = 30 * 60 * 1000L
    }
}