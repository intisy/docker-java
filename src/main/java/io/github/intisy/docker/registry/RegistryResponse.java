package io.github.intisy.docker.registry;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * @implNote headers are held case-insensitively because {@code HttpURLConnection} preserves
 * whatever casing the server sent, and registries disagree about {@code Docker-Content-Digest}.
 *
 * @author Finn Birich
 */
public final class RegistryResponse {
    private final int status;
    private final Map<String, String> headers;
    private final byte[] body;

    public RegistryResponse(int status, Map<String, String> headers, byte[] body) {
        this.status = status;
        Map<String, String> insensitive = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        insensitive.putAll(headers == null ? new HashMap<String, String>() : headers);
        this.headers = Collections.unmodifiableMap(insensitive);
        this.body = body == null ? new byte[0] : body;
    }

    public int status() {
        return status;
    }

    public boolean isSuccessful() {
        return status >= 200 && status < 300;
    }

    public String header(String name) {
        return headers.get(name);
    }

    public byte[] bytes() {
        return body;
    }

    public String text() {
        return new String(body, StandardCharsets.UTF_8);
    }
}
