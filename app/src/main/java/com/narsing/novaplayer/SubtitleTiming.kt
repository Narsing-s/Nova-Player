package com.narsing.novaplayer

/**
 * Returns subtitle text with every SRT/VTT cue timestamp shifted by [offsetMs].
 * Negative results are clamped to zero; unrelated subtitle text is preserved.
 */
internal fun shiftSubtitleTimestamps(original: String, offsetMs: Long): String {
    if (offsetMs == 0L) return original

    // Supports SRT timestamps and WEBVTT MM:SS.mmm / HH:MM:SS.mmm timestamps.
    val timestamp = Regex("""(?:\d{2}:)?\d{2}:\d{2}[,.]\d{3}""")
    return timestamp.replace(original) { match ->
        val value = match.value
        val separator = if (value.contains(',')) ',' else '.'
        val clock = value.substringBeforeLast(separator)
        val fraction = value.substringAfterLast(separator).toLong()
        val parts = clock.split(':').map { it.toLong() }
        val baseMs = if (parts.size == 3) {
            ((parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000) + fraction
        } else {
            ((parts[0] * 60 + parts[1]) * 1000) + fraction
        }
        val shifted = (baseMs + offsetMs).coerceAtLeast(0L)
        val hours = shifted / 3_600_000
        val minutes = (shifted % 3_600_000) / 60_000
        val seconds = (shifted % 60_000) / 1_000
        val millis = shifted % 1_000

        if (parts.size == 2 && shifted < 3_600_000) {
            "%02d:%02d%c%03d".format(shifted / 60_000, seconds, separator, millis)
        } else {
            "%02d:%02d:%02d%c%03d".format(hours, minutes, seconds, separator, millis)
        }
    }
}
