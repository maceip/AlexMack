package com.alexmack.liquidui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

/**
 * @param cardOffset Pixel offset of this card's top-left from the combined layout center.
 *                   Used for flute alignment across split cards. Default (0,0) = standalone.
 * @param totalSize  Pixel dimensions of the full combined bounding box (all cards + gaps).
 *                   Default null = use this card's own size (standalone mode).
 */
@Composable
fun LiquidCard(
    modifier: Modifier = Modifier,
    state: LiquidSurfaceState = rememberLiquidSurfaceState(),
    motionState: LiquidMotionState = LiquidMotionState(),
    containerColor: Color = Color(0xFF101722),
    borderColor: Color = Color(0x80FFFFFF),
    borderWidth: Dp = 1.dp,
    contentPadding: Dp = 20.dp,
    cardOffset: Offset = Offset.Zero,
    totalSize: IntSize? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val cardShader = remember { createCardShaderWrapperOrNull() }
    var shaderReady by remember { mutableStateOf(false) }

    LaunchedEffect(cardShader, size) {
        if (cardShader == null) return@LaunchedEffect
        val startTime = withFrameMillis { it }
        while (isActive) {
            withFrameMillis { frameTime ->
                if (size != IntSize.Zero) {
                    val time = (frameTime - startTime) / 1000f
                    cardShader.update(
                        size = size,
                        time = time,
                        radius = state.cornerRadius,
                        cardOffset = cardOffset,
                        totalSize = totalSize ?: size,
                    )
                    if (!shaderReady) shaderReady = true
                }
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { size = it }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        // Layer 1: Liquid blob via ShaderBrush (or solid fallback)
        if (cardShader != null && shaderReady) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(brush = cardShader.brush)
            }
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = containerColor,
                    cornerRadius = CornerRadius(state.cornerRadius, state.cornerRadius),
                )
            }
        }

        // Layer 2: Border
        if (borderWidth > 0.dp) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(state.cornerRadius, state.cornerRadius),
                    style = Stroke(width = borderWidth.toPx()),
                )
            }
        }

        // Layer 3: User content overlaid on top
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            content = content,
        )
    }
}
