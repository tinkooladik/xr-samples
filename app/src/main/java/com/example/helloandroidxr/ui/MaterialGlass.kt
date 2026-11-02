package com.example.helloandroidxr.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The Glass skin's layout and frost with Material 3 components on top instead of Glimmer. */
@Composable
internal fun MaterialGlassWing(show: Show, onClick: () -> Unit) {
    Backdrop(show) { backdrop ->
        Surface(
            onClick = onClick,
            shape = GlassShape,
            color = glassColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .frosted(backdrop, GlassShape),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(show.title, style = MaterialTheme.typography.titleMedium)
                Text(show.meta, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun MaterialGlassHero(show: Show) {
    Backdrop(show) { backdrop ->
        Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Surface(
                shape = GlassShape,
                color = glassColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1.2f).align(Alignment.Bottom).frosted(backdrop, GlassShape),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(show.title, style = MaterialTheme.typography.headlineSmall)
                    Text(show.meta, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = {}) { Text("Play") }
                        FilledTonalButton(onClick = {}) { Text("My list") }
                    }
                }
            }
            Surface(
                shape = GlassShape,
                color = glassColor(),
                modifier = Modifier.weight(1f).fillMaxHeight().frosted(backdrop, GlassShape),
            ) {
                LazyColumn {
                    items(episodes) { (title, length) ->
                        ListItem(
                            headlineContent = { Text(title) },
                            supportingContent = { Text(length) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {},
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun glassColor() = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)

private val GlassShape = RoundedCornerShape(28.dp)
