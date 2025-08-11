package ovh.paulem.mc.engine.render;

import ovh.paulem.mc.MC;
import ovh.paulem.mc.engine.Camera;
import ovh.paulem.mc.engine.Hotbar;
import ovh.paulem.mc.engine.Window;
import ovh.paulem.mc.engine.render.texture.*;
import ovh.paulem.mc.math.FastRandom;
import ovh.paulem.mc.world.Biome;
import ovh.paulem.mc.world.block.types.Block;
import ovh.paulem.mc.world.Chunk;
import ovh.paulem.mc.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import ovh.paulem.mc.world.block.Blocks;

import java.util.*;
import java.util.concurrent.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL46.*;

public class Render {

    // Constantes pour les directions et les normales des faces
    private static final int[][] DIRECTIONS = new int[][]{
            { 1, 0, 0}, {-1, 0, 0},
            { 0, 1, 0}, { 0,-1, 0},
            { 0, 0, 1}, { 0, 0,-1}
    };

    private static final Vector3f[] NORMALS = new Vector3f[]{
            new Vector3f( 1, 0, 0), new Vector3f(-1, 0, 0),
            new Vector3f( 0, 1, 0), new Vector3f( 0,-1, 0),
            new Vector3f( 0, 0, 1), new Vector3f( 0, 0,-1)
    };
    public static final float GREEDY_DIST = 80.0f;

    public record MeshBatch(Mesh mesh, Texture texture, String texturePath) {
    }

    private Shader shader;
    private Camera camera;
    private World world;
    private HotbarRenderer hotbarRenderer;
    private Hotbar hotbar;

    // Cache meshes per chunk for multi-chunk rendering
    private record ChunkMesh(List<MeshBatch> list, boolean greedy, int version) {}
    private final Map<Chunk, ChunkMesh> meshCache = new HashMap<>();

    // Queue of chunks that need mesh (re)build; processed with small budget per frame to avoid spikes
    private final ArrayDeque<Chunk> meshBuildQueue = new ArrayDeque<>();
    private final int meshesPerFrameBudget = 2;

    // Ajout d'un ExecutorService pour le meshing parallèle
    private final ExecutorService meshExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    // Map temporaire pour stocker les résultats de meshing asynchrone
    private final Map<Chunk, Future<MeshBuildResult>> meshFutures = new ConcurrentHashMap<>();

    private ParticleSystem particleSystem = new ParticleSystem();
    private Shader particleShader;
    private int particleVao = 0;
    private int particleVbo = 0;

    public void init() {
        // Active OpenGL
        GL.createCapabilities();
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        // Couleur de fond
        glClearColor(0.5f, 0.8f, 1.0f, 1.0f);

        // Charger shader
        shader = new Shader("/shaders/vertex.glsl", "/shaders/fragment.glsl");

        // Charger le shader de particules
        particleShader = new Shader("/shaders/particle_vertex.glsl", "/shaders/particle_fragment.glsl");
        // Générer VAO/VBO pour les particules (1 buffer dynamique)
        int[] vaos = new int[1];
        int[] vbos = new int[1];
        glGenVertexArrays(vaos);
        glGenBuffers(vbos);
        particleVao = vaos[0];
        particleVbo = vbos[0];
        glBindVertexArray(particleVao);
        glBindBuffer(GL_ARRAY_BUFFER, particleVbo);
        // 3 floats pos, 3 floats color, 1 float size, 1 float lightLevel
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 8 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 8 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, 8 * Float.BYTES, 6 * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(3, 1, GL_FLOAT, false, 8 * Float.BYTES, 7 * Float.BYTES);
        glEnableVertexAttribArray(3);
        glBindVertexArray(0);

        Blocks.blocks.forEach((integer, block) -> block.serveTextures(Textures.textureCache));

        // Caméra
        camera = new Camera();
        particleSystem = new ParticleSystem();
    }

    public void setWorld(World world) {
        this.world = world;
        this.meshCache.clear();
    }

    public void setHotbar(Hotbar hotbar) {
        this.hotbar = hotbar;
        this.hotbarRenderer = new HotbarRenderer(hotbar, shader);
    }

    public Camera getCamera() {
        return camera;
    }

