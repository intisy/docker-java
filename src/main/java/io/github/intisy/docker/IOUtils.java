package io.github.intisy.docker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * I/O utility methods for Java 8 compatibility.
 *
 * @author Finn Birich
 */
final class IOUtils {
    
    private IOUtils() {}
    
    /**
     * Reads all bytes from an input stream (Java 8 compatible replacement for InputStream.readAllBytes()).
     *
     * @param inputStream the input stream to read from
     * @return byte array containing all bytes read
     * @throws IOException if an I/O error occurs
     */
    static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        return buffer.toByteArray();
    }
}
