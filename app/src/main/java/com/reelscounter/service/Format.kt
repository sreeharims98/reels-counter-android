package com.reelscounter.service

import com.reelscounter.TrackedApp
import com.reelscounter.limits.BlockReason
import com.reelscounter.limits.Verdict

fun formatMinutes(seconds: Long): String {
    val m = seconds / 60
    return if (m < 60) "${m}m" else "${m / 60}h ${m % 60}m"
}

fun blockMessage(app: TrackedApp, v: Verdict.Blocked): String = when (v.reason) {
    BlockReason.COUNT -> "Daily reel limit reached (${v.used}/${v.limit}). ${app.feedName} blocked until tomorrow."
    BlockReason.TIME -> "Daily ${app.feedName} time limit reached (${v.limit / 60} min). Blocked until tomorrow."
}
