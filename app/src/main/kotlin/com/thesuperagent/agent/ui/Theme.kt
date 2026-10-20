package com.thesuperagent.agent.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Bg = Color(0xFF050A14)
val Cyan = Color(0xFF00E5FF)
val Panel = Color(0xFF0B1524)
val PanelBorder = Color(0xFF123047)
val TextMain = Color(0xFFE6F7FF)
val TextDim = Color(0xFF7FA6B8)

fun superColors() = darkColorScheme(
    primary = Cyan,
    onPrimary = Color.Black,
    background = Bg,
    onBackground = TextMain,
    surface = Panel,
    onSurface = TextMain,
    secondary = Color(0xFF0AA7C2),
    outline = PanelBorder,
)

@Composable
fun AnimatedOrb(modifier: Modifier = Modifier, active: Boolean = false) {
    val inf = rememberInfiniteTransition(label = "orb")
    val scale by inf.animateFloat(
        initialValue = 0.8f, targetValue = if (active) 1.5f else 1.1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "scale"
    )
    Box(modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size((36 * scale).dp)
                .background(Brush.radialGradient(listOf(Cyan, Color(0xFF0077AA))), CircleShape)
                .blur(6.dp)
        )
    }
}

@Composable
fun VoiceVisualizer(levels: List<Float>, modifier: Modifier = Modifier) {
    Row(modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        levels.forEach { l ->
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .width(4.dp)
                    .height((8 + l * 40).dp)
                    .background(Cyan.copy(alpha = 0.85f))
            )
        }
    }
}