    public void render(Window window) {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        shader.use();

        Matrix4f projection = window.getProjectionMatrix();
        Matrix4f view = camera.getViewMatrix();

        shader.setUniformMat4("projection", projection);
        shader.setUniformMat4("view", view);
        shader.setUniform("textureSampler", 0);
        shader.setUniform("overlaySampler", 1);

        // Set biome colors for the current chunk
        Biome currentBiome = Biome.NORMAL;
        shader.setUniform3f("biomeGrassColor",
                           currentBiome.grassR(),
                           currentBiome.grassG(),
                           currentBiome.grassB());
        shader.setUniform3f("biomeFoliageColor",
                           currentBiome.foliageR(),
                           currentBiome.foliageG(),
                           currentBiome.foliageB());

        if (world != null) {
            // Rebuild up to a small number of chunk meshes per frame to avoid spikes
            int rebuilt = 0;
            while (rebuilt < meshesPerFrameBudget && !meshBuildQueue.isEmpty()) {
                Chunk qc = meshBuildQueue.pollFirst();
                if (qc == null) break;
                // Determine greedy based on current camera distance
                float qccx = qc.getOriginX() + Chunk.CHUNK_X * 0.5f;
                float qccz = qc.getOriginZ() + Chunk.CHUNK_Z * 0.5f;
                float qdx = camera.getPosition().x - qccx;
                float qdz = camera.getPosition().z - qccz;
                float qdist = (float)Math.sqrt(qdx * qdx + qdz * qdz);
                boolean qGreedy = qdist > 80.0f;
                int qver = qc.getVersion();
                // Lancer la génération du mesh en tâche asynchrone si pas déjà en cours
                if (!meshFutures.containsKey(qc)) {
                    meshFutures.put(qc, meshExecutor.submit(() -> {
                        RawMeshData raw = qGreedy ? buildChunkMeshesGreedyRaw(qc) : buildChunkMeshesNonGreedyRaw(qc);
                        return new MeshBuildResult(raw, qGreedy, qver);
                    }));
                }
                rebuilt++;
            }
            // Récupérer les résultats terminés et les placer dans le cache principal
            Iterator<Map.Entry<Chunk, Future<MeshBuildResult>>> it = meshFutures.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Chunk, Future<MeshBuildResult>> entry = it.next();
                Future<MeshBuildResult> future = entry.getValue();
                if (future.isDone()) {
                    try {
                        MeshBuildResult result = future.get();
                        List<MeshBatch> batches = buildMeshBatchesFromRaw(result.raw);
                        meshCache.put(entry.getKey(), new ChunkMesh(batches, result.greedy, result.version));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    it.remove();
                }
            }

            // Render all loaded chunks with per-chunk model transform
            Collection<Chunk> chunks = world.getChunks();
            float camX = camera.getPosition().x;
            float camZ = camera.getPosition().z;
            for (Chunk c : chunks) {
                // Decide whether to use greedy meshing based on horizontal distance to chunk center
                float chunkCenterX = c.getOriginX() + Chunk.CHUNK_X * 0.5f;
                float chunkCenterZ = c.getOriginZ() + Chunk.CHUNK_Z * 0.5f;
                float dx = camX - chunkCenterX;
                float dz = camZ - chunkCenterZ;
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                boolean useGreedy = dist > GREEDY_DIST;

                ChunkMesh cm = meshCache.get(c);
                int ver = c.getVersion();
                boolean needRebuild = (cm == null || cm.greedy != useGreedy || cm.version != ver);
                if (needRebuild) {
                    // enqueue if not already queued or en cours de génération
                    if (!meshBuildQueue.contains(c) && !meshFutures.containsKey(c)) {
                        meshBuildQueue.addLast(c);
                    }
                }
                if (cm == null) {
                    // nothing to render yet
                    continue;
                }
                Matrix4f model = new Matrix4f().translation(c.getOriginX(), 0, c.getOriginZ());
                shader.setUniformMat4("model", model);
                for (MeshBatch batch : cm.list) {
                    int mode = getMode(batch);
                    shader.setUniform("mode", mode);
                    if (batch.texture != null) batch.texture.bind(0);
                    if (batch.mesh != null) batch.mesh.render();
                }
                // reset mode
                shader.setUniform("mode", 0);
            }
        }

        // Désactiver le shader 3D avant de dessiner l'UI 2D
        shader.detach();

        // --- Affichage des bordures de chunk (F3+G) ---
        // Détection de F3+G pour activer/désactiver l'affichage des bordures de chunk
        if ((glfwGetKey(window.getGLFWWindow(), GLFW_KEY_F3) == GLFW_PRESS) &&
            (glfwGetKey(window.getGLFWWindow(), GLFW_KEY_G) == GLFW_PRESS)) {
            MC.showChunkBorders = !MC.showChunkBorders;
        }
        if (MC.showChunkBorders) {
            renderChunkBorders(projection, view);
        }

        // Mise à jour et rendu des particules
        particleSystem.update((float) MC.INSTANCE.getDeltaTime(), world);
        renderParticles(projection, view);

        // Dessiner la hotbar si elle est initialisée (après les particules)
        if (hotbarRenderer != null) {
            hotbarRenderer.render(window);
        }
    }

