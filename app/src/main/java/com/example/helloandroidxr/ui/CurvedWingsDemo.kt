package com.example.helloandroidxr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.spatial.ContentEdge
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialCurvedRow
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.layout.SpatialArrangement
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.size
import com.example.helloandroidxr.ui.components.EnvironmentControls
import com.example.helloandroidxr.ui.theme.HelloAndroidXRTheme

/**
 * Hero card flanked by the previous/next items as wings. [SpatialCurvedRow] turns each child
 * tangent to its arc, so the centred hero stays flat and the wings angle in towards the user.
 */
@Composable
fun CurvedWingsDemo() {
    var current by remember { mutableIntStateOf(0) }
    val previous = (current - 1).mod(shows.size)
    val next = (current + 1).mod(shows.size)

    if (LocalSpatialCapabilities.current.isSpatialUiEnabled) {
        Subspace {
            SpatialCurvedRow(
                curveRadius = 1000.dp,
                horizontalArrangement = SpatialArrangement.spacedBy(24.dp),
            ) {
                SpatialPanel(SubspaceModifier.size(width = 320.dp, height = 420.dp)) {
                    WingCard(shows[previous], onClick = { current = previous })
                }
                SpatialPanel(SubspaceModifier.size(width = 900.dp, height = 500.dp)) {
                    HeroCard(shows[current], index = current)
                    Orbiter(position = ContentEdge.Top, offset = 16.dp, alignment = Alignment.End) {
                        EnvironmentControls()
                    }
                }
                SpatialPanel(SubspaceModifier.size(width = 320.dp, height = 420.dp)) {
                    WingCard(shows[next], onClick = { current = next })
                }
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            HeroCard(shows[current], index = current)
            EnvironmentControls(Modifier.align(Alignment.TopEnd).padding(16.dp))
        }
    }
}

@Composable
private fun HeroCard(show: Show, index: Int) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(show.colors))
    ) {
        Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
            Text(show.title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(show.meta, style = MaterialTheme.typography.bodyMedium, color = Color.White)
        }
        PageDots(
            count = shows.size,
            current = index,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        )
    }
}

// Clickable Surface with the panel's corner radius: the system gaze highlight follows this shape.
@Composable
private fun WingCard(show: Show, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxSize()) {
        Box(Modifier.background(Brush.linearGradient(show.colors))) {
            Text(
                show.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
            )
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(width = if (i == current) 24.dp else 6.dp, height = 6.dp)
                    .background(Color.White.copy(alpha = if (i == current) 1f else 0.4f), CircleShape)
            )
        }
    }
}

private data class Show(val title: String, val meta: String, val colors: List<Color>)

private val shows = listOf(
    Show("Ridge Line", "Documentary · 52 min · 2022", listOf(Color(0xFF0B1F2A), Color(0xFF1E5631))),
    Show("Pit Wall", "Series · 8 episodes", listOf(Color(0xFF1B2A6B), Color(0xFFC9A227))),
    Show("Deep Water", "Film · 1 h 34 min · 2024", listOf(Color(0xFF02242F), Color(0xFF1F8A9E))),
    Show("Cold Start", "Documentary · 47 min · 2023", listOf(Color(0xFF3A3F47), Color(0xFFB7C4CF))),
    Show("Night Shift", "Series · 6 episodes", listOf(Color(0xFF1A0B2E), Color(0xFF8E3B8A))),
)

@Composable
@Preview(device = "spec:width=1280dp,height=720dp,dpi=160")
private fun CurvedWingsDemoPreview() {
    HelloAndroidXRTheme {
        CurvedWingsDemo()
    }
}
