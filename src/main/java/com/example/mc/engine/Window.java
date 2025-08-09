package com.example.mc.engine;

import org.joml.Matrix4f;

public class Window {

    private int width;
    private int height;
    private Matrix4f projectionMatrix;

    public Window(int width, int height) {
        this.width = width;
        this.height = height;
        updateProjectionMatrix();
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
        updateProjectionMatrix();
    }

    private void updateProjectionMatrix() {
        float fov = (float) Math.toRadians(70.0f);
        float aspectRatio = (float) width / (float) height;
        float near = 0.01f;
        float far = 1000.0f;
        projectionMatrix = new Matrix4f().perspective(fov, aspectRatio, near, far);
    }

    public Matrix4f getProjectionMatrix() {
        return projectionMatrix;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}