    private void renderParticles(Matrix4f projection, Matrix4f view) {
        List<Particle> particles = particleSystem.getParticles();
        if (particles.isEmpty()) return;
        // Grouper les particules par (texturePath, uOffset, vOffset)
        Map<String, Map<String, List<Particle>>> grouped = new HashMap<>();
        for (Particle p : particles) {
            if (p.texturePath == null) continue;
            String tex = p.texturePath;
            // On regroupe aussi par offset UV pour chaque texture
            String offsetKey = p.uOffset + "," + p.vOffset;
            grouped.computeIfAbsent(tex, k -> new HashMap<>())
                   .computeIfAbsent(offsetKey, k -> new ArrayList<>())
                   .add(p);
        }
        for (Map.Entry<String, Map<String, List<Particle>>> texEntry : grouped.entrySet()) {
            String texturePath = texEntry.getKey();
            Texture tex = Textures.textureCache.get(texturePath);
            if (tex != null) tex.bind(0);
            for (Map.Entry<String, List<Particle>> offsetEntry : texEntry.getValue().entrySet()) {
                List<Particle> group = offsetEntry.getValue();
                if (group.isEmpty()) continue;
                // Tous les offsets du groupe sont identiques
                Particle ref = group.get(0);
                float[] data = new float[group.size() * 8];
                int i = 0;
                for (Particle p : group) {
                    data[i++] = p.position.x;
                    data[i++] = p.position.y;
                    data[i++] = p.position.z;
                    data[i++] = p.color.x;
                    data[i++] = p.color.y;
                    data[i++] = p.color.z;
                    data[i++] = p.size;
                    data[i++] = p.lightLevel;
                }
                glBindBuffer(GL_ARRAY_BUFFER, particleVbo);
                glBufferData(GL_ARRAY_BUFFER, data, GL_DYNAMIC_DRAW);
                glBindVertexArray(particleVao);
                particleShader.use();
                particleShader.setUniformMat4("projection", projection);
                particleShader.setUniformMat4("view", view);
                particleShader.setUniform("particleTexture", 0);
                // Offset UV du groupe
                particleShader.setUniform("uvOffset", ref.uOffset, ref.vOffset);
                glEnable(GL_PROGRAM_POINT_SIZE);
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                glDepthMask(false);
                glDrawArrays(GL_POINTS, 0, group.size());
                int err = glGetError();
                if (err != 0) System.out.println("OpenGL ERROR après draw particules: " + err);
                glDepthMask(true);
                glDisable(GL_BLEND);
                glBindVertexArray(0);
                glUseProgram(0);
            }
        }
    }

    public void spawnBlockParticles(Vector3f position, Block block) {
        String texturePath = block.getFaceTextureName(0); // Face 0 = face principale
        for (int i = 0; i < 128; i++) {
            Vector3f vel = new Vector3f((float)Math.random()-0.5f, (float)Math.random(), (float)Math.random()-0.5f).mul(2f);
            float size = 0.08f + (float)Math.random()*0.07f; // Particules plus petites
            float life = 5.0f; // 5 secondes pour debug
            // Coordonnées UV aléatoires pour un "bout" de texture
            float uOffset = (float)Math.random();
            float vOffset = (float)Math.random();
            // Couleur blanche (sera ignorée si la texture est utilisée)
            Vector3f color = new Vector3f(1, 1, 1);

            float randomX = FastRandom.INSTANCE.nextFloat(-0.25f, 0.25f);
            float randomY = FastRandom.INSTANCE.nextFloat(-0.25f, 0.25f);
            float randomZ = FastRandom.INSTANCE.nextFloat(-0.25f, 0.25f);
            particleSystem.addParticle(new Particle(new Vector3f(position).add(randomX, randomY, randomZ), vel, color, life, size, texturePath, uOffset, vOffset));
        }
    }

