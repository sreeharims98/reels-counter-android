package com.reelscounter.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DayKeyTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `day key uses local date`() {
        assertEquals("2026-11-09", DayKey.of(millis(2026, 11, 9, 0, 1), zone))
        assertEquals("2026-11-09", DayKey.of(millis(2026, 11, 9, 23, 59), zone))
    }

    @Test
    fun `rolls over at local midnight`() {
        assertEquals("2026-11-10", DayKey.of(millis(2026, 11, 10, 0, 0), zone))
    }

    @Test
    fun `last N days are oldest first and include today`() {
        assertEquals(
            listOf("2026-02-27", "2026-02-28", "2026-03-01"),
            DayKey.lastDays(millis(2026, 3, 1, 12, 0), 3, zone),
        )
    }
}
