package com.example.helloandroidxr.ui.launch

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

/** The farthest glow disc sits this far behind the logo plane; each nearer one steps [GLOW_STEP] forward. */
private const val GLOW_Z = -0.04f
private const val GLOW_STEP = 0.001f

/** The bar keeps its blended material through the flash, which only that material can show. */
private const val SLAB_FLASH_END = 0.4f

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
                if (fadeStart < 0f && isReady && t >= LaunchTimeline.DURATION) fadeStart = t
                val fade = (if (fadeStart < 0f) min(1f, t / LaunchTimeline.FADE_IN) else 1f - (t - fadeStart) / FADE_OUT).coerceIn(0f, 1f)
                parts.apply(t, fade)
                if (fade <= 0f) break
            }
        } finally {
            player?.release()
        }
        onFinished()
    }

    Subspace { SceneCoreEntity(factory = { root }) }
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

/**
 * One extruded part as two entities moved together: its walls and, in front of them, its face.
 *
 * Impress orders the blended primitives of one mesh unpredictably but sorts separate meshes back to front,
 * and the face is always the part's nearest surface, so split this way the layering holds from any angle.
 */
private class Part(private val walls: GltfModelEntity, private val face: GltfModelEntity) {
    fun apply(t: LaunchTimeline.Transform) {
        walls.apply(t)
        face.apply(t)
    }
}

/**
 * The blended override materials of one entity, worn only while it fades.
 *
 * Blended materials never write depth, so a solid part keeps the glb's own opaque materials (baked with
 * the same values): then the glow discs and anything else drawn later behind it fail the depth test.
 */
private class Overrides(private val entity: GltfModelEntity, private val materials: List<Material>) {
    private var worn = false

    fun wear(on: Boolean) {
        if (on == worn) return
        worn = on
        entity.nodes.forEach { node ->
            materials.forEachIndexed { i, material -> if (on) node.setMaterialOverride(material, i) else node.clearMaterialOverride(i) }
        }
    }
}

