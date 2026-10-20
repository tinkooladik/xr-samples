package com.example.helloandroidxr.ui.launch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The colour wash around the mark. visionOS splits it into an in-window "aurora" and ornament "blooms"
 * because window content is clipped; a spatial panel has no such clip, so both sets draw on one panel
 * sized to carry the glow out into the room.
 */
internal object LaunchGlow {
    /** Metres the panel covers: square, since the blooms orbit the mark. */
    const val WIDTH = 3.2f
    const val HEIGHT = 3.2f


    private val red = Color(0.839f, 0.027f, 0.216f)
    private val amber = Color(0.949f, 0.580f, 0.039f)
    private val blue = Color(0.298f, 0.671f, 0.851f)

    /** The visionOS window the wash was measured in: the logo took 55% of it, 1280 points wide. */
    private const val WINDOW = LaunchTimeline.LOGO_WIDTH / 0.55f
    private const val POINT = WINDOW / 1280f

    /** One soft ellipse of the in-window wash, in window widths, y up. */
    private class Aurora(
        val color: Color, val offsetX: Float, val offsetY: Float, val sizeX: Float, val sizeY: Float,
        val falloff: Float, val alpha: Float, val delay: Float, val driftX: Float, val driftY: Float, val phase: Float,
    )

    /** One ornament bloom, in points, y down. */
    private class Bloom(
        val color: Color, val offsetX: Float, val offsetY: Float, val diameter: Float, val opacity: Float,
        val delay: Float, val driftX: Float, val driftY: Float, val phase: Float,
    )

    private val auroras = listOf(
        Aurora(red, -0.165f, -0.011f, 0.594f, 0.550f, 1.9f, 0.62f, 0.15f, 0.028f, 0.022f, 0.00f),
        Aurora(amber, 0.011f, -0.033f, 0.561f, 0.517f, 1.8f, 0.58f, 0.30f, 0.022f, 0.028f, 0.21f),
        Aurora(blue, 0.187f, 0.055f, 0.638f, 0.572f, 1.9f, 0.60f, 0.62f, 0.028f, 0.028f, 0.64f),
    )

    private val blooms = listOf(
        Bloom(red, -373f, -215f, 2050f, 0.7f, 0.15f, 30f, 24f, 0.00f),
        Bloom(amber, 0f, 430f, 1600f, 1f, 0.30f, 24f, 30f, 0.21f),
        Bloom(blue, 373f, -215f, 1590f, 1f, 0.62f, 30f, 30f, 0.64f),
    )

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

    private const val LOBE_COUNT = 3
    private const val LOBE_REACH = 0.20f
    private const val LOBE_SIZE = 0.85f
    private const val LOBE_OPACITY = 0.62f
    private const val LOBE_PHASE_STEP = 0.29f

    private val stops = FloatArray(11) { it / 10f }

    fun DrawScope.drawGlow(time: Float) {
        if (time < 0f) return
        val ppm = size.width / WIDTH
        for (a in auroras) drawAurora(a, time, ppm)
        for (b in blooms) drawBloom(b, time, ppm)
    }

    private fun DrawScope.drawAurora(a: Aurora, t: Float, ppm: Float) {
        val bloom = LaunchTimeline.easyEase(LaunchTimeline.progress(t, a.delay, a.delay + BLOOM_DURATION))
        if (bloom <= 0f) return
        val x = center.x + (a.offsetX + a.driftX * sin(TAU * (t / DRIFT_PERIOD_X + a.phase))) * WINDOW * ppm
        val y = center.y - (a.offsetY + a.driftY * sin(TAU * (t / DRIFT_PERIOD_Y + a.phase * 1.7f))) * WINDOW * ppm
        val swell = 0.80f + 0.20f * bloom
        val wobble = AURORA_WOBBLE_DEPTH * sin(TAU * (t / AURORA_WOBBLE_PERIOD + a.phase))
        val held = t - AURORA_PULSE_START
        val pulse = if (held <= 0f) 1f else {
            1f - AURORA_PULSE_DEPTH * min(1f, held / AURORA_PULSE_PERIOD) * (1f - cos(TAU * (held / AURORA_PULSE_PERIOD + a.phase))) / 2f
        }
        softDisc(
            a.color, x, y,
            radiusX = a.sizeX * WINDOW * ppm / 2 * swell * (1f + wobble),
            radiusY = a.sizeY * WINDOW * ppm / 2 * swell * (1f - wobble),
            alpha = a.alpha * bloom * pulse,
            falloff = a.falloff,
        )
    }

