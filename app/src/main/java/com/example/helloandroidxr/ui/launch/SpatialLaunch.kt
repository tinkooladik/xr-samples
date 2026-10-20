package com.example.helloandroidxr.ui.launch

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SceneCoreEntity
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.draw.alpha
import androidx.xr.compose.subspace.draw.scale
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.offset
import androidx.xr.compose.subspace.layout.width
import androidx.xr.compose.unit.Meter
import androidx.xr.runtime.Session
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.runtime.math.Vector4
import androidx.xr.scenecore.AlphaMode
import androidx.xr.scenecore.Entity
import androidx.xr.scenecore.GltfModel
import androidx.xr.scenecore.GltfModelEntity
import androidx.xr.scenecore.KhronosPbrMaterial
import androidx.xr.scenecore.KhronosUnlitMaterial
import androidx.xr.scenecore.Material
import androidx.xr.scenecore.Texture
import androidx.xr.scenecore.scene
import com.example.helloandroidxr.R
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.nio.file.Paths
import kotlin.math.min
import kotlin.math.pow

/** Glow panel sits this far behind the logo plane. */
private const val GLOW_Z = -0.04f
private const val START_DELAY_MS = 300L
private const val FADE_OUT = 0.28f

/** Which unwrapped sun texture each of the 61 authored frames shows; -1 while the swoosh is still blank. */
private val SUN_FRAME_TEXTURE = IntArray(61) { if (it < 13) -1 else it - 13 }

/**
 * The Red Bull TV launch animation as real 3D parts floating in Full Space, with the brand glow behind.
 *
 * Plays once from the first frame after the assets load; after [LaunchTimeline.DURATION] the mark holds and
 * the glow keeps breathing until [ready], then everything fades out and [onFinished] fires.
 */
@Composable
fun SpatialLaunch(ready: Boolean, onFinished: () -> Unit) {
    val session = LocalSession.current ?: return
    LaunchedEffect(session) { session.scene.requestFullSpaceMode() }
    if (!LocalSpatialCapabilities.current.isSpatialUiEnabled) return

    val context = LocalContext.current
    val isReady by rememberUpdatedState(ready)
    val root = remember(session) { Entity.create(session, "launch") }
    var scene by remember { mutableStateOf<LaunchScene?>(null) }
    var time by remember { mutableFloatStateOf(-1f) }
    var fade by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(session) { scene = LaunchScene.load(session, root) }
    DisposableEffect(root) { onDispose { scene?.close(); root.dispose() } }

    LaunchedEffect(scene) {
        val parts = scene ?: return@LaunchedEffect
        delay(START_DELAY_MS)
        val player = MediaPlayer.create(context, R.raw.rbtv_launch_sound)
        try {
            val start = withFrameNanos { it }
            player?.start()
            var fadeStart = -1f
            while (true) {
                val t = (withFrameNanos { it } - start) / 1e9f
                time = t
                if (fadeStart < 0f && isReady && t >= LaunchTimeline.DURATION) fadeStart = t
                fade = if (fadeStart < 0f) min(1f, t / LaunchTimeline.FADE_IN) else 1f - (t - fadeStart) / FADE_OUT
                parts.apply(t, fade.coerceIn(0f, 1f))
                if (fade <= 0f) break
            }
        } finally {
            player?.release()
        }
        onFinished()
    }

    Subspace {
        SpatialPanel(
            SubspaceModifier
                .width(Meter(LaunchGlow.WIDTH / LaunchGlow.PANEL_SCALE).toDp())
                .height(Meter(LaunchGlow.HEIGHT / LaunchGlow.PANEL_SCALE).toDp())
                .offset(z = Meter(GLOW_Z).toDp())
                .scale(LaunchGlow.PANEL_SCALE)
                .alpha(fade.coerceIn(0f, 1f)),
        ) {
            LaunchGlowPanel(time)
        }
        SceneCoreEntity(factory = { root })
    }
}

/** A glb material re-created at runtime so its opacity can be driven per frame; sRGB colour as authored. */
private class Shade(red: Float, green: Float, blue: Float, val roughness: Float = 0f, val emissive: Float = 0f) {
    val color = Vector3(linear(red), linear(green), linear(blue))

    private fun linear(c: Float) = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
}

private val WHITE = Shade(1f, 1f, 1f)
private val WHITE_LIT = Shade(1f, 1f, 1f, roughness = 0.4f, emissive = 0.5f)
private val RED = Shade(0.8588f, 0.0392f, 0.251f)
private val RED_LIT = Shade(0.8588f, 0.0392f, 0.251f, roughness = 0.26f, emissive = 0.22f)
private val SLAB_RED = Shade(0.86f, 0.04f, 0.25f, roughness = 0.3f, emissive = 0.3f)
private val SLAB_ORANGE = Shade(0.95f, 0.58f, 0.04f, roughness = 0.3f)
private val SLAB_YELLOW = Shade(0.98f, 0.84f, 0.04f, roughness = 0.3f)

