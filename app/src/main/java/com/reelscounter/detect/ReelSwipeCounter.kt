package com.reelscounter.detect

/**
 * Turns the stream of scroll events from a reels pager into "a new reel is on screen" signals.
 *
 * A swipe produces many scroll events; we wait until scrolling has been quiet for [settleMs]
 * and then look at the first visible item index of the last event. If the pager reports no
 * index (-1), each settled burst of scrolling counts as one reel.
 *
 * The reel visible when the user enters the feed is counted by the caller, so a first settled
 * position of 0 is treated as that same reel.
 */
class ReelSwipeCounter(private val settleMs: Long = 450) {
    private var lastIndex: Int? = null
    private var pending = false
    private var pendingIndex = -1
    private var lastScrollAt = 0L

    fun reset() {
        lastIndex = null
        pending = false
        pendingIndex = -1
    }

    fun onScroll(fromIndex: Int, now: Long) {
        pending = true
        pendingIndex = fromIndex
        lastScrollAt = now
    }

    /** Returns true exactly once per settled swipe that landed on a different reel. */
    fun checkSettled(now: Long): Boolean {
        if (!pending || now - lastScrollAt < settleMs) return false
        pending = false
        if (pendingIndex < 0) return true
        val previous = lastIndex
        lastIndex = pendingIndex
        return if (previous == null) pendingIndex > 0 else pendingIndex != previous
    }

    val settleDelayMs: Long get() = settleMs
}
