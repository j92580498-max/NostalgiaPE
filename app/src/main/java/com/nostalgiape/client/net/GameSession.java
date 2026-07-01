package com.nostalgiape.client.net;

import com.nostalgiape.client.util.ByteBuf;
import com.nostalgiape.client.world.World;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Inflater;

/**
 * MCPE 0.8.1 game session. Sits on top of {@link RakNetClient} and speaks the
 * classic Pocket Edition game protocol: login, world/chunk streaming, movement,
 * block updates and chat. Clean-room implementation from the documented wire
 * format.
 */
public class GameSession implements RakNetClient.Listener {

    public interface GameListener {
        void onStatus(String message);
        void onLoginSuccess(long entityId, float x, float y, float z);
        void onChatMessage(String source, String message);
        void onSpawn();
        void onDisconnected(String reason);
    }

    private final RakNetClient net;
    private final String username;
    private final GameListener gl;
    private final World world;

    private long entityId;
    public float playerX, playerY, playerZ;
    public float playerYaw, playerPitch;
    private boolean spawned;
    private final int clientId;

    public GameSession(String host, int port, String username, World world, GameListener gl) {
        this.net = new RakNetClient(host, port, this);
        this.username = username;
        this.world = world;
        this.gl = gl;
        this.clientId = (int) (Math.random() * Integer.MAX_VALUE);
    }

    public void start() {
        gl.onStatus("Connecting to server...");
        net.connect();
    }

    public void stop() { net.disconnect(); }

    // ---- RakNet callbacks ----

    @Override
    public void onConnected() {
        gl.onStatus("Handshake OK, logging in as " + username + "...");
        sendLogin();
    }

    @Override
    public void onDisconnected(String reason) {
        gl.onDisconnected(reason == null ? "connection lost" : reason);
    }

    @Override
    public void onGamePacket(byte[] payload) {
        try {
            handleGame(payload);
        } catch (Exception e) {
            // Never let a malformed packet kill the session.
        }
    }

    // ---- Outgoing ----

    private void sendLogin() {
        ByteBuf b = new ByteBuf();
        b.writeByte(McpeProtocol.LOGIN);
        b.writeString(username);
        b.writeInt(McpeProtocol.PROTOCOL_VERSION);
        b.writeInt(McpeProtocol.PROTOCOL_VERSION);
        b.writeInt(clientId);
        b.writeString(""); // realms/login payload (unused on classic servers)
        net.send(b.toBytes());
    }

    public void sendChat(String message) {
        if (message.length() > 116) message = message.substring(0, 116);
        ByteBuf b = new ByteBuf();
        b.writeByte(McpeProtocol.CHAT);
        b.writeString(message);
        net.send(b.toBytes());
    }

    public void sendMove(float x, float y, float z, float yaw, float pitch) {
        this.playerX = x; this.playerY = y; this.playerZ = z;
        this.playerYaw = yaw; this.playerPitch = pitch;
        if (!spawned) return;
        ByteBuf b = new ByteBuf();
        b.writeByte(McpeProtocol.MOVE_PLAYER);
        b.writeInt((int) entityId);
        b.writeFloat(x);
        b.writeFloat(y);
        b.writeFloat(z);
        b.writeFloat(yaw);
        b.writeFloat(pitch);
        b.writeFloat(yaw); // bodyYaw
        net.send(b.toBytes());
    }

    public void sendRemoveBlock(int x, int y, int z) {
        ByteBuf b = new ByteBuf();
        b.writeByte(McpeProtocol.REMOVE_BLOCK);
        b.writeInt((int) entityId);
        b.writeInt(x);
        b.writeInt(z);
        b.writeByte(y);
        net.send(b.toBytes());
    }

    public void sendUseItem(int x, int y, int z, int face, int itemId, int meta) {
        ByteBuf b = new ByteBuf();
        b.writeByte(McpeProtocol.USE_ITEM);
        b.writeInt(x);
        b.writeInt(y);
        b.writeInt(z);
        b.writeInt(face);
        b.writeShort(itemId);
        b.writeByte(meta);
        b.writeInt((int) entityId);
        b.writeFloat(0); b.writeFloat(0); b.writeFloat(0);
        b.writeFloat(playerX); b.writeFloat(playerY); b.writeFloat(playerZ);
        net.send(b.toBytes());
    }

