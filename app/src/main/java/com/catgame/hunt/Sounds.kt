package com.catgame.hunt

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Sounds are synthesized at startup, written to cache as WAV and played through SoundPool. */
class Sounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val loaded = HashSet<Int>()

    val squeak: Int
    val chirp: Int
    val rustle: Int
    val psps: Int
    val sparkle: Int

    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            if (status == 0) synchronized(loaded) { loaded.add(id) }
        }
        val dir = context.cacheDir
        squeak = load(dir, "squeak", Synth.squeak())
        chirp = load(dir, "chirp", Synth.chirp())
        rustle = load(dir, "rustle", Synth.rustle())
        psps = load(dir, "psps", Synth.psps())
        sparkle = load(dir, "sparkle", Synth.sparkle())
    }

    /** [pan] is -1 (left) .. 1 (right). */
    fun play(id: Int, volume: Float = 1f, pan: Float = 0f, rate: Float = 1f) {
        if (synchronized(loaded) { id !in loaded }) return
        val p = pan.coerceIn(-1f, 1f)
        pool.play(id, volume * min(1f, 1f - p), volume * min(1f, 1f + p), 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun autoPause() = pool.autoPause()
    fun autoResume() = pool.autoResume()
    fun release() = pool.release()

    private fun load(dir: File, name: String, pcm: ShortArray): Int {
        val file = File(dir, "$name.wav")
        writeWav(file, pcm)
        return pool.load(file.path, 1)
    }

    private fun writeWav(file: File, pcm: ShortArray) {
        val dataSize = pcm.size * 2
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(Synth.SR).putInt(Synth.SR * 2).putShort(2).putShort(16)
        buf.put("data".toByteArray()).putInt(dataSize)
        for (s in pcm) buf.putShort(s)
        file.writeBytes(buf.array())
    }
}

private object Synth {
    const val SR = 44100
    private val rnd = Random(7)

    private fun len(sec: Double) = (sec * SR).toInt()

    private fun toPcm(buf: FloatArray): ShortArray {
        var peak = 1e-6f
        for (v in buf) peak = max(peak, abs(v))
        val gain = 0.9f / peak
        return ShortArray(buf.size) { (buf[it] * gain * 32767).toInt().coerceIn(-32768, 32767).toShort() }
    }

    /** RBJ band-pass biquad applied in place. */
    private fun bandPass(buf: FloatArray, f0: Double, q: Double) {
        val w0 = 2 * PI * f0 / SR
        val alpha = sin(w0) / (2 * q)
        val a0 = 1 + alpha
        val a1 = -2 * cos(w0)
        val a2 = 1 - alpha
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in buf.indices) {
            val x = buf[i].toDouble()
            val y = (alpha * x - alpha * x2 - a1 * y1 - a2 * y2) / a0
            x2 = x1; x1 = x; y2 = y1; y1 = y
            buf[i] = y.toFloat()
        }
    }

    private fun noise() = rnd.nextFloat() * 2 - 1

    /** Three high-pitched mouse squeaks with vibrato. */
    fun squeak(): ShortArray {
        val out = FloatArray(len(0.55))
        val starts = doubleArrayOf(0.0, 0.17, 0.36)
        val durs = doubleArrayOf(0.11, 0.13, 0.09)
        for (k in starts.indices) {
            val s0 = len(starts[k])
            val n = len(durs[k])
            val f0 = 3600.0 + 300 * k
            var ph = 0.0
            for (i in 0 until n) {
                val t = i.toDouble() / n
                val f = f0 + 1400 * sin(PI * t) + 120 * sin(2 * PI * 45 * i / SR)
                ph += 2 * PI * f / SR
                val env = min(1.0, t * 20) * (1 - t).pow(1.5)
                out[s0 + i] += (env * (sin(ph) + 0.25 * sin(2 * ph))).toFloat()
            }
        }
        return toPcm(out)
    }

    /** Bird-like trill of fast falling chirps. */
    fun chirp(): ShortArray {
        val note = 0.055
        val gap = 0.035
        val count = 5
        val out = FloatArray(len(count * (note + gap) + 0.05))
        for (k in 0 until count) {
            val s0 = len(k * (note + gap))
            val n = len(note)
            var ph = 0.0
            for (i in 0 until n) {
                val t = i.toDouble() / n
                ph += 2 * PI * (6500 - 3300 * t) / SR
                out[s0 + i] += (sin(PI * t).pow(0.7) * sin(ph)).toFloat()
            }
        }
        return toPcm(out)
    }

    /** Scratchy rustle, like tiny feet on paper. */
    fun rustle(): ShortArray {
        val n = len(0.5)
        val out = FloatArray(n) { noise() }
        bandPass(out, 3500.0, 0.8)
        var env = 0f
        for (i in 0 until n) {
            if (rnd.nextFloat() < 0.0025f) env = 0.5f + rnd.nextFloat() * 0.5f
            env *= 0.9975f
            out[i] *= env * sin(PI * i / n).toFloat()
        }
        return toPcm(out)
    }

    /** "Ps-ps-ps" — the classic way to call a cat. */
    fun psps(): ShortArray {
        val burst = 0.16
        val gap = 0.07
        val out = FloatArray(len(3 * (burst + gap)))
        val hiss = FloatArray(out.size) { noise() }
        bandPass(hiss, 6500.0, 1.2)
        val clickLen = len(0.006)
        for (k in 0 until 3) {
            val s0 = len(k * (burst + gap))
            val n = len(burst)
            for (i in 0 until n) {
                val t = i.toDouble() / n
                val click = if (i < clickLen) noise() * 0.5f * (1 - i.toFloat() / clickLen) else 0f
                val env = if (t < 0.08) 0.3 + t / 0.08 * 0.7 else (1 - (t - 0.08) / 0.92).pow(0.8)
                out[s0 + i] += click + (hiss[s0 + i] * env).toFloat()
            }
        }
        return toPcm(out)
    }

    /** Bright rising arpeggio of chimes for a catch. */
    fun sparkle(): ShortArray {
        val out = FloatArray(len(0.75))
        val notes = doubleArrayOf(2093.0, 2637.0, 3136.0, 4186.0, 5274.0)
        for ((k, f) in notes.withIndex()) {
            val s0 = len(k * 0.055)
            for (i in 0 until out.size - s0) {
                val t = i.toDouble() / SR
                val env = exp(-t / 0.11) * min(1.0, t / 0.003)
                out[s0 + i] += (env * (sin(2 * PI * f * t) + 0.3 * sin(2 * PI * f * 2.01 * t)) * 0.6).toFloat()
            }
        }
        return toPcm(out)
    }
}
