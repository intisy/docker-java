package io.github.intisy.docker.registry;

/**
 * A parsed container image reference.
 *
 * @implNote {@code docker.io} is the canonical registry name but not the host that serves it, so
 * {@link #registry()} and {@link #host()} differ for Docker Hub and nowhere else.
 *
 * @author Finn Birich
 */
public final class ImageReference {
    private static final String DOCKER_HUB = "docker.io";
    private static final String DOCKER_HUB_HOST = "registry-1.docker.io";
    private static final String DEFAULT_TAG = "latest";

    private final String registry;
    private final String repository;
    private final String reference;
    private final boolean digest;

    private ImageReference(String registry, String repository, String reference, boolean digest) {
        this.registry = registry;
        this.repository = repository;
        this.reference = reference;
        this.digest = digest;
    }

    public static ImageReference parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("image reference must not be empty");
        }
        String remainder = text.trim();
        String registry = DOCKER_HUB;
        int firstSlash = remainder.indexOf('/');
        if (firstSlash > 0 && looksLikeRegistry(remainder.substring(0, firstSlash))) {
            registry = remainder.substring(0, firstSlash);
            remainder = remainder.substring(firstSlash + 1);
        }

        String reference = DEFAULT_TAG;
        boolean isDigest = false;
        int at = remainder.indexOf('@');
        if (at >= 0) {
            reference = remainder.substring(at + 1);
            remainder = remainder.substring(0, at);
            isDigest = true;
        } else {
            int colon = remainder.lastIndexOf(':');
            if (colon >= 0) {
                reference = remainder.substring(colon + 1);
                remainder = remainder.substring(0, colon);
            }
        }

        String repository = remainder;
        if (DOCKER_HUB.equals(registry) && repository.indexOf('/') < 0) {
            repository = "library/" + repository;
        }
        return new ImageReference(registry, repository, reference, isDigest);
    }

    /**
     * @implNote the docker convention: a first segment is a registry only if it carries a dot or a
     * port, or is literally localhost. Without this, {@code myorg/app} would read as a registry
     * called myorg.
     */
    private static boolean looksLikeRegistry(String candidate) {
        return candidate.indexOf('.') >= 0 || candidate.indexOf(':') >= 0 || "localhost".equals(candidate);
    }

    public String registry() {
        return registry;
    }

    public String host() {
        return DOCKER_HUB.equals(registry) ? DOCKER_HUB_HOST : registry;
    }

    public String repository() {
        return repository;
    }

    public String reference() {
        return reference;
    }

    public boolean isDigest() {
        return digest;
    }

    public ImageReference withRegistry(String newRegistry) {
        return new ImageReference(newRegistry, repository, reference, digest);
    }

    public ImageReference withReference(String newReference, boolean newIsDigest) {
        return new ImageReference(registry, repository, newReference, newIsDigest);
    }

    @Override
    public String toString() {
        return registry + "/" + repository + (digest ? "@" : ":") + reference;
    }
}
