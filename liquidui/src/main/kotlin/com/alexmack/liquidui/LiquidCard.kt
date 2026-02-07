package com.alexmack.liquidui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun LiquidCard(
    modifier: Modifier = Modifier,
    state: LiquidSurfaceState = rememberLiquidSurfaceState(),
    motionState: LiquidMotionState = LiquidMotionState(),
    containerColor: Color = Color(0xFF101722),
    borderColor: Color = Color(0x80FFFFFF),
    borderWidth: Dp = 1.dp,
    contentPadding: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    LiquidSurface(
        modifier = modifier.then(
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
        state = state,
        motionState = motionState,
        interactionSource = interactionSource,
    ) {
        Box(
            modifier = Modifier
                .background(containerColor)
                .drawWithContent {
                    drawContent()
                    if (borderWidth.toPx() > 0f) {
                        drawRoundRect(
                            color = borderColor,
                            topLeft = Offset.Zero,
                            size = Size(size.width, size.height),
                            cornerRadius = CornerRadius(state.cornerRadius, state.cornerRadius),
                            style = Stroke(width = borderWidth.toPx()),
                        )
                    }
                }
                .padding(contentPadding),
            content = content,
        )
    }
}
