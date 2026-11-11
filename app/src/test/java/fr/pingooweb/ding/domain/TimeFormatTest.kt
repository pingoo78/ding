package fr.pingooweb.ding.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    @Test
    fun formatsMinutesAndSeconds() {
        assertEquals("0:00", TimeFormat.format(0))
        assertEquals("0:30", TimeFormat.format(30_000))
        assertEquals("5:00", TimeFormat.format(300_000))
        assertEquals("59:59", TimeFormat.format(3_599_000))
    }

    @Test
    fun formatsHours() {
        assertEquals("1:00:00", TimeFormat.format(3_600_000))
        assertEquals("1:02:03", TimeFormat.format(3_723_000))
    }

    @Test
    fun roundsUpForCountdown() {
        assertEquals("0:05", TimeFormat.format(4_200))
        assertEquals("0:01", TimeFormat.format(1))
        assertEquals("0:01", TimeFormat.format(1_000))
        assertEquals("0:02", TimeFormat.format(1_001))
        assertEquals("1:00", TimeFormat.format(59_001))
    }

    @Test
    fun floorWhenRequested() {
        assertEquals("0:04", TimeFormat.format(4_900, roundUp = false))
    }

    @Test
    fun negativeIsZero() {
        assertEquals("0:00", TimeFormat.format(-5_000))
    }

    @Test
    fun parsesNumericPadDigits() {
        assertEquals(12 * 60_000L + 30_000L, TimeFormat.parseDigits("1230"))
        assertEquals(5_000L, TimeFormat.parseDigits("5"))
        assertEquals(90_000L, TimeFormat.parseDigits("130"))
        assertEquals(3_723_000L, TimeFormat.parseDigits("10203"))
        assertEquals(0L, TimeFormat.parseDigits(""))
        // Plus de 6 chiffres : on garde les 6 derniers.
        assertEquals(3_723_000L, TimeFormat.parseDigits("9010203"))
    }

    @Test
    fun formatsDigitsProgressively() {
        assertEquals("0:00", TimeFormat.formatDigits(""))
        assertEquals("0:05", TimeFormat.formatDigits("5"))
        assertEquals("1:30", TimeFormat.formatDigits("130"))
        assertEquals("12:30", TimeFormat.formatDigits("1230"))
        assertEquals("1:02:03", TimeFormat.formatDigits("10203"))
    }

    @Test
    fun digitsRoundTrip() {
        assertEquals("1230", TimeFormat.toDigits(750_000))
        assertEquals(750_000L, TimeFormat.parseDigits(TimeFormat.toDigits(750_000)))
        assertEquals("", TimeFormat.toDigits(0))
    }
}
