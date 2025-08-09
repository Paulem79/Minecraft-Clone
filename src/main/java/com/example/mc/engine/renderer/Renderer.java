package com.example.mc.engine.renderer;

import com.example.mc.engine.Camera;
import com.example.mc.engine.Window;
import com.example.mc.world.block.Block;
import com.example.mc.world.block.Blocks;
import com.example.mc.world.Chunk;
import com.example.mc.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;

import java.util.*;

import static org.lwjgl.opengl.GL11.*;

public class Renderer {

    private record MeshBatch(Mesh mesh, Texture texture, String texturePath) {
    }

    private Shader shader;
    private Camera camera;
    private World world;

    private Texture grassSideOverlayTex;

    // Texture cache to avoid reloading the same texture many times
    private final Map<String, Texture> textureCache = new HashMap<>();

    // Batches (one mesh per texture/block type) kept for legacy single-chunk path
    private List<MeshBatch> batches = new ArrayList<>();
    // Cache meshes per chunk for multi-chunk rendering
    private record ChunkMesh(List<MeshBatch> list, boolean greedy, int version) {}
    private final Map<Chunk, ChunkMesh> meshCache = new HashMap<>();

    // Queue of chunks that need mesh (re)build; processed with small budget per frame to avoid spikes
    private final ArrayDeque<Chunk> meshBuildQueue = new ArrayDeque<>();
    private int meshesPerFrameBudget = 2;

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

        // Charger textures additionnelles
        grassSideOverlayTex = new Texture("/textures/grass_block_side_overlay.png");

