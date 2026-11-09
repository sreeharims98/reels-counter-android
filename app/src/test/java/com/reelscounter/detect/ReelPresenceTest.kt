package com.reelscounter.detect

import com.reelscounter.TrackedApp.INSTAGRAM
import com.reelscounter.TrackedApp.YOUTUBE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReelPresenceTest {

    private val presence = ReelPresence(exitGraceMs = 1000)

    @Test
    fun `enters immediately`() {
        assertEquals(Transition.Entered(INSTAGRAM), presence.update(INSTAGRAM, 0))
        assertEquals(INSTAGRAM, presence.current)
    }

    @Test
    fun `repeated detection is not a transition`() {
        presence.update(INSTAGRAM, 0)
        assertEquals(Transition.None, presence.update(INSTAGRAM, 100))
    }

    @Test
    fun `brief miss inside the grace period does not exit`() {
        presence.update(YOUTUBE, 0)
        assertEquals(Transition.None, presence.update(null, 100))
        assertEquals(Transition.None, presence.update(YOUTUBE, 600))
        assertEquals(Transition.None, presence.update(null, 700))
        assertEquals(Transition.None, presence.update(null, 1600))
        assertEquals(YOUTUBE, presence.current)
    }

    @Test
    fun `sustained miss exits`() {
        presence.update(YOUTUBE, 0)
        presence.update(null, 100)
        assertEquals(Transition.Exited(YOUTUBE), presence.update(null, 1100))
        assertNull(presence.current)
    }

    @Test
    fun `forced exit skips the grace period`() {
        presence.update(INSTAGRAM, 0)
        assertEquals(Transition.Exited(INSTAGRAM), presence.forceExit())
        assertEquals(Transition.None, presence.forceExit())
    }

    @Test
    fun `switching app exits then enters`() {
        presence.update(INSTAGRAM, 0)
        assertEquals(Transition.Switched(INSTAGRAM, YOUTUBE), presence.update(YOUTUBE, 50))
        assertEquals(YOUTUBE, presence.current)
    }
}
