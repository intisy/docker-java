package io.github.intisy.docker.registry;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author Finn Birich
 */
public final class Digests {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final int BUFFER_BYTES = 64 * 1024;

    public static String sha256(byte[] content) {
        MessageDigest digest = newDigest();
        digest.update(content);
        return format(digest.digest());
    }

    public static String sha256(InputStream content) throws IOException {
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[BUFFER_BYTES];
        int read;
        while ((read = content.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return format(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by every JRE", impossible);
        }
    }

    private static String format(byte[] raw) {
        StringBuilder hex = new StringBuilder("sha256:");
        for (int i = 0; i < raw.length; i++) {
            int value = raw[i] & 0xff;
            hex.append(HEX[value >>> 4]).append(HEX[value & 0x0f]);
        }
        return hex.toString();
    }

    private Digests() {}
}
