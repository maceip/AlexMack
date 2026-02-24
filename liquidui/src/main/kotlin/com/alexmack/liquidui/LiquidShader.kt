package com.alexmack.liquidui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.BitmapShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
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

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
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
            BitmapShader(
                noiseTexture.asAndroidBitmap(),
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT,
            ),
        )
    }
}

internal fun createShaderWrapperOrNull(): LiquidShaderWrapper? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        LiquidShaderWrapper()
    } else {
        null
    }
}

internal fun Offset.normalized(size: IntSize): Offset {
    return if (size.width == 0 || size.height == 0) {
        Offset.Zero
    } else {
        Offset(x / size.width.toFloat(), y / size.height.toFloat())
    }
}

internal fun Offset.isSpecified(): Boolean = this != Offset.Unspecified

// ── Dark metallic liquid shader: fluted glass dipped in ink ──

internal const val LIQUID_CARD_SHADER_CODE = """
uniform float2 resolution;
uniform float time;
uniform float radius;
// Flute alignment: compute UV in a shared global coordinate space so that
// adjacent split cards see the same SDF scene and specular highlights align.
// cardOffset = pixel offset of this card's top-left from the combined layout center.
// totalSize  = pixel dimensions of the full combined bounding box.
// When a single card is shown, set cardOffset = (0,0) and totalSize = resolution.
uniform float2 cardOffset;
uniform float2 totalSize;

// ----- Classic 2D Perlin Noise (Stefan Gustavson) -----
vec4 mod289(vec4 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec4 permute(vec4 x) { return mod289(((x * 34.0) + 1.0) * x); }
vec4 taylorInvSqrt(vec4 r) { return 1.79284291400159 - 0.85373472095314 * r; }
vec2 fade(vec2 t) { return t * t * t * (t * (t * 6.0 - 15.0) + 10.0); }

float cnoise(vec2 P) {
  vec4 Pi = floor(P.xyxy) + vec4(0.0, 0.0, 1.0, 1.0);
  vec4 Pf = fract(P.xyxy) - vec4(0.0, 0.0, 1.0, 1.0);
  Pi = mod289(Pi);
  vec4 ix = Pi.xzxz; vec4 iy = Pi.yyww;
  vec4 fx = Pf.xzxz; vec4 fy = Pf.yyww;
  vec4 i = permute(permute(ix) + iy);
  vec4 gx = fract(i * (1.0 / 41.0)) * 2.0 - 1.0;
  vec4 gy = abs(gx) - 0.5;
  vec4 tx = floor(gx + 0.5);
  gx = gx - tx;
  vec2 g00 = vec2(gx.x,gy.x); vec2 g10 = vec2(gx.y,gy.y);
  vec2 g01 = vec2(gx.z,gy.z); vec2 g11 = vec2(gx.w,gy.w);
  vec4 norm = taylorInvSqrt(vec4(dot(g00,g00),dot(g01,g01),dot(g10,g10),dot(g11,g11)));
  g00 *= norm.x; g01 *= norm.y; g10 *= norm.z; g11 *= norm.w;
  float n00 = dot(g00, vec2(fx.x,fy.x));
  float n10 = dot(g10, vec2(fx.y,fy.y));
  float n01 = dot(g01, vec2(fx.z,fy.z));
  float n11 = dot(g11, vec2(fx.w,fy.w));
  vec2 fade_xy = fade(Pf.xy);
  vec2 n_x = mix(vec2(n00,n01), vec2(n10,n11), fade_xy.x);
  return 2.3 * mix(n_x.x, n_x.y, fade_xy.y);
}

// ----- SDF scene — original SDF4 blob -----
float sdSphere(vec3 p, float r) { return length(p) - r; }
float smin(float a, float b, float k) {
  float h = clamp(0.5 + 0.5*(b-a)/k, 0.0, 1.0);
  return mix(b,a,h) - k*h*(1.0-h);
}

float scene(vec3 p) {
  float displacement = cnoise(p.yy + time * 0.5) / 4.0;
  float d  = sdSphere(p, 0.75);
  float d2 = sdSphere(p - vec3(cos(time), sin(time), 0.0), 0.25);
  float d3 = sdSphere(p - vec3(cos(time), cos(time), 0.0), 0.25);
  float dist = smin(smin(d, d2, 0.7), d3, 0.7);
  return (dist + displacement) / 2.0;
}

vec3 getNormal(vec3 p) {
  vec2 e = vec2(0.01, 0.0);
  return normalize(scene(p) - vec3(scene(p-e.xyy), scene(p-e.yxy), scene(p-e.yyx)));
}

// ----- Rounded-rect SDF for card clipping -----
float roundedRectSdf(float2 p, float2 halfSize, float r) {
  float2 q = abs(p) - (halfSize - r);
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 fragCoord) {
  // Map local fragment coordinate into the shared global space so all cards
  // ray-march the same SDF scene and flute highlights stay continuous.
  float2 globalCoord = fragCoord + cardOffset;
  float2 uv = (globalCoord - 0.5 * totalSize) / totalSize.y * 2.0;

  vec3 ro = vec3(uv, 1.0);
  vec3 rd = normalize(vec3(uv, -1.0));
  vec3 rp = ro;
  vec3 viewDir = normalize(vec3(0.0, 0.0, 1.0));

  // Three lights for fluted-glass multi-reflection look
  vec3 light1 = normalize(vec3(-1.0, 1.0, 1.0));
  vec3 light2 = normalize(vec3(1.0, -0.5, 0.8));
  vec3 light3 = normalize(vec3(0.0, 1.0, 0.5));

  // Ink-black base for the entire card
  vec3 inkBase = vec3(0.02, 0.02, 0.03);
  vec3 finalColor = inkBase;

  bool hit = false;
  for (int i = 0; i < 64; i++) {
    float d = scene(rp);
    rp += d * rd;
    if (d < 0.01) {
      hit = true;
      vec3 n = getNormal(rp);

      // Fresnel — metallic surfaces reflect more at glancing angles
      float fresnel = pow(1.0 - max(dot(n, viewDir), 0.0), 4.0);

      // Diffuse: very subtle, keeps it dark
      float diff1 = max(0.0, dot(n, light1));
      float diff2 = max(0.0, dot(n, light2));

      // Sharp specular from multiple lights — fluted glass caustics
      vec3 h1 = normalize(light1 + viewDir);
      vec3 h2 = normalize(light2 + viewDir);
      vec3 h3 = normalize(light3 + viewDir);
      float spec1 = pow(max(dot(n, h1), 0.0), 128.0);
      float spec2 = pow(max(dot(n, h2), 0.0), 96.0);
      float spec3 = pow(max(dot(n, h3), 0.0), 64.0);

      // Dark metallic base — barely visible diffuse, ink-like
      finalColor = inkBase + vec3(0.03, 0.03, 0.04) * (diff1 + diff2 * 0.5);

      // Metallic specular highlights — cool silver/steel tones
      finalColor += vec3(0.7, 0.72, 0.78) * spec1 * 0.6;
      finalColor += vec3(0.5, 0.55, 0.65) * spec2 * 0.35;
      finalColor += vec3(0.4, 0.42, 0.5)  * spec3 * 0.2;

      // Fresnel rim — subtle silver edge glow
      finalColor += vec3(0.08, 0.09, 0.12) * fresnel;

      // Subtle smoky swirl in the dark areas — Perlin-driven
      float smoke = cnoise(rp.xy * 4.0 + time * 0.3);
      finalColor += vec3(0.015, 0.018, 0.025) * (smoke * 0.5 + 0.5);

      break;
    }
  }

  if (!hit) {
    // Background: ink black with faint smoky wisps
    float bgSmoke = cnoise(uv * 3.0 + time * 0.2);
    float bgSmoke2 = cnoise(uv * 5.0 - time * 0.15);
    finalColor = inkBase + vec3(0.01, 0.012, 0.018) * (bgSmoke * 0.5 + 0.5)
                         + vec3(0.008, 0.008, 0.012) * (bgSmoke2 * 0.5 + 0.5);
  }

  // --- Rounded-rect card mask ---
  float2 center = resolution * 0.5;
  float2 halfSize = resolution * 0.5;
  float sdf = roundedRectSdf(fragCoord - center, halfSize, radius);
  float cardAlpha = smoothstep(1.0, -1.0, sdf);

  return half4(finalColor, cardAlpha);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class LiquidCardShaderWrapper {
    private val shader = RuntimeShader(LIQUID_CARD_SHADER_CODE)
    val brush = ShaderBrush(shader)

    /**
     * @param size       This card's own pixel dimensions.
     * @param time       Animation time in seconds.
     * @param radius     Corner radius for the rounded-rect mask.
     * @param cardOffset Pixel offset of this card's top-left corner relative to
     *                   the center of the combined layout. For a single unsplit
     *                   card pass (0, 0).
     * @param totalSize  Pixel dimensions of the full combined bounding box
     *                   (all cards + gaps). For a single unsplit card pass [size].
     */
    fun update(
        size: IntSize,
        time: Float,
        radius: Float,
        cardOffset: Offset = Offset.Zero,
        totalSize: IntSize = size,
    ) {
        shader.setFloatUniform("resolution", size.width.toFloat(), size.height.toFloat())
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("cardOffset", cardOffset.x, cardOffset.y)
        shader.setFloatUniform("totalSize", totalSize.width.toFloat(), totalSize.height.toFloat())
    }
}

internal fun createCardShaderWrapperOrNull(): LiquidCardShaderWrapper? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        LiquidCardShaderWrapper()
    } else {
        null
    }
}

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
