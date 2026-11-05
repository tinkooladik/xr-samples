package com.example.helloandroidxr.ui.launch

import kotlin.math.abs

/**
 * The launch animation's keyframes, ported from the visionOS `RBTVLaunchScene` (which keyed them off the
 * After Effects source). Positions are metres in the logo's own space: origin at the logo centre, y up,
 * z towards the viewer.
 */
internal object LaunchTimeline {
    const val DURATION = 2f
    const val FADE_IN = 0.2f

    /** Baked into the glb files by `gen_launch_assets.py`; the extrusion depths below assume it. */
    const val LOGO_WIDTH = 0.6f
    const val SUN_X = 0.0278f * LOGO_WIDTH
    const val SUN_Z = 0.008f
    private const val SUN_FPS = 30f

    private const val SLAB_END = 0.62f
    private const val SETTLE_END = 1.467f

    // The generator centres every part on the logo plane, so a part whose back belongs at `depth`
    // sits at depth + extrusion / 2.
    private const val SILHOUETTE_Z = 0.016f + 0.015f / 2
    private const val BULL_Z = 0.030f + 0.028f / 2
    private const val LETTERS_Z = 0.052f + 0.014f / 2

    class Transform(
        val x: Float = 0f,
        val y: Float = 0f,
        val z: Float = 0f,
        val yawDegrees: Float = 0f,
        val scale: Float = 1f,
        val alpha: Float = 1f,
    )

    /** The striped bar: slides in edge-on, opens, closes and shrinks away into the bull. */
    fun slab(t: Float): Transform {
        if (t < 0.033f || t >= SLAB_END) return Transform(alpha = 0f)
        val slide = bezier(progress(t, 0f, 0.5f), 0.711f, 0f, 0.153f, 1f)
        val approach = easyEase(progress(t, 0f, 0.5f))
        val opening = easyEase(progress(t, 0f, 0.233f))
        val closing = easyEase(progress(t, 0.267f, 0.5f))
        val shrink = easyEase(progress(t, 0.3f, SLAB_END))
        return Transform(
            x = (0.635f - 1.017f * slide + 0.2f * shrink) * LOGO_WIDTH,
            z = 0.17f - 0.10f * approach - 0.07f * shrink,
            yawDegrees = -90f + 90f * opening + 90f * closing,
            scale = 1f - shrink,
        )
    }

    /** The bar's face flares as it opens; 0 to 1, peaking at 0.233 s. */
    fun slabFlash(t: Float) = (1f - abs(t - 0.233f) / 0.1f).coerceAtLeast(0f)

    /** The whole mark: revealed as the bar closes, then settles down from 111%. */
    fun logo(t: Float): Transform {
        val reveal = easyEase(progress(t, 0.233f, 0.267f))
        val settle = bezier(progress(t, 0.967f, SETTLE_END), 0.597f, 0f, 0.016f, 1f)
        return Transform(scale = 1.111f - 0.111f * settle, alpha = reveal)
    }

    fun silhouette(t: Float) = Transform(x = bullSlide(t), z = SILHOUETTE_Z)

    fun bull(t: Float) = Transform(x = bullSlide(t), z = BULL_Z)

    private fun bullSlide(t: Float): Float {
        val slide = bezier(progress(t, 0f, 0.833f), 0.211f, 0f, 0.093f, 1f)
        return (slide - 1f) * 0.2f * LOGO_WIDTH
    }

    /** "TV" arrives from in front of the mark; alpha only gates when it shows. */
    fun letters(t: Float): Transform {
        val arrive = easyEase(progress(t, 0.7f, 0.933f))
        return Transform(z = LETTERS_Z + (1f - arrive) * 0.11f, alpha = arrive)
    }

    fun sunFrame(t: Float, frameCount: Int) = (t * SUN_FPS).toInt().coerceIn(0, frameCount - 1)

    fun progress(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

    /** After Effects' default "easy ease". */
    fun easyEase(p: Float) = bezier(p, 0.167f, 0f, 0.833f, 1f)

    /** Cubic bezier easing with the control points After Effects exports. */
    fun bezier(p: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        if (p <= 0f || p >= 1f) return p
        var u = p
        for (i in 0 until 8) {
            val slope = cubicDerivative(u, x1, x2)
            if (abs(slope) < 1e-6f) break
            u = (u - (cubic(u, x1, x2) - p) / slope).coerceIn(0f, 1f)
        }
        return cubic(u, y1, y2)
    }

    private fun cubic(t: Float, c1: Float, c2: Float): Float {
        val inv = 1f - t
        return 3f * inv * inv * t * c1 + 3f * inv * t * t * c2 + t * t * t
    }

    private fun cubicDerivative(t: Float, c1: Float, c2: Float): Float {
        val inv = 1f - t
        return 3f * inv * inv * c1 + 6f * inv * t * (c2 - c1) + 3f * t * t * (1f - c2)
    }
}