        // Caméra
        camera = new Camera();
        camera.setPosition(8, 80, 8);
    }

    public void setWorld(World world) {
        this.world = world;
        // Don't build here; we'll lazily build per-chunk caches during render
        this.batches = new ArrayList<>();
        this.meshCache.clear();
    }

    public Camera getCamera() {
        return camera;
    }

    private Texture getOrCreateTexture(String path) {
        return textureCache.computeIfAbsent(path, Texture::new);
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
        // Set biome grass color (NORMAL biome for now)
        shader.setUniform3f("biomeGrassColor", com.example.mc.world.Biome.NORMAL.grassR(), com.example.mc.world.Biome.NORMAL.grassG(), com.example.mc.world.Biome.NORMAL.grassB());

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
                List<MeshBatch> qlist = buildChunkMeshes(qc, qGreedy);
                meshCache.put(qc, new ChunkMesh(qlist, qGreedy, qver));
                rebuilt++;
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
                float dist = (float)Math.sqrt(dx * dx + dz * dz);
                boolean useGreedy = dist > 80.0f;

                ChunkMesh cm = meshCache.get(c);
                int ver = c.getVersion();
                boolean needRebuild = (cm == null || cm.greedy != useGreedy || cm.version != ver);
                if (needRebuild) {
                    // enqueue if not already queued
                    if (!meshBuildQueue.contains(c)) {
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
                    int mode = 0;
                    if (batch.texturePath != null) {
                        if (batch.texturePath.endsWith("grass_block_top.png")) {
                            mode = 1; // tint base with biome grass color
                        } else if (batch.texturePath.endsWith("grass_block_side.png")) {
                            mode = 2; // blend overlay
                            if (grassSideOverlayTex != null) grassSideOverlayTex.bind(1);
                        }
                    }
                    shader.setUniform("mode", mode);
                    if (batch.texture != null) batch.texture.bind(0);
                    if (batch.mesh != null) batch.mesh.render();
                }
                // reset mode
                shader.setUniform("mode", 0);
            }
        } else {
            // Fallback: render any prebuilt batches at origin
            Matrix4f model = new Matrix4f().identity();
            shader.setUniformMat4("model", model);
            for (MeshBatch batch : batches) {
                int mode = 0;
                if (batch.texturePath != null) {
                    if (batch.texturePath.endsWith("grass_block_top.png")) {
                        mode = 1;
                    } else if (batch.texturePath.endsWith("grass_block_side.png")) {
                        mode = 2;
                        if (grassSideOverlayTex != null) grassSideOverlayTex.bind(1);
                    }
                }
                shader.setUniform("mode", mode);
                if (batch.texture != null) batch.texture.bind(0);
                if (batch.mesh != null) batch.mesh.render();
            }
            shader.setUniform("mode", 0);
        }
    }

    // Greedy meshing implementation
    private List<MeshBatch> buildChunkMeshesGreedy(Chunk chunk) {
        // Accumulate per texture to allow different textures on different faces of the same block
        class Acc {
            final List<Float> verts = new ArrayList<>();
            final List<Integer> inds = new ArrayList<>();
            int indexOffset = 0;
        }
        Map<String, Acc> accs = new HashMap<>();

        // Directions: +/-X, +/-Y, +/-Z
        int[][] dirs = new int[][]{
                { 1, 0, 0}, {-1, 0, 0},
                { 0, 1, 0}, { 0,-1, 0},
                { 0, 0, 1}, { 0, 0,-1}
        };
        Vector3f[] normals = new Vector3f[]{
                new Vector3f( 1, 0, 0), new Vector3f(-1, 0, 0),
                new Vector3f( 0, 1, 0), new Vector3f( 0,-1, 0),
                new Vector3f( 0, 0, 1), new Vector3f( 0, 0,-1)
        };

        // Greedy meshing per face direction f
        for (int f = 0; f < 6; f++) {
            final int U, V, W; // Dimensions per face orientation
            // For each face, we define which axes are in-plane (u,v) and which is the slice axis (w)
            // Also define normal for this face
            Vector3f normal = normals[f];
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
                        if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) {
                            mask[u][v] = null;
                            continue;
                        }
                        Block blk = chunk.getBlock(x, y, z);
                        if (!blk.isOpaque()) { mask[u][v] = null; continue; }
                        // Compute neighbor position in world coords to test visibility
                        int wx = chunk.getOriginX() + x;
                        int wy = y;
                        int wz = chunk.getOriginZ() + z;
                        int nwx = wx + dirs[f][0];
                        int nwy = wy + dirs[f][1];
                        int nwz = wz + dirs[f][2];
                        boolean neighborSolid = (world != null) && world.isSolid(nwx, nwy, nwz);
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
                                float y0 = v; float y1 = v + height;
                                float z0 = u; float z1 = u + width;
                                addQuad(acc.verts, acc.inds,
                                        new float[]{x0, y0, z1}, new float[]{x0, y0, z0}, new float[]{x0, y1, z0}, new float[]{x0, y1, z1},
                                        normal, acc.indexOffset);
                                acc.indexOffset += 4;
                                break; }
                            case 1: { // -X at x=w, u=z, v=y
                                float x0 = w; // face sits at -X side
                                float y0 = v; float y1 = v + height;
                                float z0 = u; float z1 = u + width;
                                addQuad(acc.verts, acc.inds,
                                        new float[]{x0, y0, z0}, new float[]{x0, y0, z1}, new float[]{x0, y1, z1}, new float[]{x0, y1, z0},
                                        normal, acc.indexOffset);
                                acc.indexOffset += 4;
                                break; }
                            case 2: { // +Y at y=w, u=x, v=z
                                float y0 = w + 1; // top face at +Y side
                                float x0 = u; float x1 = u + width;
                                float z0 = v; float z1 = v + height;
                                addQuad(acc.verts, acc.inds,
                                        new float[]{x0, y0, z0}, new float[]{x0, y0, z1}, new float[]{x1, y0, z1}, new float[]{x1, y0, z0},
                                        normal, acc.indexOffset);
                                acc.indexOffset += 4;
                                break; }
                            case 3: { // -Y at y=w, u=x, v=z
                                float y0 = w; // bottom face at -Y side
                                float x0 = u; float x1 = u + width;
                                float z0 = v; float z1 = v + height;
                                addQuad(acc.verts, acc.inds,
                                        new float[]{x0, y0, z0}, new float[]{x1, y0, z0}, new float[]{x1, y0, z1}, new float[]{x0, y0, z1},
                                        normal, acc.indexOffset);
                                acc.indexOffset += 4;
                                break; }
                            case 4: { // +Z at z=w, u=x, v=y
                                float z0 = w + 1; // front face at +Z side
                                float x0 = u; float x1 = u + width;
                                float y0 = v; float y1 = v + height;
                                addQuad(acc.verts, acc.inds,
                                        new float[]{x0, y0, z0}, new float[]{x1, y0, z0}, new float[]{x1, y1, z0}, new float[]{x0, y1, z0},
                                        normal, acc.indexOffset);
                                acc.indexOffset += 4;
                                break; }
                            default: { // 5: -Z at z=w, u=x, v=y
                                float z0 = w; // back face at -Z side
                                float x0 = u; float x1 = u + width;
                                float y0 = v; float y1 = v + height;
                                addQuad(acc.verts, acc.inds,
                                        new float[]{x1, y0, z0}, new float[]{x0, y0, z0}, new float[]{x0, y1, z0}, new float[]{x1, y1, z0},
                                        normal, acc.indexOffset);
                                acc.indexOffset += 4;
                                break; }
                        }
                    }
                }
            }
        }

        List<MeshBatch> out = new ArrayList<>();
        for (Map.Entry<String, Acc> e : accs.entrySet()) {
            Acc a = e.getValue();
            if (a.inds.isEmpty()) continue;
            float[] vertices = new float[a.verts.size()];
            for (int i = 0; i < a.verts.size(); i++) vertices[i] = a.verts.get(i);
            int[] indices = new int[a.inds.size()];
            for (int i = 0; i < a.inds.size(); i++) indices[i] = a.inds.get(i);
            Mesh mesh = new Mesh(vertices, indices);
            String texPath = e.getKey();
            Texture tex = getOrCreateTexture(texPath);
            out.add(new MeshBatch(mesh, tex, texPath));
        }
        return out;
    }

    // Adds a quad for a cube face at block (x,y,z) for face index f
    // Vertex layout: pos(3), uv(2), normal(3)
    private void addFace(List<Float> verts, List<Integer> inds, int x, int y, int z, int f, Vector3f normal, int indexOffset) {
        float px = x;
        float py = y;
        float pz = z;
        // Define 4 vertices per face depending on face index
        float[][] corners;
        switch (f) {
            case 0: // +X (right)
                // CCW when viewed from +X
                corners = new float[][]{{px+1,py,  pz+1}, {px+1,py,  pz  }, {px+1,py+1,pz  }, {px+1,py+1,pz+1}};
                break;
            case 1: // -X (left)
                // CCW when viewed from -X
                corners = new float[][]{{px,  py,  pz  }, {px,  py,  pz+1}, {px,  py+1,pz+1}, {px,  py+1,pz  }};
                break;
            case 2: // +Y (top)
                // Order to ensure CCW winding when viewed from above
                corners = new float[][]{{px,  py+1,pz  }, {px,  py+1,pz+1}, {px+1,py+1,pz+1}, {px+1,py+1,pz  }};
                break;
            case 3: // -Y (bottom)
                // Order to ensure CCW winding when viewed from below
                corners = new float[][]{{px,  py,  pz  }, {px+1,py,  pz  }, {px+1,py,  pz+1}, {px,  py,  pz+1}};
                break;
            case 4: // +Z (front)
                // CCW when viewed from +Z towards origin
                corners = new float[][]{{px,  py,  pz+1}, {px+1,py,  pz+1}, {px+1,py+1,pz+1}, {px,  py+1,pz+1}};
                break;
            default: // -Z (back)
                // CCW when viewed from -Z (outside)
                corners = new float[][]{{px+1,py,  pz  }, {px,  py,  pz  }, {px,  py+1,pz  }, {px+1,py+1,pz  }};
                break;
        }
        addQuad(verts, inds, corners[0], corners[1], corners[2], corners[3], normal, indexOffset);
    }

    // Generic quad adder from 4 provided corners (CCW order as seen from outside)
    // UVs are set 0..1 across the quad (stretched when greedy meshed)
    private void addQuad(List<Float> verts, List<Integer> inds, float[] c0, float[] c1, float[] c2, float[] c3, Vector3f normal, int indexOffset) {
        float[][] uvs = new float[][]{{0,0},{1,0},{1,1},{0,1}};
        float[][] corners = new float[][]{c0, c1, c2, c3};
        for (int i = 0; i < 4; i++) {
            float[] c = corners[i];
            verts.add(c[0]); verts.add(c[1]); verts.add(c[2]);
            verts.add(uvs[i][0]); verts.add(uvs[i][1]);
            verts.add(normal.x); verts.add(normal.y); verts.add(normal.z);
        }
        inds.add(indexOffset + 0); inds.add(indexOffset + 1); inds.add(indexOffset + 2);
        inds.add(indexOffset + 2); inds.add(indexOffset + 3); inds.add(indexOffset + 0);
    }

    public void cleanup() {
        // Cleanup legacy list
        for (MeshBatch b : batches) {
            if (b.mesh != null) b.mesh.cleanup();
        }
        // Cleanup cached chunk meshes
        for (ChunkMesh cm : meshCache.values()) {
            for (MeshBatch b : cm.list) {
                if (b.mesh != null) b.mesh.cleanup();
            }
        }
        meshCache.clear();
        // Delete cached textures we created
        if (grassSideOverlayTex != null) grassSideOverlayTex.delete();
        for (Texture t : new HashSet<>(textureCache.values())) {
            if (t != null) t.delete();
        }
        textureCache.clear();
        if (shader != null) shader.delete();
    }

    // Non-greedy meshing: emit one quad per visible face
    private List<MeshBatch> buildChunkMeshesNonGreedy(Chunk chunk) {
        class Acc {
            final List<Float> verts = new ArrayList<>();
            final List<Integer> inds = new ArrayList<>();
            int indexOffset = 0;
        }
        Map<String, Acc> accs = new HashMap<>();
        int[][] dirs = new int[][]{
                { 1, 0, 0}, {-1, 0, 0},
                { 0, 1, 0}, { 0,-1, 0},
                { 0, 0, 1}, { 0, 0,-1}
        };
        Vector3f[] normals = new Vector3f[]{
                new Vector3f( 1, 0, 0), new Vector3f(-1, 0, 0),
                new Vector3f( 0, 1, 0), new Vector3f( 0,-1, 0),
                new Vector3f( 0, 0, 1), new Vector3f( 0, 0,-1)
        };
        for (int x = 0; x < Chunk.CHUNK_X; x++) {
            for (int y = 0; y < Chunk.CHUNK_Y; y++) {
                for (int z = 0; z < Chunk.CHUNK_Z; z++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (!block.isOpaque()) continue;
                    for (int f = 0; f < 6; f++) {
                        int wx = chunk.getOriginX() + x;
                        int wy = y;
                        int wz = chunk.getOriginZ() + z;
                        int nwx = wx + dirs[f][0];
                        int nwy = wy + dirs[f][1];
                        int nwz = wz + dirs[f][2];
                        boolean neighborSolid = (world != null) && world.isSolid(nwx, nwy, nwz);
                        if (neighborSolid) continue;
                        String texName = block.getFaceTextureName(f);
                        Acc acc = accs.computeIfAbsent(texName, k -> new Acc());
                        addFace(acc.verts, acc.inds, x, y, z, f, normals[f], acc.indexOffset);
                        acc.indexOffset += 4;
                    }
                }
            }
        }
        List<MeshBatch> out = new ArrayList<>();
        for (Map.Entry<String, Acc> e : accs.entrySet()) {
            Acc a = e.getValue();
            if (a.inds.isEmpty()) continue;
            float[] vertices = new float[a.verts.size()];
            for (int i = 0; i < a.verts.size(); i++) vertices[i] = a.verts.get(i);
            int[] indices = new int[a.inds.size()];
            for (int i = 0; i < a.inds.size(); i++) indices[i] = a.inds.get(i);
            Mesh mesh = new Mesh(vertices, indices);
            String texPath = e.getKey();
            Texture tex = getOrCreateTexture(texPath);
            out.add(new MeshBatch(mesh, tex, texPath));
        }
        return out;
    }

    private List<MeshBatch> buildChunkMeshes(Chunk chunk, boolean useGreedy) {
        return useGreedy ? buildChunkMeshesGreedy(chunk) : buildChunkMeshesNonGreedy(chunk);
    }

}