    private fun DrawScope.drawBloom(b: Bloom, t: Float, ppm: Float) {
        val ramp = LaunchTimeline.progress(t, b.delay, b.delay + BLOOM_DURATION)
        val bloom = ramp * ramp * (3f - 2f * ramp)
        if (bloom <= 0f) return
        val held = t - BREATHING_STARTS_AT
        val breathing = if (held <= 0f) 1f else {
            1f - BREATHING_DEPTH * min(1f, held / BREATHING_PERIOD) * (1f - cos(TAU * (held / BREATHING_PERIOD + b.phase))) / 2f
        }
        val orbit = TAU * t / ORBIT_PERIOD
        val homeX = b.offsetX * cos(orbit) - b.offsetY * sin(orbit)
        val homeY = b.offsetX * sin(orbit) + b.offsetY * cos(orbit)
        val x = center.x + (homeX + b.driftX * sin(TAU * (t / DRIFT_PERIOD_X + b.phase))) * POINT * ppm
        val y = center.y + (homeY + b.driftY * sin(TAU * (t / DRIFT_PERIOD_Y + b.phase * 1.7f))) * POINT * ppm
        val radius = b.diameter / 2 * POINT * ppm
        val alpha = b.opacity * bloom * breathing * LOBE_OPACITY
        for (i in 0 until LOBE_COUNT) {
            val phase = b.phase + i * LOBE_PHASE_STEP
            val stretch = WOBBLE_DEPTH * sin(TAU * (t / WOBBLE_PERIOD + phase))
            val angle = i.toFloat() / LOBE_COUNT * TAU
            val reach = radius * LOBE_REACH
            softDisc(
                b.color, x + reach * cos(angle), y + reach * sin(angle),
                radiusX = radius * LOBE_SIZE * (1f + stretch),
                radiusY = radius * LOBE_SIZE * (1f - stretch),
                alpha = alpha,
                falloff = 3f,
                rotationDegrees = 360f * (t / TURN_PERIOD + phase),
            )
        }
    }

    /** A radial `(1 - d)^falloff` feather; a two-stop gradient's straight slope reads as a hard disc. */
    private fun DrawScope.softDisc(
        color: Color, x: Float, y: Float, radiusX: Float, radiusY: Float, alpha: Float, falloff: Float,
        rotationDegrees: Float = 0f,
    ) {
        val brush = Brush.radialGradient(
            colorStops = Array(stops.size) { stops[it] to color.copy(alpha = (1f - stops[it]).pow(falloff)) },
            center = Offset.Zero,
            radius = UNIT,
        )
        withTransform({
            translate(x, y)
            rotate(rotationDegrees, Offset.Zero)
            scale(radiusX / UNIT, radiusY / UNIT, Offset.Zero)
        }) {
            drawCircle(brush, radius = UNIT, center = Offset.Zero, alpha = alpha)
        }
    }

    private const val UNIT = 100f
    private const val TAU = (2 * PI).toFloat()

    // Shaded per panel pixel on purpose: any magnified raster (scaled panel or offscreen layer) shows
    // its texel grid on the headset.
    @Composable
    fun Panel(time: Float) {
        Canvas(Modifier.fillMaxSize()) { drawGlow(time) }
    }
}
