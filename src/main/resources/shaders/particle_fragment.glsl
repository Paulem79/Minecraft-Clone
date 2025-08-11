#version 330 core
in vec3 fragColor;
in float fragLightLevel;
uniform sampler2D particleTexture;
uniform vec2 uvOffset;
out vec4 outColor;

void main() {
    // Utilise la coordonnée du point pour échantillonner la texture
    vec2 uv = gl_PointCoord * 0.25 + uvOffset; // 0.25 = taille d'un "bout" de texture
    vec4 texColor = texture(particleTexture, uv);
    if (texColor.a < 0.1) discard;
    outColor = texColor * vec4(fragColor * fragLightLevel, 1.0);
}
