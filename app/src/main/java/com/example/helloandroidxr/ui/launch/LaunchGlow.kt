package com.example.helloandroidxr.ui.launch

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The colour wash around the mark. visionOS splits it into an in-window "aurora" and ornament "blooms"
 * because window content is clipped; here both are soft discs floating behind the mark, each a unit disc
 * mesh with its falloff baked into vertex alpha, placed and tinted per frame.
 */
internal object LaunchGlow {
    private const val LOBE_COUNT = 3
    const val AURORA_COUNT = 3
    const val DISC_COUNT = AURORA_COUNT + 3 * LOBE_COUNT

    /** Metres, y up; the rotation is about the view axis. Alpha 0 hides the disc. */
    class Disc(val x: Float, val y: Float, val radiusX: Float, val radiusY: Float, val rotationDegrees: Float, val alpha: Float)

    private val red = Color(0.839f, 0.027f, 0.216f)
    private val amber = Color(0.949f, 0.580f, 0.039f)
    private val blue = Color(0.298f, 0.671f, 0.851f)

    /** The visionOS window the wash was measured in: the logo took 55% of it, 1280 points wide. */
    private const val WINDOW = LaunchTimeline.LOGO_WIDTH / 0.55f
    private const val POINT = WINDOW / 1280f

    /** One soft ellipse of the in-window wash, in window widths, y up. */
    private class Aurora(
        val color: Color, val offsetX: Float, val offsetY: Float, val sizeX: Float, val sizeY: Float,
        val alpha: Float, val delay: Float, val driftX: Float, val driftY: Float, val phase: Float,
    )

    /** One ornament bloom, in points, y down. */
    private class Bloom(
        val color: Color, val offsetX: Float, val offsetY: Float, val diameter: Float, val opacity: Float,
        val delay: Float, val driftX: Float, val driftY: Float, val phase: Float,
    )

    private val auroras = listOf(
        Aurora(red, -0.165f, -0.011f, 0.594f, 0.550f, 0.62f, 0.15f, 0.028f, 0.022f, 0.00f),
        Aurora(amber, 0.011f, -0.033f, 0.561f, 0.517f, 0.58f, 0.30f, 0.022f, 0.028f, 0.21f),
        Aurora(blue, 0.187f, 0.055f, 0.638f, 0.572f, 0.60f, 0.62f, 0.028f, 0.028f, 0.64f),
    )

    private val blooms = listOf(
        Bloom(red, -373f, -215f, 2050f, 0.7f, 0.15f, 30f, 24f, 0.00f),
        Bloom(amber, 0f, 430f, 1600f, 1f, 0.30f, 24f, 30f, 0.21f),
        Bloom(blue, 373f, -215f, 1590f, 1f, 0.62f, 30f, 30f, 0.64f),
    )

    /** One colour per material: the auroras, then the blooms, whose lobes share theirs. */
    val colors: List<Color> = auroras.map { it.color } + blooms.map { it.color }

    private const val AURORA_PULSE_START = 1.6f
    private const val AURORA_PULSE_PERIOD = 1.9f
    private const val AURORA_PULSE_DEPTH = 0.34f
    private const val AURORA_WOBBLE_PERIOD = 2.7f
    private const val AURORA_WOBBLE_DEPTH = 0.07f

    private const val BLOOM_DURATION = 0.6f
    private const val BREATHING_STARTS_AT = 0.15f
    private const val BREATHING_PERIOD = 1.1f
    private const val BREATHING_DEPTH = 0.55f
    private const val WOBBLE_DEPTH = 0.22f
    private const val WOBBLE_PERIOD = 1.5f
    private const val TURN_PERIOD = 8f
    private const val ORBIT_PERIOD = 6f
    private const val DRIFT_PERIOD_X = 3.4f
    private const val DRIFT_PERIOD_Y = 4.9f

    private const val LOBE_REACH = 0.20f
    private const val LOBE_SIZE = 0.85f
    private const val LOBE_OPACITY = 0.62f
    private const val LOBE_PHASE_STEP = 0.29f

