package com.fuzzydl.app.ui

import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val gib = bytes / 1_073_741_824.0
    return if (gib >= 1.0) {
        String.format(Locale.getDefault(), "%.2f GiB", gib)
    } else {
        String.format(Locale.getDefault(), "%.0f MiB", bytes / 1_048_576.0)
    }
}

fun formatSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0) return "—"
    return String.format(Locale.getDefault(), "%.2f MiB/s", bytesPerSecond / 1_048_576.0)
}

fun formatEta(seconds: Long): String {
    if (seconds <= 0) return "—"
    return String.format(
        Locale.US,
        "%02d:%02d:%02d",
        seconds / 3600,
        seconds % 3600 / 60,
        seconds % 60,
    )
}

fun formatPercent(done: Long, total: Long): String {
    if (total <= 0) return "0%"
    return String.format(Locale.getDefault(), "%.1f%%", done * 100.0 / total)
}
