package io.github.xtratter.cathunt

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

internal const val PI_F = PI.toFloat()

internal fun fill(color: Long) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toInt() }

internal fun stroke(color: Long, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color.toInt()
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

/** Anything on screen the cat can catch. Sizes and speeds are in dp, scaled by [d]. */
abstract class Critter(protected val d: Float, protected val rnd: Random) {
    var x = 0f
    var y = 0f
    var alive = false
    var respawnIn = 0f
    var voiceIn = 2f
    /** Variety mode: this critter is being retired and is removed once it is off-screen (or after a while). */
    var leaving = false
    var leaveLeft = 8f
    protected var time = 0f

    abstract val hitRadius: Float
    open fun hitTest(tx: Float, ty: Float, slack: Float) = hypot(tx - x, ty - y) < hitRadius + slack
    fun onScreen(w: Float, h: Float) = x in 0f..w && y in 0f..h

    abstract fun spawn(w: Float, h: Float)
    abstract fun update(dt: Float, w: Float, h: Float)
    abstract fun draw(c: Canvas)
}

/**
 * Stop-and-go movement: dash to a random point, sometimes freeze, sometimes run off-screen
 * and hide for a moment before coming back. Cats love exactly that.
 */
abstract class Runner(d: Float, rnd: Random) : Critter(d, rnd) {
    protected abstract val minSpeed: Float
    protected abstract val maxSpeed: Float
    protected abstract val turnRate: Float
    protected abstract val pauseChance: Float
    protected abstract val pauseMin: Float
    protected abstract val pauseMax: Float
    protected abstract val hideChance: Float
    /** Chance that after hiding off-screen the critter first peeks in at the edge and freezes, like prey checking the coast. */
    protected open val peekChance = 0f

    var heading = 0f
    var speed = 0f
    /** Set for one frame when the critter starts running after a pause or hiding. */
    var startedDash = false

    private var tx = 0f
    private var ty = 0f
    private var targetSpeed = 0f
    private var offTarget = false
    private var peekArrive = false
    private var pauseLeft = 0f
    private var hiddenLeft = 0f
    private var legTime = 0f

    protected val motion get() = (speed / (maxSpeed * d)).coerceIn(0f, 1f)

    override fun spawn(w: Float, h: Float) {
        val m = 70 * d
        when (rnd.nextInt(4)) {
            0 -> { x = -m; y = rnd.nextFloat() * h }
            1 -> { x = w + m; y = rnd.nextFloat() * h }
            2 -> { x = rnd.nextFloat() * w; y = -m }
            else -> { x = rnd.nextFloat() * w; y = h + m }
        }
        time = 0f
        pauseLeft = 0f
        hiddenLeft = 0f
        pickTarget(w, h, allowHide = false)
        heading = atan2(ty - y, tx - x)
        speed = targetSpeed
    }

    private fun pickTarget(w: Float, h: Float, allowHide: Boolean) {
        legTime = 0f
        peekArrive = false
        offTarget = allowHide && rnd.nextFloat() < hideChance
        if (offTarget) {
            val o = 90 * d
            when (rnd.nextInt(4)) {
                0 -> { tx = -o; ty = rnd.nextFloat() * h }
                1 -> { tx = w + o; ty = rnd.nextFloat() * h }
                2 -> { tx = rnd.nextFloat() * w; ty = -o }
                else -> { tx = rnd.nextFloat() * w; ty = h + o }
            }
        } else {
            val m = 60 * d
            tx = m + rnd.nextFloat() * max(1f, w - 2 * m)
            ty = m + rnd.nextFloat() * max(1f, h - 2 * m)
        }
        targetSpeed = (minSpeed + rnd.nextFloat() * (maxSpeed - minSpeed)) * d
    }

    protected open fun wobble(dt: Float) = 0f

    override fun update(dt: Float, w: Float, h: Float) {
        time += dt
        startedDash = false
        if (hiddenLeft > 0f) {
            hiddenLeft -= dt
            if (hiddenLeft <= 0f) {
                if (rnd.nextFloat() < peekChance) {
                    // come back just inside the nearest edge and freeze there
                    val inset = 22 * d
                    tx = if (x < 0f) inset else if (x > w) w - inset else x.coerceIn(inset, w - inset)
                    ty = if (y < 0f) inset else if (y > h) h - inset else y.coerceIn(inset, h - inset)
                    offTarget = false
                    peekArrive = true
                    legTime = 0f
                    targetSpeed = (minSpeed * 0.8f) * d
                } else {
                    pickTarget(w, h, allowHide = false)
                }
                startedDash = true
            }
            return
        }
        val dx = tx - x
        val dy = ty - y
        val dist = hypot(dx, dy)
        if (pauseLeft > 0f) {
            pauseLeft -= dt
            speed = max(0f, speed - 2500 * d * dt)
            if (pauseLeft <= 0f) {
                pickTarget(w, h, allowHide = true)
                startedDash = true
            }
        } else {
            legTime += dt
            if (dist < 26 * d || legTime > 5f) {
                if (peekArrive && dist < 26 * d) {
                    peekArrive = false
                    pauseLeft = 1.2f + rnd.nextFloat() * 1.8f
                    speed = 0f
                    return
                }
                if (offTarget && dist < 26 * d) {
                    hiddenLeft = 0.8f + rnd.nextFloat() * 2.2f
                    speed = 0f
                    return
                }
                if (rnd.nextFloat() < pauseChance) {
                    pauseLeft = pauseMin + rnd.nextFloat() * (pauseMax - pauseMin)
                } else {
                    pickTarget(w, h, allowHide = true)
                }
            }
            var diff = atan2(dy, dx) - heading
            while (diff > PI_F) diff -= 2 * PI_F
            while (diff < -PI_F) diff += 2 * PI_F
            val maxTurn = turnRate * dt
            heading += diff.coerceIn(-maxTurn, maxTurn) + wobble(dt)
            // Slow down near the target and in sharp turns so we don't orbit it.
            val slow = (dist / (80 * d)).coerceIn(0.35f, 1f) * (if (abs(diff) > 1.2f) 0.45f else 1f)
            val accel = 3000 * d * dt
            speed += (targetSpeed * slow - speed).coerceIn(-accel, accel)
        }
        x += cos(heading) * speed * dt
        y += sin(heading) * speed * dt
    }
}

class Mouse(d: Float, rnd: Random, furColor: Long) : Runner(d, rnd) {
    override val minSpeed = 170f
    override val maxSpeed = 480f
    override val turnRate = 6.5f
    override val pauseChance = 0.55f
    override val pauseMin = 0.4f
    override val pauseMax = 2.2f
    override val hideChance = 0.25f
    override val peekChance = 0.5f
    override val hitRadius = 40 * d

    private val fur = fill(furColor)
    private val pink = fill(0xFFF4A6B8)
    private val eye = fill(0xFF111111)
    private val shine = fill(0xFFFFFFFF)
    private val tail = stroke(0xFFE8A0B0, 3.5f * d)
    private val whisker = stroke(0xAAFFFFFF, 1f * d)
    private val path = Path()

    override fun draw(c: Canvas) {
        val s = d
        c.save()
        c.translate(x, y)
        c.rotate(heading * 180f / PI_F)

        val wig = sin(time * 10f) * (0.3f + motion) * 14f * s
        path.reset()
        path.moveTo(-26 * s, 0f)
        path.cubicTo(-45 * s, wig, -60 * s, -wig, -80 * s, wig * 0.5f)
        c.drawPath(path, tail)

        val step = sin(time * 25f) * motion * 5f * s
        c.drawCircle(12 * s + step, -14 * s, 4 * s, pink)
        c.drawCircle(12 * s - step, 14 * s, 4 * s, pink)
        c.drawCircle(-16 * s - step, -14 * s, 4 * s, pink)
        c.drawCircle(-16 * s + step, 14 * s, 4 * s, pink)

        c.drawOval(-30 * s, -16 * s, 22 * s, 16 * s, fur)
        c.drawOval(6 * s, -13 * s, 40 * s, 13 * s, fur)

        for (side in intArrayOf(-1, 1)) {
            c.drawCircle(13 * s, side * 13 * s, 9 * s, fur)
            c.drawCircle(13 * s, side * 13 * s, 5.5f * s, pink)
            c.drawCircle(30 * s, side * 6 * s, 2.6f * s, eye)
            c.drawCircle(30.8f * s, side * 6.6f * s, 0.9f * s, shine)
            c.drawLine(37 * s, side * 2 * s, 52 * s, side * 10 * s, whisker)
            c.drawLine(37 * s, side * 2 * s, 53 * s, side * 3 * s, whisker)
        }
        c.drawCircle(40 * s, 0f, 3 * s, pink)
        c.restore()
    }
}

class Roach(d: Float, rnd: Random) : Runner(d, rnd) {
    override val minSpeed = 250f
    override val maxSpeed = 650f
    override val turnRate = 9f
    override val pauseChance = 0.4f
    override val pauseMin = 0.2f
    override val pauseMax = 1.2f
    override val hideChance = 0.2f
    override val hitRadius = 32 * d

    private val shell = fill(0xFF8A4B26)
    private val head = fill(0xFF3E2010)
    private val seam = stroke(0xFF4A2512, 1.5f * d)
    private val leg = stroke(0xFF5A3018, 2f * d)
    private val antenna = stroke(0xFF6B3A1C, 1.5f * d)
    private val path = Path()

    override fun wobble(dt: Float) = (rnd.nextFloat() - 0.5f) * 9f * dt + sin(time * 11f) * 1.2f * dt

    override fun draw(c: Canvas) {
        val s = d
        c.save()
        c.translate(x, y)
        c.rotate(heading * 180f / PI_F)

        for (side in intArrayOf(-1, 1)) {
            for (k in 0..2) {
                val phase = time * 30f + k * PI_F + (if (side > 0) PI_F else 0f)
                val swing = sin(phase) * 6f * s * (0.2f + motion)
                val bx = (-8 + k * 9) * s
                path.reset()
                path.moveTo(bx, side * 8 * s)
                path.lineTo(bx + swing * 0.5f, side * 17 * s)
                path.lineTo(bx + swing + (k - 1) * 8 * s, side * 25 * s)
                c.drawPath(path, leg)
            }
            val wave = sin(time * 7f + side) * 5f
            path.reset()
            path.moveTo(22 * s, side * 3 * s)
            path.cubicTo(34 * s, side * (6 + wave) * s, 42 * s, side * (14 + wave) * s, 54 * s, side * (8 + wave * 1.5f) * s)
            c.drawPath(path, antenna)
        }

        c.drawOval(-22 * s, -11 * s, 16 * s, 11 * s, shell)
        c.drawLine(-20 * s, 0f, 12 * s, 0f, seam)
        c.drawOval(12 * s, -7 * s, 25 * s, 7 * s, head)
        c.restore()
    }
}

/** A string/rope whose head wanders like a snake and whose body trails behind. */
class Rope(d: Float, rnd: Random, color: Long, stripeColor: Long, outlineColor: Long) : Runner(d, rnd) {
    override val minSpeed = 150f
    override val maxSpeed = 420f
    override val turnRate = 3.5f
    override val pauseChance = 0.35f
    override val pauseMin = 0.3f
    override val pauseMax = 1.4f
    override val hideChance = 0.2f
    override val hitRadius = 26 * d

    private val n = 24
    private val seg = 8 * d
    private val px = FloatArray(n)
    private val py = FloatArray(n)

    private val outline = stroke(outlineColor, 11 * d)
    private val body = stroke(color, 7 * d)
    private val stripe = stroke(stripeColor, 3 * d).apply {
        pathEffect = DashPathEffect(floatArrayOf(6 * d, 7 * d), 0f)
        strokeCap = Paint.Cap.BUTT
    }
    private val knot = fill(color)
    private val tassel = stroke(color, 3 * d)
    private val path = Path()

    override fun wobble(dt: Float) = sin(time * 5f) * 2.2f * dt

    override fun spawn(w: Float, h: Float) {
        super.spawn(w, h)
        px.fill(x)
        py.fill(y)
    }

    override fun update(dt: Float, w: Float, h: Float) {
        super.update(dt, w, h)
        px[0] = x
        py[0] = y
        for (i in 1 until n) {
            val dx = px[i] - px[i - 1]
            val dy = py[i] - py[i - 1]
            val dist = hypot(dx, dy)
            if (dist > seg) {
                px[i] = px[i - 1] + dx / dist * seg
                py[i] = py[i - 1] + dy / dist * seg
            }
        }
    }

    override fun hitTest(tx: Float, ty: Float, slack: Float): Boolean {
        for (i in 0 until n) if (hypot(tx - px[i], ty - py[i]) < hitRadius + slack) return true
        return false
    }

    override fun draw(c: Canvas) {
        path.reset()
        path.moveTo(px[0], py[0])
        for (i in 1 until n - 1) {
            path.quadTo(px[i], py[i], (px[i] + px[i + 1]) / 2, (py[i] + py[i + 1]) / 2)
        }
        path.lineTo(px[n - 1], py[n - 1])
        c.drawPath(path, outline)
        c.drawPath(path, body)
        c.drawPath(path, stripe)
        c.drawCircle(px[0], py[0], 7 * d, knot)

        val ang = atan2(py[n - 1] - py[n - 2], px[n - 1] - px[n - 2])
        for (k in -2..2) {
            val a = ang + k * 0.25f + sin(time * 9f + k) * 0.15f
            c.drawLine(px[n - 1], py[n - 1], px[n - 1] + cos(a) * 16 * d, py[n - 1] + sin(a) * 16 * d, tassel)
        }
    }
}

/** Seen from above: wings flap by squashing their span. Flutters erratically and sometimes lands. */
class Butterfly(d: Float, rnd: Random, foreColor: Long, hindColor: Long) : Runner(d, rnd) {
    override val minSpeed = 90f
    override val maxSpeed = 260f
    override val turnRate = 3f
    override val pauseChance = 0.45f
    override val pauseMin = 0.8f
    override val pauseMax = 2.5f
    override val hideChance = 0.2f
    override val hitRadius = 34 * d

    private val fore = fill(foreColor)
    private val hind = fill(hindColor)
    private val edge = stroke(0xFF1A1A1A, 2f * d)
    private val spot = fill(0xFFFFFFFF)
    private val body = fill(0xFF1A1A1A)
    private val antenna = stroke(0xFF1A1A1A, 1.2f * d)
    private var flapPhase = 0f

    override fun wobble(dt: Float) = sin(time * 3f) * 1.5f * dt + (rnd.nextFloat() - 0.5f) * 4f * dt

    override fun update(dt: Float, w: Float, h: Float) {
        super.update(dt, w, h)
        flapPhase += dt * (3f + 15f * motion)
    }

    override fun draw(c: Canvas) {
        val s = d
        c.save()
        c.translate(x, y)
        c.rotate(heading * 180f / PI_F)
        for (side in intArrayOf(-1, 1)) {
            c.save()
            c.scale(1f, 0.2f + 0.8f * abs(sin(flapPhase)))
            val far = side * 30 * s
            val hindFar = side * 22 * s
            c.drawOval(-18 * s, minOf(0f, hindFar), 4 * s, maxOf(0f, hindFar), hind)
            c.drawOval(-18 * s, minOf(0f, hindFar), 4 * s, maxOf(0f, hindFar), edge)
            c.drawOval(-3 * s, minOf(0f, far), 22 * s, maxOf(0f, far), fore)
            c.drawOval(-3 * s, minOf(0f, far), 22 * s, maxOf(0f, far), edge)
            c.drawCircle(12 * s, side * 22 * s, 3 * s, spot)
            c.drawCircle(-9 * s, side * 15 * s, 2.2f * s, spot)
            c.restore()
        }
        c.drawOval(-16 * s, -3 * s, 16 * s, 3 * s, body)
        for (side in intArrayOf(-1, 1)) {
            c.drawLine(15 * s, 0f, 27 * s, side * 8 * s, antenna)
            c.drawCircle(27 * s, side * 8 * s, 1.8f * s, body)
        }
        c.restore()
    }
}

/** Very fast red dot with sharp stops, a glowing trail and a hand-held tremor. */
class LaserDot(d: Float, rnd: Random) : Runner(d, rnd) {
    override val minSpeed = 300f
    override val maxSpeed = 1100f
    override val turnRate = 20f
    override val pauseChance = 0.6f
    override val pauseMin = 0.15f
    override val pauseMax = 1f
    override val hideChance = 0.1f
    override val hitRadius = 28 * d

    private val trailLen = 10
    private val trailX = FloatArray(trailLen)
    private val trailY = FloatArray(trailLen)
    private val halo = fill(0x55FF1744)
    private val glow = fill(0xAAFF1744)
    private val core = fill(0xFFFF1744)
    private val center = fill(0xFFFFF0F0)
    private val trail = fill(0xFFFF1744)

    override fun spawn(w: Float, h: Float) {
        super.spawn(w, h)
        trailX.fill(x)
        trailY.fill(y)
    }

    override fun update(dt: Float, w: Float, h: Float) {
        super.update(dt, w, h)
        for (i in trailLen - 1 downTo 1) {
            trailX[i] = trailX[i - 1]
            trailY[i] = trailY[i - 1]
        }
        trailX[0] = x
        trailY[0] = y
    }

    override fun draw(c: Canvas) {
        val s = d
        for (i in 1 until trailLen) {
            val k = 1f - i.toFloat() / trailLen
            trail.alpha = (120 * k).toInt()
            c.drawCircle(trailX[i], trailY[i], 5 * s * k, trail)
        }
        val jx = x + sin(time * 37f) * 1.2f * s
        val jy = y + cos(time * 29f) * 1.2f * s
        c.drawCircle(jx, jy, 18 * s, halo)
        c.drawCircle(jx, jy, 10 * s, glow)
        c.drawCircle(jx, jy, 6 * s, core)
        c.drawCircle(jx, jy, 2.5f * s, center)
    }
}

/** Seen from above, like in a pond: swims in waves, wags its tail and leaves bubbles. */
class Fish(d: Float, rnd: Random, bodyColor: Long, finColor: Long) : Runner(d, rnd) {
    override val minSpeed = 110f
    override val maxSpeed = 380f
    override val turnRate = 2.8f
    override val pauseChance = 0.4f
    override val pauseMin = 0.5f
    override val pauseMax = 1.8f
    override val hideChance = 0.15f
    override val hitRadius = 36 * d

    private val body = fill(bodyColor)
    private val fin = fill(finColor)
    private val highlight = fill(0x40FFFFFF)
    private val eyeWhite = fill(0xFFFFFFFF)
    private val pupil = fill(0xFF111111)
    private val bubble = stroke(0x99FFFFFF, 1.5f * d)
    private val path = Path()

    private val bubbleCount = 8
    private val bx = FloatArray(bubbleCount)
    private val by = FloatArray(bubbleCount)
    private val bLife = FloatArray(bubbleCount)
    private var nextBubble = 0
    private var bubbleIn = 0.5f

    override fun wobble(dt: Float) = sin(time * 4f) * 1.3f * dt

    override fun spawn(w: Float, h: Float) {
        super.spawn(w, h)
        bLife.fill(0f)
    }

    override fun update(dt: Float, w: Float, h: Float) {
        super.update(dt, w, h)
        bubbleIn -= dt
        if (bubbleIn <= 0f) {
            bubbleIn = 0.4f + rnd.nextFloat() * 0.8f
            bx[nextBubble] = x + cos(heading) * 22 * d
            by[nextBubble] = y + sin(heading) * 22 * d
            bLife[nextBubble] = 1.5f
            nextBubble = (nextBubble + 1) % bubbleCount
        }
        for (i in 0 until bubbleCount) {
            if (bLife[i] <= 0f) continue
            bLife[i] -= dt
            by[i] -= 30 * d * dt
            bx[i] += sin(time * 6f + i) * 12 * d * dt
        }
    }

    override fun draw(c: Canvas) {
        val s = d
        for (i in 0 until bubbleCount) {
            if (bLife[i] <= 0f) continue
            bubble.alpha = (153 * (bLife[i] / 1.5f)).toInt()
            c.drawCircle(bx[i], by[i], (2 + (1.5f - bLife[i]) * 3) * s, bubble)
        }

        c.save()
        c.translate(x, y)
        c.rotate(heading * 180f / PI_F)

        val sway = sin(time * (4f + 10f * motion)) * 25f
        c.save()
        c.translate(-20 * s, 0f)
        c.rotate(sway)
        path.reset()
        path.moveTo(0f, 0f)
        path.quadTo(-14 * s, -4 * s, -24 * s, -15 * s)
        path.lineTo(-18 * s, 0f)
        path.lineTo(-24 * s, 15 * s)
        path.quadTo(-14 * s, 4 * s, 0f, 0f)
        c.drawPath(path, fin)
        c.restore()

        for (side in intArrayOf(-1, 1)) {
            c.save()
            c.translate(6 * s, side * 10 * s)
            c.rotate(side * (35f + sin(time * 8f) * 20f))
            c.drawOval(-11 * s, -4 * s, 3 * s, 4 * s, fin)
            c.restore()
        }

        c.drawOval(-24 * s, -12 * s, 26 * s, 12 * s, body)
        c.drawOval(-14 * s, -4 * s, 14 * s, 4 * s, highlight)
        for (side in intArrayOf(-1, 1)) {
            c.drawCircle(15 * s, side * 7 * s, 3.2f * s, eyeWhite)
            c.drawCircle(16 * s, side * 7 * s, 1.8f * s, pupil)
        }
        c.restore()
    }
}