private class LaunchScene(
    private val models: List<GltfModel>,
    private val logo: Entity,
    private val slab: Entity,
    private val silhouette: Entity,
    private val bull: Entity,
    private val letters: Entity,
    private val sun: GltfModelEntity,
    private val slabFront: KhronosPbrMaterial,
    private val sunMaterial: KhronosUnlitMaterial,
    private val sunTextures: List<Texture>,
    /** Every override material with its colour, so one opacity can be written through all of them. */
    private val tinted: List<Pair<Shade, Material>>,
) {
    private var opacity = -1f
    private var sunOpacity = -1f
    private var shownSunFrame = -1

    fun apply(t: Float, fade: Float) {
        setOpacity(fade)
        slab.apply(LaunchTimeline.slab(t))
        slabFront.setEmissiveFactor(SLAB_RED.color * (0.3f + 1.1f * LaunchTimeline.slabFlash(t)))

        val mark = LaunchTimeline.logo(t)
        logo.setScale(mark.scale)
        logo.setEnabled(mark.alpha > 0f)
        silhouette.apply(LaunchTimeline.silhouette(t))
        bull.apply(LaunchTimeline.bull(t))
        letters.apply(LaunchTimeline.letters(t))

        val frame = LaunchTimeline.sunFrame(t, SUN_FRAME_TEXTURE.size)
        val texture = SUN_FRAME_TEXTURE[frame]
        if (texture >= 0 && (frame != shownSunFrame || fade != sunOpacity)) {
            // Impress drops the unlit material's texture when its colour factor is written, so the
            // factor always goes first and the frame's texture is re-applied after it.
            sunMaterial.setBaseColorFactor(Vector4(1f, 1f, 1f, fade))
            sunMaterial.setBaseColorTexture(sunTextures[texture])
            sun.nodes.forEach { it.setMaterialOverride(sunMaterial) }
            sunOpacity = fade
        }
        shownSunFrame = frame
        sun.setEnabled(texture >= 0)
        // The textured unlit material ignores the factor's alpha, so the sun leaves by shrinking instead.
        sun.setScale(fade.coerceAtLeast(0.001f))
    }

    private fun setOpacity(value: Float) {
        if (value == opacity) return
        opacity = value
        for ((shade, material) in tinted) {
            val tint = Vector4(shade.color.x, shade.color.y, shade.color.z, value)
            when (material) {
                is KhronosUnlitMaterial -> material.setBaseColorFactor(tint)
                is KhronosPbrMaterial -> material.setBaseColorFactor(tint)
            }
        }
    }

    fun close() {
        sunTextures.forEach { it.close() }
        sunMaterial.close()
        tinted.forEach { (_, material) ->
            when (material) {
                is KhronosUnlitMaterial -> material.close()
                is KhronosPbrMaterial -> material.close()
            }
        }
        models.forEach { it.close() }
    }

    companion object {
        suspend fun load(session: Session, root: Entity): LaunchScene = coroutineScope {
            val faceWhite = async { KhronosUnlitMaterial.create(session, AlphaMode.BLEND) }
            val faceRed = async { KhronosUnlitMaterial.create(session, AlphaMode.BLEND) }
            val sideWhite = async { lit(session, WHITE_LIT) }
            val sideRed = async { lit(session, RED_LIT) }
            val slabRed = async { lit(session, SLAB_RED) }
            val slabOrange = async { lit(session, SLAB_ORANGE) }
            val slabYellow = async { lit(session, SLAB_YELLOW) }
            val sunMaterial = async { KhronosUnlitMaterial.create(session, AlphaMode.BLEND) }
            val models = listOf("slab", "silhouette", "bull", "letters", "sun").map { name ->
                async { GltfModel.create(session, Paths.get("models/launch_$name.glb")) }
            }
            val textures = (0..SUN_FRAME_TEXTURE.max()).map { i ->
                async { Texture.create(session, Paths.get("textures/sun_%02d.png".format(i))) }
            }

            val logo = Entity.create(session, "logo", Pose.Identity, root)
            val (slab, silhouette, bull, letters, sun) = models.mapIndexed { i, model ->
                val parent = if (i == 0) root else logo
                val pose = if (i == 4) Pose(Vector3(LaunchTimeline.SUN_X, 0f, LaunchTimeline.SUN_Z)) else Pose.Identity
                GltfModelEntity.create(session, model.await(), pose, parent)
            }
            slab.override(slabRed.await(), slabOrange.await(), slabYellow.await())
            silhouette.override(faceWhite.await(), sideWhite.await())
            bull.override(faceRed.await(), sideRed.await())
            letters.override(faceWhite.await(), sideWhite.await())

            LaunchScene(
                models = models.map { it.await() },
                logo = logo,
                slab = slab, silhouette = silhouette, bull = bull, letters = letters, sun = sun,
                slabFront = slabRed.await(),
                sunMaterial = sunMaterial.await(),
                sunTextures = textures.map { it.await() },
                tinted = listOf(
                    WHITE to faceWhite.await(), RED to faceRed.await(),
                    WHITE_LIT to sideWhite.await(), RED_LIT to sideRed.await(),
                    SLAB_RED to slabRed.await(), SLAB_ORANGE to slabOrange.await(), SLAB_YELLOW to slabYellow.await(),
                ),
            ).also { it.apply(0f, 0f) }
        }

        private suspend fun lit(session: Session, shade: Shade) =
            KhronosPbrMaterial.create(session, AlphaMode.BLEND).apply {
                setMetallicFactor(0f)
                setRoughnessFactor(shade.roughness)
                setEmissiveFactor(shade.color * shade.emissive)
            }

        /** Materials in glb primitive order: faces, sides, then the slab's bevel. */
        private fun GltfModelEntity.override(vararg materials: Material) {
            nodes.forEach { node -> materials.forEachIndexed { index, material -> node.setMaterialOverride(material, index) } }
        }
    }
}

// Node alpha is not applied to glTF content on every compositor (the XR emulator ignores it), so
// anything fully transparent is hidden outright and the fades run through the materials instead.
private fun Entity.apply(t: LaunchTimeline.Transform) {
    setPose(Pose(Vector3(t.x, t.y, t.z), Quaternion.fromAxisAngle(Vector3.Up, t.yawDegrees)))
    setScale(t.scale.coerceAtLeast(0.001f))
    setEnabled(t.alpha > 0f)
}
