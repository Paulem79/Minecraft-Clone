#version 330 core
in vec3 fragColor;
in float fragLightLevel;
in vec2 vUV;

uniform sampler2D particleTexture;
uniform sampler2D overlayTexture;
uniform vec2 uvOffset;
uniform int isOverlay;
uniform vec3 overlayColor;

out vec4 outColor;

void main() {
    vec2 uv = gl_PointCoord * 0.25 + vUV;
    vec4 base = texture(particleTexture, uv);
    if (base.a < 0.1) discard;
    if (isOverlay == 1) {
        vec4 overlay = texture(overlayTexture, uv);
        // Correction : si overlayColor est blanc, il faut forcer l'affichage de l'overlay (et non la base)
        float isWhite = float(all(greaterThanEqual(overlayColor, vec3(0.999))));
        float overlayAlpha = overlay.a * (1.0 - isWhite) + isWhite; // Si blanc, overlayAlpha=1
        vec3 overlayTinted = overlay.rgb * mix(overlayColor, vec3(1.0), isWhite);
        vec3 finalColor = mix(base.rgb, overlayTinted, overlayAlpha);
        outColor = vec4(finalColor * fragColor * fragLightLevel, base.a);
    } else {
        outColor = base * vec4(fragColor * fragLightLevel, 1.0);
    }
}
