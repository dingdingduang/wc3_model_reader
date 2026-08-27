package com.wc3model2mc.client.mdx.io;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Bounds-checked little-endian reader used by the legacy WC3 codecs. */
final class LittleEndianDataReader {
    private final ByteBuffer buffer;

    LittleEndianDataReader(byte[] bytes) {
        this(ByteBuffer.wrap(Objects.requireNonNull(bytes, "bytes"))
                .order(ByteOrder.LITTLE_ENDIAN));
    }

    private LittleEndianDataReader(ByteBuffer buffer) {
        this.buffer = buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    int remaining() {
        return buffer.remaining();
    }

    int position() {
        return buffer.position();
    }

    int readInt() throws IOException {
        require(4);
        return buffer.getInt();
    }

    float readFloat() throws IOException {
        require(4);
        return buffer.getFloat();
    }

    int readUnsignedShort() throws IOException {
        require(2);
        return Short.toUnsignedInt(buffer.getShort());
    }

    int readUnsignedByte() throws IOException {
        require(1);
        return Byte.toUnsignedInt(buffer.get());
    }

    String readTag() throws IOException {
        return new String(readBytes(4), StandardCharsets.ISO_8859_1);
    }

    String peekTag() throws IOException {
        require(4);
        int position = buffer.position();
        String tag = readTag();
        buffer.position(position);
        return tag;
    }

    void expectTag(String expected) throws IOException {
        String found = readTag();
        if (!expected.equals(found)) {
            throw new IOException("Expected MDX tag " + expected + " but found " + found);
        }
    }

    String readFixedString(int byteLength) throws IOException {
        byte[] bytes = readBytes(byteLength);
        int length = 0;
        while (length < bytes.length && bytes[length] != 0) {
            length++;
        }
        return new String(bytes, 0, length, StandardCharsets.ISO_8859_1).strip();
    }

    byte[] readBytes(int length) throws IOException {
        require(length);
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return bytes;
    }

    LittleEndianDataReader readSlice(int length) throws IOException {
        require(length);
        ByteBuffer slice = buffer.slice().order(ByteOrder.LITTLE_ENDIAN);
        slice.limit(length);
        buffer.position(buffer.position() + length);
        return new LittleEndianDataReader(slice);
    }

    void skip(int length) throws IOException {
        require(length);
        buffer.position(buffer.position() + length);
    }

    int checkedCount(int elementSize, String label) throws IOException {
        int count = readInt();
        if (count < 0 || (long) count * elementSize > remaining()) {
            throw new IOException("Invalid " + label + " count: " + Integer.toUnsignedLong(count));
        }
        return count;
    }

    private void require(int length) throws IOException {
        if (length < 0 || buffer.remaining() < length) {
            throw new EOFException(
                    "WC3 resource ended at byte " + buffer.position()
                            + " while reading " + length + " byte(s)"
            );
        }
    }
}
