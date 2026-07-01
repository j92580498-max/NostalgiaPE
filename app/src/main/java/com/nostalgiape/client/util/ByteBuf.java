package com.nostalgiape.client.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Minimal big-endian binary writer/reader used for building and parsing
 * RakNet + MCPE 0.8.1 packets. All multi-byte integers are big-endian,
 * matching the RakNet wire format used by classic Pocket Edition servers.
 *
 * This is a clean-room implementation written from the documented wire
 * format; it does not reuse any third-party or proprietary source.
 */
public class ByteBuf {

    // ---- Writer ----
    private final ByteArrayOutputStream out;

    public ByteBuf() {
        this.out = new ByteArrayOutputStream(64);
        this.data = null;
    }

    public void writeByte(int v) { out.write(v & 0xFF); }

    public void writeBool(boolean v) { out.write(v ? 1 : 0); }

    public void writeShort(int v) {
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }

    public void writeShortLE(int v) {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
    }

    public void writeTriad(int v) {
        // 24-bit little-endian (RakNet datagram sequence numbers)
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 16) & 0xFF);
    }

    public void writeInt(int v) {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }

    public void writeLong(long v) {
        for (int i = 7; i >= 0; i--) out.write((int) ((v >>> (i * 8)) & 0xFF));
    }

    public void writeFloat(float v) { writeInt(Float.floatToIntBits(v)); }

    public void writeBytes(byte[] b) { out.write(b, 0, b.length); }

    public void writeBytes(byte[] b, int off, int len) { out.write(b, off, len); }

    /** RakNet-style string: big-endian unsigned short length + UTF-8 bytes. */
    public void writeString(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        writeShort(b.length);
        writeBytes(b);
    }

    public byte[] toBytes() { return out.toByteArray(); }

    public int size() { return out.size(); }

    // ---- Reader ----
    private byte[] data;
    private int pos;

    public ByteBuf(byte[] data) {
        this.out = null;
        this.data = data;
        this.pos = 0;
    }

    public int remaining() { return data.length - pos; }

    public int position() { return pos; }

    public void skip(int n) { pos += n; }

    public int readByte() { return data[pos++] & 0xFF; }

    public int readSignedByte() { return data[pos++]; }

    public boolean readBool() { return data[pos++] != 0; }

    public int readShort() {
        int v = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
        pos += 2;
        return v;
    }

    public int readShortLE() {
        int v = (data[pos] & 0xFF) | ((data[pos + 1] & 0xFF) << 8);
        pos += 2;
        return v;
    }

    public int readTriad() {
        int v = (data[pos] & 0xFF) | ((data[pos + 1] & 0xFF) << 8) | ((data[pos + 2] & 0xFF) << 16);
        pos += 3;
        return v;
    }

    public int readInt() {
        int v = ((data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16)
                | ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
        pos += 4;
        return v;
    }

    public long readLong() {
        long v = 0;
        for (int i = 0; i < 8; i++) v = (v << 8) | (data[pos++] & 0xFF);
        return v;
    }

    public float readFloat() { return Float.intBitsToFloat(readInt()); }

    public byte[] readBytes(int len) {
        byte[] b = new byte[len];
        System.arraycopy(data, pos, b, 0, len);
        pos += len;
        return b;
    }

    public String readString() {
        int len = readShort();
        String s = new String(data, pos, len, StandardCharsets.UTF_8);
        pos += len;
        return s;
    }

    public byte[] readRemaining() {
        return readBytes(remaining());
    }
}
