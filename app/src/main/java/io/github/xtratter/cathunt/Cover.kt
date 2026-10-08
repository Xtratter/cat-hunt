package io.github.xtratter.cathunt

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** A place to hide: critters run under it, wait, and spring out. Drawn on top of the critters, seen from above. */
class Cover(val kind: Kind, val x: Float, val y: Float, val r: Float) {
    enum class Kind { BOX, POT }
    class Spot(val x: Float, val y: Float)

    /** 0 when still; otherwise a small rotation offset in radians. */
    var wiggle = 0f; private set
    /** Seconds of strong shaking left after a poke. */
    var shake = 0f; private set
    private var t = 0f

    fun poke() { shake = 0.5f }

    /** An occupied cover twitches now and then — a hint for the cat that something is inside. */
    fun update(dt: Float, occupied: Boolean) {
        t += dt
        shake = max(0f, shake - dt)
        wiggle = when {
            shake > 0f -> sin(t * 60f) * 0.06f * (shake / 0.5f)
            occupied -> (sin(t * 4.3f).let { if (it > 0.82f) sin(t * 55f) * 0.035f else 0f })
            else -> 0f
        }
    }

    fun hit(px: Float, py: Float, slack: Float) = hypot(px - x, py - y) < r + slack

    // paints are created on first draw so the logic above stays testable on the JVM
    private val art by lazy { Art() }
    private class Art {
        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC8A165.toInt() }
        val boxDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFA9824A.toInt() }
        val tape = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE9D8A6.toInt() }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8D6A3A.toInt(); style = Paint.Style.STROKE }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000 }
        val pot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB5654A.toInt() }
        val potRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD07A5D.toInt() }
        val soil = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3B2A20.toInt() }
        val leaf = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF66BB6A.toInt() }
        val leafDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF43A047.toInt() }
        val path = Path()
    }

    fun draw(c: Canvas) {
        val a = art
        c.save()
        c.translate(x, y)
        c.rotate((wiggle * 180f / PI.toFloat()))
        c.drawCircle(r * 0.08f, r * 0.12f, r * 1.05f, a.shadow)
        when (kind) {
            Kind.BOX -> {
                val s = r * 0.92f
                a.line.strokeWidth = max(1f, r * 0.03f)
                c.drawRoundRect(-s, -s, s, s, r * 0.08f, r * 0.08f, a.box)
                // flaps: two halves with a seam, darker edges
                c.drawRect(-s, -s * 0.02f, s, s * 0.02f, a.boxDark)
                c.drawRoundRect(-s, -s, s, s, r * 0.08f, r * 0.08f, a.line)
                // tape along the seam
                c.drawRect(-s * 0.16f, -s, s * 0.16f, s, a.tape)
                c.drawLine(-s * 0.16f, -s, -s * 0.16f, s, a.line)
                c.drawLine(s * 0.16f, -s, s * 0.16f, s, a.line)
            }
            Kind.POT -> {
                c.drawCircle(0f, 0f, r * 0.85f, a.pot)
                c.drawCircle(0f, 0f, r * 0.85f, a.line)
                c.drawCircle(0f, 0f, r * 0.62f, a.soil)
                // leaves radiating from the middle
                for (i in 0 until 9) {
                    val ang = (i / 9f) * 2f * PI.toFloat() + 0.3f
                    val p = a.path
                    p.reset()
                    p.moveTo(0f, 0f)
                    val tx = cos(ang) * r * 1.0f; val ty = sin(ang) * r * 1.0f
                    val nx = -sin(ang) * r * 0.26f; val ny = cos(ang) * r * 0.26f
                    p.quadTo(tx * 0.5f + nx, ty * 0.5f + ny, tx, ty)
                    p.quadTo(tx * 0.5f - nx, ty * 0.5f - ny, 0f, 0f)
                    c.drawPath(p, if (i % 2 == 0) a.leaf else a.leafDark)
                }
                c.drawCircle(0f, 0f, r * 0.12f, a.leafDark)
            }
        }
        c.restore()
    }

    companion object {
        /** Up to [count] non-overlapping spots fully on screen and away from the top-right gear. May return fewer. */
        fun place(w: Float, h: Float, r: Float, count: Int, rnd: Random, gearZone: Float): List<Spot> {
            val out = ArrayList<Spot>()
            if (count <= 0 || w < 2 * r + 20f || h < 2 * r + 20f) return out
            var tries = 0
            while (out.size < count && tries < 60) {
                tries++
                val x = r + rnd.nextFloat() * (w - 2 * r)
                val y = r + rnd.nextFloat() * (h - 2 * r)
                if (x > w - gearZone && y < gearZone) continue
                if (out.any { hypot(it.x - x, it.y - y) < 2 * r + 20f }) continue
                out += Spot(x, y)
            }
            return out
        }
    }
}
