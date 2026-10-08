package io.github.xtratter.cathunt

import kotlin.math.min

/**
 * A play session that ends gently: after [minutes] the prey slows down (WIND_DOWN), one last critter stands
 * still so the cat gets a final catch (LAST), then the screen fades out (FADE → ENDED). Hunting that never
 * ends in a catch leaves cats over-excited. 0 minutes = no limit. Pure logic, no Android types.
 */
class PlayTimer(minutes: Int) {
    enum class Phase { ACTIVE, WIND_DOWN, LAST, FADE, ENDED }

    var phase = Phase.ACTIVE; private set
    var speedFactor = 1f; private set
    /** 0..1, how dark the screen is. */
    var fade = 0f; private set
    val ended get() = phase == Phase.ENDED

    private var limit = minutes * 60f
    private var t = 0f
    private var phaseT = 0f

    fun restart(minutes: Int) {
        limit = minutes * 60f
        t = 0f; phaseT = 0f; phase = Phase.ACTIVE; speedFactor = 1f; fade = 0f
    }

    fun update(dt: Float) {
        if (limit <= 0f) return
        val d = min(dt, MAX_DT)
        when (phase) {
            Phase.ACTIVE -> { t += d; if (t >= limit) enter(Phase.WIND_DOWN) }
            Phase.WIND_DOWN -> {
                phaseT += d
                speedFactor = 1f - (1f - MIN_SPEED) * min(1f, phaseT / WIND_DOWN_SEC)
                if (phaseT >= WIND_DOWN_SEC) enter(Phase.LAST)
            }
            Phase.LAST -> { phaseT += d; if (phaseT >= LAST_TIMEOUT_SEC) enter(Phase.FADE) }
            Phase.FADE -> {
                phaseT += d
                fade = min(1f, phaseT / FADE_SEC)
                if (phaseT >= FADE_SEC) enter(Phase.ENDED)
            }
            Phase.ENDED -> {}
        }
    }

    /** The cat caught the last critter. */
    fun lastCaught() { if (phase == Phase.LAST) enter(Phase.FADE) }

    private fun enter(p: Phase) {
        phase = p; phaseT = 0f
        if (p == Phase.ENDED) fade = 1f
    }

    companion object {
        const val MIN_SPEED = 0.35f
        const val WIND_DOWN_SEC = 30f
        const val LAST_TIMEOUT_SEC = 60f
        const val FADE_SEC = 3f
        private const val MAX_DT = 0.25f
    }
}
