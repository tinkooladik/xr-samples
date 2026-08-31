/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.helloandroidxr.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.helloandroidxr.ui.theme.HelloAndroidXRTheme

data class CardItem(val title: String, val body: String)

@Composable
fun CardListPane(
    title: String,
    items: List<CardItem>,
    modifier: Modifier = Modifier,
) {
    var selectedTitle by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    Surface(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(text = title, style = MaterialTheme.typography.headlineSmall)
            }
            items(items, key = { it.title }) { item ->
                val isSelected = item.title == selectedTitle
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val cut by remember(listState, item.title) {
                    derivedStateOf { listState.layoutInfo.viewportCut(item.title) }
                }
                Box(Modifier.fillMaxWidth()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Pointer and hand-ray hover reach the app, so this outline is ours to
                            // draw and follows the card shape. Gaze hover never does — there the
                            // system draws its own generic highlight and the app has no say.
                            .then(
                                if (hovered) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CardShape)
                                } else {
                                    Modifier
                                }
                            ),
                        shape = CardShape,
                        colors = cardColors(isSelected),
                    ) {
                        CardBody(item)
                    }
                    // The system's gaze highlight follows the interactable element's own bounds and
                    // ignores the list clip, so a click on the full-height Card would light up
                    // outside the panel. The Card stays inert; this overlay carries the click,
                    // shrunk to the slice of the card that is actually inside the viewport.
                    cut?.let { (topPx, bottomPx) ->
                        Spacer(
                            Modifier
                                .matchParentSize()
                                .padding(
                                    top = with(density) { topPx.toDp() },
                                    bottom = with(density) { bottomPx.toDp() },
                                )
                                .clip(sliceShape(topPx, bottomPx))
                                .clickable(
                                    interactionSource = interaction,
                                    indication = ripple(),
                                ) {
                                    selectedTitle = if (isSelected) null else item.title
                                }
                                .semantics {
                                    contentDescription = item.title
                                    selected = isSelected
                                }
                        )
                    }
                }
            }
        }
    }
}

private val CardShape = RoundedCornerShape(12.dp)

/** [CardShape] squared off at whichever edge the viewport cuts, so the slice matches the card. */
private fun sliceShape(topCut: Int, bottomCut: Int) = RoundedCornerShape(
    topStart = if (topCut > 0) 0.dp else 12.dp,
    topEnd = if (topCut > 0) 0.dp else 12.dp,
    bottomStart = if (bottomCut > 0) 0.dp else 12.dp,
    bottomEnd = if (bottomCut > 0) 0.dp else 12.dp,
)

/**
 * Pixels of [key]'s item hidden past the top and bottom viewport edges, or null when the item is
 * composed but not on screen. [viewportStartOffset] and [viewportEndOffset] already span the
 * content padding, which items scroll through and are drawn in.
 */
private fun LazyListLayoutInfo.viewportCut(key: Any): Pair<Int, Int>? {
    val item = visibleItemsInfo.firstOrNull { it.key == key } ?: return null
    return (viewportStartOffset - item.offset).coerceAtLeast(0) to
        (item.offset + item.size - viewportEndOffset).coerceAtLeast(0)
}

@Composable
private fun cardColors(isSelected: Boolean) = if (isSelected) {
    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
} else {
    CardDefaults.cardColors()
}

@Composable
private fun CardBody(item: CardItem) {
    Column(Modifier.padding(16.dp)) {
        Text(text = item.title, style = MaterialTheme.typography.titleMedium)
        Text(text = item.body, style = MaterialTheme.typography.bodyMedium)
    }
}

val sampleCardItems = listOf(
    CardItem("Spatial panels", "2D content placed in 3D space, movable and resizable."),
    CardItem("Orbiters", "Chrome that detaches from a panel and floats alongside it."),
    CardItem("Environments", "Swap the passthrough view for a skybox and IBL lighting."),
    CardItem("Home space", "Multitasking mode — your app shares the space with others."),
    CardItem("Full space", "Your app owns the whole space and can place 3D content."),
    CardItem("Subspace", "The bridge from Compose UI into the 3D scene graph."),
    CardItem("Volumes", "Reserve a box of space for a 3D model inside your layout."),
    CardItem("glTF models", "Load .glb assets and drive their transform from state."),
    CardItem("Materials", "Tune base color, metallic and roughness at runtime."),
    CardItem("Anchors", "Pin content to a detected plane or a tracked hand."),
    CardItem("Passthrough", "Blend the real room in behind your panels."),
    CardItem("Adaptive layout", "One layout that collapses to phone, expands to XR."),
)

@Composable
@Preview(device = "spec:width=400dp,height=800dp")
private fun CardListPanePreview() {
    HelloAndroidXRTheme {
        CardListPane(title = "Android XR", items = sampleCardItems)
    }
}
