package io.github.xtratter.cathunt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayTimerTest {
    private fun run(t: PlayTimer, sec: Float) { var left = sec; while (left > 0f) { t.update(0.05f); left -= 0.05f } }

    @Test fun zeroLimitNeverEnds() {
        val t = PlayTimer(0)
        run(t, 4000f)
        assertEquals(PlayTimer.Phase.ACTIVE, t.phase)
        assertEquals(1f, t.speedFactor, 0f)
    }

    @Test fun windDownSlowsMonotonically() {
        val t = PlayTimer(1)                       // 1 minute
        run(t, 59f)
        assertEquals(PlayTimer.Phase.ACTIVE, t.phase)
        run(t, 2f)
        assertEquals(PlayTimer.Phase.WIND_DOWN, t.phase)
        var prev = t.speedFactor
        repeat(20) { run(t, 1f); assertTrue(t.speedFactor <= prev + 1e-6f); prev = t.speedFactor }
        assertTrue(t.speedFactor >= PlayTimer.MIN_SPEED - 1e-6f)
    }

    @Test fun lastCritterPhaseThenCatchFadesAndEnds() {
        val t = PlayTimer(1)
        run(t, 60f + PlayTimer.WIND_DOWN_SEC + 1f)
        assertEquals(PlayTimer.Phase.LAST, t.phase)
        t.lastCaught()
        assertEquals(PlayTimer.Phase.FADE, t.phase)
        run(t, PlayTimer.FADE_SEC + 0.2f)
        assertEquals(PlayTimer.Phase.ENDED, t.phase)
        assertEquals(1f, t.fade, 1e-6f)
    }

    @Test fun lastPhaseTimesOutWithoutCatch() {
        val t = PlayTimer(1)
        run(t, 60f + PlayTimer.WIND_DOWN_SEC + PlayTimer.LAST_TIMEOUT_SEC + PlayTimer.FADE_SEC + 1f)
        assertEquals(PlayTimer.Phase.ENDED, t.phase)
    }

    @Test fun restartResetsEverything() {
        val t = PlayTimer(1)
        run(t, 200f)
        assertEquals(PlayTimer.Phase.ENDED, t.phase)
        t.restart(5)
        assertEquals(PlayTimer.Phase.ACTIVE, t.phase)
        assertEquals(0f, t.fade, 0f)
        assertEquals(1f, t.speedFactor, 0f)
        run(t, 299f)
        assertEquals(PlayTimer.Phase.ACTIVE, t.phase)
    }

    @Test fun spuriousCallsAreIgnored() {
        val t = PlayTimer(1)
        t.lastCaught()                             // nothing to catch yet
        assertEquals(PlayTimer.Phase.ACTIVE, t.phase)
        t.update(100f)                             // a huge dt must not skip past the stages silently
        assertTrue(t.phase != PlayTimer.Phase.ENDED)
        assertFalse(t.ended)
    }
}
