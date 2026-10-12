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