    private void sendReady() {
        ByteBuf b = new ByteBuf();
        b.writeByte(McpeProtocol.READY);
        b.writeByte(1);
        net.send(b.toBytes());
    }

    // ---- Incoming ----

    private void handleGame(byte[] payload) {
        ByteBuf r = new ByteBuf(payload);
        int id = r.readByte();
        switch (id) {
            case McpeProtocol.LOGIN_STATUS: {
                int status = r.readInt();
                if (status == McpeProtocol.STATUS_LOGIN_SUCCESS) {
                    gl.onStatus("Login accepted");
                } else {
                    gl.onDisconnected("Login refused (status " + status + ")");
                }
                break;
            }
            case McpeProtocol.START_GAME: {
                r.readInt(); // seed
                r.readInt(); // generator
                r.readInt(); // gamemode
                entityId = r.readInt();
                r.readInt(); // spawnX
                r.readInt(); // spawnY
                r.readInt(); // spawnZ
                playerX = r.readFloat();
                playerY = r.readFloat();
                playerZ = r.readFloat();
                gl.onStatus("World starting...");
                gl.onLoginSuccess(entityId, playerX, playerY, playerZ);
                break;
            }
            case McpeProtocol.FULL_CHUNK_DATA: {
                parseFullChunk(r.readRemaining());
                break;
            }
            case McpeProtocol.SET_SPAWN_POSITION: {
                // int x, int z, byte y
                break;
            }
            case McpeProtocol.SET_TIME:
                break;
            case McpeProtocol.MESSAGE: {
                String source = r.readString();
                String msg = r.readString();
                gl.onChatMessage(source, msg);
                break;
            }
            case McpeProtocol.CHAT: {
                String msg = r.readString();
                gl.onChatMessage("", msg);
                break;
            }
            case McpeProtocol.UPDATE_BLOCK: {
                int x = r.readInt();
                int z = r.readInt();
                int y = r.readByte();
                int bid = r.readByte();
                r.readByte(); // meta
                world.setBlock(x, y, z, bid);
                break;
            }
            case McpeProtocol.MOVE_PLAYER: {
                // other players / server-corrected position; ignored for now
                break;
            }
            case McpeProtocol.SET_HEALTH:
            case McpeProtocol.RESPAWN:
                break;
            default:
                // Not all classic packets are handled; that's fine for a viewer client.
                break;
        }

        if (!spawned && entityId != 0) {
            spawned = true;
            sendReady();
            gl.onSpawn();
        }
    }

    /**
     * FullChunkData payload = zlib( LInt(x) LInt(z) blockIds[32768] blockData[16384]
     * skyLight[16384] blockLight[16384] heightmap[256] biome[256] tileNBT... ).
     * Block ids are ordered XZY: index = (x*16 + z)*128 + y.
     */
    private void parseFullChunk(byte[] compressed) {
        byte[] raw = inflate(compressed);
        if (raw == null || raw.length < 8 + 32768) return;
        ByteBuf r = new ByteBuf(raw);
        int chunkX = readLInt(r);
        int chunkZ = readLInt(r);
        byte[] ids = r.readBytes(32768);
        world.putChunk(chunkX, chunkZ, ids);
    }

    private static int readLInt(ByteBuf r) {
        int b0 = r.readByte();
        int b1 = r.readByte();
        int b2 = r.readByte();
        int b3 = r.readByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static byte[] inflate(byte[] data) {
        try {
            Inflater inflater = new Inflater();
            inflater.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream(65536);
            byte[] buf = new byte[8192];
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break;
                }
                out.write(buf, 0, n);
            }
            inflater.end();
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isSpawned() { return spawned; }
    public long getEntityId() { return entityId; }
}
