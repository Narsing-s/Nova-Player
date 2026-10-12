package com.narsing.novaplayer

import android.content.pm.ActivityInfo
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
    @Test
    fun playbackErrorsGiveActionableGuidance() {
        assertEquals(
            "This video file could not be found. It may have moved or been deleted. Choose the file again.",
            playbackErrorGuidance("ERROR_CODE_IO_FILE_NOT_FOUND")
        )
        assertEquals(
            "This media codec may not be supported on this device. Try another audio/video track or another file.",
            playbackErrorGuidance("ERROR_CODE_DECODER_INIT_FAILED")
        )
        assertEquals(
            "The stream could not be reached. Check your connection and try again.",
            playbackErrorGuidance("ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT")
        )
        assertEquals(
            "Playback failed (ERROR_CODE_UNEXPECTED). Try again or choose another video.",
            playbackErrorGuidance("ERROR_CODE_UNEXPECTED")
        )
    }

    @Test
    fun orientationModeMapsToExpectedActivityRequests() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requestedOrientationForMode("Portrait"))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, requestedOrientationForMode("Landscape"))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR, requestedOrientationForMode("Auto"))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR, requestedOrientationForMode("unexpected"))
    }

    @Test
    fun immersivePlaybackOnlyUsesLandscapeWhenMediaIsSelected() {
        assertEquals(true, shouldUseImmersivePlayback(fullScreen = true, hasSelectedMedia = false, isLandscape = false))
        assertEquals(true, shouldUseImmersivePlayback(fullScreen = false, hasSelectedMedia = true, isLandscape = true))
        assertEquals(false, shouldUseImmersivePlayback(fullScreen = false, hasSelectedMedia = false, isLandscape = true))
        assertEquals(false, shouldUseImmersivePlayback(fullScreen = false, hasSelectedMedia = true, isLandscape = false))
    }

}
