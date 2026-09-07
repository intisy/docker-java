package io.github.intisy.docker.registry;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * A client for one registry, speaking the OCI distribution v2 API and nothing else. It never
 * contacts a Docker daemon.
 *
 * @author Finn Birich
 */
public final class RegistryClient {
    public static final String OCI_MANIFEST = "application/vnd.oci.image.manifest.v1+json";
    public static final String OCI_INDEX = "application/vnd.oci.image.index.v1+json";
    public static final String DOCKER_MANIFEST = "application/vnd.docker.distribution.manifest.v2+json";
    public static final String DOCKER_MANIFEST_LIST = "application/vnd.docker.distribution.manifest.list.v2+json";
    public static final String OCI_LAYER_GZIP = "application/vnd.oci.image.layer.v1.tar+gzip";
    public static final String OCI_CONFIG = "application/vnd.oci.image.config.v1+json";

    private static final String ACCEPT_ALL_MANIFESTS =
            OCI_INDEX + "," + OCI_MANIFEST + "," + DOCKER_MANIFEST_LIST + "," + DOCKER_MANIFEST;

    private final RegistryHttp http;
    private final String host;
    private final String scheme;

    public RegistryClient(RegistryHttp http, String host, boolean plainHttp) {
        this.http = http;
        this.host = host;
        this.scheme = plainHttp ? "http://" : "https://";
    }

    public static final class Manifest {
        private final String mediaType;
        private final String digest;
        private final byte[] body;

        Manifest(String mediaType, String digest, byte[] body) {
            this.mediaType = mediaType;
            this.digest = digest;
            this.body = body;
        }

        public String mediaType() {
            return mediaType;
        }

        public String digest() {
            return digest;
        }

        public byte[] bytes() {
            return body;
        }

        public String text() {
            return new String(body, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public Manifest getManifest(ImageReference reference) throws IOException {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Accept", ACCEPT_ALL_MANIFESTS);
        String url = base(reference.repository()) + "/manifests/" + reference.reference();
        RegistryResponse response = http.send("GET", url, headers, RegistryBody.none());
        require(response, "GET manifest " + reference);
        String digest = response.header("Docker-Content-Digest");
        if (digest == null) {
            digest = Digests.sha256(response.bytes());
        }
        return new Manifest(response.header("Content-Type"), digest, response.bytes());
    }

    public boolean blobExists(String repository, String digest) throws IOException {
        RegistryResponse response = http.send("HEAD", base(repository) + "/blobs/" + digest,
                new HashMap<String, String>(), RegistryBody.none());
        if (response.status() == 404) {
            return false;
        }
        require(response, "HEAD blob " + digest);
        return true;
    }

    public RegistryResponse getBlob(String repository, String digest) throws IOException {
        RegistryResponse response = http.send("GET", base(repository) + "/blobs/" + digest,
                new HashMap<String, String>(), RegistryBody.none());
        require(response, "GET blob " + digest);
        return response;
    }

    /**
     * The two-step upload (POST for a session, PUT with the digest) rather than the single-POST form, because
     * the monolithic POST is optional in the spec and registries differ about it, while every registry
     * implements the two-step.
     */
    public void putBlob(String repository, String digest, RegistryBody body) throws IOException {
        RegistryResponse session = http.send("POST", base(repository) + "/blobs/uploads/",
                new HashMap<String, String>(), RegistryBody.none());
        require(session, "POST blob upload session for " + digest);
        String location = session.header("Location");
        if (location == null) {
            throw new IOException("registry accepted the upload session for " + digest
                    + " but sent no Location header");
        }
        String uploadUrl = absolute(location) + (location.indexOf('?') >= 0 ? "&" : "?")
                + "digest=" + encode(digest);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/octet-stream");
        RegistryResponse upload = http.send("PUT", uploadUrl, headers, body);
        require(upload, "PUT blob " + digest);
    }

    public void putManifest(ImageReference target, String mediaType, byte[] manifestJson) throws IOException {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", mediaType);
        RegistryResponse response = http.send("PUT",
                base(target.repository()) + "/manifests/" + target.reference(),
                headers, RegistryBody.ofBytes(manifestJson));
        require(response, "PUT manifest " + target);
    }

    private String base(String repository) {
        return scheme + host + "/v2/" + repository;
    }

    private String absolute(String location) {
        return location.startsWith("http://") || location.startsWith("https://")
                ? location
                : scheme + host + location;
    }

    private static void require(RegistryResponse response, String what) throws IOException {
        if (!response.isSuccessful()) {
            throw new IOException(what + " failed: HTTP " + response.status() + ": " + response.text());
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 is required by every JRE", impossible);
        }
    }
}
