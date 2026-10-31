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

    // Super-8 look applied to the camera's external (OES) texture:
    // soft lens, heavy grain, faded warm colour, projector bounce, flicker,
    // light leaks, scratches, dust and hairs. All procedural (no assets).
    // uFrameSeed changes once per film frame (~18 fps; see FilmEffectProcessor).
    const val FRAGMENT_SHADER = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;

        varying vec2 vTexCoord;
        uniform samplerExternalOES sTexture;
        uniform float uTime;
        uniform float uFrameSeed;
        uniform float uAspectFix;

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
            // --- Projector bounce / gate weave: the whole image jumps a little
            // every film frame, with an occasional bigger vertical hop.
            float hop = step(0.92, hash(vec2(uFrameSeed, 9.0))) * 0.03;
            float weaveX = (hash(vec2(uFrameSeed, 1.0)) - 0.5) * 0.012;
            float weaveY = (hash(vec2(uFrameSeed, 2.0)) - 0.5) * 0.014 + hop;
            vec2 uv = clamp(vTexCoord + vec2(weaveX, weaveY), 0.0, 1.0);

            // --- Soft, slightly out-of-focus 8mm lens (5-tap blur).
            float r = 0.0022;
            vec3 color = texture2D(sTexture, uv).rgb * 0.4;
            color += texture2D(sTexture, clamp(uv + vec2(r, 0.0), 0.0, 1.0)).rgb * 0.15;
            color += texture2D(sTexture, clamp(uv - vec2(r, 0.0), 0.0, 1.0)).rgb * 0.15;
            color += texture2D(sTexture, clamp(uv + vec2(0.0, r), 0.0, 1.0)).rgb * 0.15;
            color += texture2D(sTexture, clamp(uv - vec2(0.0, r), 0.0, 1.0)).rgb * 0.15;

            // --- Faded, warm, low-saturation colour with lifted blacks.
            float luma = dot(color, vec3(0.299, 0.587, 0.114));
            color = mix(vec3(luma), color, 0.6);
            color = pow(color, vec3(0.88));
            color *= vec3(1.14, 1.0, 0.78);
            color = (color - 0.5) * 0.82 + 0.5;
            color += vec3(0.07, 0.05, 0.02);

            // --- Strong vignette (the 8mm gate is never evenly lit).
            vec2 centered = (uv - 0.5) * vec2(1.0, 1.0 / max(uAspectFix, 0.6));
            float vig = 1.0 - smoothstep(0.25, 0.72, length(centered));
            color *= mix(0.2, 1.0, vig);

            // --- Projector lamp flicker, re-rolled every film frame.
            float flicker = 1.0 + (hash(vec2(uFrameSeed, 7.0)) - 0.5) * 0.30;
            color *= flicker;

            // --- Heavy film grain: fine + coarse layers, new each frame.
            float fine = noise(uv * 900.0 + uFrameSeed * 97.0) - 0.5;
            float coarse = noise(uv * 220.0 + uFrameSeed * 31.0) - 0.5;
            color += fine * 0.16 + coarse * 0.10;

            // --- Light leaks: warm orange/red glow drifting in from the edges.
            float leakPhase = fract(uTime * 0.07);
            vec2 leakCenter = vec2(1.15 - leakPhase * 0.9, -0.15 + leakPhase * 0.5);
            float leak = smoothstep(0.85, 0.0, length(uv - leakCenter)) * 0.65;
            color += vec3(1.0, 0.45, 0.12) * leak;
            float leak2 = smoothstep(0.5, 0.0, uv.x) * step(0.8, hash(vec2(floor(uTime * 1.5), 5.0))) * 0.35;
            color += vec3(1.0, 0.25, 0.08) * leak2;

            // --- Vertical scratches (several, appear and vanish per frame).
            for (int i = 0; i < 3; i++) {
                float fi = float(i);
                float sx = hash(vec2(uFrameSeed, 20.0 + fi));
                float on = step(0.72, hash(vec2(uFrameSeed, 30.0 + fi)));
                float line = 1.0 - smoothstep(0.0, 0.0018, abs(uv.x - sx));
                color = mix(color, vec3(0.95, 0.92, 0.85), line * on * 0.7);
            }

            // --- Dust specks and the odd hair.
            float dust = step(0.990, hash(floor(uv * 260.0) + uFrameSeed * 13.0));
            color = mix(color, vec3(0.03), dust * 0.8);
            float hairX = hash(vec2(floor(uFrameSeed / 6.0), 41.0));
            float hairY = hash(vec2(floor(uFrameSeed / 6.0), 42.0));
            float hair = (1.0 - smoothstep(0.0, 0.003, abs((uv.x - hairX) - (uv.y - hairY) * 0.3)))
                       * step(abs(uv.y - hairY), 0.08)
                       * step(0.6, hash(vec2(floor(uFrameSeed / 6.0), 43.0)));
            color = mix(color, vec3(0.02), hair * 0.8);

            gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
        }
    """
}
