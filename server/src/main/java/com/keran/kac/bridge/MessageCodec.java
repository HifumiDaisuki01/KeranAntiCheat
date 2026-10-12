package com.keran.kac.bridge;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Fabric PacketByteBuf string framing, with no permissive raw-payload fallback. */
public final class MessageCodec {
    private MessageCodec() {}
    public static byte[] encode(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 30000) throw new IllegalArgumentException("Message too large");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int length = bytes.length;
        do { int part = length & 127; length >>>= 7; out.write(length == 0 ? part : part | 128); } while (length != 0);
        out.writeBytes(bytes); return out.toByteArray();
    }
    public static String decode(byte[] data) {
        if (data == null || data.length == 0 || data.length > 32767) return null;
        int length = 0, pos = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            if (pos >= data.length) return null;
            int b = data[pos++] & 255;
            if (shift == 28 && (b & 0xf0) != 0) return null;
            length |= (b & 127) << shift;
            if ((b & 128) == 0) {
                if (length < 0 || length > 30000 || length != data.length - pos) return null;
                return new String(data, pos, length, StandardCharsets.UTF_8);
            }
        }
        return null;
    }
    public static boolean reasonableDepth(String json) {
        int depth = 0; boolean quoted = false, escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') { if (++depth > 8) return false; }
            else if (c == '}' || c == ']') { if (--depth < 0) return false; }
        }
        return depth == 0 && !quoted;
    }
}
