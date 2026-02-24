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
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.isActive

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
    var shaderReady by remember { mutableStateOf(false) }

    LaunchedEffect(shaderWrapper, noiseTexture, state, motionState, size) {
        if (shaderWrapper == null) return@LaunchedEffect
        val startTime = withFrameMillis { it }
        while (isActive) {
            withFrameMillis { frameTime ->
                if (size != IntSize.Zero) {
                    val time = (frameTime - startTime) / 1000f
                    val touchPt = state.touchPoint
                    val inputs = LiquidShaderInputs(
                        size = size,
                        radius = state.cornerRadius,
                        viscosity = state.viscosity,
                        accel = motionState.accel,
                        touchPoint = if (touchPt.isSpecified()) touchPt else Offset(0.5f, 0.5f),
                        touchStrength = state.touchStrength,
                        time = time,
                    )
                    shaderWrapper.update(inputs, noiseTexture)
                    if (!shaderReady) shaderReady = true
                }
            }
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
                if (shaderWrapper != null && shaderReady) {
                    Modifier.graphicsLayer {
                        renderEffect = shaderWrapper.renderEffect.asComposeRenderEffect()
                    }
                } else {
                    Modifier
                }
            ),
    ) {
        content()
    }
}
