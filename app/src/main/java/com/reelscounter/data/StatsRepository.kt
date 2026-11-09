package com.reelscounter.data

import com.reelscounter.TrackedApp
import com.reelscounter.limits.Usage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

data class DayStats(val day: String, val usage: Usage)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsRepository(private val dao: ReelDao) {

    /** Emits the current day key and re-emits when local midnight passes. */
    val currentDay: Flow<String> = flow {
        while (true) {
            emit(DayKey.today())
            delay(30_000)
        }
    }.distinctUntilChanged()

    val today: Flow<Usage> = currentDay.flatMapLatest { usageFor(it) }

    /** Last [days] days (oldest first), always [days] entries long. */
    fun history(days: Int = 7): Flow<List<DayStats>> = currentDay.flatMapLatest {
        val keys = DayKey.lastDays(System.currentTimeMillis(), days)
        combine(dao.observeCountsSince(keys.first()), dao.observeWatchTimeSince(keys.first())) { counts, times ->
            keys.map { day ->
                DayStats(
                    day,
                    Usage(
                        counts = counts.filter { it.day == day }
                            .mapNotNull { c -> TrackedApp.fromName(c.app)?.let { it to c.count } }.toMap(),
                        seconds = times.filter { it.day == day }
                            .mapNotNull { t -> TrackedApp.fromName(t.app)?.let { it to t.seconds } }.toMap(),
                    ),
                )
            }
        }
    }

    private fun usageFor(day: String): Flow<Usage> =
        combine(dao.observeCounts(day), dao.observeWatchTime(day)) { counts, times ->
            Usage(
                counts = counts.mapNotNull { c -> TrackedApp.fromName(c.app)?.let { it to c.count } }.toMap(),
                seconds = times.mapNotNull { t -> TrackedApp.fromName(t.app)?.let { it to t.seconds } }.toMap(),
            )
        }

    suspend fun recordReel(app: TrackedApp, now: Long = System.currentTimeMillis()) =
        dao.insertEvent(ReelEvent(timestamp = now, day = DayKey.of(now), app = app.name))

    suspend fun addWatchSeconds(app: TrackedApp, seconds: Long) {
        if (seconds > 0) dao.addSeconds(DayKey.today(), app.name, seconds)
    }

    suspend fun resetToday() = dao.clearDay(DayKey.today())
}
