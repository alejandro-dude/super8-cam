package com.alejandro.super8cam.gl

object FilmShader {

    const val VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec4 aTexCoord;
        uniform mat4 uTexMatrix;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = (uTexMatrix * aTexCoord).xy;
        }
    """

    // Renders the camera's external (OES) texture with a Super-8 film look:
    // grain, vignette, warm fade, gate-weave jitter, frame flicker,
    // an animated light leak and occasional vertical scratches.
    // All effects are procedural (hash/noise functions) -- no external assets.
    const val FRAGMENT_SHADER = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;

        varying vec2 vTexCoord;
        uniform samplerExternalOES sTexture;
        uniform float uTime;        // seconds since first frame
        uniform float uFrameSeed;   // changes once per simulated film frame (~18fps)
        uniform float uAspectFix;   // width/height, for round-shaped grain/vignette math

        float hash(vec2 p) {
            return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
        }

        float noise(vec2 p) {
            vec2 i = floor(p);
            vec2 f = fract(p);
            float a = hash(i);
            float b = hash(i + vec2(1.0, 0.0));
            float c = hash(i + vec2(0.0, 1.0));
            float d = hash(i + vec2(1.0, 1.0));
            vec2 u = f * f * (3.0 - 2.0 * f);
            return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
        }

        void main() {
            // --- Gate weave: the frame drifts a tiny, jittery amount each
            // simulated film frame, like an imperfectly pulled-down 8mm gate.
            float weaveX = (hash(vec2(uFrameSeed, 1.0)) - 0.5) * 0.006;
            float weaveY = (hash(vec2(uFrameSeed, 2.0)) - 0.5) * 0.006;
            vec2 uv = vTexCoord + vec2(weaveX, weaveY);
            uv = clamp(uv, 0.0, 1.0);

            vec3 color = texture2D(sTexture, uv).rgb;

            // --- Warm, low-contrast film grade.
            color = pow(color, vec3(0.92));
            color *= vec3(1.08, 1.0, 0.86);
            color = mix(color, vec3(dot(color, vec3(0.299, 0.587, 0.114))), 0.08);
            color = (color - 0.5) * 0.92 + 0.5;
            color += vec3(0.02, 0.015, 0.0);

            // --- Vignette, elliptical to roughly match the Super-8 aperture.
            vec2 centered = (uv - 0.5) * vec2(1.0, 1.0 / max(uAspectFix, 0.6));
            float vig = 1.0 - smoothstep(0.35, 0.78, length(centered));
            color *= mix(0.45, 1.0, vig);

            // --- Film grain: fine noise, re-rolled every simulated frame.
            float grain = noise(uv * 720.0 + uFrameSeed * 97.0) - 0.5;
            color += grain * 0.09;

            // --- Per-frame brightness flicker (lamp / exposure instability).
            float flicker = 1.0 + (hash(vec2(uFrameSeed, 7.0)) - 0.5) * 0.12;
            color *= flicker;

            // --- Animated light leak creeping in from one corner.
            float leakPhase = fract(uTime * 0.03);
            vec2 leakCenter = vec2(1.1 - leakPhase * 0.6, -0.1 + leakPhase * 0.4);
            float leakDist = length(uv - leakCenter);
            float leak = smoothstep(0.9, 0.0, leakDist) * 0.35;
            color += vec3(1.0, 0.55, 0.2) * leak;

            // --- Occasional thin vertical scratch, position changes slowly.
            float scratchSeed = floor(uTime * 0.5);
            float scratchX = hash(vec2(scratchSeed, 3.0));
            float distToScratch = abs(uv.x - scratchX);
            float scratch = (1.0 - smoothstep(0.0, 0.0015, distToScratch));
            scratch *= step(0.85, hash(vec2(scratchSeed, 4.0))); // rare
            color = mix(color, vec3(0.9), scratch * 0.6);

            // --- Dust specks.
            float dust = step(0.997, hash(uv * 400.0 + uFrameSeed * 13.0));
            color = mix(color, vec3(0.0), dust * 0.5);

            gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
        }
    """
}
