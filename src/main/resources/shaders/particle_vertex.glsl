#version 330 core
layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inColor;
layout(location = 2) in float inSize;
layout(location = 3) in float inLightLevel;

uniform mat4 projection;
uniform mat4 view;
uniform vec2 uvOffset;

out vec3 fragColor;
out float fragLightLevel;
out vec2 vUV;

void main() {
    vec4 pos = projection * view * vec4(inPosition, 1.0);
    gl_Position = pos;
    // Correction : taille constante à l'écran selon la perspective
    gl_PointSize = inSize / pos.w;
    fragColor = inColor;
    fragLightLevel = inLightLevel;
    vUV = uvOffset;
}
