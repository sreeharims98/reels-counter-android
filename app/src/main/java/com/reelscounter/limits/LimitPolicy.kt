package com.reelscounter.limits

import com.reelscounter.TrackedApp

enum class LimitScope { COMBINED, PER_APP }

data class Limits(
    val countEnabled: Boolean,
    val countLimit: Int,
    val timeEnabled: Boolean,
    val timeLimitMinutes: Int,
    val scope: LimitScope = LimitScope.COMBINED,
)

/** Today's usage per app. */
data class Usage(
    val counts: Map<TrackedApp, Int> = emptyMap(),
    val seconds: Map<TrackedApp, Long> = emptyMap(),
) {
    val totalCount: Int get() = counts.values.sum()
    val totalSeconds: Long get() = seconds.values.sum()
    fun count(app: TrackedApp) = counts[app] ?: 0
    fun seconds(app: TrackedApp) = seconds[app] ?: 0L

    fun plusSeconds(extra: Map<TrackedApp, Long>): Usage =
        if (extra.isEmpty()) this
        else copy(seconds = TrackedApp.entries.associateWith { seconds(it) + (extra[it] ?: 0L) })
}

enum class BlockReason { COUNT, TIME }

sealed interface Verdict {
    data object Allowed : Verdict

    /** [used] / [limit] are reels for COUNT and seconds for TIME. */
    data class Blocked(val reason: BlockReason, val used: Long, val limit: Long) : Verdict
}

object LimitPolicy {
    /** May the user watch (another) reel in [app] right now? */
    fun evaluate(app: TrackedApp, usage: Usage, limits: Limits): Verdict {
        val perApp = limits.scope == LimitScope.PER_APP
        if (limits.countEnabled) {
            val used = (if (perApp) usage.count(app) else usage.totalCount).toLong()
            val limit = limits.countLimit.toLong()
            if (used >= limit) return Verdict.Blocked(BlockReason.COUNT, used, limit)
        }
        if (limits.timeEnabled) {
            val used = if (perApp) usage.seconds(app) else usage.totalSeconds
            val limit = limits.timeLimitMinutes * 60L
            if (used >= limit) return Verdict.Blocked(BlockReason.TIME, used, limit)
        }
        return Verdict.Allowed
    }
}
