package ovh.paulem.mc.engine.render;

import java.io.IOException;

import static org.lwjgl.opengl.GL46.*;

public class Shader {

    private final int programId;

    public Shader(String vertexPath, String fragmentPath) {
        // Charger et compiler le vertex shader
        int vertexShaderId = compileShader(vertexPath, GL_VERTEX_SHADER);
        // Charger et compiler le fragment shader
        int fragmentShaderId = compileShader(fragmentPath, GL_FRAGMENT_SHADER);

        // Lier les shaders dans un programme
        programId = glCreateProgram();
        glAttachShader(programId, vertexShaderId);
        glAttachShader(programId, fragmentShaderId);
        glLinkProgram(programId);

        // Vérifier erreurs de linkage
        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Erreur linkage shader: " + glGetProgramInfoLog(programId));
        }

        // Les shaders individuels peuvent être supprimés après linkage
        glDeleteShader(vertexShaderId);
        glDeleteShader(fragmentShaderId);
    }

    private int compileShader(String filePath, int type) {
        String source;
        try {
            source = new String(getClass().getResourceAsStream(filePath).readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException("Impossible de lire le shader: " + filePath, e);
        }

        int shaderId = glCreateShader(type);
        glShaderSource(shaderId, source);
        glCompileShader(shaderId);

        // Vérifier erreurs de compilation
        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Erreur compilation shader (" + filePath + "): " + glGetShaderInfoLog(shaderId));
        }

        return shaderId;
    }

    public void use() {
        glUseProgram(programId);
    }

    public void delete() {
        glDeleteProgram(programId);
    }

    // Envoi d'un float
    public void setUniform(String name, float value) {
        glUniform1f(glGetUniformLocation(programId, name), value);
    }

    // Envoi d'une matrice 4x4
    public void setUniformMat4(String name, org.joml.Matrix4f matrix) {
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            glUniformMatrix4fv(glGetUniformLocation(programId, name), false,
                    matrix.get(stack.mallocFloat(16)));
        }
    }

    // Envoi d'un int (pour les textures)
    public void setUniform(String name, int value) {
        glUniform1i(glGetUniformLocation(programId, name), value);
    }

    // Envoi d'un vec3
    public void setUniform3f(String name, float x, float y, float z) {
        glUniform3f(glGetUniformLocation(programId, name), x, y, z);
    }

    // Envoi d'un vec2
    public void setUniform(String name, float x, float y) {
        glUniform2f(glGetUniformLocation(programId, name), x, y);
    }

    public void detach() {
        glUseProgram(0);
    }
}
