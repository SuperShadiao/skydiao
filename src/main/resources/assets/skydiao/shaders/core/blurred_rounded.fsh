#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform Blur {
    vec4 data[37];
};

uniform sampler2D Scene;
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

float roundedDistance(vec2 p, vec4 bounds, float radius) {
    vec2 q = abs(p - bounds.xy - bounds.zw * 0.5) - bounds.zw * 0.5 + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

void main() {
    if (data[0].w < 0.5) {
        vec3 result = texture(Scene, texCoord).rgb * data[4].x;
        for (int i = 1; i <= int(data[0].z); ++i) {
            vec2 offset = data[0].xy * data[4 + i].y;
            result += (texture(Scene, texCoord + offset).rgb
                     + texture(Scene, texCoord - offset).rgb) * data[4 + i].x;
        }
        fragColor = vec4(result, 1.0);
        return;
    }

    vec2 p = vec2(gl_FragCoord.x, data[1].y - gl_FragCoord.y);
    vec4 bounds = data[2];
    float radius = data[3].x;
    float scale = max(data[3].z, 0.01);
    float d = roundedDistance(p, bounds, radius);
    float aa = max(fwidth(d), 0.75);
    float coverage = 1.0 - smoothstep(-aa * 0.5, aa * 0.5, d);

    // data[7].z = 1：液态玻璃（采样预模糊场景）；0：直接用半透明底色叠加到现有画面。
    // data[7].w = 1：覆盖此前已绘制的颜色（玻璃体不透明）；0：与之混合，保留已绘制的颜色。
    vec4 glassTint = data[5];
    float useGlass = data[7].z;
    float overwrite = data[7].w;
    vec3 sceneColor = texture(Scene, texCoord).rgb;
    vec3 body = useGlass > 0.5 ? mix(sceneColor, glassTint.rgb, glassTint.a) : glassTint.rgb;
    float bodyAlpha = (useGlass > 0.5 && overwrite > 0.5) ? 1.0 : glassTint.a;
    float rim = (1.0 - smoothstep(0.0, 0.85 * scale, abs(d))) * 0.12;
    float topLight = 1.0 - clamp((p.y - bounds.y) / max(bounds.w, 1.0), 0.0, 1.0);
    body += vec3(0.78, 0.84, 0.94) * rim * (0.45 + 0.55 * topLight);

    // Halo: data[7].y = 1 selects a colored glow (color/strength from data[6], reach from
    // data[7].x); 0 keeps the original neutral shadow + cool glow that the glass default uses.
    float outside = max(d, 0.0) / scale;
    float glowSigma = max(data[7].x, 0.001);
    float custom = clamp(data[7].y, 0.0, 1.0);
    float shadow = exp(-0.5 * pow(outside / 3.0, 2.0)) * 0.25;
    float glow = exp(-0.5 * pow(outside / 1.8, 2.0)) * 0.14;
    vec3 origColor = vec3(0.43, 0.55, 0.74) * glow / max(shadow + glow, 0.001);
    float origAlpha = shadow + glow;
    float coloredGlow = exp(-0.5 * pow(outside / glowSigma, 2.0)) * 0.45 * max(data[6].a, 0.0);
    float outsideAlpha = mix(origAlpha, coloredGlow, custom) * (1.0 - coverage);
    float bodyCoverage = coverage * bodyAlpha;
    float alpha = bodyCoverage + outsideAlpha;
    if (alpha < 0.001) discard;
    vec3 outer = mix(origColor, data[6].rgb, custom);
    fragColor = vec4((body * bodyCoverage + outer * outsideAlpha) / alpha, alpha);
}