    public static int getMode(MeshBatch batch) {
        int mode = 0;
        if (batch.texture != null && batch.texturePath != null) {
            // Configure le mode de rendu en fonction du type de texture
            if (batch.texture instanceof TintTexture tintTex) {
                if (tintTex.getTintType() == TintType.GRASS) {
                    mode = 1; // tint base with biome grass color
                } else if (tintTex.getTintType() == TintType.FOLIAGE) {
                    mode = 3; // tint with biome foliage color
                }
            } else if (batch.texture instanceof OverlayTexture) {
                mode = 2; // blend overlay with grass tint
            }
        }
        return mode;
    }

    /**
     * Méthode utilitaire pour convertir une map d'accumulateurs en RawMeshData
     */
    private static RawMeshData convertAccsToRawMeshData(Map<String, Acc> accs) {
        Map<String, float[]> verticesByTexture = new HashMap<>();
        Map<String, int[]> indicesByTexture = new HashMap<>();
        for (Map.Entry<String, Acc> e : accs.entrySet()) {
            Acc a = e.getValue();
            if (a.inds.isEmpty()) continue;
            float[] vertices = new float[a.verts.size()];
            for (int i = 0; i < a.verts.size(); i++) vertices[i] = a.verts.get(i);
            int[] indices = new int[a.inds.size()];
            for (int i = 0; i < a.inds.size(); i++) indices[i] = a.inds.get(i);
            verticesByTexture.put(e.getKey(), vertices);
            indicesByTexture.put(e.getKey(), indices);
        }
        return new RawMeshData(verticesByTexture, indicesByTexture);
    }

    // Structure temporaire pour stocker les données brutes de mesh
    private record RawMeshData(Map<String, float[]> verticesByTexture, Map<String, int[]> indicesByTexture) {
    }

