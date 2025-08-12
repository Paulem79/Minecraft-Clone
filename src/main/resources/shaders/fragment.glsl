#version 330 core

in vec2 TexCoord;
in float LightLevel;
in vec3 BiomeColor; // couleur du biome par sommet (injectée par le vertex shader)

out vec4 FragColor;

uniform sampler2D textureSampler;
uniform sampler2D overlaySampler; // used when mode==2
// Modes: 0=default, 1=grassTint, 2=overlayWithTint, 3=foliageTint
uniform int mode;

void main()
{
    vec4 base = texture(textureSampler, TexCoord);

    // Preserve alpha for cutout textures (like overlay)
    if (base.a < 0.01)
        discard;

    vec3 color = base.rgb;
    vec3 tint = BiomeColor;

    if (mode == 1) {
        // Tint grass top by couleur sommet
        color *= tint;
    } else if (mode == 2) {
        // Blend base with overlay tinted by biome color
        vec4 overlay = texture(overlaySampler, TexCoord);
        vec3 tintedOverlay = overlay.rgb * tint;
        color = mix(color, tintedOverlay, overlay.a);
    } else if (mode == 3) {
        // Tint foliage (leaves) by couleur sommet
        vec3 foliageTint = BiomeColor;
        color *= foliageTint;
    }

    // Appliquer la lumière APRÈS tous les blends/tints
    color *= LightLevel * 0.8 + 0.2;

    FragColor = vec4(color, base.a);
}