package com.narsing.novaplayer

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackDiagnosticsTest {
    @Test
    fun seekClampsToStartAndKnownDuration() {
        assertEquals(0L, clampSeekPosition(1_000L, -5_000L, 10_000L))
        assertEquals(10_000L, clampSeekPosition(9_000L, 5_000L, 10_000L))
    }

    @Test
    fun seekAllowsForwardPositionWhenDurationIsUnknown() {
        assertEquals(15_000L, clampSeekPosition(10_000L, 5_000L, 0L))
    }

    @Test
    fun seekDoesNotOverflowAtLongBoundaries() {
        assertEquals(Long.MAX_VALUE, clampSeekPosition(Long.MAX_VALUE - 2L, 10L, 0L))
        assertEquals(0L, clampSeekPosition(2L, Long.MIN_VALUE, 0L))
    }

    @Test
    fun playbackStatesHaveReadableDiagnostics() {
        assertEquals("Idle", playbackStateLabel(Player.STATE_IDLE))
        assertEquals("Buffering", playbackStateLabel(Player.STATE_BUFFERING))
        assertEquals("Ready", playbackStateLabel(Player.STATE_READY))
        assertEquals("Ended", playbackStateLabel(Player.STATE_ENDED))
    }
}
