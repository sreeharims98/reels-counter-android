package com.reelscounter.limits

import com.reelscounter.TrackedApp.INSTAGRAM
import com.reelscounter.TrackedApp.YOUTUBE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitPolicyTest {

    private fun usage(ig: Int = 0, yt: Int = 0, igSec: Long = 0, ytSec: Long = 0) = Usage(
        counts = mapOf(INSTAGRAM to ig, YOUTUBE to yt),
        seconds = mapOf(INSTAGRAM to igSec, YOUTUBE to ytSec),
    )

    @Test
    fun `no limits enabled always allows`() {
        val limits = Limits(countEnabled = false, countLimit = 1, timeEnabled = false, timeLimitMinutes = 1)
        assertEquals(Verdict.Allowed, LimitPolicy.evaluate(INSTAGRAM, usage(ig = 500, igSec = 99_999), limits))
    }

    @Test
    fun `count limit allows exactly N reels then blocks`() {
        val limits = Limits(countEnabled = true, countLimit = 3, timeEnabled = false, timeLimitMinutes = 30)
        assertEquals(Verdict.Allowed, LimitPolicy.evaluate(INSTAGRAM, usage(ig = 2), limits))
        val v = LimitPolicy.evaluate(INSTAGRAM, usage(ig = 3), limits)
        assertEquals(Verdict.Blocked(BlockReason.COUNT, used = 3, limit = 3), v)
    }

    @Test
    fun `combined scope sums both apps`() {
        val limits = Limits(countEnabled = true, countLimit = 5, timeEnabled = false, timeLimitMinutes = 30, scope = LimitScope.COMBINED)
        assertTrue(LimitPolicy.evaluate(YOUTUBE, usage(ig = 3, yt = 2), limits) is Verdict.Blocked)
    }

    @Test
    fun `per app scope only counts that app`() {
        val limits = Limits(countEnabled = true, countLimit = 5, timeEnabled = false, timeLimitMinutes = 30, scope = LimitScope.PER_APP)
        assertEquals(Verdict.Allowed, LimitPolicy.evaluate(YOUTUBE, usage(ig = 10, yt = 2), limits))
        assertTrue(LimitPolicy.evaluate(INSTAGRAM, usage(ig = 10, yt = 2), limits) is Verdict.Blocked)
    }

    @Test
    fun `time limit blocks once minutes are used up`() {
        val limits = Limits(countEnabled = false, countLimit = 50, timeEnabled = true, timeLimitMinutes = 2)
        assertEquals(Verdict.Allowed, LimitPolicy.evaluate(YOUTUBE, usage(igSec = 60, ytSec = 59), limits))
        assertEquals(
            Verdict.Blocked(BlockReason.TIME, used = 120, limit = 120),
            LimitPolicy.evaluate(YOUTUBE, usage(igSec = 60, ytSec = 60), limits),
        )
    }

    @Test
    fun `count reason wins when both limits are hit`() {
        val limits = Limits(countEnabled = true, countLimit = 1, timeEnabled = true, timeLimitMinutes = 1)
        val v = LimitPolicy.evaluate(INSTAGRAM, usage(ig = 1, igSec = 600), limits)
        assertEquals(BlockReason.COUNT, (v as Verdict.Blocked).reason)
    }
}
