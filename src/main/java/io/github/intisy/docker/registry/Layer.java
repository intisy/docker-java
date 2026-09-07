package io.github.intisy.docker.registry;

import java.nio.file.Path;

/**
 * A built image layer: the gzipped tar on disk, the digest of that file, and the digest of the
 * tar inside it.
 *
 * @author Finn Birich
 */
public final class Layer {
    private final Path file;
    private final String diffId;
    private final String digest;
    private final long size;

    public Layer(Path file, String diffId, String digest, long size) {
        this.file = file;
        this.diffId = diffId;
        this.digest = digest;
        this.size = size;
    }

    public Path file() {
        return file;
    }

    /** Digest of the UNCOMPRESSED tar, which is what the image config records. */
    public String diffId() {
        return diffId;
    }

    /** Digest of the gzipped blob, which is what the manifest and the registry record. */
    public String digest() {
        return digest;
    }

    public long size() {
        return size;
    }
}
