package com.example.helloandroidxr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
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
import androidx.xr.glimmer.Colors
import androidx.xr.glimmer.GlimmerTheme
import androidx.xr.glimmer.ImageCard
import androidx.xr.glimmer.ListItem
import androidx.xr.glimmer.Text
import androidx.xr.glimmer.ToggleButton
import androidx.xr.glimmer.list.GlimmerLazyColumn
import androidx.xr.glimmer.list.items
import androidx.xr.glimmer.surface
import com.example.helloandroidxr.ui.components.EnvironmentControls

enum class Skin { Material, Glimmer, Glass }

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

    GlimmerTheme(colors = if (skin == Skin.Glass) GlassColors else Colors()) {
        Subspace {
            SpatialCurvedRow(
                curveRadius = 1000.dp,
                horizontalArrangement = SpatialArrangement.spacedBy(24.dp),
            ) {
                SpatialPanel(SubspaceModifier.size(width = 320.dp, height = 420.dp)) {
                    Wing(skin, shows[previous], onClick = { current = previous })
                }
                SpatialPanel(SubspaceModifier.size(width = 900.dp, height = 500.dp)) {
                    if (skin == Skin.Material) HeroCard(shows[current], index = current) else GlimmerHero(shows[current])
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
    if (skin == Skin.Material) {
        WingCard(show, onClick)
        return
    }
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
private fun GlimmerHero(show: Show) {
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
            Text(show.title, style = GlimmerTheme.typography.titleLarge)
            Text(show.meta, style = GlimmerTheme.typography.caption)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {}, interactionSource = rememberHoverLitSource()) { Text("Play") }
                Button(onClick = {}, interactionSource = rememberHoverLitSource()) { Text("My list") }
            }
        }
        GlimmerLazyColumn(Modifier.weight(1f).fillMaxHeight()) {
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
                Text(it.name)
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

private val GlassColors = Colors().let {
    it.copy(surface = it.surface.copy(alpha = 0.5f), background = it.background.copy(alpha = 0.2f))
}

private val episodes = List(10) { "Episode ${it + 1}" to "${38 + it * 3 % 17} min" }
