package com.nostalgiape.client.net;

import com.nostalgiape.client.util.ByteBuf;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Clean-room RakNet client for classic Minecraft PE servers (PocketMine /
 * NostalgiaCore family, RakNet "structure 5", MCPE protocol 0.8.x).
 *
 * <p>Implements only the subset a client needs:
 * <ul>
 *   <li>Offline handshake: OPEN_CONNECTION_REQUEST_1/2 and their replies.</li>
 *   <li>Online connection: CLIENT_CONNECT, SERVER_HANDSHAKE, CLIENT_HANDSHAKE.</li>
 *   <li>Reliable-ordered encapsulated messages inside DATA_PACKET datagrams.</li>
 *   <li>ACK/NACK bookkeeping (ACKs are honoured; we resend nothing fancy).</li>
 * </ul>
 *
 * <p>This is written from a documented understanding of the wire format and
 * shares no code with RakNet, PocketMine, or Mojang. It intentionally keeps
 * the reliability layer minimal: enough to talk to a lenient classic server.
 */
public class RakNetClient {

    public static final byte[] MAGIC = {
            (byte) 0x00, (byte) 0xff, (byte) 0xff, (byte) 0x00,
            (byte) 0xfe, (byte) 0xfe, (byte) 0xfe, (byte) 0xfe,
            (byte) 0xfd, (byte) 0xfd, (byte) 0xfd, (byte) 0xfd,
            (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78
    };

    // RakNet packet ids
    private static final int ID_OPEN_CONNECTION_REQUEST_1 = 0x05;
    private static final int ID_OPEN_CONNECTION_REPLY_1 = 0x06;
    private static final int ID_OPEN_CONNECTION_REQUEST_2 = 0x07;
    private static final int ID_OPEN_CONNECTION_REPLY_2 = 0x08;
    private static final int ID_INCOMPATIBLE_PROTOCOL = 0x1a;
    private static final int ID_DATA_PACKET_MIN = 0x80;
    private static final int ID_DATA_PACKET_MAX = 0x8f;
    private static final int ID_NACK = 0xa0;
    private static final int ID_ACK = 0xc0;

    // MCPE control messages (inside encapsulated payloads)
    public static final int MC_CLIENT_CONNECT = 0x09;
    public static final int MC_SERVER_HANDSHAKE = 0x10;
    public static final int MC_CLIENT_HANDSHAKE = 0x13;
    public static final int MC_DISCONNECT = 0x15;
    public static final int MC_PING = 0x00;
    public static final int MC_PONG = 0x03;

    private static final int RAKNET_STRUCTURE = 5;
    private static final short MTU = 1447;

    public interface Listener {
        /** Called once the RakNet + MCPE session handshake has completed. */
        void onConnected();
        /** Called for every fully-received game-level payload (first byte = MCPE packet id). */
        void onGamePacket(byte[] payload);
        /** Called on disconnect or fatal error. */
        void onDisconnected(String reason);
    }

    private final String host;
    private final int port;
    private final Listener listener;

    private DatagramSocket socket;
    private InetSocketAddress addr;
    private Thread recvThread;
    private Thread tickThread;
    private volatile boolean running;
    private volatile boolean handshakeDone;

    private final long clientId;
    private int sendSeq = 0;        // datagram sequence number (24-bit)
    private int messageIndex = 0;   // reliable message index (24-bit)
    private int orderIndex = 0;     // ordered index on channel 0 (24-bit)

    private final ConcurrentLinkedQueue<byte[]> outgoing = new ConcurrentLinkedQueue<>();

    public RakNetClient(String host, int port, Listener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
        this.clientId = (long) (Math.random() * Long.MAX_VALUE);
    }

    public void connect() {
        running = true;
        recvThread = new Thread(this::runNetwork, "raknet-recv");
        recvThread.setDaemon(true);
        recvThread.start();
    }

    public void disconnect() {
        running = false;
        if (handshakeDone) {
            try {
                ByteBuf b = new ByteBuf();
                b.writeByte(MC_DISCONNECT);
                sendEncapsulated(b.toBytes());
            } catch (Exception ignored) {
            }
        }
        if (socket != null) socket.close();
    }

    private void runNetwork() {
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(1000);
            addr = new InetSocketAddress(InetAddress.getByName(host), port);

            // Step 1: OpenConnectionRequest1
            sendOpenConnectionRequest1();

            byte[] recvBuf = new byte[2048];
            long lastHandshakeSend = System.currentTimeMillis();
            int stage = 1; // 1 = waiting reply1, 2 = waiting reply2, 3 = online

            while (running) {
                // resend outgoing game packets
                byte[] pending;
                while (handshakeDone && (pending = outgoing.poll()) != null) {
                    sendEncapsulated(pending);
                }

                try {
                    DatagramPacket dp = new DatagramPacket(recvBuf, recvBuf.length);
                    socket.receive(dp);
                    stage = handleDatagram(recvBuf, dp.getLength(), stage);
                } catch (java.net.SocketTimeoutException te) {
                    // resend handshake steps if stalled
                    long now = System.currentTimeMillis();
                    if (!handshakeDone && now - lastHandshakeSend > 1000) {
                        if (stage == 1) sendOpenConnectionRequest1();
                        else if (stage == 2) sendOpenConnectionRequest2();
                        lastHandshakeSend = now;
                    }
                }
            }
        } catch (Exception e) {
            if (running && listener != null) listener.onDisconnected(e.getMessage());
        }
    }