    // Modifie buildChunkMeshesGreedy pour retourner RawMeshData
    private RawMeshData buildChunkMeshesGreedyRaw(Chunk chunk) {
        Map<String, Acc> accs = new HashMap<>();

        // Greedy meshing per face direction f
        for (int f = 0; f < 6; f++) {
            // For each face, we define which axes are in-plane (u,v) and which is the slice axis (w)
            // Also define normal for this face
            Vector3f normal = NORMALS[f];
            int sizeX = Chunk.CHUNK_X;
            int sizeY = Chunk.CHUNK_Y;
            int sizeZ = Chunk.CHUNK_Z;
            int uSize, vSize, wSize;
            // Mapping functions depend on f
            // We'll iterate slices along w, and for each build a u-v mask of texture strings
            switch (f) {
                case 0: // +X: plane (u=z, v=y), slices over x
                case 1: // -X
                    uSize = sizeZ; vSize = sizeY; wSize = sizeX;
                    break;
                case 2: // +Y: plane (u=x, v=z), slices over y
                case 3: // -Y
                    uSize = sizeX; vSize = sizeZ; wSize = sizeY;
                    break;
                default: // 4:+Z, 5:-Z -> plane (u=x, v=y), slices over z
                    uSize = sizeX; vSize = sizeY; wSize = sizeZ;
                    break;
            }

            for (int w = 0; w < wSize; w++) {
                String[][] mask = new String[uSize][vSize];
                // Fill mask with texture name when the face at (u,v,w) is visible
                for (int v = 0; v < vSize; v++) {
                    for (int u = 0; u < uSize; u++) {
                        // Map (u,v,w) to chunk-local (x,y,z)
                        int x, y, z;
                        switch (f) {
                            case 0: // +X face at x = w, neighbor at x+1
                                x = w; y = v; z = u; break;
                            case 1: // -X face at x = w, neighbor at x-1
                                x = w; y = v; z = u; break;
                            case 2: // +Y face at y = w, neighbor at y+1
                                x = u; y = w; z = v; break;
                            case 3: // -Y face at y = w, neighbor at y-1
                                x = u; y = w; z = v; break;
                            case 4: // +Z face at z = w, neighbor at z+1
                                x = u; y = v; z = w; break;
                            default: // 5: -Z face at z = w, neighbor at z-1
                                x = u; y = v; z = w; break;
                        }
                        // Bounds safeguard (should be within chunk)
                        if (x >= sizeX || z >= sizeZ) {
                            mask[u][v] = null;
                            continue;
                        }
                        Block blk = chunk.getBlock(x, y, z);
                        if (!blk.isBlock()) { mask[u][v] = null; continue; }
                        // Compute neighbor position in world coords to test visibility
                        int wx = chunk.getOriginX() + x;
                        int wy = y;
                        int wz = chunk.getOriginZ() + z;
                        int nwx = wx + DIRECTIONS[f][0];
                        int nwy = wy + DIRECTIONS[f][1];
                        int nwz = wz + DIRECTIONS[f][2];
                        boolean neighborSolid = (world != null) && world.isOccluding(nwx, nwy, nwz);
                        if (neighborSolid) { mask[u][v] = null; continue; }
                        mask[u][v] = blk.getFaceTextureName(f);
                    }
                }

                // Greedy merge rectangles of same texture in mask
                boolean[][] used = new boolean[uSize][vSize];
                for (int v = 0; v < vSize; v++) {
                    for (int u = 0; u < uSize; u++) {
                        if (used[u][v]) continue;
                        String tex = mask[u][v];
                        if (tex == null) { used[u][v] = true; continue; }
                        // Find maximum width
                        int width = 1;
                        while (u + width < uSize && !used[u + width][v] && tex.equals(mask[u + width][v])) width++;
                        // Find maximum height while all cells in the next row match
                        int height = 1;
                        outer:
                        while (v + height < vSize) {
                            for (int du = 0; du < width; du++) {
                                if (used[u + du][v + height] || !tex.equals(mask[u + du][v + height])) {
                                    break outer;
                                }
                            }
                            height++;
                        }
                        // Mark used
                        for (int dv = 0; dv < height; dv++) {
                            for (int du = 0; du < width; du++) used[u + du][v + dv] = true;
                        }
                        // Emit one quad for this merged rect
                        Acc acc = accs.computeIfAbsent(tex, k -> new Acc());
                        // Convert (u..u+width, v..v+height, w) back to block-space rect corners for face f
                        // We will generate a quad with 4 corners in 3D depending on face and ensure CCW winding for outside
                        switch (f) {
                            case 0: { // +X at x=w, u=z, v=y
                                float x0 = w + 1; // face sits at +X side
                                float y1 = v + height;
                                float z1 = u + width;
                                float[] lightLevels = new float[]{
                                        safeGetLightLevel(chunk, (int)x0-1, v, (int)z1-1),
                                        safeGetLightLevel(chunk, (int)x0-1, v, u),
                                        safeGetLightLevel(chunk, (int)x0-1, (int)y1-1, u),
                                        safeGetLightLevel(chunk, (int)x0-1, (int)y1-1, (int)z1-1)
                                };
                                addQuad(acc.verts, acc.inds,
                                        new float[]{x0, (float) v, z1}, new float[]{x0, (float) v, (float) u}, new float[]{x0, y1, (float) u}, new float[]{x0, y1, z1},
                                        normal, acc.indexOffset, lightLevels);
                                acc.indexOffset += 4;
                                break; }
                            case 1: { // -X at x=w, u=z, v=y
                                // face sits at -X side
                                float y1 = v + height;
                                float z1 = u + width;
                                float[] lightLevels = new float[]{
                                        safeGetLightLevel(chunk, w, v, u),
                                        safeGetLightLevel(chunk, w, v, (int)z1),
                                        safeGetLightLevel(chunk, w, (int)y1-1, (int)z1),
                                        safeGetLightLevel(chunk, w, (int)y1-1, u)
                                };
                                addQuad(acc.verts, acc.inds,
                                        new float[]{(float) w, (float) v, (float) u}, new float[]{(float) w, (float) v, z1}, new float[]{(float) w, y1, z1}, new float[]{(float) w, y1, (float) u},
                                        normal, acc.indexOffset, lightLevels);
                                acc.indexOffset += 4;
                                break; }
                            case 2: { // +Y at y=w, u=x, v=z
                                float y0 = w + 1; // top face at +Y side
                                float x1 = u + width;
                                float z1 = v + height;
                                float[] lightLevels = new float[]{
                                        safeGetLightLevel(chunk, u, w, v),
                                        safeGetLightLevel(chunk, u, w, (int)z1),
                                        safeGetLightLevel(chunk, (int)x1, w, (int)z1),
                                        safeGetLightLevel(chunk, (int)x1, w, v)
                                };
                                addQuad(acc.verts, acc.inds,
                                        new float[]{(float) u, y0, (float) v}, new float[]{(float) u, y0, z1}, new float[]{x1, y0, z1}, new float[]{x1, y0, (float) v},
                                        normal, acc.indexOffset, lightLevels);
                                acc.indexOffset += 4;
                                break; }
                            case 3: { // -Y at y=w, u=x, v=z
                                // bottom face at -Y side
                                float x1 = u + width;
                                float z1 = v + height;
                                float[] lightLevels = new float[]{
                                        safeGetLightLevel(chunk, u, w, v),
                                        safeGetLightLevel(chunk, (int)x1, w, v),
                                        safeGetLightLevel(chunk, (int)x1, w, (int)z1),
                                        safeGetLightLevel(chunk, u, w, (int)z1)
                                };
                                addQuad(acc.verts, acc.inds,
                                        new float[]{(float) u, (float) w, (float) v}, new float[]{x1, (float) w, (float) v}, new float[]{x1, (float) w, z1}, new float[]{(float) u, (float) w, z1},
                                        normal, acc.indexOffset, lightLevels);
                                acc.indexOffset += 4;
                                break; }
                            case 4: { // +Z at z=w, u=x, v=y
                                float z0 = w + 1; // front face at +Z side
                                float x1 = u + width;
                                float y1 = v + height;
                                float[] lightLevels = new float[]{
                                        safeGetLightLevel(chunk, u, v, w),
                                        safeGetLightLevel(chunk, (int)x1, v, w),
                                        safeGetLightLevel(chunk, (int)x1, (int)y1, w),
                                        safeGetLightLevel(chunk, u, (int)y1, w)
                                };
                                addQuad(acc.verts, acc.inds,
                                        new float[]{(float) u, (float) v, z0}, new float[]{x1, (float) v, z0}, new float[]{x1, y1, z0}, new float[]{(float) u, y1, z0},
                                        normal, acc.indexOffset, lightLevels);
                                acc.indexOffset += 4;
                                break; }
                            default: { // 5: -Z at z=w, u=x, v=y
                                // back face at -Z side
                                float x1 = u + width;
                                float y1 = v + height;
                                float[] lightLevels = new float[]{
                                        safeGetLightLevel(chunk, (int)x1, v, w),
                                        safeGetLightLevel(chunk, u, v, w),
                                        safeGetLightLevel(chunk, u, (int)y1, w),
                                        safeGetLightLevel(chunk, (int)x1, (int)y1, w)
                                };
                                addQuad(acc.verts, acc.inds,
                                        new float[]{x1, (float) v, (float) w}, new float[]{(float) u, (float) v, (float) w}, new float[]{(float) u, y1, (float) w}, new float[]{x1, y1, (float) w},
                                        normal, acc.indexOffset, lightLevels);
                                acc.indexOffset += 4;
                                break; }
                        }
                    }
                }
            }
        }

        return convertAccsToRawMeshData(accs);
    }

