package com.catgame.hunt

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import java.util.Random
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private class Particle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float, val size: Float, val color: Int,
)

private class Ring(
    val x: Float, val y: Float, val maxRadius: Float,
    var life: Float, val maxLife: Float, val color: Int, val width: Float, val alpha: Float,
)

@SuppressLint("ViewConstructor")
class GameView(
    context: Context,
    private val sounds: Sounds,
    private val settings: Settings,
) : View(context) {
    private val d = resources.displayMetrics.density
    private val rnd = Random()
    private val critters = ArrayList<Critter>()
    private val particles = ArrayList<Particle>()
    private val rings = ArrayList<Ring>()

    private var running = false
    private var lastNanos = 0L
    private var ambientIn = 4f
    private var flashAlpha = 0f
    private var flashColor = 0
    private var caught = 0
    private var hintLeft = 6f

    /** While the settings panel is open: nothing moves, touches are ignored. */
    var frozen = false

    private val bgPaint = Paint()
    private val flashPaint = Paint()
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF; textSize = 18 * d }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAAFFFFFF.toInt()
        textSize = 16 * d
        textAlign = Paint.Align.CENTER
    }

    private val sparkColors = intArrayOf(
        0xFFFFEB3B.toInt(), 0xFF00E5FF.toInt(), 0xFFFF4081.toInt(),
        0xFFFFFFFF.toInt(), 0xFFFF9100.toInt(), 0xFF76FF03.toInt(),
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(), 0xFF22343F.toInt(), 0xFF0E171C.toInt(), Shader.TileMode.CLAMP,
        )
        if (critters.isEmpty()) rebuild()
    }

    /** Recreates critters from the current settings; they enter the screen one by one. */
    fun rebuild() {
        val s = d * settings.size
        critters.clear()
        if (settings.mice) critters += Mouse(s, rnd, 0xFFB0B0B0)
        if (settings.roach) critters += Roach(s, rnd)
        if (settings.rope) critters += Rope(s, rnd, 0xFFFF3D3D, 0xFFFFA0A0, 0xFF5A0000)
        if (settings.butterfly) critters += Butterfly(s, rnd, 0xFFFF8F00, 0xFFFFC107)
        if (settings.fish) critters += Fish(s, rnd, 0xFFFF7043, 0xFFFFAB91)
        if (settings.mice) critters += Mouse(s, rnd, 0xFFE8DCC8)
        if (settings.laser) critters += LaserDot(s, rnd)
        critters.forEachIndexed { i, c -> c.respawnIn = 0.5f + i * 2.5f }
    }

    fun resetScore() {
        caught = 0
    }

    fun resume() {
        running = true
        lastNanos = 0L
        sounds.autoResume()
        postInvalidateOnAnimation()
    }

    fun pause() {
        running = false
        sounds.autoPause()
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0f else min(0.05f, (now - lastNanos) / 1e9f)
        lastNanos = now
        if (!frozen) update(dt)
        render(canvas)
        if (running) postInvalidateOnAnimation()
    }

    private fun pan(c: Critter) = (c.x / width * 2 - 1).coerceIn(-1f, 1f)

    private fun update(dt: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        val critterDt = dt * settings.speed
        for (c in critters) {
            if (!c.alive) {
                c.respawnIn -= dt
                if (c.respawnIn <= 0f) {
                    c.spawn(w, h)
                    c.alive = true
                    callOut(c)
                }
                continue
            }
            c.update(critterDt, w, h)
            if (c is Mouse) {
                c.voiceIn -= dt
                if (c.voiceIn <= 0f) {
                    c.voiceIn = 1.5f + rnd.nextFloat() * 4f
                    if (c.onScreen(w, h)) sounds.play(sounds.squeak, 0.9f, pan(c), 0.85f + rnd.nextFloat() * 0.4f)
                }
            }
            if (c is Runner && c.startedDash) {
                when {
                    c is Roach && rnd.nextFloat() < 0.6f -> sounds.play(sounds.rustle, 0.8f, pan(c), 0.9f + rnd.nextFloat() * 0.3f)
                    c is Rope && rnd.nextFloat() < 0.3f -> sounds.play(sounds.rustle, 0.5f, pan(c), 0.7f)
                    c is Fish && rnd.nextFloat() < 0.5f -> sounds.play(sounds.bubble, 0.8f, pan(c), 0.8f + rnd.nextFloat() * 0.4f)
                }
            }
        }

        ambientIn -= dt
        if (ambientIn <= 0f && settings.lure) {
            ambientIn = 6f + rnd.nextFloat() * 8f
            if (rnd.nextBoolean()) sounds.play(sounds.psps, 0.9f) else sounds.play(sounds.chirp, 0.8f, rnd.nextFloat() * 2 - 1)
        }

        val drag = exp(-2.5f * dt)
        for (p in particles) {
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vx *= drag
            p.vy *= drag
            p.life -= dt
        }
        particles.removeAll { it.life <= 0f }
        for (r in rings) r.life -= dt
        rings.removeAll { it.life <= 0f }
        flashAlpha = max(0f, flashAlpha - dt * 3f)
        hintLeft -= dt
    }

    /** Sound when a critter enters the screen, to draw the cat's attention. */
    private fun callOut(c: Critter) {
        when (c) {
            is Mouse -> sounds.play(sounds.squeak, 1f, pan(c))
            is Roach -> sounds.play(sounds.rustle, 0.9f, pan(c))
            is Rope, is Butterfly -> sounds.play(sounds.chirp, 0.7f, pan(c))
            is Fish -> sounds.play(sounds.bubble, 0.9f, pan(c))
        }
    }

    private fun render(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        for (c in critters) if (c.alive) c.draw(canvas)

        for (r in rings) {
            val t = 1f - r.life / r.maxLife
            val eased = 1f - (1f - t) * (1f - t)
            ringPaint.color = r.color
            ringPaint.alpha = (255 * r.alpha * (1f - t)).toInt()
            ringPaint.strokeWidth = r.width * (1f - t * 0.5f)
            canvas.drawCircle(r.x, r.y, r.maxRadius * eased, ringPaint)
        }
        for (p in particles) {
            val k = p.life / p.maxLife
            particlePaint.color = p.color
            particlePaint.alpha = (255 * k).toInt()
            canvas.drawCircle(p.x, p.y, p.size * (0.4f + 0.6f * k), particlePaint)
        }
        if (flashAlpha > 0f) {
            flashPaint.color = flashColor
            flashPaint.alpha = (255 * flashAlpha * 0.8f).toInt()
            canvas.drawRect(0f, 0f, w, h, flashPaint)
        }

        if (settings.showScore) canvas.drawText("🐾 $caught", 16 * d, 30 * d, scorePaint)
        if (hintLeft > 0f) {
            hintPaint.alpha = (170 * min(1f, hintLeft)).toInt()
            canvas.drawText(context.getString(R.string.hint_exit), w / 2, h - 24 * d, hintPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (frozen) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                touch(e.getX(e.actionIndex), e.getY(e.actionIndex), isDown = true)
            MotionEvent.ACTION_MOVE ->
                for (i in 0 until e.pointerCount) touch(e.getX(i), e.getY(i), isDown = false)
        }
        return true
    }

    private fun touch(tx: Float, ty: Float, isDown: Boolean) {
        var hit = false
        for (c in critters) {
            if (c.alive && c.hitTest(tx, ty, 18 * d)) {
                catchIt(c, tx, ty)
                hit = true
            }
        }
        if (!hit && isDown) rings += Ring(tx, ty, 60 * d, 0.35f, 0.35f, 0xFFFFFFFF.toInt(), 3 * d, 0.4f)
    }

    private fun catchIt(c: Critter, tx: Float, ty: Float) {
        c.alive = false
        c.respawnIn = 1.5f + rnd.nextFloat() * 2.5f
        caught++

        repeat(45) {
            val a = rnd.nextFloat() * 2 * Math.PI.toFloat()
            val v = (200 + rnd.nextFloat() * 700) * d
            val life = 0.6f + rnd.nextFloat() * 0.5f
            particles += Particle(
                tx, ty, cos(a) * v, sin(a) * v, life, life,
                (4 + rnd.nextFloat() * 6) * d, sparkColors[rnd.nextInt(sparkColors.size)],
            )
        }
        rings += Ring(tx, ty, 280 * d, 0.6f, 0.6f, 0xFFFFEB3B.toInt(), 12 * d, 1f)
        rings += Ring(tx, ty, 180 * d, 0.45f, 0.45f, 0xFF00E5FF.toInt(), 8 * d, 1f)
        flashColor = sparkColors[rnd.nextInt(sparkColors.size)]
        if (settings.flash) flashAlpha = 0.85f
        sounds.play(sounds.sparkle, 1f, (tx / width * 2 - 1).coerceIn(-1f, 1f))
    }
}