    // ---- Offline handshake ----

    private void sendOpenConnectionRequest1() {
        ByteBuf b = new ByteBuf();
        b.writeByte(ID_OPEN_CONNECTION_REQUEST_1);
        b.writeBytes(MAGIC);
        b.writeByte(RAKNET_STRUCTURE);
        // padding to advertise MTU (server measures the datagram length)
        byte[] pad = new byte[MTU - 18];
        b.writeBytes(pad);
        sendRaw(b.toBytes());
    }

    private void sendOpenConnectionRequest2() {
        ByteBuf b = new ByteBuf();
        b.writeByte(ID_OPEN_CONNECTION_REQUEST_2);
        b.writeBytes(MAGIC);
        // server security (5 bytes): address family(1)+ip(4). classic uses 0.
        b.writeByte(4);
        b.writeByte(255); b.writeByte(255); b.writeByte(255); b.writeByte(255);
        b.writeShort(port);
        b.writeShort(MTU);
        b.writeLong(clientId);
        sendRaw(b.toBytes());
    }

    private int handleDatagram(byte[] buf, int len, int stage) {
        int pid = buf[0] & 0xFF;
        if (pid == ID_OPEN_CONNECTION_REPLY_1) {
            sendOpenConnectionRequest2();
            return 2;
        } else if (pid == ID_OPEN_CONNECTION_REPLY_2) {
            sendClientConnect();
            return 3;
        } else if (pid == ID_INCOMPATIBLE_PROTOCOL) {
            if (listener != null) listener.onDisconnected("Incompatible RakNet protocol");
            running = false;
            return stage;
        } else if (pid == ID_ACK || pid == ID_NACK) {
            // We keep the reliability layer minimal; ignore for now.
            return stage;
        } else if (pid >= ID_DATA_PACKET_MIN && pid <= ID_DATA_PACKET_MAX) {
            handleDataPacket(buf, len);
            return stage;
        }
        return stage;
    }

    // ---- Online (encapsulated) layer ----

    private void sendClientConnect() {
        ByteBuf b = new ByteBuf();
        b.writeByte(MC_CLIENT_CONNECT);
        b.writeLong(clientId);
        b.writeLong(System.currentTimeMillis());   // session
        b.writeByte(0);                              // unknown/security
        sendEncapsulated(b.toBytes());
    }

