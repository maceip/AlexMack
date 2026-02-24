package com.alexmack.liquidui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.launch

@Composable
fun LiquidSplitCardDemo(
    modifier: Modifier = Modifier,
    useMotionController: Boolean = true,
    motionState: LiquidMotionState = LiquidMotionState(),
) {
    val motionController = if (useMotionController) rememberLiquidMotionController() else null
    val activeMotionState = if (motionController != null) {
        val state by motionController.state
        state
    } else {
        motionState
    }
    val surfaceState = rememberLiquidSurfaceState(viscosity = 0.95f, cornerRadius = 120f)
    val expandProgress = remember { Animatable(0f) }
    val splitProgress = remember { Animatable(0f) }
    var isSplit by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        expandProgress.animateTo(1f, tween(durationMillis = 700))
    }

    LaunchedEffect(expandProgress.value) {
        surfaceState.cornerRadius = lerp(120f.dp, 32f.dp, expandProgress.value).value
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val baseWidth = maxWidth * 0.9f
        val gap = lerp(0.dp, 16.dp, splitProgress.value)
        val splitWidth = (baseWidth - gap) / 2
        val activeWidth = lerp(baseWidth, splitWidth, splitProgress.value)
        val cardHeight = 200.dp

        // Pixel dimensions for the shared global coordinate space.
        // totalSize = the full combined bounding box that both cards share.
        // Each card's cardOffset = where its top-left sits relative to totalSize center.
        val totalWidthPx = with(density) { baseWidth.toPx().toInt() }
        val totalHeightPx = with(density) { cardHeight.toPx().toInt() }
        val combinedSize = IntSize(totalWidthPx, totalHeightPx)

        val activeWidthPx = with(density) { activeWidth.toPx() }
        val gapPx = with(density) { gap.toPx() }

        // Each card's top-left x-position within the combined bounding box.
        // Left card: starts at (totalWidth/2 - activeWidth - gap/2)
        // Right card: starts at (totalWidth/2 + gap/2)
        val leftEdge = (totalWidthPx / 2f) - activeWidthPx - (gapPx / 2f)
        val rightEdge = (totalWidthPx / 2f) + (gapPx / 2f)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (splitProgress.value < 0.01f) {
                    // Single card — cardOffset = (0,0), totalSize = card's own size (identity)
                    LiquidCard(
                        modifier = Modifier
                            .width(activeWidth)
                            .height(cardHeight),
                        state = surfaceState,
                        motionState = activeMotionState,
                        containerColor = Color(0xFF0C1924),
                    ) {
                        Column {
                            Text(text = "Liquid Card", color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Drops expand into surface.", color = Color(0xFFBBD7FF))
                        }
                    }
                } else {
                    val offsetAmount = gap / 2
                    LiquidCard(
                        modifier = Modifier
                            .width(activeWidth)
                            .height(cardHeight)
                            .offset(x = -offsetAmount),
                        state = surfaceState,
                        motionState = activeMotionState,
                        containerColor = Color(0xFF0C1924),
                        cardOffset = Offset(leftEdge, 0f),
                        totalSize = combinedSize,
                    ) {
                        Column {
                            Text(text = "Left Card", color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "High viscosity split.", color = Color(0xFFBBD7FF))
                        }
                    }
                    LiquidCard(
                        modifier = Modifier
                            .width(activeWidth)
                            .height(cardHeight)
                            .offset(x = offsetAmount),
                        state = surfaceState,
                        motionState = activeMotionState,
                        containerColor = Color(0xFF0C1924),
                        cardOffset = Offset(rightEdge, 0f),
                        totalSize = combinedSize,
                    ) {
                        Column {
                            Text(text = "Right Card", color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Accelerometer distorts edge.", color = Color(0xFFBBD7FF))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    isSplit = !isSplit
                    scope.launch {
                        splitProgress.animateTo(
                            if (isSplit) 1f else 0f,
                            tween(durationMillis = 650),
                        )
                    }
                },
            ) {
                Text(if (isSplit) "Merge" else "Split")
            }
        }
    }
}
