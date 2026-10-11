package com.narsing.novaplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleTimingTest {
    @Test
    fun shiftsSrtCueStartAndEndWithoutChangingCueText() {
        val input = "1\n00:00:01,200 --> 00:00:03,400\nHello world"
        assertEquals(
            "1\n00:00:02,200 --> 00:00:04,400\nHello world",
            shiftSubtitleTimestamps(input, 1_000L)
        )
    }

    @Test
    fun shiftsWebVttShortTimestampsAndPreservesHeader() {
        val input = "WEBVTT\n\n00:01.000 --> 00:03.500\nCaption"
        assertEquals(
            "WEBVTT\n\n00:02.000 --> 00:04.500\nCaption",
            shiftSubtitleTimestamps(input, 1_000L)
        )
    }

    @Test
    fun clampsNegativeTimestampsAtZero() {
        val input = "00:00:00,500 --> 00:00:02,000"
        assertEquals(
            "00:00:00,000 --> 00:00:01,000",
            shiftSubtitleTimestamps(input, -1_000L)
        )
    }

    @Test
    fun promotesShortTimestampWhenShiftCrossesOneHour() {
        assertEquals(
            "01:00:00.250",
            shiftSubtitleTimestamps("59:59.250", 1_000L)
        )
    }

    @Test
    fun zeroOffsetLeavesOriginalTextUntouched() {
        val input = "WEBVTT\n\n00:01.000 --> 00:02.000"
        assertEquals(input, shiftSubtitleTimestamps(input, 0L))
    }
}
