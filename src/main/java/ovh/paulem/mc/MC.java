package ovh.paulem.mc;

import lombok.Getter;
import ovh.paulem.mc.engine.Camera;
import ovh.paulem.mc.engine.Hotbar;
import ovh.paulem.mc.engine.Player;
import ovh.paulem.mc.engine.Raycaster;
import ovh.paulem.mc.engine.Window;
import ovh.paulem.mc.engine.render.IRenderer;
import ovh.paulem.mc.engine.render.RendererFactory;
import ovh.paulem.mc.config.Config;
import ovh.paulem.mc.math.ArraysUtils;
import ovh.paulem.mc.world.RaycastResult;
import ovh.paulem.mc.world.World;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import ovh.paulem.mc.world.block.Blocks;
import ovh.paulem.mc.world.block.types.Block;

import java.awt.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class MC {
    public static MC INSTANCE;

    public MC() { INSTANCE = this; }

    @Getter private long window;
    @Getter private IRenderer render;
    @Getter private Window windowWrapper;
    @Getter private World world;
    @Getter private Player player;
    @Getter private Hotbar hotbar;

    // Rayon sélection bloc
    private static final float RAY_MAX_DISTANCE = 5.0f;
    private boolean leftMousePressed = false;
    private boolean rightMousePressed = false;
    private long lastBlockActionTime = 0;
    private static final long BLOCK_ACTION_COOLDOWN = 0; // ms

    // Suppression physique async -> timestep fixe synchrone
    private static final float FIXED_DT = 1f / 60f; // 60 Hz logique
    private double accumulator = 0.0;
    private float lastFrameDt = 0f; // dt réel de la frame (rendu)

    // Souris
    private double lastMouseX, lastMouseY;
    private boolean firstMouse = true;
    private double pendingYawDelta = 0.0;
    private double pendingPitchDelta = 0.0;
    
    // Renderer toggle tracking
    private boolean f6KeyPressed = false;

    private long lastTimeNanos;

    private int frameCount = 0;
    private double fpsTimer = 0;
    private int currentFps = 0;
    private double titleTimer = 0; // mise à jour titre toutes 0.5s

    // Retourne le vrai delta time (frame précédente) en secondes
    public double getDeltaTime() { return lastFrameDt; }

    // Transition FOV
    private float currentFov = 70.0f;
    private final float fovDamping = 10f; // coefficient exponentiel

    public static boolean showChunkBorders = false;

    @Getter private SoundPlayer soundPlayer;

    // Objets temporaires réutilisables
    private final Vector3f tmpWish = new Vector3f();
    private final Vector3f tmpForward = new Vector3f();
    private final Vector3f tmpRight = new Vector3f();
    private final Vector3f tmpWorldWish = new Vector3f();

    // Raycast objets réutilisés
    private final Raycaster raycaster = new Raycaster();
    private final Vector3f raycastTmpDir = new Vector3f();
    private final Vector3f raycastCamPos = new Vector3f();

    public void run() throws Exception { init(); loop(); cleanup(); }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Impossible d'initialiser GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        int width = (gd.getDisplayMode().getWidth()/5)*3;
        int height = (gd.getDisplayMode().getHeight()/5)*3;
        window = glfwCreateWindow(width, height, "Minecraft Clone", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Échec création de fenêtre GLFW");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // V-Sync par défaut (ajouter option pour uncapped)
        glfwShowWindow(window);
        GL.createCapabilities();

        // Capture & raw mouse
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        if (glfwRawMouseMotionSupported()) glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            if (firstMouse) { lastMouseX = xpos; lastMouseY = ypos; firstMouse = false; return; }
            double dx = xpos - lastMouseX;
            double dy = ypos - lastMouseY;
            lastMouseX = xpos; lastMouseY = ypos;
            pendingYawDelta += dx; // yaw = rotation.y
            pendingPitchDelta += dy; // pitch = rotation.x
        });

        // Hotbar & rendu
        hotbar = new Hotbar();
        windowWrapper = new Window(width, height, window);
        render = RendererFactory.getCurrentRenderer();
        render.init();
        render.setHotbar(hotbar);

        world = new World();
        render.setWorld(world);

        player = new Player(world, render.getCamera());
        player.setPosition(8, 120, 8);

        soundPlayer = new SoundPlayer();
        lastTimeNanos = System.nanoTime();
        
        // Enable performance logging to see renderer comparison
        Config.setPerformanceLogging(true);
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();

            // dt frame
            long now = System.nanoTime();
            float dt = (now - lastTimeNanos) / 1_000_000_000.0f;
            lastTimeNanos = now;
            if (dt > 0.1f) dt = 0.1f; // clamp plus serré
            lastFrameDt = dt;
            accumulator += dt;

            // FPS & titre (0.5s)
            frameCount++;
            fpsTimer += dt;
            titleTimer += dt;
            if (fpsTimer >= 1.0) { currentFps = frameCount; frameCount = 0; fpsTimer = 0; }
            if (titleTimer >= 0.5) {
                titleTimer = 0;
                Vector3f position = player.getPosition();
                glfwSetWindowTitle(window, "MC Clone - " + currentFps + " FPS | Dir: " + player.getLookingDirection() +
                        " | X:" + String.format("%.1f", position.x) +
                        " Y:" + String.format("%.1f", position.y) +
                        " Z:" + String.format("%.1f", position.z));
            }

            // Inputs clavier (capturés à la frame pour toutes les steps logiques à suivre)
            tmpWish.set(0,0,0);
            if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) tmpWish.z += 1;
            if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) tmpWish.z -= 1;
            if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) tmpWish.x += 1;
            if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) tmpWish.x -= 1;
            if (tmpWish.lengthSquared() > 0) tmpWish.normalize();
            Camera cam = render.getCamera();
            float yawRad = (float)Math.toRadians(cam.getRotation().y);
            tmpForward.set((float)Math.sin(yawRad), 0, (float)-Math.cos(yawRad));
            tmpRight.set(tmpForward.z, 0, -tmpForward.x);
            tmpWorldWish.set(0,0,0).fma(tmpWish.z, tmpForward).fma(tmpWish.x, tmpRight);
            boolean sprint = glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS;
            float moveSpeed = sprint ? 5.612f : 4.317f; // valeurs arbitraires actuelles
            tmpWorldWish.mul(moveSpeed);
            boolean jump = glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS;

            // FOV cible + interpolation exponentielle
            float targetFov = sprint ? 90.0f : 70.0f;
            float fovLerp = 1f - (float)Math.exp(-fovDamping * dt);
            currentFov += (targetFov - currentFov) * fovLerp;
            windowWrapper.setFov(currentFov);

            // Souris -> appliquer deltas accumulés
            float sensitivity = 0.1f;
            if (pendingYawDelta != 0 || pendingPitchDelta != 0) {
                cam.moveRotation((float)(pendingPitchDelta * sensitivity), (float)(pendingYawDelta * sensitivity), 0);
                // clamp pitch
                if (cam.getRotation().x > 89) cam.getRotation().x = 89;
                if (cam.getRotation().x < -89) cam.getRotation().x = -89;
                pendingYawDelta = 0; pendingPitchDelta = 0;
            }

            // F6 key toggle for renderer switching
            boolean currentF6 = glfwGetKey(window, GLFW_KEY_F6) == GLFW_PRESS;
            if (currentF6 && !f6KeyPressed) {
                f6KeyPressed = true;
                boolean newMode = Config.toggleRenderer();
                System.out.println("Switched to " + (newMode ? "Voxel Ray" : "Mesh") + " Renderer");
                
                // Get new renderer and transfer state
                IRenderer oldRenderer = render;
                render = RendererFactory.getCurrentRenderer();
                
                // Transfer world and hotbar to new renderer
                if (render != oldRenderer) {
                    render.setWorld(world);
                    render.setHotbar(hotbar);
                    
                    if (Config.isPerformanceLogging()) {
                        System.out.println("Renderer switched successfully to: " + render.getName());
                    }
                }
            } else if (!currentF6 && f6KeyPressed) {
                f6KeyPressed = false;
            }

            // Clics souris (block actions)
            boolean currentLeftMouse = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
            boolean currentRightMouse = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS;
            if (currentLeftMouse && !leftMousePressed) { leftMousePressed = true; handleBlockBreak(getPlayer()); }
            else if (!currentLeftMouse && leftMousePressed) { leftMousePressed = false; }
            if (currentRightMouse && !rightMousePressed) { rightMousePressed = true; handleBlockPlace(getPlayer()); }
            else if (!currentRightMouse && rightMousePressed) { rightMousePressed = false; }

            // Steps logiques fixes
            while (accumulator >= FIXED_DT) {
                // Physique synchrone
                player.update(world, FIXED_DT, tmpWorldWish, jump, sprint);
                accumulator -= FIXED_DT;
            }

            // Caméra suit joueur (état après steps)
            Vector3f pos = player.getPosition();
            cam.setPosition(pos.x, pos.y + 1.65f, pos.z);

            // Génération / update monde
            world.update(pos.x, pos.z);

            // Rendu (peut utiliser interpolation si plus tard on stocke states N/N+1)
            render.render(windowWrapper, dt);

            glfwSwapBuffers(window);
        }
    }

    private void cleanup() {
        if (world != null) world.shutdown();
        RendererFactory.shutdown();
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    public static void main(String[] args) throws Exception { new MC().run(); }

    // Résultat raycast réutilisable - now handled by Raycaster class

    private RaycastResult raycast() {
        Camera camera = render.getCamera();
        raycastCamPos.set(camera.getPosition());
        double yaw = Math.toRadians(camera.getRotation().y);
        double pitch = Math.toRadians(camera.getRotation().x);
        raycastTmpDir.set((float)(Math.sin(yaw) * Math.cos(pitch)), (float)(-Math.sin(pitch)), (float)(-Math.cos(yaw) * Math.cos(pitch))).normalize();
        return raycaster.raycast(world, raycastCamPos, raycastTmpDir, RAY_MAX_DISTANCE);
    }

    // The old raycastBlock method is now replaced by the Raycaster class

    private void handleBlockBreak(Player player) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBlockActionTime < BLOCK_ACTION_COOLDOWN) return;
        lastBlockActionTime = currentTime;
        RaycastResult result = raycast();
        if (result.hit) {
            Block block = world.getBlock(result.x, result.y, result.z);
            world.setBlock(result.x, result.y, result.z, Blocks.AIR);
            if (block != null && block.isBlock()) {
                getSoundPlayer().play("/sounds/" + ArraysUtils.getRandom(block.getSounds()) + ".ogg");
                MC.INSTANCE.getRender().spawnBlockParticles(new Vector3f(result.x+0.5f, result.y+0.5f, result.z+0.5f), block);
            }
        }
    }

    private void handleBlockPlace(Player player) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBlockActionTime < BLOCK_ACTION_COOLDOWN) return;
        lastBlockActionTime = currentTime;
        RaycastResult result = raycast();
        if (result.hit) {
            int newX = result.x; int newY = result.y; int newZ = result.z;
            switch (result.face) {
                case 0 -> newX += 1; case 1 -> newX -= 1; case 2 -> newY += 1; case 3 -> newY -= 1; case 4 -> newZ += 1; case 5 -> newZ -= 1;
            }
            Vector3f playerPos = this.player.getPosition();
            float playerMinX = playerPos.x - Player.WIDTH/2;
            float playerMaxX = playerPos.x + Player.WIDTH/2;
            float playerMinY = playerPos.y;
            float playerMaxY = playerPos.y + Player.HEIGHT;
            float playerMinZ = playerPos.z - Player.DEPTH/2;
            float playerMaxZ = playerPos.z + Player.DEPTH/2;
            if (newX + 1 > playerMinX && newX < playerMaxX && newY + 1 > playerMinY && newY < playerMaxY && newZ + 1 > playerMinZ && newZ < playerMaxZ) return;
            world.setBlock(newX, newY, newZ, hotbar.getSelectedBlock());
        }
    }
}