    fun isAurora(disc: Int) = disc < AURORA_COUNT

    /** Which of [colors] a disc wears. */
    fun materialOf(disc: Int) = if (isAurora(disc)) disc else AURORA_COUNT + (disc - AURORA_COUNT) / LOBE_COUNT

    /** The [DISC_COUNT] discs at [t]: the auroras, then each bloom's lobes. */
    fun discs(t: Float, fade: Float): List<Disc> = auroras.map { aurora(it, t, fade) } + blooms.flatMap { bloom(it, t, fade) }

    private fun aurora(a: Aurora, t: Float, fade: Float): Disc {
        val bloom = LaunchTimeline.easyEase(LaunchTimeline.progress(t, a.delay, a.delay + BLOOM_DURATION))
        val x = (a.offsetX + a.driftX * sin(TAU * (t / DRIFT_PERIOD_X + a.phase))) * WINDOW
        val y = (a.offsetY + a.driftY * sin(TAU * (t / DRIFT_PERIOD_Y + a.phase * 1.7f))) * WINDOW
        val swell = 0.80f + 0.20f * bloom
        val wobble = AURORA_WOBBLE_DEPTH * sin(TAU * (t / AURORA_WOBBLE_PERIOD + a.phase))
        val held = t - AURORA_PULSE_START
        val pulse = if (held <= 0f) 1f else {
            1f - AURORA_PULSE_DEPTH * min(1f, held / AURORA_PULSE_PERIOD) * (1f - cos(TAU * (held / AURORA_PULSE_PERIOD + a.phase))) / 2f
        }
        return Disc(
            x, y,
            radiusX = a.sizeX * WINDOW / 2 * swell * (1f + wobble),
            radiusY = a.sizeY * WINDOW / 2 * swell * (1f - wobble),
            rotationDegrees = 0f,
            alpha = a.alpha * bloom * pulse * fade,
        )
    }

    private fun bloom(b: Bloom, t: Float, fade: Float): List<Disc> {
        val ramp = LaunchTimeline.progress(t, b.delay, b.delay + BLOOM_DURATION)
        val bloom = ramp * ramp * (3f - 2f * ramp)
        val held = t - BREATHING_STARTS_AT
        val breathing = if (held <= 0f) 1f else {
            1f - BREATHING_DEPTH * min(1f, held / BREATHING_PERIOD) * (1f - cos(TAU * (held / BREATHING_PERIOD + b.phase))) / 2f
        }
        val orbit = TAU * t / ORBIT_PERIOD
        val homeX = b.offsetX * cos(orbit) - b.offsetY * sin(orbit)
        val homeY = b.offsetX * sin(orbit) + b.offsetY * cos(orbit)
        val x = (homeX + b.driftX * sin(TAU * (t / DRIFT_PERIOD_X + b.phase))) * POINT
        val y = -(homeY + b.driftY * sin(TAU * (t / DRIFT_PERIOD_Y + b.phase * 1.7f))) * POINT
        val radius = b.diameter / 2 * POINT
        val alpha = b.opacity * bloom * breathing * LOBE_OPACITY * fade
        return List(LOBE_COUNT) { i ->
            val phase = b.phase + i * LOBE_PHASE_STEP
            val stretch = WOBBLE_DEPTH * sin(TAU * (t / WOBBLE_PERIOD + phase))
            val angle = i.toFloat() / LOBE_COUNT * TAU
            val reach = radius * LOBE_REACH
            Disc(
                x + reach * cos(angle), y - reach * sin(angle),
                radiusX = radius * LOBE_SIZE * (1f + stretch),
                radiusY = radius * LOBE_SIZE * (1f - stretch),
                rotationDegrees = 360f * (t / TURN_PERIOD + phase),
                alpha = alpha,
            )
        }
    }

    private const val TAU = (2 * PI).toFloat()
}
