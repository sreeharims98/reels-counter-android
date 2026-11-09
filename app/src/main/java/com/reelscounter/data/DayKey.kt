package com.reelscounter.data

import java.time.Instant
import java.time.ZoneId

/** Days are stored as local ISO dates ("2026-11-09"), so counts reset at local midnight. */
object DayKey {
    fun of(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toString()

    fun today(): String = of(System.currentTimeMillis())

    /** The last [n] day keys ending today, oldest first. */
    fun lastDays(epochMillis: Long, n: Int, zone: ZoneId = ZoneId.systemDefault()): List<String> {
        val today = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        return (n - 1 downTo 0).map { today.minusDays(it.toLong()).toString() }
    }
}
