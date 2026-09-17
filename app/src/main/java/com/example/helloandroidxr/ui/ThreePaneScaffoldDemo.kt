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

package com.example.helloandroidxr.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.xr.compose.material3.EnableXrComponentOverrides
import androidx.xr.compose.material3.ExperimentalMaterial3XrApi
import androidx.xr.compose.material3.SpaceToggleButton
import com.example.helloandroidxr.ui.theme.HelloAndroidXRTheme

/**
 * `ThreePaneScaffold` is Material 3 adaptive's list/detail/extra layout. It takes two inputs and
 * places the three panes accordingly:
 *
 *  - a [calculatePaneScaffoldDirective] — how many panes the window can fit side by side;
 *  - a [calculateThreePaneScaffoldValue] — which pane the user is "on", expanded against the
 *    directive, so each role ends up Expanded, Hidden or Reflowed.
 *
 * [ListDetailPaneScaffold] is the same scaffold with its roles named List/Detail/Extra;
 * `SupportingPaneScaffold` names them Main/Supporting/Extra. There is no navigation built in —
 * the destination below is plain state.
 *
 * [EnableXrComponentOverrides] is what makes it spatial: it swaps in `XrThreePaneScaffoldOverride`,
 * which renders every expanded pane as its own `SpatialPanel` in a `SpatialRow` instead of laying
 * them out inside one 2D window. The pane content is unchanged.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3XrApi::class)
@Composable
fun ThreePaneScaffoldDemo(modifier: Modifier = Modifier) {
    EnableXrComponentOverrides {
        var selected by remember { mutableStateOf<DemoItem?>(null) }
        var showExtra by remember { mutableStateOf(false) }

        val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
        val destination = ThreePaneScaffoldDestinationItem<Nothing>(
            when {
                showExtra -> ListDetailPaneScaffoldRole.Extra
                selected != null -> ListDetailPaneScaffoldRole.Detail
                else -> ListDetailPaneScaffoldRole.List
            }
        )

        ListDetailPaneScaffold(
            directive = directive,
            value = calculateThreePaneScaffoldValue(
                maxHorizontalPartitions = directive.maxHorizontalPartitions,
                adaptStrategies = ListDetailPaneScaffoldDefaults.adaptStrategies(),
                currentDestination = destination,
            ),
            modifier = modifier,
            listPane = {
                AnimatedPane {
                    ListPane(
                        selected = selected,
                        onSelect = { selected = it; showExtra = false },
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    DetailPane(
                        item = selected,
                        onBack = { selected = null },
                        onShowExtra = { showExtra = true },
                    )
                }
            },
            extraPane = {
                AnimatedPane {
                    ExtraPane(item = selected, onClose = { showExtra = false })
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3XrApi::class)
@Composable
private fun ListPane(selected: DemoItem?, onSelect: (DemoItem) -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        LazyColumn {
            // Home Space keeps the app in one 2D window, where the XR overrides stay off and the
            // scaffold lays the panes out itself. Full Space is where it spatializes.
            item {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Roles", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.weight(1f))
                    SpaceToggleButton()
                }
            }
            items(demoItems, key = { it.title }) { item ->
                ListItem(
                    headlineContent = { Text(item.title) },
                    supportingContent = { Text(item.body) },
                    modifier = Modifier.clickable { onSelect(item) },
                    colors = ListItemDefaults.colors(
                        containerColor = if (item == selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DetailPane(item: DemoItem?, onBack: () -> Unit, onShowExtra: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(24.dp)) {
            if (item == null) {
                Text("Pick something from the list.", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            Text(item.title, style = MaterialTheme.typography.headlineMedium)
            Text(item.body, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onShowExtra, modifier = Modifier.padding(top = 24.dp)) {
                Text("Open extra pane")
            }
            TextButton(onClick = onBack) { Text("Back to list") }
        }
    }
}

@Composable
private fun ExtraPane(item: DemoItem?, onClose: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(24.dp)) {
            Text("Extra", style = MaterialTheme.typography.headlineMedium)
            Text(
                "The third role. On a window wide enough for three partitions it sits beside the " +
                    "others; narrower, it takes over and the list and detail go Hidden. " +
                    "Currently showing: ${item?.title ?: "nothing"}.",
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(onClick = onClose) { Text("Close") }
        }
    }
}

private data class DemoItem(val title: String, val body: String)

private val demoItems = listOf(
    DemoItem("Spatial panels", "2D content placed in 3D space, movable and resizable."),
    DemoItem("Orbiters", "Chrome that detaches from a panel and floats alongside it."),
    DemoItem("Environments", "Swap the passthrough view for a skybox and IBL lighting."),
    DemoItem("Home space", "Multitasking mode — your app shares the space with others."),
    DemoItem("Full space", "Your app owns the whole space and can place 3D content."),
    DemoItem("Subspace", "The bridge from Compose UI into the 3D scene graph."),
    DemoItem("Volumes", "Reserve a box of space for a 3D model inside your layout."),
    DemoItem("glTF models", "Load .glb assets and drive their transform from state."),
    DemoItem("Materials", "Tune base color, metallic and roughness at runtime."),
    DemoItem("Anchors", "Pin content to a detected plane or a tracked hand."),
    DemoItem("Passthrough", "Blend the real room in behind your panels."),
    DemoItem("Adaptive layout", "One layout that collapses to phone, expands to XR."),
)

@Composable
@Preview(device = "spec:width=1920dp,height=1080dp,dpi=160")
@Preview(device = "spec:width=411dp,height=891dp")
private fun ThreePaneScaffoldDemoPreview() {
    HelloAndroidXRTheme {
        ThreePaneScaffoldDemo()
    }
}