    // Structure pour le résultat intermédiaire
    private record MeshBuildResult(RawMeshData raw, boolean greedy, int version) {
    }

    // Arrêt du thread pool à la fermeture
    public void shutdown() {
        meshExecutor.shutdown();
    }

    public static class Acc {
        final List<Float> verts = new ArrayList<>();
        final List<Integer> inds = new ArrayList<>();
        int indexOffset = 0;
    }

    private Map<String, Acc> buildChunkFaces(Chunk chunk) {
        Map<String, Acc> accs = new HashMap<>();

        for (int x = 0; x < Chunk.CHUNK_X; x++) {
            for (int y = 0; y < Chunk.CHUNK_Y; y++) {
                for (int z = 0; z < Chunk.CHUNK_Z; z++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (!block.isBlock()) continue;
                    for (int f = 0; f < 6; f++) {
                        int wx = chunk.getOriginX() + x;
                        int wz = chunk.getOriginZ() + z;
                        int nwx = wx + DIRECTIONS[f][0];
                        int nwy = y + DIRECTIONS[f][1];
                        int nwz = wz + DIRECTIONS[f][2];
                        boolean neighborSolid = (world != null) && world.isOccluding(nwx, nwy, nwz);
                        if (neighborSolid) continue;
                        String texName = block.getFaceTextureName(f);
                        Acc acc = accs.computeIfAbsent(texName, k -> new Acc());
                        // Calcul des niveaux de lumière pour chaque coin de la face
                        float[] lightLevels = switch (f) {
                            case 0 -> new float[]{
                                safeGetLightLevel(chunk, x + 1, y, z + 1),
                                safeGetLightLevel(chunk, x + 1, y, z),
                                safeGetLightLevel(chunk, x + 1, y + 1, z),
                                safeGetLightLevel(chunk, x + 1, y + 1, z + 1)
                            };
                            case 1 -> new float[]{
                                safeGetLightLevel(chunk, x, y, z),
                                safeGetLightLevel(chunk, x, y, z + 1),
                                safeGetLightLevel(chunk, x, y + 1, z + 1),
                                safeGetLightLevel(chunk, x, y + 1, z)
                            };
                            case 2 -> new float[]{
                                safeGetLightLevel(chunk, x, y + 1, z),
                                safeGetLightLevel(chunk, x, y + 1, z + 1),
                                safeGetLightLevel(chunk, x + 1, y + 1, z + 1),
                                safeGetLightLevel(chunk, x + 1, y + 1, z)
                            };
                            case 3 -> new float[]{
                                safeGetLightLevel(chunk, x, y, z),
                                safeGetLightLevel(chunk, x + 1, y, z),
                                safeGetLightLevel(chunk, x + 1, y, z + 1),
                                safeGetLightLevel(chunk, x, y, z + 1)
                            };
                            case 4 -> new float[]{
                                safeGetLightLevel(chunk, x, y, z + 1),
                                safeGetLightLevel(chunk, x + 1, y, z + 1),
                                safeGetLightLevel(chunk, x + 1, y + 1, z + 1),
                                safeGetLightLevel(chunk, x, y + 1, z + 1)
                            };
                            default -> new float[]{
                                safeGetLightLevel(chunk, x + 1, y, z),
                                safeGetLightLevel(chunk, x, y, z),
                                safeGetLightLevel(chunk, x, y + 1, z),
                                safeGetLightLevel(chunk, x + 1, y + 1, z)
                            };
                        };
                        addFace(acc.verts, acc.inds, x, y, z, f, NORMALS[f], acc.indexOffset, lightLevels);
                        acc.indexOffset += 4;
                    }
                }
            }
        }

        return accs;
    }

