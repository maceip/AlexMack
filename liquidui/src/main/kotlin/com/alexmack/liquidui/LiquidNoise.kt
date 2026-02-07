package com.alexmack.liquidui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import kotlin.random.Random

/**
 * Creates a low-resolution noise texture once per size change so multiple components can reuse
 * the same distortion field without expensive per-frame CPU work.
 */
@Composable
fun rememberLiquidNoiseTexture(
    size: IntSize = IntSize(64, 64),
): ImageBitmap {
    return remember(size) {
        val seed = Random.nextFloat() * 255f
        createSolidNoiseBitmap(size, seed)
    }
}
