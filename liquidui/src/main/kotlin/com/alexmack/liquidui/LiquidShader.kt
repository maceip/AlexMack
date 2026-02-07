package com.alexmack.liquidui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize

internal const val LiquidShaderCode = """
uniform float2 resolution;
uniform float time;
uniform float radius;
uniform float viscosity;
uniform float2 accel;
uniform float2 touchPoint;
uniform float touchStrength;
uniform shader content;
uniform shader noise;

float roundedRectSdf(float2 p, float2 halfSize, float radius) {
    float2 q = abs(p) - (halfSize - radius);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;
    float2 noiseSample = noise.eval(uv).rg * 2.0 - 1.0;
    float2 warp = noiseSample * 0.035 * viscosity;
    float2 touchVector = (touchPoint - uv) * touchStrength;
    float2 accelWarp = accel * 0.03;

    float2 distorted = fragCoord + (warp - touchVector + accelWarp) * resolution;

    float2 center = resolution * 0.5;
    float2 halfSize = resolution * 0.5;
    float2 local = distorted - center;

    float sdf = roundedRectSdf(local, halfSize, radius);
    float alpha = smoothstep(1.0, -1.0, sdf);

    half4 color = content.eval(distorted);
    color.a *= alpha;
    return color;
}
"""

@Immutable
internal data class LiquidShaderInputs(
    val size: IntSize,
    val radius: Float,
    val viscosity: Float,
    val accel: Offset,
    val touchPoint: Offset,
    val touchStrength: Float,
    val time: Float,
)

internal class LiquidShaderWrapper {
    private val shader = RuntimeShader(LiquidShaderCode)
    val renderEffect: RenderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")

    fun update(
        inputs: LiquidShaderInputs,
        noiseTexture: ImageBitmap,
    ) {
        shader.setFloatUniform("resolution", inputs.size.width.toFloat(), inputs.size.height.toFloat())
        shader.setFloatUniform("radius", inputs.radius)
        shader.setFloatUniform("viscosity", inputs.viscosity)
        shader.setFloatUniform("accel", inputs.accel.x, inputs.accel.y)
        shader.setFloatUniform("touchPoint", inputs.touchPoint.x, inputs.touchPoint.y)
        shader.setFloatUniform("touchStrength", inputs.touchStrength)
        shader.setFloatUniform("time", inputs.time)
        shader.setInputShader(
            "noise",
            Shader.makeBitmapShader(
                noiseTexture.asAndroidBitmap(),
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT,
            ),
        )
    }
}

internal fun Offset.normalized(size: IntSize): Offset {
    return if (size.width == 0 || size.height == 0) {
        Offset.Zero
    } else {
        Offset(x / size.width.toFloat(), y / size.height.toFloat())
    }
}

internal fun Offset.isSpecified(): Boolean = !x.isNaN() && !y.isNaN()

internal fun createSolidNoiseBitmap(size: IntSize, seed: Float): ImageBitmap {
    val width = size.width.coerceAtLeast(1)
    val height = size.height.coerceAtLeast(1)
    val pixels = IntArray(width * height)
    var value = seed
    for (i in pixels.indices) {
        value = (value * 1664525f + 1013904223f) % 255f
        val color = androidx.compose.ui.graphics.Color(
            red = (value % 255f) / 255f,
            green = ((value * 0.5f) % 255f) / 255f,
            blue = ((value * 0.25f) % 255f) / 255f,
            alpha = 1f,
        )
        pixels[i] = color.toArgb()
    }
    return android.graphics.Bitmap.createBitmap(pixels, width, height, android.graphics.Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}
