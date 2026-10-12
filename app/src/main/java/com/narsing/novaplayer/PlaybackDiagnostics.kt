package com.narsing.novaplayer

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