    private void sendClientHandshake(int serverPort) {
        ByteBuf b = new ByteBuf();
        b.writeByte(MC_CLIENT_HANDSHAKE);
        b.writeBytes(new byte[]{0x04, 0x3f, 0x57, (byte) 0xfe}); // cookie
        b.writeByte(0xcd);                                       // security flags
        b.writeShort(serverPort);
        b.writeByte(4);
        b.writeBytes(new byte[]{(byte) 0xf5, (byte) 0xff, (byte) 0xff, (byte) 0xf5});
        // 9 more system addresses (matching server's DataArray of 10 total)
        for (int i = 0; i < 9; i++) {
            b.writeTriad(4);
            b.writeBytes(new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff});
        }
        b.writeShort(0);                       // timestamp (2 bytes)
        b.writeLong(System.currentTimeMillis());
        b.writeLong(clientId);
        sendEncapsulated(b.toBytes());
    }

    private void handleDataPacket(byte[] buf, int len) {
        ByteBuf r = new ByteBuf(buf);
        r.readByte();                 // datagram header id
        int seq = r.readTriad();
        // ACK this datagram
        sendAck(seq);

        while (r.remaining() > 0) {
            Encapsulated enc = readEncapsulated(r);
            if (enc == null || enc.payload == null || enc.payload.length == 0) break;
            if (enc.hasSplit) {
                byte[] full = handleSplit(enc);
                if (full != null) dispatch(full);
            } else {
                dispatch(enc.payload);
            }
        }
    }

    private static final class Encapsulated {
        byte[] payload;
        boolean hasSplit;
        int splitCount;
        int splitId;
        int splitIndex;
    }

    // splitId -> fragments
    private final java.util.Map<Integer, byte[][]> splitBuffers = new java.util.HashMap<>();

    private byte[] handleSplit(Encapsulated enc) {
        byte[][] parts = splitBuffers.get(enc.splitId);
        if (parts == null) {
            if (enc.splitCount <= 0 || enc.splitCount > 4096) return null;
            parts = new byte[enc.splitCount][];
            splitBuffers.put(enc.splitId, parts);
        }
        if (enc.splitIndex < 0 || enc.splitIndex >= parts.length) return null;
        parts[enc.splitIndex] = enc.payload;
        // check completeness
        int total = 0;
        for (byte[] p : parts) {
            if (p == null) return null;
            total += p.length;
        }
        byte[] full = new byte[total];
        int off = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, full, off, p.length);
            off += p.length;
        }
        splitBuffers.remove(enc.splitId);
        return full;
    }

    private Encapsulated readEncapsulated(ByteBuf r) {
        if (r.remaining() < 3) return null;
        Encapsulated enc = new Encapsulated();
        int flags = r.readByte();
        int reliability = (flags & 0xE0) >> 5;
        enc.hasSplit = (flags & 0x10) != 0;
        int lengthBits = r.readShort();
        int length = (lengthBits + 7) >> 3;

        if (reliability == 2 || reliability == 3 || reliability == 4 || reliability == 6 || reliability == 7) {
            r.readTriad(); // messageIndex
        }
        if (reliability == 1 || reliability == 4) {
            r.readTriad(); // sequenceIndex
        }
        if (reliability == 1 || reliability == 3 || reliability == 4 || reliability == 7) {
            r.readTriad(); // orderIndex
            r.readByte();  // orderChannel
        }
        if (enc.hasSplit) {
            enc.splitCount = r.readInt();   // splitCount
            enc.splitId = r.readShort();    // splitId
            enc.splitIndex = r.readInt();   // splitIndex
        }
        if (length <= 0 || length > r.remaining()) return null;
        enc.payload = r.readBytes(length);
        return enc;
    }

    private void dispatch(byte[] payload) {
        int id = payload[0] & 0xFF;
        switch (id) {
            case MC_SERVER_HANDSHAKE: {
                sendClientHandshake(port);
                if (!handshakeDone) {
                    handshakeDone = true;
                    if (listener != null) listener.onConnected();
                }
                break;
            }
            case MC_PING: {
                // reply with pong echoing timestamp
                ByteBuf b = new ByteBuf();
                b.writeByte(MC_PONG);
                if (payload.length >= 9) b.writeBytes(payload, 1, 8);
                sendEncapsulated(b.toBytes());
                break;
            }
            case MC_DISCONNECT: {
                if (listener != null) listener.onDisconnected("Server closed the connection");
                running = false;
                break;
            }
            default:
                if (listener != null) listener.onGamePacket(payload);
                break;
        }
    }

    // ---- Sending helpers ----

    /** Queue a game-level payload (first byte = MCPE packet id) for reliable-ordered delivery. */
    public void send(byte[] payload) {
        if (handshakeDone) sendEncapsulated(payload);
        else outgoing.add(payload);
    }

    private synchronized void sendEncapsulated(byte[] payload) {
        ByteBuf b = new ByteBuf();
        b.writeByte(0x84); // DATA_PACKET_4
        b.writeTriad(sendSeq++);

        // encapsulation header: reliable ordered (reliability 3)
        int reliability = 3;
        int flags = (reliability << 5);
        b.writeByte(flags);
        b.writeShort(payload.length * 8); // length in bits
        b.writeTriad(messageIndex++);
        b.writeTriad(orderIndex++);
        b.writeByte(0); // order channel
        b.writeBytes(payload);
        sendRaw(b.toBytes());
    }

    private void sendAck(int seq) {
        ByteBuf b = new ByteBuf();
        b.writeByte(ID_ACK);
        b.writeShort(1);       // record count
        b.writeByte(1);        // range == single (1) / continuous (0)
        b.writeTriad(seq);
        sendRaw(b.toBytes());
    }

    private void sendRaw(byte[] data) {
        try {
            DatagramPacket dp = new DatagramPacket(data, data.length, addr);
            socket.send(dp);
        } catch (Exception e) {
            if (running && listener != null) listener.onDisconnected("send failed: " + e.getMessage());
        }
    }

    public boolean isConnected() { return handshakeDone; }
}