    // Nouvelle méthode pour créer les MeshBatch à partir de RawMeshData (thread principal)
    private List<MeshBatch> buildMeshBatchesFromRaw(RawMeshData raw) {
        List<MeshBatch> out = new ArrayList<>();
        for (String texPath : raw.verticesByTexture.keySet()) {
            float[] vertices = raw.verticesByTexture.get(texPath);
            int[] indices = raw.indicesByTexture.get(texPath);
            if (indices.length == 0) continue;
            Mesh mesh = new Mesh(vertices, indices);
            Texture tex = Textures.textureCache.get(texPath);
            out.add(new MeshBatch(mesh, tex, texPath));
        }
        return out;
    }

    // Idem pour buildChunkMeshesNonGreedy
    private RawMeshData buildChunkMeshesNonGreedyRaw(Chunk chunk) {
        Map<String, Acc> accs = buildChunkFaces(chunk);

        return convertAccsToRawMeshData(accs);
    }

    // --- Ajout des méthodes utilitaires manquantes ---
    // Ajoute un quad à la liste des vertex et indices, avec la lumière par sommet
    private static void addQuad(List<Float> verts, List<Integer> inds, float[] c0, float[] c1, float[] c2, float[] c3, Vector3f normal, int indexOffset, float[] lightLevels) {
        float[][] uvs = new float[][]{{0,0},{1,0},{1,1},{0,1}};
        float[][] corners = new float[][]{c0, c1, c2, c3};
        for (int i = 0; i < 4; i++) {
            float[] c = corners[i];
            verts.add(c[0]); verts.add(c[1]); verts.add(c[2]);
            verts.add(uvs[i][0]); verts.add(uvs[i][1]);
            verts.add(normal.x); verts.add(normal.y); verts.add(normal.z);
            verts.add(lightLevels[i]); // Ajout du niveau de lumière
        }
        inds.add(indexOffset); inds.add(indexOffset + 1); inds.add(indexOffset + 2);
        inds.add(indexOffset + 2); inds.add(indexOffset + 3); inds.add(indexOffset);
    }

