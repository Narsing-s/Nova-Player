package com.narsing.novaplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTimeTest {
    @Test
    fun formatsShortClipsAsMinutesAndSeconds() {
        assertEquals("00:00", formatTime(0L))
        assertEquals("00:09", formatTime(9_999L))
        assertEquals("01:05", formatTime(65_000L))
        assertEquals("59:59", formatTime(3_599_000L))
    }

    @Test
    fun formatsLongVideosWithHours() {
        assertEquals("01:00:00", formatTime(3_600_000L))
        assertEquals("12:34:56", formatTime(45_296_000L))
    }

    @Test
    fun clampsNegativePositionsToZero() {
        assertEquals("00:00", formatTime(-1L))
        assertEquals("00:00", formatTime(Long.MIN_VALUE))
    }
}
