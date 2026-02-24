package com.alexmack.liquidui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.isActive

// Ported from R3F project: sdf4/recovered/src/shaders/fragment.glsl
// Adapted for Android AGSL
private const val LIQUID_3D_SHADER = """
uniform float2 resolution;
uniform float time;

// ----- Classic 2D Perlin Noise -----
// Author: Stefan Gustavson
vec4 mod289(vec4 x) {
  return x - floor(x * (1.0 / 289.0)) * 289.0;
}

vec4 permute(vec4 x) {
  return mod289(((x * 34.0) + 1.0) * x);
}

vec4 taylorInvSqrt(vec4 r) {
  return 1.79284291400159 - 0.85373472095314 * r;
}

vec2 fade(vec2 t) {
  return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

float cnoise(vec2 P) {
  vec4 Pi = floor(P.xyxy) + vec4(0.0, 0.0, 1.0, 1.0);
  vec4 Pf = fract(P.xyxy) - vec4(0.0, 0.0, 1.0, 1.0);
  Pi = mod289(Pi);
  vec4 ix = Pi.xzxz;
  vec4 iy = Pi.yyww;
  vec4 fx = Pf.xzxz;
  vec4 fy = Pf.yyww;

  vec4 i = permute(permute(ix) + iy);

  vec4 gx = fract(i * (1.0 / 41.0)) * 2.0 - 1.0;
  vec4 gy = abs(gx) - 0.5;
  vec4 tx = floor(gx + 0.5);
  gx = gx - tx;

  vec2 g00 = vec2(gx.x, gy.x);
  vec2 g10 = vec2(gx.y, gy.y);
  vec2 g01 = vec2(gx.z, gy.z);
  vec2 g11 = vec2(gx.w, gy.w);

  vec4 norm = taylorInvSqrt(vec4(dot(g00, g00), dot(g01, g01), dot(g10, g10), dot(g11, g11)));
  g00 *= norm.x;
  g01 *= norm.y;
  g10 *= norm.z;
  g11 *= norm.w;

  float n00 = dot(g00, vec2(fx.x, fy.x));
  float n10 = dot(g10, vec2(fx.y, fy.y));
  float n01 = dot(g01, vec2(fx.z, fy.z));
  float n11 = dot(g11, vec2(fx.w, fy.w));

  vec2 fade_xy = fade(Pf.xy);
  vec2 n_x = mix(vec2(n00, n01), vec2(n10, n11), fade_xy.x);
  float n_xy = mix(n_x.x, n_x.y, fade_xy.y);
  return 2.3 * n_xy;
}

// ----- SDF & Scene -----

float sdSphere(vec3 p, float radius) {
  return length(p) - radius;
}

float smin(float a, float b, float k) {
  float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
  return mix(b, a, h) - k * h * (1.0 - h);
}

float scene(vec3 p) {
  // Displace based on y coordinates and time, effectively rippling the surface
  float displacement = cnoise(p.yy + time * 0.5) / 4.0;
  
  float d = sdSphere(p, 0.75);
  // Orbiting spheres
  float d2 = sdSphere(p - vec3(cos(time), sin(time), 0.0), 0.25);
  float d3 = sdSphere(p - vec3(cos(time), cos(time), 0.0), 0.25);

  float dist = smin(d, d2, 0.7);
  dist = smin(dist, d3, 0.7);
  dist += displacement;

  return dist / 2.0;
}

vec3 getNormal(vec3 p) {
  vec2 e = vec2(0.01, 0.0);
  vec3 n = scene(p) - vec3(
    scene(p - e.xyy),
    scene(p - e.yxy),
    scene(p - e.yyx)
  );
  return normalize(n);
}

vec3 getColor(float amount) {
  // Cosine palette
  vec3 color = 0.5 + 0.5 * cos(6.2831 * (vec3(0.0, 0.1, 0.2) + amount * vec3(1.0, 1.0, 1.0)));
  return color * amount;
}

half4 main(float2 fragCoord) {
  // Normalize UVs to be centered and aspect-ratio correct
  // R3F: vec2 uv = vUv * 2.0 - 1.0; uv.x *= aspect;
  float2 uv = (fragCoord - 0.5 * resolution) / resolution.y * 2.0;

  // Camera setup
  vec3 rayOrigin = vec3(uv, 1.0);
  vec3 rayDirection = normalize(vec3(uv, -1.0));
  vec3 rayPosition = rayOrigin;

  float dist = 0.0;
  float rayLength = 0.0;
  
  // Lighting
  vec3 light = vec3(-1.0, 1.0, 1.0);
  vec3 color = vec3(0.0);

  // Raymarching loop
  const int MAX_STEPS = 64;
  const float SURFACE_DIST = 0.01;

  for (int i = 0; i < MAX_STEPS; i++) {
    dist = scene(rayPosition);
    rayLength += dist;
    rayPosition = rayOrigin + rayLength * rayDirection;

    if (dist < SURFACE_DIST) {
      vec3 n = getNormal(rayPosition);
      float diffuse = max(0.0, dot(n, light));
      color = getColor(diffuse);
      break;
    }
  }

  return half4(color, 1.0);
}
"""

@Composable
fun Liquid3D(modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        // Fallback or empty for older devices to prevent crash
        return
    }

    val shader = remember { RuntimeShader(LIQUID_3D_SHADER) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    
    val time by produceState(0f) {
        val startTime = withFrameMillis { it }
        while (isActive) {
            withFrameMillis { frameTime ->
                value = (frameTime - startTime) / 1000f
            }
        }
    }

    LaunchedEffect(size, time) {
        if (size.width > 0 && size.height > 0) {
            shader.setFloatUniform("resolution", size.width.toFloat(), size.height.toFloat())
            shader.setFloatUniform("time", time)
        }
    }

    Canvas(modifier = modifier.fillMaxSize().onSizeChanged { size = it }) {
        drawRect(brush = ShaderBrush(shader))
    }
}
