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

    // Batches (one mesh per texture/block type) kept for legacy single-chunk path
    private List<MeshBatch> batches = new ArrayList<>();
    // Cache meshes per chunk for multi-chunk rendering
    private final Map<Chunk, List<MeshBatch>> meshCache = new HashMap<>();

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
            // Render all loaded chunks with per-chunk model transform
            Collection<Chunk> chunks = world.getChunks();
            for (Chunk c : chunks) {
                List<MeshBatch> list = meshCache.get(c);
                if (list == null) {
                    list = buildChunkMeshes(c);
                    meshCache.put(c, list);
                }
                Matrix4f model = new Matrix4f().translation(c.getOriginX(), 0, c.getOriginZ());
                shader.setUniformMat4("model", model);
                for (MeshBatch batch : list) {
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

    private List<MeshBatch> buildChunkMeshes(Chunk chunk) {
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

        for (int x = 0; x < Chunk.CHUNK_X; x++) {
            for (int y = 0; y < Chunk.CHUNK_Y; y++) {
                for (int z = 0; z < Chunk.CHUNK_Z; z++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (!block.isOpaque()) continue;
                    // For each face, add if neighbor is air. Use world lookup to handle cross-chunk neighbors correctly.
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
                        // Add face to the accumulator of this texture
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
            Texture tex = new Texture(texPath);
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
        float[][] uvs = new float[][]{{0,0},{1,0},{1,1},{0,1}};
        for (int i = 0; i < 4; i++) {
            float[] c = corners[i];
            // pos
            verts.add(c[0]); verts.add(c[1]); verts.add(c[2]);
            // uv
            verts.add(uvs[i][0]); verts.add(uvs[i][1]);
            // normal
            verts.add(normal.x); verts.add(normal.y); verts.add(normal.z);
        }
        // two triangles
        inds.add(indexOffset + 0); inds.add(indexOffset + 1); inds.add(indexOffset + 2);
        inds.add(indexOffset + 2); inds.add(indexOffset + 3); inds.add(indexOffset + 0);
    }

    public void cleanup() {
        // Cleanup legacy list
        for (MeshBatch b : batches) {
            if (b.mesh != null) b.mesh.cleanup();
        }
        // Cleanup cached chunk meshes
        for (List<MeshBatch> list : meshCache.values()) {
            for (MeshBatch b : list) {
                if (b.mesh != null) b.mesh.cleanup();
            }
        }
        meshCache.clear();
        for (Block block : new HashSet<>(Blocks.blocks.values())) {
            if (block != null && block.getTexture() != null) block.getTexture().delete();
        }
        if (shader != null) shader.delete();
    }
}