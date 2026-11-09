package com.reelscounter.detect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelSwipeCounterTest {

    private val counter = ReelSwipeCounter(settleMs = 400)

    @Test
    fun `nothing to report without scrolls`() {
        assertFalse(counter.checkSettled(10_000))
    }

    @Test
    fun `does not report before the scroll settles`() {
        counter.onScroll(fromIndex = 1, now = 1000)
        assertFalse(counter.checkSettled(1200))
        assertTrue(counter.checkSettled(1400))
    }

    @Test
    fun `many scroll events in one swipe count once`() {
        counter.onScroll(0, 1000)
        counter.onScroll(0, 1050)
        counter.onScroll(1, 1100)
        counter.onScroll(1, 1150)
        assertTrue(counter.checkSettled(1600))
        assertFalse(counter.checkSettled(2000))
    }

    @Test
    fun `snapping back to the same reel does not count`() {
        counter.onScroll(2, 1000)
        assertTrue(counter.checkSettled(1500))
        counter.onScroll(2, 2000) // partial drag, released, same item
        assertFalse(counter.checkSettled(2500))
    }

    @Test
    fun `first settled position at index 0 is the reel already counted on entry`() {
        counter.onScroll(0, 1000)
        assertFalse(counter.checkSettled(1500))
        counter.onScroll(1, 2000)
        assertTrue(counter.checkSettled(2500))
    }

    @Test
    fun `going back to a previous reel counts as a new view`() {
        counter.onScroll(3, 1000); counter.checkSettled(1500)
        counter.onScroll(2, 2000)
        assertTrue(counter.checkSettled(2500))
    }

    @Test
    fun `without index info each settled burst counts once`() {
        counter.onScroll(-1, 1000)
        counter.onScroll(-1, 1100)
        assertTrue(counter.checkSettled(1600))
        counter.onScroll(-1, 3000)
        assertTrue(counter.checkSettled(3500))
    }

    @Test
    fun `reset forgets the last position`() {
        counter.onScroll(4, 1000); counter.checkSettled(1500)
        counter.reset()
        counter.onScroll(0, 2000)
        assertFalse(counter.checkSettled(2500))
    }
}
