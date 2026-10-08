package io.github.xtratter.cathunt

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class CoverTest {
    @Test fun placementsStayInsideApartAndOffTheGear() {
        val rnd = Random(3)
        repeat(300) {
            val w = 600f + rnd.nextInt(2000); val h = 400f + rnd.nextInt(1200)
            val r = 60f + rnd.nextInt(40)
            val spots = Cover.place(w, h, r, 2, rnd, gearZone = 200f)
            for (s in spots) {
                assertTrue(s.x - r >= 0f && s.x + r <= w && s.y - r >= 0f && s.y + r <= h)
                assertTrue("gear zone", !(s.x > w - 200f && s.y < 200f))
            }
            if (spots.size == 2) assertTrue(hypot(spots[0].x - spots[1].x, spots[0].y - spots[1].y) >= 2 * r + 20f)
        }
    }

    @Test fun tinyScreenGivesNoCoversAndDoesNotThrow() {
        assertEquals(0, Cover.place(100f, 100f, 60f, 2, Random(1), gearZone = 200f).size)
        assertEquals(0, Cover.place(0f, 0f, 60f, 2, Random(1), gearZone = 200f).size)
    }

    @Test fun zeroCountIsEmpty() {
        assertEquals(0, Cover.place(1000f, 600f, 60f, 0, Random(1), gearZone = 200f).size)
    }

    @Test fun occupiedCoverWigglesIdleOneDoesNot() {
        val c = Cover(Cover.Kind.BOX, 100f, 100f, 60f)
        c.update(0.5f, occupied = false)
        assertEquals(0f, c.wiggle, 1e-6f)
        var maxW = 0f
        repeat(200) { c.update(0.02f, occupied = true); maxW = maxOf(maxW, kotlin.math.abs(c.wiggle)) }
        assertTrue(maxW > 0.01f)
    }

    @Test fun pokeShakesAndDecays() {
        val c = Cover(Cover.Kind.POT, 100f, 100f, 60f)
        c.poke()
        c.update(0.05f, occupied = false)
        assertTrue(kotlin.math.abs(c.wiggle) > 0f || c.shake > 0f)
        repeat(100) { c.update(0.05f, occupied = false) }
        assertEquals(0f, c.shake, 1e-6f)
    }
}
