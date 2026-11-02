package cepai;

import java.io.IOException;

public final class Base64 {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    public static String encode(byte[] bytes) {
        StringBuffer out = new StringBuffer((bytes.length + 2) / 3 * 4);
        for (int i = 0; i < bytes.length; i += 3) {
            int a = bytes[i] & 255, b = i + 1 < bytes.length ? bytes[i + 1] & 255 : 0;
            int c = i + 2 < bytes.length ? bytes[i + 2] & 255 : 0;
            out.append(ALPHABET.charAt(a >> 2)); out.append(ALPHABET.charAt(((a & 3) << 4) | (b >> 4)));
            out.append(i + 1 < bytes.length ? ALPHABET.charAt(((b & 15) << 2) | (c >> 6)) : '=');
            out.append(i + 2 < bytes.length ? ALPHABET.charAt(c & 63) : '=');
        }
        return out.toString();
    }
    public static byte[] decode(String text, int limit) throws IOException {
        int n = text.length();
        if (n % 4 != 0 || n > ((limit + 2) / 3) * 4) throw new IOException("Invalid audio encoding or size.");
        int padding = n > 0 && text.charAt(n - 1) == '=' ? 1 : 0;
        if (n > 1 && text.charAt(n - 2) == '=') padding++;
        int size = n / 4 * 3 - padding;
        if (size > limit) throw new IOException("Audio is too large.");
        byte[] out = new byte[size]; int at = 0;
        for (int i = 0; i < n; i += 4) {
            int value = 0;
            for (int j = 0; j < 4; j++) {
                char c = text.charAt(i + j); int code = ALPHABET.indexOf(c);
                if (c == '=' && i == n - 4 && i + j >= n - padding) code = 0;
                if (code < 0) throw new IOException("Invalid base64.");
                value = (value << 6) | code;
            }
            if (at < size) out[at++] = (byte) (value >> 16);
            if (at < size) out[at++] = (byte) (value >> 8);
            if (at < size) out[at++] = (byte) value;
        }
        return out;
    }
}
