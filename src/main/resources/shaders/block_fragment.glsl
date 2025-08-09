#version 330 core

in vec2 TexCoord;
out vec4 FragColor;

uniform sampler2D textureSampler;
uniform float ambientLight; // 0.0 (nuit) à 1.0 (plein jour)

void main()
{
    vec4 texColor = texture(textureSampler, TexCoord);

    // Gestion de transparence
    if (texColor.a < 0.1)
        discard;

    // Application d'une lumière simple
    FragColor = vec4(texColor.rgb * ambientLight, texColor.a);
}