    // Ajoute une face de cube à la liste
    private static void addFace(List<Float> verts, List<Integer> inds, int x, int y, int z, int f, Vector3f normal, int indexOffset, float[] lightLevels) {
        float[][] corners = switch (f) {
            case 0 ->
                    new float[][]{{(float) x + 1, (float) y, (float) z + 1}, {(float) x + 1, (float) y, (float) z}, {(float) x + 1, (float) y + 1, (float) z}, {(float) x + 1, (float) y + 1, (float) z + 1}};
            case 1 ->
                    new float[][]{{(float) x, (float) y, (float) z}, {(float) x, (float) y, (float) z + 1}, {(float) x, (float) y + 1, (float) z + 1}, {(float) x, (float) y + 1, (float) z}};
            case 2 ->
                    new float[][]{{(float) x, (float) y + 1, (float) z}, {(float) x, (float) y + 1, (float) z + 1}, {(float) x + 1, (float) y + 1, (float) z + 1}, {(float) x + 1, (float) y + 1, (float) z}};
            case 3 ->
                    new float[][]{{(float) x, (float) y, (float) z}, {(float) x + 1, (float) y, (float) z}, {(float) x + 1, (float) y, (float) z + 1}, {(float) x, (float) y, (float) z + 1}};
            case 4 ->
                    new float[][]{{(float) x, (float) y, (float) z + 1}, {(float) x + 1, (float) y, (float) z + 1}, {(float) x + 1, (float) y + 1, (float) z + 1}, {(float) x, (float) y + 1, (float) z + 1}};
            default ->
                    new float[][]{{(float) x + 1, (float) y, (float) z}, {(float) x, (float) y, (float) z}, {(float) x, (float) y + 1, (float) z}, {(float) x + 1, (float) y + 1, (float) z}};
        };
        addQuad(verts, inds, corners[0], corners[1], corners[2], corners[3], normal, indexOffset, lightLevels);
    }

    // Utilitaire pour éviter les ArrayIndexOutOfBounds lors de l'accès à la lumière
    public static float safeGetLightLevel(Chunk chunk, int x, int y, int z) {
        if (y >= Chunk.CHUNK_Y) return 1.0f; // ciel
        if (x < 0 || y < 0 || z < 0) return 0.0f; // hors chunk ou souterrain
        // On va lisser entre les chunks en accédant aux voisins même hors chunk courant
        float sum = 0.0f;
        int count = 0;
        int[][] dirs = {{0,0,0},{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int[] d : dirs) {
            int nx = x + d[0];
            int ny = y + d[1];
            int nz = z + d[2];
            Chunk refChunk = chunk;
            int cx = nx, cy = ny, cz = nz;
            // Si hors chunk courant, chercher le chunk voisin
            if (nx < 0 || nx >= Chunk.CHUNK_X || nz < 0 || nz >= Chunk.CHUNK_Z) {
                if (chunk.getWorld() == null) continue;
                int wx = chunk.getOriginX() + nx;
                int wz = chunk.getOriginZ() + nz;
                refChunk = chunk.getWorld().getChunkAt(wx, wz);
                if (refChunk == null) continue;
                cx = (wx % Chunk.CHUNK_X + Chunk.CHUNK_X) % Chunk.CHUNK_X;
                cz = (wz % Chunk.CHUNK_Z + Chunk.CHUNK_Z) % Chunk.CHUNK_Z;
            }
            if (cy < 0 || cy >= Chunk.CHUNK_Y) continue;
            sum += refChunk.getLightLevel(cx, cy, cz) / 15.0f;
            count++;
        }
        if (count == 0) return chunk.getLightLevel(x, y, z) / 15.0f;
        return sum / count;
    }

    // Affiche les bordures de chunk en 3D (wireframe)
    private void renderChunkBorders(Matrix4f projection, Matrix4f view) {
        if (world == null) return;
        // Sauvegarder les matrices actuelles
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadMatrixf(projection.mul(view, new Matrix4f()).get(new float[16]));
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glUseProgram(0); // Pas de shader, couleur fixe
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_CULL_FACE);
        glLineWidth(2.0f);
        float[] color = {1.0f, 1.0f, 0.0f, 1.0f}; // Jaune
        glColor4f(color[0], color[1], color[2], color[3]);
        for (Chunk chunk : MC.INSTANCE.getPlayer().getChunksAround(1)) {
            float x0 = chunk.getOriginX();
            float z0 = chunk.getOriginZ();
            float x1 = x0 + Chunk.CHUNK_X;
            float z1 = z0 + Chunk.CHUNK_Z;
            float y0 = 0f;
            float y1 = 256f;
            // Bordures du chunk
            float[][] chunkPts = {
                {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1},
                {x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}
            };
            int[][] chunkEdges = {
                {0,1},{1,2},{2,3},{3,0}, // bas
                {4,5},{5,6},{6,7},{7,4}, // haut
                {0,4},{1,5},{2,6},{3,7}  // verticales
            };
            glBegin(GL_LINES);
            // Bordures du chunk
            for (int[] e : chunkEdges) {
                float[] p0 = chunkPts[e[0]];
                float[] p1 = chunkPts[e[1]];
                glVertex3f(p0[0], p0[1], p0[2]);
                glVertex3f(p1[0], p1[1], p1[2]);
            }
            glEnd();
        }
        glEnable(GL_CULL_FACE);
        // Restaurer les matrices
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }
}
