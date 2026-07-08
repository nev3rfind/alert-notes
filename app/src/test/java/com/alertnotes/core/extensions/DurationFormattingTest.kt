package com.alertnotes.core.extensions

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormattingTest {

    @Test
    fun `countdown format picks the right precision per magnitude`() {
        assertEquals("42s", Duration.ofSeconds(42).toCountdownString())
        assertEquals("19m 58s", Duration.ofMinutes(19).plusSeconds(58).toCountdownString())
        assertEquals("1h 04m 12s", Duration.ofHours(1).plusMinutes(4).plusSeconds(12).toCountdownString())
        assertEquals("3d 4h", Duration.ofDays(3).plusHours(4).toCountdownString())
    }

    @Test
    fun `countdown clamps negative durations to zero`() {
        assertEquals("0s", Duration.ofSeconds(-5).toCountdownString())
    }

    @Test
    fun `clock format is always fixed-width HH-MM-SS`() {
        assertEquals("00:00:52", Duration.ofSeconds(52).toClockString())
        assertEquals("00:15:20", Duration.ofMinutes(15).plusSeconds(20).toClockString())
        assertEquals("02:05:09", Duration.ofHours(2).plusMinutes(5).plusSeconds(9).toClockString())
        assertEquals("00:00:00", Duration.ofSeconds(-1).toClockString())
    }
}
