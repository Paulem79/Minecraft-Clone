package com.example.mc;

import com.example.mc.engine.Camera;
import com.example.mc.engine.Player;
import com.example.mc.engine.Window;
import com.example.mc.engine.renderer.Renderer;
import com.example.mc.world.World;
import org.joml.Vector2d;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.util.concurrent.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Main {

    private long window;
    private Renderer renderer;
    private Window windowWrapper;
    private World world;
    private Player player;

    // Physics executor for player movement on a separate thread
    private ExecutorService physicsExec;
    private Future<Player.State> pendingSim;

    private double lastMouseX, lastMouseY;
    private boolean firstMouse = true;

    private long lastTimeNanos;

    private int frameCount = 0;
    private double fpsTimer = 0;
    private int currentFps = 0;

    // Pour effet de transition FOV
    private float currentFov = 70.0f;
    private final float fovTransitionSpeed = 8.5f; // Plus grand = plus rapide

    public void run() throws Exception {
        init();
        loop();
        cleanup();
    }

    private void init() throws Exception {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Impossible d'initialiser GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(1280, 720, "Minecraft Clone", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Échec création de fenêtre GLFW");
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);

        GL.createCapabilities();

        // capture mouse
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        windowWrapper = new Window(1280, 720);
        renderer = new Renderer();
        renderer.init();

        world = new World();
        renderer.setWorld(world);

        player = new Player();
        player.setPosition(8, 120, 8);

        physicsExec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "physics-thread");
            t.setDaemon(true);
            return t;
        });

        lastTimeNanos = System.nanoTime();
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();

            // delta time
            long now = System.nanoTime();
            float dt = (now - lastTimeNanos) / 1_000_000_000.0f;
            lastTimeNanos = now;
            if (dt > 0.05f) dt = 0.05f; // clamp to avoid huge steps

            // FPS counter
            frameCount++;
            fpsTimer += dt;
            if (fpsTimer >= 1.0) {
                currentFps = frameCount;
                frameCount = 0;
                fpsTimer = 0;
                glfwSetWindowTitle(window, "Minecraft Clone - FPS: " + currentFps);
            }

            // Mouse look
            Vector2d mouse = new Vector2d();
            try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                double[] mx = new double[1];
                double[] my = new double[1];
                glfwGetCursorPos(window, mx, my);
                mouse.set(mx[0], my[0]);
            }
            if (firstMouse) {
                lastMouseX = mouse.x;
                lastMouseY = mouse.y;
                firstMouse = false;
            }
            double dx = mouse.x - lastMouseX;
            double dy = mouse.y - lastMouseY;
            lastMouseX = mouse.x;
            lastMouseY = mouse.y;
            float sensitivity = 0.1f;
            Camera cam = renderer.getCamera();
            cam.moveRotation((float)(dy * sensitivity), (float)(dx * sensitivity), 0);
            // clamp pitch
            if (cam.getRotation().x > 89) cam.getRotation().x = 89;
            if (cam.getRotation().x < -89) cam.getRotation().x = -89;

            // Keyboard movement
            Vector3f wish = new Vector3f(0,0,0);
            if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) wish.z += 1;
            if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) wish.z -= 1;
            if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) wish.x += 1;
            if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) wish.x -= 1;
            if (wish.lengthSquared() > 0) wish.normalize();
            // Convert wish (local) to world using yaw
            float yawRad = (float)Math.toRadians(cam.getRotation().y);
            Vector3f forward = new Vector3f((float)Math.sin(yawRad), 0, (float)-Math.cos(yawRad));
            Vector3f right = new Vector3f(forward.z, 0, -forward.x);
            Vector3f worldWish = new Vector3f(0,0,0);
            worldWish.fma(wish.z, forward).fma(wish.x, right);

            // Sprint
            boolean sprint = glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS;
            float moveSpeed = sprint ? 5.612f : 4.317f;
            worldWish.mul(moveSpeed);

            // FOV cible selon le sprint
            float targetFov = sprint ? 90.0f : 70.0f;
            // Interpolation lissée du FOV
            currentFov += (targetFov - currentFov) * Math.min(1, fovTransitionSpeed * dt);
            cam.setFov(currentFov);
            windowWrapper.setFov(currentFov);

            boolean jump = glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS;

            // Apply last finished simulation result if ready
            if (pendingSim != null && pendingSim.isDone()) {
                try {
                    Player.State result = pendingSim.get();
                    player.apply(result);
                } catch (Exception ignored) { }
                pendingSim = null;
            }
            // If no simulation is pending, start a new one with current inputs
            if (pendingSim == null) {
                final Player.State snap = player.snapshot();
                final Vector3f wishCopy = new Vector3f(worldWish);
                final boolean jumpCopy = jump;
                final boolean sprintCopy = sprint;
                final float dtCopy = dt;
                pendingSim = physicsExec.submit(() -> Player.simulate(world, dtCopy, wishCopy, jumpCopy, sprintCopy, snap));
            }

            // Update camera to follow player (uses last applied state)
            Vector3f pos = player.getPosition();
            cam.setPosition(pos.x, pos.y + 1.65f, pos.z);

            // Ensure chunks around player are generated
            world.update(pos.x, pos.z);

            renderer.render(windowWrapper);

            glfwSwapBuffers(window);
        }
    }

    private void cleanup() {
        if (physicsExec != null) {
            physicsExec.shutdownNow();
        }
        if (world != null) {
            world.shutdown();
        }
        renderer.shutdown();
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    public static void main(String[] args) throws Exception {
        new Main().run();
    }
}