private class LaunchScene(
    private val models: List<GltfModel>,
    private val logo: Entity,
    private val slab: Part,
    private val silhouette: Part,
    private val bull: Part,
    private val letters: Part,
    private val sun: GltfModelEntity,
    private val glow: List<GltfModelEntity>,
    private val glowMaterials: List<Pair<Shade, KhronosUnlitMaterial>>,
    private val slabFront: KhronosPbrMaterial,
    private val sunMaterial: KhronosUnlitMaterial,
    private val sunTextures: List<Texture>,
    /** Every override material with its colour, so one opacity can be written through all of them. */
    private val tinted: List<Pair<Shade, Material>>,
    private val slabOverrides: List<Overrides>,
    private val markOverrides: List<Overrides>,
) {
    private var opacity = -1f
    private var sunOpacity = -1f
    private var shownSunFrame = -1

    fun apply(t: Float, fade: Float) {
        setOpacity(fade)
        val fading = fade < 1f
        markOverrides.forEach { it.wear(fading) }
        slabOverrides.forEach { it.wear(fading || t < SLAB_FLASH_END) }
        slab.apply(LaunchTimeline.slab(t))
        slabFront.setEmissiveFactor(SLAB_RED.color * (0.3f + 1.1f * LaunchTimeline.slabFlash(t)))

        val mark = LaunchTimeline.logo(t)
        logo.setScale(mark.scale)
        logo.setEnabled(mark.alpha > 0f)
        silhouette.apply(LaunchTimeline.silhouette(t))
        bull.apply(LaunchTimeline.bull(t))
        letters.apply(LaunchTimeline.letters(t))

        // The glow is scene geometry rather than a panel or surface quad: the system composites those
        // over Impress content whatever their depth, which put the blooms on top of the mark.
        LaunchGlow.discs(t, fade).forEachIndexed { i, disc ->
            glow[i].setEnabled(disc.alpha > 0f)
            if (disc.alpha <= 0f) return@forEachIndexed
            val spin = Quaternion.fromAxisAngle(Vector3(0f, 0f, 1f), disc.rotationDegrees)
            glow[i].setPose(Pose(Vector3(disc.x, disc.y, GLOW_Z + GLOW_STEP * i), spin))
            glow[i].setScale(Vector3(disc.radiusX, disc.radiusY, 1f))
            val (shade, material) = glowMaterials[LaunchGlow.materialOf(i)]
            material.setBaseColorFactor(Vector4(shade.color.x, shade.color.y, shade.color.z, disc.alpha))
        }

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
        glowMaterials.forEach { it.second.close() }
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
            // Materials are created one at a time and before anything else; concurrent creation aborts
            // Impress with an unknown instance id.
            val faceWhite = KhronosUnlitMaterial.create(session, AlphaMode.BLEND)
            val faceRed = KhronosUnlitMaterial.create(session, AlphaMode.BLEND)
            val sideWhite = lit(session, WHITE_LIT)
            val sideRed = lit(session, RED_LIT)
            val slabRed = lit(session, SLAB_RED)
            val slabOrange = lit(session, SLAB_ORANGE)
            val slabYellow = lit(session, SLAB_YELLOW)
            val sunMaterial = KhronosUnlitMaterial.create(session, AlphaMode.BLEND)
            val glowMaterials = LaunchGlow.colors.map { color ->
                Shade(color.red, color.green, color.blue) to KhronosUnlitMaterial.create(session, AlphaMode.BLEND)
            }
            fun model(name: String) = async { GltfModel.create(session, Paths.get("models/launch_$name.glb")) }
            val names = listOf(
                "slab_walls", "slab_face", "silhouette_walls", "silhouette_face",
                "bull_walls", "bull_face", "letters_walls", "letters_face", "sun",
            )
            val models = names.map(::model)
            val aurora = model("aurora")
            val bloom = model("bloom")
            val textures = (0..SUN_FRAME_TEXTURE.max()).map { i ->
                async { Texture.create(session, Paths.get("textures/sun_%02d.png".format(i))) }
            }

            val logo = Entity.create(session, "logo", Pose.Identity, root)
            val entities = models.mapIndexed { i, model ->
                val parent = if (names[i].startsWith("slab")) root else logo
                val pose = if (names[i] == "sun") Pose(Vector3(LaunchTimeline.SUN_X, 0f, LaunchTimeline.SUN_Z)) else Pose.Identity
                GltfModelEntity.create(session, model.await(), pose, parent)
            }
            val glow = List(LaunchGlow.DISC_COUNT) { i ->
                GltfModelEntity.create(session, (if (LaunchGlow.isAurora(i)) aurora else bloom).await(), Pose.Identity, root)
            }
            // Materials in glb primitive order (the slab's walls carry its sides, then bevels).
            val slabOverrides = listOf(
                Overrides(entities[0], listOf(slabOrange, slabYellow)),
                Overrides(entities[1], listOf(slabRed)),
            )
            val markOverrides = listOf(
                Overrides(entities[2], listOf(sideWhite)), Overrides(entities[3], listOf(faceWhite)),
                Overrides(entities[4], listOf(sideRed)), Overrides(entities[5], listOf(faceRed)),
                Overrides(entities[6], listOf(sideWhite)), Overrides(entities[7], listOf(faceWhite)),
            )
            (slabOverrides + markOverrides).forEach { it.wear(true) }
            glow.forEachIndexed { i, disc ->
                disc.nodes.forEach { it.setMaterialOverride(glowMaterials[LaunchGlow.materialOf(i)].second) }
            }

            LaunchScene(
                models = (models + aurora + bloom).map { it.await() },
                logo = logo,
                slab = Part(entities[0], entities[1]),
                silhouette = Part(entities[2], entities[3]),
                bull = Part(entities[4], entities[5]),
                letters = Part(entities[6], entities[7]),
                sun = entities[8],
                glow = glow,
                glowMaterials = glowMaterials,
                slabFront = slabRed,
                sunMaterial = sunMaterial,
                sunTextures = textures.map { it.await() },
                tinted = listOf(
                    WHITE to faceWhite, RED to faceRed,
                    WHITE_LIT to sideWhite, RED_LIT to sideRed,
                    SLAB_RED to slabRed, SLAB_ORANGE to slabOrange, SLAB_YELLOW to slabYellow,
                ),
                slabOverrides = slabOverrides,
                markOverrides = markOverrides,
            ).also { it.apply(0f, 0f) }
        }

        private suspend fun lit(session: Session, shade: Shade) =
            KhronosPbrMaterial.create(session, AlphaMode.BLEND).apply {
                setMetallicFactor(0f)
                setRoughnessFactor(shade.roughness)
                setEmissiveFactor(shade.color * shade.emissive)
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
