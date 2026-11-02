package com.example.helloandroidxr.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.setOutline
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.spatial.ContentEdge
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.OrbiterOffsetType
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialCurvedRow
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.layout.SpatialArrangement
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.size
import androidx.xr.glimmer.Button
import androidx.xr.glimmer.Card
import androidx.xr.glimmer.Colors
import androidx.xr.glimmer.GlimmerTheme
import androidx.xr.glimmer.ImageCard
import androidx.xr.glimmer.ListItem
import androidx.xr.glimmer.SurfaceDepthEffect
import androidx.xr.glimmer.Text
import androidx.xr.glimmer.ToggleButton
import androidx.xr.glimmer.list.GlimmerLazyColumn
import androidx.xr.glimmer.list.items
import androidx.xr.glimmer.surface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import com.example.helloandroidxr.ui.components.EnvironmentControls

enum class Skin(val label: String) {
    Material("Material"),
    Glimmer("Glimmer"),
    Clear("Clear"),
    Glass("Glass"),
    MaterialGlass("M3 glass"),
}

/** [CurvedWingsDemo]'s layout with a skin switch, to compare Material and Glimmer in the headset. */
@Composable
fun GlimmerWingsDemo() {
    if (!LocalSpatialCapabilities.current.isSpatialUiEnabled) {
        CurvedWingsDemo()
        return
    }
    var current by remember { mutableIntStateOf(0) }
    var skin by remember { mutableStateOf(Skin.Glimmer) }
    val previous = (current - 1).mod(shows.size)
    val next = (current + 1).mod(shows.size)

    val colors = when (skin) {
        Skin.Clear -> ClearColors
        Skin.Glass -> GlassColors
        else -> Colors()
    }
    GlimmerTheme(colors = colors) {
        Subspace {
            SpatialCurvedRow(
                curveRadius = 1000.dp,
                horizontalArrangement = SpatialArrangement.spacedBy(24.dp),
            ) {
                SpatialPanel(SubspaceModifier.size(width = 320.dp, height = 420.dp)) {
                    Wing(skin, shows[previous], onClick = { current = previous })
                }
                SpatialPanel(SubspaceModifier.size(width = 900.dp, height = 500.dp)) {
                    when (skin) {
                        Skin.Material -> HeroCard(shows[current], index = current)
                        Skin.Clear -> ClearHero(shows[current])
                        Skin.MaterialGlass -> MaterialGlassHero(shows[current])
                        else -> GlimmerHero(shows[current], glass = skin == Skin.Glass)
                    }
                    Orbiter(position = ContentEdge.Top, offset = 16.dp, alignment = Alignment.End) {
                        EnvironmentControls()
                    }
                    Orbiter(position = ContentEdge.Bottom, offset = 16.dp, offsetType = OrbiterOffsetType.InnerEdge) {
                        SkinPicker(skin, onPick = { skin = it })
                    }
                }
                SpatialPanel(SubspaceModifier.size(width = 320.dp, height = 420.dp)) {
                    Wing(skin, shows[next], onClick = { current = next })
                }
            }
        }
    }
}

@Composable
private fun Wing(skin: Skin, show: Show, onClick: () -> Unit) {
    when (skin) {
        Skin.Material -> WingCard(show, onClick)
        Skin.Clear -> ClearWing(show, onClick)
        Skin.MaterialGlass -> MaterialGlassWing(show, onClick)
        else -> GlimmerWing(show, onClick, glass = skin == Skin.Glass)
    }
}

@Composable
private fun GlimmerWing(show: Show, onClick: () -> Unit, glass: Boolean) {
    val shape = GlimmerTheme.shapes.medium
    Backdrop(show) { backdrop ->
        Card(
            onClick = onClick,
            title = { Text(show.title) },
            shape = shape,
            interactionSource = rememberHoverLitSource(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .frosted(backdrop.takeIf { glass }, shape),
        ) {
            Text(show.meta)
        }
    }
}

@Composable
private fun GlimmerHero(show: Show, glass: Boolean) {
    val shape = GlimmerTheme.shapes.medium
    Backdrop(show) { backdrop ->
        val frost = backdrop.takeIf { glass }
        Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(
                Modifier
                    .weight(1.2f)
                    .align(Alignment.Bottom)
                    .frosted(frost, shape)
                    .surface(
                        shape = shape,
                        depthEffect = if (glass) null else SurfaceDepthEffect(GlimmerTheme.depthEffectLevels.level3, null),
                    )
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShowInfo(show)
            }
            Episodes(Modifier.weight(1f).fillMaxHeight().frosted(frost, shape))
        }
    }
}

@Composable
private fun ClearWing(show: Show, onClick: () -> Unit) {
    ImageCard(
        onClick = onClick,
        image = { Box(Modifier.fillMaxSize().background(Brush.linearGradient(show.colors))) },
        title = { Text(show.title) },
        shape = PanelShape,
        interactionSource = rememberHoverLitSource(),
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(show.meta)
    }
}

