package com.narsing.novaplayer

import android.content.pm.ActivityInfo
import androidx.media3.common.Player

/** Clamps gesture-based seeks without overflowing on extreme offsets. */
internal fun clampSeekPosition(currentPositionMs: Long, deltaMs: Long, durationMs: Long): Long {
    val current = currentPositionMs.coerceAtLeast(0L)
    val target = try {
        Math.addExact(current, deltaMs)
    } catch (_: ArithmeticException) {
        if (deltaMs >= 0L) Long.MAX_VALUE else Long.MIN_VALUE
    }
    val nonNegative = target.coerceAtLeast(0L)
    return if (durationMs > 0L) nonNegative.coerceAtMost(durationMs) else nonNegative
}

internal fun playbackStateLabel(state: Int): String = when (state) {
    Player.STATE_IDLE -> "Idle"
    Player.STATE_BUFFERING -> "Buffering"
    Player.STATE_READY -> "Ready"
    Player.STATE_ENDED -> "Ended"
    else -> "Unknown ($state)"
}

/** Keeps orientation policy centralized so it can be regression-tested. */
internal fun requestedOrientationForMode(mode: String): Int = when (mode) {
    "Portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    "Landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
}

/** Immersive playback is active for explicit full-screen or landscape playback. */
internal fun shouldUseImmersivePlayback(fullScreen: Boolean, hasSelectedMedia: Boolean, isLandscape: Boolean): Boolean =
    fullScreen || (hasSelectedMedia && isLandscape)


/** Turns Media3 error codes into practical next steps without exposing private media paths. */
internal fun playbackErrorGuidance(errorCodeName: String): String {
    val code = errorCodeName.uppercase()
    return when {
        "FILE_NOT_FOUND" in code ->
            "This video file could not be found. It may have moved or been deleted. Choose the file again."
        "DECODER" in code || "DECODING" in code ->
            "This media codec may not be supported on this device. Try another audio/video track or another file."
        "PARSING" in code || "MALFORMED" in code ->
            "This file may be damaged or use an unsupported format. Try another copy or a different format."
        "NETWORK_CONNECTION_TIMEOUT" in code || "NETWORK_CONNECTION_FAILED" in code ->
            "The stream could not be reached. Check your connection and try again."
        "BAD_HTTP_STATUS" in code || "INVALID_HTTP_CONTENT_TYPE" in code ->
            "The stream server rejected the request. Check the stream URL or access permissions and try again."
        "CLEARTEXT_NOT_PERMITTED" in code ->
            "This stream uses an insecure HTTP connection that Android blocks. Use an HTTPS source if available."
        "DRM" in code ->
            "This media requires a protection system that could not be initialized on this device."
        else ->
            "Playback failed ($errorCodeName). Try again or choose another video."
    }
}
