package io.github.xtratter.cathunt

import android.graphics.Canvas
import android.graphics.Path
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Newer critters. Their colours lean on what cats see best: blues and yellow-greens.

/** A small blue bird seen from above: hops in short flights, flaps its wings, sometimes lands and freezes. */
class Bird(d: Float, rnd: Random, bodyColor: Long, wingColor: Long) : Runner(d, rnd) {
    override val minSpeed = 200f
    override val maxSpeed = 520f
    override val turnRate = 4.5f
    override val pauseChance = 0.4f
    override val pauseMin = 0.4f
    override val pauseMax = 1.6f
    override val hideChance = 0.3f
    override val peekChance = 0.4f
    override val hitRadius = 38 * d

    private val body = fill(bodyColor)
    private val wing = fill(wingColor)
    private val belly = fill(0xFFE3F2FD)
    private val beak = fill(0xFFFFCA28)
    private val eye = fill(0xFF111111)
    private val path = Path()
    private var flapPhase = 0f

    override fun update(dt: Float, w: Float, h: Float) {
        super.update(dt, w, h)
        flapPhase += dt * (5f + 26f * motion)
    }

    override fun draw(c: Canvas) {
        val s = d
        c.save()
        c.translate(x, y)
        c.rotate(heading * 180f / PI_F)
        // tail fan
        path.reset()
        path.moveTo(-12 * s, 0f)
        path.lineTo(-34 * s, -8 * s)
        path.lineTo(-30 * s, 0f)
        path.lineTo(-34 * s, 8 * s)
        path.close()
        c.drawPath(path, wing)
        // wings
        val flap = 0.3f + 0.7f * abs(sin(flapPhase))
        for (side in intArrayOf(-1, 1)) {
            c.save()
            c.scale(1f, flap)
            c.drawOval(-14 * s, minOf(0f, side * 34 * s), 12 * s, maxOf(0f, side * 34 * s), wing)
            c.restore()
        }
        c.drawOval(-16 * s, -9 * s, 14 * s, 9 * s, body)
        c.drawOval(-8 * s, -5 * s, 10 * s, 5 * s, belly)
        c.drawCircle(15 * s, 0f, 7.5f * s, body)
        path.reset()
        path.moveTo(21 * s, -3 * s)
        path.lineTo(30 * s, 0f)
        path.lineTo(21 * s, 3 * s)
        path.close()
        c.drawPath(path, beak)
        for (side in intArrayOf(-1, 1)) c.drawCircle(17 * s, side * 4 * s, 1.6f * s, eye)
        c.restore()
    }
}

/** A fly: darts in tight zigzags, buzzes, hovers for a moment. */
class Fly(d: Float, rnd: Random) : Runner(d, rnd) {
    override val minSpeed = 300f
    override val maxSpeed = 720f
    override val turnRate = 12f
    override val pauseChance = 0.35f
    override val pauseMin = 0.12f
    override val pauseMax = 0.5f
    override val hideChance = 0.15f
    override val hitRadius = 32 * d

    private val body = fill(0xFF2B2B38)
    private val shell = fill(0xFF4E5A78)
    private val eye = fill(0xFFE53935)
    private val wing = fill(0x66B3E5FC)
    private val wingEdge = stroke(0x88FFFFFF, 1f * d)
    private val leg = stroke(0xFF1A1A22, 1.2f * d)

    override fun wobble(dt: Float) = (rnd.nextFloat() - 0.5f) * 36f * dt

    override fun draw(c: Canvas) {
        val s = d
        c.save()
        c.translate(x + (rnd.nextFloat() - 0.5f) * 1.6f * s, y + (rnd.nextFloat() - 0.5f) * 1.6f * s)
        c.rotate(heading * 180f / PI_F)
        for (side in intArrayOf(-1, 1)) {
            for (i in -1..1) c.drawLine(i * 4 * s, side * 3 * s, i * 6 * s - 3 * s, side * 11 * s, leg)
        }
        val buzz = 0.45f + 0.55f * abs(sin(time * 90f))
        for (side in intArrayOf(-1, 1)) {
            c.save()
            c.scale(1f, buzz)
            c.drawOval(-12 * s, minOf(0f, side * 24 * s), 10 * s, maxOf(0f, side * 24 * s), wing)
            c.drawOval(-12 * s, minOf(0f, side * 24 * s), 10 * s, maxOf(0f, side * 24 * s), wingEdge)
            c.restore()
        }
        c.drawOval(-16 * s, -6 * s, 0f, 6 * s, shell)
        c.drawOval(-6 * s, -6.5f * s, 10 * s, 6.5f * s, body)
        c.drawCircle(11 * s, 0f, 5 * s, body)
        for (side in intArrayOf(-1, 1)) c.drawCircle(13 * s, side * 3.4f * s, 2.8f * s, eye)
        c.restore()
    }
}

/** A golden ladybug: slow, stops often, little legs. Yellow instead of red — cats see it better. */
class Ladybug(d: Float, rnd: Random) : Runner(d, rnd) {
    override val minSpeed = 45f
    override val maxSpeed = 130f
    override val turnRate = 4f
    override val pauseChance = 0.6f
    override val pauseMin = 1f
    override val pauseMax = 3.5f
    override val hideChance = 0.1f
    override val hitRadius = 36 * d

    private val shell = fill(0xFFFFC400)
    private val spots = fill(0xFF33291A)
    private val head = fill(0xFF1E1810)
    private val seam = stroke(0xFF33291A, 1.6f * d)
    private val leg = stroke(0xFF1E1810, 1.6f * d)
    private val shine = fill(0x66FFFFFF)

