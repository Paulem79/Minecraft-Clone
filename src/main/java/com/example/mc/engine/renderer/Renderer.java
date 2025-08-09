package com.example.mc.engine.renderer;

import com.example.mc.engine.Camera;
import com.example.mc.engine.Window;
import com.example.mc.world.Block;
import com.example.mc.world.Chunk;
import com.example.mc.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;

import java.util.*;

import static org.lwjgl.opengl.GL11.*;

public class Renderer {

    private static class MeshBatch {
        final Mesh mesh;
        final Texture texture;
        MeshBatch(Mesh mesh, Texture texture) { this.mesh = mesh; this.texture = texture; }
    }

    private Shader shader;
    private Camera camera;
    private World world;

    // Textures by block id
    private final Map<Integer, Texture> textures = new HashMap<>();
    // Batches (one mesh per texture/block type) kept for legacy single-chunk path
    private List<MeshBatch> batches = new ArrayList<>();
    // Cache meshes per chunk for multi-chunk rendering
    private final Map<Chunk, List<MeshBatch>> meshCache = new HashMap<>();

    public void init() throws Exception {
        // Active OpenGL
        GL.createCapabilities();
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        // Couleur de fond
        glClearColor(0.5f, 0.8f, 1.0f, 1.0f);

        // Charger shader
        shader = new Shader("/shaders/vertex.glsl", "/shaders/fragment.glsl");

        // Charger les textures correspondantes aux IDs de blocs
        // Valeurs par défaut simples pour l'instant
        textures.put(Block.DIRT, new Texture("/textures/dirt.png"));
        textures.put(Block.GRASS, new Texture("/textures/grass.png"));
        // Texture de repli
        textures.putIfAbsent(-1, new Texture("/textures/stone.png"));

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
                    if (batch.texture != null) batch.texture.bind(0);
                    if (batch.mesh != null) batch.mesh.render();
                }
            }
        } else {
            // Fallback: render any prebuilt batches at origin
            Matrix4f model = new Matrix4f().identity();
            shader.setUniformMat4("model", model);
            for (MeshBatch batch : batches) {
                if (batch.texture != null) batch.texture.bind(0);
                if (batch.mesh != null) batch.mesh.render();
            }
        }
    }

    private List<MeshBatch> buildChunkMeshes(Chunk chunk) {
        // Accumulateurs par type de bloc
        class Acc {
            final List<Float> verts = new ArrayList<>();
            final List<Integer> inds = new ArrayList<>();
            int indexOffset = 0;
        }
        Map<Integer, Acc> accs = new HashMap<>();

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
                    int id = chunk.getBlock(x, y, z);
                    if (id == Block.AIR) continue;
                    Acc acc = accs.computeIfAbsent(id, k -> new Acc());
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
                        // Add face to the accumulator of this block id
                        addFace(acc.verts, acc.inds, x, y, z, f, normals[f], acc.indexOffset);
                        acc.indexOffset += 4;
                    }
                }
            }
        }

        List<MeshBatch> out = new ArrayList<>();
        for (Map.Entry<Integer, Acc> e : accs.entrySet()) {
            Acc a = e.getValue();
            if (a.inds.isEmpty()) continue;
            float[] v = new float[a.verts.size()];
            for (int i = 0; i < a.verts.size(); i++) v[i] = a.verts.get(i);
            int[] ii = new int[a.inds.size()];
            for (int i = 0; i < a.inds.size(); i++) ii[i] = a.inds.get(i);
            Mesh mesh = new Mesh(v, ii);
            Texture tex = textures.getOrDefault(e.getKey(), textures.get(-1));
            out.add(new MeshBatch(mesh, tex));
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
            case 0: // +X
                corners = new float[][]{{px+1,py,  pz  }, {px+1,py,  pz+1}, {px+1,py+1,pz+1}, {px+1,py+1,pz  }};
                break;
            case 1: // -X
                corners = new float[][]{{px,  py,  pz+1}, {px,  py,  pz  }, {px,  py+1,pz  }, {px,  py+1,pz+1}};
                break;
            case 2: // +Y (top)
                corners = new float[][]{{px,  py+1,pz  }, {px+1,py+1,pz  }, {px+1,py+1,pz+1}, {px,  py+1,pz+1}};
                break;
            case 3: // -Y (bottom)
                corners = new float[][]{{px,  py,  pz+1}, {px+1,py,  pz+1}, {px+1,py,  pz  }, {px,  py,  pz  }};
                break;
            case 4: // +Z
                corners = new float[][]{{px+1,py,  pz+1}, {px,  py,  pz+1}, {px,  py+1,pz+1}, {px+1,py+1,pz+1}};
                break;
            default: // -Z
                corners = new float[][]{{px,  py,  pz  }, {px+1,py,  pz  }, {px+1,py+1,pz  }, {px,  py+1,pz  }};
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
        for (Texture t : new HashSet<>(textures.values())) {
            if (t != null) t.delete();
        }
        if (shader != null) shader.delete();
    }
}