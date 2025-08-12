package ovh.paulem.mc;

import lombok.Getter;
import ovh.paulem.mc.engine.Camera;
import ovh.paulem.mc.engine.Hotbar;
import ovh.paulem.mc.engine.Player;
import ovh.paulem.mc.engine.Window;
import ovh.paulem.mc.engine.render.Render;
import ovh.paulem.mc.math.ArraysUtils;
import ovh.paulem.mc.world.World;
import org.joml.Vector2d;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import ovh.paulem.mc.world.block.Blocks;
import ovh.paulem.mc.world.block.types.Block;

import java.awt.*;
import java.util.concurrent.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class MC {
    public static MC INSTANCE;

    public MC() {
        INSTANCE = this;
    }

    @Getter
    private long window;
    @Getter
    private Render render;

    @Getter
    private Window windowWrapper;
    @Getter
    private World world;
    @Getter
    private Player player;

    @Getter
    private Hotbar hotbar;

    // Raycasting pour détection des blocs
    private static final float RAY_MAX_DISTANCE = 5.0f;
    private boolean leftMousePressed = false;
    private boolean rightMousePressed = false;
    private long lastBlockActionTime = 0;
    private static final long BLOCK_ACTION_COOLDOWN = 0; // millisecondes

    // Physics executor for player movement on a separate thread
    private ExecutorService physicsExec;
    private Future<Player.State> pendingSim;

    private double lastMouseX, lastMouseY;
    private boolean firstMouse = true;

    private long lastTimeNanos;

    private int frameCount = 0;
    private double fpsTimer = 0;
    private int currentFps = 0;

    // Retourne le temps écoulé entre les frames en secondes (inverse du FPS actuel)
    public double getDeltaTime() {
        return currentFps > 0 ? 1.0 / currentFps : 0.0;
    }

    // Pour effet de transition FOV
    private float currentFov = 70.0f;
    private final float fovTransitionSpeed = 8.5f; // Plus grand = plus rapide

    // Affichage des bordures de chunk (F3+G)
    public static boolean showChunkBorders = false;

    @Getter
    private SoundPlayer soundPlayer;

    public void run() throws Exception {
        init();
        loop();
        cleanup();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Impossible d'initialiser GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        int width = (gd.getDisplayMode().getWidth()/5)*3;
        int height = (gd.getDisplayMode().getHeight()/5)*3;
        window = glfwCreateWindow(width, height, "Minecraft Clone", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Échec création de fenêtre GLFW");
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);

        GL.createCapabilities();

        // capture mouse
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        // Initialiser la hotbar
        hotbar = new Hotbar();

        // Configuration des callbacks pour le scroll et les clics souris
        glfwSetScrollCallback(window, (window, xoffset, yoffset) -> {
            if (yoffset > 0) {
                hotbar.previousSlot();
            } else if (yoffset < 0) {
                hotbar.nextSlot();
            }
        });

        windowWrapper = new Window(width, height, window);
        render = new Render();
        render.init();

        // Associer la hotbar au renderer
        render.setHotbar(hotbar);

        world = new World();
        render.setWorld(world);

        player = new Player(world, render.getCamera());
        player.setPosition(8, 120, 8);

        physicsExec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "physics-thread");
            t.setDaemon(true);
            return t;
        });

        soundPlayer = new SoundPlayer();

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

                Vector3f position = player.getPosition();
                glfwSetWindowTitle(window, "Minecraft Clone - FPS: " + currentFps +
                        " Looking: " + player.getLookingDirection() +
                        " Coords: X: " + String.format("%.2f", position.x) +
                        " Y: " + String.format("%.2f", position.y) +
                        " Z: " + String.format("%.2f", position.z));
            }

            // Détection des clics souris pour poser/casser des blocs
            boolean currentLeftMouse = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
            boolean currentRightMouse = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS;

            // Détection des changements d'état des boutons de souris
            if (currentLeftMouse && !leftMousePressed) {
                leftMousePressed = true;
                handleBlockBreak(getPlayer());
            } else if (!currentLeftMouse && leftMousePressed) {
                leftMousePressed = false;
            }

            if (currentRightMouse && !rightMousePressed) {
                rightMousePressed = true;
                handleBlockPlace(getPlayer());
            } else if (!currentRightMouse && rightMousePressed) {
                rightMousePressed = false;
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
            Camera cam = render.getCamera();
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

            render.render(windowWrapper);

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
        render.shutdown();
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    public static void main(String[] args) throws Exception {
        new MC().run();
    }

    // Structure pour stocker le résultat du raycasting
    private static class RaycastResult {
        public boolean hit;
        public int x, y, z; // Position du bloc touché
        public int face;    // Face touchée (0:+X, 1:-X, 2:+Y, 3:-Y, 4:+Z, 5:-Z)

        public RaycastResult() {
            this.hit = false;
        }
    }

    // Méthode pour le raycasting - détection des blocs
    private RaycastResult raycast() {
        Camera camera = render.getCamera();
        Vector3f position = new Vector3f(camera.getPosition());

        // Direction du regard
        double yaw = Math.toRadians(camera.getRotation().y);
        double pitch = Math.toRadians(camera.getRotation().x);

        Vector3f direction = new Vector3f(
            (float)(Math.sin(yaw) * Math.cos(pitch)),
            (float)(-Math.sin(pitch)),
            (float)(-Math.cos(yaw) * Math.cos(pitch))
        );
        direction.normalize();

        return raycastBlock(position, direction, RAY_MAX_DISTANCE);
    }

    // Implémentation de l'algorithme de raycasting pour les blocs
    private RaycastResult raycastBlock(Vector3f start, Vector3f dir, float maxDistance) {
        RaycastResult result = new RaycastResult();

        // Position actuelle
        int x = (int) Math.floor(start.x);
        int y = (int) Math.floor(start.y);
        int z = (int) Math.floor(start.z);

        // Direction du rayon normalisée
        Vector3f rayDir = new Vector3f(dir).normalize();

        // Calcul des pas et distances initiales
        float stepX = rayDir.x > 0 ? 1 : -1;
        float stepY = rayDir.y > 0 ? 1 : -1;
        float stepZ = rayDir.z > 0 ? 1 : -1;

        // Distances jusqu'aux prochains bords de blocs
        float tMaxX = rayDir.x != 0 ? Math.abs((x + (stepX > 0 ? 1 : 0) - start.x) / rayDir.x) : Float.MAX_VALUE;
        float tMaxY = rayDir.y != 0 ? Math.abs((y + (stepY > 0 ? 1 : 0) - start.y) / rayDir.y) : Float.MAX_VALUE;
        float tMaxZ = rayDir.z != 0 ? Math.abs((z + (stepZ > 0 ? 1 : 0) - start.z) / rayDir.z) : Float.MAX_VALUE;

        // Distances entre intersections avec les bords
        float tDeltaX = rayDir.x != 0 ? Math.abs(1 / rayDir.x) : Float.MAX_VALUE;
        float tDeltaY = rayDir.y != 0 ? Math.abs(1 / rayDir.y) : Float.MAX_VALUE;
        float tDeltaZ = rayDir.z != 0 ? Math.abs(1 / rayDir.z) : Float.MAX_VALUE;

        // Face du bloc touchée (-1 = non défini)
        int face = -1;
        float distance = 0;

        // Boucle principale du raycasting
        while (distance < maxDistance) {
            // Vérifier si le bloc actuel n'est pas de l'air
            if (!world.isPassable(x, y, z)) {
                result.hit = true;
                result.x = x;
                result.y = y;
                result.z = z;
                result.face = face;
                return result;
            }

            // Trouver le prochain bloc à traverser
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                // Traverser dans la direction X
                distance = tMaxX;
                tMaxX += tDeltaX;
                x += (int) stepX;
                face = stepX > 0 ? 1 : 0; // Face -X si on avance en +X, et vice versa
            } else if (tMaxY < tMaxZ) {
                // Traverser dans la direction Y
                distance = tMaxY;
                tMaxY += tDeltaY;
                y += (int) stepY;
                face = stepY > 0 ? 3 : 2; // Face -Y si on avance en +Y, et vice versa
            } else {
                // Traverser dans la direction Z
                distance = tMaxZ;
                tMaxZ += tDeltaZ;
                z += (int) stepZ;
                face = stepZ > 0 ? 5 : 4; // Face -Z si on avance en +Z, et vice versa
            }
        }

        // Aucun bloc trouvé dans la portée maximale
        return result;
    }

    // Méthode pour casser un bloc
    private void handleBlockBreak(Player player) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBlockActionTime < BLOCK_ACTION_COOLDOWN) {
            return;  // Évite les actions trop rapides
        }
        lastBlockActionTime = currentTime;

        RaycastResult result = raycast();
        if (result.hit) {
            // Remplacer le bloc par de l'air en utilisant world.setBlock pour assurer la mise à jour du rendu
            Block block = world.getBlock(result.x, result.y, result.z);
            world.setBlock(result.x, result.y, result.z, Blocks.AIR);
            // Générer les particules si ce n'est pas de l'air
            if (block != null && block.isBlock()) {
                getSoundPlayer().play("/sounds/" + ArraysUtils.getRandom(block.getSounds()) + ".ogg");
                MC.INSTANCE.getRender().spawnBlockParticles(new Vector3f(result.x+0.5f, result.y+0.5f, result.z+0.5f), block);
            }
        }
    }

    // Méthode pour poser un bloc
    private void handleBlockPlace(Player player) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBlockActionTime < BLOCK_ACTION_COOLDOWN) {
            return;  // Évite les actions trop rapides
        }
        lastBlockActionTime = currentTime;

        RaycastResult result = raycast();
        if (result.hit) {
            // Calculer la position du nouveau bloc en fonction de la face touchée
            int newX = result.x;
            int newY = result.y;
            int newZ = result.z;

            // Ajuster les coordonnées en fonction de la face touchée
            switch (result.face) {
                case 0: newX += 1; break; // Face +X
                case 1: newX -= 1; break; // Face -X
                case 2: newY += 1; break; // Face +Y
                case 3: newY -= 1; break; // Face -Y
                case 4: newZ += 1; break; // Face +Z
                case 5: newZ -= 1; break; // Face -Z
            }

            // Vérifier si le nouveau bloc ne collisionne pas avec le joueur
            Vector3f playerPos = this.player.getPosition();
            float playerMinX = playerPos.x - Player.WIDTH/2;
            float playerMaxX = playerPos.x + Player.WIDTH/2;
            float playerMinY = playerPos.y;
            float playerMaxY = playerPos.y + Player.HEIGHT;
            float playerMinZ = playerPos.z - Player.DEPTH/2;
            float playerMaxZ = playerPos.z + Player.DEPTH/2;

            // Vérifier si le nouveau bloc ne collisionne pas avec le joueur (AABB complet)
            if (newX + 1 > playerMinX && newX < playerMaxX &&
                    newY + 1 > playerMinY && newY < playerMaxY &&
                    newZ + 1 > playerMinZ && newZ < playerMaxZ) {
                return;
            }

            // Placer le bloc sélectionné avec world.setBlock pour assurer la mise à jour du rendu
            world.setBlock(newX, newY, newZ, hotbar.getSelectedBlock());
        }
    }
}