    override fun wobble(dt: Float) = sin(time * 2f) * 0.8f * dt

    override fun draw(c: Canvas) {
        val s = d
        c.save()
        c.translate(x, y)
        c.rotate(heading * 180f / PI_F)
        val step = sin(time * 22f) * motion * 4f * s
        for (side in intArrayOf(-1, 1)) for (i in -1..1) {
            val sx = i * 7 * s
            c.drawLine(sx, side * 8 * s, sx + step * i, side * 17 * s, leg)
        }
        c.drawCircle(15 * s, 0f, 6.5f * s, head)
        c.drawOval(-16 * s, -13 * s, 14 * s, 13 * s, shell)
        c.drawLine(-16 * s, 0f, 12 * s, 0f, seam)
        for ((px, py) in listOf(-6f to -6f, -6f to 6f, 3f to -7f, 3f to 7f, -11f to 0f)) c.drawCircle(px * s, py * s, 2.4f * s, spots)
        c.drawOval(-8 * s, -10 * s, 4 * s, -5 * s, shine)
        c.restore()
    }
}

/** A lizard: freezes for long, then a lightning dash; the tail wags behind. */
class Lizard(d: Float, rnd: Random) : Runner(d, rnd) {
    override val minSpeed = 170f
    override val maxSpeed = 680f
    override val turnRate = 5.5f
    override val pauseChance = 0.75f
    override val pauseMin = 0.8f
    override val pauseMax = 3f
    override val hideChance = 0.25f
    override val peekChance = 0.5f
    override val hitRadius = 44 * d

    private val body = fill(0xFF9CCC65)
    private val back = fill(0xFF689F38)
    private val tailPaint = stroke(0xFF9CCC65, 9f * d)
    private val tailTip = stroke(0xFF9CCC65, 4f * d)
    private val leg = stroke(0xFF7CB342, 4f * d)
    private val eye = fill(0xFF111111)
    private val path = Path()

    override fun draw(c: Canvas) {
        val s = d
        c.save()
        c.translate(x, y)
        c.rotate(heading * 180f / PI_F)
        val wig = sin(time * (4f + 10f * motion)) * (6f + 18f * motion) * s
        path.reset()
        path.moveTo(-20 * s, 0f)
        path.cubicTo(-42 * s, wig, -62 * s, -wig, -90 * s, wig * 0.6f)
        c.drawPath(path, tailPaint)
        path.reset()
        path.moveTo(-70 * s, -wig * 0.3f)
        path.cubicTo(-80 * s, wig * 0.2f, -86 * s, 0f, -96 * s, wig * 0.7f)
        c.drawPath(path, tailTip)
        val step = sin(time * 20f) * motion * 9f * s
        for (side in intArrayOf(-1, 1)) {
            c.drawLine(12 * s, side * 6 * s, 18 * s + step * side, side * 18 * s, leg)
            c.drawLine(-12 * s, side * 6 * s, -16 * s - step * side, side * 18 * s, leg)
        }
        c.drawOval(-24 * s, -9 * s, 16 * s, 9 * s, body)
        c.drawOval(-16 * s, -4 * s, 10 * s, 4 * s, back)
        c.drawOval(12 * s, -7 * s, 34 * s, 7 * s, body)
        for (side in intArrayOf(-1, 1)) c.drawCircle(26 * s, side * 5 * s, 2.2f * s, eye)
        c.restore()
    }
}

/** A glowing firefly: drifts slowly and blinks; completely silent. */
class Firefly(d: Float, rnd: Random) : Runner(d, rnd) {
    override val minSpeed = 40f
    override val maxSpeed = 140f
    override val turnRate = 2.5f
    override val pauseChance = 0.3f
    override val pauseMin = 0.5f
    override val pauseMax = 2f
    override val hideChance = 0.1f
    override val hitRadius = 42 * d

    private val body = fill(0xFF2E3B2E)
    private val glow = fill(0xFFE6FF59)
    private val halo = fill(0xFFE6FF59)
    private val wing = fill(0x44CFE8FF)
    private val phase = rnd.nextFloat() * 6f

    override fun wobble(dt: Float) = sin(time * 1.7f) * 1.2f * dt

    override fun draw(c: Canvas) {
        val s = d
        val blink = 0.15f + 0.85f * (0.5f + 0.5f * sin(time * 2.6f + phase)).let { it * it }
        c.save()
        c.translate(x, y)
        c.rotate(heading * 180f / PI_F)
        halo.alpha = (28 * blink).toInt(); c.drawCircle(-12 * s, 0f, 40 * s, halo)
        halo.alpha = (60 * blink).toInt(); c.drawCircle(-12 * s, 0f, 24 * s, halo)
        halo.alpha = (120 * blink).toInt(); c.drawCircle(-12 * s, 0f, 13 * s, halo)
        val flap = 0.4f + 0.6f * abs(sin(time * 40f))
        for (side in intArrayOf(-1, 1)) {
            c.save(); c.scale(1f, flap)
            c.drawOval(-10 * s, minOf(0f, side * 16 * s), 8 * s, maxOf(0f, side * 16 * s), wing)
            c.restore()
        }
        c.drawOval(-8 * s, -5 * s, 10 * s, 5 * s, body)
        c.drawCircle(11 * s, 0f, 3.5f * s, body)
        glow.alpha = (255 * (0.35f + 0.65f * blink)).toInt()
        c.drawCircle(-10 * s, 0f, 5.5f * s, glow)
        c.restore()
    }
}