@Composable
private fun ClearHero(show: Show) {
    Row(
        Modifier
            .fillMaxSize()
            .surface(shape = PanelShape, color = GlimmerTheme.colors.background)
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(GlimmerTheme.shapes.small)
                    .background(Brush.linearGradient(show.colors))
            )
            ShowInfo(show)
        }
        Episodes(Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun ShowInfo(show: Show) {
    Text(show.title, style = GlimmerTheme.typography.titleLarge)
    Text(show.meta, style = GlimmerTheme.typography.caption)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = {}, interactionSource = rememberHoverLitSource()) { Text("Play") }
        Button(onClick = {}, interactionSource = rememberHoverLitSource()) { Text("My list") }
    }
}

@Composable
private fun Episodes(modifier: Modifier) {
    GlimmerLazyColumn(modifier) {
        items(episodes) { episode ->
            ListItem(
                onClick = {},
                supportingLabel = { Text(episode.second) },
                interactionSource = rememberHoverLitSource(),
            ) {
                Text(episode.first)
            }
        }
    }
}

@Composable
internal fun Backdrop(show: Show, content: @Composable BoxScope.(GraphicsLayer) -> Unit) {
    val layer = rememberGraphicsLayer()
    Box(Modifier.fillMaxSize().clip(PanelShape)) {
        Aurora(
            show,
            Modifier.matchParentSize().drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
        )
        content(layer)
    }
}

@Composable
private fun Aurora(show: Show, modifier: Modifier) {
    val phase = rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing)),
    )
    Box(
        modifier
            .background(Brush.linearGradient(show.colors))
            .drawBehind {
                val radius = size.minDimension * 0.6f
                listOf(show.colors.last(), Coral, Sky).forEachIndexed { i, color ->
                    val a = phase.value + i * 2.1f
                    val center = Offset(size.width * (0.5f + 0.4f * cos(a)), size.height * (0.5f + 0.4f * sin(2 * a)))
                    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, radius), radius, center)
                }
            },
    ) {
        BasicText(
            show.title.uppercase(),
            Modifier
                .offset { IntOffset((sin(phase.value) * 120.dp.toPx()).roundToInt(), 0) }
                .padding(24.dp),
            softWrap = false,
            style = TextStyle(color = Color.White, fontSize = 110.sp, fontWeight = FontWeight.Black, lineHeight = 100.sp),
        )
    }
}

// Offsets are in root coordinates, so the backdrop must sit at the panel's origin.
@Composable
internal fun Modifier.frosted(backdrop: GraphicsLayer?, shape: Shape): Modifier {
    if (backdrop == null) return this
    val blurred = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { origin = it.positionInRoot() }.drawBehind {
        blurred.renderEffect = BlurEffect(FrostRadius.toPx(), FrostRadius.toPx())
        blurred.setOutline(shape.createOutline(size, layoutDirection, this))
        blurred.clip = true
        blurred.record { translate(-origin.x, -origin.y) { drawLayer(backdrop) } }
        drawLayer(blurred)
    }
}

@Composable
private fun SkinPicker(skin: Skin, onPick: (Skin) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Skin.entries.forEach {
            ToggleButton(
                checked = it == skin,
                onCheckedChange = { _ -> onPick(it) },
                interactionSource = rememberHoverLitSource(),
            ) {
                Text(it.label)
            }
        }
    }
}

// Glimmer lights surfaces on focus only. Controller and hand-ray hover reach the app but never
// focus anything, and gaze reaches it not at all, so replay hover as focus for the visuals.
@Composable
private fun rememberHoverLitSource(): MutableInteractionSource {
    val source = remember { MutableInteractionSource() }
    LaunchedEffect(source) {
        var focus: FocusInteraction.Focus? = null
        source.interactions.collect {
            when (it) {
                is HoverInteraction.Enter -> focus = FocusInteraction.Focus().also { f -> source.emit(f) }
                is HoverInteraction.Exit -> focus?.let { f -> source.emit(FocusInteraction.Unfocus(f)) }.also { focus = null }
            }
        }
    }
    return source
}

private val PanelShape = RoundedCornerShape(32.dp)

private val ClearColors = Colors().let {
    it.copy(surface = it.surface.copy(alpha = 0.5f), background = it.background.copy(alpha = 0.2f))
}
private val GlassColors = Colors().let { it.copy(surface = it.surface.copy(alpha = 0.4f)) }

private val FrostRadius = 32.dp
private val Coral = Color(0xFFFF5F6D)
private val Sky = Color(0xFF4FACFE)

internal val episodes = List(10) { "Episode ${it + 1}" to "${38 + it * 3 % 17} min" }
