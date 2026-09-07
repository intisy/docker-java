package io.github.intisy.docker.registry;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * A request body, either fully in memory or streamed with a known length. A blob can be a hundred
 * megabytes, so the streaming form exists to keep it off the heap.
 *
 * @author Finn Birich
 */
public final class RegistryBody {
    private static final RegistryBody NONE = new RegistryBody(null, null, -1);

    private final byte[] bytes;
    private final InputStream stream;
    private final long length;

    private RegistryBody(byte[] bytes, InputStream stream, long length) {
        this.bytes = bytes;
        this.stream = stream;
        this.length = length;
    }

    public static RegistryBody none() {
        return NONE;
    }

    public static RegistryBody ofBytes(byte[] content) {
        return new RegistryBody(content, null, content.length);
    }

    public static RegistryBody ofStream(InputStream content, long length) {
        return new RegistryBody(null, content, length);
    }

    public boolean isPresent() {
        return bytes != null || stream != null;
    }

    public byte[] bytes() {
        return bytes;
    }

    public long length() {
        return length;
    }

    public InputStream openStream() {
        return stream != null ? stream : new ByteArrayInputStream(bytes == null ? new byte[0] : bytes);
    }
}
