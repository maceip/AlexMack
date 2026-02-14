package com.alexmack.liquidui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield

@Stable
class LiquidSurfaceState(
    initialViscosity: Float,
    initialCornerRadius: Float,
) {
    var viscosity by mutableFloatStateOf(initialViscosity)
    var cornerRadius by mutableFloatStateOf(initialCornerRadius)
    var touchPoint by mutableStateOf(Offset.Unspecified)
    var touchStrength by mutableFloatStateOf(0f)

    fun resetTouch() {
        touchPoint = Offset.Unspecified
        touchStrength = 0f
    }
}

@Composable
fun rememberLiquidSurfaceState(
    viscosity: Float = 0.9f,
    cornerRadius: Float = 36f,
): LiquidSurfaceState = remember(viscosity, cornerRadius) {
    LiquidSurfaceState(viscosity, cornerRadius)
}

/**
 * GPU-first liquid surface that relies on RuntimeShader + SDF edges instead of CPU path animation.
 *
 * The shader is applied as a single composition layer to minimize overdraw, and the distortion
 * texture is expected to be a precomputed low-res noise bitmap to reduce per-pixel math.
 */
@Composable
fun LiquidSurface(
    modifier: Modifier = Modifier,
    state: LiquidSurfaceState = rememberLiquidSurfaceState(),
    motionState: LiquidMotionState = LiquidMotionState(),
    noiseTexture: ImageBitmap = rememberLiquidNoiseTexture(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val shaderWrapper = remember { createShaderWrapperOrNull() }

    LaunchedEffect(shaderWrapper, noiseTexture, state, motionState, size) {
        if (shaderWrapper == null) return@LaunchedEffect
        var time = 0f
        while (isActive) {
            if (size != IntSize.Zero) {
                val inputs = LiquidShaderInputs(
                    size = size,
                    radius = state.cornerRadius,
                    viscosity = state.viscosity,
                    accel = motionState.accel,
                    touchPoint = if (state.touchPoint.isSpecified()) state.touchPoint else Offset(0.5f, 0.5f),
                    touchStrength = state.touchStrength,
                    time = time,
                )
                shaderWrapper.update(inputs, noiseTexture)
            }
            time += 0.016f
            yield()
        }
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .onSizeChanged { size = it }
            .pointerInput(state, interactionSource) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = down.changes.firstOrNull() ?: continue
                        if (change.pressed) {
                            state.touchPoint = change.position.normalized(size)
                            state.touchStrength = 0.6f
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        }
                        val up = awaitPointerEvent()
                        if (up.changes.none { it.pressed }) {
                            state.resetTouch()
                        }
                    }
                }
            }
            .then(
                if (shaderWrapper != null) {
                    Modifier.graphicsLayer {
                        renderEffect = shaderWrapper.renderEffect
                    }
                } else {
                    Modifier
                }
            ),
    ) {
        content()
    }
}
