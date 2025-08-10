#version 330 core

layout (location = 0) in vec3 aPos;      // Position du sommet
layout (location = 1) in vec2 aTexCoord; // Coordonnées de texture
layout (location = 2) in vec3 aNormal;
layout (location = 3) in float aLight;

out vec2 TexCoord;
out float LightLevel;

uniform mat4 projection;
uniform mat4 view;
uniform mat4 model;

void main()
{
    gl_Position = projection * view * model * vec4(aPos, 1.0);
    TexCoord = aTexCoord;
    LightLevel = aLight;
}
