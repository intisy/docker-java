package io.github.intisy.docker.transport;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Represents a response from the Docker daemon.
 *
 * @author Finn Birich
 */
public class DockerResponse {
    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final String body;
    private final InputStream stream;

    public DockerResponse(int statusCode, Map<String, List<String>> headers, String body) {
        this(statusCode, headers, body, null);
    }

    public DockerResponse(int statusCode, Map<String, List<String>> headers, String body, InputStream stream) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body;
        this.stream = stream;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    public InputStream getStream() {
        return stream;
    }

    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    public String getHeader(String name) {
        List<String> values = headers.get(name);
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                List<String> vals = entry.getValue();
                if (vals != null && !vals.isEmpty()) {
                    return vals.get(0);
                }
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "DockerResponse{" +
                "statusCode=" + statusCode +
                ", body='" + (body != null ? body.substring(0, Math.min(body.length(), 200)) : "null") + '\'' +
                '}';
    }
}
