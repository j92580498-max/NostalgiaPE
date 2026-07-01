package com.nostalgiape.client.world;

import java.util.HashMap;
import java.util.Map;

/**
 * Sparse voxel world. Classic PE worlds are 128 blocks tall. Blocks are stored
 * per 16x128x16 chunk in flat arrays keyed by chunk coordinate.
 */
public class World {
    public static final int CHUNK_SIZE = 16;
    public static final int WORLD_HEIGHT = 128;

    public static class Chunk {
        public final int cx, cz;
        public final byte[] blocks = new byte[CHUNK_SIZE * WORLD_HEIGHT * CHUNK_SIZE];
        public volatile boolean dirty = true;

        public Chunk(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
        }

        private static int idx(int x, int y, int z) {
            return (x * CHUNK_SIZE + z) * WORLD_HEIGHT + y;
        }

        public int get(int x, int y, int z) {
            if (y < 0 || y >= WORLD_HEIGHT) return 0;
            return blocks[idx(x, y, z)] & 0xFF;
        }

        public void set(int x, int y, int z, int id) {
            if (y < 0 || y >= WORLD_HEIGHT) return;
            blocks[idx(x, y, z)] = (byte) id;
            dirty = true;
        }
    }

    private final Map<Long, Chunk> chunks = new HashMap<>();

    private static long key(int cx, int cz) {
        return (((long) cx) & 0xFFFFFFFFL) | ((((long) cz) & 0xFFFFFFFFL) << 32);
    }

    public synchronized Chunk getOrCreateChunk(int cx, int cz) {
        long k = key(cx, cz);
        Chunk c = chunks.get(k);
        if (c == null) {
            c = new Chunk(cx, cz);
            chunks.put(k, c);
        }
        return c;
    }

    public synchronized Chunk getChunk(int cx, int cz) {
        return chunks.get(key(cx, cz));
    }

    public synchronized Iterable<Chunk> chunks() {
        return new java.util.ArrayList<>(chunks.values());
    }

    public int getBlock(int x, int y, int z) {
        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cz = Math.floorDiv(z, CHUNK_SIZE);
        Chunk c = getChunk(cx, cz);
        if (c == null) return 0;
        return c.get(Math.floorMod(x, CHUNK_SIZE), y, Math.floorMod(z, CHUNK_SIZE));
    }

    public void setBlock(int x, int y, int z, int id) {
        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cz = Math.floorDiv(z, CHUNK_SIZE);
        Chunk c = getOrCreateChunk(cx, cz);
        c.set(Math.floorMod(x, CHUNK_SIZE), y, Math.floorMod(z, CHUNK_SIZE), id);
    }

    /**
     * Load a full chunk from the server. The wire format orders block ids as
     * XZY: index = (x*16 + z)*128 + y, which matches {@link Chunk#idx}, so the
     * 32768-byte id array is copied directly.
     */
    public synchronized void putChunk(int cx, int cz, byte[] ids) {
        Chunk c = getOrCreateChunk(cx, cz);
        int n = Math.min(ids.length, c.blocks.length);
        System.arraycopy(ids, 0, c.blocks, 0, n);
        c.dirty = true;
    }

    public boolean isSolid(int x, int y, int z) {
        return Block.isSolid(getBlock(x, y, z));
    }

    public synchronized void clear() {
        chunks.clear();
    }
}
