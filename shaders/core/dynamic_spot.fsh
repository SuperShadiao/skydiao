#version 330

layout(std140) uniform Blur {
    vec4 data[37];
};

uniform sampler2D Scene;
in vec2 texCoord;
out vec4 fragColor;

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

    // Samsara's neutral translucent body over the filtered world, with a fine rim.
    vec3 body = texture(Scene, texCoord).rgb * (1.0 - data[3].y);
    float rim = (1.0 - smoothstep(0.0, 0.85 * scale, abs(d))) * 0.12;
    float topLight = 1.0 - clamp((p.y - bounds.y) / max(bounds.w, 1.0), 0.0, 1.0);
    body += vec3(0.78, 0.84, 0.94) * rim * (0.45 + 0.55 * topLight);

    // A restrained cool halo and a wider neutral shadow, clipped outside the body.
    float outside = max(d, 0.0) / scale;
    float shadow = exp(-0.5 * pow(outside / 3.0, 2.0)) * 0.25;
    float glow = exp(-0.5 * pow(outside / 1.8, 2.0)) * 0.14;
    float outsideAlpha = (shadow + glow) * (1.0 - coverage);
    float alpha = coverage + outsideAlpha;
    if (alpha < 0.001) discard;
    vec3 outer = vec3(0.43, 0.55, 0.74) * glow / max(shadow + glow, 0.001);
    fragColor = vec4((body * coverage + outer * outsideAlpha) / alpha, alpha);
}
