package ovh.paulem.mc.engine;

import org.joml.Matrix4f;

public class Window {

    private int width;
    private int height;
    private float fov = 70.0f;
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

    public void setFov(float fov) {
        this.fov = fov;
        updateProjectionMatrix();
    }

    public float getFov() {
        return fov;
    }

    private void updateProjectionMatrix() {
        float fovRad = (float) Math.toRadians(fov);
        float aspectRatio = (float) width / (float) height;
        float near = 0.01f;
        float far = 1000.0f;
        projectionMatrix = new Matrix4f().perspective(fovRad, aspectRatio, near, far);
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