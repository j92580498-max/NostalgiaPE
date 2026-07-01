package com.nostalgiape.client.render;

import com.nostalgiape.client.world.Block;
import com.nostalgiape.client.world.World;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Builds a renderable mesh (interleaved position + colour) for one chunk using
 * greedy per-face culling: a face is emitted only when the neighbour is
 * transparent. Colours come from {@link Block#color}. Clean-room implementation.
 */
public class ChunkMesh {
    public FloatBuffer buffer;
    public int vertexCount;

    private static final int[][] FACE_NORMALS = {
            {0, 1, 0},   // top
            {0, -1, 0},  // bottom
            {0, 0, 1},   // north +z
            {0, 0, -1},  // south -z
            {1, 0, 0},   // east +x
            {-1, 0, 0},  // west -x
    };

    // 4 corners per face (x,y,z offsets)
    private static final float[][][] FACE_VERTS = {
            // top (y+1)
            {{0,1,0},{0,1,1},{1,1,1},{1,1,0}},
            // bottom (y)
            {{0,0,0},{1,0,0},{1,0,1},{0,0,1}},
            // north (z+1)
            {{0,0,1},{1,0,1},{1,1,1},{0,1,1}},
            // south (z)
            {{0,0,0},{0,1,0},{1,1,0},{1,0,0}},
            // east (x+1)
            {{1,0,0},{1,1,0},{1,1,1},{1,0,1}},
            // west (x)
            {{0,0,0},{0,0,1},{0,1,1},{0,1,0}},
    };

    // Per-face brightness to fake directional light (top brightest).
    private static final float[] FACE_SHADE = {1.0f, 0.5f, 0.8f, 0.8f, 0.6f, 0.6f};

    public void build(World world, World.Chunk chunk) {
        // 6 floats per vertex (x,y,z,r,g,b), 6 vertices per face (2 tris).
        java.util.ArrayList<Float> verts = new java.util.ArrayList<>(4096);
        int baseX = chunk.cx * World.CHUNK_SIZE;
        int baseZ = chunk.cz * World.CHUNK_SIZE;

        for (int x = 0; x < World.CHUNK_SIZE; x++) {
            for (int z = 0; z < World.CHUNK_SIZE; z++) {
                for (int y = 0; y < World.WORLD_HEIGHT; y++) {
                    int id = chunk.get(x, y, z);
                    if (id == Block.AIR) continue;
                    int wx = baseX + x, wz = baseZ + z;
                    int rgb = Block.color(id);
                    float r = ((rgb >> 16) & 0xFF) / 255f;
                    float g = ((rgb >> 8) & 0xFF) / 255f;
                    float b = (rgb & 0xFF) / 255f;

                    for (int f = 0; f < 6; f++) {
                        int nx = wx + FACE_NORMALS[f][0];
                        int ny = y + FACE_NORMALS[f][1];
                        int nz = wz + FACE_NORMALS[f][2];
                        int neighbour = world.getBlock(nx, ny, nz);
                        // draw face if neighbour is transparent and not same block
                        if (!Block.isTransparent(neighbour)) continue;
                        if (neighbour == id) continue;

                        float shade = FACE_SHADE[f];
                        float[][] corners = FACE_VERTS[f];
                        // two triangles: 0,1,2 and 0,2,3
                        int[] order = {0, 1, 2, 0, 2, 3};
                        for (int oi : order) {
                            float[] cvtx = corners[oi];
                            verts.add(wx + cvtx[0]);
                            verts.add((float) y + cvtx[1]);
                            verts.add(wz + cvtx[2]);
                            verts.add(r * shade);
                            verts.add(g * shade);
                            verts.add(b * shade);
                        }
                    }
                }
            }
        }

        vertexCount = verts.size() / 6;
        ByteBuffer bb = ByteBuffer.allocateDirect(verts.size() * 4).order(ByteOrder.nativeOrder());
        buffer = bb.asFloatBuffer();
        for (float v : verts) buffer.put(v);
        buffer.position(0);
    }
}
