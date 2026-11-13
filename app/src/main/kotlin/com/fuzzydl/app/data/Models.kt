package com.fuzzydl.app.data

import org.json.JSONObject

data class PartSpec(
    val index: Int,
    val id: String,
    val url: String,
)

data class PartsConfig(
    val targetPackage: String,
    val targetDir: String,
    val parts: List<PartSpec>,
)

enum class PartStage {
    PENDING,
    RESOLVING,
    DOWNLOADING,
    VERIFYING,
    EXTRACTING,
    PAUSED,
    DONE,
    FAILED;

    companion object {
        fun from(name: String?): PartStage = entries.firstOrNull { it.name == name } ?: PENDING
    }
}

data class SettingsData(
    val threads: Int = 4,
    val wifiOnly: Boolean = true,
    val theme: String = "dark",
    val simulation: Boolean = false,
)

data class PartState(
    val index: Int,
    val stage: PartStage = PartStage.PENDING,
    val bytesDone: Long = 0,
    val bytesTotal: Long = 0,
    val zipBytes: Long = 0,
    val error: String? = null,
    val mirror: String? = null,
    val filesExtracted: Int = 0,
)

data class EngineSnapshot(
    val parts: List<PartState> = emptyList(),
    val settings: SettingsData = SettingsData(),
) {
    fun toJson(): String = JSONObject().apply {
        put("version", 1)
        put("settings", JSONObject().apply {
            put("threads", settings.threads)
            put("wifiOnly", settings.wifiOnly)
            put("theme", settings.theme)
            put("simulation", settings.simulation)
        })
        put("parts", org.json.JSONArray().apply {
            parts.forEach { p ->
                put(JSONObject().apply {
                    put("index", p.index)
                    put("stage", p.stage.name)
                    put("bytesDone", p.bytesDone)
                    put("bytesTotal", p.bytesTotal)
                    put("zipBytes", p.zipBytes)
                    put("filesExtracted", p.filesExtracted)
                    put("mirror", p.mirror)
                    p.error?.let { put("error", it) }
                })
            }
        })
    }.toString()

    companion object {
        fun fromJson(text: String): EngineSnapshot? = runCatching {
            val root = JSONObject(text)
            if (root.optInt("version") != 1) return null
            val s = root.optJSONObject("settings")
            val settings = SettingsData(
                threads = s?.optInt("threads", 4)?.coerceIn(1, 8) ?: 4,
                wifiOnly = s?.optBoolean("wifiOnly", true) ?: true,
                theme = s?.optString("theme", "dark") ?: "dark",
                simulation = s?.optBoolean("simulation", false) ?: false,
            )
            val arr = root.optJSONArray("parts") ?: org.json.JSONArray()
            val parts = ArrayList<PartState>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                parts += PartState(
                    index = o.optInt("index", i + 1),
                    stage = PartStage.from(o.optString("stage")),
                    bytesDone = o.optLong("bytesDone"),
                    bytesTotal = o.optLong("bytesTotal"),
                    zipBytes = o.optLong("zipBytes"),
                    filesExtracted = o.optInt("filesExtracted"),
                    mirror = o.optString("mirror").takeIf { it.isNotEmpty() && it != "null" },
                    error = o.optString("error").takeIf { it.isNotEmpty() && it != "null" },
                )
            }
            EngineSnapshot(parts.sortedBy { it.index }, settings)
        }.getOrNull()
    }
}

data class ShizukuStatus(
    val installed: Boolean = false,
    val running: Boolean = false,
    val permission: Boolean = false,
    val legacy: Boolean = false,
    val ready: Boolean = false,
    val version: Int = 0,
    val error: String? = null,
)
