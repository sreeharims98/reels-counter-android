package com.reelscounter.detect

import com.reelscounter.TrackedApp

sealed interface Transition {
    data object None : Transition
    data class Entered(val app: TrackedApp) : Transition
    data class Exited(val app: TrackedApp) : Transition
    data class Switched(val from: TrackedApp, val to: TrackedApp) : Transition
}

/**
 * Tracks whether the user is inside a Reels/Shorts feed. Entering is immediate; leaving needs the
 * feed to be missing for [exitGraceMs] so a flaky accessibility snapshot (sheet opening, layout
 * pass) doesn't end the session and re-count the current reel.
 */
class ReelPresence(private val exitGraceMs: Long = 1200) {
    var current: TrackedApp? = null
        private set
    private var missingSince: Long? = null

    fun update(detected: TrackedApp?, now: Long): Transition {
        val cur = current
        if (detected != null) {
            missingSince = null
            current = detected
            return when {
                cur == null -> Transition.Entered(detected)
                cur != detected -> Transition.Switched(cur, detected)
                else -> Transition.None
            }
        }
        if (cur == null) return Transition.None
        val since = missingSince ?: now.also { missingSince = it }
        if (now - since >= exitGraceMs) {
            current = null
            missingSince = null
            return Transition.Exited(cur)
        }
        return Transition.None
    }

    fun forceExit(): Transition {
        val cur = current ?: return Transition.None
        current = null
        missingSince = null
        return Transition.Exited(cur)
    }
}
