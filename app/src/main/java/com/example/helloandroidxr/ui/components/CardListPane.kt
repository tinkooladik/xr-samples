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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    Surface(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(text = title, style = MaterialTheme.typography.headlineSmall)
            }
            items(items) { item ->
                val isSelected = item.title == selectedTitle
                // The gaze highlight is drawn by the system, only on elements the UI framework
                // reports as interactable — a non-clickable Card never lights up.
                Card(
                    onClick = { selectedTitle = if (isSelected) null else item.title },
                    colors = if (isSelected) {
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    } else {
                        CardDefaults.cardColors()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { selected = isSelected },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(text = item.title, style = MaterialTheme.typography.titleMedium)
                        Text(text = item.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
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
