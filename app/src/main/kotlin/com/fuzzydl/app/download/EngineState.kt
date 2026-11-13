package com.fuzzydl.app.download

import com.fuzzydl.app.data.PartStage

data class PartUiState(
    val index: Int,
    val stage: PartStage = PartStage.PENDING,
    val bytesDone: Long = 0,
    val bytesTotal: Long = 0,
    val zipBytes: Long = 0,
    val filesExtracted: Int = 0,
    val extractDone: Long = 0,
    val extractTotal: Long = 0,
    val speedBps: Long = 0,
    val etaSecs: Long = 0,
    val mirror: String? = null,
    val fileName: String? = null,
    val error: String? = null,
)

enum class EngineNotice {
    NOT_WIFI,
    SHIZUKU_REQUIRED,
    TARGET_MISSING,
    NO_SPACE,
    DOWNLOAD_FAILED,
}

data class EngineState(
    val parts: List<PartUiState> = emptyList(),
    val currentIndex: Int? = null,
    val running: Boolean = false,
    val paused: Boolean = false,
    val finished: Boolean = false,
    val notice: EngineNotice? = null,
    val lastError: String? = null,
    val targetAvailable: Boolean = true,
)

sealed interface EngineEvent {
    data class PartDone(val index: Int) : EngineEvent
    data object AllDone : EngineEvent
    data class Failed(val index: Int, val message: String) : EngineEvent
}
